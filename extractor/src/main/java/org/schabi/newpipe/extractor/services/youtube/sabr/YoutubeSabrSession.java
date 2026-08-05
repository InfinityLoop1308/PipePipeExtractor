package org.schabi.newpipe.extractor.services.youtube.sabr;

import org.schabi.newpipe.extractor.services.youtube.sabr.exception.SabrProtocolException;
import org.schabi.newpipe.extractor.services.youtube.sabr.exception.SabrRecoverableException;
import org.schabi.newpipe.extractor.services.youtube.sabr.media.SabrMediaSegment;
import org.schabi.newpipe.extractor.services.youtube.sabr.protocol.SabrStreamingResponseReader;
import org.schabi.newpipe.extractor.services.youtube.sabr.protocol.SabrProto;

import org.schabi.newpipe.extractor.NewPipe;
import org.schabi.newpipe.extractor.downloader.StreamingResponse;
import org.schabi.newpipe.extractor.exceptions.ExtractionException;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.io.File;
import java.io.IOException;
import java.io.InterruptedIOException;
import java.net.URI;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Collections;
import java.util.List;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Collection;
import java.util.Set;
import java.util.Map;
import java.util.Objects;

public final class YoutubeSabrSession {
    @FunctionalInterface
    public interface PoTokenRefresher {
        @Nonnull
        byte[] refresh() throws IOException, ExtractionException;
    }

    @FunctionalInterface
    public interface IdentityRefresher {
        @Nonnull
        SessionIdentity refresh() throws IOException, ExtractionException;
    }

    public static final class SessionIdentity {
        @Nonnull private final YoutubeSabrInfo info;
        @Nonnull private final byte[] poToken;

        public SessionIdentity(@Nonnull final YoutubeSabrInfo info,
                               @Nonnull final byte[] poToken) {
            this.info = info;
            this.poToken = poToken.clone();
        }
    }

    // -------------------------------------------------------------------------
    // Session configuration and protocol state
    // -------------------------------------------------------------------------

    private static final int MAX_REDIRECTS_PER_SESSION = 3;
    private static final int MAX_INCOMPLETE_MEDIA_RESPONSES = 3;
    private static final int MAX_CONSECUTIVE_ATTESTATION_PENDING_RESPONSES = 3;
    private static final int MAX_BACKOFF_MS = 30_000;
    private static final int MAX_INITIALIZATION_BYTES = 4 * 1024 * 1024;
    @Nonnull
    private YoutubeSabrInfo info;
    @Nullable
    private final YoutubeSabrInfo.Format audioFormat;
    @Nullable
    private final YoutubeSabrInfo.Format videoFormat;
    @Nullable
    private final File segmentSpoolDirectory;
    @Nonnull
    private String serverAbrStreamingUrl;
    private int requestNumber;
    private int redirectCount;
    private int consecutiveIntegrityFailures;
    private int consecutiveAttestationPendingResponses;
    private int consecutiveAttestationTokenRefreshes;
    @Nullable
    private byte[] playbackCookie;
    private final Map<Integer, byte[]> sabrContexts = new LinkedHashMap<>();
    private final Set<Integer> activeSabrContextTypes = new LinkedHashSet<>();
    private boolean live;
    private boolean postLiveDvr;
    private long liveHeadSequenceNumber = -1;
    private long liveHeadTimeMs = -1;
    private long bandwidthEstimate = -1;
    private volatile long backoffDeadlineNs;
    @Nullable private byte[] poToken;
    @Nullable private PoTokenRefresher poTokenRefresher;
    @Nullable private IdentityRefresher identityRefresher;

    // -------------------------------------------------------------------------
    // Construction
    // -------------------------------------------------------------------------

    public YoutubeSabrSession(@Nonnull final YoutubeSabrInfo info,
                               @Nullable final YoutubeSabrInfo.Format audioFormat,
                               @Nullable final YoutubeSabrInfo.Format videoFormat) {
        this(info, audioFormat, videoFormat, (File) null);
    }

    public YoutubeSabrSession(@Nonnull final YoutubeSabrInfo info,
                              @Nullable final YoutubeSabrInfo.Format audioFormat,
                              @Nullable final YoutubeSabrInfo.Format videoFormat,
                              @Nullable final File segmentSpoolDirectory) {
        validateFormats(audioFormat, videoFormat);
        if (info.getServerAbrStreamingUrl() == null || info.getServerAbrStreamingUrl().isEmpty()) {
            throw new IllegalArgumentException("Missing SABR streaming URL");
        }
        this.info = info;
        this.audioFormat = audioFormat;
        this.videoFormat = videoFormat;
        this.segmentSpoolDirectory = segmentSpoolDirectory;
        this.serverAbrStreamingUrl = info.getServerAbrStreamingUrl();
    }

    // -------------------------------------------------------------------------
    // SABR transactions
    // -------------------------------------------------------------------------

