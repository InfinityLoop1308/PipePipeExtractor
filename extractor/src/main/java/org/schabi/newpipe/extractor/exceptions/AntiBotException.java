package org.schabi.newpipe.extractor.exceptions;

/**
 * The service blocked the request as automated traffic. Nothing the user can do in-app
 * reliably fixes it. Subclasses describe blocks that can be passed by user interaction.
 */
public class AntiBotException extends ExtractionException {

    public AntiBotException(String message) {
        super(message);
    }

    public AntiBotException(String message, Throwable cause) {
        super(message, cause);
    }
}
