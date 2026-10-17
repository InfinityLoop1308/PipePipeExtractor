package org.schabi.newpipe.extractor.services.youtube.extractors;

import com.grack.nanojson.JsonObject;

import org.schabi.newpipe.extractor.bulletComments.BulletCommentsInfoItem;
import org.schabi.newpipe.extractor.exceptions.ParsingException;

public class YoutubeSuperChatInfoItemExtractor extends YoutubeBulletCommentsInfoItemExtractor {
    private JsonObject data;
    public YoutubeSuperChatInfoItemExtractor(JsonObject item, long startTime, long offsetDuration) {
        super(item, startTime, offsetDuration);
        data = item;
    }

    @Override
    public String getCommentText() throws ParsingException {
        String superResult = super.getCommentText();
        if(superResult.length() == 0){
            return "";
        }
        return String.format("(%s) ", data.getObject("purchaseAmountText")
                .getString("simpleText")) + super.getCommentText();
    }

    @Override
    public String getAmountText() throws ParsingException {
        return data.getObject("purchaseAmountText").getString("simpleText");
    }

    @Override
    public String getAuthorAvatarUrl() throws ParsingException {
        final com.grack.nanojson.JsonArray thumbs =
                data.getObject("authorPhoto").getArray("thumbnails");
        return thumbs.isEmpty() ? null : thumbs.getObject(thumbs.size() - 1).getString("url");
    }

    @Override
    public int getArgbColor() throws ParsingException {
        return (int) data.getLong("bodyBackgroundColor");
    }

    @Override
    public long getPinnedTimeMillis() {
        // pin time per tier; $100 and above share one colour, so use the average of 1h to 5h
        final long seconds;
        switch ((int) data.getLong("bodyBackgroundColor") & 0xFFFFFF) {
            case 0x0F9D58:
                seconds = 120;
                break;
            case 0xFFCA28:
                seconds = 300;
                break;
            case 0xF57C00:
                seconds = 600;
                break;
            case 0xE91E63:
                seconds = 1800;
                break;
            case 0xE62117:
                seconds = 10800;
                break;
            default:
                seconds = 0;
        }
        return seconds * 1000;
    }

    @Override
    public BulletCommentsInfoItem.Position getPosition() throws ParsingException {
        return BulletCommentsInfoItem.Position.SUPERCHAT;
    }
}
