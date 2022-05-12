package org.schabi.newpipe.extractor.services.bilibili.extractors;

import java.text.SimpleDateFormat;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.Date;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.grack.nanojson.JsonArray;
import com.grack.nanojson.JsonObject;
import com.grack.nanojson.JsonParser;
import com.grack.nanojson.JsonParserException;

import org.jsoup.nodes.Element;
import org.schabi.newpipe.extractor.exceptions.ParsingException;
import org.schabi.newpipe.extractor.localization.DateWrapper;
import org.schabi.newpipe.extractor.stream.StreamInfoItemExtractor;
import org.schabi.newpipe.extractor.stream.StreamType;

public class BilibiliStreamInfoItemExtractor implements StreamInfoItemExtractor{

    protected final JsonObject item;
    public BilibiliStreamInfoItemExtractor(final JsonObject json) {
        item = json;
    }

    @Override
    public String getName() throws ParsingException {
        return item.getString("title").replace("<em class=\"keyword\">","").replace("</em>", "");
    }

    @Override
    public String getUrl() throws ParsingException {
        return item.getString("arcurl");
    }

    @Override
    public String getThumbnailUrl() throws ParsingException {
        return "https:" + item.getString("pic");
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
        String duration = item.getString("duration");
        long result = 0;
        int len = duration.split(":").length;
        try {
            result += Integer.parseInt(duration.split(":")[len-1]);
            result += Integer.parseInt(duration.split(":")[len-2]) * 60;
            result += Integer.parseInt(duration.split(":")[len-3]) * 3600;


        } catch (Exception e){
            e.printStackTrace();
        }
        return  result;
    }

    @Override
    public long getViewCount() throws ParsingException {
        return item.getLong("play");
    }

    @Override
    public String getUploaderName() throws ParsingException {
        return item.getString("author");
    }

    @Override
    public String getUploaderUrl() throws ParsingException {
        return null;
    }

    @Override
    public String getUploaderAvatarUrl() throws ParsingException {
        return item.getString("upic").replace("http:", "https:");
    }

    @Override
    public boolean isUploaderVerified() throws ParsingException {
        return false;
    }

    @Override
    public String getTextualUploadDate() throws ParsingException {
        return new SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(new Date(item.getInt("pubdate") * 1000L));
    }

    @Override
    public DateWrapper getUploadDate() throws ParsingException {
        return new DateWrapper(LocalDateTime.parse(
                getTextualUploadDate(), DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")).atOffset(ZoneOffset.ofHours(+8)));
    }

}
