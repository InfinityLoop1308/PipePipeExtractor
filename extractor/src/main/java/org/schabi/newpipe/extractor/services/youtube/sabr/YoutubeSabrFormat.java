package org.schabi.newpipe.extractor.services.youtube.sabr;

import com.grack.nanojson.JsonArray;
import com.grack.nanojson.JsonObject;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;

public final class YoutubeSabrFormat {
    private final int itag;
    private final long lastModified;
    @Nullable
    private final String xtags;
    @Nullable
    private final String mimeType;
    @Nullable
    private final String audioTrackId;
    @Nullable
    private final String qualityLabel;
    @Nullable
    private final String audioQuality;
    private final boolean drc;
    private final int width;
    private final int height;
    private final int bitrate;
    private final long contentLength;
    private final long approxDurationMs;

    private YoutubeSabrFormat(final int itag,
                              final long lastModified,
                              @Nullable final String xtags,
                              @Nullable final String mimeType,
                              @Nullable final String audioTrackId,
                              @Nullable final String qualityLabel,
                              @Nullable final String audioQuality,
                              final boolean drc,
                              final int width,
                              final int height,
                              final int bitrate,
                              final long contentLength,
                              final long approxDurationMs) {
        this.itag = itag;
        this.lastModified = lastModified;
        this.xtags = xtags;
        this.mimeType = mimeType;
        this.audioTrackId = audioTrackId;
        this.qualityLabel = qualityLabel;
        this.audioQuality = audioQuality;
        this.drc = drc;
        this.width = width;
        this.height = height;
        this.bitrate = bitrate;
        this.contentLength = contentLength;
        this.approxDurationMs = approxDurationMs;
    }

    @Nonnull
    static List<YoutubeSabrFormat> fromAdaptiveFormats(@Nullable final JsonArray formats) {
        final List<YoutubeSabrFormat> result = new ArrayList<>();
        if (formats == null) {
            return result;
        }
        for (int i = 0; i < formats.size(); i++) {
            final JsonObject format = formats.getObject(i);
            if (format != null && format.has("itag")) {
                result.add(fromJson(format));
            }
        }
        return result;
    }

    @Nonnull
    private static YoutubeSabrFormat fromJson(@Nonnull final JsonObject format) {
        final JsonObject audioTrack = format.getObject("audioTrack");
        return new YoutubeSabrFormat(
                format.getInt("itag"),
                parseLong(format.get("lastModified")),
                format.getString("xtags"),
                format.getString("mimeType"),
                audioTrack == null ? null : audioTrack.getString("id"),
                format.getString("qualityLabel"),
                format.getString("audioQuality"),
                format.getBoolean("isDrc", false),
                format.getInt("width", -1),
                format.getInt("height", -1),
                format.getInt("bitrate", -1),
                parseLong(format.get("contentLength")),
                parseLong(format.get("approxDurationMs")));
    }

    private static long parseLong(@Nullable final Object value) {
        if (value instanceof Number) {
            return ((Number) value).longValue();
        }
        if (value instanceof String) {
            try {
                return Long.parseLong((String) value);
            } catch (final NumberFormatException ignored) {
                return -1;
            }
        }
        return -1;
    }

    public boolean isAudio() {
        return mimeType != null && mimeType.contains("audio");
    }

    public boolean isVideo() {
        return mimeType != null && mimeType.contains("video");
    }

    public int getItag() {
        return itag;
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
    public String getQualityLabel() {
        return qualityLabel;
    }

    @Nullable
    public String getAudioQuality() {
        return audioQuality;
    }

    public boolean isDrc() {
        return drc;
    }

    public int getWidth() {
        return width;
    }

    public int getHeight() {
        return height;
    }

    public int getBitrate() {
        return bitrate;
    }

    public long getContentLength() {
        return contentLength;
    }

    public long getApproxDurationMs() {
        return approxDurationMs;
    }
}
