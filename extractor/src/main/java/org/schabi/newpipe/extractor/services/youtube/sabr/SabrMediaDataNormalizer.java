package org.schabi.newpipe.extractor.services.youtube.sabr;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Locale;

final class SabrMediaDataNormalizer {
    private static final int MP4_HEADER_SIZE = 8;
    private static final int MP4_EXTENDED_HEADER_SIZE = 16;
    private static final long EBML_HEADER_ID = 0x1A45DFA3L;
    private static final long WEBM_SEGMENT_ID = 0x18538067L;
    private static final long WEBM_CLUSTER_ID = 0x1F43B675L;

    private SabrMediaDataNormalizer() {
    }

    @Nullable
    static SabrMediaDataParts split(@Nullable final String mimeType,
                                    @Nonnull final byte[] data) {
        if (mimeType == null) {
            return null;
        }
        final String container = mimeType.split(";", 2)[0].trim().toLowerCase(Locale.ROOT);
        if (container.endsWith("/mp4")) {
            return splitMp4(data);
        }
        if (container.endsWith("/webm")) {
            return splitWebm(data);
        }
        return null;
    }

    @Nullable
    private static SabrMediaDataParts splitMp4(@Nonnull final byte[] data) {
        int offset = 0;
        int initializationEnd = -1;
        while (offset + MP4_HEADER_SIZE <= data.length) {
            final long size32 = readUnsignedInt(data, offset);
            final String type = new String(data, offset + 4, 4, StandardCharsets.US_ASCII);
            final int headerSize = size32 == 1 ? MP4_EXTENDED_HEADER_SIZE : MP4_HEADER_SIZE;
            if (offset + headerSize > data.length) {
                return null;
            }
            final long size = size32 == 0 ? data.length - (long) offset
                    : size32 == 1 ? readUnsignedLong(data, offset + MP4_HEADER_SIZE) : size32;
            if (size < headerSize || size > data.length - (long) offset) {
                return null;
            }
            final int end = offset + (int) size;
            if ("moov".equals(type)) {
                initializationEnd = end;
            } else if ("moof".equals(type) && initializationEnd > 0) {
                return splitAt(data, initializationEnd);
            }
            offset = end;
        }
        return null;
    }

    @Nullable
    private static SabrMediaDataParts splitWebm(@Nonnull final byte[] data) {
        final EbmlElement ebml = readEbmlElement(data, 0);
        if (ebml == null || ebml.id != EBML_HEADER_ID || ebml.size < 0) {
            return null;
        }
        final long segmentOffset = ebml.payloadOffset + ebml.size;
        if (segmentOffset > Integer.MAX_VALUE) {
            return null;
        }
        final EbmlElement segment = readEbmlElement(data, (int) segmentOffset);
        if (segment == null || segment.id != WEBM_SEGMENT_ID) {
            return null;
        }
        int offset = segment.payloadOffset;
        while (offset < data.length) {
            final EbmlElement element = readEbmlElement(data, offset);
            if (element == null) {
                return null;
            }
            if (element.id == WEBM_CLUSTER_ID) {
                return offset <= segment.payloadOffset ? null : splitAt(data, offset);
            }
            if (element.size < 0) {
                return null;
            }
            final long next = element.payloadOffset + element.size;
            if (next <= offset || next > data.length) {
                return null;
            }
            offset = (int) next;
        }
        return null;
    }

    @Nullable
    private static EbmlElement readEbmlElement(@Nonnull final byte[] data, final int offset) {
        final int idLength = vintLength(data, offset, 4);
        if (idLength < 0) {
            return null;
        }
        final long id = readRawValue(data, offset, idLength);
        final int sizeOffset = offset + idLength;
        final int sizeLength = vintLength(data, sizeOffset, 8);
        if (sizeLength < 0) {
            return null;
        }
        final long rawSize = readRawValue(data, sizeOffset, sizeLength);
        final long marker = 1L << (7 * sizeLength);
        final long sizeValue = rawSize & (marker - 1);
        final long size = sizeValue == marker - 1 ? -1 : sizeValue;
        return new EbmlElement(id, size, sizeOffset + sizeLength);
    }

    private static int vintLength(@Nonnull final byte[] data, final int offset,
                                  final int maximum) {
        if (offset < 0 || offset >= data.length) {
            return -1;
        }
        final int first = data[offset] & 0xFF;
        if (first == 0) {
            return -1;
        }
        final int length = Integer.numberOfLeadingZeros(first) - 23;
        return length >= 1 && length <= maximum && offset + length <= data.length ? length : -1;
    }

    private static long readUnsignedInt(@Nonnull final byte[] data, final int offset) {
        return (long) (data[offset] & 0xFF) << 24
                | (long) (data[offset + 1] & 0xFF) << 16
                | (long) (data[offset + 2] & 0xFF) << 8
                | data[offset + 3] & 0xFFL;
    }

    private static long readUnsignedLong(@Nonnull final byte[] data, final int offset) {
        final long value = readRawValue(data, offset, 8);
        return value < 0 ? -1 : value;
    }

    private static long readRawValue(@Nonnull final byte[] data, final int offset,
                                     final int length) {
        if (length < 1 || length > 8 || offset < 0 || offset + length > data.length) {
            return -1;
        }
        long value = 0;
        for (int i = 0; i < length; i++) {
            value = (value << 8) | (data[offset + i] & 0xFFL);
        }
        return value;
    }

    @Nonnull
    private static SabrMediaDataParts splitAt(@Nonnull final byte[] data, final int offset) {
        return new SabrMediaDataParts(Arrays.copyOfRange(data, 0, offset),
                Arrays.copyOfRange(data, offset, data.length));
    }

    private static final class EbmlElement {
        private final long id;
        private final long size;
        private final int payloadOffset;

        private EbmlElement(final long id, final long size, final int payloadOffset) {
            this.id = id;
            this.size = size;
            this.payloadOffset = payloadOffset;
        }
    }
}