    @Nonnull
    private YoutubeSabrResponse fetchNextResponse(
            @Nullable final YoutubeSabrInfo.Format requestedAudioFormat,
            @Nullable final YoutubeSabrInfo.Format requestedVideoFormat,
            final long playerTimeMs,
            @Nullable final YoutubeSabrFormatTimeline audioTimeline,
            final int audioBufferedThrough,
            @Nullable final YoutubeSabrFormatTimeline videoTimeline,
            final int videoBufferedThrough,
            final boolean audioActive,
            final boolean videoActive,
            final boolean videoFirst,
            final float playbackRate,
            @Nullable final SabrStreamingResponseReader.SegmentConsumer segmentConsumer)
            throws IOException, ExtractionException {
        addDiagnosticEvent("request n=" + requestNumber
                + " playerMs=" + playerTimeMs
                + " audioThrough=" + audioBufferedThrough
                + " videoThrough=" + videoBufferedThrough
                + " poTokenBytes=" + (poToken == null ? -1 : poToken.length));
        final SabrStreamingResponseReader.SegmentConsumer timedConsumer = segmentConsumer;
        final SabrStreamingResponseReader.SegmentConsumer startedConsumer = segment -> { };
        final YoutubeSabrResponse result;
        try {
            result = YoutubeSabrRequestHelper.post(info, requestedAudioFormat,
                    requestedVideoFormat, this,
                    playerTimeMs, audioTimeline, audioBufferedThrough,
                    videoTimeline, videoBufferedThrough, audioActive, videoActive, videoFirst,
                    playbackRate,
                    serverAbrStreamingUrl, requestNumber, timedConsumer,
                    startedConsumer, segmentSpoolDirectory);
        } catch (final IOException | ExtractionException e) {
            addDiagnosticEvent("request_failed n=" + requestNumber
                    + " type=" + e.getClass().getSimpleName()
                    + " message=" + String.valueOf(e.getMessage()));
            throw e;
        }
        addDiagnosticEvent("response n=" + requestNumber
                + " http=" + result.getResponseCode()
                + " contentType=" + result.getContentType()
                + " segments=" + (result.getSegments().isEmpty()
                ? "count=" + result.getSegmentCount() : summarizeSegments(result.getSegments()))
                + " decoded={" + result.summarizeForDiagnostics() + '}');
        if (result.getBackoffTimeMs() > MAX_BACKOFF_MS) {
            throw new SabrProtocolException("SABR backoff exceeds limit: "
                    + result.getBackoffTimeMs() + "ms");
        }
        updateBackoff(result.getBackoffTimeMs());
        diagnostics.recordResponse(result, requestNumber);
        updateBandwidthEstimate(result.getResponseBytes(), result.getRequestElapsedMs());
        requestNumber++;
        return result;
    }

    public synchronized RequestResult requestOnce(
            final long playerTimeMs,
            @Nullable final YoutubeSabrFormatTimeline audioTimeline,
            final int audioBufferedThrough,
            @Nullable final YoutubeSabrFormatTimeline videoTimeline,
            final int videoBufferedThrough,
            final boolean audioActive,
            final boolean videoActive,
            final boolean videoFirst,
            final float playbackRate,
            @Nonnull final SabrStreamingResponseReader.SegmentConsumer consumer)
            throws IOException, ExtractionException {
        return requestOnce(audioFormat, videoFormat, playerTimeMs,
                audioTimeline, audioBufferedThrough, videoTimeline, videoBufferedThrough,
                audioActive, videoActive, videoFirst, playbackRate, consumer);
    }

    public synchronized RequestResult requestOnce(
            @Nullable final YoutubeSabrInfo.Format requestedAudioFormat,
            @Nullable final YoutubeSabrInfo.Format requestedVideoFormat,
            final long playerTimeMs,
            @Nullable final YoutubeSabrFormatTimeline audioTimeline,
            final int audioBufferedThrough,
            @Nullable final YoutubeSabrFormatTimeline videoTimeline,
            final int videoBufferedThrough,
            final boolean audioActive,
            final boolean videoActive,
            final boolean videoFirst,
            final float playbackRate,
            @Nonnull final SabrStreamingResponseReader.SegmentConsumer consumer)
            throws IOException, ExtractionException {
        validateFormats(requestedAudioFormat, requestedVideoFormat);
        validateActiveFormats(requestedAudioFormat, requestedVideoFormat,
                audioActive, videoActive);
        final long backoffRemainingMs = getBackoffRemainingMs();
        if (backoffRemainingMs > 0) {
            return new RequestResult(0, (int) Math.min(Integer.MAX_VALUE,
                    backoffRemainingMs), true);
        }
        final YoutubeSabrResponse result = fetchAndProcessResponse(
                requestedAudioFormat, requestedVideoFormat, playerTimeMs,
                audioTimeline, audioBufferedThrough, videoTimeline, videoBufferedThrough,
                audioActive, videoActive, videoFirst, playbackRate,
                consumer);
        if (result == null) {
            return new RequestResult(0, (int) Math.min(Integer.MAX_VALUE,
                    getBackoffRemainingMs()), false);
        }
        return new RequestResult(result.getSegmentCount(), result.getBackoffTimeMs(), false);
    }

    public static final class RequestResult {
        private final int segmentCount;
        private final int backoffMs;
        private final boolean deferred;

        private RequestResult(final int segmentCount, final int backoffMs,
                              final boolean deferred) {
            this.segmentCount = segmentCount;
            this.backoffMs = backoffMs;
            this.deferred = deferred;
        }

        public int getSegmentCount() { return segmentCount; }
        public int getBackoffMs() { return backoffMs; }
        /** True when no HTTP request was sent because this session is still backing off. */
        public boolean isDeferred() { return deferred; }
    }

