package org.schabi.newpipe.extractor.services.twitch.extractors;

import com.grack.nanojson.JsonParserException;

import org.jetbrains.annotations.Nullable;
import org.schabi.newpipe.extractor.InfoItem;
import org.schabi.newpipe.extractor.MultiInfoItemsCollector;
import org.schabi.newpipe.extractor.Page;
import org.schabi.newpipe.extractor.StreamingService;
import org.schabi.newpipe.extractor.channel.ChannelInfoItem;
import org.schabi.newpipe.extractor.channel.ChannelInfoItemExtractor;
import org.schabi.newpipe.extractor.downloader.Downloader;
import org.schabi.newpipe.extractor.exceptions.ExtractionException;
import org.schabi.newpipe.extractor.exceptions.ParsingException;
import org.schabi.newpipe.extractor.linkhandler.SearchQueryHandler;
import org.schabi.newpipe.extractor.localization.DateWrapper;
import org.schabi.newpipe.extractor.playlist.PlaylistInfo;
import org.schabi.newpipe.extractor.playlist.PlaylistInfoItem;
import org.schabi.newpipe.extractor.playlist.PlaylistInfoItemExtractor;
import org.schabi.newpipe.extractor.search.SearchExtractor;
import org.schabi.newpipe.extractor.services.twitch.TwitchUrlBuilder;
import org.schabi.newpipe.extractor.services.twitch.TwitchUrlParser;
import org.schabi.newpipe.extractor.services.twitch.api.TwitchApiClient;
import org.schabi.newpipe.extractor.services.twitch.data.api.pagination.CursorBasedPagination;
import org.schabi.newpipe.extractor.services.twitch.data.api.pagination.Pagination;
import org.schabi.newpipe.extractor.services.twitch.data.api.pagination.PositionBasedPagination;
import org.schabi.newpipe.extractor.services.twitch.data.api.responses.search.TwitchSearchResponse;
import org.schabi.newpipe.extractor.services.twitch.data.api.responses.search.types.TwitchSearchChannelResponseEntry;
import org.schabi.newpipe.extractor.services.twitch.data.api.responses.search.types.TwitchSearchGameResponseEntry;
import org.schabi.newpipe.extractor.services.twitch.data.api.responses.search.types.TwitchSearchStreamResponseEntry;
import org.schabi.newpipe.extractor.services.twitch.data.api.responses.search.types.TwitchSearchVodResponseEntry;
import org.schabi.newpipe.extractor.stream.StreamInfoItem;
import org.schabi.newpipe.extractor.stream.StreamInfoItemExtractor;
import org.schabi.newpipe.extractor.stream.StreamType;

import java.io.IOException;

import javax.annotation.Nonnull;

public class TwitchSearchExtractor extends SearchExtractor {

    @Nonnull
    private final TwitchApiClient apiClient;
    private TwitchSearchResponse response;

    public TwitchSearchExtractor(final @Nonnull StreamingService service,
                                 final @Nonnull SearchQueryHandler linkHandler,
                                 final @Nonnull TwitchApiClient apiClient) {
        super(service, linkHandler);
        this.apiClient = apiClient;
    }

    private StreamInfoItem buildStreamInfoItem(final int serviceId, final TwitchSearchStreamResponseEntry entry) {
        final var item = new StreamInfoItem(
                serviceId,
                TwitchUrlBuilder.buildStreamUrlFromChannelName(
                        entry.getChannelName(),
                        entry.getChannelId(),
                        apiClient.getApiSettings().shouldUseHelixForKiosk()
                                ? CursorBasedPagination.DEFAULT_CURSOR_PAGINATION
                                : PositionBasedPagination.DEFAULT_POSITION_PAGINATION
                ),
                entry.getStreamTitle(),
                StreamType.LIVE_STREAM
        );
        item.setViewCount(entry.getStreamViewerCount());
        item.setShortDescription(entry.getStreamGameName());
        item.setUploaderAvatarUrl(entry.getChannelAvatarUrl());
        item.setUploaderName(entry.getChannelName());
        item.setThumbnailUrl(entry.getStreamThumbnailUrl());
        return item;
    }

