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

public class NiconicoLiveHistoryInfoItemExtractor implements StreamInfoItemExtractor {
    private final JsonObject data;

    public NiconicoLiveHistoryInfoItemExtractor(final JsonObject data) {
        this.data = data;
    }

    @Override
    public String getName() throws ParsingException {
        return data.getObject("program").getString("title", "");
    }

    @Override
    public String getUrl() throws ParsingException {
        return NiconicoService.LIVE_URL + data.getObject("id").getString("value", "");
    }

    @Override
    public String getThumbnailUrl() throws ParsingException {
        final JsonObject thumbnail = data.getObject("thumbnail");
        final String listing = thumbnail.getObject("listing").getString("middle");
        if (listing != null && !listing.isEmpty()) {
            return listing;
        }
        return thumbnail.getObject("screenshot").getString("middle", "");
    }

    @Override
    public StreamType getStreamType() throws ParsingException {
        return "ON_AIR".equals(getSchedule().getString("status"))
                ? StreamType.LIVE_STREAM : StreamType.VIDEO_STREAM;
    }

    @Override
    public boolean isAd() throws ParsingException {
        return false;
    }

    @Override
    public long getDuration() throws ParsingException {
        if (getStreamType() == StreamType.LIVE_STREAM) {
            return -1;
        }
        final JsonObject schedule = getSchedule();
        return schedule.getObject("endTime").getLong("seconds")
                - schedule.getObject("beginTime").getLong("seconds");
    }

    @Override
    public long getViewCount() throws ParsingException {
        return data.getObject("statistics").getObject("viewers").getLong("value");
    }

    @Override
    public String getUploaderName() throws ParsingException {
        return data.getObject("programProvider").getString("name", "");
    }

    @Override
    public String getUploaderUrl() throws ParsingException {
        return data.getObject("programProvider").getString("profileUrl", "");
    }

    @Nullable
    @Override
    public String getUploaderAvatarUrl() throws ParsingException {
        return data.getObject("programProvider").getObject("icons").getString("uri150x150");
    }

    @Override
    public boolean isUploaderVerified() throws ParsingException {
        return false;
    }

    @Nullable
    @Override
    public String getTextualUploadDate() throws ParsingException {
        final long beginTimeSeconds = getBeginTimeSeconds();
        if (beginTimeSeconds <= 0) {
            return null;
        }
        return Instant.ofEpochSecond(beginTimeSeconds).atOffset(ZoneOffset.ofHours(9)).toString();
    }

    @Nullable
    @Override
    public DateWrapper getUploadDate() throws ParsingException {
        final long beginTimeSeconds = getBeginTimeSeconds();
        if (beginTimeSeconds <= 0) {
            return null;
        }
        return new DateWrapper(
                Instant.ofEpochSecond(beginTimeSeconds).atOffset(ZoneOffset.ofHours(9)));
    }

    private JsonObject getSchedule() {
        return data.getObject("program").getObject("schedule");
    }

    private long getBeginTimeSeconds() {
        return getSchedule().getObject("beginTime").getLong("seconds");
    }
}
