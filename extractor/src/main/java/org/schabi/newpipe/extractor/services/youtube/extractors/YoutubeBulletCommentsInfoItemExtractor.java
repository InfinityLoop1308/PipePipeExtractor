package org.schabi.newpipe.extractor.services.youtube.extractors;

import com.grack.nanojson.JsonArray;
import com.grack.nanojson.JsonObject;

import org.schabi.newpipe.extractor.bulletComments.BulletCommentsInfoItem;
import org.schabi.newpipe.extractor.bulletComments.BulletCommentsInfoItemExtractor;
import org.schabi.newpipe.extractor.exceptions.ParsingException;

import java.time.Duration;

public class YoutubeBulletCommentsInfoItemExtractor implements BulletCommentsInfoItemExtractor {
    private JsonObject data;
    private long startTime;
    public YoutubeBulletCommentsInfoItemExtractor(JsonObject item, long startTime) {
        data = item;
        this.startTime = startTime;
    }

    @Override
    public String getName() throws ParsingException {
        return null;
    }

    @Override
    public String getUrl() throws ParsingException {
        return null;
    }

    @Override
    public String getThumbnailUrl() throws ParsingException {
        return null;
    }

    @Override
    public String getCommentText() throws ParsingException {
        JsonArray array = data.getObject("message").getArray("runs");
        StringBuilder result = new StringBuilder();
        for(int i = 0; i< array.size(); i++){
            if(array.getObject(i).has("text")){
                result.append(array.getObject(i).getString("text"));
            }
        }
        return result.toString().replaceAll("□", "");
    }

    @Override
    public int getArgbColor() throws ParsingException {
        return BulletCommentsInfoItemExtractor.super.getArgbColor();
    }

    @Override
    public BulletCommentsInfoItem.Position getPosition() throws ParsingException {
        return BulletCommentsInfoItem.Position.REGULAR;
    }

    @Override
    public double getRelativeFontSize() throws ParsingException {
        return BulletCommentsInfoItemExtractor.super.getRelativeFontSize();
    }

    @Override
    public Duration getDuration() throws ParsingException {
        return Duration.ofMillis(Long.parseLong(data.getString("timestampUsec"))/1000 - startTime);
    }

    @Override
    public int getLastingTime() {
        return BulletCommentsInfoItemExtractor.super.getLastingTime();
    }
}