    private ChannelInfoItem buildChannelInfoItem(final int serviceId, final TwitchSearchChannelResponseEntry entry) {
        final var item = new ChannelInfoItem(
                serviceId,
                TwitchUrlBuilder.buildChannelUrlFromChannelNameAndPagination(
                        entry.getChannelName(),
                        entry.getChannelId(),
                        apiClient.getApiSettings().shouldUseHelixForVods()
                                ? CursorBasedPagination.DEFAULT_CURSOR_PAGINATION
                                : PositionBasedPagination.DEFAULT_POSITION_PAGINATION
                ),
                entry.getChannelName()
        );
        item.setThumbnailUrl(entry.getChannelAvatarUrl());
        item.setDescription(entry.getChannelDescription());
        item.setVerified(entry.isChannelPartnered());
        item.setSubscriberCount(entry.getChannelFollowerCount());
        return item;
    }

    private PlaylistInfoItem buildCategoryInfoItem(final int serviceId, final TwitchSearchGameResponseEntry entry) {
        final var item = new PlaylistInfoItem(
                serviceId,
                TwitchUrlBuilder.buildCategoryUrlFromCategoryName(
                        entry.getGameName(),
                        entry.getSlug(),
                        entry.getId(),
                        apiClient.getApiSettings().shouldUseHelixForCategories()
                            ? CursorBasedPagination.DEFAULT_CURSOR_PAGINATION
                            : PositionBasedPagination.DEFAULT_POSITION_PAGINATION
                ),
                entry.getGameName()
        );
        item.setThumbnailUrl(entry.getGameBoxArtUrl());
        item.setStreamCount(-1);
        item.setPlaylistType(PlaylistInfo.PlaylistType.MIX_STREAM);
        return item;
    }

    private static StreamInfoItem buildVodInfoItem(final int serviceId, final TwitchSearchVodResponseEntry entry) {
        final var item = new StreamInfoItem(
                serviceId,
                TwitchUrlBuilder.buildVodUrlFromVodId(entry.getVodId()),
                entry.getVodTitle(),
                StreamType.POST_LIVE_STREAM
        );
        item.setViewCount(entry.getVodViewCount());
        item.setDuration(entry.getVodDurationInSeconds());
        item.setUploaderName(entry.getChannelName());
        item.setThumbnailUrl(entry.getStreamThumbnailUrl());
        return item;
    }

