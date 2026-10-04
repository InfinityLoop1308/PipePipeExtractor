package org.schabi.newpipe.extractor.services.twitch;

import static org.schabi.newpipe.extractor.StreamingService.ServiceInfo.MediaCapability.LIVE;
import static org.schabi.newpipe.extractor.StreamingService.ServiceInfo.MediaCapability.VIDEO;

import org.schabi.newpipe.extractor.StreamingService;
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
import org.schabi.newpipe.extractor.services.twitch.api.TwitchApiClient;
import org.schabi.newpipe.extractor.services.twitch.api.TwitchApiSettings;
import org.schabi.newpipe.extractor.services.twitch.extractors.TwitchChannelClipExtractor;
import org.schabi.newpipe.extractor.services.twitch.extractors.TwitchChannelExtractor;
import org.schabi.newpipe.extractor.services.twitch.extractors.TwitchChannelStreamExtractor;
import org.schabi.newpipe.extractor.services.twitch.extractors.TwitchChannelVodExtractor;
import org.schabi.newpipe.extractor.services.twitch.extractors.TwitchClipExtractor;
import org.schabi.newpipe.extractor.services.twitch.extractors.TwitchPlaylistExtractor;
import org.schabi.newpipe.extractor.services.twitch.extractors.TwitchSearchExtractor;
import org.schabi.newpipe.extractor.services.twitch.extractors.TwitchStreamExtractor;
import org.schabi.newpipe.extractor.services.twitch.extractors.TwitchVodExtractor;
import org.schabi.newpipe.extractor.services.twitch.kiosks.TwitchLiveKiosk;
import org.schabi.newpipe.extractor.services.twitch.linkHandlers.TwitchChannelLinkHandlerFactory;
import org.schabi.newpipe.extractor.services.twitch.linkHandlers.TwitchChannelTabLinkHandlerFactory;
import org.schabi.newpipe.extractor.services.twitch.linkHandlers.TwitchLiveKioskLinkHandlerFactory;
import org.schabi.newpipe.extractor.services.twitch.linkHandlers.TwitchPlaylistLinkHandlerFactory;
import org.schabi.newpipe.extractor.services.twitch.linkHandlers.TwitchSearchQueryHandlerFactory;
import org.schabi.newpipe.extractor.services.twitch.linkHandlers.TwitchStreamLinkHandlerFactory;
import org.schabi.newpipe.extractor.stream.StreamExtractor;
import org.schabi.newpipe.extractor.subscription.SubscriptionExtractor;
import org.schabi.newpipe.extractor.suggestion.SuggestionExtractor;

import java.util.List;

public final class TwitchService extends StreamingService {

    public static final String BaseUrl = "https://twitch.tv";

    private boolean shouldUseHelixForKiosk;
    private boolean shouldUseHelixForCategories;

    private TwitchApiClient twitchApiClient;

    private TwitchApiClient getOrInitializeTwitchClient() {
        if(twitchApiClient == null)
            twitchApiClient = new TwitchApiClient(
                    new TwitchApiSettings(
//                            shouldUseHelixForKiosk,
//                            shouldUseHelixForCategories,
                            false,
                            false,
                            false,
                            false,
                            false
                    ),
                    getTokens()
            );
        return twitchApiClient;
    }

    public TwitchService(final int id) {
        super(id, "Twitch", List.of(LIVE, VIDEO));
    }

    @Override
    public String getBaseUrl() {
        return BaseUrl;
    }

    @Override
    public SearchExtractor getSearchExtractor(SearchQueryHandler queryHandler) {
        return new TwitchSearchExtractor(this, queryHandler, getOrInitializeTwitchClient());
    }

    @Override
    public LinkHandlerFactory getStreamLHFactory() {
        return new TwitchStreamLinkHandlerFactory();
    }

    @Override
    public SearchQueryHandlerFactory getSearchQHFactory() {
        return new TwitchSearchQueryHandlerFactory();
    }

