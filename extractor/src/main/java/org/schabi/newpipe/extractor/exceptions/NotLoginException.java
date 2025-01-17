package org.schabi.newpipe.extractor.exceptions;

public class NotLoginException extends ParsingException {

    public NotLoginException(String message) {
        super(message);
    }

    public NotLoginException(String message, Throwable cause) {
        super(message, cause);
    }
}
