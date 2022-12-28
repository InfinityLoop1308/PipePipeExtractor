package org.schabi.newpipe.extractor.services.bilibili.extractors;

import org.jsoup.nodes.Element;
import org.schabi.newpipe.extractor.bulletComments.BulletCommentsInfoItem;
import org.schabi.newpipe.extractor.bulletComments.BulletCommentsInfoItemExtractor;
import org.schabi.newpipe.extractor.exceptions.ParsingException;

import java.time.Duration;
import java.util.HashMap;

public class BilibiliBulletCommentsInfoItemExtractor implements BulletCommentsInfoItemExtractor {
    private Element element;
    private String[] attr;

    public BilibiliBulletCommentsInfoItemExtractor(Element element) {
        this.element = element;
        attr = element.attr("p").split(",");
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
        return element.text();
    }

    @Override
    public int getArgbColor() throws ParsingException {
        return Integer.parseInt(attr[3]) + 0xFF000000;
    }

    @Override
    public BulletCommentsInfoItem.Position getPosition() throws ParsingException {
        if(attr[1].equals("4")){
            return BulletCommentsInfoItem.Position.BOTTOM;
        } else if (attr[1].equals("5")) {
            return BulletCommentsInfoItem.Position.TOP;
        }
        return BulletCommentsInfoItem.Position.REGULAR;
    }

    @Override
    public double getRelativeFontSize() throws ParsingException {
        switch (attr[2]){
            case "18":
                return 0.5;
            case "25":
            default:
                return 0.6;
            case "36":
                return 0.7;
        }
    }

    @Override
    public Duration getDuration() throws ParsingException {
        return Duration.ofMillis((long) (Double.parseDouble(attr[0])*1000));
    }
}
