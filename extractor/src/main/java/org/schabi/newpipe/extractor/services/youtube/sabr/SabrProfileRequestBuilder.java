package org.schabi.newpipe.extractor.services.youtube.sabr;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** Builds bounded protobuf requests from Host-owned typed values. */
final class SabrProfileRequestBuilder {
    private SabrProfileRequestBuilder() {
    }

    @Nonnull
    static byte[] build(@Nonnull final YoutubeSabrInfo info,
                        @Nonnull final YoutubeSabrFormat audioFormat,
                        @Nonnull final YoutubeSabrFormat videoFormat,
                        @Nonnull final YoutubeSabrStreamState streamState,
                        final boolean initial,
                        @Nonnull final List<SabrProfileRequestField> template)
            throws SabrProtocolException {
        synchronized (streamState) {
            final SabrProto.Writer request = new SabrProto.Writer();
            for (final SabrProfileRequestField field : template) {
                final List<Object> values = resolve(info, audioFormat, videoFormat, streamState,
                        initial, field.getSource());
                if (values.isEmpty() && field.isRequired()) {
                    throw new SabrProtocolException("Required SABR profile source is empty: "
                            + field.getSource());
                }
                for (final Object value : values) {
                    if (field.getWireType() == SabrProfileRequestField.WireType.VARINT) {
                        request.writeUInt64(field.getField(), (Long) value);
                    } else {
                        request.writeBytes(field.getField(), (byte[]) value);
                    }
                }
            }
            return request.toByteArray();
        }
    }

    @Nonnull
    private static List<Object> resolve(
            @Nonnull final YoutubeSabrInfo info,
            @Nonnull final YoutubeSabrFormat audioFormat,
            @Nonnull final YoutubeSabrFormat videoFormat,
            @Nonnull final YoutubeSabrStreamState streamState,
            final boolean initial,
            @Nonnull final SabrProfileRequestField.Source source)
            throws SabrProtocolException {
        final long playerTimeMs = streamState.getRequestPlayerTimeMs();
        final List<SabrBufferedRange> bufferedRanges = streamState.getBufferedRanges();
        final boolean includePlaybackState = !initial || playerTimeMs > 0
                || !bufferedRanges.isEmpty();
        switch (source) {
            case PLAYER_TIME_MS:
                return includePlaybackState && streamState.shouldWriteTopLevelPlayerTimeMs()
                        ? singletonLong(playerTimeMs) : Collections.emptyList();
            case BUFFERED_RANGES:
                if (!includePlaybackState) return Collections.emptyList();
                final List<Object> ranges = new ArrayList<>();
                for (final SabrBufferedRange range : bufferedRanges) {
                    ranges.add(range.toProto(streamState.shouldWriteBufferedRangeTimeRange()));
                }
                return ranges;
            case PLAYBACK_COOKIE:
                return singletonBytes(streamState.getRawPlaybackCookie());
            case PO_TOKEN:
                return singletonBytes(streamState.getRawPoToken());
            case SELECTED_FORMATS:
                return selectedFormats(audioFormat, videoFormat, streamState,
                        includePlaybackState);
            case CLIENT_CONTEXT:
                return singletonBytes(YoutubeSabrRequestBuilder.buildStreamerContext(
                        info, streamState));
            case CLIENT_ABR_STATE:
                return singletonBytes(YoutubeSabrRequestBuilder.buildClientAbrState(
                        audioFormat, videoFormat, playerTimeMs, includePlaybackState,
                        streamState.getEnabledTrackTypesBitfield(), streamState));
            case USTREAMER_CONFIG:
                final String config = info.getVideoPlaybackUstreamerConfig();
                if (config == null || config.isEmpty()) return Collections.emptyList();
                return singletonBytes(YoutubeSabrRequestBuilder.decodeBase64(config));
            case PREFERRED_AUDIO_FORMATS:
                return preferredFormats(info, audioFormat, videoFormat, streamState, true);
            case PREFERRED_VIDEO_FORMATS:
                return preferredFormats(info, audioFormat, videoFormat, streamState, false);
            default:
                throw new SabrProtocolException("Unsupported SABR profile request source: "
                        + source);
        }
    }

