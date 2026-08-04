package org.schabi.newpipe.extractor.services.youtube.sabr;

import com.grack.nanojson.JsonArray;
import com.grack.nanojson.JsonObject;
import com.grack.nanojson.JsonParser;
import org.junit.jupiter.api.Test;
import org.schabi.newpipe.extractor.NewPipe;
import org.schabi.newpipe.extractor.downloader.CancellableCall;
import org.schabi.newpipe.extractor.downloader.Downloader;
import org.schabi.newpipe.extractor.downloader.Request;
import org.schabi.newpipe.extractor.downloader.Response;
import org.schabi.newpipe.extractor.downloader.StreamingResponse;
import org.schabi.newpipe.extractor.exceptions.ReCaptchaException;
import org.schabi.newpipe.extractor.localization.Localization;
import org.schabi.newpipe.extractor.services.youtube.sabr.media.SabrMediaSegment;
import org.schabi.newpipe.extractor.services.youtube.sabr.protocol.SabrProto;
import org.schabi.newpipe.extractor.services.youtube.sabr.protocol.SabrResponseDecoder;
import org.schabi.newpipe.extractor.services.youtube.extractors.YoutubeStreamExtractor;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class YoutubeSabrSessionInitializationStateTest {
    private static final int AUDIO_ITAG = 140;
    private static final int VIDEO_ITAG = 299;
    private static final byte[] INITIALIZATION_DATA = {1, 2, 3};

    @Test
    void successfulInitializationFetchRestoresExactRequestState() throws Exception {
        final Fixture fixture = createFixture(new FakeDownloader(initializationResponse(AUDIO_ITAG)));
        final YoutubeSabrStreamState state = fixture.session.getStreamState();
        state.setRequestTrackMode(YoutubeSabrStreamState.TRACK_MODE_AUDIO_ONLY, true, false);
        state.setPreferredTrackTypes(true, false);
        state.setPlayerTimeMs(12_345);
        state.setSuppressBufferedRanges(true);
        state.setWriteFirstRequestPlaybackState(false);

        final SabrMediaSegment segment = fixture.session.fetchSegment(
                SabrSegmentRequest.initialization(fixture.audioFormat), Localization.DEFAULT);

        assertArrayEquals(INITIALIZATION_DATA, segment.getData());
        assertRequestState(state, 0, false, true, false);
    }

    @Test
    void failedInitializationFetchRestoresExactRequestState() throws Exception {
        final Fixture fixture = createFixture(new FakeDownloader(new IOException("test failure")));
        final YoutubeSabrStreamState state = fixture.session.getStreamState();
        state.setRequestTrackMode(YoutubeSabrStreamState.TRACK_MODE_AUDIO_ONLY, true, false);
        state.setPreferredTrackTypes(true, false);
        state.setPlayerTimeMs(12_345);
        state.setSuppressBufferedRanges(false);
        state.setWriteFirstRequestPlaybackState(true);

        assertThrows(IOException.class, () -> fixture.session.fetchSegment(
                SabrSegmentRequest.initialization(fixture.videoFormat), Localization.DEFAULT));

        assertRequestState(state, 0, true, true, false);
    }

    private static void assertRequestState(@Nonnull final YoutubeSabrStreamState state,
                                           final int bufferedRangeCount,
                                           final boolean writeFirstRequestPlaybackState,
                                           final boolean writeTopLevelPlayerTimeMs,
                                           final boolean writeLastManualSelectedResolution) {
        assertEquals(YoutubeSabrStreamState.TRACK_MODE_AUDIO_ONLY,
                state.getEnabledTrackTypesBitfield());
        assertTrue(state.shouldSelectAudioFormat());
        assertFalse(state.shouldSelectVideoFormat());
        assertFalse(state.shouldPreferAudioFormat());
        assertTrue(state.shouldPreferVideoFormat());
        assertEquals(12_345, state.getPlayerTimeMs());
        final int[] actualBufferedRangeCount = {0};
        state.forEachBufferedRange((itag, lastModified, xtags, startTimeMs, durationMs,
                                    startSegmentIndex, endSegmentIndex, timescale) ->
                actualBufferedRangeCount[0]++);
        assertEquals(bufferedRangeCount, actualBufferedRangeCount[0]);
        assertEquals(writeFirstRequestPlaybackState,
                state.shouldWriteFirstRequestPlaybackState());
        assertEquals(writeTopLevelPlayerTimeMs, state.shouldWriteTopLevelPlayerTimeMs());
        assertEquals(writeLastManualSelectedResolution,
                state.shouldWriteLastManualSelectedResolution());
    }

    @Nonnull
    private static Fixture createFixture(@Nonnull final Downloader downloader) throws Exception {
        NewPipe.init(downloader);
        final JsonArray formats = JsonParser.array().from("["
                + "{\"itag\":140,\"lastModified\":\"1\",\"mimeType\":\"audio/mp4\","
                + "\"bitrate\":128000,\"contentLength\":\"1000\","
                + "\"approxDurationMs\":\"60000\"},"
                + "{\"itag\":299,\"lastModified\":\"2\",\"mimeType\":\"video/mp4\","
                + "\"width\":1920,\"height\":1080,\"bitrate\":4000000,"
                + "\"contentLength\":\"2000\",\"approxDurationMs\":\"60000\"}]");
        final List<YoutubeSabrInfo.Format> parsedFormats = parseFormats(formats);
        final YoutubeSabrInfo.Format audioFormat = parsedFormats.get(0);
        final YoutubeSabrInfo.Format videoFormat = parsedFormats.get(1);
        final YoutubeSabrInfo info = new YoutubeSabrInfo(
                "video",
                "cpn",
                "2.20250122.04.00",
                "visitor",
                "https://example.com/sabr",
                "AA==",
                parsedFormats);
        return new Fixture(
                new YoutubeSabrSession(info, audioFormat, videoFormat),
                audioFormat,
                videoFormat);
    }

    @Nonnull
    private static List<YoutubeSabrInfo.Format> parseFormats(@Nonnull final JsonArray formats)
            throws Exception {
        final JsonObject streamingData = new JsonObject();
        streamingData.put("serverAbrStreamingUrl", "https://example.com/sabr");
        streamingData.put("adaptiveFormats", formats);
        final JsonObject response = new JsonObject();
        response.put("streamingData", streamingData);
        return YoutubeStreamExtractor.buildSabrInfoFromPlayerResponse(
                "video", "cpn", response, null, "2.20250122.04.00").getFormats();
    }

    @Nonnull
    private static byte[] initializationResponse(final int itag) {
        final SabrProto.Writer header = new SabrProto.Writer();
        header.writeInt32(1, 1);
        header.writeInt32(3, itag);
        header.writeBool(8, true);
        header.writeUInt64(14, INITIALIZATION_DATA.length);

        final ByteArrayOutputStream output = new ByteArrayOutputStream();
        writeUmpPart(output, SabrResponseDecoder.MEDIA_HEADER, header.toByteArray());
        final byte[] media = new byte[INITIALIZATION_DATA.length + 1];
        media[0] = 1;
        System.arraycopy(INITIALIZATION_DATA, 0, media, 1, INITIALIZATION_DATA.length);
        writeUmpPart(output, SabrResponseDecoder.MEDIA, media);
        writeUmpPart(output, SabrResponseDecoder.MEDIA_END, new byte[]{1});
        return output.toByteArray();
    }

    private static void writeUmpPart(@Nonnull final ByteArrayOutputStream output,
                                     final int type,
                                     @Nonnull final byte[] payload) {
        if (type >= 128 || payload.length >= 128) {
            throw new IllegalArgumentException("Test UMP part exceeds single-byte encoding");
        }
        output.write(type);
        output.write(payload.length);
        output.write(payload, 0, payload.length);
    }

    private static final class Fixture {
        @Nonnull
        private final YoutubeSabrSession session;
        @Nonnull
        private final YoutubeSabrInfo.Format audioFormat;
        @Nonnull
        private final YoutubeSabrInfo.Format videoFormat;

        private Fixture(@Nonnull final YoutubeSabrSession session,
                        @Nonnull final YoutubeSabrInfo.Format audioFormat,
                        @Nonnull final YoutubeSabrInfo.Format videoFormat) {
            this.session = session;
            this.audioFormat = audioFormat;
            this.videoFormat = videoFormat;
        }
    }

    private static final class FakeDownloader extends Downloader {
        @Nullable
        private final byte[] responseBody;
        @Nullable
        private final IOException failure;

        private FakeDownloader(@Nonnull final byte[] responseBody) {
            this.responseBody = Arrays.copyOf(responseBody, responseBody.length);
            this.failure = null;
        }

        private FakeDownloader(@Nonnull final IOException failure) {
            this.responseBody = null;
            this.failure = failure;
        }

        @Override
        public StreamingResponse postStreaming(
                final String url,
                @Nullable final Map<String, List<String>> headers,
                @Nullable final byte[] dataToSend,
                @Nullable final Localization localization) throws IOException {
            if (failure != null) {
                throw failure;
            }
            return new StreamingResponse(
                    200,
                    Collections.singletonMap("Content-Type",
                            Collections.singletonList("application/vnd.yt-ump")),
                    new ByteArrayInputStream(responseBody));
        }

        @Override
        public Response execute(@Nonnull final Request request)
                throws IOException, ReCaptchaException {
            throw new UnsupportedOperationException();
        }

        @Override
        public CancellableCall executeAsync(@Nonnull final Request request,
                                            final AsyncCallback callback)
                throws IOException, ReCaptchaException {
            throw new UnsupportedOperationException();
        }
    }
}
