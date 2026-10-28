package org.schabi.newpipe.extractor.services.bilibili.extractors;

import com.grack.nanojson.JsonObject;

import org.schabi.newpipe.extractor.exceptions.ParsingException;
import org.schabi.newpipe.extractor.playlist.PlaylistInfoItemExtractor;
import org.schabi.newpipe.extractor.services.bilibili.linkHandler.BilibiliChannelLinkHandlerFactory;
import org.schabi.newpipe.extractor.services.bilibili.utils;

import javax.annotation.Nullable;

public class BilibiliPlaylistInfoItemExtractor implements PlaylistInfoItemExtractor {
    private final JsonObject itemObject;
    private final String type;
    private String name;
    private String url;
    private String thumbnail;
    private String uploader;
    private String uploaderId;
    private long streamCount;

    public BilibiliPlaylistInfoItemExtractor(JsonObject itemObject, String type) {
        if (itemObject != null && itemObject.has("meta") && itemObject.getObject("meta") != null) {
            this.itemObject = itemObject.getObject("meta");
        } else {
            this.itemObject = itemObject;
        }
        this.type = type;
    }
    public BilibiliPlaylistInfoItemExtractor(String name, String url, String thumbnail, String uploader, long streamCount) {
        this(name, url, thumbnail, uploader, null, streamCount);
    }

    public BilibiliPlaylistInfoItemExtractor(String name, String url, String thumbnail, String uploader, String uploaderId, long streamCount) {
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
        if (itemObject == null) {
            return "";
        }
        if (itemObject.has("name") && itemObject.getString("name") != null && !itemObject.getString("name").isEmpty()) {
            return itemObject.getString("name");
        }
        if (itemObject.has("title") && itemObject.getString("title") != null && !itemObject.getString("title").isEmpty()) {
            return itemObject.getString("title");
        }
        return "";
    }

    @Override
    public String getUrl() throws ParsingException {
        if (type == null) {
            return url;
        }
        if (itemObject == null) {
            return null;
        }
        if ("seasons_archives".equals(type)) {
            return String.format("https://www.bilibili.com/medialist/play/%d?business=space_collection&business_id=%d",
                    itemObject.getLong("mid"), itemObject.getLong("season_id"));
        } else if ("archives".equals(type) || "series".equals(type)) {
            return String.format("https://www.bilibili.com/medialist/play/%d?business=space_series&business_id=%d&name=%s",
                    itemObject.getLong("mid"), itemObject.getLong("series_id"),
                    utils.formatParamWithPercentSpace(getName()));
        }
        return null;
    }

    @Override
    public String getThumbnailUrl() throws ParsingException {
        if (type == null) {
            return thumbnail;
        }
        if (itemObject == null) {
            return "";
        }
        String cover = itemObject.getString("cover");
        return cover != null ? cover.replace("http:", "https:") : "";
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
        if (itemObject == null) {
            return null;
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
        if (itemObject == null) {
            return null;
        }
        final long mid = itemObject.getLong("mid", -1);
        return mid > 0 ? BilibiliChannelLinkHandlerFactory.baseUrl + mid : null;
    }

    @Override
    public long getStreamCount() throws ParsingException {
        if (type == null) {
            return streamCount;
        }
        return itemObject != null ? itemObject.getLong("total") : 0;
    }
}
