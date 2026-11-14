package org.schabi.newpipe.extractor.services.youtube.sabr;

import org.schabi.newpipe.extractor.services.youtube.sabr.exception.SabrProtocolException;
import org.schabi.newpipe.extractor.services.youtube.sabr.media.SabrMp4SegmentIndexParser;
import org.schabi.newpipe.extractor.services.youtube.sabr.media.SabrSegmentIndex;
import org.schabi.newpipe.extractor.services.youtube.sabr.media.SabrWebmSegmentIndexParser;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/**
 * The segment timeline of one SABR format.
 *
 * <p>Video on demand timelines are parsed from the {@code sidx}/{@code Cues} data of the format
 * initialization segment and are numbered from 1 at presentation time 0. Post-live DVR timelines
 * are derived from the live metadata and the format initialization metadata instead: they use the
 * broadcast's absolute sequence numbers and absolute millisecond timestamps, so the generated DASH
 * manifest has to expose the window start as {@code presentationTimeOffset}.</p>
 */
public final class YoutubeSabrFormatTimeline {
    @Nullable private final SabrSegmentIndex index;
    private final int startSequence;
    private final int endSequence;
    private final long startTimeMs;
    private final long segmentDurationMs;

    private YoutubeSabrFormatTimeline(@Nullable final SabrSegmentIndex index,
                                      final int startSequence,
                                      final int endSequence,
                                      final long startTimeMs,
                                      final long segmentDurationMs) {
        this.index = index;
        this.startSequence = startSequence;
        this.endSequence = endSequence;
        this.startTimeMs = startTimeMs;
        this.segmentDurationMs = segmentDurationMs;
    }

    @Nonnull
    public static YoutubeSabrFormatTimeline parse(
            @Nonnull final YoutubeSabrInfo.Format format,
            @Nonnull final byte[] initializationData) throws SabrProtocolException {
        final String mimeType = format.getMimeType();
        if (mimeType == null) {
            throw new SabrProtocolException("Missing SABR format MIME type: itag="
                    + format.getItag());
        }
        final SabrSegmentIndex index;
        if (mimeType.contains("mp4")) {
            index = SabrMp4SegmentIndexParser.parse(initializationData, format);
        } else if (mimeType.contains("webm")) {
            index = SabrWebmSegmentIndexParser.parse(initializationData, format);
        } else {
            throw new SabrProtocolException("Unsupported SABR initialization format: " + mimeType);
        }
        if (index.size() == 0) {
            throw new SabrProtocolException("Empty SABR segment index: itag=" + format.getItag());
        }
        return new YoutubeSabrFormatTimeline(index, 1, index.size(), 0, 0);
    }

    /**
     * Builds a finite timeline for an ended live stream.
     *
     * @param startSequence first sequence retained by the DVR window, inclusive; live
     *                      sequences start at 0
     * @param endSequence   last sequence of the stream, inclusive
     * @param startTimeMs   absolute broadcast time of {@code startSequence}
     * @param segmentDurationMs target duration of one sequence, used while no exact index exists
     */
    @Nonnull
    public static YoutubeSabrFormatTimeline live(final int startSequence,
                                                 final int endSequence,
                                                 final long startTimeMs,
                                                 final long segmentDurationMs)
            throws SabrProtocolException {
        if (startSequence < 0 || endSequence < startSequence
                || startTimeMs < 0 || segmentDurationMs <= 0) {
            throw new SabrProtocolException("Invalid post-live SABR timeline: start="
                    + startSequence + ", end=" + endSequence + ", time=" + startTimeMs
                    + ", duration=" + segmentDurationMs);
        }
        return new YoutubeSabrFormatTimeline(null, startSequence, endSequence, startTimeMs,
                segmentDurationMs);
    }

    /**
     * Builds the post-live DVR timeline of {@code format} from the live metadata received so far.
     *
     * <p>Post-live SABR has no {@code sidx}/{@code Cues}: the window comes from
     * {@code LIVE_METADATA} and {@code FORMAT_INITIALIZATION_METADATA}. The
     * {@code LIVE_METADATA} post-live flag is not repeated in every response, so any live window
     * is accepted; callers decide whether the stream is post-live.</p>
     *
     * @return the timeline, or {@code null} while the window is not known yet
     */
    @Nullable
    public static YoutubeSabrFormatTimeline fromLiveMetadata(
            @Nonnull final YoutubeSabrSession session,
            @Nonnull final YoutubeSabrInfo.Format format) {
        if (!session.isLive()) {
            return null;
        }
        final long start = session.getLiveStartSegmentNumber(format);
        final long end = session.getLiveEndSegmentNumber(format);
        final long duration = session.getLiveSegmentDurationMs(format);
        if (start < 0 || end < start || end > Integer.MAX_VALUE || duration <= 0) {
            return null;
        }
        try {
            return live((int) start, (int) end,
                    Math.max(0, session.getLiveSeekableStartTimeMs()), duration);
        } catch (final SabrProtocolException error) {
            session.addDiagnosticEvent("post_live_timeline_invalid itag=" + format.getItag()
                    + " message=" + error.getMessage());
            return null;
        }
    }

    public int getStartSequence() {
        return startSequence;
    }

    public int getEndSequence() {
        return endSequence;
    }

    public boolean hasIndex() {
        return index != null;
    }

    /** Absolute start time of the window, or 0 for a VOD timeline. */
    public long getPresentationTimeOffsetMs() {
        return index == null ? startTimeMs : 0;
    }

    public long getSegmentDurationMs() {
        return segmentDurationMs;
    }

    public long getStartMs(final int sequenceNumber) {
        if (sequenceNumber < startSequence) {
            return index == null ? startTimeMs : 0;
        }
        if (index != null) {
            final SabrSegmentIndex.Entry entry = index.getEntry(sequenceNumber - startSequence + 1);
            return entry == null ? -1 : entry.getStartMs();
        }
        return startTimeMs + (sequenceNumber - (long) startSequence) * segmentDurationMs;
    }

    public long getEndMs(final int sequenceNumber) {
        if (index != null) {
            final SabrSegmentIndex.Entry entry = index.getEntry(sequenceNumber - startSequence + 1);
            return entry == null ? -1 : entry.getEndMs();
        }
        return getStartMs(sequenceNumber) + segmentDurationMs;
    }

    public int getSequenceAt(final long timeMs) {
        if (timeMs <= startTimeMs) {
            return startSequence;
        }
        if (index == null) {
            final long offset = timeMs - startTimeMs;
            final long sequence = startSequence + offset / segmentDurationMs;
            return sequence > endSequence ? endSequence + 1 : (int) sequence;
        }
        for (int sequence = startSequence; sequence <= endSequence; sequence++) {
            final long endMs = getEndMs(sequence);
            if (endMs > timeMs) {
                return sequence;
            }
        }
        return endSequence == Integer.MAX_VALUE ? Integer.MAX_VALUE : endSequence + 1;
    }
}