    @Nonnull
    private static List<Object> selectedFormats(@Nonnull final YoutubeSabrFormat audioFormat,
                                                @Nonnull final YoutubeSabrFormat videoFormat,
                                                @Nonnull final YoutubeSabrStreamState streamState,
                                                final boolean includePlaybackState) {
        if (!includePlaybackState) return Collections.emptyList();
        final List<Object> values = new ArrayList<>();
        if (streamState.shouldSelectVideoFormatBeforeAudio()) {
            addSelected(values, videoFormat, streamState.shouldSelectVideoFormat(), streamState);
        }
        addSelected(values, audioFormat, streamState.shouldSelectAudioFormat(), streamState);
        if (!streamState.shouldSelectVideoFormatBeforeAudio()) {
            addSelected(values, videoFormat, streamState.shouldSelectVideoFormat(), streamState);
        }
        return values;
    }

    private static void addSelected(@Nonnull final List<Object> values,
                                    @Nonnull final YoutubeSabrFormat format,
                                    final boolean selected,
                                    @Nonnull final YoutubeSabrStreamState streamState) {
        if (selected && streamState.isInitialized(format)) {
            values.add(SabrProto.formatId(format));
        }
    }

    @Nonnull
    private static List<Object> preferredFormats(
            @Nonnull final YoutubeSabrInfo info,
            @Nonnull final YoutubeSabrFormat audioFormat,
            @Nonnull final YoutubeSabrFormat videoFormat,
            @Nonnull final YoutubeSabrStreamState streamState,
            final boolean audio) {
        final List<Object> values = new ArrayList<>();
        if (audio && !streamState.shouldSelectAudioFormat()
                || !audio && !streamState.shouldSelectVideoFormat()) {
            return values;
        }
        if (!streamState.shouldWriteAllPreferredFormats()) {
            values.add(SabrProto.formatId(audio ? audioFormat : videoFormat));
            return values;
        }
        if (streamState.shouldWriteOfficialWebPreferredFormats()) {
            for (final YoutubeSabrFormat format : officialPreferredFormats(info)) {
                if (audio == format.isAudio()) values.add(SabrProto.formatId(format));
            }
            return values;
        }
        for (final YoutubeSabrFormat format : info.getFormats()) {
            if (audio == format.isAudio() && (audio || format.isVideo())) {
                values.add(SabrProto.formatId(format));
            }
        }
        return values;
    }

    @Nonnull
    private static List<YoutubeSabrFormat> officialPreferredFormats(
            @Nonnull final YoutubeSabrInfo info) {
        final List<YoutubeSabrFormat> values = new ArrayList<>();
        addAudio(values, info, 251, 12);
        addAudio(values, info, 251, 14);
        addAudio(values, info, 251, 0);
        addAudio(values, info, 250, 14);
        addAudio(values, info, 250, 0);
        addAudio(values, info, 250, 12);
        for (final int itag : new int[]{248, 247, 244, 243, 242, 278}) {
            addVideo(values, info, itag);
        }
        return values;
    }

    private static void addAudio(@Nonnull final List<YoutubeSabrFormat> values,
                                 @Nonnull final YoutubeSabrInfo info,
                                 final int itag, final int xtagsLength) {
        for (final YoutubeSabrFormat format : info.getFormats()) {
            final String xtags = format.getXtags();
            if (format.isAudio() && format.getItag() == itag
                    && (xtags == null ? 0 : xtags.length()) == xtagsLength) {
                values.add(format);
            }
        }
    }

    private static void addVideo(@Nonnull final List<YoutubeSabrFormat> values,
                                 @Nonnull final YoutubeSabrInfo info, final int itag) {
        for (final YoutubeSabrFormat format : info.getFormats()) {
            if (format.isVideo() && format.getItag() == itag) {
                values.add(format);
                return;
            }
        }
    }

    @Nonnull
    private static List<Object> singletonLong(final long value) {
        return Collections.<Object>singletonList(value);
    }

    @Nonnull
    private static List<Object> singletonBytes(@Nullable final byte[] value) {
        return value == null || value.length == 0
                ? Collections.emptyList() : Collections.<Object>singletonList(value);
    }
}
