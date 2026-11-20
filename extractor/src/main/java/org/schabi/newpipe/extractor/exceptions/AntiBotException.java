package org.schabi.newpipe.extractor.exceptions;

/**
 * The service blocked the request as automated traffic. Nothing the user can do in-app
 * reliably fixes it. Subclasses describe blocks that can be passed by user interaction.
 */
public class AntiBotException extends ExtractionException {
    /** Id of the service that blocked the request, or -1 when the thrower does not know it. */
    private final int serviceId;

    public AntiBotException(final int serviceId, final String message) {
        super(message);
        this.serviceId = serviceId;
    }

    public AntiBotException(final int serviceId, final String message, final Throwable cause) {
        super(message, cause);
        this.serviceId = serviceId;
    }

    public AntiBotException(final String message) {
        this(-1, message);
    }

    public AntiBotException(final String message, final Throwable cause) {
        this(-1, message, cause);
    }

    public int getServiceId() {
        return serviceId;
    }
}
