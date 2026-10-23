package org.schabi.newpipe.extractor.services.twitch.data.api.responses.clip;

import javax.annotation.Nonnull;

public record TwitchClipResponseInner(@Nonnull String clipThumbnailUrl, @Nonnull String gameName,
                                      @Nonnull String clipTitle, @Nonnull String clipId,
                                      @Nonnull String uploadDateTimeString,
                                      @Nonnull String clipperName, int clipViewerCount,
                                      int clipLength) {

}
