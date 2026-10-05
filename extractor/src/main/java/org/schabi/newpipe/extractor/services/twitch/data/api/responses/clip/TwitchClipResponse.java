package org.schabi.newpipe.extractor.services.twitch.data.api.responses.clip;

import com.grack.nanojson.JsonObject;

import org.jetbrains.annotations.NotNull;
import org.schabi.newpipe.extractor.services.twitch.data.api.TwitchExtensionsData;
import org.schabi.newpipe.extractor.services.twitch.data.api.responses.TwitchBaseResponse;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

public final class TwitchClipResponse extends TwitchBaseResponse<TwitchClipResponseInner[]> {

    public TwitchClipResponse(@Nullable String[] errors, @Nonnull TwitchExtensionsData extensions
            , @Nonnull JsonObject data) {
        super(errors, extensions, data);
    }

    @Override
    protected TwitchClipResponseInner[] ParseData(@NotNull JsonObject data) {
        final var clipObjects = data.getObject("user").getObject("clips").getArray("edges");
        return clipObjects.stream().map(ob -> {
            final var obj = (JsonObject) ob;
            final var node = obj.getObject("node");
            return new TwitchClipResponseInner(
                    node.getString("thumbnailURL"),
                    node.getObject("game").getString("name"),
                    node.getString("title"),
                    node.getString("slug"),
                    node.getString("createdAt"),
                    node.getObject("curator").getString("displayName"),
                    node.getInt("viewCount"),
                    node.getInt("durationSeconds")
            );
        }).toArray(TwitchClipResponseInner[]::new);
    }
}
