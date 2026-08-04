package org.schabi.newpipe.extractor.services.youtube.sabr;

import org.schabi.newpipe.extractor.services.youtube.sabr.exception.SabrProtocolException;
import org.schabi.newpipe.extractor.services.youtube.sabr.generated.SabrFormatInitializationMetadata;
import org.schabi.newpipe.extractor.services.youtube.sabr.generated.SabrMediaHeader;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.schabi.newpipe.extractor.services.youtube.sabr.media.SabrMediaSegment;
import org.schabi.newpipe.extractor.services.youtube.sabr.protocol.UmpReader;

/** The complete HTTP, UMP control and media result of one SABR request. */
public final class YoutubeSabrResponse {
    public static final int ATTESTATION_PENDING = 2;
    public static final int ATTESTATION_REQUIRED = 3;
    private static final int MAX_MALFORMED_PARTS = 16;
    private static final int MAX_MALFORMED_MESSAGE_CHARS = 256;

    @Nullable private YoutubeSabrInfo info;
    @Nonnull private List<SabrMediaSegment> segments = Collections.emptyList();
    private int segmentCount;
    private int responseCode;
    @Nonnull private String contentType = "";
    private long responseBytes;
    private long mediaPayloadBytes;
    private long mediaPartPayloadBytes;
    private long controlPayloadBytes;
    private long totalPayloadBytes;
    private long maxPartBytes;
    private long maxMediaPartPayloadBytes;
    private long maxSegmentBytes;
    private long requestElapsedMs;
    private long firstSegmentElapsedMs = -1;

    private final List<UmpReader.UmpPart> parts = new ArrayList<>();
    private final List<String> partSummaries = new ArrayList<>();
    private final List<String> wireFieldSummaries = new ArrayList<>();
    private final List<SabrFormatInitializationMetadata> formatInitializationMetadata =
            new ArrayList<>();
    private final List<SabrMediaHeader> mediaHeaders = new ArrayList<>();
    private final List<byte[]> sabrContextUpdates = new ArrayList<>();
    private final List<byte[]> liveMetadata = new ArrayList<>();
    private final Map<Integer, Long> mediaBytesByHeaderId = new LinkedHashMap<>();
    private final List<Integer> mediaEndHeaderIds = new ArrayList<>();
    private final List<Integer> unknownPartTypes = new ArrayList<>();
    private final List<String> malformedParts = new ArrayList<>();
    private final Map<Integer, List<String>> genericPartDescriptions = new LinkedHashMap<>();
    @Nullable private String redirectUrl;
    @Nullable private String sabrError;
    @Nullable private byte[] nextRequestPolicy;
    @Nullable private byte[] sabrContextSendingPolicy;
    private int streamProtectionStatus = -1;
    private int streamProtectionMaxRetries = -1;
    private int backoffTimeMs = -1;
    private boolean reloadRequested;

    public YoutubeSabrResponse() { }

    void complete(@Nonnull final YoutubeSabrInfo requestInfo,
                  @Nonnull final List<SabrMediaSegment> responseSegments,
                  final int responseSegmentCount, final int httpResponseCode,
                  @Nonnull final String responseContentType, final long rawResponseBytes,
                  final long rawMediaPayloadBytes, final long rawMediaPartPayloadBytes,
                  final long rawControlPayloadBytes, final long rawTotalPayloadBytes,
                  final long largestPartBytes, final long largestMediaPartPayloadBytes,
                  final long largestSegmentBytes, final long elapsedMs,
                  final long firstMediaElapsedMs) {
        info = requestInfo;
        segments = responseSegments;
        segmentCount = responseSegmentCount;
        responseCode = httpResponseCode;
        contentType = responseContentType;
        responseBytes = rawResponseBytes;
        mediaPayloadBytes = rawMediaPayloadBytes;
        mediaPartPayloadBytes = rawMediaPartPayloadBytes;
        controlPayloadBytes = rawControlPayloadBytes;
        totalPayloadBytes = rawTotalPayloadBytes;
        maxPartBytes = largestPartBytes;
        maxMediaPartPayloadBytes = largestMediaPartPayloadBytes;
        maxSegmentBytes = largestSegmentBytes;
        requestElapsedMs = elapsedMs;
        firstSegmentElapsedMs = firstMediaElapsedMs;
    }

    public void addPart(@Nonnull final UmpReader.UmpPart part) {
        parts.add(part);
        addPartSummary(partSummaries, part.getType(), part.getSize());
    }

