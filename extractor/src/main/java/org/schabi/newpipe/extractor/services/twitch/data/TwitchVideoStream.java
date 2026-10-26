package org.schabi.newpipe.extractor.services.twitch.data;

import javax.annotation.Nonnull;

public record TwitchVideoStream(
        @Nonnull Resolution resolution,
        @Nonnull String streamUrl
) {
}
