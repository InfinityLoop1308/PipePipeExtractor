package org.schabi.newpipe.extractor.services.youtube.extractors;

import com.grack.nanojson.JsonObject;
import org.schabi.newpipe.extractor.exceptions.ParsingException;
import org.schabi.newpipe.extractor.localization.DateWrapper;
import org.schabi.newpipe.extractor.localization.TimeAgoParser;
import org.schabi.newpipe.extractor.stream.StreamInfoItemExtractor;
import org.schabi.newpipe.extractor.stream.StreamType;
import org.schabi.newpipe.extractor.utils.Utils;

import javax.annotation.Nullable;

import static org.schabi.newpipe.extractor.utils.Utils.isNullOrEmpty;

public class YoutubeShortsInfoItemExtractor implements StreamInfoItemExtractor {
    public JsonObject item;
    @Nullable
    private final String textualUploadDate;
    private final TimeAgoParser timeAgoParser;

    public YoutubeShortsInfoItemExtractor(final JsonObject item) {
        this(item, null, null);
    }

    public YoutubeShortsInfoItemExtractor(final JsonObject item,
                                          @Nullable final String textualUploadDate,
                                          @Nullable final TimeAgoParser timeAgoParser) {
        this.item = item;
        this.textualUploadDate = textualUploadDate;
        this.timeAgoParser = timeAgoParser;
    }

    @Override
    public String getName() throws ParsingException {
        return item.getObject("overlayMetadata").getObject("primaryText").getString("content");
    }

    @Override
    public String getUrl() throws ParsingException {
        return "https://youtube.com" + item.getObject("onTap").getObject("innertubeCommand")
                .getObject("commandMetadata").getObject("webCommandMetadata").getString("url");
    }

    @Override
    public String getThumbnailUrl() throws ParsingException {
        if (item.has("thumbnail")) {
            return item.getObject("thumbnail").getArray("sources").getObject(0).getString("url");
        }
        return item.getObject("thumbnailViewModel")
                .getObject("thumbnailViewModel")
                .getObject("image")
                .getArray("sources")
                .getObject(0)
                .getString("url");
    }

    @Override
    public StreamType getStreamType() throws ParsingException {
        return StreamType.VIDEO_STREAM;
    }

    @Override
    public long getDuration() throws ParsingException {
        return 0;
    }

    @Override
    public long getViewCount() throws ParsingException {
        try {
            return Utils.mixedNumberWordToLong(item.getObject("overlayMetadata").getObject("secondaryText").getString("content").split(" view")[0]);
        } catch (Exception e) {
            return -1;
        }
    }

    @Override
    public String getUploaderName() throws ParsingException {
        return null;
    }

    @Nullable
    @Override
    public String getTextualUploadDate() throws ParsingException {
        return textualUploadDate;
    }

    @Nullable
    @Override
    public DateWrapper getUploadDate() throws ParsingException {
        final String textualUploadDate = getTextualUploadDate();
        if (timeAgoParser == null || isNullOrEmpty(textualUploadDate)) {
            return null;
        }
        return timeAgoParser.parse(textualUploadDate);
    }
}
