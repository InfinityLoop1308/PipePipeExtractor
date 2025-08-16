package org.schabi.newpipe.extractor.services.bilibili.extractors;

import static org.schabi.newpipe.extractor.services.bilibili.BilibiliService.*;
import static org.schabi.newpipe.extractor.services.bilibili.utils.buildUserVideosUrlWebAPI;
import static org.schabi.newpipe.extractor.services.bilibili.utils.buildUserVideosUrlClientAPI;
import static org.schabi.newpipe.extractor.services.bilibili.utils.getNextPageFromCurrentUrl;
import static org.schabi.newpipe.extractor.services.bilibili.utils.isClientAPIMode;
import static org.schabi.newpipe.extractor.services.bilibili.utils.requestUserSpaceResponse;

import com.grack.nanojson.JsonArray;
import com.grack.nanojson.JsonObject;

import org.schabi.newpipe.extractor.Page;
import org.schabi.newpipe.extractor.ServiceList;
import org.schabi.newpipe.extractor.StreamingService;
import org.schabi.newpipe.extractor.channel.ChannelExtractor;
import org.schabi.newpipe.extractor.downloader.Downloader;
import org.schabi.newpipe.extractor.exceptions.ExtractionException;
import org.schabi.newpipe.extractor.exceptions.ParsingException;
import org.schabi.newpipe.extractor.exceptions.ReCaptchaException;
import org.schabi.newpipe.extractor.linkhandler.ChannelTabs;
import org.schabi.newpipe.extractor.linkhandler.ListLinkHandler;
import org.schabi.newpipe.extractor.search.filter.Filter;
import org.schabi.newpipe.extractor.search.filter.FilterItem;
import org.schabi.newpipe.extractor.services.bilibili.BilibiliService;
import org.schabi.newpipe.extractor.services.bilibili.utils;
import org.schabi.newpipe.extractor.stream.StreamInfoItem;
import org.schabi.newpipe.extractor.stream.StreamInfoItemsCollector;

import java.io.IOException;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import javax.annotation.Nonnull;

public class BilibiliChannelExtractor extends ChannelExtractor {

    JsonObject userInfoData = new JsonObject();
    JsonObject userLiveData = new JsonObject();

    //region User Video Impl

    private final ClientUserVideoImpl clientUserVideoImpl = new ClientUserVideoImpl();
    private final WebUserVideoImpl webUserVideoImpl = new WebUserVideoImpl();

    private UserVideoImpl getVideoImpl() {
        return isClientAPIMode ? clientUserVideoImpl : webUserVideoImpl;
    }
    //endregion

    public BilibiliChannelExtractor(StreamingService service, ListLinkHandler linkHandler) {
        super(service, linkHandler);
    }

    @Override
    public void onFetchPage(@Nonnull Downloader downloader) throws IOException, ExtractionException {
        Map<String, List<String>> headers = getHeaders(getOriginalUrl());
        String id = getId();

        getVideoImpl().onFetchPage(downloader, id, getUrl());

        userInfoData = requestUserSpaceResponse(downloader, QUERY_USER_INFO_URL + id, headers);

        userLiveData = requestUserSpaceResponse(downloader, QUERY_LIVEROOM_STATUS_URL + id, headers);
    }

    @Nonnull
    @Override
    public String getName() throws ParsingException {
        return userInfoData.getObject("data").getObject("card").getString("name");
    }

    @Nonnull
    @Override
    public InfoItemsPage<StreamInfoItem> getInitialPage() throws IOException, ExtractionException {

        final StreamInfoItemsCollector collector = new StreamInfoItemsCollector(getServiceId());
        if (userLiveData.getObject("data").getObject(getId()).getInt("live_status") != 0) {
            collector.commit(new BilibiliLiveInfoItemExtractor(userLiveData.getObject("data").getObject(getId()), 1));
        }
        UserVideoImpl videoImpl = getVideoImpl();
        boolean hasVideos = videoImpl.getInitialPage(collector, this);
        Page nextPage = null;
        if (hasVideos) nextPage = new Page(getNextPageFromCurrentUrl(
                getUrl(), "pn", 1, true, "1", "?"), String.valueOf(videoImpl.lastVideo()), null, BilibiliService.getDefaultCookies(), null
        );
        if (ServiceList.BiliBili.getFilterTypes().contains("channels")) {
            collector.applyBlocking(ServiceList.BiliBili.getFilterConfig());
        }
        return new InfoItemsPage<>(collector, nextPage);
    }

