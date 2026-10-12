package org.schabi.newpipe.extractor.services.youtube.sabr;

import org.schabi.newpipe.extractor.services.youtube.sabr.exception.SabrProtocolException;
import org.schabi.newpipe.extractor.services.youtube.sabr.exception.SabrRecoverableException;
import org.schabi.newpipe.extractor.services.youtube.sabr.exception.SabrAttestationException;
import org.schabi.newpipe.extractor.services.youtube.sabr.exception.SabrReloadException;
import org.schabi.newpipe.extractor.services.youtube.sabr.media.SabrMediaSegment;
import org.schabi.newpipe.extractor.services.youtube.sabr.protocol.SabrStreamingResponseReader;
import org.schabi.newpipe.extractor.services.youtube.sabr.protocol.SabrProto;

import org.schabi.newpipe.extractor.exceptions.ExtractionException;
import okhttp3.HttpUrl;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.io.File;
import java.io.IOException;
import java.net.ConnectException;
import java.net.NoRouteToHostException;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Collection;
import java.util.Set;
import java.util.Map;
import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class YoutubeSabrSession {
    // -------------------------------------------------------------------------
    // Session configuration and protocol state
    // -------------------------------------------------------------------------

    private static final int MAX_REDIRECTS_PER_SESSION = 3;
    private static final int MAX_INCOMPLETE_MEDIA_RESPONSES = 3;
    private static final int MAX_CONSECUTIVE_ATTESTATION_PENDING_RESPONSES = 3;
    private static final int MAX_RELOADS_PER_SESSION = 2;
    private static final int MAX_BACKOFF_MS = 30_000;
    /**
     * A googlevideo node host, such as {@code rr2---sn-qja5m5-5v.googlevideo.com}. The captured
     * group is the {@code rr} index, which is also advertised by the streaming URL as {@code mvi}.
     */
    private static final Pattern GOOGLEVIDEO_HOST_PATTERN =
            Pattern.compile("^rr(\\d+)---[^/]+\\.googlevideo\\.com$");
    @Nonnull
    private YoutubeSabrInfo info;
    @Nullable
    private volatile SabrInfoReloader infoReloader;
    @Nullable
    private final File segmentSpoolDirectory;
    @Nonnull
    private String serverAbrStreamingUrl;
    /**
     * Candidate streaming URLs, in preference order, built from the {@code mn} node list and the
     * {@code mvi} index of the current URL. Index 0 is always the URL the server handed us.
     */
    @Nonnull
    private List<String> hostCandidates = Collections.emptyList();
    private int hostCandidateIndex;
    private int requestNumber;
    private int redirectCount;
    private int consecutiveIntegrityFailures;
    private int consecutiveAttestationPendingResponses;
    private int reloads;
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

    // -------------------------------------------------------------------------
    // Construction
    // -------------------------------------------------------------------------

    public YoutubeSabrSession(@Nonnull final YoutubeSabrInfo info) {
        this(info, null);
    }

    public YoutubeSabrSession(@Nonnull final YoutubeSabrInfo info,
                              @Nullable final File segmentSpoolDirectory) {
        if (info.getServerAbrStreamingUrl() == null || info.getServerAbrStreamingUrl().isEmpty()) {
            throw new IllegalArgumentException("Missing SABR streaming URL");
        }
        this.info = info;
        this.segmentSpoolDirectory = segmentSpoolDirectory;
        this.serverAbrStreamingUrl = info.getServerAbrStreamingUrl();
        updateHostCandidates(this.serverAbrStreamingUrl);
    }

    // -------------------------------------------------------------------------
    // SABR transactions
    // -------------------------------------------------------------------------

    @Nonnull
    private YoutubeSabrResponse fetchNextResponse(
            @Nonnull final YoutubeSabrRequest request,
            @Nullable final SabrStreamingResponseReader.SegmentConsumer segmentConsumer)
            throws IOException, ExtractionException {
        final YoutubeSabrRequest.PlaybackState playbackState = request.getPlaybackState();
        final YoutubeSabrRequest.Track audioTrack = request.getAudioTrack();
        final YoutubeSabrRequest.Track videoTrack = request.getVideoTrack();
        addDiagnosticEvent("request n=" + requestNumber
                + " playerMs=" + playbackState.getPlayerTimeMs()
                + " audioThrough=" + (audioTrack == null ? 0 : audioTrack.getBufferedThrough())
                + " videoThrough=" + (videoTrack == null ? 0 : videoTrack.getBufferedThrough())
                + " selectedTracks=" + request.hasSelectedTracks()
                + " poTokenBytes=" + (poToken == null ? -1 : poToken.length));
        final SabrStreamingResponseReader.SegmentConsumer timedConsumer = segmentConsumer;
        final SabrStreamingResponseReader.SegmentConsumer startedConsumer = segment -> { };
        final YoutubeSabrResponse result;
        try {
            result = postWithHostRotation(request, timedConsumer, startedConsumer);
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

    /**
     * Posts a SABR request, rotating to another {@code googlevideo} node advertised by the
     * streaming URL's {@code mn} parameter when the current host cannot be reached.
     *
     * <p>A transport-level failure such as {@link UnknownHostException} (Android reports DNS
     * {@code EAI_NODATA} this way) means the current hostname could not be resolved on this
     * network. Rotating to a sibling node changes the DNS cache key, which bypasses a negative
     * cache entry for the previous host and any filtering that only affects that node.
     *</p>
     *
     * <p>The SABR protocol state is not advanced here: {@code rn} stays at the current request
     * number because no response was received, so a rotated retry is the same logical request.
     *</p>
     */
    @Nonnull
    private YoutubeSabrResponse postWithHostRotation(
            @Nonnull final YoutubeSabrRequest request,
            @Nullable final SabrStreamingResponseReader.SegmentConsumer segmentConsumer,
            @Nullable final SabrStreamingResponseReader.SegmentConsumer startedConsumer)
            throws IOException, ExtractionException {
        while (true) {
            try {
                return YoutubeSabrRequestHelper.post(info, request, this,
                        serverAbrStreamingUrl, requestNumber, segmentConsumer,
                        startedConsumer, segmentSpoolDirectory);
            } catch (final UnknownHostException | ConnectException | NoRouteToHostException e) {
                addDiagnosticEvent("host_unreachable n=" + requestNumber
                        + " host=" + hostOf(serverAbrStreamingUrl)
                        + " type=" + e.getClass().getSimpleName());
                if (!rotateHost()) {
                    throw e;
                }
                addDiagnosticEvent("host_rotate n=" + requestNumber
                        + " host=" + hostOf(serverAbrStreamingUrl));
            }
        }
    }

    private void updateHostCandidates(@Nonnull final String url) {
        hostCandidates = buildHostCandidates(url);
        hostCandidateIndex = 0;
    }

    /**
     * Advances to the next candidate host, if any, and returns whether one was available.
     */
    private boolean rotateHost() {
        if (hostCandidateIndex + 1 >= hostCandidates.size()) {
            return false;
        }
        hostCandidateIndex++;
        serverAbrStreamingUrl = hostCandidates.get(hostCandidateIndex);
        return true;
    }

    /**
     * Builds the list of equivalent streaming URLs by rewriting the host to every node advertised
     * under the {@code mn} parameter, keeping the current URL first and preserving all other
     * parameters (including the signature, which is not bound to the host).
     */
    @Nonnull
    private static List<String> buildHostCandidates(@Nonnull final String url) {
        final HttpUrl parsed = HttpUrl.parse(url);
        if (parsed == null) {
            return Collections.singletonList(url);
        }
        final String host = parsed.host();
        final Matcher hostMatcher = GOOGLEVIDEO_HOST_PATTERN.matcher(host);
        final String mn = parsed.queryParameter("mn");
        if (!hostMatcher.matches() || mn == null || mn.isEmpty()) {
            return Collections.singletonList(url);
        }
        final String mvi = parsed.queryParameter("mvi");
        final String rrIndex = mvi != null && mvi.matches("\\d+") ? mvi : hostMatcher.group(1);
        final List<String> candidates = new ArrayList<>();
        candidates.add(url);
        for (final String node : mn.split(",")) {
            final String trimmedNode = node.trim();
            if (trimmedNode.isEmpty()) {
                continue;
            }
            final String candidateHost = "rr" + rrIndex + "---" + trimmedNode + ".googlevideo.com";
            if (candidateHost.equals(host)) {
                continue;
            }
            final String candidateUrl = parsed.newBuilder().host(candidateHost).build().toString();
            if (!candidates.contains(candidateUrl)) {
                candidates.add(candidateUrl);
            }
        }
        return candidates;
    }

    @Nullable
    private static String hostOf(@Nonnull final String url) {
        final HttpUrl parsed = HttpUrl.parse(url);
        return parsed == null ? null : parsed.host();
    }

    public synchronized RequestResult requestOnce(
            @Nonnull final YoutubeSabrRequest request,
            @Nonnull final SabrStreamingResponseReader.SegmentConsumer consumer)
            throws IOException, ExtractionException {
        final long backoffRemainingMs = getBackoffRemainingMs();
        if (backoffRemainingMs > 0) {
            return new RequestResult(0, (int) Math.min(Integer.MAX_VALUE,
                    backoffRemainingMs), true);
        }
        final YoutubeSabrResponse result = fetchAndProcessResponse(request, consumer);
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
            @Nonnull final YoutubeSabrRequest request,
            @Nonnull final SabrStreamingResponseReader.SegmentConsumer segmentConsumer)
            throws IOException, ExtractionException {
        final YoutubeSabrResponse result;
        try {
            result = fetchNextResponse(request, segmentConsumer);
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
                throw new SabrAttestationException(
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
            serverAbrStreamingUrl = redirectUrl;
            updateHostCandidates(redirectUrl);
        }

        if (decoded.getSabrError() != null) {
            throw new SabrProtocolException("SABR error: " + decoded.getSabrError());
        }
        if (decoded.isAttestationRequired()) {
            throw new SabrAttestationException("SABR attestation required: "
                    + decoded.summarizeNoMediaResponse());
        }
        if (decoded.isReloadRequested()) {
            if (tryReload(decoded.getReloadToken())) {
                return;
            }
            throw new SabrReloadException("SABR requested player reload: "
                    + decoded.summarizeNoMediaResponse(), decoded.getReloadToken());
        }

        if (result.getSegmentCount() > 0) {
            redirectCount = 0;
            reloads = 0;
        }
    }

    /** Installs the callback used to fetch a fresh player response on a reload request. */
    public void setInfoReloader(@Nullable final SabrInfoReloader reloader) {
        infoReloader = reloader;
    }

    /**
     * Handles a server-requested player reload: fetches a fresh info through the installed
     * reloader and hot-swaps the streaming URL, ustreamer config and PO token, keeping the
     * playback state (request number, playback cookie, live head) so the session resumes in place.
     *
     * @return {@code true} when a fresh info was applied and the caller should retry
     */
    private boolean tryReload(@Nullable final String token) throws IOException, ExtractionException {
        if (infoReloader == null || reloads >= MAX_RELOADS_PER_SESSION) {
            return false;
        }
        reloads++;
        addDiagnosticEvent("reload_requested n=" + requestNumber + " attempt=" + reloads
                + " token=" + (token == null || token.isEmpty() ? "absent" : "present"));
        final YoutubeSabrInfo fresh = infoReloader.reload(info.getVideoId(),
                token == null ? "" : token);
        if (fresh == null || fresh.getServerAbrStreamingUrl() == null
                || fresh.getServerAbrStreamingUrl().isEmpty()) {
            addDiagnosticEvent("reload_failed n=" + requestNumber);
            return false;
        }
        applyReload(fresh);
        addDiagnosticEvent("reload_applied n=" + requestNumber
                + " url=" + hostOf(fresh.getServerAbrStreamingUrl()));
        return true;
    }

    private synchronized void applyReload(@Nonnull final YoutubeSabrInfo fresh) {
        info = fresh;
        serverAbrStreamingUrl = fresh.getServerAbrStreamingUrl();
        updateHostCandidates(serverAbrStreamingUrl);
        redirectCount = 0;
        consecutiveIntegrityFailures = 0;
        sabrContexts.clear();
        activeSabrContextTypes.clear();
        final byte[] freshPoToken = fresh.getPoToken();
        if (freshPoToken != null && freshPoToken.length > 0) {
            poToken = freshPoToken;
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

    public synchronized void setPoToken(@Nullable final byte[] value) {
        poToken = value == null ? null : value.clone();
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