    public void setPartSummaries(@Nonnull final List<String> summaries) {
        partSummaries.clear();
        partSummaries.addAll(summaries);
    }

    public static void addPartSummary(@Nonnull final List<String> summaries,
                               final int type, final int size) {
        final String value = type + ":" + size;
        if (summaries.isEmpty()) {
            summaries.add(value);
            return;
        }
        final int lastIndex = summaries.size() - 1;
        final String last = summaries.get(lastIndex);
        if (last.equals(value)) summaries.set(lastIndex, value + "x2");
        else if (last.startsWith(value + 'x')) summaries.set(lastIndex, value + 'x'
                + (Integer.parseInt(last.substring(value.length() + 1)) + 1));
        else summaries.add(value);
    }

    public void addUnknownPartType(final int type) { unknownPartTypes.add(type); }
    public void addWireFieldSummary(final int type, @Nonnull final String summary) {
        wireFieldSummaries.add(type + "={" + summary + '}');
    }
    public void addGenericPartDescription(final int type, @Nonnull final String description) {
        genericPartDescriptions.computeIfAbsent(type, ignored -> new ArrayList<>())
                .add(description);
    }
    public void addMalformedPart(final int type, final int size,
                          @Nonnull final SabrProtocolException error) {
        if (malformedParts.size() >= MAX_MALFORMED_PARTS) return;
        final String message = String.valueOf(error.getMessage());
        malformedParts.add(type + ":" + size + ":" + (message.length()
                > MAX_MALFORMED_MESSAGE_CHARS
                ? message.substring(0, MAX_MALFORMED_MESSAGE_CHARS) : message));
    }
    public void addFormatInitializationMetadata(
            @Nonnull final SabrFormatInitializationMetadata value) {
        formatInitializationMetadata.add(value);
    }
    public void addMediaHeader(@Nonnull final SabrMediaHeader value) { mediaHeaders.add(value); }
    public void addSabrContextUpdate(@Nonnull final byte[] data) {
        sabrContextUpdates.add(data.clone());
    }
    public void addLiveMetadata(@Nonnull final byte[] data) { liveMetadata.add(data.clone()); }
    public void addMediaBytes(final int headerId, final long bytes) {
        mediaBytesByHeaderId.put(headerId,
                mediaBytesByHeaderId.getOrDefault(headerId, 0L) + bytes);
    }
    public void addMediaEndHeaderId(final int headerId) { mediaEndHeaderIds.add(headerId); }
    public void setRedirectUrl(@Nullable final String value) { redirectUrl = value; }
    public void setSabrError(@Nullable final String value) { sabrError = value; }
    public void setNextRequestPolicy(@Nonnull final byte[] data) { nextRequestPolicy = data.clone(); }
    public void setSabrContextSendingPolicy(@Nonnull final byte[] data) {
        sabrContextSendingPolicy = data.clone();
    }
    public void setStreamProtectionStatus(final int value) { streamProtectionStatus = value; }
    public void setStreamProtectionMaxRetries(final int value) { streamProtectionMaxRetries = value; }
    public void setBackoffTimeMs(final int value) { backoffTimeMs = value; }
    public void setReloadRequested(final boolean value) { reloadRequested = value; }

