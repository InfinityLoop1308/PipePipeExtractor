package org.schabi.newpipe.extractor.services.bilibili.extractors;

import com.grack.nanojson.JsonArray;
import com.grack.nanojson.JsonObject;
import com.grack.nanojson.JsonParser;
import com.grack.nanojson.JsonParserException;

import org.schabi.newpipe.extractor.InfoItemsCollector;
import org.schabi.newpipe.extractor.Page;
import org.schabi.newpipe.extractor.ServiceList;
import org.schabi.newpipe.extractor.StreamingService;
import org.schabi.newpipe.extractor.downloader.Downloader;
import org.schabi.newpipe.extractor.exceptions.ExtractionException;
import org.schabi.newpipe.extractor.exceptions.ParsingException;
import org.schabi.newpipe.extractor.linkhandler.ListLinkHandler;
import org.schabi.newpipe.extractor.playlist.PlaylistExtractor;
import org.schabi.newpipe.extractor.services.bilibili.linkHandler.BilibiliChannelLinkHandlerFactory;
import org.schabi.newpipe.extractor.services.bilibili.utils;
import org.schabi.newpipe.extractor.stream.StreamInfoItem;
import org.schabi.newpipe.extractor.stream.StreamInfoItemExtractor;
import org.schabi.newpipe.extractor.stream.StreamInfoItemsCollector;
import org.schabi.newpipe.extractor.stream.StreamCollectionInfo;
import org.schabi.newpipe.extractor.utils.Utils;

import java.io.IOException;
import java.net.MalformedURLException;
import java.util.Collections;
import java.util.List;

import javax.annotation.Nonnull;

import static org.schabi.newpipe.extractor.services.bilibili.BilibiliService.*;
import static org.schabi.newpipe.extractor.services.bilibili.utils.formatParamWithPercentSpace;

public class BilibiliPlaylistExtractor extends PlaylistExtractor {
    public JsonObject data;
    public String type;
    private JsonObject userData;
    private List<StreamCollectionInfo> sourceCollections = Collections.emptyList();

    public BilibiliPlaylistExtractor(StreamingService service, ListLinkHandler linkHandler) {
        super(service, linkHandler);
    }

    @Override
    public void onFetchPage(@Nonnull Downloader downloader) throws IOException, ExtractionException {
        try {
            final String sourceBvid = getQueryParameter("source_bvid");
            final String apiUrl = stripSourceBvid(getLinkHandler().getUrl(), sourceBvid);
            data = JsonParser.object().from(downloader.get(apiUrl,
                    getHeaders(getOriginalUrl())).responseBody());
            if (getLinkHandler().getUrl().contains(GET_SEASON_ARCHIVES_ARCHIVE_BASE_URL)) {
                type = "seasons_archives";
            } else if (getLinkHandler().getUrl().contains(GET_SERIES_BASE_URL)) {
                type = "series";
            } else if (getLinkHandler().getUrl().contains(GET_PARTITION_URL)) {
                type = "partition";
                return;
            }
            data = data.getObject("data");
            if ("seasons_archives".equals(type) && sourceBvid != null
                    && sourceBvid.matches("BV[0-9A-Za-z]+")) {
                try {
                    final String watchResponse = downloader.get(
                            "https://api.bilibili.com/x/web-interface/wbi/view?bvid="
                                    + sourceBvid,
                            getHeaders(getOriginalUrl())).responseBody();
                    final JsonObject watch = JsonParser.object().from(watchResponse)
                            .getObject("data");
                    sourceCollections = BilibiliCollectionParser.parse(watch);
                } catch (IOException | JsonParserException ignored) {
                    sourceCollections = Collections.emptyList();
                }
            }
        } catch (JsonParserException e) {
            throw new RuntimeException(e);
        }
    }

    @Nonnull
    @Override
    public String getName() throws ParsingException {
        final String name = parseQueryParameter(getLinkHandler().getUrl(), "name");
        if (name == null || name.isEmpty()) {
            throw new ParsingException("BiliBili playlist URL has no name");
        }
        return name;
    }

    @Nonnull
    @Override
    public InfoItemsPage<StreamInfoItem> getInitialPage() throws IOException, ExtractionException {
        if (type.equals("partition")) {
            InfoItemsCollector<StreamInfoItem, StreamInfoItemExtractor> collector = new StreamInfoItemsCollector(getServiceId());
            JsonArray relatedArray = data.getArray("data");
            for (int i = 0; i < relatedArray.size(); i++) {
                collector.commit(
                        new BilibiliRelatedInfoItemExtractor(
                                relatedArray.getObject(i), getQueryParameter("bvid"),
                                getQueryParameter("thumbnail"),
                                String.valueOf(i + 1), getUploaderName(), null));
            }
            if (ServiceList.BiliBili.getFilterTypes().contains("playlists")) {
                collector.applyBlocking(ServiceList.BiliBili.getFilterConfig());
            }
            return new InfoItemsPage<>(collector, null);
        }
        if (!sourceCollections.isEmpty()) {
            final StreamInfoItemsCollector collector = new StreamInfoItemsCollector(getServiceId());
            final String uploaderName = getUploaderName();
            final String uploaderId = utils.getMidFromRecordApiUrl(getLinkHandler().getUrl());
            final String uploaderAvatarUrl = getUploaderAvatarUrl();
            for (final StreamCollectionInfo collection : sourceCollections) {
                for (final StreamCollectionInfo.Section section : collection.getSections()) {
                    for (final StreamCollectionInfo.Episode episode : section.getEpisodes()) {
                        collector.commit(new BilibiliCollectionEpisodeInfoItemExtractor(episode,
                                section.getTitle(), uploaderName, uploaderId, uploaderAvatarUrl));
                    }
                }
            }
            if (ServiceList.BiliBili.getFilterTypes().contains("playlists")) {
                collector.applyBlocking(ServiceList.BiliBili.getFilterConfig());
            }
            return new InfoItemsPage<>(collector, null);
        }
        return getPage(new Page(getUrl() + "&username="
                + formatParamWithPercentSpace(getUploaderName()), getDefaultCookies()));
    }

