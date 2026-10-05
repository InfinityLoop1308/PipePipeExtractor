package org.schabi.newpipe.extractor.services.twitch.extractors;

import com.grack.nanojson.JsonParserException;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.schabi.newpipe.extractor.Page;
import org.schabi.newpipe.extractor.StreamingService;
import org.schabi.newpipe.extractor.channel.ChannelExtractor;
import org.schabi.newpipe.extractor.downloader.Downloader;
import org.schabi.newpipe.extractor.exceptions.ExtractionException;
import org.schabi.newpipe.extractor.exceptions.ParsingException;
import org.schabi.newpipe.extractor.linkhandler.ChannelTabs;
import org.schabi.newpipe.extractor.linkhandler.ListLinkHandler;
import org.schabi.newpipe.extractor.localization.DateWrapper;
import org.schabi.newpipe.extractor.search.filter.Filter;
import org.schabi.newpipe.extractor.search.filter.FilterItem;
import org.schabi.newpipe.extractor.services.twitch.TwitchUrlBuilder;
import org.schabi.newpipe.extractor.services.twitch.TwitchUrlParser;
import org.schabi.newpipe.extractor.services.twitch.api.TwitchApiClient;
import org.schabi.newpipe.extractor.services.twitch.data.TwitchChannelTabLinkType;
import org.schabi.newpipe.extractor.services.twitch.data.api.pagination.CursorBasedPagination;
import org.schabi.newpipe.extractor.services.twitch.data.api.pagination.PositionBasedPagination;
import org.schabi.newpipe.extractor.services.twitch.data.api.pagination.TwitchPaginationHelper;
import org.schabi.newpipe.extractor.services.twitch.data.api.responses.channel.TwitchChannelResponseInner;
import org.schabi.newpipe.extractor.services.twitch.data.api.responses.vod.TwitchHelixVodResponse;
import org.schabi.newpipe.extractor.services.twitch.data.api.responses.vod.TwitchVodResponseInner;
import org.schabi.newpipe.extractor.services.twitch.data.url.ChannelUrl;
import org.schabi.newpipe.extractor.stream.StreamInfoItem;
import org.schabi.newpipe.extractor.stream.StreamInfoItemExtractor;
import org.schabi.newpipe.extractor.stream.StreamInfoItemsCollector;
import org.schabi.newpipe.extractor.stream.StreamType;

import java.io.IOException;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import javax.annotation.Nonnull;

public class TwitchChannelExtractor extends ChannelExtractor {

    @Nonnull
    private final TwitchApiClient apiClient;

    private ChannelUrl channelUrl;
    private TwitchChannelResponseInner channelResponse;
    private TwitchHelixVodResponse helixResponse;
    private TwitchVodResponseInner[] gqlResponse;

    public TwitchChannelExtractor(final @Nonnull StreamingService service,
                                  final @Nonnull ListLinkHandler linkHandler,
                                  final @Nonnull TwitchApiClient apiClient) {
        super(service, linkHandler);
        this.apiClient = apiClient;
    }


    @NotNull
    @Override
    public InfoItemsPage<StreamInfoItem> getInitialPage() throws IOException, ExtractionException {
        Page nextPage = null;

        if (channelUrl.pagination() instanceof PositionBasedPagination positionBasedPagination) {
            if (positionBasedPagination.offset() + positionBasedPagination.size() < 100
                    && gqlResponse.length != 0)
                nextPage = new Page(TwitchUrlBuilder.buildChannelTabUrl(
                        channelUrl.channelLoginName(),
                        channelUrl.channelId(),
                        TwitchChannelTabLinkType.VIDEOS,
                        positionBasedPagination.shift(100)
                ));
        }

        if (channelUrl.pagination() instanceof CursorBasedPagination) {
            if (helixResponse.hasMoreEntries())
                nextPage = new Page(TwitchUrlBuilder.buildChannelTabUrl(
                        channelUrl.channelLoginName(),
                        channelUrl.channelId(),
                        TwitchChannelTabLinkType.VIDEOS,
                        new CursorBasedPagination(helixResponse.cursor())
                ));
        }

        final var collector = new StreamInfoItemsCollector(getServiceId());
        for (final var vod : gqlResponse) {
            collector.commit(new StreamInfoItemExtractor() {
                @Override
                public StreamType getStreamType() throws ParsingException {
                    return StreamType.POST_LIVE_STREAM;
                }

                @Override
                public long getDuration() throws ParsingException {
                    return vod.getVodLength();
                }

                @Override
                public long getViewCount() throws ParsingException {
                    return vod.getVodViewerCount();
                }

                @Override
                public String getUploaderName() throws ParsingException {
                    return "";
                }

                @Nullable
                @Override
                public String getTextualUploadDate() throws ParsingException {
                    return null;
                }

                @Nullable
                @Override
                public DateWrapper getUploadDate() throws ParsingException {
                    return new DateWrapper(OffsetDateTime.parse(vod.getUploadDateTimeString()));
                }

                @Override
                public String getName() throws ParsingException {
                    return vod.getVodTitle();
                }

                @Override
                public String getUrl() throws ParsingException {
                    return TwitchUrlBuilder.buildVodUrlFromVodId(vod.getVodId());
                }

                @Override
                public String getThumbnailUrl() throws ParsingException {
                    return vod.getVodThumbnailUrl();
                }
            });
        }

        return new InfoItemsPage<>(collector,
                nextPage
        );
    }

