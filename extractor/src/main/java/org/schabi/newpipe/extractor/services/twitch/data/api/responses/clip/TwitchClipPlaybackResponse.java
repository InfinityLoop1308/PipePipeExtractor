package org.schabi.newpipe.extractor.services.twitch.data.api.responses.clip;

import com.grack.nanojson.JsonObject;

import org.jetbrains.annotations.NotNull;
import org.schabi.newpipe.extractor.services.twitch.data.api.TwitchExtensionsData;
import org.schabi.newpipe.extractor.services.twitch.data.api.responses.TwitchBaseResponse;

import java.net.URLEncoder;
import java.nio.charset.Charset;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

public final class TwitchClipPlaybackResponse extends TwitchBaseResponse<TwitchClipPlaybackResponseInner[]> {

    public TwitchClipPlaybackResponse(@Nullable String[] errors,
                                      @Nonnull TwitchExtensionsData extensions,
                                      @Nonnull JsonObject data) {
        super(errors, extensions, data);
    }

    @Override
    protected TwitchClipPlaybackResponseInner[] ParseData(@NotNull JsonObject data) {
        final var clip = data.getObject("clip");
        final var accessToken = clip.getObject("playbackAccessToken");
        final var signature = accessToken.getString("signature");
        final var value = accessToken.getString("value");
        final var aspectRatio = clip.getArray("assets").getObject(0).getFloat("aspectRatio");
        return clip.getArray("videoQualities").stream().map(ob -> {
            final var obj = (JsonObject) ob;
            final var clipHeight = Integer.parseInt(obj.getString("quality"));
            final var clipWidth = Math.round(clipHeight * aspectRatio);
            return new TwitchClipPlaybackResponseInner(
                    obj.getString("sourceURL") + "?token=" + URLEncoder.encode(value,
                            Charset.defaultCharset()) + "&sig=" + signature,
                    clipHeight,
                    clipWidth
            );
        }).toArray(TwitchClipPlaybackResponseInner[]::new);
    }
}
