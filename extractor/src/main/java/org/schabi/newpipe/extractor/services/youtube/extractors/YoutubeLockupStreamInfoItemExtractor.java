package org.schabi.newpipe.extractor.services.youtube.extractors;

import com.grack.nanojson.JsonArray;
import com.grack.nanojson.JsonObject;
import org.schabi.newpipe.extractor.exceptions.ParsingException;
import org.schabi.newpipe.extractor.localization.DateWrapper;
import org.schabi.newpipe.extractor.localization.TimeAgoParser;
import org.schabi.newpipe.extractor.services.youtube.YoutubeParsingHelper;
import org.schabi.newpipe.extractor.services.youtube.linkHandler.YoutubeStreamLinkHandlerFactory;
import org.schabi.newpipe.extractor.stream.StreamInfoItemExtractor;
import org.schabi.newpipe.extractor.stream.StreamType;

import javax.annotation.Nullable;

import static org.schabi.newpipe.extractor.services.youtube.YoutubeParsingHelper.getImagesFromThumbnailsArray;
import static org.schabi.newpipe.extractor.services.youtube.YoutubeParsingHelper.getUrlFromNavigationEndpoint;
import static org.schabi.newpipe.extractor.services.youtube.YoutubeParsingHelper.hasArtistOrVerifiedIconBadgeAttachment;
import static org.schabi.newpipe.extractor.utils.Utils.isNullOrEmpty;
import static org.schabi.newpipe.extractor.utils.Utils.mixedNumberWordToLong;

public class YoutubeLockupStreamInfoItemExtractor implements StreamInfoItemExtractor {
    private final JsonObject lockupViewModel;
    private final JsonObject thumbnailViewModel;
    private final JsonObject lockupMetadataViewModel;
    private final TimeAgoParser timeAgoParser;
    private StreamType cachedStreamType;

    public YoutubeLockupStreamInfoItemExtractor(final JsonObject lockupViewModel,
                                                @Nullable final TimeAgoParser timeAgoParser) {
        this.lockupViewModel = lockupViewModel;
        this.thumbnailViewModel = lockupViewModel.getObject("contentImage")
                .getObject("thumbnailViewModel");
        this.lockupMetadataViewModel = lockupViewModel.getObject("metadata")
                .getObject("lockupMetadataViewModel");
        this.timeAgoParser = timeAgoParser;
    }

    @Override
    public String getName() throws ParsingException {
        final String name = lockupMetadataViewModel.getObject("title").getString("content");
        if (isNullOrEmpty(name)) {
            throw new ParsingException("Could not get name");
        }
        return name;
    }

    @Override
    public String getUrl() throws ParsingException {
        try {
            final String videoId = lockupViewModel.getString("contentId");
            return YoutubeStreamLinkHandlerFactory.getInstance().getUrl(videoId);
        } catch (final Exception e) {
            throw new ParsingException("Could not get url", e);
        }
    }

    @Override
    public String getThumbnailUrl() throws ParsingException {
        return getImagesFromThumbnailsArray(thumbnailViewModel.getObject("image")
                .getArray("sources")).get(1).getUrl();
    }

    @Override
    public StreamType getStreamType() throws ParsingException {
        if (cachedStreamType != null) {
            return cachedStreamType;
        }
//
//        // Check for live stream indicators in overlays
//        final JsonArray overlays = thumbnailViewModel.getArray("overlays");
//        for (int i = 0; i < overlays.size(); i++) {
//            final JsonObject overlay = overlays.getObject(i);
//            if (overlay.has("thumbnailOverlayBadgeViewModel")) {
//                final JsonObject badgeViewModel = overlay.getObject("thumbnailOverlayBadgeViewModel")
//                        .getArray("thumbnailBadges")
//                        .getObject(0)
//                        .getObject("thumbnailBadgeViewModel");
//
//                if (badgeViewModel.has("animatedText") &&
//                        "Now playing".equals(badgeViewModel.getString("animatedText"))) {
//                    cachedStreamType = StreamType.LIVE_STREAM;
//                    return cachedStreamType;
//                }
//            }
//        }

        cachedStreamType = StreamType.VIDEO_STREAM;
        return cachedStreamType;
    }

    @Override
    public long getDuration() throws ParsingException {
        if (getStreamType() == StreamType.LIVE_STREAM) {
            return -1;
        }

        // Get duration from thumbnail overlay badge
        final JsonArray overlays = thumbnailViewModel.getArray("overlays");
        for (int i = 0; i < overlays.size(); i++) {
            final JsonObject overlay = overlays.getObject(i);
            if (overlay.has("thumbnailOverlayBadgeViewModel")) {
                final JsonObject badgeViewModel = overlay.getObject("thumbnailOverlayBadgeViewModel")
                        .getArray("thumbnailBadges")
                        .getObject(0)
                        .getObject("thumbnailBadgeViewModel");

                final String durationText = badgeViewModel.getString("text");
                if (!isNullOrEmpty(durationText)) {
                    return YoutubeParsingHelper.parseDurationString(durationText);
                }
            }
        }

        return 0;
    }

