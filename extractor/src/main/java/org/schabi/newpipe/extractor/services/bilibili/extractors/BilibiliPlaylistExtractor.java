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
import org.schabi.newpipe.extractor.services.bilibili.linkHandler.BilibiliPlaylistLinkHandlerFactory;
import org.schabi.newpipe.extractor.services.bilibili.utils;
import org.schabi.newpipe.extractor.stream.StreamCollectionInfo;
import org.schabi.newpipe.extractor.stream.StreamInfoItem;
import org.schabi.newpipe.extractor.stream.StreamInfoItemExtractor;
import org.schabi.newpipe.extractor.stream.StreamInfoItemsCollector;
import org.schabi.newpipe.extractor.utils.Utils;

import java.io.IOException;
import java.net.MalformedURLException;
import java.util.Collections;
import java.util.List;

import javax.annotation.Nonnull;

import static org.schabi.newpipe.extractor.services.bilibili.BilibiliService.*;

public class BilibiliPlaylistExtractor extends PlaylistExtractor {
    public JsonObject data;
    public String type;
    private String currentMid = "";
    private String firstPageApiUrl = "";
    private JsonObject userData;
    private List<StreamCollectionInfo> sourceCollections = Collections.emptyList();

    public BilibiliPlaylistExtractor(StreamingService service, ListLinkHandler linkHandler) {
        super(service, linkHandler);
    }

    private String getRefererUrl() {
        try {
            final String originalUrl = getOriginalUrl();
            if (originalUrl != null && (originalUrl.startsWith("http://")
                    || originalUrl.startsWith("https://"))) {
                return originalUrl;
            }
        } catch (ParsingException ignored) {
            // Use the canonical URL below when the original one is not a network URL.
        }
        try {
            final String currentUrl = getUrl();
            if (currentUrl != null && (currentUrl.startsWith("http://")
                    || currentUrl.startsWith("https://"))) {
                return currentUrl;
            }
        } catch (ParsingException ignored) {
            // Fall back to Bilibili's public landing page.
        }
        return WWW_REFERER;
    }

    @Override
    public void onFetchPage(@Nonnull final Downloader downloader)
            throws IOException, ExtractionException {
        final String originalUrl = getLinkHandler().getOriginalUrl();
        BilibiliPlaylistLinkHandlerFactory.PlaylistTarget target;
        try {
            target = BilibiliPlaylistLinkHandlerFactory.parseTarget(getLinkHandler().getUrl());
        } catch (ParsingException e) {
            target = BilibiliPlaylistLinkHandlerFactory.parseTarget(originalUrl);
        }
        type = target.type;
        currentMid = target.mid;

        if ("partition".equals(type)) {
            try {
                data = JsonParser.object().from(downloader.get(getLinkHandler().getUrl(),
                        getHeaders(getRefererUrl())).responseBody());
            } catch (JsonParserException e) {
                throw new ExtractionException("Failed to parse partition response", e);
            }
            return;
        }

        final String midParam = currentMid.isEmpty() ? "" : "mid=" + currentMid + "&";
        if ("seasons_archives".equals(type)) {
            firstPageApiUrl = "https://api.bilibili.com/x/polymer/web-space/seasons_archives_list?"
                    + midParam + "season_id=" + target.id
                    + "&sort_reverse=false&page_num=1&page_size=30";
        } else {
            firstPageApiUrl = "https://api.bilibili.com/x/series/archives?"
                    + midParam + "series_id=" + target.id
                    + "&only_normal=true&sort=desc&pn=1&ps=30";
        }

        try {
            final String responseBody = downloader.get(firstPageApiUrl,
                    getHeaders(getRefererUrl())).responseBody();
            final JsonObject root = JsonParser.object().from(responseBody);
            data = root.getObject("data");
            if (data != null && data.has("meta") && data.getObject("meta") != null) {
                final long mid = data.getObject("meta").getLong("mid");
                if (mid > 0) {
                    currentMid = String.valueOf(mid);
                }
            }
        } catch (JsonParserException e) {
            throw new ExtractionException("Failed to parse playlist response", e);
        }

        // The playlist link generated from a stream keeps source_bvid only in its original URL.
        final String sourceBvid = getQueryParameter("source_bvid");
        if ("seasons_archives".equals(type) && sourceBvid != null
                && sourceBvid.matches("BV[0-9A-Za-z]+")) {
            try {
                final String watchResponse = downloader.get(
                        "https://api.bilibili.com/x/web-interface/wbi/view?bvid=" + sourceBvid,
                        getHeaders(getRefererUrl())).responseBody();
                final JsonObject watch = JsonParser.object().from(watchResponse)
                        .getObject("data");
                sourceCollections = BilibiliCollectionParser.parse(watch);
            } catch (IOException | JsonParserException ignored) {
                sourceCollections = Collections.emptyList();
            }
        }
    }

