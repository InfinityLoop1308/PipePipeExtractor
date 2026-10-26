package org.schabi.newpipe.extractor.services.twitch.api;

import java.io.IOException;

import javax.annotation.Nonnull;

public final class TwitchApiResponseException extends IOException {
    public TwitchApiResponseException(@Nonnull String[] errors) {
        final var errorString = String.join("; ", errors);
        super(errorString);
    }
}
