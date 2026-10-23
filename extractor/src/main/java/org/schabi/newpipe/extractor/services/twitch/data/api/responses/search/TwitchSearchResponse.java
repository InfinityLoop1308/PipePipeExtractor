package org.schabi.newpipe.extractor.services.twitch.data.api.responses.search;

import com.grack.nanojson.JsonArray;
import com.grack.nanojson.JsonObject;

import org.jetbrains.annotations.NotNull;
import org.schabi.newpipe.extractor.services.twitch.data.api.TwitchExtensionsData;
import org.schabi.newpipe.extractor.services.twitch.data.api.responses.TwitchBaseResponse;
import org.schabi.newpipe.extractor.services.twitch.data.api.responses.search.types.TwitchSearchChannelResponseEntry;
import org.schabi.newpipe.extractor.services.twitch.data.api.responses.search.types.TwitchSearchGameResponseEntry;
import org.schabi.newpipe.extractor.services.twitch.data.api.responses.search.types.TwitchSearchStreamResponseEntry;
import org.schabi.newpipe.extractor.services.twitch.data.api.responses.search.types.TwitchSearchVodResponseEntry;

import java.util.LinkedList;
import java.util.List;
import java.util.Optional;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

public final class TwitchSearchResponse extends TwitchBaseResponse<List<TwitchSearchBaseResponseEntry>> {

    public TwitchSearchResponse(@Nullable String[] errors,
                                @Nonnull TwitchExtensionsData extensions,
                                @Nonnull JsonObject data) {
        super(errors, extensions, data);
    }

    private static @Nonnull TwitchSearchChannelResponseEntry parseAsChannelEntry(JsonObject node) {
        final var item = node.getObject("item");
        return new TwitchSearchChannelResponseEntry(
                item.getString("displayName"),
                item.getString("login"),
                item.getString("id"),
                item.getString("description"),
                item.getString("profileImageURL"),
                item.getObject("roles").getBoolean("isPartner"),
                item.getObject("followers").getInt("totalCount"));
    }

    private static @Nonnull TwitchSearchStreamResponseEntry parseAsTaggedStreamEntry(JsonObject node) {
        final var item = node.getObject("item");
        final var stream = item.getObject("stream");
        return new TwitchSearchStreamResponseEntry(
                item.getString("displayName"),
                item.getString("login"),
                item.getString("id"),
                item.getString("description"),
                item.getObject("broadcastSettings").getString("title"),
                stream.getInt("viewersCount"),
                stream.getString("previewImageURL"),
                stream.getObject("game").getString("name"),
                stream.getObject("game").getString("id"),
                item.getString("profileImageURL")
        );
    }

    private static @Nonnull TwitchSearchStreamResponseEntry parseAsStreamEntry(JsonObject node) {
        final var item = node.getObject("item");
        final var stream = item.getObject("stream");
        return new TwitchSearchStreamResponseEntry(
                item.getString("displayName"),
                item.getString("login"),
                item.getString("id"),
                item.getString("description"),
                item.getObject("broadcastSettings").getString("title"),
                stream.getInt("viewersCount"),
                stream.getString("previewImageURL"),
                stream.getObject("game").getString("name"),
                stream.getObject("game").getString("id"),
                item.getString("profileImageURL")
        );
    }

    private static @Nonnull TwitchSearchVodResponseEntry parseAsVodEntry(JsonObject node) {
        final var item = node.getObject("item");
        final var owner = item.getObject("owner");
        return new TwitchSearchVodResponseEntry(
                item.getString("title"),
                item.getString("id"),
                owner.getString("displayName"),
                owner.getString("login"),
                owner.getString("id"),
                item.getInt("viewCount"),
                item.getInt("lengthSeconds"),
                item.getString("previewThumbnailURL"),
                item.getString("createdAt")
        );
    }

    private static @Nonnull TwitchSearchGameResponseEntry parseAsGameEntry(JsonObject node) {
        final var item = node.getObject("item");
        return new TwitchSearchGameResponseEntry(
                item.getString("displayName"),
                item.getString("slug"),
                item.getString("id"),
                item.getString("boxArtURL"));
    }

    @Override
    protected List<TwitchSearchBaseResponseEntry> ParseData(@NotNull JsonObject o) {
        final var searchFor = o.getObject("searchFor");
        final Optional<JsonArray> channelsWithTags = searchFor.has("channelsWithTag")
                ? Optional.of(searchFor.getObject("channelsWithTag").getArray("edges"))
                : Optional.empty();
        final Optional<JsonArray> channels = searchFor.has("channels")
                ? Optional.of(searchFor.getObject("channels").getArray("edges"))
                : Optional.empty();
        final Optional<JsonArray> games = searchFor.has("games")
                ? Optional.of(searchFor.getObject("games").getArray("edges"))
                : Optional.empty();
        final Optional<JsonArray> vods = searchFor.has("videos")
                ? Optional.of(searchFor.getObject("videos").getArray("edges"))
                : Optional.empty();
        final var masterList = new LinkedList<TwitchSearchBaseResponseEntry>();
        channelsWithTags.ifPresent(objects -> masterList.addAll(objects.stream().map(ob -> parseAsTaggedStreamEntry((JsonObject) ob)).toList()));
        channels.ifPresent(objects -> masterList.addAll(objects.stream().map(ob -> {
            final var obj = (JsonObject) ob;
            final var isLive = obj.getObject("item").getObject("stream").has("viewersCount");
            return isLive ? parseAsStreamEntry(obj) : parseAsChannelEntry(obj);
        }).toList()));
        games.ifPresent(objects ->
                masterList.addAll(objects.stream().map(ob -> TwitchSearchResponse.parseAsGameEntry((JsonObject) ob)).toList()));
        vods.ifPresent(objects ->
                masterList.addAll(objects.stream().map(ob -> TwitchSearchResponse.parseAsVodEntry((JsonObject) ob)).toList()));
        return masterList;
    }
}
