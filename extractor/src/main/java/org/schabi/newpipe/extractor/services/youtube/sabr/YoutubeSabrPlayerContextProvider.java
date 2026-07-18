package org.schabi.newpipe.extractor.services.youtube.sabr;

import org.schabi.newpipe.extractor.exceptions.ExtractionException;

import javax.annotation.Nonnull;
import java.io.IOException;

/**
 * Supplies route-affine player admission material as one atomic value.
 */
@FunctionalInterface
public interface YoutubeSabrPlayerContextProvider {
    /**
     * Returns the current player admission context for a video and client profile.
     */
    @Nonnull
    YoutubeSabrPlayerContext getPlayerContext(@Nonnull String videoId,
                                              @Nonnull YoutubeSabrClientProfile profile)
            throws IOException, ExtractionException;

    /**
     * Returns a fresh context when {@code forceRefresh} is true. The default implementation keeps
     * compatibility with providers that do not cache their result.
     */
    @Nonnull
    default YoutubeSabrPlayerContext getPlayerContext(
            @Nonnull final String videoId,
            @Nonnull final YoutubeSabrClientProfile profile,
            final boolean forceRefresh) throws IOException, ExtractionException {
        return getPlayerContext(videoId, profile);
    }
}
