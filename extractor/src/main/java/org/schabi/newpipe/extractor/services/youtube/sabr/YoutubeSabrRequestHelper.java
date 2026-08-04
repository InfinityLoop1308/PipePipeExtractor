package org.schabi.newpipe.extractor.services.youtube.sabr;

import org.schabi.newpipe.extractor.NewPipe;
import org.schabi.newpipe.extractor.downloader.StreamingResponse;
import org.schabi.newpipe.extractor.exceptions.ExtractionException;
import org.schabi.newpipe.extractor.localization.Localization;
import org.schabi.newpipe.extractor.services.youtube.sabr.exception.SabrProtocolException;
import org.schabi.newpipe.extractor.services.youtube.sabr.protocol.SabrProto;
import org.schabi.newpipe.extractor.services.youtube.sabr.protocol.SabrStreamingResponseReader;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.io.File;
import java.io.FilterInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import static org.schabi.newpipe.extractor.services.youtube.YoutubeParsingHelper.MWEB_USER_AGENT;

final class YoutubeSabrRequestHelper {
    private static final int MWEB_CLIENT_ID = 2;

    private YoutubeSabrRequestHelper() {
    }

    @Nonnull
    static YoutubeSabrResponse post(@Nonnull final YoutubeSabrInfo info,
                                    @Nonnull final YoutubeSabrInfo.Format audioFormat,
                                    @Nonnull final YoutubeSabrInfo.Format videoFormat,
                                    @Nonnull final YoutubeSabrStreamState streamState,
                                    @Nonnull final String serverAbrStreamingUrl,
                                    final int requestNumber,
                                    @Nonnull final Localization localization,
                                    @Nullable final SabrStreamingResponseReader
                                            .StoppableSegmentConsumer segmentConsumer,
                                    @Nullable final SabrStreamingResponseReader
                                            .SegmentConsumer segmentStartConsumer,
                                    @Nullable final File segmentSpoolDirectory)
            throws IOException, ExtractionException {
        final byte[] requestBody = buildMediaRequest(
                info, audioFormat, videoFormat, streamState, requestNumber > 0);
        final long requestStartNs = System.nanoTime();
        final long[] firstSegmentElapsedMs = {-1};
        final SabrStreamingResponseReader.StoppableSegmentConsumer timedConsumer =
                segmentConsumer == null ? null : segment -> {
                    if (firstSegmentElapsedMs[0] < 0) {
                        firstSegmentElapsedMs[0] = elapsedMs(requestStartNs);
                    }
                    return segmentConsumer.accept(segment);
                };
        final SabrStreamingResponseReader.SegmentConsumer timedStartConsumer =
                segmentStartConsumer == null ? null : segment -> {
                    if (firstSegmentElapsedMs[0] < 0) {
                        firstSegmentElapsedMs[0] = elapsedMs(requestStartNs);
                    }
                    segmentStartConsumer.accept(segment);
                };
        try (StreamingResponse response = NewPipe.getDownloader().postStreaming(
                withSessionParameters(serverAbrStreamingUrl, info.getCpn(), requestNumber),
                buildRequestHeaders(), requestBody, localization)) {
            final String contentType = response.getHeader("Content-Type");
            if (contentType == null
                    || !contentType.toLowerCase().contains("application/vnd.yt-ump")) {
                throw new SabrProtocolException("Expected UMP response, got content type: "
                        + contentType + ", status=" + response.responseCode());
            }
            final CountingInputStream body = new CountingInputStream(response.body());
            final SabrStreamingResponseReader.Result streamed =
                    SabrStreamingResponseReader.readUntil(body, timedConsumer,
                            timedStartConsumer, segmentSpoolDirectory);
            final YoutubeSabrResponse result = streamed.getProbeResult();
            result.complete(info, streamed.getSegments(), streamed.getSegmentCount(),
                    response.responseCode(), contentType, body.getCount(),
                    streamed.getMediaPayloadBytes(), streamed.getMediaPartPayloadBytes(),
                    streamed.getControlPayloadBytes(), streamed.getTotalPayloadBytes(),
                    streamed.getMaxPartBytes(), streamed.getMaxMediaPartPayloadBytes(),
                    streamed.getMaxSegmentBytes(), elapsedMs(requestStartNs),
                    firstSegmentElapsedMs[0]);
            return result;
        }
    }

