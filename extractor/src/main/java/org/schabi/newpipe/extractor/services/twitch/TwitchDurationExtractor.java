package org.schabi.newpipe.extractor.services.twitch;

import java.util.regex.Pattern;

import javax.annotation.Nonnull;

public final class TwitchDurationExtractor {

    private static final Pattern digitPattern = Pattern.compile("\\d+");

    private static int getMultiplier(final char unit) {
        return switch (unit) {
            case 's' -> 1;
            case 'm' -> 60;
            case 'h' -> 3600;
            default -> throw new IllegalStateException("Unexpected value: " + unit);
        };
    }

    public static int parseDurationFromString(@Nonnull final String toParse) {
        final var matcher = digitPattern.matcher(toParse);
        var duration = 0;
        while (matcher.find()) {
            final var amount = Integer.parseInt(matcher.group());
            final var unitChar = toParse.charAt(matcher.end());
            duration += amount * getMultiplier(unitChar);
        }
        return duration;
    }

}