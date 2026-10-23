package org.schabi.newpipe.extractor.services.twitch.api;

import com.grack.nanojson.JsonArray;
import com.grack.nanojson.JsonObject;
import com.grack.nanojson.JsonParser;
import com.grack.nanojson.JsonParserException;

import java.util.LinkedList;

import javax.annotation.Nonnull;

// Helpers so I don't have to catch the JsonParserException in every lambda in the api client
public final class TwitchJsonHelper {
    public static JsonObject parseJsonObjectFromString(@Nonnull final String jsonString) {
        try {
            return JsonParser.object().from(jsonString);
        } catch (JsonParserException e) {
            throw new RuntimeException(e);
        }
    }

    public static JsonArray parseJsonArrayFromString(@Nonnull final String jsonString) {
        try {
            return JsonParser.array().from(jsonString);
        } catch (JsonParserException e) {
            throw new RuntimeException(e);
        }
    }

    public static JsonObject createJsonObjectFromArray(final JsonArray array) {
        var totalDuration = 0L;
        final var data = new JsonObject();
        final var errors = new LinkedList<>();
        for (var i = 0; i < array.size(); i++) {
            final var arrayElement = (JsonObject) array.get(i);
            data.put("synthetic-" + i, arrayElement.getObject("data"));
            totalDuration += arrayElement.getObject("extensions").getLong("durationMilliseconds");
            if (arrayElement.has("errors"))
                errors.addAll(arrayElement.getArray("errors"));
        }
        final var fullObject = new JsonObject();
        fullObject.put("data", data);
        final var fullExtensions = new JsonObject();
        fullExtensions.put("durationMilliseconds", totalDuration);
        fullObject.put("extensions", fullExtensions);
        if (!errors.isEmpty())
            fullObject.put("errors", errors);
        return fullObject;
    }
}
