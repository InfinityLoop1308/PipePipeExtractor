package org.schabi.newpipe.extractor.services.bilibili.extractors;

import com.grack.nanojson.JsonObject;

import org.schabi.newpipe.extractor.exceptions.ParsingException;
import org.schabi.newpipe.extractor.playlist.PlaylistInfoItemExtractor;
import org.schabi.newpipe.extractor.services.bilibili.linkHandler.BilibiliChannelLinkHandlerFactory;

import javax.annotation.Nullable;

import static org.schabi.newpipe.extractor.services.bilibili.BilibiliService.GET_SEASON_ARCHIVES_LIST_RAW_URL;
import static org.schabi.newpipe.extractor.services.bilibili.BilibiliService.GET_SERIES_RAW_URL;

public class BilibiliPlaylistInfoItemExtractor implements PlaylistInfoItemExtractor {
    private final JsonObject itemObject;
    private final String type;
    private String name;
    private String url;
    private String thumbnail;
    private String uploader;
    private String uploaderId;
    private long streamCount;
    public BilibiliPlaylistInfoItemExtractor(JsonObject itemObject, String type){
        this.itemObject = itemObject.getObject("meta");
        this.type = type;
    }
    public BilibiliPlaylistInfoItemExtractor(String name, String url, String thumbnail, String uploader, long streamCount){
        this(name, url, thumbnail, uploader, null, streamCount);
    }
    public BilibiliPlaylistInfoItemExtractor(String name, String url, String thumbnail, String uploader, String uploaderId, long streamCount){
        this.name = name;
        this.url = url;
        this.thumbnail = thumbnail;
        this.uploader = uploader;
        this.uploaderId = uploaderId;
        this.streamCount = streamCount;
        this.itemObject = null;
        this.type = null;
    }
    @Override
    public String getName() throws ParsingException {
        if (type == null) {
            return name;
        }
        return itemObject.getString("name");
    }

    @Override
    public String getUrl() throws ParsingException {
        if (type == null) {
            return url;
        }
        if(type.equals("seasons_archives")){
           return String.format(GET_SEASON_ARCHIVES_LIST_RAW_URL,
                    itemObject.getLong("mid"), itemObject.getLong("season_id"), getName());
        }
        else if(type.equals("archives")){
            return String.format(GET_SERIES_RAW_URL,
                    itemObject.getLong("mid"), itemObject.getLong("series_id"), getName());
        }
        return null;
    }

    @Override
    public String getThumbnailUrl() throws ParsingException {
        if (type == null) {
            return thumbnail;
        }
        return itemObject.getString("cover").replace("http:", "https:");
    }

    @Override
    public String getUploaderName() throws ParsingException {
        if (type == null) {
            return uploader;
        }
        return null;
    }

    @Nullable
    @Override
    public String getUploaderId() {
        if (type == null) {
            return uploaderId;
        }
        final long mid = itemObject.getLong("mid", -1);
        return mid > 0 ? String.valueOf(mid) : null;
    }

    @Nullable
    @Override
    public String getUploaderUrl() {
        if (type == null) {
            return uploaderId == null ? null
                    : BilibiliChannelLinkHandlerFactory.baseUrl + uploaderId;
        }
        final long mid = itemObject.getLong("mid", -1);
        return mid > 0 ? BilibiliChannelLinkHandlerFactory.baseUrl + mid : null;
    }

    @Override
    public long getStreamCount() throws ParsingException {
        if (type == null) {
            return streamCount;
        }
        return itemObject.getLong("total");
    }
}
