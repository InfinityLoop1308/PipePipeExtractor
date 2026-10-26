package org.schabi.newpipe.extractor.services.twitch.data.api.responses.search.types;

import org.schabi.newpipe.extractor.services.twitch.data.api.responses.search.TwitchSearchBaseResponseEntry;

import javax.annotation.Nonnull;

public final class TwitchSearchVodResponseEntry extends TwitchSearchBaseResponseEntry {

    @Nonnull
    private final String vodTitle;

    @Nonnull
    private final String channelName;

    @Nonnull
    private final String channelLoginName;
    @Nonnull
    private final String channelId;
    private final int vodViewCount;

    private final int vodDurationInSeconds;

    @Nonnull
    private final String streamThumbnailUrl;

    @Nonnull
    private final String uploadTimeStampString;

    @Nonnull
    private final String vodId;

    public TwitchSearchVodResponseEntry(
            @Nonnull String vodTitle,
            @Nonnull String vodId,
            @Nonnull String channelName,
            @Nonnull String channelLoginName,
            @Nonnull String channelId,
            int vodViewCount,
            int vodDurationInSeconds,
            @Nonnull String streamThumbnailUrl,
            @Nonnull String uploadTimeStampString) {
        this.vodTitle = vodTitle;
        this.vodId = vodId;
        this.channelName = channelName;
        this.channelLoginName = channelLoginName;
        this.channelId = channelId;
        this.vodViewCount = vodViewCount;
        this.vodDurationInSeconds = vodDurationInSeconds;
        this.streamThumbnailUrl = streamThumbnailUrl;
        this.uploadTimeStampString = uploadTimeStampString;
    }

    @Nonnull
    public String getVodTitle() {
        return vodTitle;
    }

    @Nonnull
    public String getChannelName() {
        return channelName;
    }

    public int getVodViewCount() {
        return vodViewCount;
    }

    public int getVodDurationInSeconds() {
        return vodDurationInSeconds;
    }

    @Nonnull
    public String getStreamThumbnailUrl() {
        return streamThumbnailUrl;
    }

    @Nonnull
    public String getUploadTimeStampString() {
        return uploadTimeStampString;
    }

    @Nonnull
    public String getVodId() {
        return vodId;
    }

    @Nonnull
    public String getChannelId() {
        return channelId;
    }

    @Nonnull
    public String getChannelLoginName() {
        return channelLoginName;
    }
}
