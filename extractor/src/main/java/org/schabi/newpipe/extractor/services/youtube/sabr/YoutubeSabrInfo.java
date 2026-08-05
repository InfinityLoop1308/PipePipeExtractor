package org.schabi.newpipe.extractor.services.youtube.sabr;

import org.schabi.newpipe.extractor.services.youtube.ItagItem;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.io.Serializable;
import java.util.Collections;
import java.util.List;

public final class YoutubeSabrInfo implements Serializable {
    private static final long serialVersionUID = 3L;

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
    @Nullable
    private final byte[] poToken;
    @Nonnull
    private final List<Format> formats;

    public YoutubeSabrInfo(@Nonnull final String videoId,
                    @Nonnull final String cpn,
                    @Nonnull final String clientVersion,
                    @Nullable final String visitorData,
                    @Nullable final String serverAbrStreamingUrl,
                    @Nullable final String videoPlaybackUstreamerConfig,
                    @Nonnull final List<Format> formats) {
        this(videoId, cpn, clientVersion, visitorData, serverAbrStreamingUrl,
                videoPlaybackUstreamerConfig, formats, null);
    }

    public YoutubeSabrInfo(@Nonnull final String videoId,
                    @Nonnull final String cpn,
                    @Nonnull final String clientVersion,
                    @Nullable final String visitorData,
                    @Nullable final String serverAbrStreamingUrl,
                    @Nullable final String videoPlaybackUstreamerConfig,
                    @Nonnull final List<Format> formats,
                    @Nullable final byte[] poToken) {
        this.videoId = videoId;
        this.cpn = cpn;
        this.clientVersion = clientVersion;
        this.visitorData = visitorData;
        this.serverAbrStreamingUrl = serverAbrStreamingUrl;
        this.videoPlaybackUstreamerConfig = videoPlaybackUstreamerConfig;
        this.formats = formats;
        this.poToken = poToken == null ? null : poToken.clone();
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

    @Nullable
    public byte[] getPoToken() {
        return poToken == null ? null : poToken.clone();
    }

    @Nonnull
    public List<Format> getFormats() {
        return Collections.unmodifiableList(formats);
    }

    public static final class Format implements Serializable {
        private static final long serialVersionUID = 1L;

        @Nonnull
        private final ItagItem parsedFormat;
        private final long lastModified;
        @Nullable
        private final String xtags;
        @Nullable
        private final String mimeType;
        @Nullable
        private final String audioTrackId;
        @Nullable
        private final String audioTrackDisplayName;
        private final boolean drc;
        @Nullable
        private final String initializationUrl;
        private final long initRangeStart;
        private final long initRangeEnd;

        private Format(@Nonnull final ItagItem parsedFormat,
                       final long lastModified,
                       @Nullable final String xtags,
                       @Nullable final String mimeType,
                       @Nullable final String audioTrackId,
                       @Nullable final String audioTrackDisplayName,
                       final boolean drc,
                       @Nullable final String initializationUrl,
                       final long initRangeStart,
                       final long initRangeEnd) {
            this.parsedFormat = new ItagItem(parsedFormat);
            this.lastModified = lastModified;
            this.xtags = xtags;
            this.mimeType = mimeType;
            this.audioTrackId = audioTrackId;
            this.audioTrackDisplayName = audioTrackDisplayName;
            this.drc = drc;
            this.initializationUrl = initializationUrl;
            this.initRangeStart = initRangeStart;
            this.initRangeEnd = initRangeEnd;
        }

        @Nonnull
        public static Format fromParsedFormat(
                @Nonnull final ItagItem parsedFormat,
                final long lastModified,
                @Nullable final String xtags,
                @Nullable final String mimeType,
                @Nullable final String audioTrackId,
                @Nullable final String audioTrackDisplayName,
                final boolean drc,
                @Nullable final String initializationUrl,
                final long initRangeStart,
                final long initRangeEnd) {
            return new Format(parsedFormat, lastModified, xtags, mimeType,
                    audioTrackId, audioTrackDisplayName, drc, initializationUrl,
                    initRangeStart, initRangeEnd);
        }

        public boolean isAudio() {
            return mimeType != null && mimeType.startsWith("audio/");
        }

        public boolean isVideo() {
            return mimeType != null && mimeType.startsWith("video/");
        }

        public int getItag() {
            return parsedFormat.id;
        }

        @Nonnull
        public ItagItem toItagItem() {
            return new ItagItem(parsedFormat);
        }

        public long getLastModified() {
            return lastModified;
        }

        @Nullable
        public String getXtags() {
            return xtags;
        }

        @Nullable
        public String getMimeType() {
            return mimeType;
        }

        @Nullable
        public String getAudioTrackId() {
            return audioTrackId;
        }

        @Nullable
        public String getAudioTrackDisplayName() {
            return audioTrackDisplayName;
        }

        public boolean isOriginalAudio() {
            return audioTrackDisplayName != null
                    && (audioTrackDisplayName.contains("original")
                    || audioTrackDisplayName.contains("yokuqala"));
        }

        public boolean isDrc() {
            return drc;
        }

        public int getWidth() {
            return parsedFormat.getWidth();
        }

        public int getHeight() {
            return parsedFormat.getHeight();
        }

        public int getBitrate() {
            return parsedFormat.getBitrate();
        }

        public long getContentLength() {
            return parsedFormat.getContentLength();
        }

        public long getApproxDurationMs() {
            return parsedFormat.getApproxDurationMs();
        }

        @Nullable
        public String getInitializationUrl() {
            return initializationUrl;
        }

        public long getInitRangeStart() {
            return initRangeStart;
        }

        public long getInitRangeEnd() {
            return initRangeEnd;
        }
    }
}
