package org.schabi.newpipe.extractor.services.youtube.sabr;

import org.schabi.newpipe.extractor.exceptions.ExtractionException;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.io.IOException;

/**
 * Fetches a fresh {@link YoutubeSabrInfo} when the server asks for a player reload.
 *
 * <p>Installed by the client, because re-extracting a player response needs the surrounding
 * extraction stack (player request, PO token, signature timestamp). The session calls it on a
 * {@code RELOAD_PLAYER_RESPONSE} control part and hot-swaps the returned info, so the stream
 * continues with the new streaming URL, ustreamer config and PO token without restarting from 0.
 */
public interface SabrInfoReloader {
    /**
     * @param videoId     the video whose player response has to be reloaded
     * @param reloadToken the token carried by the reload response, or an empty string when the
     *                    server did not provide one
     * @return a fresh info carrying the new streaming URL and ustreamer config, or {@code null}
     *     when the reload could not be performed
     */
    @Nullable
    YoutubeSabrInfo reload(@Nonnull String videoId, @Nonnull String reloadToken)
            throws IOException, ExtractionException;
}
