package org.schabi.newpipe.extractor.services.twitch.data.api.responses.vod;

import javax.annotation.Nonnull;

public final class TwitchVodResponseInner {

    @Nonnull
    private final String vodPreviewUrl;

    @Nonnull
    private final String gameName;

    @Nonnull
    private final String vodId;

    @Nonnull
    private final String vodTitle;

    @Nonnull
    private final String uploadDateTimeString;

    private final int vodViewerCount;

    private final int vodLength;

    public TwitchVodResponseInner(
            @Nonnull String vodPreviewUrl,
            @Nonnull String gameName,
            @Nonnull String vodId,
            @Nonnull String vodTitle,
            @Nonnull String uploadDateTimeString,
            int vodViewerCount,
            int vodLength) {
        this.vodPreviewUrl = vodPreviewUrl;
        this.gameName = gameName;
        this.vodId = vodId;
        this.vodTitle = vodTitle;
        this.uploadDateTimeString = uploadDateTimeString;
        this.vodViewerCount = vodViewerCount;
        this.vodLength = vodLength;
    }

    @Nonnull
    public String getVodThumbnailUrl() {
        return vodPreviewUrl;
    }

    @Nonnull
    public String getGameName() {
        return gameName;
    }

    @Nonnull
    public String getVodTitle() {
        return vodTitle;
    }

    @Nonnull
    public String getUploadDateTimeString() {
        return uploadDateTimeString;
    }

    public int getVodViewerCount() {
        return vodViewerCount;
    }

    public int getVodLength() {
        return vodLength;
    }

    @Nonnull
    public String getVodId() {
        return vodId;
    }
}
