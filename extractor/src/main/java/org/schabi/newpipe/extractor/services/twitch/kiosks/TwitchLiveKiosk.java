package org.schabi.newpipe.extractor.services.twitch.kiosks;

import com.grack.nanojson.JsonParserException;

import org.jetbrains.annotations.Nullable;
import org.schabi.newpipe.extractor.Page;
import org.schabi.newpipe.extractor.StreamingService;
import org.schabi.newpipe.extractor.downloader.Downloader;
import org.schabi.newpipe.extractor.exceptions.ExtractionException;
import org.schabi.newpipe.extractor.exceptions.ParsingException;
import org.schabi.newpipe.extractor.kiosk.KioskExtractor;
import org.schabi.newpipe.extractor.linkhandler.ListLinkHandler;
import org.schabi.newpipe.extractor.services.twitch.TwitchUrlBuilder;
import org.schabi.newpipe.extractor.services.twitch.TwitchUrlParser;
import org.schabi.newpipe.extractor.services.twitch.api.TwitchApiClient;
import org.schabi.newpipe.extractor.services.twitch.data.api.pagination.CursorBasedPagination;
import org.schabi.newpipe.extractor.services.twitch.data.api.pagination.Pagination;
import org.schabi.newpipe.extractor.services.twitch.data.api.pagination.PositionBasedPagination;
import org.schabi.newpipe.extractor.services.twitch.data.api.pagination.TwitchPaginationHelper;
import org.schabi.newpipe.extractor.services.twitch.data.api.responses.nowLive.TwitchHelixNowLiveResponseInner;
import org.schabi.newpipe.extractor.services.twitch.data.api.responses.nowLive.TwitchNowLiveResponseEntry;
import org.schabi.newpipe.extractor.stream.StreamInfoItem;
import org.schabi.newpipe.extractor.stream.StreamType;

import java.io.IOException;
import java.util.Arrays;
import java.util.Collections;
import java.util.stream.Collectors;

import javax.annotation.Nonnull;

public class TwitchLiveKiosk extends KioskExtractor<StreamInfoItem> {

    public static final String KIOSK_ID = "live";
    @Nonnull
    private final TwitchApiClient apiClient;
    private TwitchHelixNowLiveResponseInner response;
    private TwitchNowLiveResponseEntry[] entries;
    private Pagination pagination;

    public TwitchLiveKiosk(final @Nonnull StreamingService streamingService,
                           final @Nonnull ListLinkHandler linkHandler,
                           final @Nonnull TwitchApiClient apiClient) {
        super(streamingService, linkHandler, KIOSK_ID);
        this.apiClient = apiClient;
    }

    @Override
    public void onFetchPage(@Nonnull Downloader downloader) throws IOException, ExtractionException {
        fetchData(downloader, getUrl());
    }

    private void fetchData(@Nonnull final Downloader downloader, @Nonnull final String url) throws IOException, ExtractionException {
        try {
            final var paginationString = TwitchUrlParser.parsePaginationFromKioskUrl(url);
            pagination = TwitchPaginationHelper.getPaginationFromString(paginationString);
            if(apiClient.getApiSettings().shouldUseHelixForKiosk()) {
                response = apiClient.getAuthorized().getNowLiveInformation(downloader,
                        ((CursorBasedPagination)pagination).cursor()
                );
            }
            else
                entries = TwitchPaginationHelper.paginateArray(
                        apiClient.getUnauthorized().getNowLiveInformation(
                                        downloader,
                                        ((PositionBasedPagination)pagination).offset()
                                                + ((PositionBasedPagination)pagination).size()
                                ).getData(),
                            TwitchNowLiveResponseEntry.class,
                            (PositionBasedPagination)pagination
                );
        } catch (JsonParserException e) {
            throw new IOException(e);
        }
    }

    @Nonnull
    @Override
    public String getName() throws ParsingException {
        return KIOSK_ID;
    }

    @Nonnull
    @Override
    public InfoItemsPage<StreamInfoItem> getInitialPage() throws IOException, ExtractionException {
        Page nextPage = null;
        if(apiClient.getApiSettings().shouldUseHelixForKiosk()) {
            if(response.hasMoreEntries()) {
                nextPage = new Page(TwitchUrlBuilder.buildLiveKioskUrlFromPagination(
                        new CursorBasedPagination(response.cursor())
                ));
            }
            return getPage(
                    response.liveEntries().toArray(TwitchNowLiveResponseEntry[]::new),
                    response.hasMoreEntries() ? nextPage : null
            );
        }
        else {
            final var positionPagination = (PositionBasedPagination)pagination;
            final var hasMoreEntries = positionPagination.offset() + positionPagination.size() < 30;
            if(hasMoreEntries) {
                nextPage = new Page(TwitchUrlBuilder.buildLiveKioskUrlFromPagination(
                        positionPagination.shift(30)
                ));
            }
            return getPage(
                    entries,
                    hasMoreEntries ? nextPage : null
            );
        }
    }

    private InfoItemsPage<StreamInfoItem> getPage(
            @Nonnull final TwitchNowLiveResponseEntry[] entries,
            @Nullable final Page nextPage) {
        return new InfoItemsPage<>(
                Arrays.stream(entries)
                        .map(liveEntry -> {
                            final var infoItem = new StreamInfoItem(
                                    getServiceId(),
                                    TwitchUrlBuilder.buildStreamUrlFromChannelName(
                                            liveEntry.streamerLoginName(),
                                            liveEntry.streamerId(),
                                            PositionBasedPagination.DEFAULT_POSITION_PAGINATION
                                    ),
                                    liveEntry.streamTitle(),
                                    StreamType.LIVE_STREAM
                            );
                            infoItem.setViewCount(liveEntry.streamViewers());
                            infoItem.setUploaderName(liveEntry.streamerName());
                            infoItem.setUploaderUrl(TwitchUrlBuilder.buildChannelUrlFromChannelNameAndPagination(
                                    liveEntry.streamerLoginName(),
                                    liveEntry.streamerId(),
                                    PositionBasedPagination.DEFAULT_POSITION_PAGINATION
                            ));
                            infoItem.setShortDescription(liveEntry.gameName());
                            infoItem.setUploaderAvatarUrl(liveEntry.thumbnailUrl());
                            infoItem.setThumbnailUrl(liveEntry.thumbnailUrl());
                            return infoItem;
                        })
                        .collect(Collectors.toList()),
                        nextPage,
                Collections.emptyList()
        );
    }

    @Override
    public InfoItemsPage<StreamInfoItem> getPage(Page page) throws IOException, ExtractionException {
        fetchData(getDownloader(), page.getUrl());
        return getInitialPage();
    }
}
