package org.schabi.newpipe.extractor.services.twitch;

import org.schabi.newpipe.extractor.services.twitch.data.Resolution;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Pattern;

public final class TwitchUtils {

    private static final Pattern imageSizePattern = Pattern.compile("(\\d+)x(\\d+)");

    private TwitchUtils() {
    }

    public static boolean isSuccessfulResponseCode(final int code) {
        // 2xx is success. Not all there exist but whatever
        return code >= 200 && code < 300;
    }

    public static Optional<Resolution> tryGetResolutionFromUrl(final String url) {
        final var matcher = imageSizePattern.matcher(url);
        if (matcher.find())
            return Optional.of(
                    new Resolution(Integer.parseInt(matcher.group(1)), Integer.parseInt(matcher.group(2)))
            );
        return Optional.empty();
    }

    public static <K, V> Map<K, V> mapOfNullables(Object... objects) {
        final var map = new HashMap<K, V>();
        if(objects.length % 2 != 0)
            throw new RuntimeException("Cannot create map of an uneven amount of objects");
        for(var i = 0; i < objects.length - 1; i += 2)
            //noinspection unchecked
            map.put((K)objects[i], (V)objects[i + 1]);
        return map;
    }
}
