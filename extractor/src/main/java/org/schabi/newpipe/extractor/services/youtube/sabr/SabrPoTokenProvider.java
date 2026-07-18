package org.schabi.newpipe.extractor.services.youtube.sabr;

import org.schabi.newpipe.extractor.exceptions.ExtractionException;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.io.IOException;

/**
 * Supplies raw video ID-bound PO token bytes for SABR media requests.
 * This token belongs in {@code StreamerContext}; it must not be used as the
 * visitor/session-bound PO token on the Innertube player request.
 */
@FunctionalInterface
public interface SabrPoTokenProvider {
    /**
     * Returns raw content PO token bytes for the current video, or {@code null} if unavailable.
     */
    @Nullable
    byte[] getPoToken(@Nonnull YoutubeSabrInfo info,
                      @Nonnull YoutubeSabrStreamState streamState)
            throws IOException, ExtractionException;

    /**
     * Like {@link #getPoToken}, but {@code forceRefresh} drops the cached token and mints a fresh
     * one. For when the server rejects a token that died mid-playback. Default impl ignores the flag.
     */
    @Nullable
    default byte[] getPoToken(@Nonnull final YoutubeSabrInfo info,
                              @Nonnull final YoutubeSabrStreamState streamState,
                              final boolean forceRefresh)
            throws IOException, ExtractionException {
        return getPoToken(info, streamState);
    }
}
