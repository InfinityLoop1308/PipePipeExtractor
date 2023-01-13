package org.schabi.newpipe.extractor.services.bilibili.extractors;

import org.schabi.newpipe.extractor.exceptions.ParsingException;
import org.schabi.newpipe.extractor.localization.DateWrapper;
import org.schabi.newpipe.extractor.stream.StreamInfoItemExtractor;
import org.schabi.newpipe.extractor.stream.StreamType;

import javax.annotation.Nullable;

public class BilibiliRecommendLiveInfoItemExtractor implements StreamInfoItemExtractor {
    private final String url;
    private final String thumbnail;
    private final String title;
    private final String name;
    private final Long views;


    public BilibiliRecommendLiveInfoItemExtractor(String url, String thumbnail, String title, String name, Long views) {
        this.url = url;
        this.thumbnail = thumbnail;
        this.title = title;
        this.name = name;
        this.views = views;
    }

    @Override
    public String getName() throws ParsingException {
        return title;
    }

    @Override
    public String getUrl() throws ParsingException {
        return url;
    }

    @Override
    public String getThumbnailUrl() throws ParsingException {
        return thumbnail;
    }

    @Override
    public StreamType getStreamType() throws ParsingException {
        return StreamType.LIVE_STREAM;
    }

    @Override
    public long getDuration() throws ParsingException {
        return -1;
    }

    @Override
    public long getViewCount() throws ParsingException {
        return views;
    }

    @Override
    public String getUploaderName() throws ParsingException {
        return name;
    }

    @Nullable
    @Override
    public String getTextualUploadDate() throws ParsingException {
        return null;
    }

    @Nullable
    @Override
    public DateWrapper getUploadDate() throws ParsingException {
        return null;
    }
}
