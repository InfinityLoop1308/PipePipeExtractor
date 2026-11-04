package org.schabi.newpipe.extractor.services.youtube.sabr;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/** Initialization and media payloads split out of one self-initializing SABR segment. */
public final class SabrMediaDataParts {
    @Nonnull private final byte[] initializationData;
    @Nullable private final byte[] mediaData;

    SabrMediaDataParts(@Nonnull final byte[] initializationData,
                       @Nullable final byte[] mediaData) {
        this.initializationData = initializationData;
        this.mediaData = mediaData;
    }

    @Nonnull
    public byte[] getInitializationData() {
        return initializationData;
    }

    /** The media payload that followed the initialization box, or {@code null} if none did. */
    @Nullable
    public byte[] getMediaData() {
        return mediaData;
    }
}
