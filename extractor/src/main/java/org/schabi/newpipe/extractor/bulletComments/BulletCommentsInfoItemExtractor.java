package org.schabi.newpipe.extractor.bulletComments;

import org.schabi.newpipe.extractor.InfoItemExtractor;
import org.schabi.newpipe.extractor.exceptions.ParsingException;
import org.schabi.newpipe.extractor.utils.Utils;

import java.time.Duration;

public interface BulletCommentsInfoItemExtractor extends InfoItemExtractor {
    @Override
    default String getName() throws ParsingException {
        return null;
    }

    @Override
    default String getThumbnailUrl() throws ParsingException{
        return null;
    }

    @Override
    default String getUrl() throws ParsingException {
        return null;
    }

    default String getCommentText() throws ParsingException {
        return Utils.EMPTY_STRING;
    }

    /**
     * Returns ARGB32 int. White: 0xFFFFFFFF.
     * @return ARGB32 int. White: 0xFFFFFFFF.
     */
    default int getArgbColor() throws ParsingException {
        return 0xFFFFFFFF;
    }

    default BulletCommentsInfoItem.Position getPosition() throws ParsingException {
        return BulletCommentsInfoItem.Position.REGULAR;
    }

    default double getRelativeFontSize() throws ParsingException {
        return 0.7;
    }

    /** Author name, null if unknown. */
    default String getAuthorName() throws ParsingException {
        return null;
    }

    /** Author avatar url, only provided for super chats. Null if unknown. */
    default String getAuthorAvatarUrl() throws ParsingException {
        return null;
    }

    /** Paid amount text of a super chat, null for normal comments. */
    default String getAmountText() throws ParsingException {
        return null;
    }

    default int getLastingTime() {
        return -1;
    }

    /**
     * @return how long a super chat stays pinned, in milliseconds. 0 if it is not pinned.
     */
    default long getPinnedTimeMillis() {
        return 0;
    }

    default boolean isLive() throws ParsingException {
        return false;
    }

    // Must be implemented. If that is a live stream you should at least calculate the time from the start of the stream.
    Duration getDuration() throws ParsingException;
}
