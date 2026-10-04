package org.schabi.newpipe.extractor.services.twitch.data.api.responses;

import com.grack.nanojson.JsonObject;

import org.jetbrains.annotations.NotNull;
import org.schabi.newpipe.extractor.services.twitch.data.api.TwitchExtensionsData;

import java.util.stream.Stream;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

public class TwitchSideNavResponse extends TwitchBaseResponse<TwitchSideNavResponseInner[]> {

    public TwitchSideNavResponse(@Nullable String[] errors, @Nonnull TwitchExtensionsData extensions, @Nonnull JsonObject data) {
        super(errors, extensions, data);
    }

    @Override
    protected TwitchSideNavResponseInner[] ParseData(@NotNull JsonObject data) {
        var edges = data.getObject("sideNav").getObject("sections").getArray("edges");
        return Stream.concat(
                edges.getObject(1).getObject("node").getObject("content").getArray("edges").stream(),
                edges.getObject(0).getObject("node").getObject("content").getArray("edges").stream()
        ).map(ob -> {
            final var obj = (JsonObject) ob;
            final var node = obj.getObject("node");
            final var broadcaster = node.getObject("broadcaster");
            final var broadcastSettings = broadcaster.getObject("broadcastSettings");
            return new TwitchSideNavResponseInner(
                    broadcaster.getString("displayName"),
                    broadcaster.getString("login"),
                    broadcastSettings.getString("title"),
                    node.getInt("viewersCount")
            );
        }).toArray(TwitchSideNavResponseInner[]::new);
    }
}
