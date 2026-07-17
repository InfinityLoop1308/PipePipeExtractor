package org.schabi.newpipe.extractor.services.youtube.sabr;

import javax.annotation.Nonnull;

final class SabrMediaDataParts {
    @Nonnull
    private final byte[] initializationData;
    @Nonnull
    private final byte[] mediaData;

    SabrMediaDataParts(@Nonnull final byte[] initializationData,
                       @Nonnull final byte[] mediaData) {
        this.initializationData = initializationData;
        this.mediaData = mediaData;
    }

    @Nonnull
    byte[] getInitializationData() {
        return initializationData;
    }

    @Nonnull
    byte[] getMediaData() {
        return mediaData;
    }
}
