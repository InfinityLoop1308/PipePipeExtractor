package org.schabi.newpipe.extractor.services.twitch.data.api.responses.directory;

import java.util.List;

import javax.annotation.Nonnull;

public record TwitchDirectoryResponseInner(@Nonnull String directoryName,
                                           @Nonnull String gameId,
                                           @Nonnull List<TwitchDirectoryResponseEntry> entries) {
}
