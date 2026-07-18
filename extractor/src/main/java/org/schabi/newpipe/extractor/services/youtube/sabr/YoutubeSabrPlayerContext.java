package org.schabi.newpipe.extractor.services.youtube.sabr;

import javax.annotation.Nonnull;
import java.util.Objects;

/**
 * Route-affine material used together for an Innertube player request.
 * The PO token is bound to the visitor ID for an anonymous session, or to the
 * account data sync ID when the provider uses an authenticated session.
 */
public final class YoutubeSabrPlayerContext {
    @Nonnull
    private final String visitorData;
    @Nonnull
    private final String playerPoToken;

    public YoutubeSabrPlayerContext(@Nonnull final String visitorData,
                                    @Nonnull final String playerPoToken) {
        this.visitorData = Objects.requireNonNull(visitorData, "visitorData");
        this.playerPoToken = Objects.requireNonNull(playerPoToken, "playerPoToken");
        if (this.visitorData.isEmpty()) {
            throw new IllegalArgumentException("visitorData must not be empty");
        }
        if (this.playerPoToken.isEmpty()) {
            throw new IllegalArgumentException("playerPoToken must not be empty");
        }
    }

    @Nonnull
    public String getVisitorData() {
        return visitorData;
    }

    @Nonnull
    public String getPlayerPoToken() {
        return playerPoToken;
    }
}
