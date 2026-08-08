package org.schabi.newpipe.extractor.services.youtube.sabr;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/** Immutable description of one SABR transaction. */
public final class YoutubeSabrRequest {
    @Nonnull
    private final List<Track> tracks;
    @Nullable
    private final PlaybackState playbackState;

    private YoutubeSabrRequest(@Nonnull final Collection<Track> tracks,
                               @Nullable final PlaybackState playbackState) {
        Objects.requireNonNull(tracks, "tracks");
        if (tracks.isEmpty()) {
            throw new IllegalArgumentException("SABR request must contain at least one track");
        }
        final List<Track> copy = new ArrayList<>(tracks.size());
        YoutubeSabrInfo.Format audioFormat = null;
        YoutubeSabrInfo.Format videoFormat = null;
        for (final Track track : tracks) {
            if (track == null) {
                throw new IllegalArgumentException("SABR request track must not be null");
            }
            if (track.getFormat().isAudio()) {
                if (audioFormat != null) {
                    throw new IllegalArgumentException(
                            "SABR request must not contain multiple audio tracks");
                }
                audioFormat = track.getFormat();
            } else if (track.getFormat().isVideo()) {
                if (videoFormat != null) {
                    throw new IllegalArgumentException(
                            "SABR request must not contain multiple video tracks");
                }
                videoFormat = track.getFormat();
            } else {
                throw new IllegalArgumentException("SABR request format has no track type: itag="
                        + track.getFormat().getItag());
            }
            copy.add(track);
        }
        if (audioFormat != null && videoFormat != null
                && audioFormat.getItag() == videoFormat.getItag()) {
            throw new IllegalArgumentException("SABR audio/video formats must be distinct");
        }
        if (playbackState == null && copy.size() != 1) {
            throw new IllegalArgumentException(
                    "SABR initialization request must contain exactly one track");
        }
        this.tracks = Collections.unmodifiableList(copy);
        this.playbackState = playbackState;
    }

    /** Requests a missing initialization segment without sending playback state. */
    @Nonnull
    public static YoutubeSabrRequest initialization(
            @Nonnull final YoutubeSabrInfo.Format format) {
        return new YoutubeSabrRequest(
                Collections.singletonList(Track.of(format, null, 0)), null);
    }

    /** Requests media for the active tracks in their preferred response order. */
    @Nonnull
    public static YoutubeSabrRequest playback(
            final long playerTimeMs,
            final float playbackRate,
            @Nonnull final Collection<Track> tracks) {
        if (playerTimeMs < 0) {
            throw new IllegalArgumentException("SABR player time must not be negative");
        }
        if (!(playbackRate > 0)) {
            throw new IllegalArgumentException("SABR playback rate must be positive");
        }
        return new YoutubeSabrRequest(tracks,
                new PlaybackState(playerTimeMs, playbackRate));
    }

    @Nonnull
    List<Track> getTracks() {
        return tracks;
    }

    @Nullable
    PlaybackState getPlaybackState() {
        return playbackState;
    }

    @Nullable
    Track getAudioTrack() {
        for (final Track track : tracks) {
            if (track.getFormat().isAudio()) return track;
        }
        return null;
    }

    @Nullable
    Track getVideoTrack() {
        for (final Track track : tracks) {
            if (track.getFormat().isVideo()) return track;
        }
        return null;
    }

    public static final class Track {
        @Nonnull
        private final YoutubeSabrInfo.Format format;
        @Nullable
        private final YoutubeSabrFormatTimeline timeline;
        private final int bufferedThrough;

        private Track(@Nonnull final YoutubeSabrInfo.Format format,
                      @Nullable final YoutubeSabrFormatTimeline timeline,
                      final int bufferedThrough) {
            if (bufferedThrough < 0) {
                throw new IllegalArgumentException(
                        "SABR buffered-through sequence must not be negative");
            }
            this.format = Objects.requireNonNull(format, "format");
            this.timeline = timeline;
            this.bufferedThrough = bufferedThrough;
        }

        @Nonnull
        public static Track of(@Nonnull final YoutubeSabrInfo.Format format,
                               @Nullable final YoutubeSabrFormatTimeline timeline,
                               final int bufferedThrough) {
            return new Track(format, timeline, bufferedThrough);
        }

        @Nonnull
        YoutubeSabrInfo.Format getFormat() {
            return format;
        }

        @Nullable
        YoutubeSabrFormatTimeline getTimeline() {
            return timeline;
        }

        int getBufferedThrough() {
            return bufferedThrough;
        }
    }

    static final class PlaybackState {
        private final long playerTimeMs;
        private final float playbackRate;

        private PlaybackState(final long playerTimeMs, final float playbackRate) {
            this.playerTimeMs = playerTimeMs;
            this.playbackRate = playbackRate;
        }

        long getPlayerTimeMs() {
            return playerTimeMs;
        }

        float getPlaybackRate() {
            return playbackRate;
        }
    }
}
