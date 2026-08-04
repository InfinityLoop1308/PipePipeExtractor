package org.schabi.newpipe.extractor.services.youtube.sabr;

import org.schabi.newpipe.extractor.services.youtube.sabr.exception.SabrProtocolException;
import org.schabi.newpipe.extractor.services.youtube.sabr.generated.SabrFormatInitializationMetadata;
import org.schabi.newpipe.extractor.services.youtube.sabr.generated.SabrMediaHeader;
import org.schabi.newpipe.extractor.services.youtube.sabr.media.SabrMediaSegment;
import org.schabi.newpipe.extractor.services.youtube.sabr.media.SabrMp4SegmentIndexParser;
import org.schabi.newpipe.extractor.services.youtube.sabr.media.SabrSegmentIndex;
import org.schabi.newpipe.extractor.services.youtube.sabr.media.SabrWebmSegmentIndexParser;
import org.schabi.newpipe.extractor.services.youtube.sabr.protocol.SabrProto;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public final class YoutubeSabrStreamState {
    public static final int TRACK_MODE_VIDEO_AND_AUDIO = 0;
    public static final int TRACK_MODE_AUDIO_ONLY = 1;
    public static final int TRACK_MODE_VIDEO_ONLY = 2;

    private final FormatProgress audio;
    private final FormatProgress video;
    private final Map<Integer, byte[]> sabrContexts = new LinkedHashMap<>();
    private final Set<Integer> activeSabrContextTypes = new LinkedHashSet<>();
    @Nullable
    private byte[] playbackCookie;
    @Nullable
    private byte[] poToken;
    private volatile int targetAudioReadaheadMs = -1;
    private volatile int targetVideoReadaheadMs = -1;
    private volatile int maxTimeSinceLastRequestMs = -1;
    private volatile int minAudioReadaheadMs = -1;
    private volatile int minVideoReadaheadMs = -1;
    private long playerTimeMsOverride = -1;
    private volatile int enabledTrackTypesBitfield = TRACK_MODE_VIDEO_AND_AUDIO;
    private volatile boolean selectAudioFormat = true;
    private volatile boolean selectVideoFormat = true;
    private volatile boolean preferAudioFormat = true;
    private volatile boolean preferVideoFormat = true;
    private boolean writeFirstRequestPlaybackState;
    private boolean writeTopLevelPlayerTimeMs = true;
    private long bandwidthEstimate = -1;
    private float playbackRate = 1.0f;
    private boolean writeLastManualSelectedResolution;
    private boolean selectVideoFormatBeforeAudio;
    private boolean suppressBufferedRanges;

    // how close to the head counts as "at the live edge" (segments of slack before we wait)
    private static final long LIVE_EDGE_MARGIN_SEGMENTS = 2;
    // live: foundation only. we record what the server tells us about the live edge (via
    // LIVE_METADATA) so a future live-aware pump can follow the head. VOD never sets these.
    private boolean live;
    private boolean postLiveDvr;
    private long liveHeadSequenceNumber = -1;
    private long liveHeadTimeMs = -1;

    public YoutubeSabrStreamState(@Nonnull final YoutubeSabrInfo.Format audioFormat,
                                  @Nonnull final YoutubeSabrInfo.Format videoFormat) {
        audio = new FormatProgress(audioFormat);
        video = new FormatProgress(videoFormat);
    }

    public boolean ingest(@Nonnull final YoutubeSabrResponse response) {
        final boolean progressed = ingestLocalProgress(response);
        final byte[] nextRequestPolicy = response.getNextRequestPolicy();
        if (nextRequestPolicy != null) {
            ingestNextRequestPolicy(nextRequestPolicy);
        }
        for (final byte[] contextUpdate : response.getSabrContextUpdates()) {
            ingestContextUpdate(contextUpdate);
        }
        final byte[] contextSendingPolicy = response.getSabrContextSendingPolicy();
        if (contextSendingPolicy != null) {
            ingestContextSendingPolicy(contextSendingPolicy);
        }
        return progressed;
    }

    boolean ingestLocalProgress(@Nonnull final YoutubeSabrResponse response) {
        boolean progressed = false;
        for (final byte[] metadata : response.getLiveMetadata()) {
            ingestLiveMetadata(metadata);
        }
        for (final SabrFormatInitializationMetadata metadata
                : response.getFormatInitializationMetadata()) {
            final FormatProgress progress = findProgressForItag(metadata.getItag());
            if (progress != null) {
                progressed |= progress.observeMetadata(metadata);
            }
        }
        for (final SabrMediaHeader header : response.getMediaHeaders()) {
            final FormatProgress progress = findProgressForItag(header.getItag());
            if (progress != null) {
                progressed |= progress.observeHeader(header);
            }
        }
        return progressed;
    }

    public boolean ingest(@Nonnull final SabrMediaSegment segment) {
        final FormatProgress progress = findProgressForItag(segment.getHeader().getItag());
        return progress != null && progress.observeSegment(segment);
    }

    public boolean ingestInitializationData(@Nonnull final YoutubeSabrInfo.Format format,
                                            @Nonnull final byte[] data) {
        final FormatProgress progress = findProgressForItag(format.getItag());
        if (progress == null) {
            return false;
        }
        progress.initReceived = true;
        progress.observeInitializationData(data);
        return true;
    }

    void forEachBufferedRange(@Nonnull final BufferedRangeConsumer consumer) {
        if (suppressBufferedRanges) {
            return;
        }
        if (isAudioEnabled()) {
            audio.emitBufferedRange(consumer);
        }
        if (isVideoEnabled()) {
            video.emitBufferedRange(consumer);
        }
    }

    void setSuppressBufferedRanges(final boolean suppressBufferedRanges) {
        this.suppressBufferedRanges = suppressBufferedRanges;
    }

    public long getPlayerTimeMs() {
        if (playerTimeMsOverride >= 0) {
            return playerTimeMsOverride;
        }
        return Math.max(audio.getBufferedEndMs(), video.getBufferedEndMs());
    }

    long getRequestPlayerTimeMs() {
        if (playerTimeMsOverride >= 0) {
            return playerTimeMsOverride;
        }
        if ((isAudioEnabled() && !audio.initReceived)
                || (isVideoEnabled() && !video.initReceived)) {
            return 0;
        }
        return getPlayerTimeMs();
    }

    /** buffered end (ms) of the slower track = how far we can actually play. the weakest link wins. */
    public long getMinBufferedEndMs() {
        if (!isVideoEnabled()) {
            return audio.getBufferedEndMs();
        }
        if (!isAudioEnabled()) {
            return video.getBufferedEndMs();
        }
        return Math.min(audio.getBufferedEndMs(), video.getBufferedEndMs());
    }

    public long getBufferedEndMs(@Nonnull final YoutubeSabrInfo.Format format) {
        return progressForItag(format.getItag()).getBufferedEndMs();
    }

    public void setPlayerTimeMs(final long playerTimeMs) {
        playerTimeMsOverride = Math.max(0, playerTimeMs);
    }

    public void clearPlayerTimeMsOverride() {
        playerTimeMsOverride = -1;
    }

    @Nonnull
    synchronized InitializationRequestSnapshot prepareInitializationRequest(
            @Nonnull final YoutubeSabrInfo.Format format) {
        final InitializationRequestSnapshot snapshot = new InitializationRequestSnapshot(
                enabledTrackTypesBitfield,
                selectAudioFormat,
                selectVideoFormat,
                preferAudioFormat,
                preferVideoFormat,
                playerTimeMsOverride,
                suppressBufferedRanges,
                writeFirstRequestPlaybackState,
                writeTopLevelPlayerTimeMs,
                writeLastManualSelectedResolution);
        final long playerTimeMs = getPlayerTimeMs();
        writeFirstRequestPlaybackState = true;
        writeTopLevelPlayerTimeMs = false;
        writeLastManualSelectedResolution = format.isVideo();
        suppressBufferedRanges = true;
        enabledTrackTypesBitfield = format.isVideo()
                ? TRACK_MODE_VIDEO_ONLY : TRACK_MODE_AUDIO_ONLY;
        selectAudioFormat = false;
        selectVideoFormat = false;
        preferAudioFormat = true;
        preferVideoFormat = true;
        playerTimeMsOverride = Math.max(0, playerTimeMs);
        return snapshot;
    }

    synchronized void restoreInitializationRequest(
            @Nonnull final InitializationRequestSnapshot snapshot) {
        enabledTrackTypesBitfield = snapshot.enabledTrackTypesBitfield;
        selectAudioFormat = snapshot.selectAudioFormat;
        selectVideoFormat = snapshot.selectVideoFormat;
        preferAudioFormat = snapshot.preferAudioFormat;
        preferVideoFormat = snapshot.preferVideoFormat;
        playerTimeMsOverride = snapshot.playerTimeMsOverride;
        suppressBufferedRanges = snapshot.suppressBufferedRanges;
        writeFirstRequestPlaybackState = snapshot.writeFirstRequestPlaybackState;
        writeTopLevelPlayerTimeMs = snapshot.writeTopLevelPlayerTimeMs;
        writeLastManualSelectedResolution = snapshot.writeLastManualSelectedResolution;
    }

    void clearPlaybackCookie() {
        playbackCookie = null;
    }

    void resetServerSessionState() {
        playbackCookie = null;
        targetAudioReadaheadMs = -1;
        targetVideoReadaheadMs = -1;
        maxTimeSinceLastRequestMs = -1;
        minAudioReadaheadMs = -1;
        minVideoReadaheadMs = -1;
        sabrContexts.clear();
        activeSabrContextTypes.clear();
    }

    boolean isInitialized(@Nonnull final YoutubeSabrInfo.Format format) {
        return progressForItag(format.getItag()).initReceived;
    }

    void resetInitialization(@Nonnull final YoutubeSabrInfo.Format format) {
        progressForItag(format.getItag()).initReceived = false;
    }

    @Nullable
    public byte[] getPlaybackCookie() {
        return playbackCookie == null ? null : playbackCookie.clone();
    }

    public void setPoToken(@Nullable final byte[] poToken) {
        this.poToken = poToken == null ? null : poToken.clone();
    }

    @Nullable
    public byte[] getPoToken() {
        return poToken == null ? null : poToken.clone();
    }

    @Nullable
    byte[] getRawPlaybackCookie() {
        return playbackCookie;
    }

    @Nullable
    byte[] getRawPoToken() {
        return poToken;
    }

    @Nonnull
    Map<Integer, byte[]> getActiveSabrContexts() {
        final Map<Integer, byte[]> activeSabrContexts = new LinkedHashMap<>();
        for (final Integer type : activeSabrContextTypes) {
            final byte[] value = sabrContexts.get(type);
            if (value != null) {
                activeSabrContexts.put(type, value);
            }
        }
        return activeSabrContexts;
    }

    @Nonnull
    Collection<Integer> getUnsentSabrContextTypes() {
        final List<Integer> unsentSabrContextTypes = new ArrayList<>();
        for (final Integer type : sabrContexts.keySet()) {
            if (!activeSabrContextTypes.contains(type)) {
                unsentSabrContextTypes.add(type);
            }
        }
        return unsentSabrContextTypes;
    }

    public boolean isComplete() {
        return (!isAudioEnabled() || audio.isComplete())
                && (!isVideoEnabled() || video.isComplete());
    }

    /** True once the server has sent live metadata for this stream (foundation for live support). */
    public boolean isLive() {
        return live;
    }

    /** True for an ended live stream still seekable as DVR. */
    public boolean isPostLiveDvr() {
        return postLiveDvr;
    }

    /** Latest segment the live edge has reached, or -1 if unknown / not live. */
    public long getLiveHeadSequenceNumber() {
        return liveHeadSequenceNumber;
    }

    /** Live head position in ms, or -1 if unknown / not live. */
    public long getLiveHeadTimeMs() {
        return liveHeadTimeMs;
    }

    /**
     * True when we have fetched up to (within a small margin of) the live head: the slower track has
     * reached the edge, so a live-aware pump should wait for the head to advance instead of treating
     * an empty response as the end of the stream. Always false for VOD or before the head is known.
     */
    public boolean isAtLiveEdge(@Nonnull final YoutubeSabrInfo.Format audioFormat,
                                @Nonnull final YoutubeSabrInfo.Format videoFormat) {
        if (!live || liveHeadSequenceNumber < 0) {
            return false;
        }
        final long slowerTrack = Math.min(getMaxSegment(audioFormat), getMaxSegment(videoFormat));
        return slowerTrack >= liveHeadSequenceNumber - LIVE_EDGE_MARGIN_SEGMENTS;
    }

    public int getMaxSegment(@Nonnull final YoutubeSabrInfo.Format format) {
        return progressForItag(format.getItag()).maxSegment;
    }

    public long getEndSegment(@Nonnull final YoutubeSabrInfo.Format format) {
        return progressForItag(format.getItag()).endSegment;
    }

    /** True only after initialization bytes yielded an exact per-segment time index. */
    public boolean hasSegmentIndex(@Nonnull final YoutubeSabrInfo.Format format) {
        return progressForItag(format.getItag()).segmentIndex != null;
    }

    public boolean isComplete(@Nonnull final YoutubeSabrInfo.Format format) {
        return progressForItag(format.getItag()).isComplete();
    }

    public void assumeBufferedUntil(@Nonnull final YoutubeSabrInfo.Format format,
                                    final int endSegment) {
        if (endSegment > 0) {
            progressForItag(format.getItag()).assumeBufferedUntil(endSegment);
        }
    }

    /**
     * Backward seek: forget buffered segments at/after {@code fromSegment} so the next request
     * re-sends from it. {@link #assumeBufferedUntil} only ever extends the buffered head, so it
     * cannot rewind; this shrinks it.
     */
    public void rewindBufferedTo(@Nonnull final YoutubeSabrInfo.Format format, final int fromSegment) {
        if (fromSegment > 0) {
            progressForItag(format.getItag()).rewindBufferedTo(fromSegment);
        }
    }

    /**
     * Forward jump (cold seek far past the buffered edge): claim everything before
     * {@code fromSegment} as buffered and move the head onto it, so the server streams from the
     * target instead of filling the skipped span. {@link #rewindBufferedTo} is the backward
     * counterpart.
     */
    public void jumpBufferedTo(@Nonnull final YoutubeSabrInfo.Format format, final int fromSegment) {
        if (fromSegment > 0) {
            progressForItag(format.getItag()).jumpBufferedTo(fromSegment);
        }
    }

    public synchronized void setRequestTrackMode(final int enabledTrackTypesBitfield,
                                                 final boolean selectAudioFormat,
                                                 final boolean selectVideoFormat) {
        this.enabledTrackTypesBitfield = enabledTrackTypesBitfield;
        this.selectAudioFormat = selectAudioFormat;
        this.selectVideoFormat = selectVideoFormat;
        this.preferAudioFormat = selectAudioFormat;
        this.preferVideoFormat = selectVideoFormat;
    }

    synchronized void setPreferredTrackTypes(final boolean videoActive,
                                             final boolean audioActive) {
        preferAudioFormat = audioActive;
        preferVideoFormat = videoActive;
    }

    public void setActiveTrackTypes(final boolean videoActive, final boolean audioActive) {
        if (audioActive && !videoActive) {
            setRequestTrackMode(TRACK_MODE_AUDIO_ONLY,
                    true, false);
        } else if (videoActive && !audioActive) {
            setRequestTrackMode(TRACK_MODE_VIDEO_ONLY,
                    false, true);
        } else if (videoActive) {
            setRequestTrackMode(TRACK_MODE_VIDEO_AND_AUDIO,
                    true, true);
        }
    }

    public void setAudioOnlyRequestMode() {
        setRequestTrackMode(TRACK_MODE_AUDIO_ONLY, true, false);
    }

    public void setVideoOnlyRequestMode() {
        setRequestTrackMode(TRACK_MODE_VIDEO_ONLY, false, true);
    }

    public void setVideoAndAudioRequestMode() {
        setRequestTrackMode(TRACK_MODE_VIDEO_AND_AUDIO, true, true);
    }

    private boolean isAudioEnabled() {
        return enabledTrackTypesBitfield != TRACK_MODE_VIDEO_ONLY;
    }

    private boolean isVideoEnabled() {
        return enabledTrackTypesBitfield != TRACK_MODE_AUDIO_ONLY;
    }

    public void setBandwidthEstimate(final long bandwidthEstimate) {
        this.bandwidthEstimate = bandwidthEstimate;
    }

    public long getBandwidthEstimate() {
        return bandwidthEstimate;
    }

    public int getTargetAudioReadaheadMs() { return targetAudioReadaheadMs; }
    public int getTargetVideoReadaheadMs() { return targetVideoReadaheadMs; }
    public int getMaxTimeSinceLastRequestMs() { return maxTimeSinceLastRequestMs; }
    public int getMinAudioReadaheadMs() { return minAudioReadaheadMs; }
    public int getMinVideoReadaheadMs() { return minVideoReadaheadMs; }

    public void setPlaybackRate(final float playbackRate) {
        if (playbackRate > 0.0f) {
            this.playbackRate = playbackRate;
        }
    }

    float getPlaybackRate() {
        return playbackRate;
    }

    int getEnabledTrackTypesBitfield() {
        return enabledTrackTypesBitfield;
    }

    boolean shouldSelectAudioFormat() {
        return selectAudioFormat;
    }

    boolean shouldSelectVideoFormat() {
        return selectVideoFormat;
    }

    boolean isAudioTrackEnabled() {
        return isAudioEnabled();
    }

    boolean isVideoTrackEnabled() {
        return isVideoEnabled();
    }

    boolean shouldPreferAudioFormat() {
        return preferAudioFormat;
    }

    boolean shouldPreferVideoFormat() {
        return preferVideoFormat;
    }

    void setWriteFirstRequestPlaybackState(final boolean writeFirstRequestPlaybackState) {
        this.writeFirstRequestPlaybackState = writeFirstRequestPlaybackState;
    }

    boolean shouldWriteFirstRequestPlaybackState() {
        return writeFirstRequestPlaybackState;
    }

    boolean shouldWriteTopLevelPlayerTimeMs() {
        return writeTopLevelPlayerTimeMs;
    }

    boolean shouldWriteLastManualSelectedResolution() {
        return writeLastManualSelectedResolution;
    }

    public void setSelectVideoFormatBeforeAudio(final boolean selectVideoFormatBeforeAudio) {
        this.selectVideoFormatBeforeAudio = selectVideoFormatBeforeAudio;
    }

    boolean shouldSelectVideoFormatBeforeAudio() {
        return selectVideoFormatBeforeAudio;
    }

    @Nonnull
    public String summarizeBufferedRanges() {
        final StringBuilder builder = new StringBuilder();
        forEachBufferedRange((itag, lastModified, xtags, startTimeMs, durationMs,
                              startSegmentIndex, endSegmentIndex, timescale) -> {
            if (builder.length() > 0) {
                builder.append(',');
            }
            builder.append("itag=").append(itag)
                    .append(":seq=").append(startSegmentIndex).append('-')
                    .append(endSegmentIndex)
                    .append(":time=").append(startTimeMs).append('+').append(durationMs)
                    .append(":timescale=").append(timescale);
        });
        return builder.toString();
    }

    public long getAverageSegmentDurationMs(@Nonnull final YoutubeSabrInfo.Format format) {
        return progressForItag(format.getItag()).averageDurationMs;
    }

    public long getSegmentStartMs(@Nonnull final YoutubeSabrInfo.Format format,
                                  final int sequenceNumber) {
        return progressForItag(format.getItag()).getSegmentStartMs(sequenceNumber);
    }

    public long getSegmentEndMs(@Nonnull final YoutubeSabrInfo.Format format,
                                 final int sequenceNumber) {
        return progressForItag(format.getItag()).getSegmentEndMs(sequenceNumber);
    }

    public int getSegmentNumberAtOrAfterTimeMs(@Nonnull final YoutubeSabrInfo.Format format,
                                               final long timeMs) {
        return progressForItag(format.getItag()).getSegmentNumberAtOrAfterTimeMs(timeMs);
    }

    @Nonnull
    private FormatProgress progressForItag(final int itag) {
        final FormatProgress progress = findProgressForItag(itag);
        if (progress == null) {
            throw new IllegalArgumentException("Unknown SABR itag: " + itag);
        }
        return progress;
    }

    @Nullable
    private FormatProgress findProgressForItag(final int itag) {
        if (audio.itag == itag) {
            return audio;
        }
        if (video.itag == itag) {
            return video;
        }
        return null;
    }

    private void ingestNextRequestPolicy(@Nonnull final byte[] data) {
        try {
            for (final SabrProto.Field field : SabrProto.readFields(data)) {
                switch (field.getNumber()) {
                    case 1: targetAudioReadaheadMs = (int) field.getVarint(); break;
                    case 2: targetVideoReadaheadMs = (int) field.getVarint(); break;
                    case 3: maxTimeSinceLastRequestMs = (int) field.getVarint(); break;
                    case 5: minAudioReadaheadMs = (int) field.getVarint(); break;
                    case 6: minVideoReadaheadMs = (int) field.getVarint(); break;
                    case 7: playbackCookie = field.getBytes(); break;
                    default: break;
                }
            }
        } catch (final SabrProtocolException ignored) {
            // The decoder already records malformed control parts.
        }
    }

    private void ingestLiveMetadata(@Nonnull final byte[] data) {
        try {
            live = true;
            for (final SabrProto.Field field : SabrProto.readFields(data)) {
                switch (field.getNumber()) {
                    case 3: liveHeadSequenceNumber = field.getVarint(); break;
                    case 4: liveHeadTimeMs = field.getVarint(); break;
                    case 8: postLiveDvr = field.getVarint() != 0; break;
                    default: break;
                }
            }
        } catch (final SabrProtocolException ignored) {
            // The decoder already records malformed control parts.
        }
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
        if (sendByDefault) activeSabrContextTypes.add(type);
    }

    private void ingestContextSendingPolicy(@Nonnull final byte[] data) {
        try {
            for (final SabrProto.Field field : SabrProto.readFields(data)) {
                final List<Long> values = field.getWireType() == SabrProto.WIRE_VARINT
                        ? java.util.Collections.singletonList(field.getVarint())
                        : field.getWireType() == SabrProto.WIRE_LENGTH_DELIMITED
                        ? SabrProto.readPackedVarints(field.getBytes())
                        : java.util.Collections.emptyList();
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
            // The decoder already records malformed control parts.
        }
    }

    @FunctionalInterface
    interface BufferedRangeConsumer {
        void accept(int itag,
                    long lastModified,
                    @Nullable String xtags,
                    long startTimeMs,
                    long durationMs,
                    int startSegmentIndex,
                    int endSegmentIndex,
                    int timescale);
    }

    private static final class FormatProgress {
        @Nonnull
        private final YoutubeSabrInfo.Format format;
        private final int itag;
        private final long lastModified;
        @Nullable
        private final String xtags;
        // pump thread writes it, ExoPlayer loader threads read it. volatile so they actually see it.
        private volatile boolean initReceived;
        private volatile int maxSegment;
        // Highest segment with NO gap from the start. We tell the server we have up to here so it
        // fills the exact next segment, instead of skipping ahead off an overstated maxSegment and
        // leaving a hole the sequential reader then starves on forever.
        private volatile int contiguousMaxSegment;
        private final java.util.Set<Integer> aheadOfContiguous = new java.util.HashSet<>();
        private int observedMaxSegment;
        private volatile long endSegment = -1;
        private long averageDurationMs = 5000;
        private int firstObservedSegment = -1;
        private long observedStartMs = -1;
        private long observedEndMs = -1;
        @Nullable
        private SabrFormatInitializationMetadata metadata;
        @Nullable
        private SabrSegmentIndex segmentIndex;

        private FormatProgress(@Nonnull final YoutubeSabrInfo.Format format) {
            this.format = format;
            itag = format.getItag();
            lastModified = format.getLastModified();
            xtags = format.getXtags();
        }

        private boolean observeMetadata(@Nonnull final SabrFormatInitializationMetadata metadata) {
            this.metadata = metadata;
            final long previousEndSegment = endSegment;
            endSegment = metadata.getEndSegmentNumber();
            if (metadata.getDurationUnits() > 0 && metadata.getDurationTimescale() > 0
                    && metadata.getEndSegmentNumber() > 0) {
                final long totalMs = metadata.getDurationUnits() * 1000L
                        / metadata.getDurationTimescale();
                averageDurationMs = Math.max(1L, totalMs / metadata.getEndSegmentNumber());
            } else if (endSegment > 0 && format.getApproxDurationMs() > 0) {
                // The init metadata gives the segment count but no per-segment timing for this
                // format (seen on some YouTube responses). Derive the average from the format's
                // total duration so a cold seek maps the time to the right segment, instead of the
                // 5000ms default that doubles ~10s-audio-segment numbers and overshoots endSegment
                // -> out-of-bounds request -> the server returns nothing -> endless buffering.
                averageDurationMs = Math.max(1L, format.getApproxDurationMs() / endSegment);
            }
            return previousEndSegment != endSegment;
        }

        private boolean observeSegment(@Nonnull final SabrMediaSegment segment) {
            if (!segment.getHeader().isInitSegment() || metadata == null || segmentIndex != null) {
                return false;
            }
            return observeInitializationData(segment.getData());
        }

        private boolean observeInitializationData(@Nonnull final byte[] data) {
            if (segmentIndex != null) {
                return false;
            }
            final String mimeType = metadata == null ? format.getMimeType() : metadata.getMimeType();
            if (mimeType == null) {
                return false;
            }
            try {
                if (mimeType.contains("mp4")) {
                    segmentIndex = metadata == null
                            ? SabrMp4SegmentIndexParser.parse(data, format)
                            : SabrMp4SegmentIndexParser.parse(data, metadata);
                } else if (mimeType.contains("webm")) {
                    segmentIndex = metadata == null
                            ? SabrWebmSegmentIndexParser.parse(data, format)
                            : SabrWebmSegmentIndexParser.parse(data, metadata);
                } else {
                    return false;
                }
                observeSegmentIndex();
                return true;
            } catch (final SabrProtocolException ignored) {
                if (metadata == null) {
                    return false;
                }
                try {
                    if (mimeType.contains("mp4")) {
                        segmentIndex = SabrMp4SegmentIndexParser.parse(data, format);
                    } else if (mimeType.contains("webm")) {
                        segmentIndex = SabrWebmSegmentIndexParser.parse(data, format);
                    } else {
                        return false;
                    }
                    observeSegmentIndex();
                    return true;
                } catch (final SabrProtocolException ignoredFallback) {
                    return false;
                }
            } catch (final Exception ignored) {
                return false;
            }
        }

        private void observeSegmentIndex() {
            if (segmentIndex == null) {
                return;
            }
            if (endSegment <= 0) {
                endSegment = segmentIndex.size();
            }
            if (format.getApproxDurationMs() > 0 && endSegment > 0) {
                averageDurationMs = Math.max(1L, format.getApproxDurationMs() / endSegment);
            }
        }

        private boolean observeHeader(@Nonnull final SabrMediaHeader header) {
            if (header.isInitSegment()) {
                final boolean changed = !initReceived;
                initReceived = true;
                return changed;
            }
            if (header.getSequenceNumber() > maxSegment) {
                maxSegment = header.getSequenceNumber();
            }
            final int seq = header.getSequenceNumber();
            if (seq == contiguousMaxSegment + 1) {
                contiguousMaxSegment = seq;
                while (aheadOfContiguous.remove(contiguousMaxSegment + 1)) {
                    contiguousMaxSegment++;
                }
            } else if (seq > contiguousMaxSegment + 1) {
                aheadOfContiguous.add(seq);
            }
            if (header.getSequenceNumber() > observedMaxSegment) {
                observedMaxSegment = header.getSequenceNumber();
            }
            if (firstObservedSegment < 0 || header.getSequenceNumber() < firstObservedSegment) {
                firstObservedSegment = header.getSequenceNumber();
            }
            if (header.getStartMs() >= 0 && header.getDurationMs() > 0) {
                if (observedStartMs < 0 || header.getStartMs() < observedStartMs) {
                    observedStartMs = header.getStartMs();
                }
                observedEndMs = Math.max(observedEndMs, header.getStartMs() + header.getDurationMs());
            }
            if (header.getSequenceNumber() == maxSegment) {
                return true;
            }
            return false;
        }

        private void emitBufferedRange(@Nonnull final BufferedRangeConsumer consumer) {
            if (!initReceived || maxSegment <= 0) {
                return;
            }
            // Only trust observed timing when there is NO hole (contiguous == max); otherwise the
            // observed end overstates past the gap and the server skips the segment we still need.
            final boolean canUseObservedTiming = observedStartMs >= 0 && observedEndMs > observedStartMs
                    && observedMaxSegment >= maxSegment && firstObservedSegment > 0
                    && contiguousMaxSegment >= maxSegment;
            consumer.accept(itag, lastModified, xtags,
                    canUseObservedTiming ? observedStartMs : 0,
                    canUseObservedTiming ? observedEndMs - observedStartMs : getBufferedEndMs(),
                    canUseObservedTiming ? firstObservedSegment : 1,
                    contiguousMaxSegment, 1000);
        }

        private long getBufferedEndMs() {
            // contiguous, not maxSegment: a hole means we are NOT really buffered past it.
            final long indexedEndMs = getSegmentEndMs(contiguousMaxSegment);
            if (indexedEndMs >= 0) {
                return indexedEndMs;
            }
            return contiguousMaxSegment * averageDurationMs;
        }

        private long getSegmentStartMs(final int sequenceNumber) {
            if (sequenceNumber <= 1) {
                return 0;
            }
            if (segmentIndex != null) {
                final SabrSegmentIndex.Entry entry = segmentIndex.getEntry(sequenceNumber);
                if (entry != null) {
                    return entry.getStartMs();
                }
            }
            return Math.max(0, sequenceNumber - 1L) * averageDurationMs;
        }

        private long getSegmentEndMs(final int sequenceNumber) {
            if (segmentIndex != null) {
                final SabrSegmentIndex.Entry entry = segmentIndex.getEntry(sequenceNumber);
                if (entry != null) {
                    return entry.getEndMs();
                }
            }
            if (sequenceNumber <= 0) {
                return -1;
            }
            return sequenceNumber * averageDurationMs;
        }

        private int getSegmentNumberAtOrAfterTimeMs(final long timeMs) {
            if (timeMs <= 0) {
                return 1;
            }
            if (segmentIndex != null) {
                for (int i = 1; i <= segmentIndex.size(); i++) {
                    final SabrSegmentIndex.Entry entry = segmentIndex.getEntry(i);
                    if (entry != null && entry.getEndMs() > timeMs) {
                        return entry.getSequenceNumber();
                    }
                }
                return segmentIndex.size() == Integer.MAX_VALUE
                        ? Integer.MAX_VALUE : Math.max(1, segmentIndex.size() + 1);
            }
            final long durationMs = Math.max(1, averageDurationMs);
            final long sequenceNumber = timeMs / durationMs + 1;
            return sequenceNumber > Integer.MAX_VALUE
                    ? Integer.MAX_VALUE
                    : Math.max(1, (int) sequenceNumber);
        }

        private void assumeBufferedUntil(final int endSegment) {
            maxSegment = Math.max(maxSegment, endSegment);
        }

        private void rewindBufferedTo(final int fromSegment) {
            final int last = Math.max(0, fromSegment - 1);
            if (last >= contiguousMaxSegment) {
                return; // not a rewind for this track
            }
            // The buffered range the server reads ends at contiguousMaxSegment (+ observed timing),
            // not maxSegment. Shrink both and drop the observed-timing window so the range falls back
            // to the contiguous end; otherwise the server thinks we still hold the target and the
            // re-request comes back empty.
            maxSegment = last;
            contiguousMaxSegment = last;
            observedMaxSegment = Math.min(observedMaxSegment, last);
            firstObservedSegment = -1;
            observedStartMs = -1;
            observedEndMs = -1;
        }

        private void jumpBufferedTo(final int fromSegment) {
            final int last = Math.max(0, fromSegment - 1);
            if (last <= contiguousMaxSegment) {
                return; // not a forward jump for this track
            }
            // Forward jump (cold seek far past the edge): claim everything before the target as
            // buffered. The play head jumped past it and will never play it, and keeping the old
            // contiguous edge made the pump keep reporting/filling the skipped span (ping-pong +
            // duplicate re-sends). A later backward seek into the skipped span goes through the
            // normal rewind path, which re-requests it honestly.
            contiguousMaxSegment = last;
            // Fold in target-zone segments that already arrived out of order, drop pre-jump ones.
            aheadOfContiguous.removeIf(seq -> seq <= last);
            while (aheadOfContiguous.remove(contiguousMaxSegment + 1)) {
                contiguousMaxSegment++;
            }
            maxSegment = Math.max(maxSegment, contiguousMaxSegment);
            // The observed-timing window describes pre-jump data; drop it so the reported range
            // falls back to the contiguous end until fresh headers rebuild it at the target.
            observedMaxSegment = Math.min(observedMaxSegment, contiguousMaxSegment);
            firstObservedSegment = -1;
            observedStartMs = -1;
            observedEndMs = -1;
        }

        private boolean isComplete() {
            return initReceived && endSegment > 0 && maxSegment >= endSegment;
        }
    }

    static final class InitializationRequestSnapshot {
        private final int enabledTrackTypesBitfield;
        private final boolean selectAudioFormat;
        private final boolean selectVideoFormat;
        private final boolean preferAudioFormat;
        private final boolean preferVideoFormat;
        private final long playerTimeMsOverride;
        private final boolean suppressBufferedRanges;
        private final boolean writeFirstRequestPlaybackState;
        private final boolean writeTopLevelPlayerTimeMs;
        private final boolean writeLastManualSelectedResolution;

        private InitializationRequestSnapshot(
                final int enabledTrackTypesBitfield,
                final boolean selectAudioFormat,
                final boolean selectVideoFormat,
                final boolean preferAudioFormat,
                final boolean preferVideoFormat,
                final long playerTimeMsOverride,
                final boolean suppressBufferedRanges,
                final boolean writeFirstRequestPlaybackState,
                final boolean writeTopLevelPlayerTimeMs,
                final boolean writeLastManualSelectedResolution) {
            this.enabledTrackTypesBitfield = enabledTrackTypesBitfield;
            this.selectAudioFormat = selectAudioFormat;
            this.selectVideoFormat = selectVideoFormat;
            this.preferAudioFormat = preferAudioFormat;
            this.preferVideoFormat = preferVideoFormat;
            this.playerTimeMsOverride = playerTimeMsOverride;
            this.suppressBufferedRanges = suppressBufferedRanges;
            this.writeFirstRequestPlaybackState = writeFirstRequestPlaybackState;
            this.writeTopLevelPlayerTimeMs = writeTopLevelPlayerTimeMs;
            this.writeLastManualSelectedResolution = writeLastManualSelectedResolution;
        }
    }
}