    @Nonnull public YoutubeSabrInfo getInfo() { return java.util.Objects.requireNonNull(info); }
    @Nonnull public List<SabrMediaSegment> getSegments() { return segments; }
    public int getSegmentCount() { return segmentCount; }
    public int getResponseCode() { return responseCode; }
    @Nonnull public String getContentType() { return contentType; }
    public long getResponseBytes() { return responseBytes; }
    public long getMediaPayloadBytes() { return mediaPayloadBytes; }
    public long getMediaPartPayloadBytes() { return mediaPartPayloadBytes; }
    public long getControlPayloadBytes() { return controlPayloadBytes; }
    public long getTotalPayloadBytes() { return totalPayloadBytes; }
    public long getMaxPartBytes() { return maxPartBytes; }
    public long getMaxMediaPartPayloadBytes() { return maxMediaPartPayloadBytes; }
    public long getMaxSegmentBytes() { return maxSegmentBytes; }
    public long getRequestElapsedMs() { return requestElapsedMs; }
    public long getFirstSegmentElapsedMs() { return firstSegmentElapsedMs; }
    @Nonnull public List<UmpReader.UmpPart> getParts() {
        return Collections.unmodifiableList(parts);
    }
    @Nonnull public List<SabrFormatInitializationMetadata> getFormatInitializationMetadata() {
        return Collections.unmodifiableList(formatInitializationMetadata);
    }
    @Nonnull public List<SabrMediaHeader> getMediaHeaders() {
        return Collections.unmodifiableList(mediaHeaders);
    }
    @Nonnull public List<byte[]> getSabrContextUpdates() {
        return Collections.unmodifiableList(sabrContextUpdates);
    }
    @Nonnull public List<byte[]> getLiveMetadata() {
        return Collections.unmodifiableList(liveMetadata);
    }
    @Nullable public String getRedirectUrl() { return redirectUrl; }
    @Nullable public String getSabrError() { return sabrError; }
    @Nullable public byte[] getNextRequestPolicy() {
        return nextRequestPolicy == null ? null : nextRequestPolicy.clone();
    }
    @Nullable public byte[] getSabrContextSendingPolicy() {
        return sabrContextSendingPolicy == null ? null : sabrContextSendingPolicy.clone();
    }
    public int getStreamProtectionStatus() { return streamProtectionStatus; }
    public int getStreamProtectionMaxRetries() { return streamProtectionMaxRetries; }
    public int getBackoffTimeMs() { return backoffTimeMs; }
    public boolean isReloadRequested() { return reloadRequested; }
    public boolean hasMedia() { return !mediaHeaders.isEmpty() || !mediaBytesByHeaderId.isEmpty(); }
    public boolean isNoMediaResponse() { return !hasMedia(); }
    public boolean isPolicyOnlyResponse() {
        return isNoMediaResponse() && nextRequestPolicy != null;
    }
    public boolean isAttestationRequired() {
        return streamProtectionStatus == ATTESTATION_REQUIRED;
    }
    public boolean isAttestationPending() {
        return streamProtectionStatus == ATTESTATION_PENDING;
    }

    @Nonnull
    public List<String> getIntegrityIssues() {
        final List<String> issues = new ArrayList<>();
        final List<Integer> headerIds = new ArrayList<>();
        for (final SabrMediaHeader header : mediaHeaders) {
            if (headerIds.contains(header.getHeaderId())) {
                issues.add("duplicate-media-header:" + header.getHeaderId());
            }
            headerIds.add(header.getHeaderId());
            final Long bytes = mediaBytesByHeaderId.get(header.getHeaderId());
            if (bytes == null) issues.add("missing-media:" + header.getHeaderId());
            else if (header.getContentLength() >= 0 && bytes != header.getContentLength()) {
                issues.add("length-mismatch:" + header.getHeaderId() + ":expected="
                        + header.getContentLength() + ":actual=" + bytes);
            }
            if (!mediaEndHeaderIds.contains(header.getHeaderId())) {
                issues.add("missing-media-end:" + header.getHeaderId());
            }
        }
        for (final Integer id : mediaBytesByHeaderId.keySet()) {
            if (!headerIds.contains(id)) issues.add("media-without-header:" + id);
        }
        for (final Integer id : mediaEndHeaderIds) {
            if (!headerIds.contains(id)) issues.add("media-end-without-header:" + id);
        }
        return issues;
    }

    @Nonnull
    public String summarizeForDiagnostics() {
        return "parts=" + partSummaries + ", wireFields=" + wireFieldSummaries
                + ", controls=" + genericPartDescriptions + ", mediaHeaders=" + mediaHeaders.size()
                + ", mediaBytes=" + mediaBytesByHeaderId + ", mediaEnds=" + mediaEndHeaderIds
                + ", integrity=" + getIntegrityIssues() + ", malformedParts=" + malformedParts
                + ", unknownParts=" + unknownPartTypes + ", protection="
                + streamProtectionStatus + '/' + streamProtectionMaxRetries
                + ", backoffMs=" + backoffTimeMs + ", reload=" + reloadRequested;
    }

    @Nonnull
    public String summarizeNoMediaResponse() {
        return "parts=" + parts.size() + ", status=" + streamProtectionStatus
                + ", maxRetries=" + streamProtectionMaxRetries + ", backoffMs=" + backoffTimeMs
                + ", policy=" + (nextRequestPolicy != null) + ", reload=" + reloadRequested
                + ", redirect=" + (redirectUrl != null && !redirectUrl.isEmpty())
                + ", error=" + (sabrError == null ? "null" : sabrError);
    }
}
