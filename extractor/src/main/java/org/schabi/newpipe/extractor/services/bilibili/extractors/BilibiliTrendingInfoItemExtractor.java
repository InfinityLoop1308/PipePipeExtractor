package org.schabi.newpipe.extractor.services.bilibili.extractors;

import org.schabi.newpipe.extractor.services.bilibili.linkHandler.BilibiliChannelLinkHandlerFactory;
import javax.annotation.Nullable;
import java.text.SimpleDateFormat;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.Date;
import java.util.Objects;

import com.grack.nanojson.JsonObject;

import org.schabi.newpipe.extractor.exceptions.ParsingException;
import org.schabi.newpipe.extractor.localization.DateWrapper;
import org.schabi.newpipe.extractor.stream.StreamInfoItemExtractor;
import org.schabi.newpipe.extractor.stream.StreamType;

public class BilibiliTrendingInfoItemExtractor implements StreamInfoItemExtractor {

    protected final JsonObject item;

    public BilibiliTrendingInfoItemExtractor(final JsonObject json) {
        item = json;
    }

    @Override
    public String getName() throws ParsingException {
        return item.getString("title");
    }

    @Override
    public String getUrl() throws ParsingException {
        return "https://www.bilibili.com/video/" + item.getString("bvid") + "?p=1";
    }

    @Override
    public String getThumbnailUrl() throws ParsingException {
        return item.getString("pic").replace("http:", "https:");
    }

    @Override
    public StreamType getStreamType() throws ParsingException {
        return StreamType.VIDEO_STREAM;
    }

    @Override
    public long getDuration() throws ParsingException {
        return item.getInt("duration");
    }

    @Override
    public long getViewCount() throws ParsingException {
        return item.getObject("stat").getInt("view");
    }

    @Nullable
    @Override
    public String getUploaderId() throws ParsingException {
        final long mid = item.getObject("owner").getLong("mid", -1);
        return mid > 0 ? String.valueOf(mid) : null;
    }

    @Override
    public String getUploaderUrl() throws ParsingException {
        final String id = getUploaderId();
        return id == null ? "" : BilibiliChannelLinkHandlerFactory.baseUrl + id;
    }

    @Override
    public String getUploaderName() throws ParsingException {
        return item.getObject("owner").getString("name");
    }

    @Override
    public String getUploaderAvatarUrl() throws ParsingException {
        return item.getObject("owner").getString("face");
    }


    @SuppressWarnings("SimpleDateFormat")
    @Override
    public String getTextualUploadDate() throws ParsingException {
        return new SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(new Date(item.getInt("pubdate") * 1000L));
    }

    @Override
    public DateWrapper getUploadDate() throws ParsingException {
        return new DateWrapper(LocalDateTime.parse(
                Objects.requireNonNull(getTextualUploadDate()), DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")).atOffset(ZoneOffset.ofHours(+8)));
    }

}