    @Nonnull
    @Override
    public String getName() throws ParsingException {
        if ("partition".equals(type)) {
            final String name = getQueryParameter("name");
            return name == null || name.isEmpty() ? "Partition" : name;
        }
        if (data != null && data.has("meta") && data.getObject("meta") != null) {
            final JsonObject meta = data.getObject("meta");
            if (meta.has("name") && !meta.getString("name", "").isEmpty()) {
                return meta.getString("name");
            }
            if (meta.has("title") && !meta.getString("title", "").isEmpty()) {
                return meta.getString("title");
            }
        }
        final String name = getQueryParameter("name");
        return name == null || name.isEmpty() ? "Bilibili Playlist" : name;
    }

    @Nonnull
    @Override
    public InfoItemsPage<StreamInfoItem> getInitialPage()
            throws IOException, ExtractionException {
        if ("partition".equals(type)) {
            final InfoItemsCollector<StreamInfoItem, StreamInfoItemExtractor> collector =
                    new StreamInfoItemsCollector(getServiceId());
            final JsonArray relatedArray = data == null ? null : data.getArray("data");
            for (int i = 0; relatedArray != null && i < relatedArray.size(); i++) {
                collector.commit(new BilibiliRelatedInfoItemExtractor(
                        relatedArray.getObject(i), getQueryParameter("bvid"),
                        getQueryParameter("thumbnail"), String.valueOf(i + 1),
                        getUploaderName(), null));
            }
            if (ServiceList.BiliBili.getFilterTypes().contains("playlists")) {
                collector.applyBlocking(ServiceList.BiliBili.getFilterConfig());
            }
            return new InfoItemsPage<>(collector, null);
        }

        if (!sourceCollections.isEmpty()) {
            final StreamInfoItemsCollector collector = new StreamInfoItemsCollector(getServiceId());
            final String uploaderName = getUploaderName();
            final String uploaderId = currentMid.isEmpty()
                    ? utils.getMidFromRecordApiUrl(getLinkHandler().getOriginalUrl())
                    : currentMid;
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

        return getPage(new Page(firstPageApiUrl, getDefaultCookies()));
    }

    @Override
    public InfoItemsPage<StreamInfoItem> getPage(final Page page)
            throws IOException, ExtractionException {
        JsonObject pageData = data;
        if (!"partition".equals(type) && !page.getUrl().equals(firstPageApiUrl)) {
            try {
                pageData = JsonParser.object().from(getDownloader().get(page.getUrl(),
                        getHeaders(getRefererUrl())).responseBody()).getObject("data");
            } catch (JsonParserException e) {
                throw new ExtractionException("Failed to parse playlist page response", e);
            }
        }
        if (pageData == null) {
            return new InfoItemsPage<>(new StreamInfoItemsCollector(getServiceId()), null);
        }

        final JsonArray results = pageData.getArray("archives");
        if (results == null || results.isEmpty()) {
            return new InfoItemsPage<>(new StreamInfoItemsCollector(getServiceId()), null);
        }
        final StreamInfoItemsCollector collector = new StreamInfoItemsCollector(getServiceId());
        final String uploaderName = getUploaderName();
        final String uploaderAvatar = getUploaderAvatarUrl();
        for (int i = 0; i < results.size(); i++) {
            collector.commit(new BilibiliChannelInfoItemWebAPIExtractor(
                    results.getObject(i), uploaderName, uploaderAvatar));
        }
        if (ServiceList.BiliBili.getFilterTypes().contains("playlists")) {
            collector.applyBlocking(ServiceList.BiliBili.getFilterConfig());
        }

        final String pageParam = "seasons_archives".equals(type) ? "page_num" : "pn";
        int currentPageNum = 1;
        if (page.getUrl().contains(pageParam + "=")) {
            try {
                currentPageNum = Integer.parseInt(
                        page.getUrl().split(pageParam + "=")[1].split("&")[0]);
            } catch (NumberFormatException ignored) {
                // Keep the first-page default for malformed pagination links.
            }
        }
        final int pageSize = 30;
        boolean hasMore = results.size() >= pageSize;
        final long total = getStreamCount();
        if (total > 0) {
            hasMore = (long) currentPageNum * pageSize < total;
        }
        final Page nextPage = hasMore
                ? new Page(utils.getNextPageFromCurrentUrl(page.getUrl(), pageParam, 1),
                getDefaultCookies())
                : null;
        return new InfoItemsPage<>(collector, nextPage);
    }

    @Override
    public String getUploaderUrl() throws ParsingException {
        if ("partition".equals(type)) {
            return getQueryParameter("uploaderUrl");
        }
        String uid = null;
        if (data != null && data.has("meta") && data.getObject("meta") != null) {
            final JsonObject meta = data.getObject("meta");
            final long mid = meta.getLong("mid");
            uid = mid > 0 ? String.valueOf(mid) : meta.getString("mid");
        }
        if (uid == null || uid.isEmpty()) {
            uid = currentMid;
        }
        if (uid == null || uid.isEmpty()) {
            try {
                uid = BilibiliPlaylistLinkHandlerFactory.parseTarget(
                        getLinkHandler().getUrl()).mid;
            } catch (ParsingException ignored) {
                // Fall back to an API URL containing the uploader ID.
            }
        }
        if (uid == null || uid.isEmpty()) {
            uid = getQueryParameter("mid");
        }
        if (uid == null) {
            uid = "";
        }
        return BilibiliChannelLinkHandlerFactory.baseUrl + uid;
    }

    @Override
    public String getUploaderName() throws ParsingException {
        if ("partition".equals(type)) {
            final String name = getQueryParameter("uploaderName");
            return name == null || name.isEmpty() ? "[Unknown]" : name;
        }
        final JsonObject userInfo = getUserInfoLazily();
        if (userInfo != null && userInfo.has("data")
                && userInfo.getObject("data") != null) {
            final JsonObject userData = userInfo.getObject("data");
            if (userData.has("card") && userData.getObject("card") != null) {
                return userData.getObject("card").getString("name", "[Unknown]");
            }
        }
        return "[Unknown]";
    }

    @Override
    public String getUploaderAvatarUrl() throws ParsingException {
        if ("partition".equals(type)) {
            return getQueryParameter("uploaderAvatar");
        }
        final JsonObject userInfo = getUserInfoLazily();
        if (userInfo != null && userInfo.has("data")
                && userInfo.getObject("data") != null) {
            final JsonObject userData = userInfo.getObject("data");
            if (userData.has("card") && userData.getObject("card") != null) {
                final String face = userData.getObject("card").getString("face");
                if (face != null) {
                    return face.replace("http:", "https:");
                }
            }
        }
        return "";
    }

    @Override
    public boolean isUploaderVerified() {
        return false;
    }

    @Override
    public long getStreamCount() throws ParsingException {
        if ("partition".equals(type)) {
            final JsonArray dataArray = data == null ? null : data.getArray("data");
            return dataArray == null ? 0 : dataArray.size();
        }
        if (!sourceCollections.isEmpty()) {
            return sourceCollections.stream()
                    .flatMap(collection -> collection.getSections().stream())
                    .mapToLong(section -> section.getEpisodes().size())
                    .sum();
        }
        if (data != null && data.has("page") && data.getObject("page") != null) {
            final long total = data.getObject("page").getLong("total");
            if (total > 0) {
                return total;
            }
        }
        if (data != null && data.has("meta") && data.getObject("meta") != null) {
            return data.getObject("meta").getLong("total");
        }
        return 0;
    }

    @Nonnull
    @Override
    public String getThumbnailUrl() throws ParsingException {
        if ("partition".equals(type)) {
            final String thumbnail = getQueryParameter("thumbnail");
            return thumbnail == null ? "" : thumbnail;
        }
        if (data != null && data.has("meta") && data.getObject("meta") != null) {
            final String cover = data.getObject("meta").getString("cover");
            if (cover != null && !cover.isEmpty()) {
                return cover.replace("http:", "https:");
            }
        }
        if (data != null && data.has("archives")) {
            final JsonArray archives = data.getArray("archives");
            if (archives != null && !archives.isEmpty()) {
                final String pic = archives.getObject(0).getString("pic");
                if (pic != null && !pic.isEmpty()) {
                    return pic.replace("http:", "https:");
                }
            }
        }
        return "";
    }

    private JsonObject getUserInfoLazily() {
        if (userData != null) {
            return userData;
        }
        try {
            userData = JsonParser.object().from(getDownloader().get(getUserInfoRequestUrl(),
                    getHeaders(getRefererUrl())).responseBody());
        } catch (Exception ignored) {
            userData = new JsonObject();
        }
        return userData;
    }

    private String getUserInfoRequestUrl() {
        String uid = null;
        if (data != null && data.has("meta") && data.getObject("meta") != null) {
            final JsonObject meta = data.getObject("meta");
            final long mid = meta.getLong("mid");
            uid = mid > 0 ? String.valueOf(mid) : meta.getString("mid");
        }
        if (uid == null || uid.isEmpty()) {
            uid = currentMid;
        }
        if (uid == null || uid.isEmpty()) {
            try {
                uid = BilibiliPlaylistLinkHandlerFactory.parseTarget(
                        getLinkHandler().getUrl()).mid;
            } catch (ParsingException ignored) {
                uid = "";
            }
        }
        return QUERY_USER_INFO_URL + (uid == null ? "" : uid);
    }

    private String getQueryParameter(final String name) throws ParsingException {
        final String originalUrl = getLinkHandler().getOriginalUrl();
        if (originalUrl != null && originalUrl.contains("?")) {
            try {
                return parseQueryParameter(originalUrl, name);
            } catch (ParsingException ignored) {
                // Try the normalized URL below.
            }
        }
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
}
