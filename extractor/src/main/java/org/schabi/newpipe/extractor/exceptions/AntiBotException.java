package org.schabi.newpipe.extractor.exceptions;

import javax.annotation.Nullable;

/**
 * The service blocked the request as automated traffic. Nothing the user can do in-app
 * reliably fixes it. Subclasses describe blocks that can be passed by user interaction.
 */
public class AntiBotException extends ExtractionException {
    /** Id of the service that blocked the request, or -1 when the thrower does not know it. */
    private final int serviceId;
    /** Url of the blocked request, or null when the thrower does not know it. */
    @Nullable
    private final String url;

    public AntiBotException(final int serviceId, final String message,
                            @Nullable final String url, @Nullable final Throwable cause) {
        super(message, cause);
        this.serviceId = serviceId;
        this.url = url;
    }

    public AntiBotException(final int serviceId, final String message) {
        this(serviceId, message, null, null);
    }

    public AntiBotException(final int serviceId, final String message, final Throwable cause) {
        this(serviceId, message, null, cause);
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

    /**
     * @return the url of the blocked request, which names the site that blocked it even when
     * it does not belong to the service (e.g. SponsorBlock while playing a YouTube video)
     */
    @Nullable
    public String getUrl() {
        return url;
    }
}
