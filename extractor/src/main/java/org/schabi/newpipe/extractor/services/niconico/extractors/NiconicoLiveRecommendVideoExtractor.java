package org.schabi.newpipe.extractor.services.niconico.extractors;

import com.grack.nanojson.JsonObject;

import org.schabi.newpipe.extractor.exceptions.ParsingException;
import org.schabi.newpipe.extractor.localization.DateWrapper;
import org.schabi.newpipe.extractor.services.niconico.NiconicoService;
import org.schabi.newpipe.extractor.stream.StreamInfoItemExtractor;
import org.schabi.newpipe.extractor.stream.StreamType;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.regex.Pattern;

import javax.annotation.Nullable;

public class NiconicoLiveRecommendVideoExtractor implements StreamInfoItemExtractor {
    private final JsonObject data;

    public NiconicoLiveRecommendVideoExtractor(JsonObject data) {
        this.data = data;
    }

    @Override
    public String getName() throws ParsingException {
        return data.getObject("content_meta").getString("title");
    }

    @Override
    public String getUrl() throws ParsingException {
        return NiconicoService.WATCH_URL + data.getString("id");
    }

    @Override
    public String getThumbnailUrl() throws ParsingException {
        return data.getObject("content_meta").getObject("thumbnail_url").getString("normal");
    }

    @Override
    public StreamType getStreamType() throws ParsingException {
        return StreamType.VIDEO_STREAM;
    }

    @Override
    public boolean isAd() throws ParsingException {
        return false;
    }

    @Override
    public long getDuration() throws ParsingException {
        return data.getObject("content_meta").getLong("length_seconds");
    }

    @Override
    public long getViewCount() throws ParsingException {
        return data.getObject("content_meta").getLong("view_count");
    }

    @Override
    public String getUploaderName() throws ParsingException {
        String result = "user/" + data.getObject("content_meta").getString("author_id");
        if(data.getObject("content_meta").getString("author_id") == null){
            result = data.getObject("content_meta").getObject("threads").getObject("channel").getString("channel_id");
        }
        return result;
    }

    @Override
    public String getUploaderUrl() throws ParsingException {
        return NiconicoService.USER_URL+ data.getObject("content_meta").getString("author_id");
    }

    @Nullable
    @Override
    public String getUploaderAvatarUrl() throws ParsingException {
        return null;
    }

    @Override
    public boolean isUploaderVerified() throws ParsingException {
        return false;
    }

    @Nullable
    @Override
    public String getTextualUploadDate() throws ParsingException {
        return data.getObject("content_meta").getString("upload_time");
    }

    @Nullable
    @Override
    public DateWrapper getUploadDate() throws ParsingException {
        return new DateWrapper(LocalDateTime.parse(
                getTextualUploadDate().split(Pattern.quote("+"))[0].replace("T"," "), DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")).atOffset(ZoneOffset.ofHours(9)));
    }
}
