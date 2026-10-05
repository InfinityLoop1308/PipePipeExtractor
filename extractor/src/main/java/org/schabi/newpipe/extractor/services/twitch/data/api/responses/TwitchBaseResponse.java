package org.schabi.newpipe.extractor.services.twitch.data.api.responses;

import com.grack.nanojson.JsonObject;

import org.schabi.newpipe.extractor.services.twitch.data.api.TwitchExtensionsData;

import java.io.IOException;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

public abstract class TwitchBaseResponse<T> {

    @Nullable
    private final String[] errors;

    @Nonnull
    private final TwitchExtensionsData extensions;

    @Nullable
    private final T data;

    protected TwitchBaseResponse(@Nullable String[] errors,
                                 @Nonnull TwitchExtensionsData extensions,
                                 @Nonnull JsonObject data) {
        this.errors = errors;
        this.extensions = extensions;
        this.data = errors == null
                ? ParseData(data)
                : null;
    }

    protected abstract T ParseData(@Nonnull final JsonObject data);

    @Nullable
    public T getData() {
        return data;
    }

    @Nonnull
    public TwitchExtensionsData getExtensions() {
        return extensions;
    }

    @Nullable
    public String[] getErrors() {
        return errors;
    }

    public void ensureSuccess() throws IOException {
        if (errors == null || errors.length == 0)
            return;
        var messageString = String.join("; ", errors);
        throw new IOException(messageString);
    }
}
