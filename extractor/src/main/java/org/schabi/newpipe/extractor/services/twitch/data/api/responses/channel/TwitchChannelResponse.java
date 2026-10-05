package org.schabi.newpipe.extractor.services.twitch.data.api.responses.channel;

import com.grack.nanojson.JsonObject;

import org.jetbrains.annotations.NotNull;
import org.schabi.newpipe.extractor.services.twitch.data.api.TwitchExtensionsData;
import org.schabi.newpipe.extractor.services.twitch.data.api.responses.TwitchBaseResponse;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

public final class TwitchChannelResponse extends TwitchBaseResponse<TwitchChannelResponseInner> {

    public TwitchChannelResponse(@Nullable String[] errors,
                                 @Nonnull TwitchExtensionsData extensions,
                                 @Nonnull JsonObject data) {
        super(errors, extensions, data);
    }

    @Override
    public TwitchChannelResponseInner ParseData(@NotNull JsonObject data) {
        final var homeOfflineCarousel = data.getObject("synthetic-0");
        final var channelAvatar = data.getObject("synthetic-1");
        final var channelShell = data.getObject("synthetic-2");
        return new TwitchChannelResponseInner(
                homeOfflineCarousel.getObject("user").getString("displayName"),
                homeOfflineCarousel.getObject("user").getString("id"),
                channelShell.getObject("userOrError").getString("bannerImageURL"),
                channelShell.getObject("userOrError").getString("profileImageURL"),
                homeOfflineCarousel.getObject("user").getString("description"),
                channelAvatar.getObject("user").getObject("followers").getInt("totalCount"),
                homeOfflineCarousel.getObject("user").getObject("roles").getBoolean("isPartner"),
                channelShell.getObject("userOrError").get("stream") != null
        );
    }
}
