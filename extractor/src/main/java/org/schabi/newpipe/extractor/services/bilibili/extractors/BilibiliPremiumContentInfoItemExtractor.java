package org.schabi.newpipe.extractor.services.bilibili.extractors;

import com.grack.nanojson.JsonObject;

import org.schabi.newpipe.extractor.exceptions.ParsingException;
import org.schabi.newpipe.extractor.localization.DateWrapper;
import org.schabi.newpipe.extractor.stream.StreamInfoItemExtractor;
import org.schabi.newpipe.extractor.stream.StreamType;

import java.text.SimpleDateFormat;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.Date;
import java.util.Optional;

import javax.annotation.Nullable;

public class BilibiliPremiumContentInfoItemExtractor implements StreamInfoItemExtractor {

    private final JsonObject data;

    BilibiliPremiumContentInfoItemExtractor(JsonObject json){
        data = json;
    }
    @Override
    public String getName() throws ParsingException {
        String result = data.getString("title").replace("<em class=\"keyword\">","").replace("</em>", "");
        if(result.length() == 0){
            result = data.getString("shared_copy");
        }
        return result;
    }

    @Override
    public String getUrl() throws ParsingException {
        String result = data.getString("url");
        return result == null? data.getString("shared_url"):result;
    }

    @Override
    public String getThumbnailUrl() throws ParsingException {
        return data.getString("cover").replace("http:", "https:");
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
        return data.getLong("duration") == 0?-1: data.getLong("duration");
    }

    @Override
    public long getViewCount() throws ParsingException {
        return -1;
    }

    @Override
    public String getUploaderName() throws ParsingException {
        return data.getString("org_title").replace("<em class=\"keyword\">","").replace("</em>", "");
    }

    @Override
    public String getUploaderUrl() throws ParsingException {
        return null;
    }

    @Nullable
    @Override
    public String getUploaderAvatarUrl() throws ParsingException {
        return null;
    }

    @Override
    public boolean isUploaderVerified() throws ParsingException {
        return true;
    }

    @Override
    public String getTextualUploadDate() throws ParsingException {
        return new SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(new Date(
                Optional.of(data.getInt("pubtime")).orElse(data.getInt("pub_time")) * 1000L));
    }

    @Override
    public DateWrapper getUploadDate() throws ParsingException {
        return new DateWrapper(LocalDateTime.parse(
                getTextualUploadDate(), DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")).atOffset(ZoneOffset.ofHours(+8)));
    }
}
