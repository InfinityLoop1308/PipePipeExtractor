package org.schabi.newpipe.extractor.services.niconico.extractors;

import com.grack.nanojson.JsonObject;

import org.schabi.newpipe.extractor.exceptions.ParsingException;
import org.schabi.newpipe.extractor.localization.DateWrapper;
import org.schabi.newpipe.extractor.stream.StreamInfoItemExtractor;
import org.schabi.newpipe.extractor.stream.StreamType;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.regex.Pattern;

import javax.annotation.Nullable;

public class NiconicoSeriesContentItemExtractor implements StreamInfoItemExtractor {
    private final String name;
    private final String url;
    private final String avatar;
    private JsonObject data;
    NiconicoSeriesContentItemExtractor(JsonObject data, String name, String url, String avatar){
        this.data = data;
        this.name = name;
        this.url = url;
        this.avatar = avatar;
    }
    @Override
    public String getName() throws ParsingException {
        return data.getString("name");
    }

    @Override
    public String getUrl() throws ParsingException {
        return data.getString("url");
    }

    @Override
    public String getThumbnailUrl() throws ParsingException {
        return data.getArray("thumbnailUrl").getString(0);
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
        return Long.parseLong(data.getString("duration").split("PT")[1].split("S")[0]);
    }

    @Override
    public long getViewCount() throws ParsingException {
        return data.getArray("interactionStatistic").getObject(0).getLong("userInteractionCount");
    }

    @Override
    public String getUploaderName() throws ParsingException {
        return name;
    }

    @Override
    public String getUploaderUrl() throws ParsingException {
        return url;
    }

    @Nullable
    @Override
    public String getUploaderAvatarUrl() throws ParsingException {
        return avatar;
    }

    @Override
    public boolean isUploaderVerified() throws ParsingException {
        return false;
    }

    @Nullable
    @Override
    public String getTextualUploadDate() throws ParsingException {
        return data.getString("uploadDate");
    }

    @Nullable
    @Override
    public DateWrapper getUploadDate() throws ParsingException {
        return new DateWrapper(LocalDateTime.parse(
                getTextualUploadDate().split(Pattern.quote("+"))[0].replace("T"," "), DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")).atOffset(ZoneOffset.ofHours(9)));
    }
}
