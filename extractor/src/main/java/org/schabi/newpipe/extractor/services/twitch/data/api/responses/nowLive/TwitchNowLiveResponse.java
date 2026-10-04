package org.schabi.newpipe.extractor.services.twitch.data.api.responses.nowLive;

import com.grack.nanojson.JsonObject;

import org.jetbrains.annotations.NotNull;
import org.schabi.newpipe.extractor.services.twitch.api.TwitchThumbnailURLGenerator;
import org.schabi.newpipe.extractor.services.twitch.data.api.TwitchExtensionsData;
import org.schabi.newpipe.extractor.services.twitch.data.api.responses.TwitchBaseResponse;

import java.util.Comparator;
import java.util.stream.Collectors;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

public class TwitchNowLiveResponse extends TwitchBaseResponse<TwitchNowLiveResponseEntry[]> {

    public TwitchNowLiveResponse(@Nullable String[] errors, @Nonnull TwitchExtensionsData extensions, @Nonnull JsonObject data) {
        super(errors, extensions, data);
    }

    @Override
    protected TwitchNowLiveResponseEntry[] ParseData(@NotNull JsonObject data) {
        final var streams = data.getObject("streams");
        final var nodes = streams.getArray("edges");
        return nodes.stream().map(ob -> {
                    final var outerNode = (JsonObject) ob;
                    final var node = outerNode.getObject("node");
                    final var broadcaster = node.getObject("broadcaster");
                    final var streamerName = broadcaster.getString("displayName");
                    final var loginName = broadcaster.getString("login");
                    final var title = node.getString("title");
                    final var viewers = node.getInt("viewersCount");
                    final var gameName = node.getObject("game").getString("name");
                    return new TwitchNowLiveResponseEntry(
                            streamerName,
                            loginName,
                            "",
                            title,
                            viewers,
                            TwitchThumbnailURLGenerator.getThumbnailURLForStream(loginName),
                            gameName
                    );
                })
                .sorted(Comparator.comparing(TwitchNowLiveResponseEntry::streamViewers).reversed())
                .toArray(TwitchNowLiveResponseEntry[]::new);
    }
}
