package org.schabi.newpipe.extractor.services.youtube.sabr;

import org.schabi.newpipe.extractor.exceptions.AntiBotException;

import javax.annotation.Nonnull;

/**
 * Indicates that YouTube rejected a WEB or MWEB player request before exposing SABR metadata.
 */
public final class YoutubePlayerAdmissionException extends AntiBotException {
    @Nonnull
    private final String videoId;
    @Nonnull
    private final YoutubeSabrClientProfile profile;
    @Nonnull
    private final String playabilityStatus;
    private final boolean playerPoTokenSupplied;
    private final boolean visitorDataSupplied;
    private final boolean playerContextRefreshed;

    YoutubePlayerAdmissionException(@Nonnull final String videoId,
                                    @Nonnull final YoutubeSabrClientProfile profile,
                                    @Nonnull final String playabilityStatus,
                                    @Nonnull final String reason,
                                    final boolean playerPoTokenSupplied,
                                    final boolean visitorDataSupplied,
                                    final boolean playerContextRefreshed,
                                    @Nonnull final AntiBotException cause) {
        super("YouTube rejected " + profile.getClientName() + " player admission: " + reason,
                cause);
        this.videoId = videoId;
        this.profile = profile;
        this.playabilityStatus = playabilityStatus;
        this.playerPoTokenSupplied = playerPoTokenSupplied;
        this.visitorDataSupplied = visitorDataSupplied;
        this.playerContextRefreshed = playerContextRefreshed;
    }

    @Nonnull
    public String getVideoId() {
        return videoId;
    }

    @Nonnull
    public YoutubeSabrClientProfile getProfile() {
        return profile;
    }

    @Nonnull
    public String getPlayabilityStatus() {
        return playabilityStatus;
    }

    public boolean isPlayerPoTokenSupplied() {
        return playerPoTokenSupplied;
    }

    public boolean isVisitorDataSupplied() {
        return visitorDataSupplied;
    }

    public boolean wasPlayerContextRefreshed() {
        return playerContextRefreshed;
    }
}
