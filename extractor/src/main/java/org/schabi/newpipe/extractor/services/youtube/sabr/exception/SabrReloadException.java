package org.schabi.newpipe.extractor.services.youtube.sabr.exception;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/**
 * Indicates that the server asked the client to reload the player response (UMP part
 * {@code RELOAD_PLAYER_RESPONSE}) and no reload could be performed: either no
 * {@link org.schabi.newpipe.extractor.services.youtube.sabr.SabrInfoReloader} is installed or the
 * per-session reload budget is exhausted.
 *
 * <p>Reloading is a regular event, not a fatal protocol violation: the current streaming URL and
 * ustreamer config are invalid, and a fresh player response (requested with the carried token)
 * hands out replacements that let the session continue in place.</p>
 */
public final class SabrReloadException extends SabrProtocolException {
    @Nullable
    private final String token;

    public SabrReloadException(@Nonnull final String message, @Nullable final String token) {
        super(message);
        this.token = token;
    }

    /** The token to send back in a reload player request, or {@code null} when none was carried. */
    @Nullable
    public String getToken() {
        return token;
    }
}
