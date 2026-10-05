package org.schabi.newpipe.extractor.services.twitch.data.api.responses.clip;

import com.grack.nanojson.JsonObject;

import org.jetbrains.annotations.NotNull;
import org.schabi.newpipe.extractor.services.twitch.data.api.TwitchExtensionsData;
import org.schabi.newpipe.extractor.services.twitch.data.api.responses.TwitchBaseResponse;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

public final class TwitchVideoPlayerMediaSessionManagerResponse extends TwitchBaseResponse<TwitchVideoPlayerMediaSessionManagerResponseInner> {
    public TwitchVideoPlayerMediaSessionManagerResponse(@Nullable String[] errors,
                                                        @Nonnull TwitchExtensionsData extensions,
                                                        @Nonnull JsonObject data) {
        super(errors, extensions, data);
    }

    @Override
    protected TwitchVideoPlayerMediaSessionManagerResponseInner ParseData(@NotNull JsonObject data) {
        if (data.has("video"))
            return parseVodData(data.getObject("video"));
        return parseClipData(data.getObject("clip"));
    }

    private TwitchVideoPlayerMediaSessionManagerResponseInner parseClipData(final @Nonnull JsonObject clipObject) {
        final var broadcaster = clipObject.getObject("broadcaster");
        return new TwitchVideoPlayerMediaSessionManagerResponseInner(
                clipObject.getString("title"),
                broadcaster.getString("displayName"),
                broadcaster.getString("login"),
                broadcaster.getString("id"),
                broadcaster.getString("profileImageURL")
        );
    }

    private TwitchVideoPlayerMediaSessionManagerResponseInner parseVodData(final @Nonnull JsonObject videoObject) {
        final var owner = videoObject.getObject("owner");
        return new TwitchVideoPlayerMediaSessionManagerResponseInner(
                videoObject.getString("title"),
                owner.getString("displayName"),
                owner.getString("login"),
                owner.getString("id"),
                owner.getString("profileImageURL")
        );
    }
}