    @Override
    public String getUploaderName() throws ParsingException {
        final JsonArray metadataRows = lockupMetadataViewModel.getObject("metadata")
                .getObject("contentMetadataViewModel")
                .getArray("metadataRows");

        if (metadataRows.size() > 0) {
            final JsonArray metadataParts = metadataRows.getObject(0).getArray("metadataParts");
            if (metadataParts.size() > 0) {
                final String uploaderName = metadataParts.getObject(0)
                        .getObject("text")
                        .getString("content");
                if (!isNullOrEmpty(uploaderName)) {
                    return uploaderName;
                }
            }
        }

        throw new ParsingException("Could not get uploader name");
    }

    @Override
    public String getUploaderUrl() throws ParsingException {
        return getUrlFromNavigationEndpoint(
                lockupMetadataViewModel.getObject("image")
                        .getObject("decoratedAvatarViewModel")
                        .getObject("rendererContext")
                        .getObject("commandContext")
                        .getObject("onTap")
                        .getObject("innertubeCommand"));
    }

    @Override
    public boolean isUploaderVerified() throws ParsingException {
        final JsonArray metadataRows = lockupMetadataViewModel.getObject("metadata")
                .getObject("contentMetadataViewModel")
                .getArray("metadataRows");

        if (metadataRows.size() > 0) {
            final JsonArray metadataParts = metadataRows.getObject(0).getArray("metadataParts");
            if (metadataParts.size() > 0) {
                final JsonArray attachmentRuns = metadataParts.getObject(0)
                        .getObject("text")
                        .getArray("attachmentRuns");
                return hasArtistOrVerifiedIconBadgeAttachment(attachmentRuns);
            }
        }

        return false;
    }

    @Override
    public long getViewCount() throws ParsingException {
        final JsonArray metadataRows = lockupMetadataViewModel.getObject("metadata")
                .getObject("contentMetadataViewModel")
                .getArray("metadataRows");

        if (metadataRows.size() > 1) {
            final JsonArray metadataParts = metadataRows.getObject(1).getArray("metadataParts");
            if (metadataParts.size() > 0) {
                final String viewsText = metadataParts.getObject(0)
                        .getObject("text")
                        .getString("content");

                if (!isNullOrEmpty(viewsText) && viewsText.contains("views")) {
                    return mixedNumberWordToLong(viewsText);
                }
            }
        }

        return -1;
    }

    @Nullable
    @Override
    public String getTextualUploadDate() throws ParsingException {
        final JsonArray metadataRows = lockupMetadataViewModel.getObject("metadata")
                .getObject("contentMetadataViewModel")
                .getArray("metadataRows");

        if (metadataRows.size() > 1) {
            final JsonArray metadataParts = metadataRows.getObject(1).getArray("metadataParts");
            if (metadataParts.size() > 1) {
                final String uploadText = metadataParts.getObject(1)
                        .getObject("text")
                        .getString("content");

                if (!isNullOrEmpty(uploadText)) {
                    return uploadText;
                }
            }
        }

        return null;
    }

    @Nullable
    @Override
    public DateWrapper getUploadDate() throws ParsingException {
        final String textualUploadDate = getTextualUploadDate();
        if (timeAgoParser != null && !isNullOrEmpty(textualUploadDate)) {
            try {
                return timeAgoParser.parse(textualUploadDate);
            } catch (final ParsingException e) {
                throw new ParsingException("Could not get upload date", e);
            }
        }
        return null;
    }

    @Override
    public boolean isAd() throws ParsingException {
        return false;
    }

    @Nullable
    @Override
    public String getShortDescription() throws ParsingException {
        return null;
    }

    @Override
    public boolean isShortFormContent() throws ParsingException {
        return false;
    }

    @Override
    public boolean requiresMembership() throws ParsingException {
        return false;
    }

    @Nullable
    @Override
    public String getUploaderAvatarUrl() throws ParsingException {
        try {
            return lockupMetadataViewModel.getObject("image")
                    .getObject("decoratedAvatarViewModel")
                    .getObject("avatar")
                    .getObject("avatarViewModel")
                    .getObject("image")
                    .getArray("sources")
                    .getObject(0)
                    .getString("url");
        } catch (final Exception e) {
            return null;
        }
    }
}