    /** Remaining server-requested delay before another transaction may start. */
    public long getBackoffRemainingMs() {
        final long remainingNs = backoffDeadlineNs - System.nanoTime();
        return remainingNs <= 0 ? 0 : Math.max(1, remainingNs / 1_000_000L);
    }

    private void updateBackoff(final int backoffMs) {
        backoffDeadlineNs = backoffMs <= 0 ? 0
                : System.nanoTime() + backoffMs * 1_000_000L;
    }

    @Nullable
    private YoutubeSabrResponse fetchAndProcessResponse(
            @Nullable final YoutubeSabrInfo.Format requestedAudioFormat,
            @Nullable final YoutubeSabrInfo.Format requestedVideoFormat,
            final long playerTimeMs,
            @Nullable final YoutubeSabrFormatTimeline audioTimeline,
            final int audioBufferedThrough,
            @Nullable final YoutubeSabrFormatTimeline videoTimeline,
            final int videoBufferedThrough,
            final boolean audioActive,
            final boolean videoActive,
            final boolean videoFirst,
            final float playbackRate,
            @Nonnull final SabrStreamingResponseReader.SegmentConsumer segmentConsumer)
            throws IOException, ExtractionException {
        final YoutubeSabrResponse result;
        try {
            result = fetchNextResponse(requestedAudioFormat, requestedVideoFormat,
                    playerTimeMs, audioTimeline,
                    audioBufferedThrough, videoTimeline, videoBufferedThrough,
                    audioActive, videoActive, videoFirst, playbackRate, segmentConsumer);
        } catch (final SabrRecoverableException e) {
            if (recoverFromStreamingMediaException(e)) {
                return null;
            }
            throw e;
        }
        final YoutubeSabrResponse decoded = result;
        final List<String> integrityIssues = decoded.getIntegrityIssues();
        if (!integrityIssues.isEmpty()) {
            if (isRecoverableIncompleteMediaResponse(integrityIssues)) {
                if (recoverFromIncompleteMediaResponse()) {
                    return null;
                }
                throw new SabrProtocolException("SABR media integrity issue: " + integrityIssues);
            }
            throw new SabrProtocolException("SABR media integrity issue: " + integrityIssues);
        }
        consecutiveIntegrityFailures = 0;
        handleControlResponse(result);
        return result;
    }

    // -------------------------------------------------------------------------
    // Response control and protocol state updates
    // -------------------------------------------------------------------------

    private void handleControlResponse(@Nonnull final YoutubeSabrResponse result)
            throws IOException, ExtractionException {
        final YoutubeSabrResponse decoded = result;
        if (decoded.isAttestationPending() && decoded.isNoMediaResponse()) {
            consecutiveAttestationPendingResponses++;
            addDiagnosticEvent("attestation_pending_no_media count="
                    + consecutiveAttestationPendingResponses);
            if (consecutiveAttestationPendingResponses
                    >= MAX_CONSECUTIVE_ATTESTATION_PENDING_RESPONSES) {
                throw new SabrProtocolException(
                        "SABR attestation remained pending without media for "
                                + consecutiveAttestationPendingResponses
                                + " consecutive responses: "
                                + decoded.summarizeNoMediaResponse());
            }
        } else {
            consecutiveAttestationPendingResponses = 0;
        }
        ingestControl(decoded);

        final String redirectUrl = decoded.getRedirectUrl();
        if (redirectUrl != null && !redirectUrl.isEmpty()) {
            if (++redirectCount > MAX_REDIRECTS_PER_SESSION) {
                throw new SabrProtocolException(
                        "SABR redirect limit exceeded: redirects=" + redirectCount);
            }
            validateRedirectUrl(redirectUrl);
            serverAbrStreamingUrl = redirectUrl;
        }

        if (decoded.getSabrError() != null) {
            throw new SabrProtocolException("SABR error: " + decoded.getSabrError());
        }
        if (decoded.isAttestationRequired()) {
            if (!refreshRejectedPoToken()) {
                throw new SabrProtocolException("SABR attestation required: "
                        + decoded.summarizeNoMediaResponse());
            }
            return;
        }
        if (decoded.getSegmentCount() > 0) {
            consecutiveAttestationTokenRefreshes = 0;
        }
        if (decoded.isReloadRequested()) {
            throw new SabrProtocolException("SABR requested player reload: "
                    + decoded.summarizeNoMediaResponse());
        }

        if (result.getSegmentCount() > 0) {
            redirectCount = 0;
        }
    }

    private void ingestControl(@Nonnull final YoutubeSabrResponse response) {
        final byte[] nextRequestPolicy = response.getNextRequestPolicy();
        if (nextRequestPolicy != null) {
            ingestNextRequestPolicy(nextRequestPolicy);
        }
        for (final byte[] update : response.getSabrContextUpdates()) {
            ingestContextUpdate(update);
        }
        final byte[] sendingPolicy = response.getSabrContextSendingPolicy();
        if (sendingPolicy != null) {
            ingestContextSendingPolicy(sendingPolicy);
        }
        for (final byte[] metadata : response.getLiveMetadata()) {
            ingestLiveMetadata(metadata);
        }
    }

    private void ingestNextRequestPolicy(@Nonnull final byte[] data) {
        try {
            for (final SabrProto.Field field : SabrProto.readFields(data)) {
                if (field.getNumber() == 7) {
                    playbackCookie = field.getBytes();
                }
            }
        } catch (final SabrProtocolException ignored) {
            // The response decoder already records malformed control parts.
        }
    }

