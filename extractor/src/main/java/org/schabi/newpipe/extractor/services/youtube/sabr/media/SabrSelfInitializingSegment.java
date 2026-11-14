package org.schabi.newpipe.extractor.services.youtube.sabr.media;

import javax.annotation.Nonnull;
import java.nio.charset.StandardCharsets;

/**
 * Post-live SABR has no separate initialization segment: every media segment starts with its own
 * {@code ftyp}+{@code moov}, followed by the {@code emsg}/{@code moof}/{@code mdat} fragment.
 */
public final class SabrSelfInitializingSegment {
    private SabrSelfInitializingSegment() {
    }

    /** End offset of a leading {@code ftyp}+{@code moov} pair, or -1 when there is none. */
    public static int initializationEnd(@Nonnull final byte[] data) {
        int offset = 0;
        while (offset + 8 <= data.length) {
            final long size = (data[offset] & 0xffL) << 24 | (data[offset + 1] & 0xff) << 16
                    | (data[offset + 2] & 0xff) << 8 | data[offset + 3] & 0xff;
            final String type = new String(data, offset + 4, 4, StandardCharsets.US_ASCII);
            if (size < 8 || size > data.length - offset
                    || (offset == 0 && !"ftyp".equals(type))) {
                return -1;
            }
            offset += (int) size;
            if ("moov".equals(type)) {
                return offset;
            }
        }
        return -1;
    }
}
