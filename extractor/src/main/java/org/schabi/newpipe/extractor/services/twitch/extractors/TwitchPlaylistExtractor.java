package org.schabi.newpipe.extractor.services.twitch.extractors;

import com.grack.nanojson.JsonParserException;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.schabi.newpipe.extractor.Page;
import org.schabi.newpipe.extractor.StreamingService;
import org.schabi.newpipe.extractor.downloader.Downloader;
import org.schabi.newpipe.extractor.exceptions.ExtractionException;
import org.schabi.newpipe.extractor.exceptions.ParsingException;
import org.schabi.newpipe.extractor.linkhandler.ListLinkHandler;
import org.schabi.newpipe.extractor.playlist.PlaylistExtractor;
import org.schabi.newpipe.extractor.services.twitch.TwitchUrlBuilder;
import org.schabi.newpipe.extractor.services.twitch.TwitchUrlParser;
import org.schabi.newpipe.extractor.services.twitch.api.TwitchApiClient;
import org.schabi.newpipe.extractor.services.twitch.api.TwitchThumbnailURLGenerator;
import org.schabi.newpipe.extractor.services.twitch.data.api.pagination.CursorBasedPagination;
import org.schabi.newpipe.extractor.services.twitch.data.api.pagination.Pagination;
import org.schabi.newpipe.extractor.services.twitch.data.api.pagination.PositionBasedPagination;
import org.schabi.newpipe.extractor.services.twitch.data.api.responses.directory.TwitchDirectoryResponseEntry;
import org.schabi.newpipe.extractor.services.twitch.data.api.responses.directory.TwitchHelixDirectoryResponseInner;
import org.schabi.newpipe.extractor.stream.StreamInfoItem;
import org.schabi.newpipe.extractor.stream.StreamType;

import java.io.IOException;
import java.util.Arrays;
import java.util.Collections;

import javax.annotation.Nonnull;

public class TwitchPlaylistExtractor extends PlaylistExtractor {

    @Nonnull
    private final TwitchApiClient apiClient;

    private TwitchHelixDirectoryResponseInner response;
    private TwitchDirectoryResponseEntry[] entries;
    private String gameId;
    private String gameName;
    private String gameSlug;
    private Pagination pagination;

    public TwitchPlaylistExtractor(@Nonnull final StreamingService service,
                                   @Nonnull final ListLinkHandler linkHandler,
                                   @Nonnull final TwitchApiClient apiClient) {
        super(service, linkHandler);
        this.apiClient = apiClient;
    }

    @Override
    public String getUploaderUrl() throws ParsingException {
        return "";
    }

    @Override
    public String getUploaderName() throws ParsingException {
        return apiClient.getApiSettings().shouldUseHelixForCategories()
                ? response.directoryName()
                : gameName;
    }

    @Override
    public String getUploaderAvatarUrl() throws ParsingException {
        return "";
    }

    @Override
    public boolean isUploaderVerified() throws ParsingException {
        return false;
    }

    @Override
    public long getStreamCount() throws ParsingException {
        return PlaylistExtractor.ITEM_COUNT_UNKNOWN;
    }

    private void populateData(@Nonnull final Downloader downloader, @Nonnull final String url) throws IOException, ExtractionException {
        try {
            final var categoryUrl = TwitchUrlParser.parseCategoryUrl(url);
            pagination = categoryUrl.pagination();
            if (apiClient.getApiSettings().shouldUseHelixForCategories()) {
                response = apiClient.getAuthorized().GetDirectoryInformation(
                        downloader,
                        categoryUrl.categoryName(),
                        categoryUrl.categoryId(),
                        ((CursorBasedPagination) pagination).cursor()
                );
            } else {
                final var response = apiClient.getUnauthorized().getTwitchCategory(
                        downloader,
                        categoryUrl.categorySlug(),
                        ((PositionBasedPagination) pagination).offset()
                                + ((PositionBasedPagination) pagination).size()
                ).getData();
                gameId = response.gameId();
                gameName = response.directoryName();
                gameSlug = categoryUrl.categorySlug();
                entries = response.entries().stream()
                        .skip(((PositionBasedPagination) pagination).offset())
                        .toArray(TwitchDirectoryResponseEntry[]::new);
            }
        } catch (JsonParserException e) {
            throw new IOException(e);
        }
    }

