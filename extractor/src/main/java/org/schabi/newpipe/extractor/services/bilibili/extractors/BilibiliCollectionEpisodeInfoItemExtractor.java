package org.schabi.newpipe.extractor.services.bilibili.extractors;

import org.schabi.newpipe.extractor.exceptions.ParsingException;
import org.schabi.newpipe.extractor.localization.DateWrapper;
import org.schabi.newpipe.extractor.services.bilibili.linkHandler.BilibiliChannelLinkHandlerFactory;
import org.schabi.newpipe.extractor.stream.StreamCollectionInfo;
import org.schabi.newpipe.extractor.stream.StreamInfoItemExtractor;
import org.schabi.newpipe.extractor.stream.StreamType;

import javax.annotation.Nullable;

/** A playable episode row sourced from the section metadata in a Bilibili collection. */
final class BilibiliCollectionEpisodeInfoItemExtractor implements StreamInfoItemExtractor {
    private final StreamCollectionInfo.Episode episode;
    private final String sectionTitle;
    private final String uploaderName;
    private final String uploaderId;
    private final String uploaderAvatarUrl;

    BilibiliCollectionEpisodeInfoItemExtractor(
            final StreamCollectionInfo.Episode episode,
            final String sectionTitle,
            final String uploaderName,
            final String uploaderId,
            final String uploaderAvatarUrl) {
        this.episode = episode;
        this.sectionTitle = sectionTitle;
        this.uploaderName = uploaderName;
        this.uploaderId = uploaderId;
        this.uploaderAvatarUrl = uploaderAvatarUrl;
    }

    @Override
    public String getName() {
        return sectionTitle + " · " + episode.getTitle();
    }

    @Override
    public String getUrl() {
        return episode.getUrl();
    }

    @Override
    public String getThumbnailUrl() {
        return episode.getThumbnailUrl();
    }

    @Override
    public StreamType getStreamType() {
        return StreamType.VIDEO_STREAM;
    }

    @Override
    public long getDuration() {
        return -1;
    }

    @Override
    public long getViewCount() {
        return -1;
    }

    @Override
    public String getUploaderName() {
        return uploaderName;
    }

    @Nullable
    @Override
    public String getUploaderId() {
        return uploaderId;
    }

    @Override
    public String getUploaderUrl() {
        return uploaderId == null ? ""
                : BilibiliChannelLinkHandlerFactory.baseUrl + uploaderId;
    }

    @Nullable
    @Override
    public String getUploaderAvatarUrl() {
        return uploaderAvatarUrl;
    }

    @Nullable
    @Override
    public String getTextualUploadDate() {
        return null;
    }

    @Nullable
    @Override
    public DateWrapper getUploadDate() throws ParsingException {
        return null;
    }
}
