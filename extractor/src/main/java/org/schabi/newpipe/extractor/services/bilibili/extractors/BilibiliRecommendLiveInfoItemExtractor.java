package org.schabi.newpipe.extractor.services.bilibili.extractors;

import org.schabi.newpipe.extractor.exceptions.ParsingException;
import org.schabi.newpipe.extractor.localization.DateWrapper;
import org.schabi.newpipe.extractor.stream.StreamInfoItemExtractor;
import org.schabi.newpipe.extractor.stream.StreamType;

import javax.annotation.Nullable;

public class BilibiliRecommendLiveInfoItemExtractor implements StreamInfoItemExtractor {
    private String url;
    private String thumbnail;
    private String title;
    private String name;
    private Long views;


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
    public boolean isAd() throws ParsingException {
        return false;
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

    @Override
    public String getUploaderUrl() throws ParsingException {
        return null;
    }

    @Nullable
    @Override
    public String getUploaderAvatarUrl() throws ParsingException {
        return null;
    }

    @Override
    public boolean isUploaderVerified() throws ParsingException {
        return false;
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
