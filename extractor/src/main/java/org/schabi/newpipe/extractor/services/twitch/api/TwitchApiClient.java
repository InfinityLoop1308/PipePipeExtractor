package org.schabi.newpipe.extractor.services.twitch.api;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

public class TwitchApiClient {

    private final UnauthorizedTwitchApiClient unauthorized = new UnauthorizedTwitchApiClient();
    @Nonnull
    private final TwitchApiSettings apiSettings;
    private final AuthorizedTwitchApiClient authorized;

    public TwitchApiClient(@Nonnull final TwitchApiSettings apiSettings,
                           @Nullable final String helixApiToken) {
        this.apiSettings = apiSettings;
        authorized = new AuthorizedTwitchApiClient(helixApiToken);
    }

    public UnauthorizedTwitchApiClient getUnauthorized() {
        return unauthorized;
    }

    public AuthorizedTwitchApiClient getAuthorized() {
        return authorized;
    }

    @Nonnull
    public TwitchApiSettings getApiSettings() {
        return apiSettings;
    }
}
