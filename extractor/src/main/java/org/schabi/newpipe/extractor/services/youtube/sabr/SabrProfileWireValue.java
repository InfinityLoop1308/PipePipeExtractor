package org.schabi.newpipe.extractor.services.youtube.sabr;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/** Bounded protobuf path reader used only by compiled profile mappings. */
final class SabrProfileWireValue {
    @Nullable private final Long varint;
    @Nullable private final byte[] bytes;

    private SabrProfileWireValue(@Nullable final Long varint,
                                 @Nullable final byte[] bytes) {
        this.varint = varint;
        this.bytes = bytes;
    }

    @Nullable
    static SabrProfileWireValue find(@Nonnull final byte[] protobuf,
                                     @Nonnull final SabrProfileResponseMapping mapping)
            throws SabrProtocolException {
        byte[] current = protobuf;
        final int[] path = mapping.getRawPath();
        for (int depth = 0; depth < path.length; depth++) {
            SabrProto.Field selected = null;
            for (final SabrProto.Field field : SabrProto.readFields(current)) {
                if (field.getNumber() == path[depth]) {
                    selected = field;
                }
            }
            if (selected == null) {
                return null;
            }
            if (depth < path.length - 1) {
                if (selected.getWireType() != SabrProto.WIRE_LENGTH_DELIMITED) {
                    throw new SabrProtocolException("SABR profile path crossed a non-message");
                }
                current = selected.getBytes();
                continue;
            }
            return fromField(selected, mapping.getWireType());
        }
        return null;
    }

    @Nonnull
    private static SabrProfileWireValue fromField(
            @Nonnull final SabrProto.Field field,
            @Nonnull final SabrProfileResponseMapping.WireType expected)
            throws SabrProtocolException {
        switch (expected) {
            case VARINT:
            case BOOL:
                if (field.getWireType() != SabrProto.WIRE_VARINT) {
                    throw new SabrProtocolException("SABR profile expected protobuf varint");
                }
                return new SabrProfileWireValue(field.getVarint(), null);
            case BYTES:
            case STRING:
                if (field.getWireType() != SabrProto.WIRE_LENGTH_DELIMITED) {
                    throw new SabrProtocolException("SABR profile expected protobuf bytes");
                }
                return new SabrProfileWireValue(null, field.getBytes());
            default:
                throw new SabrProtocolException("Unsupported SABR profile wire type");
        }
    }

    long asLong() throws SabrProtocolException {
        if (varint == null) {
            throw new SabrProtocolException("SABR profile value is not a varint");
        }
        return varint;
    }

    boolean asBoolean() throws SabrProtocolException {
        return asLong() != 0;
    }

    @Nonnull
    byte[] asBytes() throws SabrProtocolException {
        if (bytes == null) {
            throw new SabrProtocolException("SABR profile value is not bytes");
        }
        return bytes.clone();
    }

    @Nonnull
    String asString() throws SabrProtocolException {
        return SabrProto.asString(asBytes());
    }
}
