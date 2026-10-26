package org.schabi.newpipe.extractor.services.twitch.api;

import java.io.IOException;

public final class ResponseCodeIsNotSuccessException extends IOException {
    public ResponseCodeIsNotSuccessException(int code) {
        super("Response code " + code + " does not indicate success");
    }
}
