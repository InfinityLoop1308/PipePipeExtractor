package org.schabi.newpipe.extractor.services.youtube.sabr;

import javax.annotation.Nonnull;
import java.util.List;

/** Host-only request inputs. No secret accessor is exposed to policy implementations. */
final class SabrProfileRequestData {
    @Nonnull private final YoutubeSabrInfo info;
    @Nonnull private final YoutubeSabrFormat audioFormat;
    @Nonnull private final YoutubeSabrFormat videoFormat;
    @Nonnull private final YoutubeSabrStreamState streamState;
    private final boolean initial;

    SabrProfileRequestData(@Nonnull final YoutubeSabrInfo info,
                           @Nonnull final YoutubeSabrFormat audioFormat,
                           @Nonnull final YoutubeSabrFormat videoFormat,
                           @Nonnull final YoutubeSabrStreamState streamState,
                           final boolean initial) {
        this.info = info;
        this.audioFormat = audioFormat;
        this.videoFormat = videoFormat;
        this.streamState = streamState;
        this.initial = initial;
    }

    @Nonnull
    byte[] build(@Nonnull final List<SabrProfileRequestField> template)
            throws SabrProtocolException {
        return SabrProfileRequestBuilder.build(info, audioFormat, videoFormat,
                streamState, initial, template);
    }

    @Nonnull
    YoutubeSabrClientProfile getClientProfile() {
        return info.getProfile();
    }
}
