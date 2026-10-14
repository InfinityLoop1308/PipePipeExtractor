package org.schabi.newpipe.extractor.services.youtube.extractors;

import com.grack.nanojson.JsonArray;
import com.grack.nanojson.JsonObject;
import org.schabi.newpipe.extractor.exceptions.ParsingException;
import org.schabi.newpipe.extractor.playlist.PlaylistInfoItemExtractor;
import org.schabi.newpipe.extractor.services.youtube.linkHandler.YoutubePlaylistLinkHandlerFactory;
import org.schabi.newpipe.extractor.utils.Utils;

import javax.annotation.Nullable;

import static org.schabi.newpipe.extractor.services.youtube.YoutubeParsingHelper.fixThumbnailUrl;
import static org.schabi.newpipe.extractor.services.youtube.YoutubeParsingHelper.getChannelIdFromTextObject;
import static org.schabi.newpipe.extractor.services.youtube.YoutubeParsingHelper.getChannelUrlFromTextObject;
import static org.schabi.newpipe.extractor.services.youtube.YoutubeParsingHelper.getTextFromObject;

public class YoutubePlaylistInfoItemExtractor implements PlaylistInfoItemExtractor {
    private final JsonObject playlistInfoItem;

    public YoutubePlaylistInfoItemExtractor(final JsonObject playlistInfoItem) {
        this.playlistInfoItem = playlistInfoItem;
    }

    @Override
    public String getThumbnailUrl() throws ParsingException {
        try {
            JsonArray thumbnails = playlistInfoItem.getArray("thumbnails").getObject(0)
                    .getArray("thumbnails");
            if (thumbnails.isEmpty()) {
                thumbnails = playlistInfoItem.getObject("thumbnail").getArray("thumbnails");
            }

            final String url = thumbnails.getObject(Math.max(0, thumbnails.size() - 2)).getString("url");
            return fixThumbnailUrl(url);
        } catch (final Exception e) {
            throw new ParsingException("Could not get thumbnail url", e);
        }
    }

    @Override
    public String getName() throws ParsingException {
        try {
            return getTextFromObject(playlistInfoItem.getObject("title"));
        } catch (final Exception e) {
            throw new ParsingException("Could not get name", e);
        }
    }

    @Override
    public String getUrl() throws ParsingException {
        try {
            final String id = playlistInfoItem.getString("playlistId");
            return YoutubePlaylistLinkHandlerFactory.getInstance().getUrl(id);
        } catch (final Exception e) {
            throw new ParsingException("Could not get url", e);
        }
    }

    @Override
    public String getUploaderName() throws ParsingException {
        try {
            return getTextFromObject(playlistInfoItem.getObject("longBylineText"));
        } catch (final Exception e) {
            throw new ParsingException("Could not get uploader name", e);
        }
    }

    @Nullable
    @Override
    public String getUploaderId() {
        for (final String key : new String[]{"longBylineText", "shortBylineText", "ownerText"}) {
            final String id = getChannelIdFromTextObject(playlistInfoItem.getObject(key));
            if (id != null) {
                return id;
            }
        }
        return null;
    }

    @Nullable
    @Override
    public String getUploaderUrl() throws ParsingException {
        for (final String key : new String[]{"longBylineText", "shortBylineText", "ownerText"}) {
            final String url = getChannelUrlFromTextObject(playlistInfoItem.getObject(key));
            if (url != null) {
                return url;
            }
        }
        return null;
    }

    @Override
    public long getStreamCount() throws ParsingException {
        String videoCountText = playlistInfoItem.getString("videoCount");
        if (videoCountText == null) {
            videoCountText = getTextFromObject(playlistInfoItem.getObject("videoCountShortText"));
        }

        try {
            return Long.parseLong(Utils.removeNonDigitCharacters(videoCountText));
        } catch (final Exception e) {
            throw new ParsingException("Could not get stream count", e);
        }
    }
}
