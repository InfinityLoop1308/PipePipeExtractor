package org.schabi.newpipe.extractor.services.youtube.sabr;

import org.schabi.newpipe.extractor.services.youtube.sabr.generated.SabrMediaHeader;

import javax.annotation.Nonnull;

public final class SabrSegmentRequest {
    @Nonnull
    private final YoutubeSabrInfo.Format format;
    private final boolean initializationSegment;
    private final int sequenceNumber;

    private SabrSegmentRequest(@Nonnull final YoutubeSabrInfo.Format format,
                               final boolean initializationSegment,
                               final int sequenceNumber) {
        this.format = format;
        this.initializationSegment = initializationSegment;
        this.sequenceNumber = sequenceNumber;
    }

    @Nonnull
    public static SabrSegmentRequest initialization(@Nonnull final YoutubeSabrInfo.Format format) {
        return new SabrSegmentRequest(format, true, -1);
    }

    @Nonnull
    public static SabrSegmentRequest media(@Nonnull final YoutubeSabrInfo.Format format,
                                           final int sequenceNumber) {
        if (sequenceNumber <= 0) {
            throw new IllegalArgumentException("SABR media sequence number must be positive");
        }
        return new SabrSegmentRequest(format, false, sequenceNumber);
    }

    boolean matches(@Nonnull final SabrMediaHeader header) {
        if (header.getItag() != format.getItag()) {
            return false;
        }
        if (initializationSegment) {
            return header.isInitSegment();
        }
        return !header.isInitSegment() && header.getSequenceNumber() == sequenceNumber;
    }

    @Nonnull
    public YoutubeSabrInfo.Format getFormat() {
        return format;
    }

    public boolean isInitializationSegment() {
        return initializationSegment;
    }

    public int getSequenceNumber() {
        return sequenceNumber;
    }
}
