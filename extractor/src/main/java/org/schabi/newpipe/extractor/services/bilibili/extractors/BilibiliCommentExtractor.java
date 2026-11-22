package org.schabi.newpipe.extractor.services.bilibili.extractors;

import com.grack.nanojson.JsonArray;
import com.grack.nanojson.JsonObject;
import com.grack.nanojson.JsonParser;
import com.grack.nanojson.JsonParserException;

import org.apache.commons.lang3.StringEscapeUtils;
import org.schabi.newpipe.extractor.Page;
import org.schabi.newpipe.extractor.StreamingService;
import org.schabi.newpipe.extractor.comments.CommentsExtractor;
import org.schabi.newpipe.extractor.comments.CommentsInfoItem;
import org.schabi.newpipe.extractor.comments.CommentsInfoItemsCollector;
import org.schabi.newpipe.extractor.downloader.Downloader;
import org.schabi.newpipe.extractor.exceptions.ExtractionException;
import org.schabi.newpipe.extractor.linkhandler.ListLinkHandler;
import org.schabi.newpipe.extractor.services.bilibili.utils;

import javax.annotation.Nonnull;

import java.io.IOException;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;

import static org.schabi.newpipe.extractor.services.bilibili.BilibiliService.FETCH_COMMENTS_URL;
import static org.schabi.newpipe.extractor.services.bilibili.BilibiliService.WWW_REFERER;
import static org.schabi.newpipe.extractor.services.bilibili.BilibiliService.getDefaultCookies;
import static org.schabi.newpipe.extractor.services.bilibili.BilibiliService.getUserAgentHeaders;

public class BilibiliCommentExtractor extends CommentsExtractor {
    private static final List<String> SORT_ORDERS = Arrays.asList("Top", "Newest");

    JsonObject data = new JsonObject();
    private int sortOrder = 0;

    public BilibiliCommentExtractor(StreamingService service, ListLinkHandler uiHandler) {
        super(service, uiHandler);
    }

    @Override
    public void onFetchPage(@Nonnull Downloader downloader) throws IOException, ExtractionException {
        try {
            String url = getUrl();
            if (!isRepliesOfComment(url) && sortOrder == 1) {
                url = buildFetchUrl(url, sortOrder, "");
            }
            String response = utils.getRiskCheckedBody(isRepliesOfComment(url)
                    ? getDownloader().get(url, getUserAgentHeaders(getLinkHandler().getOriginalUrl()))
                    : getDownloader().get(url));
            data = JsonParser.object().from(response);
            data = data.getObject("data");
        } catch (JsonParserException e) {
            e.printStackTrace();
        }
    }

    @Nonnull
    @Override
    public InfoItemsPage<CommentsInfoItem> getInitialPage() throws IOException, ExtractionException {
        return getPage(new Page(getUrl(), getDefaultCookies()));
    }

    @Override
    public InfoItemsPage<CommentsInfoItem> getPage(Page page) throws IOException, ExtractionException {
        String pageUrl = page.getUrl();
        String initialUrl = getUrl();

        JsonArray results;
        if (pageUrl.equals(initialUrl)) { // first page with top comments
            results = data.getArray("top_replies");
            if (results == null) {
                results = new JsonArray();
            }
            for (int i = 0; i < results.size(); i++) {
                results.getObject(i).put("isTop", true);
            }
            final JsonArray replies = data.getArray("replies");
            if (replies != null) {
                results.addAll(replies);
            }
        } else { // other pages
            try {
                final String responseJson = utils.getRiskCheckedBody(
                        isRepliesOfComment(pageUrl)
                                ? getDownloader().get(pageUrl, getUserAgentHeaders(WWW_REFERER))
                                : getDownloader().get(pageUrl));
                data = JsonParser.object().from(responseJson).getObject("data");
            } catch (JsonParserException e) {
                e.printStackTrace();
            }
            results = data.getArray("replies");
        }

        if (results == null || results.isEmpty()) { // empty page
            return new InfoItemsPage<>(new CommentsInfoItemsCollector(getServiceId()), null);
        }

        final CommentsInfoItemsCollector collector = new CommentsInfoItemsCollector(getServiceId());
        for (int i = 0; i < results.size(); i++) {
            collector.commit(new BilibiliCommentsInfoItemExtractor(results.getObject(i)));
        }

        if (!isRepliesOfComment(pageUrl)) { //comments
            if (data.getObject("cursor").getBoolean("is_end")) {
                return new InfoItemsPage<>(collector, null);
            }
            //data.cursor.pagination_reply.next_offset
            String offset = data.getObject("cursor").getObject("pagination_reply").getString("next_offset");
            return new InfoItemsPage<>(collector, new Page(buildFetchUrl(initialUrl, sortOrder, offset), getDefaultCookies()));
        } else { //replies
            if (9 > results.size() && pageUrl.contains("pn=1")) {
                return new InfoItemsPage<>(collector, null);
            }
            return new InfoItemsPage<>(collector, new Page(utils.getNextPageFromCurrentUrl(pageUrl, "pn", 1), getDefaultCookies()));
        }
    }

    @Override
    public boolean isCommentsDisabled() throws ExtractionException {
        return getId().equals("LIVE");
    }

    private static boolean isRepliesOfComment(String requestUrl) {
        return !requestUrl.contains(FETCH_COMMENTS_URL);
    }

    private static String buildFetchUrl(String url, int sortOrder, String offset) {
        final LinkedHashMap<String, String> params = new LinkedHashMap<>();
        params.put("oid", url.split("oid=")[1].split("&")[0]);
        params.put("type", "1");
        params.put("mode", sortOrder == 1 ? "2" : "3");
        params.put("pagination_str", "{\"offset\":\"" + StringEscapeUtils.escapeJson(offset) + "\"}");
        params.put("plat", "1");
        params.put("web_location", "1315875");
        return utils.getWbiResult(FETCH_COMMENTS_URL, params);
    }

    @Override
    public List<String> getSortOrders() {
        return Collections.unmodifiableList(SORT_ORDERS);
    }

    @Override
    public int getSortOrder() {
        return sortOrder;
    }

    @Override
    public void setSortOrder(final int requestedSortOrder) {
        if (requestedSortOrder < -1 || requestedSortOrder >= SORT_ORDERS.size()) {
            throw new IllegalArgumentException("Invalid comment sort order");
        }
        if (requestedSortOrder >= 0) {
            sortOrder = requestedSortOrder;
        }
    }
}
