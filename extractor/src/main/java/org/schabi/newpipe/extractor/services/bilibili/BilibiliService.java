package org.schabi.newpipe.extractor.services.bilibili;

import org.schabi.newpipe.extractor.StreamingService;
import org.schabi.newpipe.extractor.bulletComments.BulletCommentsExtractor;
import org.schabi.newpipe.extractor.channel.ChannelExtractor;
import org.schabi.newpipe.extractor.channel.ChannelTabExtractor;
import org.schabi.newpipe.extractor.comments.CommentsExtractor;
import org.schabi.newpipe.extractor.exceptions.ExtractionException;
import org.schabi.newpipe.extractor.kiosk.KioskList;
import org.schabi.newpipe.extractor.linkhandler.LinkHandler;
import org.schabi.newpipe.extractor.linkhandler.LinkHandlerFactory;
import org.schabi.newpipe.extractor.linkhandler.ListLinkHandler;
import org.schabi.newpipe.extractor.linkhandler.ListLinkHandlerFactory;
import org.schabi.newpipe.extractor.linkhandler.SearchQueryHandler;
import org.schabi.newpipe.extractor.linkhandler.SearchQueryHandlerFactory;
import org.schabi.newpipe.extractor.playlist.PlaylistExtractor;
import org.schabi.newpipe.extractor.search.SearchExtractor;
import org.schabi.newpipe.extractor.services.bilibili.extractors.BilibiliBulletCommentsExtractor;
import org.schabi.newpipe.extractor.services.bilibili.extractors.BilibiliChannelExtractor;
import org.schabi.newpipe.extractor.services.bilibili.extractors.BilibiliChannelTabExtractor;
import org.schabi.newpipe.extractor.services.bilibili.extractors.BilibiliCommentExtractor;
import org.schabi.newpipe.extractor.services.bilibili.extractors.BilibiliFeedExtractor;
import org.schabi.newpipe.extractor.services.bilibili.extractors.BilibiliPlaylistExtractor;
import org.schabi.newpipe.extractor.services.bilibili.extractors.BilibiliSearchExtractor;
import org.schabi.newpipe.extractor.services.bilibili.extractors.BilibiliSuggestionExtractor;
import org.schabi.newpipe.extractor.services.bilibili.extractors.BillibiliStreamExtractor;
import org.schabi.newpipe.extractor.services.bilibili.linkHandler.BilibiliBulletCommentsLinkHandlerFactory;
import org.schabi.newpipe.extractor.services.bilibili.linkHandler.BilibiliChannelLinkHandlerFactory;
import org.schabi.newpipe.extractor.services.bilibili.linkHandler.BilibiliCommentsLinkHandlerFactory;
import org.schabi.newpipe.extractor.services.bilibili.linkHandler.BilibiliFeedLinkHandlerFactory;
import org.schabi.newpipe.extractor.services.bilibili.linkHandler.BilibiliPlaylistLinkHandlerFactory;
import org.schabi.newpipe.extractor.services.bilibili.linkHandler.BilibiliSearchQueryHandlerFactory;
import org.schabi.newpipe.extractor.services.bilibili.linkHandler.BilibiliStreamLinkHandlerFactory;
import org.schabi.newpipe.extractor.stream.StreamExtractor;
import org.schabi.newpipe.extractor.subscription.SubscriptionExtractor;
import org.schabi.newpipe.extractor.suggestion.SuggestionExtractor;

