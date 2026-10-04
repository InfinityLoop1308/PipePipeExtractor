package org.schabi.newpipe.extractor.services.twitch.data.api.responses.directory;

import javax.annotation.Nonnull;

public record TwitchDirectoryResponseEntry(@Nonnull String title,
                                           @Nonnull String id,
                                           @Nonnull String streamerName,
                                           @Nonnull String streamerLoginName,
                                           @Nonnull String streamerId,
                                           @Nonnull String profileImageUrl,
                                           boolean isPartner,
                                           int viewCount) {}