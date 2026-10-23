package org.schabi.newpipe.extractor.services.twitch.api;

import java.util.Map;

import javax.annotation.Nonnull;

public record Cookie(@Nonnull String name, @Nonnull String value,
                     @Nonnull Map<String, String> attributes) {
}
