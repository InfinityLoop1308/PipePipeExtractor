package org.schabi.newpipe.extractor.services.youtube.sabr;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.List;

/** Overlays profile-declared protobuf paths on the stable native media-header model. */
final class SabrProfileMediaHeaderMapper {
    @Nonnull private final List<SabrProfileResponseMapping> mappings;

    SabrProfileMediaHeaderMapper(@Nonnull final List<SabrProfileResponseMapping> mappings) {
        this.mappings = mappings;
    }

    @Nonnull
    SabrMediaHeader decode(@Nonnull final byte[] payload) throws SabrProtocolException {
        final SabrMediaHeader builtin = SabrMediaHeader.decodeLenient(payload);
        final Values values = new Values(builtin);
        for (final SabrProfileResponseMapping mapping : mappings) {
            if (!mapping.getTarget().name().startsWith("MEDIA_HEADER_")) {
                continue;
            }
            final SabrProfileWireValue value = SabrProfileWireValue.find(payload, mapping);
            if (value == null) {
                if (mapping.isRequired()) {
                    throw new SabrProtocolException("Required SABR media-header mapping is absent: "
                            + mapping);
                }
            } else {
                values.apply(mapping.getTarget(), value);
            }
        }
        return values.build();
    }

    private static final class Values {
        private int headerId;
        @Nullable private String videoId;
        private int itag;
        private long lastModified;
        @Nullable private String xtags;
        private long startRange;
        private int compression;
        private boolean init;
        private int sequence;
        private long bitrateBps;
        private long startMs;
        private long durationMs;
        private long contentLength;
        private long timeRangeStart;
        private long timeRangeDuration;
        private int timeRangeTimescale;
        private long sequenceLastModified;

        Values(@Nonnull final SabrMediaHeader header) {
            headerId = header.getHeaderId();
            videoId = header.getVideoId();
            itag = header.getItag();
            lastModified = header.getLastModified();
            xtags = header.getXtags();
            startRange = header.getStartRange();
            compression = header.getCompressionAlgorithm();
            init = header.isInitSegment();
            sequence = header.getSequenceNumber();
            bitrateBps = header.getBitrateBps();
            startMs = header.getStartMs();
            durationMs = header.getDurationMs();
            contentLength = header.getContentLength();
            timeRangeStart = header.getTimeRangeStartTicks();
            timeRangeDuration = header.getTimeRangeDurationTicks();
            timeRangeTimescale = header.getTimeRangeTimescale();
            sequenceLastModified = header.getSequenceLastModified();
        }

        void apply(@Nonnull final SabrProfileResponseMapping.Target target,
                   @Nonnull final SabrProfileWireValue value) throws SabrProtocolException {
            switch (target) {
                case MEDIA_HEADER_ID: headerId = integer(value, target); break;
                case MEDIA_HEADER_VIDEO_ID: videoId = value.asString(); break;
                case MEDIA_HEADER_ITAG: itag = integer(value, target); break;
                case MEDIA_HEADER_LAST_MODIFIED: lastModified = value.asLong(); break;
                case MEDIA_HEADER_XTAGS: xtags = value.asString(); break;
                case MEDIA_HEADER_START_RANGE: startRange = value.asLong(); break;
                case MEDIA_HEADER_COMPRESSION: compression = integer(value, target); break;
                case MEDIA_HEADER_IS_INIT: init = value.asBoolean(); break;
                case MEDIA_HEADER_SEQUENCE: sequence = integer(value, target); break;
                case MEDIA_HEADER_BITRATE_BPS: bitrateBps = value.asLong(); break;
                case MEDIA_HEADER_START_MS: startMs = value.asLong(); break;
                case MEDIA_HEADER_DURATION_MS: durationMs = value.asLong(); break;
                case MEDIA_HEADER_CONTENT_LENGTH: contentLength = value.asLong(); break;
                case MEDIA_HEADER_TIME_RANGE_START: timeRangeStart = value.asLong(); break;
                case MEDIA_HEADER_TIME_RANGE_DURATION: timeRangeDuration = value.asLong(); break;
                case MEDIA_HEADER_TIME_RANGE_TIMESCALE:
                    timeRangeTimescale = integer(value, target);
                    break;
                case MEDIA_HEADER_SEQUENCE_LAST_MODIFIED:
                    sequenceLastModified = value.asLong();
                    break;
                default:
                    break;
            }
        }

        @Nonnull
        SabrMediaHeader build() {
            if (timeRangeTimescale > 0) {
                if (startMs < 0 && timeRangeStart >= 0) {
                    startMs = timeRangeStart * 1000L / timeRangeTimescale;
                }
                if (durationMs < 0 && timeRangeDuration >= 0) {
                    durationMs = timeRangeDuration * 1000L / timeRangeTimescale;
                }
            }
            return SabrMediaHeader.normalized(headerId, videoId, itag, lastModified, xtags,
                    startRange, compression, init, sequence, bitrateBps, startMs, durationMs,
                    contentLength, timeRangeStart, timeRangeDuration, timeRangeTimescale,
                    sequenceLastModified);
        }

        private static int integer(@Nonnull final SabrProfileWireValue value,
                                   @Nonnull final SabrProfileResponseMapping.Target target)
                throws SabrProtocolException {
            final long number = value.asLong();
            if (number < Integer.MIN_VALUE || number > Integer.MAX_VALUE) {
                throw new SabrProtocolException("SABR media-header integer overflow: " + target);
            }
            return (int) number;
        }
    }
}
