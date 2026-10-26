package org.schabi.newpipe.extractor.services.twitch;

import java.util.function.Supplier;

public final class Assertions {

    public static void assertThat(Supplier<Boolean> toValidate) {
        if (toValidate.get())
            return;
        throw new AssertionFailedException();
    }

}
