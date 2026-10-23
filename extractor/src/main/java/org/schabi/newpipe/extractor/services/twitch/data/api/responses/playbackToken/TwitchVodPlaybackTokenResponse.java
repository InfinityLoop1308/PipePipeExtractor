package org.schabi.newpipe.extractor.services.twitch.data.api.responses.playbackToken;

import com.grack.nanojson.JsonObject;

import org.jetbrains.annotations.NotNull;
import org.schabi.newpipe.extractor.services.twitch.data.api.TwitchExtensionsData;
import org.schabi.newpipe.extractor.services.twitch.data.api.responses.TwitchBaseResponse;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

public final class TwitchVodPlaybackTokenResponse extends TwitchBaseResponse<TwitchStreamPlaybackTokenResponseInner> {

    public TwitchVodPlaybackTokenResponse(@Nullable String[] errors,
                                          @Nonnull TwitchExtensionsData extensions,
                                          @Nonnull JsonObject data) {
        super(errors, extensions, data);
    }

    @Override
    protected TwitchStreamPlaybackTokenResponseInner ParseData(@NotNull JsonObject data) {
        final var streamPlaybackAccessToken = data.getObject("videoPlaybackAccessToken");
        return new TwitchStreamPlaybackTokenResponseInner(
                streamPlaybackAccessToken.getString("signature"),
                streamPlaybackAccessToken.getString("value")
        );
    }
}
