package org.schabi.newpipe.extractor.services.youtube.sabr;

import javax.annotation.Nonnull;
import java.io.DataOutputStream;
import java.io.IOException;
import java.util.Arrays;
import java.util.Objects;

/** Compiled path from one UMP protobuf part to bounded normalized SABR state. */
public final class SabrProfileResponseMapping {
    private static final int MAX_PATH_DEPTH = 8;

    public enum WireType {
        VARINT,
        BYTES,
        STRING,
        BOOL
    }

    public enum Target {
        NEXT_REQUEST_TARGET_AUDIO_READAHEAD_MS("NEXT_REQUEST.TARGET_AUDIO_READAHEAD_MS"),
        NEXT_REQUEST_TARGET_VIDEO_READAHEAD_MS("NEXT_REQUEST.TARGET_VIDEO_READAHEAD_MS"),
        NEXT_REQUEST_MAX_TIME_SINCE_LAST_REQUEST_MS(
                "NEXT_REQUEST.MAX_TIME_SINCE_LAST_REQUEST_MS"),
        NEXT_REQUEST_BACKOFF_MS("NEXT_REQUEST.BACKOFF_MS"),
        NEXT_REQUEST_MIN_AUDIO_READAHEAD_MS("NEXT_REQUEST.MIN_AUDIO_READAHEAD_MS"),
        NEXT_REQUEST_MIN_VIDEO_READAHEAD_MS("NEXT_REQUEST.MIN_VIDEO_READAHEAD_MS"),
        NEXT_REQUEST_PLAYBACK_COOKIE("NEXT_REQUEST.PLAYBACK_COOKIE"),
        NEXT_REQUEST_VIDEO_ID("NEXT_REQUEST.VIDEO_ID"),
        REDIRECT_URL("REDIRECT.URL"),
        ERROR_TYPE("ERROR.TYPE"),
        ERROR_CODE("ERROR.CODE"),
        RELOAD_REQUESTED("RELOAD.REQUESTED"),
        PROTECTION_STATUS("PROTECTION.STATUS"),
        MEDIA_HEADER_ID("MEDIA_HEADER.ID"),
        MEDIA_HEADER_VIDEO_ID("MEDIA_HEADER.VIDEO_ID"),
        MEDIA_HEADER_ITAG("MEDIA_HEADER.ITAG"),
        MEDIA_HEADER_LAST_MODIFIED("MEDIA_HEADER.LAST_MODIFIED"),
        MEDIA_HEADER_XTAGS("MEDIA_HEADER.XTAGS"),
        MEDIA_HEADER_START_RANGE("MEDIA_HEADER.START_RANGE"),
        MEDIA_HEADER_COMPRESSION("MEDIA_HEADER.COMPRESSION"),
        MEDIA_HEADER_IS_INIT("MEDIA_HEADER.IS_INIT"),
        MEDIA_HEADER_SEQUENCE("MEDIA_HEADER.SEQUENCE"),
        MEDIA_HEADER_BITRATE_BPS("MEDIA_HEADER.BITRATE_BPS"),
        MEDIA_HEADER_START_MS("MEDIA_HEADER.START_MS"),
        MEDIA_HEADER_DURATION_MS("MEDIA_HEADER.DURATION_MS"),
        MEDIA_HEADER_CONTENT_LENGTH("MEDIA_HEADER.CONTENT_LENGTH"),
        MEDIA_HEADER_TIME_RANGE_START("MEDIA_HEADER.TIME_RANGE_START"),
        MEDIA_HEADER_TIME_RANGE_DURATION("MEDIA_HEADER.TIME_RANGE_DURATION"),
        MEDIA_HEADER_TIME_RANGE_TIMESCALE("MEDIA_HEADER.TIME_RANGE_TIMESCALE"),
        MEDIA_HEADER_SEQUENCE_LAST_MODIFIED("MEDIA_HEADER.SEQUENCE_LAST_MODIFIED");

        @Nonnull private final String id;

        Target(@Nonnull final String id) {
            this.id = id;
        }

        @Nonnull
        public String getId() {
            return id;
        }

        @Nonnull
        static Target fromId(@Nonnull final String id) {
            for (final Target target : values()) {
                if (target.id.equals(id)) {
                    return target;
                }
            }
            throw new IllegalArgumentException("Unsupported SABR response target: " + id);
        }
    }

    private final int partType;
    @Nonnull private final Target target;
    @Nonnull private final int[] path;
    @Nonnull private final WireType wireType;
    private final boolean required;

    public SabrProfileResponseMapping(final int partType,
                                      @Nonnull final Target target,
                                      @Nonnull final int[] path,
                                      @Nonnull final WireType wireType,
                                      final boolean required) {
        if (partType < 0 || partType > SabrCompatibilityProfile.MAX_UMP_PART_TYPE
                || path.length == 0 || path.length > MAX_PATH_DEPTH) {
            throw new IllegalArgumentException("Invalid SABR response mapping");
        }
        for (final int field : path) {
            if (field <= 0 || field > SabrCompatibilityProfile.MAX_PROTOBUF_FIELD_NUMBER) {
                throw new IllegalArgumentException("Invalid SABR response field path");
            }
        }
        this.partType = partType;
        this.target = Objects.requireNonNull(target);
        this.path = path.clone();
        this.wireType = Objects.requireNonNull(wireType);
        if (!accepts(target, wireType)) {
            throw new IllegalArgumentException("SABR response target/wire type mismatch");
        }
        this.required = required;
    }

    private static boolean accepts(@Nonnull final Target target,
                                   @Nonnull final WireType wireType) {
        switch (target) {
            case NEXT_REQUEST_PLAYBACK_COOKIE:
                return wireType == WireType.BYTES;
            case NEXT_REQUEST_VIDEO_ID:
            case REDIRECT_URL:
            case ERROR_TYPE:
            case MEDIA_HEADER_VIDEO_ID:
            case MEDIA_HEADER_XTAGS:
                return wireType == WireType.STRING;
            case RELOAD_REQUESTED:
            case MEDIA_HEADER_IS_INIT:
                return wireType == WireType.BOOL;
            default:
                return wireType == WireType.VARINT;
        }
    }

    public int getPartType() {
        return partType;
    }

    @Nonnull
    public Target getTarget() {
        return target;
    }

    @Nonnull
    public int[] getPath() {
        return path.clone();
    }

    @Nonnull
    int[] getRawPath() {
        return path;
    }

    @Nonnull
    public WireType getWireType() {
        return wireType;
    }

    public boolean isRequired() {
        return required;
    }

    void writeCanonical(@Nonnull final DataOutputStream output) throws IOException {
        output.writeInt(partType);
        SabrCompatibilityProfile.writeString(output, target.getId());
        output.writeByte(path.length);
        for (final int field : path) {
            output.writeInt(field);
        }
        SabrCompatibilityProfile.writeString(output, wireType.name());
        output.writeBoolean(required);
    }

    @Override
    public String toString() {
        return partType + ":" + target.getId() + Arrays.toString(path);
    }
}
