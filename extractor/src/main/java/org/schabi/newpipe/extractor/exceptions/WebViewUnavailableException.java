package org.schabi.newpipe.extractor.exceptions;

public class WebViewUnavailableException extends ExtractionException {
    private final boolean retryable;

    public WebViewUnavailableException(final String message) {
        this(message, false, null);
    }

    public WebViewUnavailableException(final String message, final Throwable cause) {
        this(message, true, cause);
    }

    public WebViewUnavailableException(final String message, final boolean retryable,
                                       final Throwable cause) {
        super(message, cause);
        this.retryable = retryable;
    }

    /**
     * Whether retrying the failed operation may succeed, for example because the WebView runtime
     * can be initialized again. This is {@code false} when the device has no WebView provider at
     * all, in which case retrying is pointless.
     *
     * @return whether the failure may be resolved by retrying
     */
    public boolean isRetryable() {
        return retryable;
    }
}
