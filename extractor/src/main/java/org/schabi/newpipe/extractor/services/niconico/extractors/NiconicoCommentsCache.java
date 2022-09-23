package org.schabi.newpipe.extractor.services.niconico.extractors;

import java.io.IOException;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

import org.schabi.newpipe.extractor.downloader.Downloader;
import org.schabi.newpipe.extractor.exceptions.ExtractionException;
import org.schabi.newpipe.extractor.exceptions.ParsingException;

import com.grack.nanojson.JsonArray;
import com.grack.nanojson.JsonObject;
import com.grack.nanojson.JsonParser;
import com.grack.nanojson.JsonParserException;
import com.grack.nanojson.JsonWriter;

import edu.umd.cs.findbugs.annotations.NonNull;

public class NiconicoCommentsCache {
    String id;
    JsonObject[] comments;

    public JsonObject[] getComments(@NonNull final JsonObject watch,
                                    final Downloader downloader,
                                    final String lastId) throws ExtractionException {
        if (Objects.equals(this.id, lastId)) {
            return comments;
        }
        this.id = lastId;

        final JsonArray threads = watch.getObject("comment").getArray("threads");
        final JsonObject thread = threads.getObject(1);
        final HashMap<String, Object> params = new HashMap<String, Object>() {
            {
                put("thread", thread.getInt("id"));
                put("version", "20090904");
                put("scores", "1");
                put("fork", thread.getInt("fork"));
                put("language", 0);
                put("res_from", "-1000");
            }
        };
        StringBuilder url = new StringBuilder(thread.getString("server") + "/api.json/thread?");
        for (final String key : params.keySet()) {
            url.append(key).append("=").append(params.get(key)).append("&");
        }
        url = new StringBuilder(url.substring(0, url.length() - 1));

        final String commentResponse;
        try {
            commentResponse = downloader.get(url.toString()).responseBody();
        } catch (IllegalArgumentException | IOException e) {
            throw new ExtractionException(
                    "Could not get comments. Url: "
                            + url
                            + ", Thread: "
                            + JsonWriter.string(thread), e);
        }

        try {
            final JsonArray result = JsonParser.array().from(commentResponse);
            final List<JsonObject> commentsList = Arrays.stream(result.toArray())
                    .map(s -> (JsonObject) s)
                    .filter(s -> s.has("chat"))
                    .map(s -> s.getObject("chat"))
                    .collect(Collectors.toList());
            // Reverse the order to show comments in order of newest to oldest.
            Collections.reverse(commentsList);
            comments = commentsList.toArray(new JsonObject[0]);
            return comments;
        } catch (final JsonParserException e) {
            throw new ParsingException("Could not parse comment json data. Response: "
                    + commentResponse, e);
        }
    }
}
