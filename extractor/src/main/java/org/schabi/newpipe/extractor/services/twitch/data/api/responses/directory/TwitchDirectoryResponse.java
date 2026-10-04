package org.schabi.newpipe.extractor.services.twitch.data.api.responses.directory;

import com.grack.nanojson.JsonObject;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.schabi.newpipe.extractor.services.twitch.data.api.TwitchExtensionsData;
import org.schabi.newpipe.extractor.services.twitch.data.api.responses.TwitchBaseResponse;

public class TwitchDirectoryResponse extends TwitchBaseResponse<TwitchDirectoryResponseInner> {
    public TwitchDirectoryResponse(@Nullable String[] errors, @NotNull TwitchExtensionsData extensions, @NotNull JsonObject data) {
        super(errors, extensions, data);
    }

    @Override
    protected TwitchDirectoryResponseInner ParseData(final @NotNull JsonObject data) {
        final var game = data.getObject("game");
        return new TwitchDirectoryResponseInner(
                game.getString("displayName"),
                game.getString("id"),
                game.getObject("streams").getArray("edges").stream().map(obj -> {
                    final var innerObj = (JsonObject)obj;
                    final var node = innerObj.getObject("node");
                    final var broadcaster = node.getObject("broadcaster");
                    return new TwitchDirectoryResponseEntry(
                            node.getString("title"),
                            node.getString("id"),
                            broadcaster.getString("displayName"),
                            broadcaster.getString("login"),
                            broadcaster.getString("id"),
                            broadcaster.getString("profileImageURL"),
                            broadcaster.getObject("roles").getBoolean("isPartner"),
                            node.getInt("viewersCount")
                    );
                }).toList()
        );
    }
}
