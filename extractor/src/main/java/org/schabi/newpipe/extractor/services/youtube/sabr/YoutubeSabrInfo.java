package org.schabi.newpipe.extractor.services.youtube.sabr;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.Collections;
import java.util.List;

public final class YoutubeSabrInfo {
    @Nonnull
    private final YoutubeSabrClientProfile profile;
    @Nonnull
    private final String videoId;
    @Nonnull
    private final String cpn;
    @Nonnull
    private final String clientVersion;
    @Nullable
    private final String visitorData;
    @Nullable
    private final String serverAbrStreamingUrl;
    @Nullable
    private final String videoPlaybackUstreamerConfig;
    @Nonnull
    private final List<YoutubeSabrFormat> formats;

    YoutubeSabrInfo(@Nonnull final YoutubeSabrClientProfile profile,
                    @Nonnull final String videoId,
                    @Nonnull final String cpn,
                    @Nonnull final String clientVersion,
                    @Nullable final String visitorData,
                    @Nullable final String serverAbrStreamingUrl,
                    @Nullable final String videoPlaybackUstreamerConfig,
                    @Nonnull final List<YoutubeSabrFormat> formats) {
        this.profile = profile;
        this.videoId = videoId;
        this.cpn = cpn;
        this.clientVersion = clientVersion;
        this.visitorData = visitorData;
        this.serverAbrStreamingUrl = serverAbrStreamingUrl;
        this.videoPlaybackUstreamerConfig = videoPlaybackUstreamerConfig;
        this.formats = formats;
    }

    @Nonnull
    public YoutubeSabrClientProfile getProfile() {
        return profile;
    }

    @Nonnull
    public String getVideoId() {
        return videoId;
    }

    @Nonnull
    public String getCpn() {
        return cpn;
    }

    @Nonnull
    public String getClientVersion() {
        return clientVersion;
    }

    @Nullable
    public String getVisitorData() {
        return visitorData;
    }

    @Nullable
    public String getServerAbrStreamingUrl() {
        return serverAbrStreamingUrl;
    }

    @Nullable
    public String getVideoPlaybackUstreamerConfig() {
        return videoPlaybackUstreamerConfig;
    }

    @Nonnull
    public List<YoutubeSabrFormat> getFormats() {
        return Collections.unmodifiableList(formats);
    }

    @Nullable
    public YoutubeSabrFormat findBestAudioFormat() {
        YoutubeSabrFormat best = null;
        for (final YoutubeSabrFormat format : formats) {
            if (format.isAudio() && (best == null || format.getBitrate() > best.getBitrate())) {
                best = format;
            }
        }
        return best;
    }

    @Nullable
    public YoutubeSabrFormat findBestVideoFormat() {
        YoutubeSabrFormat best = null;
        for (final YoutubeSabrFormat format : formats) {
            if (format.isVideo() && (best == null || format.getHeight() > best.getHeight())) {
                best = format;
            }
        }
        return best;
    }

    @Nullable
    public YoutubeSabrFormat findFormatByItag(final int itag) {
        for (final YoutubeSabrFormat format : formats) {
            if (format.getItag() == itag) {
                return format;
            }
        }
        return null;
    }
}