    @Override
    public InfoItemsPage<StreamInfoItem> getPage(Page page) throws IOException, ExtractionException {
        Map<String, List<String>> headers = getHeaders(getOriginalUrl());
        String id = getId();
        Downloader downloader = getDownloader();

        userInfoData = requestUserSpaceResponse(downloader, QUERY_USER_INFO_URL + id, headers);

        final StreamInfoItemsCollector collector = new StreamInfoItemsCollector(getServiceId());
        UserVideoImpl videoImpl = getVideoImpl();
        boolean hasVideos = videoImpl.getPage(page, collector, getDownloader(), this, getId());
        Page nextPage = null;
        if (hasVideos) nextPage = new Page(
                getNextPageFromCurrentUrl(page.getUrl(), "pn", 1), String.valueOf(videoImpl.lastVideo()), null, BilibiliService.getDefaultCookies(), null
        );
        if (ServiceList.BiliBili.getFilterTypes().contains("channels")) {
            collector.applyBlocking(ServiceList.BiliBili.getFilterConfig());
        }
        return new InfoItemsPage<>(collector, nextPage);
    }

    @Override
    public String getAvatarUrl() throws ParsingException {
        return userInfoData.getObject("data").getObject("card").getString("face").replace("http:", "https:");
    }

    @Override
    public String getBannerUrl() throws ParsingException {
        return userInfoData.getObject("data").getObject("space").getString("l_img").replace("http:", "https:");
    }

    @Override
    public long getSubscriberCount() throws ParsingException {
        return userInfoData.getObject("data").getObject("card").getLong("fans");
    }

    @Override
    public String getDescription() throws ParsingException {
        return userInfoData.getObject("data").getObject("card").getString("sign");
    }

    @Nonnull
    @Override
    public String getUrl() throws ParsingException {
        return super.getUrl();
    }

    @Nonnull
    @Override
    public List<ListLinkHandler> getTabs() throws ParsingException {
        String url = GET_SEASON_ARCHIVES_LIST_BASE_URL + "?mid=" + getLinkHandler().getId() + "&page_num=1&page_size=10";
        return Arrays.asList(
                new ListLinkHandler(getUrl(), url, getLinkHandler().getId(),
                        Collections.singletonList(new FilterItem(Filter.ITEM_IDENTIFIER_UNKNOWN, ChannelTabs.VIDEOS)), null),
                new ListLinkHandler(url, url, getLinkHandler().getId(),
                        Collections.singletonList(new FilterItem(Filter.ITEM_IDENTIFIER_UNKNOWN, ChannelTabs.PLAYLISTS)), null)
        );
    }


    /**
     * Abstract Implementation of extracting User Videos
     */
    public interface UserVideoImpl {

        /**
         * Collect videos from results json
         *
         * @param collector collector to be filled in
         * @param extractor current extractor
         * @param results   Json Array of user videos
         * @throws ParsingException if failed to parse
         */
        void collectVideos(
                StreamInfoItemsCollector collector,
                ChannelExtractor extractor,
                JsonArray results
        ) throws ParsingException;

        void onFetchPage(
                @Nonnull Downloader downloader,
                String id,
                String url
        ) throws IOException, ExtractionException;

        // return true if it contains videos
        boolean getInitialPage(
                @Nonnull StreamInfoItemsCollector collector,
                @Nonnull ChannelExtractor extractor
        ) throws IOException, ExtractionException;

        // return true if it contains videos
        boolean getPage(
                @Nonnull Page page,
                @Nonnull StreamInfoItemsCollector collector,
                @Nonnull Downloader downloader,
                @Nonnull ChannelExtractor extractor,
                @Nonnull String id
        ) throws IOException, ExtractionException;

        /**
         * last video of current page
         *
         * @return av
         */
        long lastVideo();
    }

    /**
     * Extracting from BiliBili Client API
     */
    public static class ClientUserVideoImpl implements UserVideoImpl {

        public JsonObject userVideoData = new JsonObject();

        private JsonArray getVideosArray() {
            return userVideoData.getObject("data").getArray("item");
        }

        private void fetchViaAPI(Downloader downloader, String id, long lastVideoAid)
                throws ParsingException, IOException, ReCaptchaException {
            Map<String, List<String>> headers = getHeaders(SPACE_REFERER);
            userVideoData = requestUserSpaceResponse(downloader, buildUserVideosUrlClientAPI(id, lastVideoAid), headers);
        }

