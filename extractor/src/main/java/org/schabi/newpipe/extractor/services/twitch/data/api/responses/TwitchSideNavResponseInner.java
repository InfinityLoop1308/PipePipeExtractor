package org.schabi.newpipe.extractor.services.twitch.data.api.responses;

import javax.annotation.Nonnull;

public record TwitchSideNavResponseInner(
        @Nonnull String streamerName,
        @Nonnull String streamerLoginName,
        @Nonnull String title,
        int viewCount
) {
}
