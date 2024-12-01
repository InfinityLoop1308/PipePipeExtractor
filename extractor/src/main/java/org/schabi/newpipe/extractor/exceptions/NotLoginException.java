package org.schabi.newpipe.extractor.exceptions;

public class NotLoginException extends ExtractionException {

    public NotLoginException(String message) {
        super(message);
    }

    public NotLoginException(Throwable cause) {
        super(cause);
    }

    public NotLoginException(String message, Throwable cause) {
        super(message, cause);
    }
}