        @Override
        public void collectVideos(
                StreamInfoItemsCollector collector,
                ChannelExtractor extractor,
                JsonArray results
        ) throws ParsingException {
            for (int i = 0; i < results.size(); i++) {
                collector.commit(new BilibiliChannelInfoItemClientAPIExtractor(results.getObject(i), extractor.getName(), extractor.getAvatarUrl()));
            }
        }

        @Override
        public void onFetchPage(@Nonnull Downloader downloader, String id, String url) throws IOException, ExtractionException {
            fetchViaAPI(downloader, id, 0);
        }

        @Override
        public boolean getInitialPage(
                @Nonnull StreamInfoItemsCollector collector,
                @Nonnull ChannelExtractor extractor
        ) throws IOException, ExtractionException {

            JsonArray videosArray = getVideosArray();

            if (!videosArray.isEmpty()) {
                collectVideos(collector, extractor, videosArray);
                return true;
            } else {
                return false;
            }
        }

        @Override
        public boolean getPage(
                @Nonnull Page page,
                @Nonnull StreamInfoItemsCollector collector,
                @Nonnull Downloader downloader,
                @Nonnull ChannelExtractor extractor,
                @Nonnull String id
        ) throws IOException, ExtractionException {

            fetchViaAPI(downloader, id, Long.parseLong(page.getId()));

            JsonArray videosArray = getVideosArray();

            if (!videosArray.isEmpty()) {
                collectVideos(collector, extractor, videosArray);
                return true;
            } else {
                return false;
            }
        }

        @Override
        public long lastVideo() {
            JsonArray videosArray = getVideosArray();
            if (!videosArray.isEmpty()) {
                JsonObject last = videosArray.getObject(videosArray.size() - 1);
                String type = last.getString("goto");
                String num = last.getString("param");
                if ("av".equals(type) && num != null) {
                    return Long.parseLong(num);
                } else {
                    String bv = last.getString("bvid");
                    return utils.bv2av(bv);
                }
            }
            return 0;
        }
    }

    /**
     * Extracting from BiliBili Web API
     */
    public static class WebUserVideoImpl implements UserVideoImpl {

        JsonObject userVideoData = new JsonObject();

        private JsonArray getVideosArray() {
            return userVideoData.getObject("data").getObject("list").getArray("vlist");
        }

        private void fetchViaAPI(Downloader downloader, String id, String currentUrl)
                throws ParsingException, IOException, ReCaptchaException {
            Map<String, List<String>> headers = getHeaders(currentUrl);
            userVideoData = requestUserSpaceResponse(downloader, buildUserVideosUrlWebAPI(currentUrl, id), headers);
        }

        @Override
        public void collectVideos(
                StreamInfoItemsCollector collector,
                ChannelExtractor extractor,
                JsonArray results
        ) throws ParsingException {
            for (int i = 0; i < results.size(); i++) {
                collector.commit(new BilibiliChannelInfoItemWebAPIExtractor(results.getObject(i), extractor.getName(), extractor.getAvatarUrl()));
            }
        }

        @Override
        public void onFetchPage(@Nonnull Downloader downloader, String id, String url) throws IOException, ExtractionException {
            fetchViaAPI(downloader, id, url);
        }

        @Override
        public boolean getInitialPage(
                @Nonnull StreamInfoItemsCollector collector,
                @Nonnull ChannelExtractor extractor
        ) throws IOException, ExtractionException {

            JsonArray videos = getVideosArray();

            if (!videos.isEmpty()) {
                collectVideos(collector, extractor, videos);
                return true;
            } else {
                return false;
            }
        }

        @Override
        public boolean getPage(
                @Nonnull Page page,
                @Nonnull StreamInfoItemsCollector collector,
                @Nonnull Downloader downloader,
                @Nonnull ChannelExtractor extractor,
                @Nonnull String id
        ) throws IOException, ExtractionException {

            fetchViaAPI(downloader, id, page.getUrl());

            JsonArray videosArray = getVideosArray();

            if (!videosArray.isEmpty()) {
                collectVideos(collector, extractor, videosArray);
                return true;
            } else {
                return false;
            }
        }

        @Override
        public long lastVideo() {
            JsonArray videos = getVideosArray();
            if (!videos.isEmpty()) {
                JsonObject last = videos.getObject(videos.size() - 1);
                long aid = last.getLong("aid", 0);
                String bvid = last.getString("bvid");
                if (aid > 0) {
                    return aid;
                } else {
                    return utils.bv2av(bvid);
                }
            }
            return 0;
        }
    }


}
