package org.schabi.newpipe.extractor.services.niconico.extractors;

import com.grack.nanojson.JsonObject;

import org.schabi.newpipe.extractor.exceptions.ParsingException;
import org.schabi.newpipe.extractor.playlist.PlaylistInfoItemExtractor;

public class NiconicoPlaylistInfoItemExtractor implements PlaylistInfoItemExtractor {
    private final JsonObject itemObject;

    public NiconicoPlaylistInfoItemExtractor(JsonObject itemObject) {
        this.itemObject = itemObject;
    }

    @Override
    public String getName() throws ParsingException {
        return itemObject.getString("name");
    }

    @Override
    public String getUrl() throws ParsingException {
        return String.format("https://www.nicovideo.jp/user/%s/mylist/%s", itemObject.getObject("owner").getString("id"), itemObject.getLong("id"));
    }

    @Override
    public String getThumbnailUrl() throws ParsingException {
        return null;
    }

    @Override
    public String getUploaderName() throws ParsingException {
        return itemObject.getObject("owner").getString("name");
    }

    @Override
    public long getStreamCount() throws ParsingException {
        return itemObject.getLong("itemsCount");
    }
}