    private void ingestLiveMetadata(@Nonnull final byte[] data) {
        boolean live = true;
        boolean postLiveDvr = false;
        long liveHeadSequenceNumber = -1;
        long liveHeadTimeMs = -1;
        try {
            for (final SabrProto.Field field : SabrProto.readFields(data)) {
                switch (field.getNumber()) {
                    case 3: liveHeadSequenceNumber = field.getVarint(); break;
                    case 4: liveHeadTimeMs = field.getVarint(); break;
                    case 8: postLiveDvr = field.getVarint() != 0; break;
                    default: break;
                }
            }
        } catch (final SabrProtocolException ignored) {
            return;
        }
        this.live = live;
        this.postLiveDvr = postLiveDvr;
        if (liveHeadSequenceNumber >= 0) this.liveHeadSequenceNumber = liveHeadSequenceNumber;
        if (liveHeadTimeMs >= 0) this.liveHeadTimeMs = liveHeadTimeMs;
    }

    private void ingestContextUpdate(@Nonnull final byte[] data) {
        int type = -1;
        byte[] value = null;
        boolean sendByDefault = false;
        int writePolicy = -1;
        try {
            for (final SabrProto.Field field : SabrProto.readFields(data)) {
                switch (field.getNumber()) {
                    case 1: type = (int) field.getVarint(); break;
                    case 3: value = field.getBytes(); break;
                    case 4: sendByDefault = field.getVarint() != 0; break;
                    case 5: writePolicy = (int) field.getVarint(); break;
                    default: break;
                }
            }
        } catch (final SabrProtocolException ignored) {
            return;
        }
        if (type < 0 || value == null || value.length == 0
                || writePolicy == 2 && sabrContexts.containsKey(type)) {
            return;
        }
        sabrContexts.put(type, value);
        if (sendByDefault) {
            activeSabrContextTypes.add(type);
        }
    }

    private void ingestContextSendingPolicy(@Nonnull final byte[] data) {
        try {
            for (final SabrProto.Field field : SabrProto.readFields(data)) {
                final List<Long> values = field.getWireType() == SabrProto.WIRE_VARINT
                        ? Collections.singletonList(field.getVarint())
                        : field.getWireType() == SabrProto.WIRE_LENGTH_DELIMITED
                        ? SabrProto.readPackedVarints(field.getBytes()) : Collections.emptyList();
                for (final Long value : values) {
                    final int type = value.intValue();
                    if (field.getNumber() == 1) activeSabrContextTypes.add(type);
                    else if (field.getNumber() == 2) activeSabrContextTypes.remove(type);
                    else if (field.getNumber() == 3) {
                        sabrContexts.remove(type);
                        activeSabrContextTypes.remove(type);
                    }
                }
            }
        } catch (final SabrProtocolException ignored) {
            // The response decoder already records malformed control parts.
        }
    }

    @Nullable
    byte[] getRawPlaybackCookie() {
        return playbackCookie;
    }

    public synchronized void clearPlaybackCookie() {
        playbackCookie = null;
    }

    @Nonnull
    Map<Integer, byte[]> getActiveSabrContexts() {
        final Map<Integer, byte[]> active = new LinkedHashMap<>();
        for (final Integer type : activeSabrContextTypes) {
            final byte[] value = sabrContexts.get(type);
            if (value != null) active.put(type, value);
        }
        return active;
    }

    @Nonnull
    Collection<Integer> getUnsentSabrContextTypes() {
        final List<Integer> unsent = new ArrayList<>();
        for (final Integer type : sabrContexts.keySet()) {
            if (!activeSabrContextTypes.contains(type)) unsent.add(type);
        }
        return unsent;
    }

    private static void validateRedirectUrl(@Nonnull final String redirectUrl)
            throws SabrProtocolException {
        try {
            final URI uri = URI.create(redirectUrl);
            final String host = uri.getHost();
            if (!"https".equalsIgnoreCase(uri.getScheme()) || host == null
                    || !(host.equals("googlevideo.com") || host.endsWith(".googlevideo.com"))) {
                throw new SabrProtocolException("SABR redirect escaped the GoogleVideo Host");
            }
        } catch (final IllegalArgumentException error) {
            throw new SabrProtocolException("Malformed SABR redirect URL", error);
        }
    }

    // -------------------------------------------------------------------------
    // Protocol status exposed to clients
    // -------------------------------------------------------------------------

    /** True once the server has reported this is a live stream (foundation for live support). */
    public boolean isLive() {
        return live;
    }

    /** Latest segment the live edge has reached, or -1 if unknown / not live. */
    public long getLiveHeadSequenceNumber() {
        return liveHeadSequenceNumber;
    }

    public boolean isPostLiveDvr() {
        return postLiveDvr;
    }

    public long getLiveHeadTimeMs() {
        return liveHeadTimeMs;
    }

    public int getRequestNumber() {
        return requestNumber;
    }

    // -------------------------------------------------------------------------
    // Initialization: adaptive range first, native SABR fallback
    // -------------------------------------------------------------------------

