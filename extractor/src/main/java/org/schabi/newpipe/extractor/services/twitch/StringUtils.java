package org.schabi.newpipe.extractor.services.twitch;

import java.nio.charset.Charset;

public final class StringUtils {
    public static byte[] stringToBytes(final String string) {
        return string.getBytes(Charset.defaultCharset());
    }
}