    @Nonnull
    private static byte[] buildMediaRequest(@Nonnull final YoutubeSabrInfo info,
                                            @Nonnull final YoutubeSabrInfo.Format audioFormat,
                                            @Nonnull final YoutubeSabrInfo.Format videoFormat,
                                            @Nonnull final YoutubeSabrStreamState streamState,
                                            final boolean followUp)
            throws SabrProtocolException {
        synchronized (streamState) {
            return buildMediaRequestLocked(
                    info, audioFormat, videoFormat, streamState, followUp);
        }
    }

    @Nonnull
    private static byte[] buildMediaRequestLocked(@Nonnull final YoutubeSabrInfo info,
                                                  @Nonnull final YoutubeSabrInfo.Format audioFormat,
                                                  @Nonnull final YoutubeSabrInfo.Format videoFormat,
                                                  @Nonnull final YoutubeSabrStreamState streamState,
                                                  final boolean followUp)
            throws SabrProtocolException {
        final String ustreamerConfig = info.getVideoPlaybackUstreamerConfig();
        if (ustreamerConfig == null || ustreamerConfig.isEmpty()) {
            throw new SabrProtocolException("Missing video playback ustreamer config");
        }

        final long playerTimeMs = streamState.getRequestPlayerTimeMs();
        final List<byte[]> bufferedRanges = new ArrayList<>();
        streamState.forEachBufferedRange((itag, lastModified, xtags, startTimeMs, durationMs,
                                          startSegmentIndex, endSegmentIndex, timescale) ->
                bufferedRanges.add(buildBufferedRange(itag, lastModified, xtags, startTimeMs,
                        durationMs, startSegmentIndex, endSegmentIndex, timescale)));
        final boolean forcedInitialPlaybackState = !followUp
                && streamState.shouldWriteFirstRequestPlaybackState();
        final boolean includePlaybackState = followUp || forcedInitialPlaybackState
                || playerTimeMs > 0 || !bufferedRanges.isEmpty();
        final SabrProto.Writer request = new SabrProto.Writer();
        request.writeMessage(1, buildClientAbrState(audioFormat, videoFormat, playerTimeMs,
                followUp || includePlaybackState && !forcedInitialPlaybackState,
                streamState.getEnabledTrackTypesBitfield(), streamState));
        if (includePlaybackState) {
            if (streamState.shouldSelectVideoFormatBeforeAudio()
                    && streamState.shouldSelectVideoFormat()
                    && streamState.isInitialized(videoFormat)) {
                request.writeMessage(2, SabrProto.formatId(videoFormat));
            }
            if (streamState.shouldSelectAudioFormat() && streamState.isInitialized(audioFormat)) {
                request.writeMessage(2, SabrProto.formatId(audioFormat));
            }
            if (!streamState.shouldSelectVideoFormatBeforeAudio()
                    && streamState.shouldSelectVideoFormat()
                    && streamState.isInitialized(videoFormat)) {
                request.writeMessage(2, SabrProto.formatId(videoFormat));
            }
            for (final byte[] range : bufferedRanges) {
                request.writeMessage(3, range);
            }
            if (streamState.shouldWriteTopLevelPlayerTimeMs()) {
                request.writeUInt64(4, playerTimeMs);
            }
        }
        request.writeBytes(5, decodeBase64(ustreamerConfig));
        writePreferredFormats(request, audioFormat, videoFormat, streamState);
        request.writeMessage(19, buildStreamerContext(info, streamState));
        return request.toByteArray();
    }

