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
    public JsonObject json;
    public String url;

    BilibiliCommentsInfoItemExtractor(JsonObject json, String url) {
        this.json = json;
        this.url = url;
    }

    @Override
    public String getName() throws ParsingException {
        return json.getObject("member").getString("uname");
    }

    @Override
    public String getUrl() throws ParsingException {
        return url;
    }

    @Override
    public int getLikeCount() throws ParsingException {
        return json.getInt("like");
    }

    @Override
    public String getTextualLikeCount() throws ParsingException {
        return String.valueOf(getLikeCount());
    }

    @Override
    public String getCommentText() throws ParsingException {
        return json.getObject("content").getString("message");
    }

    @Override
    public String getTextualUploadDate() throws ParsingException {
        return new SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(new Date(json.getInt("ctime") * 1000L));
    }

    @Override
    public String getCommentId() throws ParsingException {
        return json.getString("rpid_str");
    }

    @Override
    public String getUploaderUrl() throws ParsingException {
        return BilibiliChannelLinkHandlerFactory.baseUrl + json.get("mid");
    }

    @Override
    public String getUploaderName() throws ParsingException {
        return json.getObject("member").getString("uname");
    }

    @Override
    public String getUploaderAvatarUrl() throws ParsingException {
        return json.getObject("member").getString("avatar").replace("http:", "https:");
    }

    @Nullable
    @Override
    public Page getReplies() throws ParsingException {
        if (json.getArray("replies") == null || json.getArray("replies").size() == 0) {
            return null;
        }
        if (json.getLong("root") == json.getLong("parent") && json.getLong("root") == json.getLong("rpid")) {
            return null;
        }
        return new Page("https://api.bilibili.com/x/v2/reply/reply?type=1&pn=1&ps=20&oid=" + json.getLong("oid") + "&root=" + json.getLong("rpid"));
    }

    @Override
    public DateWrapper getUploadDate() throws ParsingException {
        return new DateWrapper(LocalDateTime.parse(
                getTextualUploadDate(), DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")).atOffset(ZoneOffset.ofHours(+8)));
    }

    @Override
    public int getReplyCount() {
        return (int) json.getLong("rcount");
    }
}