    /** Bootstrap exact timelines for every selected track exclusively through SABR. */
    private InitializationResult bootstrapInitialization(final long deadlineNs)
            throws IOException, ExtractionException {
        final byte[][] initialization = new byte[2][];
        final YoutubeSabrFormatTimeline[] timelines = new YoutubeSabrFormatTimeline[2];
        final Map<String, SabrMediaSegment> mediaSegments = new LinkedHashMap<>();
        boolean completed = false;
        try {
            while (true) {
                awaitInitializationBackoff(deadlineNs);
                fetchAndProcessResponse(audioFormat, videoFormat, 0, null, 0, null, 0,
                        audioFormat != null, videoFormat != null, false, 1.0f, segment -> {
                            if (segment.getHeader().isInitSegment()) {
                                if (audioFormat != null && matchesFormat(audioFormat, segment)) {
                                    initialization[0] = segment.getData();
                                    timelines[0] = parseTimeline(audioFormat, initialization[0]);
                                } else if (videoFormat != null
                                        && matchesFormat(videoFormat, segment)) {
                                    initialization[1] = segment.getData();
                                    timelines[1] = parseTimeline(videoFormat, initialization[1]);
                                }
                                segment.delete();
                            } else {
                                final String key = segment.getHeader().getItag() + ":"
                                        + segment.getHeader().getSequenceNumber();
                                final SabrMediaSegment previous =
                                        mediaSegments.putIfAbsent(key, segment);
                                if (previous != null) segment.delete();
                            }
                        });
                final boolean audioReady = audioFormat == null
                        || timelines[0] != null && initialization[0] != null;
                final boolean videoReady = videoFormat == null
                        || timelines[1] != null && initialization[1] != null;
                if (audioReady && videoReady) {
                    completed = true;
                    addDiagnosticEvent("bootstrap_ready");
                    return new InitializationResult(initialization[0], initialization[1],
                            timelines[0], timelines[1],
                            new ArrayList<>(mediaSegments.values()));
                }
                ensureInitializationBudget(deadlineNs);
            }
        } finally {
            if (!completed) {
                for (final SabrMediaSegment segment : mediaSegments.values()) {
                    segment.delete();
                }
            }
        }
    }

    private void awaitInitializationBackoff(final long deadlineNs) throws IOException {
        final long backoffMs = getBackoffRemainingMs();
        if (backoffMs <= 0) {
            ensureInitializationBudget(deadlineNs);
            return;
        }
        final long remainingNs = ensureInitializationBudget(deadlineNs);
        if (backoffMs * 1_000_000L >= remainingNs) {
            throw new IOException("SABR initialization backoff exceeds fetch budget");
        }
        try {
            Thread.sleep(backoffMs);
        } catch (final InterruptedException error) {
            Thread.currentThread().interrupt();
            final InterruptedIOException interrupted =
                    new InterruptedIOException("Interrupted during SABR initialization backoff");
            interrupted.initCause(error);
            throw interrupted;
        }
    }

    private static long ensureInitializationBudget(final long deadlineNs) throws IOException {
        final long remainingNs = deadlineNs - System.nanoTime();
        if (remainingNs <= 0) {
            throw new IOException("SABR initialization exceeded fetch budget");
        }
        return remainingNs;
    }

    @Nonnull
    public byte[] fetchInitializationData(@Nonnull final YoutubeSabrInfo.Format format,
                                          final long timeoutMs,
                                          @Nonnull final byte[] poToken)
            throws IOException {
        final String initializationUrl = format.getInitializationUrl();
        final long start = format.getInitRangeStart();
        final long end = format.getInitRangeEnd();
        if (initializationUrl == null || initializationUrl.isEmpty() || start < 0 || end < start
                || end - start >= MAX_INITIALIZATION_BYTES) {
            throw new IOException("Invalid SABR initialization range: itag="
                    + format.getItag() + ", start=" + start + ", end=" + end);
        }
        if (poToken.length == 0) {
            throw new IOException("Missing PO token for SABR initialization range: itag="
                    + format.getItag());
        }
        final String url = appendQueryParameterIfMissing(initializationUrl, "pot",
                Base64.getUrlEncoder().withoutPadding().encodeToString(poToken));
        final int length = (int) (end - start + 1);
        final Map<String, List<String>> headers = Collections.singletonMap(
                "Range", Collections.singletonList("bytes=" + start + '-' + end));
        try (StreamingResponse response = timeoutMs > 0
                ? NewPipe.getDownloader().getStreaming(url, headers,
                        YoutubeSabrRequestHelper.MWEB_LOCALIZATION, timeoutMs)
                : NewPipe.getDownloader().getStreaming(url, headers,
                        YoutubeSabrRequestHelper.MWEB_LOCALIZATION)) {
            if (response.responseCode() != 206
                    && !(response.responseCode() == 200 && start == 0)) {
                throw new IOException("SABR initialization range failed: itag="
                        + format.getItag() + ", status=" + response.responseCode());
            }
            final byte[] data = readExactly(response.body(), length);
            if (parseTimeline(format, data) == null) {
                throw new IOException("SABR initialization range has no exact index: itag="
                        + format.getItag());
            }
            addDiagnosticEvent("initialization_range itag=" + format.getItag()
                    + " status=" + response.responseCode() + " bytes=" + data.length);
            return data;
        } catch (final ExtractionException e) {
            throw new IOException("SABR initialization range failed: itag="
                    + format.getItag(), e);
        }
    }

    /** Fetch one format's initialization lazily, preserving native SABR as the fallback. */
    @Nonnull
    public synchronized byte[] fetchInitializationData(
            @Nonnull final YoutubeSabrInfo.Format format,
            final long timeoutMs) throws IOException, ExtractionException {
        return fetchInitializationData(format, timeoutMs, SabrMediaSegment::delete);
    }

