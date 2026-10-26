package org.schabi.newpipe.extractor.services.twitch.data.api.responses.stream;

import com.grack.nanojson.JsonObject;

import org.jetbrains.annotations.NotNull;
import org.schabi.newpipe.extractor.services.twitch.data.api.TwitchExtensionsData;
import org.schabi.newpipe.extractor.services.twitch.data.api.responses.TwitchBaseResponse;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

public final class TwitchStreamResponse extends TwitchBaseResponse<TwitchStreamResponseInner> {

    public TwitchStreamResponse(@Nullable String[] errors,
                                @Nonnull TwitchExtensionsData extensions,
                                @Nonnull JsonObject data) {
        super(errors, extensions, data);
    }

    @Override
    protected TwitchStreamResponseInner ParseData(@NotNull final JsonObject data) {
        final var user = data.getObject("user");
        final var streamerName = user.getString("displayName");
        final var streamerId = user.getString("id");
        final var loginName = user.getString("login");
        final var stream = user.getObject("stream");
        final var streamTitle = stream.getString("title");
        final var viewerCount = stream.getInt("viewersCount");
        final var createdAtString = stream.getString("createdAt");
        return new TwitchStreamResponseInner(
                streamerName,
                loginName,
                streamerId,
                streamTitle,
                viewerCount,
                createdAtString
        );
    }
}
