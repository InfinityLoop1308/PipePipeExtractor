package org.schabi.newpipe.extractor.services.twitch.data.api.responses.stream;

import javax.annotation.Nonnull;

public record TwitchStreamResponseInner(@Nonnull String streamerName,
                                        @Nonnull String streamerLoginName,
                                        @Nonnull String streamerId,
                                        @Nonnull String streamTitle,
                                        int viewerCount, @Nonnull String createdDateString) {

}