    @Nonnull
    public synchronized byte[] fetchInitializationData(
            @Nonnull final YoutubeSabrInfo.Format format,
            final long timeoutMs,
            @Nonnull final SabrStreamingResponseReader.SegmentConsumer mediaConsumer)
            throws IOException, ExtractionException {
        final byte[] token = poToken == null ? null : poToken.clone();
        if (token == null || token.length == 0) {
            throw new IOException("Missing PO token for SABR initialization: itag="
                    + format.getItag());
        }
        try {
            return fetchInitializationData(format, timeoutMs, token);
        } catch (final IOException adaptiveFailure) {
            clearPlaybackCookie();
            try {
                return bootstrapInitialization(format, mediaConsumer,
                        System.nanoTime() + Math.max(1, timeoutMs) * 1_000_000L);
            } catch (final IOException | ExtractionException nativeFailure) {
                nativeFailure.addSuppressed(adaptiveFailure);
                throw nativeFailure;
            }
        }
    }

    @Nonnull
    private byte[] bootstrapInitialization(
            @Nonnull final YoutubeSabrInfo.Format format,
            @Nonnull final SabrStreamingResponseReader.SegmentConsumer mediaConsumer,
            final long deadlineNs)
            throws IOException, ExtractionException {
        final byte[][] initialization = new byte[1][];
        final YoutubeSabrInfo.Format requestedAudio = format.isAudio() ? format : null;
        final YoutubeSabrInfo.Format requestedVideo = format.isVideo() ? format : null;
        while (true) {
            awaitInitializationBackoff(deadlineNs);
            fetchAndProcessResponse(requestedAudio, requestedVideo, 0,
                    null, 0, null, 0, format.isAudio(), format.isVideo(), false, 1.0f,
                    segment -> {
                        if (segment.getHeader().isInitSegment()) {
                            if (matchesFormat(format, segment)) {
                                initialization[0] = segment.getData();
                            }
                            segment.delete();
                        } else {
                            mediaConsumer.accept(segment);
                        }
                    });
            if (initialization[0] != null
                    && parseTimeline(format, initialization[0]) != null) {
                return initialization[0];
            }
            ensureInitializationBudget(deadlineNs);
        }
    }

    /** Prepare every selected track using adaptive initialization, then native SABR fallback. */
    @Nonnull
    public synchronized InitializationResult initialize(final long timeoutMs,
                                                        @Nonnull final byte[] poToken)
            throws IOException, ExtractionException {
        final long deadlineNs = System.nanoTime()
                + Math.max(MAX_BACKOFF_MS, Math.max(1, timeoutMs)) * 1_000_000L;
        setPoToken(poToken);
        try {
            final byte[] audio = audioFormat == null ? null
                    : fetchInitializationData(audioFormat, timeoutMs, poToken);
            final byte[] video = videoFormat == null ? null
                    : fetchInitializationData(videoFormat, timeoutMs, poToken);
            return new InitializationResult(audio, video,
                    audioFormat == null ? null
                            : YoutubeSabrFormatTimeline.parse(audioFormat, audio),
                    videoFormat == null ? null
                            : YoutubeSabrFormatTimeline.parse(videoFormat, video),
                    Collections.emptyList());
        } catch (final IOException adaptiveFailure) {
            clearPlaybackCookie();
            return bootstrapInitialization(deadlineNs);
        }
    }

    public synchronized void setPoToken(@Nullable final byte[] value) {
        poToken = value == null ? null : value.clone();
    }

    public synchronized void setPoTokenRefresher(@Nullable final PoTokenRefresher value) {
        poTokenRefresher = value;
    }

    public synchronized void setIdentityRefresher(@Nullable final IdentityRefresher value) {
        identityRefresher = value;
    }

    private boolean refreshRejectedPoToken() throws IOException, ExtractionException {
        if (consecutiveAttestationTokenRefreshes >= 3) return false;
        final IdentityRefresher epochRefresher = identityRefresher;
        byte[] refreshed;
        if (epochRefresher != null) {
            final SessionIdentity identity = Objects.requireNonNull(epochRefresher.refresh());
            if (!hasCompatibleFormats(identity.info)) {
                throw new SabrProtocolException(
                        "Refreshed SABR identity changed selected formats");
            }
            info = identity.info;
            serverAbrStreamingUrl = identity.info.getServerAbrStreamingUrl();
            requestNumber = 0;
            redirectCount = 0;
            refreshed = identity.poToken;
        } else {
            final PoTokenRefresher tokenRefresher = poTokenRefresher;
            if (tokenRefresher == null) return false;
            refreshed = tokenRefresher.refresh();
        }
        if (refreshed == null || refreshed.length == 0) return false;
        poToken = refreshed.clone();
        playbackCookie = null;
        sabrContexts.clear();
        activeSabrContextTypes.clear();
        consecutiveAttestationTokenRefreshes++;
        addDiagnosticEvent("attestation_token_refresh attempt="
                + consecutiveAttestationTokenRefreshes
                + " bytes=" + refreshed.length);
        return true;
    }

    private boolean hasCompatibleFormats(@Nonnull final YoutubeSabrInfo refreshedInfo) {
        return hasCompatibleFormat(refreshedInfo, audioFormat)
                && hasCompatibleFormat(refreshedInfo, videoFormat);
    }

