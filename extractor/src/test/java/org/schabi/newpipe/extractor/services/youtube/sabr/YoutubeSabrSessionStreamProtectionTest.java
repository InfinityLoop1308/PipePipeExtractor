package org.schabi.newpipe.extractor.services.youtube.sabr;

import com.grack.nanojson.JsonArray;
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

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class YoutubeSabrSessionStreamProtectionTest {
    private static final Localization LOCALIZATION = new Localization("en", "US");

    @Test
    void pendingAttestationDefersBackoffWithoutRefreshingToken() throws Exception {
        final FakeDownloader downloader = new FakeDownloader(
                protectionResponse(SabrStreamProtectionStatus.ATTESTATION_PENDING, 20, 2_000));
        final AtomicInteger tokenRequests = new AtomicInteger();
        final Fixture fixture = createFixture(downloader, (info, state) -> {
            tokenRequests.incrementAndGet();
            return new byte[]{9, 9, 9};
        });
        fixture.session.getStreamState().setPoToken(new byte[]{1, 2, 3});

        final YoutubeSabrSession.DemandResponseResult result =
                fixture.session.pumpOnceStreamingForDemand(LOCALIZATION,
                        SabrSegmentRequest.media(fixture.videoFormat, 1));

        assertTrue(result.wasRequestPerformed());
        assertEquals(0, result.getSegmentCount());
        assertEquals(0, tokenRequests.get());
        assertEquals(1, downloader.requestCount.get());
        assertEquals(SabrStreamProtectionStatus.ATTESTATION_PENDING,
                fixture.session.getMaxStreamProtectionStatus());
        assertTrue(fixture.session.getDemandBackoffRemainingMs() > 0);
        assertTrue(fixture.session.getDiagnosticTrace().contains("protection=2/20"));
    }

    @Test
    void rejectedAttestationTerminatesBeforeBackoff() throws Exception {
        final FakeDownloader downloader = new FakeDownloader(
                protectionResponse(SabrStreamProtectionStatus.ATTESTATION_REQUIRED, 20, 59_000));
        final Fixture fixture = createFixture(downloader, null);

        final SabrProtocolException failure = assertThrows(SabrProtocolException.class,
                () -> fixture.session.pumpOnceStreamingForDemand(LOCALIZATION,
                        SabrSegmentRequest.media(fixture.videoFormat, 1)));

        assertTrue(failure.getMessage().contains("attestation required"));
        assertEquals(1, downloader.requestCount.get());
        assertEquals(SabrStreamProtectionStatus.ATTESTATION_REQUIRED,
                fixture.session.getMaxStreamProtectionStatus());
        assertEquals(0, fixture.session.getDemandBackoffRemainingMs());
        assertTrue(fixture.session.getDiagnosticTrace().contains("protection=3/20"));
    }

    @Test
    void repeatedPendingAttestationFailsAtIndependentDeadLoopLimit() throws Exception {
        final FakeDownloader downloader = new FakeDownloader(
                protectionResponse(SabrStreamProtectionStatus.ATTESTATION_PENDING, 20, 0));
        final Fixture fixture = createFixture(downloader, null);
        final SabrSegmentRequest request = SabrSegmentRequest.media(fixture.videoFormat, 1);

        fixture.session.pumpOnceStreamingForDemand(LOCALIZATION, request);
        fixture.session.pumpOnceStreamingForDemand(LOCALIZATION, request);
        final SabrProtocolException failure = assertThrows(SabrProtocolException.class,
                () -> fixture.session.pumpOnceStreamingForDemand(LOCALIZATION, request));

        assertTrue(failure.getMessage().contains("pending without media for 3"));
        assertEquals(3, downloader.requestCount.get());
        assertTrue(fixture.session.getDiagnosticTrace()
                .contains("attestation_pending_no_media count=3"));
    }

    @Nonnull
    private static Fixture createFixture(@Nonnull final Downloader downloader,
                                         @Nullable final SabrPoTokenProvider tokenProvider)
            throws Exception {
        NewPipe.init(downloader);
        final JsonArray formats = JsonParser.array().from("["
                + "{\"itag\":140,\"lastModified\":\"1\",\"mimeType\":\"audio/mp4\","
                + "\"bitrate\":128000,\"contentLength\":\"1000\","
                + "\"approxDurationMs\":\"60000\"},"
                + "{\"itag\":299,\"lastModified\":\"2\",\"mimeType\":\"video/mp4\","
                + "\"width\":1920,\"height\":1080,\"bitrate\":4000000,"
                + "\"contentLength\":\"2000\",\"approxDurationMs\":\"60000\"}]");
        final List<YoutubeSabrFormat> parsedFormats =
                YoutubeSabrFormat.fromAdaptiveFormats("video", formats);
        final YoutubeSabrInfo info = new YoutubeSabrInfo(
                YoutubeSabrClientProfile.MWEB,
                "video",
                "cpn",
                YoutubeSabrClientProfile.MWEB.getClientVersion(),
                "visitor",
                "https://example.com/sabr",
                "AA==",
                parsedFormats);
        return new Fixture(new YoutubeSabrSession(
                info, parsedFormats.get(0), parsedFormats.get(1), tokenProvider, null),
                parsedFormats.get(1));
    }

    @Nonnull
    private static byte[] protectionResponse(final int status,
                                             final int maxRetries,
                                             final int backoffMs) {
        final SabrProto.Writer protection = new SabrProto.Writer();
        protection.writeInt32(1, status);
        protection.writeInt32(2, maxRetries);
        final SabrProto.Writer policy = new SabrProto.Writer();
        policy.writeInt32(4, backoffMs);

        final ByteArrayOutputStream output = new ByteArrayOutputStream();
        writeUmpPart(output, SabrResponseDecoder.STREAM_PROTECTION_STATUS,
                protection.toByteArray());
        writeUmpPart(output, SabrResponseDecoder.NEXT_REQUEST_POLICY, policy.toByteArray());
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
        @Nonnull private final YoutubeSabrSession session;
        @Nonnull private final YoutubeSabrFormat videoFormat;

        private Fixture(@Nonnull final YoutubeSabrSession session,
                        @Nonnull final YoutubeSabrFormat videoFormat) {
            this.session = session;
            this.videoFormat = videoFormat;
        }
    }

    private static final class FakeDownloader extends Downloader {
        @Nonnull private final byte[] responseBody;
        @Nonnull private final AtomicInteger requestCount = new AtomicInteger();

        private FakeDownloader(@Nonnull final byte[] responseBody) {
            this.responseBody = responseBody.clone();
        }

        @Override
        public StreamingResponse postStreaming(
                final String url,
                @Nullable final Map<String, List<String>> headers,
                @Nullable final byte[] dataToSend,
                @Nullable final Localization localization) {
            requestCount.incrementAndGet();
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
