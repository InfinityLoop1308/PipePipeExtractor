package org.schabi.newpipe.extractor.services.bilibili.extractors;

import com.grack.nanojson.JsonObject;
import org.schabi.newpipe.extractor.Page;
import org.schabi.newpipe.extractor.comments.CommentsInfoItemExtractor;
import org.schabi.newpipe.extractor.exceptions.ParsingException;
import org.schabi.newpipe.extractor.localization.DateWrapper;
import org.schabi.newpipe.extractor.services.bilibili.linkHandler.BilibiliChannelLinkHandlerFactory;

import javax.annotation.Nullable;
import java.text.SimpleDateFormat;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.Date;

public class BilibiliCommentsInfoItemExtractor implements CommentsInfoItemExtractor {
    public JsonObject data;
    public String url;

    BilibiliCommentsInfoItemExtractor(JsonObject json, String url) {
        this.data = json;
        this.url = url;
    }

    @Override
    public String getName() throws ParsingException {
        return data.getObject("member").getString("uname");
    }

    @Override
    public String getUrl() throws ParsingException {
        return url;
    }

    @Override
    public int getLikeCount() throws ParsingException {
        return data.getInt("like");
    }

    @Override
    public String getTextualLikeCount() throws ParsingException {
        return String.valueOf(getLikeCount());
    }

    @Override
    public String getCommentText() throws ParsingException {
        return data.getObject("content").getString("message");
    }

    @SuppressWarnings("SimpleDateFormat")
    @Override
    public String getTextualUploadDate() throws ParsingException {
        return new SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(new Date(data.getInt("ctime") * 1000L));
    }

    @Override
    public String getCommentId() throws ParsingException {
        return data.getString("rpid_str");
    }

    @Override
    public String getUploaderUrl() throws ParsingException {
        return BilibiliChannelLinkHandlerFactory.baseUrl + data.get("mid");
    }

    @Override
    public String getUploaderName() throws ParsingException {
        return data.getObject("member").getString("uname");
    }

    @Override
    public String getUploaderAvatarUrl() throws ParsingException {
        return data.getObject("member").getString("avatar").replace("http:", "https:");
    }

    @Nullable
    @Override
    public Page getReplies() throws ParsingException {
        if (data.getArray("replies") == null || data.getArray("replies").size() == 0) {
            return null;
        }
        if (data.getLong("root") == data.getLong("parent") && data.getLong("root") == data.getLong("rpid")) {
            return null;
        }
        return new Page("https://api.bilibili.com/x/v2/reply/reply?type=1&pn=1&ps=20&oid=" + data.getLong("oid") + "&root=" + data.getLong("rpid"));
    }

    @Override
    public DateWrapper getUploadDate() throws ParsingException {
        return new DateWrapper(LocalDateTime.parse(
                getTextualUploadDate(), DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")).atOffset(ZoneOffset.ofHours(+8)));
    }

    @Override
    public int getReplyCount() {
        return (int) data.getLong("rcount");
    }
}
