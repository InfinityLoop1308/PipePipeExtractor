package org.schabi.newpipe.extractor.services.bilibili.extractors;

import java.text.SimpleDateFormat;
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

public class BilibiliRelatedInfoItemExtractor implements StreamInfoItemExtractor{

    protected final JsonObject item;
    String id = "";
    String pic = "";
    public BilibiliRelatedInfoItemExtractor(final JsonObject json, String id, String pic) {
        item = json;
        this.id = id;
        this.pic = pic;
    }
    @Override
    public String getName() throws ParsingException {
            return item.getString("part");
    }

    @Override
    public String getUrl() throws ParsingException {
        return "https://bilibili.com/" +id + "?"+ item.getLong("cid")+"&duration="+getDuration();
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
        return 0;
    }

    @Override
    public String getUploaderName() throws ParsingException {
        return null;
    }

    @Override
    public String getUploaderUrl() throws ParsingException {
        return null;
    }

    @Override
    public String getUploaderAvatarUrl() throws ParsingException {
        return null;
    }

    @Override
    public boolean isUploaderVerified() throws ParsingException {
        return false;
    }

    @Override
    public String getTextualUploadDate() throws ParsingException {
        return null;
    }

    @Override
    public DateWrapper getUploadDate() throws ParsingException {
        return null;
    }

}
