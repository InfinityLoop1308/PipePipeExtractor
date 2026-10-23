package org.schabi.newpipe.extractor.services.twitch.api;

public record TwitchApiSettings(
        boolean shouldUseHelixForKiosk,
        boolean shouldUseHelixForCategories,
        boolean shouldUseHelixForVods,
        boolean shouldUseHelixForClips,
        boolean shouldUseHelixForSearch
) {
}