    private static boolean hasCompatibleFormat(@Nonnull final YoutubeSabrInfo refreshedInfo,
                                               @Nullable final YoutubeSabrInfo.Format selected) {
        if (selected == null) return true;
        for (final YoutubeSabrInfo.Format format : refreshedInfo.getFormats()) {
            if (format.isAudio() == selected.isAudio()
                    && format.getItag() == selected.getItag()
                    && Objects.equals(format.getXtags(), selected.getXtags())) return true;
        }
        return false;
    }

    @Nullable
    byte[] getRawPoToken() {
        return poToken;
    }

    long getBandwidthEstimate() {
        return bandwidthEstimate;
    }

    private void updateBandwidthEstimate(final long responseBytes, final long elapsedMs) {
        if (responseBytes <= 0 || elapsedMs <= 0) return;
        final long sample = responseBytes * 8_000L / elapsedMs;
        bandwidthEstimate = bandwidthEstimate <= 0
                ? sample : (bandwidthEstimate * 3 + sample) / 4;
    }

    public static final class InitializationResult {
        @Nullable private final byte[] audioData;
        @Nullable private final byte[] videoData;
        @Nullable private final YoutubeSabrFormatTimeline audioTimeline;
        @Nullable private final YoutubeSabrFormatTimeline videoTimeline;
        @Nonnull private final List<SabrMediaSegment> mediaSegments;

        private InitializationResult(@Nullable final byte[] audioData,
                                      @Nullable final byte[] videoData,
                                      @Nullable final YoutubeSabrFormatTimeline audioTimeline,
                                      @Nullable final YoutubeSabrFormatTimeline videoTimeline,
                                      @Nonnull final List<SabrMediaSegment> mediaSegments) {
            this.audioData = audioData == null ? null : audioData.clone();
            this.videoData = videoData == null ? null : videoData.clone();
            this.audioTimeline = audioTimeline;
            this.videoTimeline = videoTimeline;
            this.mediaSegments = Collections.unmodifiableList(new ArrayList<>(mediaSegments));
        }

        @Nullable
        public byte[] getAudioData() {
            return audioData == null ? null : audioData.clone();
        }

        @Nullable
        public byte[] getVideoData() {
            return videoData == null ? null : videoData.clone();
        }

        @Nullable public YoutubeSabrFormatTimeline getAudioTimeline() { return audioTimeline; }
        @Nullable public YoutubeSabrFormatTimeline getVideoTimeline() { return videoTimeline; }

        @Nonnull
        public List<SabrMediaSegment> getMediaSegments() {
            return mediaSegments;
        }
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
    private static byte[] readExactly(@Nonnull final java.io.InputStream input,
                                      final int length) throws IOException {
        final byte[] data = new byte[length];
        int offset = 0;
        while (offset < length) {
            final int read = input.read(data, offset, length - offset);
            if (read < 0) {
                throw new IOException("Truncated SABR initialization range: expected="
                        + length + ", actual=" + offset);
            }
            offset += read;
        }
        return data;
    }

    @Nullable
    private static YoutubeSabrFormatTimeline parseTimeline(
            @Nonnull final YoutubeSabrInfo.Format format, @Nonnull final byte[] data) {
        try {
            return YoutubeSabrFormatTimeline.parse(format, data);
        } catch (final SabrProtocolException ignored) {
            return null;
        }
    }

    private static void validateFormats(@Nullable final YoutubeSabrInfo.Format audio,
                                        @Nullable final YoutubeSabrInfo.Format video) {
        if (audio == null && video == null) {
            throw new IllegalArgumentException("SABR session must select at least one format");
        }
        if (audio != null && !audio.isAudio()) {
            throw new IllegalArgumentException("SABR audio format must be audio: itag="
                    + audio.getItag());
        }
        if (video != null && !video.isVideo()) {
            throw new IllegalArgumentException("SABR video format must be video: itag="
                    + video.getItag());
        }
        if (audio != null && video != null && audio.getItag() == video.getItag()) {
            throw new IllegalArgumentException("SABR audio/video formats must be distinct");
        }
    }

    private static void validateActiveFormats(
            @Nullable final YoutubeSabrInfo.Format audio,
            @Nullable final YoutubeSabrInfo.Format video,
            final boolean audioActive,
            final boolean videoActive) {
        if (!audioActive && !videoActive) {
            throw new IllegalArgumentException("SABR request must activate at least one track");
        }
        if (audioActive && audio == null) {
            throw new IllegalArgumentException("Active SABR audio request has no audio format");
        }
        if (videoActive && video == null) {
            throw new IllegalArgumentException("Active SABR video request has no video format");
        }
    }

    private boolean matchesFormat(@Nonnull final YoutubeSabrInfo.Format format,
                                  @Nonnull final SabrMediaSegment segment) {
        if (segment.getHeader().getItag() != format.getItag()) return false;
        final String headerXtags = segment.getHeader().getXtags();
        if (headerXtags != null) return Objects.equals(headerXtags, format.getXtags());
        int sameItagFormats = 0;
        for (final YoutubeSabrInfo.Format candidate : info.getFormats()) {
            if (candidate.getItag() == format.getItag()) sameItagFormats++;
        }
        return sameItagFormats == 1;
    }

