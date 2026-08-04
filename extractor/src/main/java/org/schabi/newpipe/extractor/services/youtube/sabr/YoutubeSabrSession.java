package org.schabi.newpipe.extractor.services.youtube.sabr;

import org.schabi.newpipe.extractor.services.youtube.sabr.exception.SabrProtocolException;
import org.schabi.newpipe.extractor.services.youtube.sabr.exception.SabrRecoverableException;
import org.schabi.newpipe.extractor.services.youtube.sabr.media.SabrMediaSegment;
import org.schabi.newpipe.extractor.services.youtube.sabr.protocol.SabrStreamingResponseReader;

import org.schabi.newpipe.extractor.NewPipe;
import org.schabi.newpipe.extractor.downloader.StreamingResponse;
import org.schabi.newpipe.extractor.exceptions.ExtractionException;
import org.schabi.newpipe.extractor.localization.Localization;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Collections;
import java.util.List;
import java.util.LinkedHashMap;
import java.util.Map;

public final class YoutubeSabrSession {
    private static final int MAX_REDIRECTS_PER_SESSION = 3;
    private static final int MAX_INCOMPLETE_MEDIA_RESPONSES = 3;
    private static final int MAX_CONSECUTIVE_ATTESTATION_PENDING_RESPONSES = 3;
    private static final int MAX_BACKOFF_MS = 30_000;
    private static final int MAX_INITIALIZATION_BYTES = 4 * 1024 * 1024;
    @Nonnull
    private final YoutubeSabrInfo info;
    @Nonnull
    private final YoutubeSabrInfo.Format audioFormat;
    @Nonnull
    private final YoutubeSabrInfo.Format videoFormat;
    @Nonnull
    private final YoutubeSabrStreamState streamState;
    @Nullable
    private final File segmentSpoolDirectory;
    @Nonnull
    private String serverAbrStreamingUrl;
    private int requestNumber;
    private int redirectCount;
    private int consecutiveIntegrityFailures;
    private int consecutiveAttestationPendingResponses;


    public YoutubeSabrSession(@Nonnull final YoutubeSabrInfo info,
                               @Nonnull final YoutubeSabrInfo.Format audioFormat,
                               @Nonnull final YoutubeSabrInfo.Format videoFormat) {
        this(info, audioFormat, videoFormat, (File) null);
    }

    public YoutubeSabrSession(@Nonnull final YoutubeSabrInfo info,
                              @Nonnull final YoutubeSabrInfo.Format audioFormat,
                              @Nonnull final YoutubeSabrInfo.Format videoFormat,
                              @Nullable final File segmentSpoolDirectory) {
        if (!audioFormat.isAudio()) {
            throw new IllegalArgumentException("SABR audio format must be audio: itag="
                    + audioFormat.getItag());
        }
        if (!videoFormat.isVideo()) {
            throw new IllegalArgumentException("SABR video format must be video: itag="
                    + videoFormat.getItag());
        }
        if (audioFormat.getItag() == videoFormat.getItag()) {
            throw new IllegalArgumentException("SABR audio/video formats must be distinct");
        }
        if (info.getServerAbrStreamingUrl() == null || info.getServerAbrStreamingUrl().isEmpty()) {
            throw new IllegalArgumentException("Missing SABR streaming URL");
        }
        this.info = info;
        this.audioFormat = audioFormat;
        this.videoFormat = videoFormat;
        this.streamState = new YoutubeSabrStreamState(audioFormat, videoFormat);
        this.segmentSpoolDirectory = segmentSpoolDirectory;
        this.serverAbrStreamingUrl = info.getServerAbrStreamingUrl();
    }

