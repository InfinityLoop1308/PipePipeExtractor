package org.schabi.newpipe.extractor.services.twitch.data;

import javax.annotation.Nonnull;

public record Resolution(int width, int height) {
    @Nonnull
    public String asResolutionString() {
        return width + "x" + height;
    }
}