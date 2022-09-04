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
import org.schabi.newpipe.extractor.services.bilibili.utils;
import org.schabi.newpipe.extractor.stream.StreamInfoItemExtractor;
import org.schabi.newpipe.extractor.stream.StreamType;

public class BilibiliRelatedInfoItemExtractor implements StreamInfoItemExtractor{

    protected final JsonObject item;
    String id = "";
    String pic = "";
    String p = "1";
    String type = "multiP";
    public BilibiliRelatedInfoItemExtractor(final JsonObject json, String id, String pic, String p) {
        item = json;
        this.id = id;
        this.pic = pic;
        this.p = p;
    }
    public BilibiliRelatedInfoItemExtractor(final JsonObject json) throws ParsingException {
        item = json;
        type = "related";
        id = item.getString("bvid").equals("")? new utils().av2bv(item.getLong("aid")):item.getString("bvid");
        pic = item.getString("pic").replace("http", "https");
    }
    @Override
    public String getName() throws ParsingException {
        if(type.equals("related")){
            return item.getString("title");
        }
            return item.getString("part");
    }

    @Override
    public String getUrl() throws ParsingException {
        return "https://bilibili.com/" +id + "?p="+ p ;
    }

    @Override
    public String getThumbnailUrl() throws ParsingException {
        return pic;
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
        return item.getLong("duration");
    }

    @Override
    public long getViewCount() throws ParsingException {
        if(type.equals("multiP"))return 0;
        return item.getObject("stat").getLong("view");
    }

    @Override
    public String getUploaderName() throws ParsingException {
        if(type.equals("multiP"))return null;
        return item.getObject("owner").getString("name");
    }

    @Override
    public String getUploaderUrl() throws ParsingException {
        return null;
    }

    @Override
    public String getUploaderAvatarUrl() throws ParsingException {
        if(type.equals("multiP"))return null;
        return item.getObject("owner").getString("face").replace("http", "https");
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
