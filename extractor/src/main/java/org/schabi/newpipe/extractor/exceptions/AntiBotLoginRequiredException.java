package org.schabi.newpipe.extractor.exceptions;

/**
 * The service blocked the request as automated traffic and lets it through after logging in.
 * Use {@link NeedLoginException} when the content itself requires an account.
 */
public class AntiBotLoginRequiredException extends AntiBotException {

    public AntiBotLoginRequiredException(final int serviceId, final String message) {
        super(serviceId, message);
    }

    public AntiBotLoginRequiredException(final int serviceId, final String message,
                                         final Throwable cause) {
        super(serviceId, message, cause);
    }
}
