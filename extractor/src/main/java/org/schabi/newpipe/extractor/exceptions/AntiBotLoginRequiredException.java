package org.schabi.newpipe.extractor.exceptions;

/**
 * The service blocked the request as automated traffic and lets it through after logging in.
 * Use {@link NeedLoginException} when the content itself requires an account.
 */
public class AntiBotLoginRequiredException extends AntiBotException {

    public AntiBotLoginRequiredException(String message) {
        super(message);
    }

    public AntiBotLoginRequiredException(String message, Throwable cause) {
        super(message, cause);
    }
}
