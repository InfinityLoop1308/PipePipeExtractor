package org.schabi.newpipe.extractor.services.twitch.data.url;

import org.schabi.newpipe.extractor.services.twitch.data.TwitchChannelTabLinkType;
import org.schabi.newpipe.extractor.services.twitch.data.api.pagination.Pagination;

import javax.annotation.Nonnull;

public record ChannelUrl(@Nonnull String channelLoginName,
                         @Nonnull String channelId,
                         @Nonnull TwitchChannelTabLinkType tabType,
                         @Nonnull Pagination pagination) {
}