    @NotNull
    @Override
    public InfoItemsPage<StreamInfoItem> getInitialPage() throws IOException, ExtractionException {
        Page nextPage = null;
        if (apiClient.getApiSettings().shouldUseHelixForCategories()) {
            if (response.hasMoreEntries()) {
                nextPage = new Page(TwitchUrlBuilder.buildCategoryUrlFromCategoryName(
                        response.directoryName(),
                        response.directoryName(),
                        response.gameId(),
                        new CursorBasedPagination(response.cursor())
                ));
            }
            return getPage(
                    response.entries().toArray(TwitchDirectoryResponseEntry[]::new),
                    response.hasMoreEntries() ? nextPage : null
            );
        } else {
            final var positionPagination = (PositionBasedPagination) pagination;
            final var hasMoreEntries =
                    positionPagination.offset() + positionPagination.size() < 100;
            if (hasMoreEntries) {
                nextPage = new Page(TwitchUrlBuilder.buildCategoryUrlFromCategoryName(
                        gameName, gameSlug, gameId,
                        positionPagination.shift(100)
                ));
            }
            return getPage(
                    entries,
                    hasMoreEntries ? nextPage : null
            );
        }
    }

    private InfoItemsPage<StreamInfoItem> getPage(
            @Nonnull final TwitchDirectoryResponseEntry[] entries,
            @Nullable final Page nextPage
    ) {
        return new InfoItemsPage<>(
                Arrays.stream(entries).map(directoryEntry -> {
                    final var streamItem = new StreamInfoItem(
                            getServiceId(),
                            TwitchUrlBuilder.buildStreamUrlFromChannelName(
                                    directoryEntry.streamerLoginName(),
                                    directoryEntry.streamerId(),
                                    apiClient.getApiSettings().shouldUseHelixForKiosk()
                                            ? CursorBasedPagination.DEFAULT_CURSOR_PAGINATION
                                            : PositionBasedPagination.DEFAULT_POSITION_PAGINATION
                            ),
                            directoryEntry.title(),
                            StreamType.VIDEO_STREAM
                    );
                    streamItem.setThumbnailUrl(TwitchThumbnailURLGenerator.getThumbnailURLForStream(directoryEntry.streamerLoginName()));
                    streamItem.setUploaderAvatarUrl(directoryEntry.profileImageUrl());
                    streamItem.setUploaderName(directoryEntry.streamerName());
                    streamItem.setShortFormContent(false);
                    streamItem.setUploaderUrl(TwitchUrlBuilder.buildChannelUrlFromChannelNameAndPagination(
                            directoryEntry.streamerLoginName(),
                            directoryEntry.streamerId(),
                            apiClient.getApiSettings().shouldUseHelixForVods()
                                    ? CursorBasedPagination.DEFAULT_CURSOR_PAGINATION
                                    : PositionBasedPagination.DEFAULT_POSITION_PAGINATION
                    ));
                    streamItem.setUploaderVerified(directoryEntry.isPartner());
                    streamItem.setViewCount(directoryEntry.viewCount());
                    return streamItem;
                }).toList(),
                nextPage,
                Collections.emptyList()
        );
    }

    @Override
    public InfoItemsPage<StreamInfoItem> getPage(Page page) throws IOException,
            ExtractionException {
        populateData(getDownloader(), page.getUrl());
        return getInitialPage();
    }

    @Override
    public void onFetchPage(@NotNull Downloader downloader) throws IOException,
            ExtractionException {
        populateData(downloader, getUrl());
    }

    @NotNull
    @Override
    public String getName() throws ParsingException {
        return apiClient.getApiSettings().shouldUseHelixForCategories()
                ? response.directoryName()
                : gameName;
    }
}
