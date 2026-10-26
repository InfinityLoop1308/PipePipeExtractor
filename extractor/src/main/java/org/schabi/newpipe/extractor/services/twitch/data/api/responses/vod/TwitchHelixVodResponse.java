package org.schabi.newpipe.extractor.services.twitch.data.api.responses.vod;

import org.jetbrains.annotations.Nullable;

public record TwitchHelixVodResponse(
        boolean hasMoreEntries,
        @Nullable String cursor,
        TwitchHelixVodResponseEntry[] entries
) {
}
