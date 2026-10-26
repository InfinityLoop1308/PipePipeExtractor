package org.schabi.newpipe.extractor.services.twitch.data.api.responses.vod;

import javax.annotation.Nonnull;

public record TwitchHelixVodResponseEntry(
        @Nonnull String title,
        @Nonnull String vodId,
        @Nonnull String userName,
        @Nonnull String userLoginName,
        @Nonnull String userId,
        @Nonnull String createDateString,
        @Nonnull String thumbnailUrl,
        int views,
        @Nonnull String durationString
) {
}
