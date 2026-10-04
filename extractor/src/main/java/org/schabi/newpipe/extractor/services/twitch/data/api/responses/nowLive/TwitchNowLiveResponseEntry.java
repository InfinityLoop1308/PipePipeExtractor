package org.schabi.newpipe.extractor.services.twitch.data.api.responses.nowLive;

import javax.annotation.Nonnull;

public record TwitchNowLiveResponseEntry(@Nonnull String streamerName,
                                         @Nonnull String streamerLoginName,
                                         @Nonnull String streamerId,
                                         @Nonnull String streamTitle,
                                         int streamViewers,
                                         @Nonnull String thumbnailUrl,
                                         @Nonnull String gameName) {
}
