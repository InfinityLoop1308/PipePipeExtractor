package org.schabi.newpipe.extractor.playlist;

import org.schabi.newpipe.extractor.InfoItemExtractor;
import org.schabi.newpipe.extractor.exceptions.ParsingException;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

public interface PlaylistInfoItemExtractor extends InfoItemExtractor {

    /**
     * Get the uploader name
     * @return the uploader name
     */
    String getUploaderName() throws ParsingException;

    /**
     * Get the service-specific id of the uploader's channel (e.g. UCxxxx on YouTube).
     *
     * @return the uploader id or {@code null} if unavailable
     */
    @Nullable
    default String getUploaderId() throws ParsingException {
        return null;
    }

    /**
     * Get the url of the uploader's channel.
     *
     * @return the uploader url or {@code null} if unavailable
     */
    @Nullable
    default String getUploaderUrl() throws ParsingException {
        return null;
    }

    /**
     * Get the number of streams
     * @return the number of streams
     */
    long getStreamCount() throws ParsingException;

    /**
     * @return the type of this playlist, see {@link PlaylistInfo.PlaylistType} for a description
     *         of types. If not overridden always returns {@link PlaylistInfo.PlaylistType#NORMAL}.
     */
    @Nonnull
    default PlaylistInfo.PlaylistType getPlaylistType() throws ParsingException {
        return PlaylistInfo.PlaylistType.NORMAL;
    }
}