import static org.schabi.newpipe.extractor.StreamingService.ServiceInfo.MediaCapability.BULLET_COMMENTS;
import static org.schabi.newpipe.extractor.StreamingService.ServiceInfo.MediaCapability.COMMENTS;
import static org.schabi.newpipe.extractor.StreamingService.ServiceInfo.MediaCapability.VIDEO;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class BilibiliService extends StreamingService {
    private final WatchDataCache watchDataCache;

    public static String FREE_VIDEO_BASE_URL = "https://api.bilibili.com/x/player/playurl";
    public static String PAID_VIDEO_BASE_URL = "https://api.bilibili.com/pgc/player/web/playurl";
    public static String LIVE_BASE_URL = "live.bilibili.com";
    public static String QUERY_VIDEO_BULLET_COMMENTS_URL = "https://api.bilibili.com/x/v1/dm/list.so?oid=";
    public static String QUERY_USER_INFO_URL = "https://api.bilibili.com/x/web-interface/card?photo=true&mid=";
    public static String QUERY_LIVEROOM_STATUS_URL = "https://api.live.bilibili.com/room/v1/Room/get_status_info_by_uids?uids[]=";
    public static String GET_SEASON_ARCHIVES_LIST_BASE_URL = "https://api.bilibili.com/x/polymer/space/seasons_archives_list?mid=%s&season_id=%s&sort_reverse=false&name=%s&page_num=1&page_size=30";
    public static String GET_SERIES_BASE_URL = "https://api.bilibili.com/x/series/archives?mid=%s&series_id=%s&only_normal=true&sort=desc&name=%s&pn=1&ps=30";
    public static String GET_SUGGESTION_URL = "https://s.search.bilibili.com/main/suggest?term=";
    public static String COMMENT_REPLIES_URL = "https://api.bilibili.com/x/v2/reply/reply?type=1&pn=1&ps=20&oid=";

    static public Map<String, List<String>> getHeaders() {
        final Map<String, List<String>> headers = new HashMap<>();
        headers.put("Cookie", Collections.singletonList("buvid3=C17989F9-9E34-6949-F6B9-19E02F3DC4B734983infoc;"));
        return headers;
    }

    static public Map<String, String> getWebSocketHeaders() {
        Map<String, String> httpHeaders = new HashMap<String, String>();
        httpHeaders.put("User-Agent", "Mozilla/5.0 (X11; Linux x86_64; rv:106.0) Gecko/20100101 Firefox/106.0");
        httpHeaders.put("Accept", "*/*");
        httpHeaders.put("Accept-Language", "en-US,en;q=0.5");
        httpHeaders.put("Accept-Encoding", "gzip, deflate, br");
        httpHeaders.put("Sec-WebSocket-Version", "13");
        httpHeaders.put("Origin", "https://www.piesocket.com");
        httpHeaders.put("Sec-WebSocket-Extensions", "permessage-deflate");
        httpHeaders.put("Sec-WebSocket-Key", "9cwI/6tCIyNBM4XSsi3jMA==");
        httpHeaders.put("DNT", "1");
        httpHeaders.put("Connection", "keep-alive, Upgrade");
        httpHeaders.put("Sec-Fetch-Dest", "websocket");
        httpHeaders.put("Sec-Fetch-Mode", "websocket");
        httpHeaders.put("Sec-Fetch-Site", "cross-site");
        httpHeaders.put("Pragma", "no-cache");
        httpHeaders.put("Cache-Control", "no-cache");
        httpHeaders.put("Upgrade", "websocket");
        return httpHeaders;
    }

    static public String getResolution(int code) {
        switch (code) {
            case 127:
                return "8K 超高清";
            case 126:
                return "杜比视界";
            case 125:
                return "HDR 真彩色";
            case 120:
                return "4K 超清";
            case 116:
                return "1080P60 高帧率";
            case 112:
                return "1080P+ 高码率";
            case 80:
                return "1080P 高清";
            case 74:
                return "720P60 高帧率";
            case 64:
                return "720P 高清";
            case 32:
                return "480P 清晰";
            case 16:
                return "360P 流畅";
            case 6:
                return "240P 极速";
            default:
                return "Unknown resolution";
        }
    }

    public BilibiliService(int id) {
        super(id, "BiliBili", Arrays.asList(VIDEO, COMMENTS, BULLET_COMMENTS));
        watchDataCache = new WatchDataCache();
    }

    @Override
    public String getBaseUrl() {
        return "https://bilibili.com";
    }

    @Override
    public LinkHandlerFactory getStreamLHFactory() {
        return new BilibiliStreamLinkHandlerFactory();
    }

    @Override
    public ListLinkHandlerFactory getChannelLHFactory() {
        return new BilibiliChannelLinkHandlerFactory();
    }

    @Override
    public ListLinkHandlerFactory getChannelTabLHFactory() {
        return null;
    }

    @Override
    public ListLinkHandlerFactory getPlaylistLHFactory() {
        return new BilibiliPlaylistLinkHandlerFactory();
    }

    @Override
    public SearchQueryHandlerFactory getSearchQHFactory() {
        return new BilibiliSearchQueryHandlerFactory();
    }

    @Override
    public SearchExtractor getSearchExtractor(SearchQueryHandler queryHandler) {
        return new BilibiliSearchExtractor(this, queryHandler);
    }

    @Override
    public SuggestionExtractor getSuggestionExtractor() {
        return new BilibiliSuggestionExtractor(this);
    }

    @Override
    public SubscriptionExtractor getSubscriptionExtractor() {
        return null;
    }

    @Override
    public KioskList getKioskList() throws ExtractionException {
        final KioskList kioskList = new KioskList(this);
        final KioskList.KioskExtractorFactory kioskFactory = (streamingService, url, id) ->
                new BilibiliFeedExtractor(this, new BilibiliFeedLinkHandlerFactory().fromUrl(url), id);
        final BilibiliFeedLinkHandlerFactory h = new BilibiliFeedLinkHandlerFactory();
        try {
            kioskList.addKioskEntry(kioskFactory, h, "Recommended Videos");
            kioskList.addKioskEntry(kioskFactory, h, "Recommended Lives");
            kioskList.addKioskEntry(kioskFactory, h, "Top 100");
            kioskList.setDefaultKiosk("Recommended Videos");
        } catch (Exception e) {
            e.printStackTrace();
        }
        return kioskList;
    }

    @Override
    public ChannelExtractor getChannelExtractor(ListLinkHandler linkHandler) throws ExtractionException {
        return new BilibiliChannelExtractor(this, linkHandler);
    }

    @Override
    public ChannelTabExtractor getChannelTabExtractor(ListLinkHandler linkHandler) throws ExtractionException {
        return new BilibiliChannelTabExtractor(this, linkHandler);
    }

    @Override
    public PlaylistExtractor getPlaylistExtractor(ListLinkHandler linkHandler) throws ExtractionException {
        return new BilibiliPlaylistExtractor(this, linkHandler);
    }

    @Override
    public StreamExtractor getStreamExtractor(LinkHandler linkHandler) throws ExtractionException {
        return new BillibiliStreamExtractor(this, linkHandler, watchDataCache);
    }

    @Override
    public CommentsExtractor getCommentsExtractor(ListLinkHandler linkHandler) throws ExtractionException {
        return new BilibiliCommentExtractor(this, linkHandler);
    }

    @Override
    public ListLinkHandlerFactory getCommentsLHFactory() {
        return new BilibiliCommentsLinkHandlerFactory(watchDataCache);
    }

    @Override
    public ListLinkHandlerFactory getBulletCommentsLHFactory() {
        return new BilibiliBulletCommentsLinkHandlerFactory();
    }

    @Override
    public BulletCommentsExtractor getBulletCommentsExtractor(ListLinkHandler linkHandler) throws ExtractionException {
        return new BilibiliBulletCommentsExtractor(this, linkHandler, watchDataCache);
    }

    @Override
    public BulletCommentsExtractor getBulletCommentsExtractor(final String url)
            throws ExtractionException {
        return getBulletCommentsExtractor(getBulletCommentsLHFactory().fromUrl(url));
    }
}
