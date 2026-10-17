package org.schabi.newpipe.extractor.services.niconico.extractors;

import com.grack.nanojson.JsonObject;

import org.schabi.newpipe.extractor.exceptions.ParsingException;
import org.schabi.newpipe.extractor.playlist.PlaylistInfoItemExtractor;
import org.schabi.newpipe.extractor.services.niconico.NiconicoService;

import javax.annotation.Nullable;

public class NiconicoSeriesInfoItemExtractor implements PlaylistInfoItemExtractor {
    private final JsonObject itemObject;
    private final String name;

    public NiconicoSeriesInfoItemExtractor(JsonObject itemObject, String name) {
        this.itemObject = itemObject;
        this.name = name;
    }

    @Override
    public String getName() throws ParsingException {
        return itemObject.getString("title");
    }

    @Override
    public String getUrl() throws ParsingException {
        return "https://www.nicovideo.jp/series/" + itemObject.getLong("id");
    }

    @Override
    public String getThumbnailUrl() throws ParsingException {
        return itemObject.getString("thumbnailUrl");
    }

    @Override
    public String getUploaderName() throws ParsingException {
        return name;
    }

    @Nullable
    @Override
    public String getUploaderId() {
        return itemObject.getObject("owner").getString("id");
    }

    @Nullable
    @Override
    public String getUploaderUrl() {
        final JsonObject owner = itemObject.getObject("owner");
        final String id = owner.getString("id", "");
        if (id.isEmpty()) {
            return null;
        }
        final String ownerType = owner.getString("ownerType", owner.getString("type", "user"));
        return "user".equals(ownerType)
                ? NiconicoService.USER_URL + id
                : NiconicoService.CHANNEL_URL + id;
    }

    @Override
    public long getStreamCount() throws ParsingException {
        return itemObject.getInt("itemsCount");
    }
}
