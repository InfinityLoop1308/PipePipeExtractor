package org.schabi.newpipe.extractor.comments;

import java.io.Serializable;

/**
 * A playable audio attachment associated with a comment.
 *
 * <p>The URL is a stable service URL which can be resolved through the normal stream extraction
 * API. It is deliberately not a short-lived CDN URL.</p>
 */
public final class CommentAudioAttachment implements Serializable {
    private final String mediaId;
    private final String url;
    private final long durationMillis;
    private final String transcript;
    private final boolean playable;
    private final String unavailableText;

    public CommentAudioAttachment(final String mediaId, final String url,
                                  final long durationMillis, final String transcript,
                                  final boolean playable, final String unavailableText) {
        this.mediaId = mediaId;
        this.url = url;
        this.durationMillis = durationMillis;
        this.transcript = transcript;
        this.playable = playable;
        this.unavailableText = unavailableText;
    }

    public String getMediaId() {
        return mediaId;
    }

    public String getUrl() {
        return url;
    }

    /** Duration in milliseconds, or {@code -1} when YouTube did not provide it. */
    public long getDurationMillis() {
        return durationMillis;
    }

    public String getTranscript() {
        return transcript;
    }

    public boolean isPlayable() {
        return playable;
    }

    public String getUnavailableText() {
        return unavailableText;
    }
}
