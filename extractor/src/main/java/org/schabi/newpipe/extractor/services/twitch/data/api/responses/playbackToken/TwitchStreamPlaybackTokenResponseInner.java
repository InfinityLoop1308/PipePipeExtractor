package org.schabi.newpipe.extractor.services.twitch.data.api.responses.playbackToken;

import javax.annotation.Nonnull;

public record TwitchStreamPlaybackTokenResponseInner(@Nonnull String signature,
                                                     @Nonnull String value) {

}
