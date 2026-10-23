package org.schabi.newpipe.extractor.services.twitch.data.api.responses.channel;

import javax.annotation.Nonnull;

public record TwitchChannelResponseInner(@Nonnull String channelName,
                                         @Nonnull String channelId,
                                         @Nonnull String channelBannerUrl,
                                         @Nonnull String streamerAvatarUrl,
                                         @Nonnull String streamerDescription, int followerCount,
                                         boolean isPartner, boolean isLive) {

}
