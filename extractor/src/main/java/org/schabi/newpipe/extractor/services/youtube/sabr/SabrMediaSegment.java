package org.schabi.newpipe.extractor.services.youtube.sabr;

import javax.annotation.Nonnull;

public final class SabrMediaSegment {
    @Nonnull
    private final SabrMediaHeader header;
    @Nonnull
    private final byte[] data;

    SabrMediaSegment(@Nonnull final SabrMediaHeader header, @Nonnull final byte[] data) {
        this.header = header;
        this.data = data.clone();
    }

    @Nonnull
    public SabrMediaHeader getHeader() {
        return header;
    }

    @Nonnull
    public byte[] getData() {
        return data.clone();
    }

    public int getLength() {
        return data.length;
    }
}
