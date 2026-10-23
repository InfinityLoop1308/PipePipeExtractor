package org.schabi.newpipe.extractor.services.twitch.api;

import org.schabi.newpipe.extractor.services.twitch.data.api.TwitchIntegrityToken;

import javax.annotation.Nullable;

public final class TwitchApiDataStore {
    public @Nullable TwitchIntegrityToken integrityToken = null;
    public @Nullable String deviceId = null;
    public @Nullable String sessionId = null;
}