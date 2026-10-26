package org.schabi.newpipe.extractor.services.twitch.data.api.responses.vod;

import com.grack.nanojson.JsonObject;

import org.jetbrains.annotations.NotNull;
import org.schabi.newpipe.extractor.services.twitch.data.api.TwitchExtensionsData;
import org.schabi.newpipe.extractor.services.twitch.data.api.responses.TwitchBaseResponse;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

public final class TwitchVodResponse extends TwitchBaseResponse<TwitchVodResponseInner[]> {

    public TwitchVodResponse(@Nullable String[] errors, @Nonnull TwitchExtensionsData extensions,
                             @Nonnull JsonObject data) {
        super(errors, extensions, data);
    }

    @Override
    protected TwitchVodResponseInner[] ParseData(@NotNull JsonObject data) {
        final var vodObjects = data.getObject("user").getObject("videos").getArray("edges");
        return vodObjects.stream().map(ob -> {
            final var obj = (JsonObject) ob;
            final var node = obj.getObject("node");
            return new TwitchVodResponseInner(
                    node.getString("previewThumbnailURL"),
                    node.getObject("game").getString("displayName"),
                    node.getString("id"),
                    node.getString("title"),
                    node.getString("publishedAt"),
                    node.getInt("viewCount"),
                    node.getInt("lengthSeconds")
            );
        }).toArray(TwitchVodResponseInner[]::new);
    }
}