    @Nonnull
    private static byte[] buildClientAbrState(@Nonnull final YoutubeSabrInfo.Format audioFormat,
                                               @Nonnull final YoutubeSabrInfo.Format videoFormat,
                                               final long playerTimeMs,
                                               final boolean includeFollowUpState,
                                               final int enabledTrackTypesBitfield,
                                               @Nonnull final YoutubeSabrStreamState streamState) {
        final SabrProto.Writer state = new SabrProto.Writer();
        if (includeFollowUpState && streamState.shouldWriteLastManualSelectedResolution()) {
            state.writeInt32(16, Math.max(videoFormat.getHeight(), 360));
        }
        if (includeFollowUpState) {
            state.writeInt32(18, Math.max(videoFormat.getWidth(), 640));
            state.writeInt32(19, Math.max(videoFormat.getHeight(), 360));
        }
        state.writeInt32(21, Math.max(videoFormat.getHeight(), 360));
        if (includeFollowUpState) {
            final long bandwidthEstimate = streamState.getBandwidthEstimate() > 0
                    ? streamState.getBandwidthEstimate()
                    : audioFormat.getBitrate() > 0 && videoFormat.getBitrate() > 0
                    ? (audioFormat.getBitrate() + videoFormat.getBitrate()) * 2L
                    : -1;
            if (bandwidthEstimate > 0) {
                state.writeUInt64(23, bandwidthEstimate);
            }
        }
        state.writeInt32(34, 1);
        state.writeFloat(35, streamState.getPlaybackRate());
        if (enabledTrackTypesBitfield != YoutubeSabrStreamState.TRACK_MODE_VIDEO_AND_AUDIO) {
            state.writeInt32(40, enabledTrackTypesBitfield);
        }
        if (audioFormat.isDrc()) {
            state.writeBool(46, true);
        }
        state.writeUInt64(28, playerTimeMs);
        state.writeStringIfNotEmpty(69, audioFormat.getAudioTrackId());
        return state.toByteArray();
    }

    private static void writePreferredFormats(@Nonnull final SabrProto.Writer request,
                                              @Nonnull final YoutubeSabrInfo.Format audioFormat,
                                              @Nonnull final YoutubeSabrInfo.Format videoFormat,
                                              @Nonnull final YoutubeSabrStreamState streamState) {
        if (streamState.shouldPreferAudioFormat()) {
            request.writeMessage(16, SabrProto.formatId(audioFormat));
        }
        if (streamState.shouldPreferVideoFormat()) {
            request.writeMessage(17, SabrProto.formatId(videoFormat));
        }
    }

    @Nonnull
    private static byte[] buildStreamerContext(@Nonnull final YoutubeSabrInfo info,
                                               @Nonnull final YoutubeSabrStreamState streamState) {
        final SabrProto.Writer context = new SabrProto.Writer();
        context.writeMessage(1, buildClientInfo(info));
        final byte[] poToken = streamState.getRawPoToken();
        if (poToken != null && poToken.length > 0) {
            context.writeBytes(2, poToken);
        }
        final byte[] playbackCookie = streamState.getRawPlaybackCookie();
        if (playbackCookie != null && playbackCookie.length > 0) {
            context.writeBytes(3, playbackCookie);
        }
        for (final Map.Entry<Integer, byte[]> entry
                : streamState.getActiveSabrContexts().entrySet()) {
            final SabrProto.Writer sabrContext = new SabrProto.Writer();
            sabrContext.writeInt32(1, entry.getKey());
            sabrContext.writeBytes(2, entry.getValue());
            context.writeMessage(5, sabrContext.toByteArray());
        }
        for (final Integer type : streamState.getUnsentSabrContextTypes()) {
            context.writeInt32(6, type);
        }
        return context.toByteArray();
    }

    @Nonnull
    private static byte[] buildClientInfo(@Nonnull final YoutubeSabrInfo info) {
        final SabrProto.Writer client = new SabrProto.Writer();
        client.writeInt32(16, MWEB_CLIENT_ID);
        client.writeStringIfNotEmpty(17, info.getClientVersion());
        client.writeStringIfNotEmpty(21, "en-US");
        client.writeStringIfNotEmpty(22, "US");
        return client.toByteArray();
    }

    @Nonnull
    private static byte[] buildBufferedRange(final int itag,
                                             final long lastModified,
                                             @Nullable final String xtags,
                                             final long startTimeMs,
                                             final long durationMs,
                                             final int startSegmentIndex,
                                             final int endSegmentIndex,
                                             final int timescale) {
        final SabrProto.Writer format = new SabrProto.Writer();
        format.writeInt32(1, itag);
        if (lastModified > 0) {
            format.writeUInt64(2, lastModified);
        }
        format.writeStringIfNotEmpty(3, xtags);

        final SabrProto.Writer timeRange = new SabrProto.Writer();
        timeRange.writeUInt64(1, startTimeMs);
        timeRange.writeUInt64(2, durationMs);
        timeRange.writeInt32(3, timescale);

        final SabrProto.Writer range = new SabrProto.Writer();
        range.writeMessage(1, format.toByteArray());
        range.writeUInt64(2, startTimeMs);
        range.writeUInt64(3, durationMs);
        range.writeInt32(4, startSegmentIndex);
        range.writeInt32(5, endSegmentIndex);
        range.writeMessage(6, timeRange.toByteArray());
        return range.toByteArray();
    }

