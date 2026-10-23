package org.schabi.newpipe.extractor.services.twitch.data.api.responses.clip;

import javax.annotation.Nonnull;

public record TwitchVideoPlayerMediaSessionManagerResponseInner(@Nonnull String clipTitle,
                                                                @Nonnull String ownerDisplayName,
                                                                @Nonnull String ownerLoginName,
                                                                @Nonnull String ownerId,
                                                                @Nonnull String ownerProfileImageUrl) {
}
