package org.schabi.newpipe.extractor.services.bilibili.extractors;

import static org.schabi.newpipe.extractor.services.bilibili.BilibiliService.getHeaders;

import com.grack.nanojson.JsonArray;
import com.grack.nanojson.JsonObject;
import com.grack.nanojson.JsonParser;
import com.grack.nanojson.JsonParserException;

import org.schabi.newpipe.extractor.Page;
import org.schabi.newpipe.extractor.StreamingService;
import org.schabi.newpipe.extractor.comments.CommentsExtractor;
import org.schabi.newpipe.extractor.comments.CommentsInfoItem;
import org.schabi.newpipe.extractor.comments.CommentsInfoItemsCollector;
import org.schabi.newpipe.extractor.downloader.Downloader;
import org.schabi.newpipe.extractor.exceptions.ExtractionException;
import org.schabi.newpipe.extractor.linkhandler.ListLinkHandler;
import org.schabi.newpipe.extractor.services.bilibili.utils;

import java.io.IOException;

import javax.annotation.Nonnull;

public class BilibiliCommentExtractor extends CommentsExtractor {
    JsonObject data = new JsonObject();

    public BilibiliCommentExtractor(StreamingService service, ListLinkHandler uiHandler) {
        super(service, uiHandler);
    }

    @Override
    public void onFetchPage(@Nonnull Downloader downloader) throws IOException, ExtractionException {
        String response = downloader.get(getUrl()).responseBody();
        try {
            data = JsonParser.object().from(response);
            data = data.getObject("data");
        } catch (JsonParserException e) {
            e.printStackTrace();
        }
    }

    @Nonnull
    @Override
    public InfoItemsPage<CommentsInfoItem> getInitialPage() throws IOException, ExtractionException {
        return getPage(new Page(getUrl()));
    }

    @Override
    public InfoItemsPage<CommentsInfoItem> getPage(Page page) throws IOException, ExtractionException {
        JsonArray results;
        if (page.getUrl().equals(getUrl())) {
            results = data.getArray("top_replies");
            for(int i = 0; i < results.size(); i++){
                results.getObject(i).put("isTop", true);
            }
            results.addAll(data.getArray("replies"));
        } else {
            final String html = getDownloader().get(page.getUrl()).responseBody();
            try {
                data = JsonParser.object().from(html);
            } catch (JsonParserException e) {
                e.printStackTrace();
            }
            results = data.getObject("data").getArray("replies");
        }

        if (results == null || results.size() == 0) {
            return new InfoItemsPage<>(new CommentsInfoItemsCollector(getServiceId()), null);
        }

        final CommentsInfoItemsCollector collector = new CommentsInfoItemsCollector(getServiceId());
        for (int i = 0; i < results.size(); i++) {
            collector.commit(new BilibiliCommentsInfoItemExtractor(results.getObject(i)));
        }
        if (19 > results.size() && page.getUrl().contains("pn=1")) {
            return new InfoItemsPage<>(collector, null);
        }
        return new InfoItemsPage<>(collector, new Page(utils.getNextPageFromCurrentUrl(page.getUrl(), "pn", 1)));
    }

    @Override
    public boolean isCommentsDisabled() throws ExtractionException {
        return getId().equals("LIVE");
    }

}
