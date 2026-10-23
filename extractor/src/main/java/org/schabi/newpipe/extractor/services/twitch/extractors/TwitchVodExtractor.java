package org.schabi.newpipe.extractor.services.twitch.extractors;

import com.grack.nanojson.JsonParserException;

import org.jetbrains.annotations.NotNull;
import org.schabi.newpipe.extractor.Image;
import org.schabi.newpipe.extractor.MediaFormat;
import org.schabi.newpipe.extractor.StreamingService;
import org.schabi.newpipe.extractor.downloader.Downloader;
import org.schabi.newpipe.extractor.exceptions.ExtractionException;
import org.schabi.newpipe.extractor.exceptions.ParsingException;
import org.schabi.newpipe.extractor.linkhandler.LinkHandler;
import org.schabi.newpipe.extractor.services.twitch.TwitchUrlBuilder;
import org.schabi.newpipe.extractor.services.twitch.TwitchUrlParser;
import org.schabi.newpipe.extractor.services.twitch.api.TwitchApiClient;
import org.schabi.newpipe.extractor.services.twitch.api.TwitchThumbnailURLGenerator;
import org.schabi.newpipe.extractor.services.twitch.data.TwitchVideoStream;
import org.schabi.newpipe.extractor.services.twitch.data.api.pagination.CursorBasedPagination;
import org.schabi.newpipe.extractor.services.twitch.data.api.pagination.PositionBasedPagination;
import org.schabi.newpipe.extractor.services.twitch.data.api.responses.clip.TwitchVideoPlayerMediaSessionManagerResponseInner;
import org.schabi.newpipe.extractor.stream.AudioStream;
import org.schabi.newpipe.extractor.stream.DeliveryMethod;
import org.schabi.newpipe.extractor.stream.StreamExtractor;
import org.schabi.newpipe.extractor.stream.StreamType;
import org.schabi.newpipe.extractor.stream.VideoStream;

import java.io.IOException;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

import javax.annotation.Nonnull;

public class TwitchVodExtractor extends StreamExtractor {

    @Nonnull
    private final TwitchApiClient apiClient;
    private TwitchVideoStream[] streams;
    private TwitchVideoPlayerMediaSessionManagerResponseInner twitchVideoPlayerMediaSessionManager;

    public TwitchVodExtractor(final @Nonnull StreamingService service,
                              final @Nonnull LinkHandler linkHandler,
                              final @Nonnull TwitchApiClient apiClient) {
        super(service, linkHandler);
        this.apiClient = apiClient;
    }

    @NotNull
    @Override
    public String getThumbnailUrl() throws ParsingException {
        return TwitchThumbnailURLGenerator.NO_THUMBNAIL_URL;
    }

    @Override
    public void onFetchPage(@Nonnull Downloader downloader) throws IOException,
            ExtractionException {
        try {
            final var vodId = TwitchUrlParser.parseVodIdFromVodUrl(getUrl());
            final var token = apiClient.getUnauthorized().getVodPlaybackToken(downloader, vodId);
            streams = apiClient.getUnauthorized().getM3U8VodPlaybackUrl(downloader, vodId,
                    token.getData().signature(), token.getData().value());
            twitchVideoPlayerMediaSessionManager =
                    apiClient.getUnauthorized().getTwitchVideoPlayerMediaSessionManager(downloader, vodId).getData();
        } catch (JsonParserException e) {
            throw new IOException(e);
        }
    }

    @Nonnull
    @Override
    public String getName() throws ParsingException {
        return twitchVideoPlayerMediaSessionManager.clipTitle();
    }

    @Nonnull
    @Override
    public List<Image> getThumbnails() throws ParsingException {
        return Collections.emptyList();
    }

    @Nonnull
    @Override
    public String getUploaderUrl() throws ParsingException {
        return TwitchUrlBuilder.buildChannelUrlFromChannelNameAndPagination(
                getUploaderName(),
                twitchVideoPlayerMediaSessionManager.ownerId(),
                apiClient.getApiSettings().shouldUseHelixForVods()
                        ? CursorBasedPagination.DEFAULT_CURSOR_PAGINATION
                        : PositionBasedPagination.DEFAULT_POSITION_PAGINATION
        );
    }

    @Nonnull
    @Override
    public String getUploaderName() throws ParsingException {
        return twitchVideoPlayerMediaSessionManager.ownerDisplayName();
    }

    @Override
    public List<AudioStream> getAudioStreams() throws IOException, ExtractionException {
        return Collections.emptyList();
    }

    @Override
    public List<VideoStream> getVideoStreams() throws IOException, ExtractionException {
        return Arrays.stream(streams).map(res ->
                        new VideoStream.Builder()
                                .setId(VideoStream.ID_UNKNOWN)
                                .setContent(res.streamUrl(), true)
                                .setDeliveryMethod(DeliveryMethod.HLS)
                                .setResolution(res.resolution().asResolutionString())
                                .setIsVideoOnly(false)
                                .setMediaFormat(MediaFormat.MPEG_4)
                                .build()
                )
                .collect(Collectors.toList());
    }

    @Override
    public List<VideoStream> getVideoOnlyStreams() throws IOException, ExtractionException {
        return Collections.emptyList();
    }

    @NotNull
    @Override
    public String getUploaderAvatarUrl() throws ParsingException {
        return twitchVideoPlayerMediaSessionManager.ownerProfileImageUrl();
    }

    @Override
    public StreamType getStreamType() throws ParsingException {
        return StreamType.POST_LIVE_STREAM;
    }
}