    @Nonnull
    private YoutubeSabrResponse fetchNextResponse(
            @Nonnull final Localization localization,
            @Nullable final SabrStreamingResponseReader.SegmentConsumer segmentConsumer)
            throws IOException, ExtractionException {
        final long playerTimeMs = streamState.getRequestPlayerTimeMs();
        final long bufferedEdgeMs = streamState.getMinBufferedEndMs();
        final byte[] rawPoToken = streamState.getRawPoToken();
        final int poTokenBytes = rawPoToken == null ? -1 : rawPoToken.length;
        addDiagnosticEvent("request n=" + requestNumber
                + " playerMs=" + playerTimeMs
                + " edgeMs=" + bufferedEdgeMs
                + " poTokenBytes=" + poTokenBytes
                + " ranges=" + streamState.summarizeBufferedRanges());
        final long requestStartNs = System.nanoTime();
        final SabrStreamingResponseReader.SegmentConsumer timedConsumer = segmentConsumer;
        final SabrStreamingResponseReader.SegmentConsumer startedConsumer = segment -> { };
        final YoutubeSabrResponse result;
        try {
            result = YoutubeSabrRequestHelper.post(info, audioFormat, videoFormat, streamState,
                    serverAbrStreamingUrl, requestNumber, localization, timedConsumer,
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
        diagnostics.recordResponse(result, requestNumber);
        updateBandwidthEstimate(result.getResponseBytes(), System.nanoTime() - requestStartNs);
        requestNumber++;
        return result;
    }

    private void updateBandwidthEstimate(final long responseBytes, final long elapsedNs) {
        if (responseBytes <= 0 || elapsedNs <= 0) {
            return;
        }
        final long sampleBitsPerSecond = responseBytes * 8_000_000_000L / elapsedNs;
        final long previous = streamState.getBandwidthEstimate();
        final long estimate = previous <= 0
                ? sampleBitsPerSecond : (previous * 3 + sampleBitsPerSecond) / 4;
        streamState.setBandwidthEstimate(estimate);
        addDiagnosticEvent("bandwidth sampleBps=" + sampleBitsPerSecond
                + " estimateBps=" + estimate);
    }

    public RequestResult requestOnce(
            @Nonnull final Localization localization,
            @Nonnull final SabrStreamingResponseReader.SegmentConsumer consumer)
            throws IOException, ExtractionException {
        final YoutubeSabrResponse result = fetchAndProcessResponse(localization, segment -> {
            consumer.accept(segment);
        });
        if (result == null) {
            return new RequestResult(0, 0);
        }
        return new RequestResult(result.getSegmentCount(), result.getBackoffTimeMs());
    }

    public static final class RequestResult {
        private final int segmentCount;
        private final int backoffMs;

        private RequestResult(final int segmentCount, final int backoffMs) {
            this.segmentCount = segmentCount;
            this.backoffMs = backoffMs;
        }

        public int getSegmentCount() { return segmentCount; }
        public int getBackoffMs() { return backoffMs; }
    }

    @Nullable
    private YoutubeSabrResponse fetchAndProcessResponse(
            @Nonnull final Localization localization,
            @Nonnull final SabrStreamingResponseReader.SegmentConsumer segmentConsumer)
            throws IOException, ExtractionException {
        final YoutubeSabrResponse result;
        try {
            result = fetchNextResponse(localization, segmentConsumer);
        } catch (final SabrRecoverableException e) {
            if (recoverFromStreamingMediaException(localization, e)) {
                return null;
            }
            throw e;
        }
        final YoutubeSabrResponse decoded = result;
        final List<String> integrityIssues = decoded.getIntegrityIssues();
        if (!integrityIssues.isEmpty()) {
            if (isRecoverableIncompleteMediaResponse(integrityIssues)) {
                if (recoverFromIncompleteMediaResponse(localization, decoded)) {
                    return null;
                }
                throw new SabrProtocolException("SABR media integrity issue: " + integrityIssues);
            }
            throw new SabrProtocolException("SABR media integrity issue: " + integrityIssues);
        }
        consecutiveIntegrityFailures = 0;
        handleControlResponse(result, null);
        return result;
    }

    private void handleControlResponse(
            @Nonnull final YoutubeSabrResponse result,
            @Nullable final SabrSegmentRequest request) throws SabrProtocolException {
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
        streamState.ingest(decoded);

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
            throw sabrResponseError(request, decoded.getSabrError());
        }
        if (decoded.isAttestationRequired()) {
            throw sabrResponseError(request, "SABR attestation required: "
                    + decoded.summarizeNoMediaResponse());
        }
        if (decoded.isReloadRequested()) {
            throw new SabrProtocolException(request == null
                    ? "SABR requested player reload: "
                    + decoded.summarizeNoMediaResponse()
                    : "SABR requested player reload while fetching "
                    + describeRequest(request) + ": "
                    + decoded.summarizeNoMediaResponse());
        }

        if (result.getSegmentCount() > 0) {
            redirectCount = 0;
        }
    }

    @Nonnull
    private static SabrProtocolException sabrResponseError(
            @Nullable final SabrSegmentRequest request,
            @Nonnull final String details) {
        return new SabrProtocolException(request == null
                ? "SABR error: " + details
                : "SABR error while fetching " + describeRequest(request) + ": " + details);
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

    /** True once the requested media segment is known to be past the last segment of the stream. */
    public boolean isBeyondEnd(@Nonnull final SabrSegmentRequest request) {
        if (request.isInitializationSegment()) {
            return false;
        }
        final long endSegment = streamState.getEndSegment(request.getFormat());
        return endSegment > 0 && request.getSequenceNumber() > endSegment;
    }

    public boolean isComplete() {
        return streamState.isComplete();
    }

    /** True once the server has reported this is a live stream (foundation for live support). */
    public boolean isLive() {
        return streamState.isLive();
    }

    /** Latest segment the live edge has reached, or -1 if unknown / not live. */
    public long getLiveHeadSequenceNumber() {
        return streamState.getLiveHeadSequenceNumber();
    }

    /**
     * True when playback has caught up to the live head: a live-aware client should wait for the head
     * to advance rather than treating an empty response as the end. Always false for VOD.
     */
    public boolean isAtLiveEdge() {
        return streamState.isAtLiveEdge(audioFormat, videoFormat);
    }

    @Nonnull
    public YoutubeSabrStreamState getStreamState() {
        return streamState;
    }

    public int getRequestNumber() {
        return requestNumber;
    }

    /**
     * Bootstrap an exact audio/video timeline exclusively through SABR. A response streams media
     * before its control metadata is applied. Initialization bytes are collected locally and
     * combined with format metadata into an exact MP4/WebM segment index.
     */
    private InitializationResult bootstrapInitialization(@Nonnull final Localization localization)
            throws IOException, ExtractionException {
        final byte[][] initialization = new byte[2][];
        final Map<String, SabrMediaSegment> mediaSegments = new LinkedHashMap<>();
        fetchAndProcessResponse(localization, segment -> {
                streamState.ingest(segment);
                if (segment.getHeader().isInitSegment()) {
                    if (segment.getHeader().getItag() == audioFormat.getItag()) {
                        initialization[0] = segment.getData();
                        streamState.ingestInitializationData(audioFormat, initialization[0]);
                    } else if (segment.getHeader().getItag() == videoFormat.getItag()) {
                        initialization[1] = segment.getData();
                        streamState.ingestInitializationData(videoFormat, initialization[1]);
                    }
                    segment.delete();
                } else {
                    final String key = segment.getHeader().getItag() + ":"
                            + segment.getHeader().getSequenceNumber();
                    final SabrMediaSegment previous = mediaSegments.putIfAbsent(key, segment);
                    if (previous != null) {
                        segment.delete();
                    }
                }
        });
        if (hasExactBootstrapTimeline() && hasBootstrapInitialization(initialization)) {
            addDiagnosticEvent("bootstrap_ready");
            return new InitializationResult(initialization[0], initialization[1],
                    new ArrayList<>(mediaSegments.values()));
        }
        for (final SabrMediaSegment segment : mediaSegments.values()) {
            segment.delete();
        }
        throw new SabrProtocolException("SABR bootstrap did not provide exact audio/video indexes"
                + ": audioInit=" + (initialization[0] != null)
                + ", videoInit=" + (initialization[1] != null)
                + ", audioEnd=" + streamState.getEndSegment(audioFormat)
                + ", videoEnd=" + streamState.getEndSegment(videoFormat));
    }

    @Nonnull
    public byte[] fetchInitializationData(@Nonnull final YoutubeSabrInfo.Format format,
                                          @Nonnull final Localization localization,
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
                ? NewPipe.getDownloader().getStreaming(url, headers, localization, timeoutMs)
                : NewPipe.getDownloader().getStreaming(url, headers, localization)) {
            if (response.responseCode() != 206
                    && !(response.responseCode() == 200 && start == 0)) {
                throw new IOException("SABR initialization range failed: itag="
                        + format.getItag() + ", status=" + response.responseCode());
            }
            final byte[] data = readExactly(response.body(), length);
            if (!streamState.ingestInitializationData(format, data)
                    || !streamState.hasSegmentIndex(format)) {
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

    /**
     * Prepare both selected tracks using adaptive initialization first, falling back to native SABR
     * bootstrap on any adaptive failure. The returned data and this session are a single handoff to
     * normal streaming requests.
     */
    @Nonnull
    public InitializationResult initialize(@Nonnull final Localization localization,
                                           final long timeoutMs,
                                           @Nonnull final byte[] poToken)
            throws IOException, ExtractionException {
        try {
            final byte[] audio = streamState.isAudioTrackEnabled()
                    ? fetchInitializationData(audioFormat, localization, timeoutMs, poToken) : null;
            final byte[] video = streamState.isVideoTrackEnabled()
                    ? fetchInitializationData(videoFormat, localization, timeoutMs, poToken) : null;
            return new InitializationResult(audio, video, Collections.emptyList());
        } catch (final IOException adaptiveFailure) {
            streamState.resetInitialization(audioFormat);
            streamState.resetInitialization(videoFormat);
            streamState.clearPlaybackCookie();
            return bootstrapInitialization(localization);
        }
    }

    public static final class InitializationResult {
        @Nullable private final byte[] audioData;
        @Nullable private final byte[] videoData;
        @Nonnull private final List<SabrMediaSegment> mediaSegments;

        private InitializationResult(@Nullable final byte[] audioData,
                                      @Nullable final byte[] videoData,
                                      @Nonnull final List<SabrMediaSegment> mediaSegments) {
            this.audioData = audioData == null ? null : audioData.clone();
            this.videoData = videoData == null ? null : videoData.clone();
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

    private boolean hasExactBootstrapTimeline() {
        return (!streamState.isAudioTrackEnabled() || streamState.hasSegmentIndex(audioFormat))
                && (!streamState.isVideoTrackEnabled() || streamState.hasSegmentIndex(videoFormat));
    }

    private boolean hasBootstrapInitialization(@Nonnull final byte[][] initialization) {
        return (!streamState.isAudioTrackEnabled() || initialization[0] != null)
                && (!streamState.isVideoTrackEnabled() || initialization[1] != null);
    }

    private boolean recoverFromIncompleteMediaResponse(@Nonnull final Localization localization,
                                                       @Nonnull final YoutubeSabrResponse decoded)
            throws IOException, ExtractionException {
        return recoverFromIncompleteMediaResponse(localization, decoded.getBackoffTimeMs());
    }

    private boolean recoverFromStreamingMediaException(@Nonnull final Localization localization,
                                                       @Nonnull final SabrRecoverableException error)
            throws IOException, ExtractionException {
        addDiagnosticEvent("streaming_integrity_recoverable type="
                + error.getClass().getSimpleName() + " message=" + error.getMessage());
        return recoverFromIncompleteMediaResponse(localization, -1);
    }

    private boolean recoverFromIncompleteMediaResponse(@Nonnull final Localization localization,
                                                       final int responseBackoffMs)
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

    @Nonnull
    private static String describeRequest(@Nonnull final SabrSegmentRequest request) {
        return "itag=" + request.getFormat().getItag()
                + (request.isInitializationSegment()
                ? ":init"
                : ":seq=" + request.getSequenceNumber());
    }

    // -------------------------------------------------------------------------
    // Diagnostics forwarding. These methods expose observability without making
    // diagnostics part of the SABR request state above.
    // -------------------------------------------------------------------------

    private final YoutubeSabrSessionDiagnostics diagnostics = new YoutubeSabrSessionDiagnostics();

    public synchronized void addDiagnosticEvent(@Nonnull final String event) {
        diagnostics.addEvent(event);
    }

    @Nonnull
    public synchronized String getDiagnosticTrace() {
        return diagnostics.getTrace();
    }

    /** ----------------------------------------------------------------------- */
    /** Response statistics and trace snapshot forwarding. */

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