    @Override
    protected InfoItemsPage<InfoItem> getInitialPageInternal() throws IOException, ExtractionException {
        var collector = new MultiInfoItemsCollector(getServiceId());
        for (final var item : response.getData()) {
            if (item instanceof TwitchSearchChannelResponseEntry) {
                var infoItem = buildChannelInfoItem(getServiceId(), (TwitchSearchChannelResponseEntry) item);
                collector.commit(new ChannelInfoItemExtractor() {
                    @Override
                    public String getDescription() throws ParsingException {
                        return infoItem.getDescription();
                    }

                    @Override
                    public long getSubscriberCount() throws ParsingException {
                        return infoItem.getSubscriberCount();
                    }

                    @Override
                    public long getStreamCount() throws ParsingException {
                        return infoItem.getStreamCount();
                    }

                    @Override
                    public boolean isVerified() throws ParsingException {
                        return infoItem.isVerified();
                    }

                    @Override
                    public String getName() throws ParsingException {
                        return infoItem.getName();
                    }

                    @Override
                    public String getUrl() throws ParsingException {
                        return infoItem.getUrl();
                    }

                    @Override
                    public String getThumbnailUrl() throws ParsingException {
                        return infoItem.getThumbnailUrl();
                    }
                });
            } else if (item instanceof TwitchSearchStreamResponseEntry) {
                var infoItem = buildStreamInfoItem(getServiceId(), (TwitchSearchStreamResponseEntry) item);
                collector.commit(new StreamInfoItemExtractor() {
                    @Override
                    public StreamType getStreamType() throws ParsingException {
                        return infoItem.getStreamType();
                    }

                    @Override
                    public long getDuration() throws ParsingException {
                        return infoItem.getDuration();
                    }

                    @Override
                    public long getViewCount() throws ParsingException {
                        return infoItem.getViewCount();
                    }

                    @Override
                    public String getUploaderName() throws ParsingException {
                        return infoItem.getUploaderName();
                    }

                    @Nullable
                    @Override
                    public String getTextualUploadDate() throws ParsingException {
                        return infoItem.getTextualUploadDate();
                    }

                    @Nullable
                    @Override
                    public DateWrapper getUploadDate() throws ParsingException {
                        return infoItem.getUploadDate();
                    }

                    @Override
                    public String getName() throws ParsingException {
                        return infoItem.getName();
                    }

                    @Override
                    public String getUrl() throws ParsingException {
                        return infoItem.getUrl();
                    }

                    @Override
                    public String getThumbnailUrl() throws ParsingException {
                        return infoItem.getThumbnailUrl();
                    }
                });
            } else if (item instanceof TwitchSearchVodResponseEntry) {
                final var infoItem = buildVodInfoItem(getServiceId(), (TwitchSearchVodResponseEntry) item);
                collector.commit(new StreamInfoItemExtractor() {
                    @Override
                    public StreamType getStreamType() throws ParsingException {
                        return infoItem.getStreamType();
                    }

                    @Override
                    public long getDuration() throws ParsingException {
                        return infoItem.getDuration();
                    }

                    @Override
                    public long getViewCount() throws ParsingException {
                        return infoItem.getViewCount();
                    }

                    @Override
                    public String getUploaderName() throws ParsingException {
                        return infoItem.getUploaderName();
                    }

                    @Nullable
                    @Override
                    public String getTextualUploadDate() throws ParsingException {
                        return infoItem.getTextualUploadDate();
                    }

                    @Nullable
                    @Override
                    public DateWrapper getUploadDate() throws ParsingException {
                        return infoItem.getUploadDate();
                    }

                    @Override
                    public String getName() throws ParsingException {
                        return infoItem.getName();
                    }

                    @Override
                    public String getUrl() throws ParsingException {
                        return infoItem.getUrl();
                    }

                    @Override
                    public String getThumbnailUrl() throws ParsingException {
                        return infoItem.getThumbnailUrl();
                    }
                });
            } else if (item instanceof TwitchSearchGameResponseEntry) {
                final var infoItem = buildCategoryInfoItem(getServiceId(), (TwitchSearchGameResponseEntry) item);
                collector.commit(new PlaylistInfoItemExtractor() {
                    @Override
                    public String getUploaderName() throws ParsingException {
                        return infoItem.getUploaderName();
                    }

                    @Override
                    public long getStreamCount() throws ParsingException {
                        return infoItem.getStreamCount();
                    }

                    @Override
                    public String getName() throws ParsingException {
                        return infoItem.getName();
                    }

                    @Override
                    public String getUrl() throws ParsingException {
                        return infoItem.getUrl();
                    }

                    @Override
                    public String getThumbnailUrl() throws ParsingException {
                        return infoItem.getThumbnailUrl();
                    }
                });
            }
        }
        return new InfoItemsPage<>(collector, null);
    }

    @Override
    protected InfoItemsPage<InfoItem> getPageInternal(Page page) throws IOException, ExtractionException {
        return getInitialPageInternal();
    }

    @Override
    public void onFetchPage(@Nonnull Downloader downloader) throws IOException, ExtractionException{
        try {
            final var query = TwitchUrlParser.parseQueryFromSearchUrl(getUrl());
            response = apiClient.getUnauthorized().getSearchResponse(downloader, query);
        } catch (JsonParserException e) {
            throw new IOException(e);
        }
    }
}
