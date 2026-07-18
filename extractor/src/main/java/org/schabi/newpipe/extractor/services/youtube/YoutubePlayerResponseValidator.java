package org.schabi.newpipe.extractor.services.youtube;

import com.grack.nanojson.JsonArray;
import com.grack.nanojson.JsonObject;

import org.schabi.newpipe.extractor.exceptions.AgeRestrictedContentException;
import org.schabi.newpipe.extractor.exceptions.AntiBotException;
import org.schabi.newpipe.extractor.exceptions.ContentNotAvailableException;
import org.schabi.newpipe.extractor.exceptions.GeographicRestrictionException;
import org.schabi.newpipe.extractor.exceptions.LiveNotStartException;
import org.schabi.newpipe.extractor.exceptions.PaidContentException;
import org.schabi.newpipe.extractor.exceptions.ParsingException;
import org.schabi.newpipe.extractor.exceptions.PrivateContentException;
import org.schabi.newpipe.extractor.exceptions.VideoNotReleaseException;
import org.schabi.newpipe.extractor.exceptions.YoutubeMusicPremiumContentException;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/**
 * Maps Innertube player playability statuses to extractor exceptions.
 */
public final class YoutubePlayerResponseValidator {
    private static final String BOT_VERIFICATION_HELP_PATH =
            "support.google.com/youtube/answer/3037019";

    private YoutubePlayerResponseValidator() {
    }

    /**
     * Validates a player response's {@code playabilityStatus} object.
     *
     * @param playabilityStatus the player response status object
     * @throws ParsingException when YouTube rejects or cannot play the content
     */
    public static void checkPlayabilityStatus(@Nonnull final JsonObject playabilityStatus)
            throws ParsingException {
        final String status = playabilityStatus.getString("status");
        if (status == null || status.equalsIgnoreCase("ok")) {
            return;
        }

        final String reason = playabilityStatus.getString("reason");
        if (status.equalsIgnoreCase("login_required")) {
            if (reason == null) {
                final JsonArray messages = playabilityStatus.getArray("messages");
                final String message = messages == null || messages.isEmpty()
                        ? null : messages.getString(0);
                if (message != null && message.contains("private")) {
                    throw new PrivateContentException("This video is private");
                }
            } else if (reason.contains("age")) {
                throw new AgeRestrictedContentException(
                        "This age-restricted video cannot be watched anonymously");
            }
        }

        if ((status.equalsIgnoreCase("unplayable") || status.equalsIgnoreCase("error"))
                && reason != null) {
            if (reason.contains("Music Premium")) {
                throw new YoutubeMusicPremiumContentException();
            }
            if (reason.contains("payment")) {
                throw new PaidContentException("This video is a paid video");
            }
            if (reason.contains("members-only")) {
                throw new PaidContentException("This video is only available"
                        + " for members of the channel of this video");
            }
            if (reason.contains("unavailable")) {
                final JsonObject renderer = getPlayerErrorMessageRenderer(playabilityStatus);
                final String detailedErrorMessage = renderer == null ? null
                        : YoutubeParsingHelper.getTextFromObject(renderer.getObject("subreason"));
                if (detailedErrorMessage != null && detailedErrorMessage.contains("country")) {
                    throw new GeographicRestrictionException(
                            "This video is not available in client's country.");
                }
                throw new ContentNotAvailableException(detailedErrorMessage == null
                        ? reason : detailedErrorMessage);
            }
        }
        if (isBotVerificationRequired(playabilityStatus, reason)) {
            throw new AntiBotException(reason == null
                    ? "YouTube bot verification is required" : reason);
        }
        if (reason != null && reason.contains("This live event will begin in")) {
            throw new LiveNotStartException(reason);
        }
        if (reason != null && reason.contains("Premieres in")) {
            throw new VideoNotReleaseException(reason);
        }
        throw new ContentNotAvailableException("Got error: \"" + reason + "\"");
    }

    private static boolean isBotVerificationRequired(
            @Nonnull final JsonObject playabilityStatus,
            @Nullable final String reason) {
        if (reason != null && reason.contains("Sign in to confirm")) {
            return true;
        }
        final JsonObject renderer = getPlayerErrorMessageRenderer(playabilityStatus);
        final JsonObject subreason = renderer == null ? null : renderer.getObject("subreason");
        final JsonArray runs = subreason == null ? null : subreason.getArray("runs");
        if (runs == null) {
            return false;
        }
        for (final Object value : runs) {
            if (value instanceof JsonObject && hasBotVerificationHelpUrl((JsonObject) value)) {
                return true;
            }
        }
        return false;
    }

    private static boolean hasBotVerificationHelpUrl(@Nonnull final JsonObject run) {
        final JsonObject navigationEndpoint = run.getObject("navigationEndpoint");
        if (navigationEndpoint == null) {
            return false;
        }
        final JsonObject urlEndpoint = navigationEndpoint.getObject("urlEndpoint");
        if (hasBotVerificationHelpPath(urlEndpoint == null
                ? null : urlEndpoint.getString("url"))) {
            return true;
        }
        final JsonObject commandMetadata = navigationEndpoint.getObject("commandMetadata");
        final JsonObject webCommandMetadata = commandMetadata == null ? null
                : commandMetadata.getObject("webCommandMetadata");
        return hasBotVerificationHelpPath(webCommandMetadata == null
                ? null : webCommandMetadata.getString("url"));
    }

    private static boolean hasBotVerificationHelpPath(@Nullable final String url) {
        return url != null && url.contains(BOT_VERIFICATION_HELP_PATH);
    }

    @Nullable
    private static JsonObject getPlayerErrorMessageRenderer(
            @Nonnull final JsonObject playabilityStatus) {
        final JsonObject errorScreen = playabilityStatus.getObject("errorScreen");
        return errorScreen == null ? null : errorScreen.getObject("playerErrorMessageRenderer");
    }
}
