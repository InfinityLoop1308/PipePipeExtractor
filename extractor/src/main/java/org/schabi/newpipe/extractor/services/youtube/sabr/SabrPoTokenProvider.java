package org.schabi.newpipe.extractor.services.youtube.sabr;

import org.schabi.newpipe.extractor.exceptions.ExtractionException;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.io.IOException;

/**
 * Supplies raw WEB PO token bytes for experimental SABR requests.
 */
@FunctionalInterface
public interface SabrPoTokenProvider {
    /**
     * Returns raw PO token bytes for the current SABR session, or {@code null} if unavailable.
     */
    @Nullable
    byte[] getPoToken(@Nonnull YoutubeSabrInfo info,
                      @Nonnull YoutubeSabrStreamState streamState)
            throws IOException, ExtractionException;

}
