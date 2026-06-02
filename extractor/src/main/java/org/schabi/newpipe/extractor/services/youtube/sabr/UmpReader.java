package org.schabi.newpipe.extractor.services.youtube.sabr;

import javax.annotation.Nonnull;
import java.util.ArrayList;
import java.util.List;

/**
 * Reader for YouTube's UMP envelope. UMP uses its own compact integer format, not protobuf varints.
 */
public final class UmpReader {
    private UmpReader() {
    }

    @Nonnull
    public static List<UmpPart> readAll(@Nonnull final byte[] data) throws SabrProtocolException {
        final Cursor cursor = new Cursor(data);
        final List<UmpPart> parts = new ArrayList<>();
        while (!cursor.isDone()) {
            final int type = cursor.readUmpInt();
            final int size = cursor.readUmpInt();
            if (type < 0 || size < 0) {
                throw new SabrProtocolException("Invalid UMP part header");
            }
            parts.add(new UmpPart(type, size, cursor.readBytes(size)));
        }
        return parts;
    }

    private static final class Cursor {
        private final byte[] data;
        private int offset;

        private Cursor(@Nonnull final byte[] data) {
            this.data = data;
        }

        boolean isDone() {
            return offset >= data.length;
        }

        int readUmpInt() throws SabrProtocolException {
            final int first = readUnsignedByte();
            if (first < 128) {
                return first;
            }
            if (first < 192) {
                return (first & 0x3f) + 64 * readUnsignedByte();
            }
            if (first < 224) {
                return (first & 0x1f) + 32 * (readUnsignedByte()
                        + 256 * readUnsignedByte());
            }
            if (first < 240) {
                return (first & 0x0f) + 16 * (readUnsignedByte()
                        + 256 * (readUnsignedByte() + 256 * readUnsignedByte()));
            }
            return readUnsignedByte()
                    + 256 * (readUnsignedByte()
                    + 256 * (readUnsignedByte() + 256 * readUnsignedByte()));
        }

        @Nonnull
        byte[] readBytes(final int length) throws SabrProtocolException {
            if (length < 0 || offset + length > data.length) {
                throw new SabrProtocolException("Unexpected EOF while reading UMP part data");
            }
            final byte[] result = new byte[length];
            System.arraycopy(data, offset, result, 0, length);
            offset += length;
            return result;
        }

        private int readUnsignedByte() throws SabrProtocolException {
            if (offset >= data.length) {
                throw new SabrProtocolException("Unexpected EOF in UMP integer");
            }
            return data[offset++] & 0xff;
        }
    }
}