    @Override
    public ListLinkHandlerFactory getChannelLHFactory() {
        return new TwitchChannelLinkHandlerFactory();
    }

    @Override
    public ListLinkHandlerFactory getChannelTabLHFactory() {
        return new TwitchChannelTabLinkHandlerFactory(getOrInitializeTwitchClient().getApiSettings());
    }

    @Override
    public ListLinkHandlerFactory getPlaylistLHFactory() {
        return new TwitchPlaylistLinkHandlerFactory();
    }

    @Override
    public ListLinkHandlerFactory getCommentsLHFactory() {
        return null;
    }

    @Override
    public SuggestionExtractor getSuggestionExtractor() {
        return null;
    }

    @Override
    public SubscriptionExtractor getSubscriptionExtractor() {
        return null;
    }

    @Override
    public KioskList getKioskList() throws ExtractionException {
        try {
            final var list = new KioskList(this);
            final var streamHandler = new TwitchLiveKioskLinkHandlerFactory(getOrInitializeTwitchClient().getApiSettings());
            list.addKioskEntry((streamingService, url, _) ->
                            new TwitchLiveKiosk(streamingService, streamHandler.fromUrl(url), getOrInitializeTwitchClient()),
                    streamHandler,
                    TwitchLiveKiosk.KIOSK_ID
            );
            list.setDefaultKiosk(TwitchLiveKiosk.KIOSK_ID);
            return list;
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public ChannelExtractor getChannelExtractor(ListLinkHandler linkHandler) throws ExtractionException {
        return new TwitchChannelExtractor(this, linkHandler, getOrInitializeTwitchClient());
    }

    @Override
    public ChannelTabExtractor getChannelTabExtractor(ListLinkHandler linkHandler) throws ExtractionException {
        final var channelUrl = TwitchUrlParser.parseChannelUrl(linkHandler.getUrl());
        return switch (channelUrl.tabType()) {
            case CLIPS -> new TwitchChannelClipExtractor(this, linkHandler, getOrInitializeTwitchClient());
            case VIDEOS -> new TwitchChannelVodExtractor(this, linkHandler, getOrInitializeTwitchClient());
            case LIVE -> new TwitchChannelStreamExtractor(this, linkHandler, getOrInitializeTwitchClient());
            default -> throw new IllegalStateException("Unexpected value: " + channelUrl.tabType());
        };
    }

    @Override
    public PlaylistExtractor getPlaylistExtractor(ListLinkHandler linkHandler) throws ExtractionException {
        return new TwitchPlaylistExtractor(this, linkHandler, getOrInitializeTwitchClient());
    }

    @Override
    public StreamExtractor getStreamExtractor(LinkHandler linkHandler) throws ExtractionException {
        final var url = linkHandler.getUrl();
        try {
            TwitchUrlParser.parseChannelUrl(url);
            return new TwitchStreamExtractor(this, linkHandler, getOrInitializeTwitchClient());
        } catch (Exception ignored) {
        }
        try {
            TwitchUrlParser.parseClipIdFromClipUrl(url);
            return new TwitchClipExtractor(this, linkHandler, getOrInitializeTwitchClient());
        } catch (Exception ignored) {
        }
        try {
            TwitchUrlParser.parseVodIdFromVodUrl(url);
            return new TwitchVodExtractor(this, linkHandler, getOrInitializeTwitchClient());
        } catch (Exception ignored) {
        }
        throw new ExtractionException("Cannot get StreamExtractor for url " + url);
    }

    @Override
    public CommentsExtractor getCommentsExtractor(ListLinkHandler linkHandler) throws ExtractionException {
        return null;
    }

    public void setShouldUseHelixForKiosk(final boolean should) {
        shouldUseHelixForKiosk = should;
    }

    public void setShouldUseHelixForCategories(final boolean should) {
        shouldUseHelixForCategories = should;
    }
}