    @Nonnull
    private static Map<String, List<String>> buildRequestHeaders() {
        final Map<String, List<String>> headers = new HashMap<>();
        headers.put("Content-Type", Collections.singletonList("application/x-protobuf"));
        headers.put("Accept", Collections.singletonList("application/vnd.yt-ump"));
        headers.put("Accept-Encoding", Collections.singletonList("identity"));
        headers.put("User-Agent", Collections.singletonList(MWEB_USER_AGENT));
        return headers;
    }

    @Nonnull
    private static String withSessionParameters(@Nonnull final String url,
                                                @Nonnull final String cpn,
                                                final int requestNumber) {
        String result = appendQueryParameterIfMissing(url, "alr", "yes");
        result = appendQueryParameterIfMissing(result, "cpn", cpn);
        return setQueryParameter(result, "rn", String.valueOf(requestNumber));
    }

    @Nonnull
    private static String appendQueryParameterIfMissing(@Nonnull final String url,
                                                        @Nonnull final String name,
                                                        @Nonnull final String value) {
        if (url.matches(".*(?:[?&])" + name + "=[^&]*.*")) {
            return url;
        }
        return url + (url.contains("?") ? '&' : '?') + name + '=' + value;
    }

    @Nonnull
    private static String setQueryParameter(@Nonnull final String url,
                                            @Nonnull final String name,
                                            @Nonnull final String value) {
        final int fragmentIndex = url.indexOf('#');
        final String baseUrl = fragmentIndex < 0 ? url : url.substring(0, fragmentIndex);
        final String fragment = fragmentIndex < 0 ? "" : url.substring(fragmentIndex);
        final int queryIndex = baseUrl.indexOf('?');
        final String path = queryIndex < 0 ? baseUrl : baseUrl.substring(0, queryIndex);
        final String query = queryIndex < 0 ? "" : baseUrl.substring(queryIndex + 1);
        final StringBuilder result = new StringBuilder(path).append('?');
        boolean wroteParameter = false;
        for (final String parameter : query.split("&", -1)) {
            if (parameter.isEmpty()) {
                continue;
            }
            final int equalsIndex = parameter.indexOf('=');
            final String parameterName = equalsIndex < 0
                    ? parameter : parameter.substring(0, equalsIndex);
            if (parameterName.equals(name)) {
                continue;
            }
            if (wroteParameter) {
                result.append('&');
            }
            result.append(parameter);
            wroteParameter = true;
        }
        if (wroteParameter) {
            result.append('&');
        }
        return result.append(name).append('=').append(value).append(fragment).toString();
    }

    @Nonnull
    private static byte[] decodeBase64(@Nonnull final String value) throws SabrProtocolException {
        try {
            return Base64.getDecoder().decode(padBase64(value));
        } catch (final IllegalArgumentException first) {
            try {
                return Base64.getUrlDecoder().decode(padBase64(value));
            } catch (final IllegalArgumentException second) {
                throw new SabrProtocolException("Could not decode base64 ustreamer config", second);
            }
        }
    }

    @Nonnull
    private static String padBase64(@Nonnull final String value) {
        final int padding = (4 - value.length() % 4) % 4;
        final StringBuilder builder = new StringBuilder(value);
        for (int i = 0; i < padding; i++) {
            builder.append('=');
        }
        return builder.toString();
    }

    private static long elapsedMs(final long startNs) {
        return Math.max(0L, TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startNs));
    }

    private static final class CountingInputStream extends FilterInputStream {
        private long count;

        private CountingInputStream(@Nonnull final InputStream input) {
            super(input);
        }

        @Override
        public int read() throws IOException {
            final int value = super.read();
            if (value >= 0) {
                count++;
            }
            return value;
        }

        @Override
        public int read(@Nonnull final byte[] buffer, final int offset, final int length)
                throws IOException {
            final int read = super.read(buffer, offset, length);
            if (read > 0) {
                count += read;
            }
            return read;
        }

        private long getCount() {
            return count;
        }
    }
}