    @Override
    public InfoItemsPage<StreamInfoItem> getPage(Page page) throws IOException, ExtractionException {
        // Partitions have only 1 page
        type = getLinkHandler().getUrl().contains("seasons_archives") ? "seasons_archives" : "archives";
        try {
            if (!(page.getUrl().contains("pn=1") || page.getUrl().contains("page_num=1"))) {
                final String apiUrl = stripSourceBvid(page.getUrl(),
                        getQueryParameter("source_bvid"));
                data = JsonParser.object().from(getDownloader().get(apiUrl,
                        getHeaders(getOriginalUrl())).responseBody()).getObject("data");
            }
        } catch (JsonParserException e) {
            throw new RuntimeException(e);
        }
        JsonArray results = data.getArray("archives");
        if (results.size() == 0) {
            return new InfoItemsPage<>(new StreamInfoItemsCollector(getServiceId()), null);
        }
        final StreamInfoItemsCollector collector = new StreamInfoItemsCollector(getServiceId());
        for (int i = 0; i < results.size(); i++) {
            collector.commit(new BilibiliChannelInfoItemWebAPIExtractor(results.getObject(i), getUploaderName(), getUploaderAvatarUrl()));
        }
        if (ServiceList.BiliBili.getFilterTypes().contains("playlists")) {
            collector.applyBlocking(ServiceList.BiliBili.getFilterConfig());
        }
        return new InfoItemsPage<>(collector, new Page(utils.getNextPageFromCurrentUrl(page.getUrl(), type.equals("seasons_archives") ? "page_num" : "pn", 1), getDefaultCookies()));
    }

    @Override
    public String getUploaderUrl() throws ParsingException {
        if (type.equals("partition")) {
            return getQueryParameter("uploaderUrl");
        }
        return BilibiliChannelLinkHandlerFactory.baseUrl + utils.getMidFromRecordApiUrl(getLinkHandler().getUrl());
    }

    @Override
    public String getUploaderName() throws ParsingException {
        if (type.equals("partition")) {
            return getQueryParameter("uploaderName");
        }
        return getUserInfoLazily().getObject("data").getObject("card").getString("name", "[Unknown]");
    }

    @Override
    public String getUploaderAvatarUrl() throws ParsingException {
        if (type.equals("partition")) {
            return getQueryParameter("uploaderAvatar");
        }
        return getUserInfoLazily().getObject("data").getObject("card").getString("face").replace("http:", "https:");
    }

    @Override
    public boolean isUploaderVerified() throws ParsingException {
        return false;
    }

    @Override
    public long getStreamCount() throws ParsingException {
        if (type.equals("partition")) {
            return data.getArray("data").size();
        }
        if (!sourceCollections.isEmpty()) {
            return sourceCollections.stream()
                    .flatMap(collection -> collection.getSections().stream())
                    .mapToLong(section -> section.getEpisodes().size())
                    .sum();
        }
        return data.getObject("page").getLong("total");
    }

    @Nonnull
    @Override
    public String getThumbnailUrl() throws ParsingException {
        if (type.equals("partition")) {
            return getQueryParameter("thumbnail");
        } else {
            return data.getObject("meta").getString("cover");
        }
    }

    // Get user metadata JSON lazily, without passible NPE.
    private JsonObject getUserInfoLazily() {
        if (userData != null) return userData;
        try {
            userData = JsonParser.object().from(getDownloader().get(getUserInfoRequestUrl(), getHeaders(getOriginalUrl())).responseBody());
        } catch (Exception e) {
            userData = new JsonObject();
        }
        return userData;
    }

    private String getUserInfoRequestUrl() {
        String uid = null;
        // Get from JSON directly
        if (data != null) {
            uid = data.getObject("meta").getString("mid");
        }
        // Use fallback
        if (uid == null) uid = utils.getMidFromRecordApiUrl(getLinkHandler().getUrl());
        return QUERY_USER_INFO_URL + uid;
    }

    private String getQueryParameter(final String name) throws ParsingException {
        return parseQueryParameter(getLinkHandler().getUrl(), name);
    }

    static String parseQueryParameter(final String url, final String name)
            throws ParsingException {
        try {
            return Utils.getQueryValue(Utils.stringToURL(url), name);
        } catch (MalformedURLException e) {
            throw new ParsingException("Could not parse BiliBili playlist URL", e);
        }
    }

    private static String stripSourceBvid(final String url, final String sourceBvid) {
        return sourceBvid == null ? url : url.replace("&source_bvid="
                + formatParamWithPercentSpace(sourceBvid), "");
    }
}
