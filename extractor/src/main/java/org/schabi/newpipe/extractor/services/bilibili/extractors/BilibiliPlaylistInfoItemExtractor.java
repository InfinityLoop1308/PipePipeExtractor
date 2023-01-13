package org.schabi.newpipe.extractor.services.bilibili.extractors;

import com.grack.nanojson.JsonObject;

import org.schabi.newpipe.extractor.exceptions.ParsingException;
import org.schabi.newpipe.extractor.playlist.PlaylistInfoItemExtractor;

import static org.schabi.newpipe.extractor.services.bilibili.BilibiliService.GET_SEASON_ARCHIVES_LIST_BASE_URL;
import static org.schabi.newpipe.extractor.services.bilibili.BilibiliService.GET_SERIES_BASE_URL;

public class BilibiliPlaylistInfoItemExtractor implements PlaylistInfoItemExtractor {
    private final JsonObject itemObject;
    private final String type;
    public BilibiliPlaylistInfoItemExtractor(JsonObject itemObject, String type){
        this.itemObject = itemObject.getObject("meta");
        this.type = type;
    }
    @Override
    public String getName() throws ParsingException {
        return itemObject.getString("name");
    }

    @Override
    public String getUrl() throws ParsingException {
        if(type.equals("seasons_archives")){
           return String.format(GET_SEASON_ARCHIVES_LIST_BASE_URL,
                    itemObject.getLong("mid"), itemObject.getLong("season_id"), getName());
        }
        else if(type.equals("archives")){
            return String.format(GET_SERIES_BASE_URL,
                    itemObject.getLong("mid"), itemObject.getLong("series_id"), getName());
        }
        return null;
    }

    @Override
    public String getThumbnailUrl() throws ParsingException {
        return itemObject.getString("cover").replace("http:", "https:");
    }

    @Override
    public String getUploaderName() throws ParsingException {
        return null;
    }

    @Override
    public long getStreamCount() throws ParsingException {
        return itemObject.getLong("total");
    }
}
