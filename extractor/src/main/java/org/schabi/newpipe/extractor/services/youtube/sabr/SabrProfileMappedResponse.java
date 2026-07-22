package org.schabi.newpipe.extractor.services.youtube.sabr;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.List;

/** Per-response normalized control values produced without exposing media payloads. */
final class SabrProfileMappedResponse {
    @Nonnull private final SabrResponseStatePatch statePatch;
    @Nullable private final String redirectUrl;
    @Nullable private final String errorDetails;
    private final int protectionStatus;
    private final int backoffMs;
    private final boolean reloadRequested;

    private SabrProfileMappedResponse(@Nonnull final Builder builder,
                                      @Nonnull final SabrDecodedResponse response) {
        final SabrNextRequestPolicy builtin = response.getNextRequestPolicy();
        final SabrNextRequestPolicy nextRequest = builder.hasNextRequestOverride
                ? SabrNextRequestPolicy.normalized(
                builder.targetAudioReadaheadMs(builtin),
                builder.targetVideoReadaheadMs(builtin),
                builder.maxTimeSinceLastRequestMs(builtin),
                builder.backoffMs(builtin),
                builder.minAudioReadaheadMs(builtin),
                builder.minVideoReadaheadMs(builtin),
                builder.playbackCookie(builtin), builder.videoId(builtin)) : builtin;
        final SabrResponseStatePatch.Builder patch = SabrResponseStatePatch.builder()
                .setNextRequestPolicy(nextRequest)
                .setContextSendingPolicy(response.getSabrContextSendingPolicy());
        for (final SabrLiveMetadata value : response.getLiveMetadata()) {
            patch.addLiveMetadata(value);
        }
        for (final SabrFormatInitializationMetadata value
                : response.getFormatInitializationMetadata()) {
            patch.addFormatMetadata(value);
        }
        for (final SabrMediaHeader value : response.getMediaHeaders()) {
            patch.addMediaHeader(value);
        }
        for (final SabrContextUpdate value : response.getSabrContextUpdates()) {
            patch.addContextUpdate(value);
        }
        statePatch = patch.build();
        redirectUrl = builder.redirectUrl != null ? builder.redirectUrl : response.getRedirectUrl();
        final SabrError builtinError = response.getSabrErrorDetails();
        final String builtinErrorType = builtinError == null ? null : builtinError.getType();
        final int builtinErrorCode = builtinError == null ? 0 : builtinError.getCode();
        final String errorType = builder.errorType != null ? builder.errorType : builtinErrorType;
        final int errorCode = builder.errorCode != null ? builder.errorCode : builtinErrorCode;
        errorDetails = errorType == null && errorCode == 0 ? null
                : "type=" + (errorType == null ? "null" : errorType) + ", code=" + errorCode;
        protectionStatus = builder.protectionStatus != null
                ? builder.protectionStatus : response.getStreamProtectionStatus();
        backoffMs = builder.backoffMs != null ? builder.backoffMs
                : Math.max(0, response.getBackoffTimeMs());
        reloadRequested = builder.reloadRequested != null
                ? builder.reloadRequested : response.isReloadRequested();
    }

    @Nonnull SabrResponseStatePatch getStatePatch() { return statePatch; }
    @Nullable String getRedirectUrl() { return redirectUrl; }
    @Nullable String getErrorDetails() { return errorDetails; }
    int getProtectionStatus() { return protectionStatus; }
    int getBackoffMs() { return backoffMs; }
    boolean isReloadRequested() { return reloadRequested; }

    static final class Builder {
        @Nullable private Integer targetAudioReadaheadMs;
        @Nullable private Integer targetVideoReadaheadMs;
        @Nullable private Integer maxTimeSinceLastRequestMs;
        @Nullable private Integer backoffMs;
        @Nullable private Integer minAudioReadaheadMs;
        @Nullable private Integer minVideoReadaheadMs;
        @Nullable private byte[] playbackCookie;
        @Nullable private String videoId;
        @Nullable private String redirectUrl;
        @Nullable private String errorType;
        @Nullable private Integer errorCode;
        @Nullable private Integer protectionStatus;
        @Nullable private Boolean reloadRequested;
        private boolean hasNextRequestOverride;

