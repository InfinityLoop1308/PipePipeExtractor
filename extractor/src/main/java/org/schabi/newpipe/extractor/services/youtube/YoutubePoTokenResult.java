package org.schabi.newpipe.extractor.services.youtube;

import javax.annotation.Nonnull;
import java.util.Objects;

/** Visitor identity and selected player PO token for one MWEB player request. */
public final class YoutubePoTokenResult {
    @Nonnull
    private final String visitorData;
    @Nonnull
    private final String clientVersion;
    @Nonnull
    private final String playerPoToken;

    public YoutubePoTokenResult(@Nonnull final String visitorData,
                                @Nonnull final String clientVersion,
                                @Nonnull final String playerPoToken) {
        this.visitorData = Objects.requireNonNull(visitorData);
        this.clientVersion = Objects.requireNonNull(clientVersion);
        this.playerPoToken = Objects.requireNonNull(playerPoToken);
    }

    @Nonnull
    public String getVisitorData() {
        return visitorData;
    }

    @Nonnull
    public String getClientVersion() {
        return clientVersion;
    }

    @Nonnull
    public String getPlayerPoToken() {
        return playerPoToken;
    }
}