    // -------------------------------------------------------------------------
    // Incomplete response classification and retry accounting
    // -------------------------------------------------------------------------

    private boolean recoverFromStreamingMediaException(
            @Nonnull final SabrRecoverableException error)
            throws IOException, ExtractionException {
        addDiagnosticEvent("streaming_integrity_recoverable type="
                + error.getClass().getSimpleName() + " message=" + error.getMessage());
        return recoverFromIncompleteMediaResponse();
    }

    private boolean recoverFromIncompleteMediaResponse()
            throws IOException, ExtractionException {
        consecutiveIntegrityFailures++;
        return consecutiveIntegrityFailures < MAX_INCOMPLETE_MEDIA_RESPONSES;
    }

    private static boolean isRecoverableIncompleteMediaResponse(
            @Nonnull final List<String> integrityIssues) {
        if (integrityIssues.isEmpty()) {
            return false;
        }
        for (final String issue : integrityIssues) {
            if (!issue.startsWith("length-mismatch:")
                    && !issue.startsWith("missing-media-end:")
                    && !issue.startsWith("missing-media:")
                    && !issue.startsWith("media-without-header:")
                    && !issue.startsWith("media-end-without-header:")) {
                return false;
            }
        }
        return true;
    }

    // -------------------------------------------------------------------------
    // Diagnostics and trace forwarding
    // -------------------------------------------------------------------------

    private final YoutubeSabrSessionDiagnostics diagnostics = new YoutubeSabrSessionDiagnostics();

    public synchronized void addDiagnosticEvent(@Nonnull final String event) {
        diagnostics.addEvent(event);
    }

    @Nonnull
    public synchronized String getDiagnosticTrace() {
        return diagnostics.getTrace();
    }

    // Response statistics and trace snapshots

    /** Raw bytes consumed from all SABR HTTP response bodies in this session. */
    public long getTotalResponseBytes() {
        return diagnostics.getTotalResponseBytes();
    }

    public long getMaxResponseBytes() {
        return diagnostics.getMaxResponseBytes();
    }

    public long getMaxUmpPartBytes() {
        return diagnostics.getMaxUmpPartBytes();
    }

    public long getMaxMediaPartPayloadBytes() {
        return diagnostics.getMaxMediaPartPayloadBytes();
    }

    public long getMaxSegmentBytes() {
        return diagnostics.getMaxSegmentBytes();
    }

    public int getMaxSegmentsPerResponse() {
        return diagnostics.getMaxSegmentsPerResponse();
    }

    public int getMaxStreamProtectionStatus() {
        return diagnostics.getMaxStreamProtectionStatus();
    }

    @Nonnull
    public String getMemoryDiagnosticSummary() {
        return diagnostics.getMemorySummary(requestNumber);
    }

    public void setTraceEnabled(final boolean traceEnabled) {
        diagnostics.setTraceEnabled(traceEnabled);
    }

    @Nonnull
    public TraceSnapshot getTraceSnapshot() {
        return diagnostics.snapshot(requestNumber);
    }

    @Nonnull
    private static String summarizeSegments(@Nonnull final List<SabrMediaSegment> segments) {
        if (segments.isEmpty()) {
            return "[]";
        }
        final StringBuilder summary = new StringBuilder("[");
        for (int i = 0; i < segments.size(); i++) {
            if (i > 0) {
                summary.append(',');
            }
            final SabrMediaSegment segment = segments.get(i);
            summary.append(segment.getHeader().getItag()).append(':');
            summary.append(segment.getHeader().isInitSegment()
                    ? "init" : segment.getHeader().getSequenceNumber());
        }
        return summary.append(']').toString();
    }

    public static final class TraceSnapshot {
        private final long responseBytes;
        private final long mediaPayloadBytes;
        private final long controlPayloadBytes;
        private final long umpOverheadBytes;
        private final long discardedBytes;
        private final int requestNumber;
        @Nonnull private final List<String> segments;
        @Nonnull private final List<String> discards;
        @Nonnull private final List<String> responses;

        TraceSnapshot(final long responseBytes, final long mediaPayloadBytes,
                      final long controlPayloadBytes, final long umpOverheadBytes,
                      final long discardedBytes, final int requestNumber,
                      @Nonnull final List<String> segments,
                      @Nonnull final List<String> discards,
                      @Nonnull final List<String> responses) {
            this.responseBytes = responseBytes;
            this.mediaPayloadBytes = mediaPayloadBytes;
            this.controlPayloadBytes = controlPayloadBytes;
            this.umpOverheadBytes = umpOverheadBytes;
            this.discardedBytes = discardedBytes;
            this.requestNumber = requestNumber;
            this.segments = Collections.unmodifiableList(segments);
            this.discards = Collections.unmodifiableList(discards);
            this.responses = Collections.unmodifiableList(responses);
        }

        public long getResponseBytes() { return responseBytes; }
        public long getMediaPayloadBytes() { return mediaPayloadBytes; }
        public long getControlPayloadBytes() { return controlPayloadBytes; }
        public long getUmpOverheadBytes() { return umpOverheadBytes; }
        public long getDiscardedBytes() { return discardedBytes; }
        public int getRequestNumber() { return requestNumber; }
        @Nonnull public List<String> getSegments() { return segments; }
        @Nonnull public List<String> getDiscards() { return discards; }
        @Nonnull public List<String> getResponses() { return responses; }
    }
}
