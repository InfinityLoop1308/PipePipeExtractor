package org.schabi.newpipe.extractor.services.youtube.sabr;

import javax.annotation.Nonnull;
import java.io.DataOutputStream;
import java.io.IOException;
import java.util.Objects;

/** One protobuf field whose value is supplied by a Host-owned typed source. */
public final class SabrProfileRequestField {
    public enum WireType {
        VARINT(SabrProto.WIRE_VARINT),
        BYTES(SabrProto.WIRE_LENGTH_DELIMITED);

        private final int protobufType;

        WireType(final int protobufType) {
            this.protobufType = protobufType;
        }

        int getProtobufType() {
            return protobufType;
        }
    }

    public enum Source {
        PLAYER_TIME_MS(WireType.VARINT),
        BUFFERED_RANGES(WireType.BYTES),
        PLAYBACK_COOKIE(WireType.BYTES),
        PO_TOKEN(WireType.BYTES),
        SELECTED_FORMATS(WireType.BYTES),
        CLIENT_CONTEXT(WireType.BYTES),
        CLIENT_ABR_STATE(WireType.BYTES),
        USTREAMER_CONFIG(WireType.BYTES),
        PREFERRED_AUDIO_FORMATS(WireType.BYTES),
        PREFERRED_VIDEO_FORMATS(WireType.BYTES);

        @Nonnull private final WireType wireType;

        Source(@Nonnull final WireType wireType) {
            this.wireType = wireType;
        }

        @Nonnull
        WireType getWireType() {
            return wireType;
        }
    }

    private final int field;
    @Nonnull private final WireType wireType;
    @Nonnull private final Source source;
    private final boolean required;

    public SabrProfileRequestField(final int field,
                                   @Nonnull final WireType wireType,
                                   @Nonnull final Source source,
                                   final boolean required) {
        if (field <= 0 || field > SabrCompatibilityProfile.MAX_PROTOBUF_FIELD_NUMBER) {
            throw new IllegalArgumentException("Invalid SABR request field number");
        }
        this.wireType = Objects.requireNonNull(wireType);
        this.source = Objects.requireNonNull(source);
        if (source.getWireType() != wireType) {
            throw new IllegalArgumentException("SABR request source/wire type mismatch");
        }
        this.field = field;
        this.required = required;
    }

    public int getField() {
        return field;
    }

    @Nonnull
    public WireType getWireType() {
        return wireType;
    }

    @Nonnull
    public Source getSource() {
        return source;
    }

    public boolean isRequired() {
        return required;
    }

    void writeCanonical(@Nonnull final DataOutputStream output) throws IOException {
        output.writeInt(field);
        SabrCompatibilityProfile.writeString(output, wireType.name());
        SabrCompatibilityProfile.writeString(output, source.name());
        output.writeBoolean(required);
    }
}
