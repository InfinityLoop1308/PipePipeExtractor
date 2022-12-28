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
import org.schabi.newpipe.extractor.services.bilibili.extractors.BilibiliPlaylistExtrator;
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

import static java.util.Arrays.asList;
import static org.schabi.newpipe.extractor.StreamingService.ServiceInfo.MediaCapability.AUDIO;
import static org.schabi.newpipe.extractor.StreamingService.ServiceInfo.MediaCapability.BULLET_COMMENTS;
import static org.schabi.newpipe.extractor.StreamingService.ServiceInfo.MediaCapability.COMMENTS;
import static org.schabi.newpipe.extractor.StreamingService.ServiceInfo.MediaCapability.LIVE;
import static org.schabi.newpipe.extractor.StreamingService.ServiceInfo.MediaCapability.VIDEO;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class BilibiliService extends StreamingService{
    private WatchDataCache watchDataCache;

    static public Map<String, List<String>> getHeaders(){
        final Map<String, List<String>> headers = new HashMap<>();
        headers.put("Cookie", Collections.singletonList("buvid3=C17989F9-9E34-6949-F6B9-19E02F3DC4B734983infoc;"));
        return headers;
    }

    public BilibiliService(int id){
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
            kioskList.addKioskEntry(kioskFactory, h, "Trending");
            kioskList.addKioskEntry(kioskFactory, h, "Recommend Lives");
            kioskList.setDefaultKiosk("Trending");
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
        return new BilibiliPlaylistExtrator(this, linkHandler);
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
        return new BilibiliCommentsLinkHandlerFactory();
    }

    @Override
    public ListLinkHandlerFactory getBulletCommentsLHFactory(){
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
