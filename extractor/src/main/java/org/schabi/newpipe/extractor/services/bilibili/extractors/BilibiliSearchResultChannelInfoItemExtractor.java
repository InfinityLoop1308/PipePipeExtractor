package org.schabi.newpipe.extractor.services.bilibili.extractors;

import com.grack.nanojson.JsonObject;

import org.schabi.newpipe.extractor.channel.ChannelInfoItemExtractor;
import org.schabi.newpipe.extractor.exceptions.ParsingException;
import org.schabi.newpipe.extractor.services.bilibili.linkHandler.BilibiliChannelLinkHandlerFactory;

public class BilibiliSearchResultChannelInfoItemExtractor implements ChannelInfoItemExtractor {
    JsonObject json;

    BilibiliSearchResultChannelInfoItemExtractor(JsonObject json) {
        this.json = json;
    }

    @Override
    public String getName() throws ParsingException {
        return json.getString("uname");
    }

    @Override
    public String getUrl() throws ParsingException {
        return BilibiliChannelLinkHandlerFactory.baseUrl + json.getLong("mid");
    }

    @Override
    public String getThumbnailUrl() throws ParsingException {
        return "https:" + json.getString("upic");
    }

    @Override
    public String getDescription() throws ParsingException {
        return json.getString("usign");
    }

    @Override
    public long getSubscriberCount() throws ParsingException {
        return json.getLong("fans");
    }

    @Override
    public long getStreamCount() throws ParsingException {
        return json.getLong("videos");
    }

    @Override
    public boolean isVerified() throws ParsingException {
        return false;
    }
}
