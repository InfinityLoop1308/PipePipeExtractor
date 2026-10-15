package org.schabi.newpipe.extractor.services.youtube.extractors;

import com.grack.nanojson.JsonArray;
import com.grack.nanojson.JsonObject;

import org.schabi.newpipe.extractor.bulletComments.BulletCommentsInfoItem;
import org.schabi.newpipe.extractor.bulletComments.BulletCommentsInfoItemExtractor;
import org.schabi.newpipe.extractor.exceptions.ParsingException;

import java.time.Duration;

public class YoutubeBulletCommentsInfoItemExtractor implements BulletCommentsInfoItemExtractor {
    private final JsonObject data;
    private long startTime;
    private long offsetDuration; // the expected offset of the comment from the start of the video
    public YoutubeBulletCommentsInfoItemExtractor(JsonObject item, long startTime, long offsetDuration) {
        data = item;
        this.startTime = startTime;
        this.offsetDuration = offsetDuration;
    }

    @Override
    public String getCommentText() throws ParsingException {
        JsonArray array = data.getObject("message").getArray("runs");
        StringBuilder result = new StringBuilder();
        for(int i = 0; i< array.size(); i++){
            final JsonObject run = array.getObject(i);
            if(run.has("text")){
                result.append(run.getString("text"));
            } else if (run.has("emoji")) {
                result.append(getEmojiText(run.getObject("emoji")));
            }
        }
        return result.toString().replaceAll("□", "");
    }

    @Override
    public String getAuthorName() throws ParsingException {
        return data.getObject("authorName").getString("simpleText");
    }

    /** Unicode emoji are their own label; custom emoji are described by their label. */
    private static String getEmojiText(final JsonObject emoji) {
        final String label = emoji.getObject("image").getObject("accessibility")
                .getObject("accessibilityData").getString("label", "");
        if (!label.isEmpty()) {
            return emoji.getBoolean("isCustomEmoji") ? ":" + label + ":" : label;
        }
        final String emojiId = emoji.getString("emojiId", "");
        if (!emojiId.isEmpty() && !emojiId.contains("/")) {
            return emojiId;
        }
        final JsonArray shortcuts = emoji.getArray("shortcuts");
        return shortcuts.isEmpty() ? "" : shortcuts.getString(0, "");
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
       // return Duration.ofMillis(Long.parseLong(data.getString("timestampUsec"))/1000 - startTime);
        return offsetDuration == -1 ? Duration.ZERO : Duration.ofMillis(offsetDuration);
    }

    @Override
    public boolean isLive() throws ParsingException {
        return true;
    }
}
