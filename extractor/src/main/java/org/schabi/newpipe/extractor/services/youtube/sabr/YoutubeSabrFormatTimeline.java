package org.schabi.newpipe.extractor.services.youtube.sabr;

import org.schabi.newpipe.extractor.services.youtube.sabr.exception.SabrProtocolException;
import org.schabi.newpipe.extractor.services.youtube.sabr.media.SabrMp4SegmentIndexParser;
import org.schabi.newpipe.extractor.services.youtube.sabr.media.SabrSegmentIndex;
import org.schabi.newpipe.extractor.services.youtube.sabr.media.SabrWebmSegmentIndexParser;

import javax.annotation.Nonnull;

/** Immutable segment timeline parsed from one format's initialization data. */
public final class YoutubeSabrFormatTimeline {
    @Nonnull private final SabrSegmentIndex index;

    private YoutubeSabrFormatTimeline(@Nonnull final SabrSegmentIndex index) {
        this.index = index;
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
        return new YoutubeSabrFormatTimeline(index);
    }

    public int getEndSequence() { return index.size(); }

    public long getStartMs(final int sequenceNumber) {
        final SabrSegmentIndex.Entry entry = index.getEntry(sequenceNumber);
        return entry == null ? -1 : entry.getStartMs();
    }

    public long getEndMs(final int sequenceNumber) {
        final SabrSegmentIndex.Entry entry = index.getEntry(sequenceNumber);
        return entry == null ? -1 : entry.getEndMs();
    }

    public int getSequenceAt(final long timeMs) {
        if (timeMs <= 0) return 1;
        for (int sequence = 1; sequence <= index.size(); sequence++) {
            final SabrSegmentIndex.Entry entry = index.getEntry(sequence);
            if (entry != null && entry.getEndMs() > timeMs) return sequence;
        }
        return index.size() == Integer.MAX_VALUE ? Integer.MAX_VALUE : index.size() + 1;
    }
}
