package org.schabi.newpipe.extractor.services.twitch.data.api.requests;

import com.grack.nanojson.JsonObject;

import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.Map;

import javax.annotation.Nonnull;

public class TwitchGqlRequestBuilder {

    @Nonnull
    private final String sha256Hash;
    @Nonnull
    private final String operationName;

    private final Map<String, Object> variables = new HashMap<>();

    public TwitchGqlRequestBuilder(@Nonnull final String sha256Hash,
                                   @Nonnull final String operationName) {
        this.sha256Hash = sha256Hash;
        this.operationName = operationName;
    }

    public TwitchGqlRequestBuilder withVariable(@Nonnull final String key,
                                                @Nullable final Object value) {
        variables.put(key, value);
        return this;
    }

    public JsonObject build() {
        final var baseObject = new JsonObject();

        final var extensionsObject = new JsonObject();
        final var persistedQueryObject = new JsonObject();

        persistedQueryObject.put("sha256Hash", sha256Hash);
        persistedQueryObject.put("version", 1);

        extensionsObject.put("persistedQuery", persistedQueryObject);

        baseObject.put("extensions", extensionsObject);
        baseObject.put("operationName", operationName);
        baseObject.put("variables", new JsonObject(variables));

        return baseObject;
    }
}