    @Override
    public InfoItemsPage<StreamInfoItem> getPage(Page page) throws IOException,
            ExtractionException {
        fetchData(getDownloader(), page.getUrl());
        return getInitialPage();
    }

    @Override
    public String getBannerUrl() throws ParsingException {
        return channelResponse.channelBannerUrl();
    }

    @Override
    public String getAvatarUrl() throws ParsingException {
        return channelResponse.streamerAvatarUrl();
    }

    @Override
    public long getSubscriberCount() throws ParsingException {
        return channelResponse.followerCount();
    }

    @Override
    public String getDescription() throws ParsingException {
        return channelResponse.streamerDescription();
    }

    @Override
    public boolean isVerified() throws ParsingException {
        return channelResponse.isPartner();
    }

    @Nonnull
    @Override
    public List<ListLinkHandler> getTabs() throws ParsingException {
        final var list = new ArrayList<ListLinkHandler>();
        list.add(new ListLinkHandler(
                getUrl(),
                TwitchUrlBuilder.buildChannelTabUrl(
                        channelUrl.channelLoginName(),
                        channelUrl.channelId(),
                        TwitchChannelTabLinkType.LIVE,
                        PositionBasedPagination.DEFAULT_POSITION_PAGINATION
                ),
                getId(),
                Collections.singletonList(new FilterItem(
                        Filter.ITEM_IDENTIFIER_UNKNOWN,
                        ChannelTabs.LIVESTREAMS
                )),
                null
        ));
        list.add(new ListLinkHandler(
                getUrl(),
                TwitchUrlBuilder.buildChannelTabUrl(
                        channelUrl.channelLoginName(),
                        channelUrl.channelId(),
                        TwitchChannelTabLinkType.VIDEOS,
                        apiClient.getApiSettings().shouldUseHelixForVods()
                                ? CursorBasedPagination.DEFAULT_CURSOR_PAGINATION
                                : PositionBasedPagination.DEFAULT_POSITION_PAGINATION
                ),
                getId(),
                Collections.singletonList(new FilterItem(
                        Filter.ITEM_IDENTIFIER_UNKNOWN,
                        ChannelTabs.VIDEOS
                )),
                null
        ));
        list.add(new ListLinkHandler(
                getUrl(),
                TwitchUrlBuilder.buildChannelTabUrl(
                        channelUrl.channelLoginName(),
                        channelUrl.channelId(),
                        TwitchChannelTabLinkType.CLIPS,
                        apiClient.getApiSettings().shouldUseHelixForClips()
                                ? CursorBasedPagination.DEFAULT_CURSOR_PAGINATION
                                : PositionBasedPagination.DEFAULT_POSITION_PAGINATION
                ),
                getId(),
                Collections.singletonList(new FilterItem(
                        Filter.ITEM_IDENTIFIER_UNKNOWN,
                        ChannelTabs.SHORTS
                )),
                null
        ));
        return list;
    }

    private void fetchData(@Nonnull final Downloader downloader, @Nonnull final String url) throws IOException, ExtractionException {
        try {
            channelUrl = TwitchUrlParser.parseChannelUrl(url);
            channelResponse = apiClient.getUnauthorized().getTwitchChannel(downloader,
                    channelUrl.channelLoginName()).getData();
            if (apiClient.getApiSettings().shouldUseHelixForVods()) {
                helixResponse = apiClient.getAuthorized().getVods(
                        downloader,
                        channelUrl.channelId(),
                        ((CursorBasedPagination) channelUrl.pagination()).cursor());
            } else {
                final var positionPagination = (PositionBasedPagination) channelUrl.pagination();
                gqlResponse = TwitchPaginationHelper.paginateArray(
                        apiClient.getUnauthorized().getTwitchVods(
                                downloader,
                                channelUrl.channelLoginName(),
                                positionPagination.offset() + positionPagination.size()
                        ).getData(), TwitchVodResponseInner.class, positionPagination
                );
            }
        } catch (JsonParserException e) {
            throw new IOException(e);
        }
    }

    @Override
    public void onFetchPage(@Nonnull Downloader downloader) throws IOException,
            ExtractionException {
        fetchData(downloader, getUrl());
    }

    @Nonnull
    @Override
    public String getName() throws ParsingException {
        return channelResponse.channelName();
    }
}
