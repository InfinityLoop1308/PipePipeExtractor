package org.schabi.newpipe.extractor.services.twitch.extractors;

import com.grack.nanojson.JsonParserException;

import org.jetbrains.annotations.NotNull;
import org.schabi.newpipe.extractor.InfoItem;
import org.schabi.newpipe.extractor.InfoItemExtractor;
import org.schabi.newpipe.extractor.InfoItemsCollector;
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
import org.schabi.newpipe.extractor.services.twitch.data.Resolution;
import org.schabi.newpipe.extractor.services.twitch.data.TwitchVideoStream;
import org.schabi.newpipe.extractor.services.twitch.data.api.pagination.CursorBasedPagination;
import org.schabi.newpipe.extractor.services.twitch.data.api.pagination.PositionBasedPagination;
import org.schabi.newpipe.extractor.services.twitch.data.api.responses.TwitchSideNavResponseInner;
import org.schabi.newpipe.extractor.services.twitch.data.api.responses.channel.TwitchChannelResponseInner;
import org.schabi.newpipe.extractor.services.twitch.data.api.responses.stream.TwitchStreamResponseInner;
import org.schabi.newpipe.extractor.stream.AudioStream;
import org.schabi.newpipe.extractor.stream.DeliveryMethod;
import org.schabi.newpipe.extractor.stream.StreamExtractor;
import org.schabi.newpipe.extractor.stream.StreamInfoItem;
import org.schabi.newpipe.extractor.stream.StreamInfoItemsCollector;
import org.schabi.newpipe.extractor.stream.StreamType;
import org.schabi.newpipe.extractor.stream.VideoStream;

import java.io.IOException;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

public class TwitchStreamExtractor extends StreamExtractor {

    @Nonnull
    private final TwitchApiClient apiClient;
    private TwitchStreamResponseInner streamResponse;
    private TwitchVideoStream[] streams;
    private TwitchChannelResponseInner channelResponse;
    private TwitchSideNavResponseInner[] sideNavResponses;

    public TwitchStreamExtractor(final @Nonnull StreamingService service,
                                 final @Nonnull LinkHandler linkHandler,
                                 final @Nonnull TwitchApiClient apiClient) {
        super(service, linkHandler);
        this.apiClient = apiClient;
    }

    private static int getResolutionPixelCount(Resolution resolution) {
        return resolution.width() * resolution.height();
    }

    @Nonnull
    private static TwitchVideoStream GetStreamWithHighestResolution(TwitchVideoStream[] streams) {
        return Arrays.stream(streams)
                .max(Comparator.comparingInt(twitchVideoStream ->
                        getResolutionPixelCount(twitchVideoStream.resolution()))).get();
    }

    @NotNull
    @Override
    public String getThumbnailUrl() throws ParsingException {
        return TwitchThumbnailURLGenerator.getThumbnailURLForStream(streamResponse.streamerLoginName());
    }

    @NotNull
    @Override
    public String getUploaderAvatarUrl() throws ParsingException {
        return channelResponse.streamerAvatarUrl();
    }

    @Nonnull
    @Override
    public String getHlsUrl() {
        return GetStreamWithHighestResolution(streams).streamUrl();
    }

    @Nonnull
    @Override
    public String getUploaderUrl() throws ParsingException {
        return TwitchUrlBuilder.buildChannelUrlFromChannelNameAndPagination(
                streamResponse.streamerLoginName(),
                channelResponse.channelId(),
                apiClient.getApiSettings().shouldUseHelixForVods()
                        ? CursorBasedPagination.DEFAULT_CURSOR_PAGINATION
                        : PositionBasedPagination.DEFAULT_POSITION_PAGINATION
        );
    }

    @Nonnull
    @Override
    public String getUploaderName() throws ParsingException {
        return streamResponse.streamerName();
    }

    @Override
    public List<AudioStream> getAudioStreams() throws IOException, ExtractionException {
        return Collections.emptyList();
    }

    @Nullable
    @Override
    public InfoItemsCollector<? extends InfoItem, ? extends InfoItemExtractor> getRelatedItems() throws IOException, ExtractionException {
        return new StreamInfoItemsCollector(getServiceId()) {
            @Override
            public List<StreamInfoItem> getItems() {
                return Arrays.stream(sideNavResponses).map(res -> {
                    var item = new StreamInfoItem(
                            getServiceId(),
                            TwitchUrlBuilder.buildStreamUrlFromChannelName(
                                    res.streamerLoginName(),
                                    channelResponse.channelId(),
                                    apiClient.getApiSettings().shouldUseHelixForKiosk()
                                            ? CursorBasedPagination.DEFAULT_CURSOR_PAGINATION
                                            : PositionBasedPagination.DEFAULT_POSITION_PAGINATION
                            ),
                            res.title(),
                            StreamType.VIDEO_STREAM
                    );
                    item.setUploaderName(res.streamerName());
                    item.setThumbnailUrl(TwitchThumbnailURLGenerator.getThumbnailURLForStream(res.streamerLoginName()));
                    return item;
                }).toList();
            }
        };
    }

    @Override
    public List<VideoStream> getVideoStreams() throws IOException, ExtractionException {
        return Arrays.stream(streams).map(str ->
                        new VideoStream.Builder()
                                .setMediaFormat(MediaFormat.MPEG_4)
                                .setId(VideoStream.ID_UNKNOWN)
                                .setContent(str.streamUrl(), true)
                                .setDeliveryMethod(DeliveryMethod.HLS)
                                .setResolution(str.resolution().asResolutionString())
                                .setIsVideoOnly(false)
                                .build()
                )
                .collect(Collectors.toList());
    }

    @Override
    public List<VideoStream> getVideoOnlyStreams() throws IOException, ExtractionException {
        return List.of();
    }

    @Override
    public StreamType getStreamType() throws ParsingException {
        return StreamType.LIVE_STREAM;
    }

    @Override
    public void onFetchPage(@Nonnull Downloader downloader) throws IOException,
            ExtractionException {
        try {
            final var channelUrl = TwitchUrlParser.parseChannelUrl(getUrl());
            final var streamInfo = apiClient.getUnauthorized().getStreamInformation(downloader,
                    channelUrl.channelLoginName());
            streamResponse = streamInfo.getData();
            channelResponse = apiClient.getUnauthorized().getTwitchChannel(downloader,
                    streamResponse.streamerLoginName()).getData();
            sideNavResponses = apiClient.getUnauthorized().getTwitchSideNavResponse(downloader,
                    streamResponse.streamerLoginName()).getData();
            final var playbackToken = apiClient.getUnauthorized().getPlaybackToken(downloader,
                    streamResponse.streamerLoginName());
            streams = apiClient.getUnauthorized().getM3U8PlaybackUrl(downloader,
                    streamResponse.streamerLoginName(), playbackToken.getData().signature(),
                    playbackToken.getData().value());
        } catch (JsonParserException e) {
            throw new IOException(e);
        }
    }

    @Nonnull
    @Override
    public String getName() throws ParsingException {
        return streamResponse.streamTitle();
    }
}
