package org.schabi.newpipe.extractor.services.niconico.extractors;

import com.grack.nanojson.JsonObject;

import org.schabi.newpipe.extractor.exceptions.ParsingException;
import org.schabi.newpipe.extractor.localization.DateWrapper;
import org.schabi.newpipe.extractor.services.niconico.NiconicoService;
import org.schabi.newpipe.extractor.stream.StreamInfoItemExtractor;
import org.schabi.newpipe.extractor.stream.StreamType;

import java.time.Instant;
import java.time.ZoneOffset;

import javax.annotation.Nullable;

public class NiconicoLiveSearchInfoItemExtractor implements StreamInfoItemExtractor {
    private final JsonObject data;

    public NiconicoLiveSearchInfoItemExtractor(final JsonObject data) {
        this.data = data;
    }

    @Override
    public String getName() throws ParsingException {
        return data.getString("title", "");
    }

    @Override
    public String getUrl() throws ParsingException {
        return data.getString("watchPageUrl", "");
    }

    @Override
    public String getThumbnailUrl() throws ParsingException {
        return data.getString("listingThumbnail", "");
    }

    @Override
    public StreamType getStreamType() throws ParsingException {
        return StreamType.LIVE_STREAM;
    }

    @Override
    public boolean isAd() throws ParsingException {
        return false;
    }

    @Override
    public long getDuration() throws ParsingException {
        return -1;
    }

    @Override
    public long getViewCount() throws ParsingException {
        return data.getObject("statistics").getLong("watchCount");
    }

    @Override
    public String getUploaderName() throws ParsingException {
        if (!data.isNull("supplier")) {
            return data.getObject("supplier").getString("name", "");
        }
        return data.getObject("socialGroup").getString("name", "");
    }

    @Override
    public String getUploaderUrl() throws ParsingException {
        if (!data.isNull("supplier")) {
            final String providerId = data.getObject("supplier")
                    .getString("programProviderId", "");
            return providerId.isEmpty() ? "" : NiconicoService.USER_URL + providerId;
        }

        final String socialGroupId = data.getObject("socialGroup").getString("id", "");
        return socialGroupId.isEmpty()
                ? "" : NiconicoService.CHANNEL_URL + "channel/" + socialGroupId;
    }

    @Nullable
    @Override
    public String getUploaderAvatarUrl() throws ParsingException {
        if (!data.isNull("supplier")) {
            return data.getObject("supplier").getObject("icons")
                    .getString("uri150x150", "");
        }
        return data.getObject("socialGroup").getString("thumbnailUrl", "");
    }

    @Override
    public boolean isUploaderVerified() throws ParsingException {
        return false;
    }

    @Nullable
    @Override
    public String getTextualUploadDate() throws ParsingException {
        final long beginTime = data.getLong("beginTime");
        return beginTime == 0 ? null
                : Instant.ofEpochSecond(beginTime).atOffset(ZoneOffset.ofHours(9)).toString();
    }

    @Nullable
    @Override
    public DateWrapper getUploadDate() throws ParsingException {
        final long beginTime = data.getLong("beginTime");
        return beginTime == 0 ? null : new DateWrapper(
                Instant.ofEpochSecond(beginTime).atOffset(ZoneOffset.ofHours(9)));
    }

    @Nullable
    @Override
    public String getShortDescription() throws ParsingException {
        return data.getString("description", "");
    }
}
