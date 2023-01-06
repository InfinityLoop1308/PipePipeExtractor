package org.schabi.newpipe.extractor.bulletComments;

import org.schabi.newpipe.extractor.InfoItemExtractor;
import org.schabi.newpipe.extractor.exceptions.ParsingException;
import org.schabi.newpipe.extractor.utils.Utils;

import java.time.Duration;

public interface BulletCommentsInfoItemExtractor extends InfoItemExtractor {
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

    default Duration getDuration() throws ParsingException {
        return Duration.ZERO;
    }

    default int getLastingTime() {
        return -1;
    }
}
