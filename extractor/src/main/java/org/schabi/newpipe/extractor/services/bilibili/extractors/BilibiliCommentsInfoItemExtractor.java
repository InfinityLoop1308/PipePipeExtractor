package org.schabi.newpipe.extractor.services.bilibili.extractors;

import com.grack.nanojson.JsonObject;

import org.schabi.newpipe.extractor.Page;
import org.schabi.newpipe.extractor.comments.CommentsInfoItemExtractor;
import org.schabi.newpipe.extractor.exceptions.ParsingException;
import org.schabi.newpipe.extractor.localization.DateWrapper;

import java.text.SimpleDateFormat;
import java.util.Date;

import javax.annotation.Nullable;

public class BilibiliCommentsInfoItemExtractor implements CommentsInfoItemExtractor {
    public JsonObject json = new JsonObject();
    public String url = "";
    BilibiliCommentsInfoItemExtractor(JsonObject json, String url){
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
    public String getThumbnailUrl() throws ParsingException {
        return null;
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
        return "https://api.bilibili.com/x/space/arc/search?pn=1&ps=10&mid=" + json.get("mid");
    }

    @Override
    public String getUploaderName() throws ParsingException {
        return json.getObject("member").getString("uname");
    }

    @Override
    public String getUploaderAvatarUrl() throws ParsingException {
        return json.getObject("member").getString("avatar").replace("http:", "https:");
    }

    @Override
    public boolean isHeartedByUploader() throws ParsingException {
        return false;
    }

    @Override
    public boolean isPinned() throws ParsingException {
        return false;
    }

    @Override
    public boolean isUploaderVerified() throws ParsingException {
        return false;
    }
    @Nullable
    @Override
    public Page getReplies() throws ParsingException {
        return null;
    }
}
