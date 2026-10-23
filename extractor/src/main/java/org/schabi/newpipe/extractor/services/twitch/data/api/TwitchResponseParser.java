package org.schabi.newpipe.extractor.services.twitch.data.api;

import com.grack.nanojson.JsonObject;

import org.schabi.newpipe.extractor.services.twitch.data.api.responses.TwitchBaseResponse;

import java.lang.reflect.InvocationTargetException;

public final class TwitchResponseParser {
    public static <T extends TwitchBaseResponse<?>> T parseFromJson(JsonObject object,
                                                                    Class<T> clazz) {
        try {
            final var hasErrored = object.has("errors");
            final var constructor = clazz.getConstructors()[0];
            final var extensions = new TwitchExtensionsData(
                    object.getObject("extensions").getLong("durationMilliseconds")
            );
            if (hasErrored) {
                final var errors =
                        object.getArray("errors").stream().map(elem -> ((JsonObject) elem).getString("message"));
                //noinspection unchecked
                return (T) constructor.newInstance(
                        errors.toArray(String[]::new),
                        extensions,
                        object.getObject("data")
                );
            } else {
                //noinspection unchecked
                return (T) constructor.newInstance(
                        null,
                        extensions,
                        object.getObject("data")
                );
            }
        } catch (ArrayIndexOutOfBoundsException e) {
            throw new RuntimeException("No constructor found for type " + clazz);
        } catch (InstantiationException | IllegalAccessException | InvocationTargetException e) {
            throw new RuntimeException(e);
        }
    }
}