        void apply(@Nonnull final SabrProfileResponseMapping.Target target,
                   @Nonnull final SabrProfileWireValue value) throws SabrProtocolException {
            switch (target) {
                case NEXT_REQUEST_TARGET_AUDIO_READAHEAD_MS:
                    targetAudioReadaheadMs = checkedInt(value.asLong(), target);
                    hasNextRequestOverride = true;
                    break;
                case NEXT_REQUEST_TARGET_VIDEO_READAHEAD_MS:
                    targetVideoReadaheadMs = checkedInt(value.asLong(), target);
                    hasNextRequestOverride = true;
                    break;
                case NEXT_REQUEST_MAX_TIME_SINCE_LAST_REQUEST_MS:
                    maxTimeSinceLastRequestMs = checkedInt(value.asLong(), target);
                    hasNextRequestOverride = true;
                    break;
                case NEXT_REQUEST_BACKOFF_MS:
                    backoffMs = checkedInt(value.asLong(), target);
                    hasNextRequestOverride = true;
                    break;
                case NEXT_REQUEST_MIN_AUDIO_READAHEAD_MS:
                    minAudioReadaheadMs = checkedInt(value.asLong(), target);
                    hasNextRequestOverride = true;
                    break;
                case NEXT_REQUEST_MIN_VIDEO_READAHEAD_MS:
                    minVideoReadaheadMs = checkedInt(value.asLong(), target);
                    hasNextRequestOverride = true;
                    break;
                case NEXT_REQUEST_PLAYBACK_COOKIE:
                    playbackCookie = value.asBytes();
                    hasNextRequestOverride = true;
                    break;
                case NEXT_REQUEST_VIDEO_ID:
                    videoId = value.asString();
                    hasNextRequestOverride = true;
                    break;
                case REDIRECT_URL:
                    redirectUrl = value.asString();
                    break;
                case ERROR_TYPE:
                    errorType = value.asString();
                    break;
                case ERROR_CODE:
                    errorCode = checkedInt(value.asLong(), target);
                    break;
                case RELOAD_REQUESTED:
                    reloadRequested = value.asBoolean();
                    break;
                case PROTECTION_STATUS:
                    protectionStatus = checkedInt(value.asLong(), target);
                    break;
                default:
                    break;
            }
        }

        @Nonnull
        SabrProfileMappedResponse build(@Nonnull final SabrDecodedResponse response) {
            return new SabrProfileMappedResponse(this, response);
        }

        private static int checkedInt(final long value,
                                      @Nonnull final SabrProfileResponseMapping.Target target)
                throws SabrProtocolException {
            if (value < Integer.MIN_VALUE || value > Integer.MAX_VALUE) {
                throw new SabrProtocolException("SABR profile integer overflow: " + target);
            }
            return (int) value;
        }

        private int targetAudioReadaheadMs(@Nullable final SabrNextRequestPolicy builtin) {
            return targetAudioReadaheadMs != null ? targetAudioReadaheadMs
                    : builtin == null ? -1 : builtin.getTargetAudioReadaheadMs();
        }
        private int targetVideoReadaheadMs(@Nullable final SabrNextRequestPolicy builtin) {
            return targetVideoReadaheadMs != null ? targetVideoReadaheadMs
                    : builtin == null ? -1 : builtin.getTargetVideoReadaheadMs();
        }
        private int maxTimeSinceLastRequestMs(@Nullable final SabrNextRequestPolicy builtin) {
            return maxTimeSinceLastRequestMs != null ? maxTimeSinceLastRequestMs
                    : builtin == null ? -1 : builtin.getMaxTimeSinceLastRequestMs();
        }
        private int backoffMs(@Nullable final SabrNextRequestPolicy builtin) {
            return backoffMs != null ? backoffMs
                    : builtin == null ? -1 : builtin.getBackoffTimeMs();
        }
        private int minAudioReadaheadMs(@Nullable final SabrNextRequestPolicy builtin) {
            return minAudioReadaheadMs != null ? minAudioReadaheadMs
                    : builtin == null ? -1 : builtin.getMinAudioReadaheadMs();
        }
        private int minVideoReadaheadMs(@Nullable final SabrNextRequestPolicy builtin) {
            return minVideoReadaheadMs != null ? minVideoReadaheadMs
                    : builtin == null ? -1 : builtin.getMinVideoReadaheadMs();
        }
        @Nullable private byte[] playbackCookie(@Nullable final SabrNextRequestPolicy builtin) {
            return playbackCookie != null ? playbackCookie
                    : builtin == null ? null : builtin.getPlaybackCookie();
        }
        @Nullable private String videoId(@Nullable final SabrNextRequestPolicy builtin) {
            return videoId != null ? videoId : builtin == null ? null : builtin.getVideoId();
        }
    }
}
