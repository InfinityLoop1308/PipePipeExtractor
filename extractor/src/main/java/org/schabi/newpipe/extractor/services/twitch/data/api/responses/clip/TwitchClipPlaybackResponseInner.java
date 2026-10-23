package org.schabi.newpipe.extractor.services.twitch.data.api.responses.clip;

import javax.annotation.Nonnull;

public record TwitchClipPlaybackResponseInner(@Nonnull String clipUrl, int clipHeight,
                                              int clipWidth) {

}
