/*
 * Created by Christian Schabesberger on 06.08.15.
 *
 * Copyright (C) Christian Schabesberger 2019 <chris.schabesberger@mailbox.org>
 * YoutubeStreamExtractor.java is part of NewPipe Extractor.
 *
 * NewPipe Extractor is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * NewPipe Extractor is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with NewPipe Extractor. If not, see <https://www.gnu.org/licenses/>.
 */

package org.schabi.newpipe.extractor.services.youtube.extractors;

import com.grack.nanojson.*;
import org.apache.commons.lang3.StringUtils;
import org.json.JSONException;
import org.json.JSONObject;
import org.schabi.newpipe.extractor.*;
import org.schabi.newpipe.extractor.downloader.CancellableCall;
import org.schabi.newpipe.extractor.downloader.Downloader;
import org.schabi.newpipe.extractor.downloader.Response;
import org.schabi.newpipe.extractor.exceptions.*;
import org.schabi.newpipe.extractor.linkhandler.LinkHandler;
import org.schabi.newpipe.extractor.localization.*;
import org.schabi.newpipe.extractor.services.youtube.*;
import org.schabi.newpipe.extractor.services.youtube.linkHandler.YoutubeChannelLinkHandlerFactory;
import org.schabi.newpipe.extractor.stream.*;
import org.schabi.newpipe.extractor.utils.JsonUtils;
import org.schabi.newpipe.extractor.utils.Pair;
import org.schabi.newpipe.extractor.utils.Parser;
import org.schabi.newpipe.extractor.utils.SubtitleDeduplicator;
import org.schabi.newpipe.extractor.utils.Utils;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.io.File;
import java.io.IOException;
import java.net.MalformedURLException;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import static java.util.Collections.singletonList;
import static org.schabi.newpipe.extractor.services.youtube.ItagItem.APPROX_DURATION_MS_UNKNOWN;
import static org.schabi.newpipe.extractor.services.youtube.ItagItem.CONTENT_LENGTH_UNKNOWN;
import static org.schabi.newpipe.extractor.services.youtube.YoutubeParsingHelper.*;
import static org.schabi.newpipe.extractor.services.youtube.YoutubeService.getTempLocalization;
import static org.schabi.newpipe.extractor.utils.Utils.EMPTY_STRING;
import static org.schabi.newpipe.extractor.utils.Utils.isNullOrEmpty;

public class YoutubeStreamExtractor extends StreamExtractor {
    private JSONObject dislikeData;
    private JsonObject playerCaptionsTracklistRenderer;
    /*//////////////////////////////////////////////////////////////////////////
    // Exceptions
    //////////////////////////////////////////////////////////////////////////*/

    public static class DeobfuscateException extends ParsingException {
        DeobfuscateException(final String message, final Throwable cause) {
            super(message, cause);
        }
    }

    /*////////////////////////////////////////////////////////////////////////*/
    public JsonObject playerResponse;
    private JsonObject nextResponse;

    @Nullable
    private JsonObject androidStreamingData;
    @Nullable
    private JsonObject iosStreamingData;

    private JsonObject tvHtml5SimplyEmbedStreamingData;

    private JsonObject webStreamingData;

    private JsonObject videoPrimaryInfoRenderer;
    private JsonObject videoSecondaryInfoRenderer;
    public JsonObject playerMicroFormatRenderer;
    private int ageLimit = -1;
    private StreamType streamType;

    // We need to store the contentPlaybackNonces because we need to append them to videoplayback
    // URLs (with the cpn parameter).
    // Also because a nonce should be unique, it should be different between clients used, so
    // three different strings are used.
    private String tvHtml5SimplyEmbedCpn;
    private String webCpn;
    private String androidCpn;
    private String iosCpn;

    public WatchDataCache watchDataCache;

    public final ArrayList<Throwable> errors = new ArrayList<>();

    public YoutubeStreamExtractor(final StreamingService service, final LinkHandler linkHandler, WatchDataCache watchDataCache) {
        super(service, linkHandler);
        this.watchDataCache = watchDataCache;
        watchDataCache.init(linkHandler.getUrl());
    }

    /*//////////////////////////////////////////////////////////////////////////
    // Impl
    //////////////////////////////////////////////////////////////////////////*/

    @Nonnull
    @Override
    public String getName() throws ParsingException {
        assertPageFetched();
        String title = null;
//
//        try {
//            title = getTextFromObject(getVideoPrimaryInfoRenderer().getObject("title"));
//        } catch (final ParsingException ignored) {
//            // Age-restricted videos cause a ParsingException here
//        }

        if (isNullOrEmpty(title)) {
            title = playerResponse.getObject("videoDetails").getString("title");

            if (isNullOrEmpty(title)) {
                throw new ParsingException("Could not get name");
            }
        }

        return title;
    }

    @Override
    public long getStartAt() throws ParsingException {
        return getUploadDate().offsetDateTime().toEpochSecond() * 1000;
    }

    @Nullable
    @Override
    public String getTextualUploadDate() throws ParsingException {
        watchDataCache.shouldBeLive = true;
        final JsonObject liveDetails = playerMicroFormatRenderer.getObject(
                "liveBroadcastDetails");
        if (!liveDetails.getString("endTimestamp", EMPTY_STRING).isEmpty()) {
            // an ended live stream
            return liveDetails.getString("endTimestamp");
        } else if (!liveDetails.getString("startTimestamp", EMPTY_STRING).isEmpty()) {
            // a running live stream
            return liveDetails.getString("startTimestamp");
        } else if (getStreamType() == StreamType.LIVE_STREAM) {
            // this should never be reached, but a live stream without upload date is valid
            return null;
        }

        watchDataCache.shouldBeLive = false;
        if (!playerMicroFormatRenderer.getString("uploadDate", EMPTY_STRING).isEmpty()) {
            return playerMicroFormatRenderer.getString("uploadDate");
        } else if (!playerMicroFormatRenderer.getString("publishDate", EMPTY_STRING).isEmpty()) {
            return playerMicroFormatRenderer.getString("publishDate");
        }

        final String videoPrimaryInfoRendererDateText =
                getTextFromObject(getVideoPrimaryInfoRenderer().getObject("dateText"));

        if (videoPrimaryInfoRendererDateText != null) {
            if (videoPrimaryInfoRendererDateText.startsWith("Premiered")) {
                final String time = videoPrimaryInfoRendererDateText.substring(13);

                try { // Premiered 20 hours ago
                    final TimeAgoParser timeAgoParser = TimeAgoPatternsManager.getTimeAgoParserFor(
                            Localization.fromLocalizationCode("en"));
                    final OffsetDateTime parsedTime = timeAgoParser.parse(time).offsetDateTime();
                    return DateTimeFormatter.ISO_LOCAL_DATE.format(parsedTime);
                } catch (final Exception ignored) {
                }

                try { // Premiered Feb 21, 2020
                    final LocalDate localDate = LocalDate.parse(time,
                            DateTimeFormatter.ofPattern("MMM dd, yyyy", Locale.ENGLISH));
                    return DateTimeFormatter.ISO_LOCAL_DATE.format(localDate);
                } catch (final Exception ignored) {
                }

                try { // Premiered on 21 Feb 2020
                    final LocalDate localDate = LocalDate.parse(time,
                            DateTimeFormatter.ofPattern("dd MMM yyyy", Locale.ENGLISH));
                    return DateTimeFormatter.ISO_LOCAL_DATE.format(localDate);
                } catch (final Exception ignored) {
                }
            }

            try {
                // TODO: this parses English formatted dates only, we need a better approach to
                //  parse the textual date
                final LocalDate localDate = LocalDate.parse(videoPrimaryInfoRendererDateText,
                        DateTimeFormatter.ofPattern("dd MMM yyyy", Locale.ENGLISH));
                return DateTimeFormatter.ISO_LOCAL_DATE.format(localDate);
            } catch (final Exception e) {
                throw new ParsingException("Could not get upload date", e);
            }
        }

        throw new ParsingException("Could not get upload date");
    }

    @Override
    public DateWrapper getUploadDate() throws ParsingException {
        final String textualUploadDate = getTextualUploadDate();

        if (isNullOrEmpty(textualUploadDate)) {
            return null;
        }

        return new DateWrapper(YoutubeParsingHelper.parseDateFrom(textualUploadDate), true);
    }

    @Nonnull
    @Override
    public String getThumbnailUrl() throws ParsingException {
        assertPageFetched();
        try {
            final JsonArray thumbnails = playerResponse
                    .getObject("videoDetails")
                    .getObject("thumbnail")
                    .getArray("thumbnails");
            // the last thumbnail is the one with the highest resolution
            final String url = thumbnails
                    .getObject(thumbnails.size() - 1)
                    .getString("url");

            return fixThumbnailUrl(url);
        } catch (final Exception e) {
            throw new ParsingException("Could not get thumbnail url");
        }

    }

    @Nonnull
    @Override
    public List<Image> getThumbnails() throws ParsingException {
        assertPageFetched();
        try {
            return getImagesFromThumbnailsArray(playerResponse.getObject("videoDetails")
                    .getObject("thumbnail")
                    .getArray("thumbnails"));
        } catch (final Exception e) {
            throw new ParsingException("Could not get thumbnails");
        }
    }

    @Nonnull
    @Override
    public Description getDescription() throws ParsingException {
        assertPageFetched();
        // Description with more info on links
        try {
            final String description = getTextFromObject(
                    getVideoSecondaryInfoRenderer().getObject("description"),
                    true);
            if (!isNullOrEmpty(description)) {
                return new Description(description, Description.HTML);
            }
        } catch (final ParsingException ignored) {
            // Age-restricted videos cause a ParsingException here
        }

        String description = playerResponse.getObject("videoDetails")
                .getString("shortDescription");
        if (description == null) {
            final JsonObject descriptionObject = playerMicroFormatRenderer.getObject("description");
            description = getTextFromObject(descriptionObject);
        }

        // Raw non-html description
        return new Description(description, Description.PLAIN_TEXT);
    }

    @Override
    public long getLength() throws ParsingException {
        assertPageFetched();

        try {
            final String duration = playerResponse
                    .getObject("videoDetails")
                    .getString("lengthSeconds");
            return Long.parseLong(duration);
        } catch (final Exception e) {
            return getDurationFromFirstAdaptiveFormat(Arrays.asList(
                    androidStreamingData, tvHtml5SimplyEmbedStreamingData, webStreamingData));
        }
    }

    private int getDurationFromFirstAdaptiveFormat(@Nonnull final List<JsonObject> streamingDatas)
            throws ParsingException {
        for (final JsonObject streamingData : streamingDatas) {
            final JsonArray adaptiveFormats = streamingData.getArray(ADAPTIVE_FORMATS);
            if (adaptiveFormats.isEmpty()) {
                continue;
            }

            final String durationMs = adaptiveFormats.getObject(0)
                    .getString("approxDurationMs");
            try {
                return Math.round(Long.parseLong(durationMs) / 1000f);
            } catch (final NumberFormatException ignored) {
            }
        }

        throw new ParsingException("Could not get duration");
    }

    /**
     * Attempts to parse (and return) the offset to start playing the video from.
     *
     * @return the offset (in seconds), or 0 if no timestamp is found.
     */
    @Override
    public long getTimeStamp() throws ParsingException {
        final long timestamp =
                getTimestampSeconds("((#|&|\\?)t=\\d*h?\\d*m?\\d+s?)");

        if (timestamp == -2) {
            // Regex for timestamp was not found
            return 0;
        }
        return timestamp;
    }

    @Override
    public long getViewCount() throws ParsingException {
        String views = null;

        try {
            views = getTextFromObject(getVideoPrimaryInfoRenderer().getObject("viewCount")
                    .getObject("videoViewCountRenderer").getObject("viewCount"));
        } catch (final ParsingException ignored) {
            // Age-restricted videos cause a ParsingException here
        }

        if (isNullOrEmpty(views)) {
            views = playerResponse.getObject("videoDetails").getString("viewCount");

            if (isNullOrEmpty(views)) {
                throw new ParsingException("Could not get view count");
            }
        }

        if (views.toLowerCase().contains("no views")) {
            return 0;
        }

        return Long.parseLong(Utils.removeNonDigitCharacters(views));
    }

    @Override
    public long getLikeCount() throws ParsingException {
        assertPageFetched();

        // If ratings are not allowed, there is no like count available
        if (!playerResponse.getObject("videoDetails").getBoolean("allowRatings")) {
            return -1;
        }

        String likesString = null;

        try {
            final List<JsonObject> topLevelButtons = getVideoPrimaryInfoRenderer()
                    .getObject("videoActions")
                    .getObject("menuRenderer")
                    .getArray("topLevelButtons")
                    .stream()
                    .filter(JsonObject.class::isInstance)
                    .map(JsonObject.class::cast)
                    .collect(Collectors.toList());

            likesString = topLevelButtons.stream()
                    .map(btn -> btn.getObject("segmentedLikeDislikeButtonViewModel")
                            .getObject("likeButtonViewModel")
                            .getObject("likeButtonViewModel")
                            .getObject("toggleButtonViewModel")
                            .getObject("toggleButtonViewModel")
                            .getObject("defaultButtonViewModel")
                            .getObject("buttonViewModel")
                            .getString("accessibilityText"))
                    .filter(Objects::nonNull)
                    .findFirst()
                    .orElse(null);

            // Old - pre Dec 2023 way
            if (likesString == null) {
                likesString = getPreDec2023LikeString(topLevelButtons);
            }

            // If ratings are allowed and the likes string is null, it means that we couldn't
            // extract the (real) like count from accessibility data
            if (likesString == null) {
                throw new ParsingException("Could not get like count from accessibility data");
            }

            // This check only works with English localizations!
            if (likesString.toLowerCase().contains("no likes")) {
                return 0;
            }

            return Integer.parseInt(Utils.removeNonDigitCharacters(likesString));
        } catch (final NumberFormatException nfe) {
            throw new ParsingException("Could not parse \"" + likesString + "\" as an Integer",
                    nfe);
        } catch (final Exception e) {
            throw new ParsingException("Could not get like count", e);
        }
    }

    protected String getPreDec2023LikeString(final List<JsonObject> topLevelButtons)
            throws ParsingException {
        // Try first with the new video actions buttons data structure
        JsonObject likeToggleButtonRenderer = topLevelButtons.stream()
                .map(button -> button.getObject("segmentedLikeDislikeButtonRenderer")
                        .getObject("likeButton")
                        .getObject("toggleButtonRenderer"))
                .filter(toggleButtonRenderer -> !isNullOrEmpty(toggleButtonRenderer))
                .findFirst()
                .orElse(null);

        // Use the old video actions buttons data structure if the new one isn't returned
        if (likeToggleButtonRenderer == null) {
            /*
            In the old video actions buttons data structure, there are 3 ways to detect whether
            a button is the like button, using its toggleButtonRenderer:
            - checking whether toggleButtonRenderer.targetId is equal to watch-like;
            - checking whether toggleButtonRenderer.defaultIcon.iconType is equal to LIKE;
            - checking whether
              toggleButtonRenderer.toggleButtonSupportedData.toggleButtonIdData.id
              is equal to TOGGLE_BUTTON_ID_TYPE_LIKE.
            */
            likeToggleButtonRenderer = topLevelButtons.stream()
                    .map(topLevelButton -> topLevelButton.getObject("toggleButtonRenderer"))
                    .filter(toggleButtonRenderer -> "watch-like".equalsIgnoreCase(
                            toggleButtonRenderer.getString("targetId"))
                            || "LIKE".equalsIgnoreCase(
                            toggleButtonRenderer.getObject("defaultIcon")
                                    .getString("iconType"))
                            || "TOGGLE_BUTTON_ID_TYPE_LIKE".equalsIgnoreCase(
                            toggleButtonRenderer.getObject("toggleButtonSupportedData")
                                    .getObject("toggleButtonIdData")
                                    .getString("id")))
                    .findFirst()
                    .orElseThrow(() -> new ParsingException(
                            "The like button is missing even though ratings are enabled"));
        }

        // Use one of the accessibility strings available (this one has the same path as the
        // one used for comments' like count extraction)
        String likesString = likeToggleButtonRenderer.getObject("accessibilityData")
                .getObject("accessibilityData")
                .getString("label");

        // Use the other accessibility string available which contains the exact like count
        if (likesString == null) {
            likesString = likeToggleButtonRenderer.getObject("accessibility")
                    .getString("label");
        }

        // Last method: use the defaultText's accessibility data, which contains the exact like
        // count too, except when it is equal to 0, where a localized string is returned instead
        if (likesString == null) {
            likesString = likeToggleButtonRenderer.getObject("defaultText")
                    .getObject("accessibility")
                    .getObject("accessibilityData")
                    .getString("label");
        }
        return likesString;
    }


    @Nonnull
    @Override
    public String getUploaderUrl() throws ParsingException {
        assertPageFetched();

        // Don't use the id in the videoSecondaryRenderer object to get real id of the uploader
        // The difference between the real id of the channel and the displayed id is especially
        // visible for music channels and autogenerated channels.
        final String uploaderId = playerResponse.getObject("videoDetails").getString("channelId");
        if (!isNullOrEmpty(uploaderId)) {
            return YoutubeChannelLinkHandlerFactory.getInstance().getUrl("channel/" + uploaderId);
        }

        throw new ParsingException("Could not get uploader url");
    }

    @Nonnull
    @Override
    public String getUploaderName() throws ParsingException {
        assertPageFetched();

        // Don't use the name in the videoSecondaryRenderer object to get real name of the uploader
        // The difference between the real name of the channel and the displayed name is especially
        // visible for music channels and autogenerated channels.
        final String uploaderName = playerResponse.getObject("videoDetails").getString("author");
        if (isNullOrEmpty(uploaderName)) {
            return "Unknown";
        }

        return uploaderName;
    }

    @Override
    public boolean isUploaderVerified() throws ParsingException {
        return YoutubeParsingHelper.isVerified(
                getVideoSecondaryInfoRenderer()
                        .getObject("owner")
                        .getObject("videoOwnerRenderer")
                        .getArray("badges"));
    }

    @Nonnull
    @Override
    public String getUploaderAvatarUrl() throws ParsingException {
        assertPageFetched();

        JsonArray thumbnails = getVideoSecondaryInfoRenderer()
                .getObject("owner")
                .getObject("videoOwnerRenderer")
                .getObject("thumbnail")
                .getArray("thumbnails");
        final String url = thumbnails
                .getObject(thumbnails.size() - 1)
                .getString("url");

        if (isNullOrEmpty(url)) {
            if (ageLimit == NO_AGE_LIMIT) {
                throw new ParsingException("Could not get uploader avatar URL");
            }

            return EMPTY_STRING;
        }

        return fixThumbnailUrl(url);
    }

    @Nonnull
    @Override
    public List<Image> getUploaderAvatars() throws ParsingException {
        assertPageFetched();

        final List<Image> imageList = getImagesFromThumbnailsArray(
                getVideoSecondaryInfoRenderer().getObject("owner")
                        .getObject("videoOwnerRenderer")
                        .getObject("thumbnail")
                        .getArray("thumbnails"));

        if (imageList.isEmpty() && ageLimit == NO_AGE_LIMIT) {
            throw new ParsingException("Could not get uploader avatars");
        }

        return imageList;
    }

    @Override
    public long getUploaderSubscriberCount() throws ParsingException {
        final JsonObject videoOwnerRenderer = JsonUtils.getObject(videoSecondaryInfoRenderer,
                "owner.videoOwnerRenderer");
        if (!videoOwnerRenderer.has("subscriberCountText")) {
            return UNKNOWN_SUBSCRIBER_COUNT;
        }
        try {
            return Utils.mixedNumberWordToLong(getTextFromObject(videoOwnerRenderer
                    .getObject("subscriberCountText")));
        } catch (final NumberFormatException e) {
            throw new ParsingException("Could not get uploader subscriber count", e);
        }
    }

    @Nonnull
    @Override
    public String getDashMpdUrl() throws ParsingException {
        assertPageFetched();

        // There is no DASH manifest available in the iOS clients and the DASH manifest of the
        // Android client doesn't contain all available streams (mainly the WEBM ones)
        return getManifestUrl(
                "dash",
                Arrays.asList(androidStreamingData));
    }

    @Nonnull
    @Override
    public String getHlsUrl() throws ParsingException {
        assertPageFetched();

        // Return HLS manifest of the iOS client first because on livestreams, the HLS manifest
        // returned has separated audio and video streams
        // Also, on videos, non-iOS clients don't have an HLS manifest URL in their player response
        return getManifestUrl(
                "hls",
                Arrays.asList(androidStreamingData, tvHtml5SimplyEmbedStreamingData, webStreamingData));
    }

    @Nonnull
    private static String getManifestUrl(@Nonnull final String manifestType,
                                         @Nonnull final List<JsonObject> streamingDataObjects) {
        final String manifestKey = manifestType + "ManifestUrl";

        return streamingDataObjects.stream()
                .filter(Objects::nonNull)
                .map(streamingDataObject -> streamingDataObject.getString(manifestKey))
                .filter(Objects::nonNull)
                .findFirst()
                .orElse(EMPTY_STRING);
    }

    // Cache for batch-processed streams
    private List<AudioStream> cachedAudioStreams;
    private List<VideoStream> cachedVideoStreams;
    private List<VideoStream> cachedVideoOnlyStreams;
    private boolean streamsCached = false;

    /**
     * Pre-fetch and batch-process all streams in a single API call.
     * This method collects all audio, video, and video-only streams,
     * then performs batch deobfuscation in one request.
     */
    private void ensureStreamsAreCached() throws ExtractionException {
        if (streamsCached) {
            return;
        }

        assertPageFetched();
        final String videoId = getId();

        // Collect all ItagInfo objects from all stream types
        final List<ItagInfo> allItagInfos = new ArrayList<>();
        final int audioStartIndex = 0;
        final int videoStartIndex;
        final int videoOnlyStartIndex;

        try {
            // Collect audio streams
            java.util.stream.Stream.of(
                    new Pair<>(androidStreamingData, androidCpn),
                    new Pair<>(tvHtml5SimplyEmbedStreamingData, tvHtml5SimplyEmbedCpn)
            )
                    .flatMap(pair -> getStreamsFromStreamingDataKey(videoId, pair.getFirst(),
                            ADAPTIVE_FORMATS, ItagItem.ItagType.AUDIO, pair.getSecond()))
                    .forEachOrdered(allItagInfos::add);

            videoStartIndex = allItagInfos.size();

            // Collect video streams
            java.util.stream.Stream.of(
                    new Pair<>(androidStreamingData, androidCpn),
                    new Pair<>(tvHtml5SimplyEmbedStreamingData, tvHtml5SimplyEmbedCpn)
            )
                    .flatMap(pair -> getStreamsFromStreamingDataKey(videoId, pair.getFirst(),
                            FORMATS, ItagItem.ItagType.VIDEO, pair.getSecond()))
                    .forEachOrdered(allItagInfos::add);

            videoOnlyStartIndex = allItagInfos.size();

            // Collect video-only streams
            java.util.stream.Stream.of(
                    new Pair<>(androidStreamingData, androidCpn),
                    new Pair<>(tvHtml5SimplyEmbedStreamingData, tvHtml5SimplyEmbedCpn)
            )
                    .flatMap(pair -> getStreamsFromStreamingDataKey(videoId, pair.getFirst(),
                            ADAPTIVE_FORMATS, ItagItem.ItagType.VIDEO_ONLY, pair.getSecond()))
                    .forEachOrdered(allItagInfos::add);

            // Batch deobfuscate ALL streams in a single API call
            batchDeobfuscateItagUrls(videoId, allItagInfos);

            // Now build the separate stream lists
            cachedAudioStreams = new ArrayList<>();
            for (int i = audioStartIndex; i < videoStartIndex; i++) {
                final AudioStream stream = getAudioStreamBuilderHelper().apply(allItagInfos.get(i));
                if (!Stream.containSimilarStream(stream, cachedAudioStreams)) {
                    cachedAudioStreams.add(stream);
                }
            }
            Collections.sort(cachedAudioStreams, Comparator.comparingInt(AudioStream::getBitrate).reversed());

            cachedVideoStreams = new ArrayList<>();
            for (int i = videoStartIndex; i < videoOnlyStartIndex; i++) {
                final VideoStream stream = getVideoStreamBuilderHelper(false).apply(allItagInfos.get(i));
                if (!Stream.containSimilarStream(stream, cachedVideoStreams)) {
                    cachedVideoStreams.add(stream);
                }
            }

            cachedVideoOnlyStreams = new ArrayList<>();
            for (int i = videoOnlyStartIndex; i < allItagInfos.size(); i++) {
                final VideoStream stream = getVideoStreamBuilderHelper(true).apply(allItagInfos.get(i));
                if (!Stream.containSimilarStream(stream, cachedVideoOnlyStreams)) {
                    cachedVideoOnlyStreams.add(stream);
                }
            }

            streamsCached = true;

        } catch (final Exception e) {
            throw new ParsingException("Could not get streams", e);
        }
    }

    @Override
    public List<AudioStream> getAudioStreams() throws ExtractionException {
        ensureStreamsAreCached();
        return cachedAudioStreams;
    }

    @Override
    public List<VideoStream> getVideoStreams() throws ExtractionException {
        ensureStreamsAreCached();
        return cachedVideoStreams;
    }

    @Override
    public List<VideoStream> getVideoOnlyStreams() throws ExtractionException {
        ensureStreamsAreCached();
        return cachedVideoOnlyStreams;
    }

    @Override
    @Nonnull
    public List<SubtitlesStream> getSubtitlesDefault() throws ParsingException {
        return getSubtitles(MediaFormat.TTML);
    }

    @Override
    @Nonnull
    public List<SubtitlesStream> getSubtitles(final MediaFormat format) throws ParsingException {
        assertPageFetched();

        if(playerCaptionsTracklistRenderer == null) {
            return new ArrayList<>();
        }

        // We cannot store the subtitles list because the media format may change
        final List<SubtitlesStream> subtitlesToReturn = new ArrayList<>();
        final JsonArray captionsArray = playerCaptionsTracklistRenderer.getArray("captionTracks");
        // TODO: use this to apply auto translation to different language from a source language
        // final JsonArray autoCaptionsArray = renderer.getArray("translationLanguages");

        String contentLanguage = ServiceList.YouTube.getContentLanguage().getLocalizationCode();
        boolean hasFoundContentLanguage = false;
        SubtitlesStream translatableSubtitle = null;
        boolean hasDuplicateEntries = false;

        for (int i = 0; i < captionsArray.size(); i++) {
            final String languageCode = captionsArray.getObject(i).getString("languageCode");
            final String baseUrl = captionsArray.getObject(i).getString("baseUrl");
            final String vssId = captionsArray.getObject(i).getString("vssId");

            if (languageCode != null && baseUrl != null && vssId != null) {
                final boolean isAutoGenerated = vssId.startsWith("a.");
                final String cleanUrl = baseUrl
                        // Remove preexisting format if exists
                        .replaceAll("&fmt=[^&]*", "")
                        // Remove translation language
                        .replaceAll("&tlang=[^&]*", "");

                String remoteSubtitleUrl = cleanUrl + "&fmt=" + format.getSuffix();

                if (i == 0) { // only check once
                    hasDuplicateEntries = SubtitleDeduplicator.hasDuplicateEntries(remoteSubtitleUrl);
                }

                subtitlesToReturn.add(new SubtitlesStream.Builder()
                        .setContent(hasDuplicateEntries? SubtitleDeduplicator.getDeduplicatedContent(remoteSubtitleUrl): remoteSubtitleUrl, !hasDuplicateEntries)
                        .setMediaFormat(format)
                        .setLanguageCode(languageCode)
                        .setAutoGenerated(isAutoGenerated)
                        .build());

                hasFoundContentLanguage = hasFoundContentLanguage || contentLanguage.equals(languageCode);
                if (translatableSubtitle == null) {
                    translatableSubtitle = subtitlesToReturn.get(subtitlesToReturn.size() - 1);
                }
            }
        }

        if (translatableSubtitle != null && !hasFoundContentLanguage && ServiceList.YouTube.getShowAutoTranslatedSubtitles()) {
            if (!hasDuplicateEntries) {
                String autoTranslatedContent = SubtitleDeduplicator.getValidSubtitleContent(translatableSubtitle.getContent() + "&tlang=" + contentLanguage);
                if(autoTranslatedContent != null) {
                    subtitlesToReturn.add(new SubtitlesStream.Builder()
                            .setContent(autoTranslatedContent, false)
                            .setMediaFormat(format)
                            .setLanguageCode(contentLanguage)
                            .setAutoGenerated(false)
                            .build());
                }
            }
        }

        return subtitlesToReturn;
    }


    @Override
    public boolean isSupportComments() throws ParsingException {
        return !getStreamType().equals(StreamType.LIVE_STREAM);
    }

    @Override
    public StreamType getStreamType() {
        return streamType;
    }

    public void setStreamType() {
        if (playerResponse.getObject("playabilityStatus").has("liveStreamability")) {
            streamType = StreamType.LIVE_STREAM;
        } else if (playerResponse.getObject("videoDetails").getBoolean("isPostLiveDvr", false)) {
            streamType = StreamType.POST_LIVE_STREAM;
        } else {
            streamType = StreamType.VIDEO_STREAM;
        }
        watchDataCache.streamType = streamType;
    }

    @Nullable
    @Override
    public MultiInfoItemsCollector getRelatedItems() throws ExtractionException {
        assertPageFetched();

        if (getAgeLimit() != NO_AGE_LIMIT) {
            return null;
        }

        try {
            final MultiInfoItemsCollector collector = new MultiInfoItemsCollector(getServiceId());

            final JsonArray results = nextResponse
                    .getObject("contents")
                    .getObject("twoColumnWatchNextResults")
                    .getObject("secondaryResults")
                    .getObject("secondaryResults")
                    .getArray("results");

            final TimeAgoParser timeAgoParser = getTimeAgoParser();
            results.stream()
                    .filter(JsonObject.class::isInstance)
                    .map(JsonObject.class::cast)
                    .map(result -> {
                        if (result.has("compactVideoRenderer")) {
                            return new YoutubeStreamInfoItemExtractor(
                                    result.getObject("compactVideoRenderer"), timeAgoParser);
                        } else if (result.has("compactRadioRenderer")) {
                            return new YoutubeMixOrPlaylistInfoItemExtractor(
                                    result.getObject("compactRadioRenderer"));
                        } else if (result.has("compactPlaylistRenderer")) {
                            return new YoutubeMixOrPlaylistInfoItemExtractor(
                                    result.getObject("compactPlaylistRenderer"));
                        } else if (result.has("lockupViewModel")) {
                            final JsonObject lockupViewModel = result.getObject("lockupViewModel");
                            if ("LOCKUP_CONTENT_TYPE_PLAYLIST".equals(
                                    lockupViewModel.getString("contentType"))) {
                                return new YoutubeMixOrPlaylistLockupInfoItemExtractor(
                                        lockupViewModel);
                            }
                            else if ("LOCKUP_CONTENT_TYPE_VIDEO".equals(
                                    lockupViewModel.getString("contentType"))){
                                return new YoutubeLockupStreamInfoItemExtractor(lockupViewModel, timeAgoParser);
                            }
                        }
                        return null;
                    })
                    .filter(Objects::nonNull)
                    .forEach(collector::commit);

            if (ServiceList.YouTube.getFilterTypes().contains("related_item")) {
                collector.applyBlocking(ServiceList.YouTube.getFilterConfig());
            }
            return collector;
        } catch (final Exception e) {
            throw new ParsingException("Could not get related videos", e);
        }
    }

    @Override
    public long getDislikeCount() throws ParsingException {
        try {
            return dislikeData.getLong("dislikes");
        } catch (Exception e) {
            return -1;
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public String getErrorMessage() {
        try {
            return getTextFromObject(playerResponse.getObject("playabilityStatus")
                    .getObject("errorScreen").getObject("playerErrorMessageRenderer")
                    .getObject("reason"));
        } catch (final ParsingException | NullPointerException e) {
            return null; // No error message
        }
    }

    /*//////////////////////////////////////////////////////////////////////////
    // Fetch page
    //////////////////////////////////////////////////////////////////////////*/

    private static final String FORMATS = "formats";
    private static final String ADAPTIVE_FORMATS = "adaptiveFormats";
    private static final String STREAMING_DATA = "streamingData";
    private static final String PLAYER = "player";
    private static final String NEXT = "next";
    private static final String SIGNATURE_CIPHER = "signatureCipher";
    private static final String CIPHER = "cipher";



    @Override
    public void onFetchPage(@Nonnull final Downloader downloader)
            throws IOException, ExtractionException {

        final String videoId = getId();
        final Localization localization = getExtractorLocalization();
        final ContentCountry contentCountry = getExtractorContentCountry();

        final Localization tempLocalizationForTitle = getTempLocalization();

        errors.clear();

        CancellableCall webPageCall = YoutubeParsingHelper.getWebPlayerResponse(
                tempLocalizationForTitle, contentCountry, videoId, this);

        CancellableCall androidCall = null;
        CancellableCall tvCall = null;
        CancellableCall webCall = null;

        if (StringUtils.isBlank(ServiceList.YouTube.getTokens())) {
            androidCall = fetchAndroidMobileJsonPlayer(contentCountry, localization, videoId);
        } else {
            tvCall = fetchTvHtml5EmbedJsonPlayer(contentCountry, localization, videoId);
            webCall = fetchWebJsonPlayer(contentCountry, localization, videoId);
        }

        final byte[] body = JsonWriter.string(
                prepareDesktopJsonBuilder(localization, contentCountry)
                        .value(VIDEO_ID, videoId)
                        .value(CONTENT_CHECK_OK, true)
                        .value(RACY_CHECK_OK, true)
                        .done())
                .getBytes(StandardCharsets.UTF_8);
       CancellableCall nextDataCall = getJsonPostResponseAsync(NEXT, body, localization, new Downloader.AsyncCallback() {
            @Override
            public void onSuccess(Response response) throws ExtractionException {
                try {
                    nextResponse = JsonUtils.toJsonObject(getValidJsonResponseBody(response));
                } catch (Exception e) {
                    e.printStackTrace();
                    errors.add(e);
                }
            }
        });

        // fetch dislike

        CancellableCall dislikeCall = downloader.getAsync("https://returnyoutubedislikeapi.com/votes?" + "videoId=" + videoId, new Downloader.AsyncCallback() {
            @Override
            public void onSuccess(Response response) throws ExtractionException {
                try {
                    dislikeData = new JSONObject(getValidJsonResponseBody(response));
                } catch (JSONException | MalformedURLException e) {
                    e.printStackTrace();
                }
            }
        });
        long startTime = System.nanoTime();
        do {
            if (((StringUtils.isBlank(ServiceList.YouTube.getTokens()) && androidCall.isFinished())
                    || (StringUtils.isNotBlank(ServiceList.YouTube.getTokens()) && webCall.isFinished() && tvCall.isFinished())) &&
                    webPageCall.isFinished() && nextDataCall.isFinished()) {
                break;
            }
        } while (TimeUnit.NANOSECONDS.toSeconds(System.nanoTime() - startTime) <= ServiceList.YouTube.getLoadingTimeout());

        if (((StringUtils.isBlank(ServiceList.YouTube.getTokens()) && androidStreamingData == null)
                || ((StringUtils.isNotBlank(ServiceList.YouTube.getTokens()) && webStreamingData == null && tvHtml5SimplyEmbedStreamingData == null)))
                || nextResponse == null) {
            for (Throwable e: errors) {
                if (e instanceof AntiBotException) {
                    throw (AntiBotException) e;
                }
                if (e instanceof ContentNotAvailableException) {
                    throw (ContentNotAvailableException) e;
                }
                throw new ExtractionException(e) ;
            }
            throw new ExtractionException("Error occurs when fetching the page. Try increase the loading timeout in Settings.");
        }

        // Check playability status from the actual stream data source
        if (playerResponse != null) {
            checkPlayabilityStatus(playerResponse.getObject("playabilityStatus"), videoId);
            setStreamType();
        }
    }



    public static JsonObject checkPlayabilityStatus(@Nonnull JsonObject playabilityStatus, String videoId)
            throws ParsingException {
        String status = playabilityStatus.getString("status");
        if (status == null || status.equalsIgnoreCase("ok")) {
            return null;
        }

        try {
            JsonObject response = JsonParser.object().from(getWebPlayerResponseSync(videoId).responseBody());
            playabilityStatus = response.getObject("playabilityStatus");
            status = playabilityStatus.getString("status");
            if (status == null || status.equalsIgnoreCase("ok")) {
                return response;
            }
        } catch (IOException | ExtractionException | JsonParserException e) {
            // this should not happen
        }


        final String reason = playabilityStatus.getString("reason");

        if (status.equalsIgnoreCase("login_required")) {
            if (reason == null) {
                final String message = playabilityStatus.getArray("messages").getString(0);
                if (message != null && message.contains("private")) {
                    throw new PrivateContentException("This video is private");
                }
            } else if (reason.contains("age")) {
                throw new AgeRestrictedContentException(
                        "This age-restricted video cannot be watched anonymously");
            }
        }

        if ((status.equalsIgnoreCase("unplayable") || status.equalsIgnoreCase("error"))
                && reason != null) {
            if (reason.contains("Music Premium")) {
                throw new YoutubeMusicPremiumContentException();
            }

            if (reason.contains("payment")) {
                throw new PaidContentException("This video is a paid video");
            }

            if (reason.contains("members-only")) {
                throw new PaidContentException("This video is only available"
                        + " for members of the channel of this video");
            }

            if (reason.contains("unavailable")) {
                final String detailedErrorMessage = getTextFromObject(playabilityStatus
                        .getObject("errorScreen")
                        .getObject("playerErrorMessageRenderer")
                        .getObject("subreason"));
                if (detailedErrorMessage != null && detailedErrorMessage.contains("country")) {
                    throw new GeographicRestrictionException(
                            "This video is not available in client's country.");
                } else {
                    if(detailedErrorMessage != null) {
                        throw new ContentNotAvailableException(detailedErrorMessage);
                    }
                    throw new ContentNotAvailableException(reason);
                }
            }
        }
        if (reason != null && reason.contains("Sign in to confirm")) {
            throw new AntiBotException(reason);
        }

        if (reason != null && reason.contains("This live event will begin in")) {
            throw new LiveNotStartException(reason);
        }

        if (reason != null && reason.contains("Premieres in")) {
            throw new VideoNotReleaseException(reason);
        }

        throw new ContentNotAvailableException("Got error: \"" + reason + "\"");
    }

    /**
     * Fetch the Android Mobile API and assign the streaming data to the androidStreamingData JSON
     * object.
     */
    private CancellableCall fetchAndroidMobileJsonPlayer(@Nonnull final ContentCountry contentCountry,
                                              @Nonnull final Localization localization,
                                              @Nonnull final String videoId)
            throws IOException, ExtractionException {
        final InnertubeClientRequestInfo innertubeClientRequestInfo =
                InnertubeClientRequestInfo.ofAndroidClient();

        final Map<String, List<String>> headers = new HashMap<>();
        headers.put("Content-Type", singletonList("application/json"));
        headers.put("User-Agent", singletonList(getAndroidUserAgent(localization)));
        headers.put("X-Goog-Api-Format-Version", singletonList("2"));

        String visitorData = YoutubeParsingHelper.getVisitorDataFromInnertube(innertubeClientRequestInfo,
                localization, contentCountry, headers, YOUTUBEI_V1_GAPIS_URL, null, false);
        androidCpn = generateContentPlaybackNonce();
        final byte[] mobileBody = JsonWriter.string(
                prepareAndroidMobileJsonBuilder(localization, contentCountry, visitorData)
                        .object("playerRequest")
                        .value(VIDEO_ID, videoId)
                        .value(CPN, androidCpn)
                        .value(CONTENT_CHECK_OK, true)
                        .value(RACY_CHECK_OK, true)
                        .end()
                        .value("disablePlayerResponse", false)
                        .done())
                .getBytes(StandardCharsets.UTF_8);

        final Downloader.AsyncCallback callback = new Downloader.AsyncCallback() {
            @Override
            public void onSuccess(Response response) throws ExtractionException {
                try {
                    final JsonObject androidPlayerResponse = JsonUtils.toJsonObject(getValidJsonResponseBody(response));
                    final JsonObject playerResponseObject = androidPlayerResponse.getObject("playerResponse");
                    if (isPlayerResponseNotValid(playerResponseObject, videoId)) {
                        return;
                    }

                    YoutubeStreamExtractor.this.playerResponse = playerResponseObject;

                    final JsonObject streamingData = playerResponseObject.getObject(STREAMING_DATA);
                    if (!isNullOrEmpty(streamingData)) {
                        androidStreamingData = streamingData;
                        if (isNullOrEmpty(playerCaptionsTracklistRenderer)) {
                            playerCaptionsTracklistRenderer = playerResponseObject.getObject("captions")
                                    .getObject("playerCaptionsTracklistRenderer");
                        }
                    }
                } catch (Exception e) {
                    e.printStackTrace();
                    errors.add(e);
                }
            }
        };

        return getJsonAndroidPostResponseAsync("reel/reel_item_watch",
                mobileBody, localization, "&t=" + generateTParameter()
                        + "&id=" + videoId + "&$fields=playerResponse", callback);
    }

    /**
     * Fetch the iOS Mobile API and assign the streaming data to the iosStreamingData JSON
     * object.
     */
    private CancellableCall fetchIosMobileJsonPlayer(@Nonnull final ContentCountry contentCountry,
                                                     @Nonnull final Localization localization,
                                                     @Nonnull final String videoId)
            throws IOException, ExtractionException {
        iosCpn = generateContentPlaybackNonce();
        final byte[] mobileBody = JsonWriter.string(
                prepareIosMobileJsonBuilder(localization, contentCountry)
                        .value(VIDEO_ID, videoId)
                        .value(CPN, iosCpn)
                        .value(CONTENT_CHECK_OK, true)
                        .value(RACY_CHECK_OK, true)
                        .done())
                .getBytes(StandardCharsets.UTF_8);

        final Downloader.AsyncCallback callback = new Downloader.AsyncCallback() {
            @Override
            public void onSuccess(Response response) throws ExtractionException {
                try {
                    JsonObject iosPlayerResponse = JsonUtils.toJsonObject(getValidJsonResponseBody(response));
                    if (isPlayerResponseNotValid(iosPlayerResponse, videoId)) {
                        if (iosPlayerResponse.toString().contains("Sign in to confirm")) {
                            throw new AntiBotException("IOS player response is not valid");
                        }
                        throw new ExtractionException("IOS player response is not valid");
                    }

                    YoutubeStreamExtractor.this.playerResponse = iosPlayerResponse;

                    final JsonObject streamingData = iosPlayerResponse.getObject(STREAMING_DATA);
                    if (!isNullOrEmpty(streamingData)) {
                        iosStreamingData = streamingData;
                        playerCaptionsTracklistRenderer = iosPlayerResponse.getObject("captions")
                                .getObject("playerCaptionsTracklistRenderer");
                    }
                } catch (Exception e) {
                    e.printStackTrace();
                    errors.add(e);
                }
            }
        };

        return getJsonIosPostResponseAsync(PLAYER,
                mobileBody, localization, "&t=" + generateTParameter()
                        + "&id=" + videoId, callback);
    }

    /**
     * Download the {@code TVHTML5_SIMPLY_EMBEDDED_PLAYER} JSON player as an embed client to bypass
     * some age-restrictions and assign the streaming data to the {@code html5StreamingData} JSON
     * object.
     *
     * @param contentCountry the content country to use
     * @param localization   the localization to use
     * @param videoId        the video id
     */
    private CancellableCall fetchWebJsonPlayer(@Nonnull final ContentCountry contentCountry,
                                             @Nonnull final Localization localization,
                                             @Nonnull final String videoId)
            throws IOException, ExtractionException {
        webCpn = generateContentPlaybackNonce();

        final Downloader.AsyncCallback callback = new Downloader.AsyncCallback() {
            @Override
            public void onSuccess(Response response) {
                JsonObject webPlayerResponse = null;
                try {
                    webPlayerResponse = JsonUtils.toJsonObject(getValidJsonResponseBody(response));
                    if (isPlayerResponseNotValid(webPlayerResponse, videoId)) {
                        if (webPlayerResponse.toString().contains("Sign in to confirm")) {
                            throw new AntiBotException("Web player response is not valid");
                        }
                        throw new ExtractionException("Web player response is not valid");
                    }

                    YoutubeStreamExtractor.this.playerResponse = webPlayerResponse;

                    final JsonObject streamingData = webPlayerResponse.getObject(STREAMING_DATA);
                    if (!isNullOrEmpty(streamingData)) {
                        webStreamingData = streamingData;
                        playerCaptionsTracklistRenderer = webPlayerResponse.getObject("captions")
                                .getObject("playerCaptionsTracklistRenderer");
                    }
                } catch (Exception e) {
                    e.printStackTrace();
                    errors.add(e);
                }
            }
        };

        return getLoggedJsonPostResponseAsync(PLAYER,
                createWebEmbedPlayerBody(localization,
                        contentCountry,
                        videoId,
                        YoutubeJavaScriptPlayerManager.getSignatureTimestamp(videoId),
                        webCpn), localization, callback);
    }

    private CancellableCall fetchTvHtml5EmbedJsonPlayer(@Nonnull final ContentCountry contentCountry,
                                                        @Nonnull final Localization localization,
                                                        @Nonnull final String videoId)
            throws IOException, ExtractionException {
        tvHtml5SimplyEmbedCpn = generateContentPlaybackNonce();

        final Downloader.AsyncCallback callback = new Downloader.AsyncCallback() {
            @Override
            public void onSuccess(Response response) {
                JsonObject tvHtml5EmbedPlayerResponse = null;
                try {
                    tvHtml5EmbedPlayerResponse = JsonUtils.toJsonObject(getValidJsonResponseBody(response));
                    if (isPlayerResponseNotValid(tvHtml5EmbedPlayerResponse, videoId)) {
                        if (tvHtml5EmbedPlayerResponse.toString().contains("Sign in to confirm")) {
                            throw new AntiBotException("TVHTML5 player response is not valid");
                        }
                        throw new ExtractionException("TVHTML5 embed player response is not valid");
                    }

                    YoutubeStreamExtractor.this.playerResponse = tvHtml5EmbedPlayerResponse;

                    final JsonObject streamingData = tvHtml5EmbedPlayerResponse.getObject(STREAMING_DATA);
                    if (!isNullOrEmpty(streamingData)) {
                        tvHtml5SimplyEmbedStreamingData = streamingData;
                        playerCaptionsTracklistRenderer = tvHtml5EmbedPlayerResponse.getObject("captions")
                                .getObject("playerCaptionsTracklistRenderer");
                    }
                } catch (Exception e) {
                    e.printStackTrace();
                    errors.add(e);
                }
            }
        };

        return getLoggedJsonPostResponseAsync(PLAYER,
                createTvHtml5EmbedPlayerBody(localization,
                        contentCountry,
                        videoId,
                        YoutubeJavaScriptPlayerManager.getSignatureTimestamp(videoId),
                        tvHtml5SimplyEmbedCpn), localization, callback);
    }


    /**
     * Checks whether an additional player response is not valid.
     *
     * <p>
     * If YouTube detect that requests come from a third party client, they may replace the real
     * player response by another one of a video saying that this content is not available on this
     * app and to watch it on the latest version of YouTube.
     * </p>
     *
     * <p>
     * We can detect this by checking whether the video ID of the player response returned is the
     * same as the one requested by the extractor.
     * </p>
     *
     * <p>
     * This behavior has been already observed on the {@code ANDROID} client, see
     * <a href="https://github.com/TeamNewPipe/NewPipe/issues/8713">
     *     https://github.com/TeamNewPipe/NewPipe/issues/8713</a>.
     * </p>
     *
     * @param additionalPlayerResponse an additional response to the one of the {@code HTML5}
     *                                 client used
     * @param videoId                  the video ID of the content requested
     * @return whether the video ID of the player response is not equal to the one requested
     */
    public static boolean isPlayerResponseNotValid(
            @Nonnull final JsonObject additionalPlayerResponse,
            @Nonnull final String videoId) {
        return !videoId.equals(additionalPlayerResponse.getObject("videoDetails")
                .getString("videoId", ""));
    }


    /*//////////////////////////////////////////////////////////////////////////
    // Utils
    //////////////////////////////////////////////////////////////////////////*/

    @Nonnull
    private JsonObject getVideoPrimaryInfoRenderer() {
        if (videoPrimaryInfoRenderer != null) {
            return videoPrimaryInfoRenderer;
        }

        videoPrimaryInfoRenderer = getVideoInfoRenderer("videoPrimaryInfoRenderer");
        return videoPrimaryInfoRenderer;
    }

    @Nonnull
    private JsonObject getVideoSecondaryInfoRenderer() {
        if (videoSecondaryInfoRenderer != null) {
            return videoSecondaryInfoRenderer;
        }

        videoSecondaryInfoRenderer = getVideoInfoRenderer("videoSecondaryInfoRenderer");
        return videoSecondaryInfoRenderer;
    }

    @Nonnull
    private JsonObject getVideoInfoRenderer(@Nonnull final String videoRendererName) {
        return nextResponse.getObject("contents")
                .getObject("twoColumnWatchNextResults")
                .getObject("results")
                .getObject("results")
                .getArray("contents")
                .stream()
                .filter(JsonObject.class::isInstance)
                .map(JsonObject.class::cast)
                .filter(content -> content.has(videoRendererName))
                .map(content -> content.getObject(videoRendererName))
                .findFirst()
                .orElse(new JsonObject());
    }

    @Nonnull
    public <T extends Stream> List<T> getItags(
            final String streamingDataKey,
            final ItagItem.ItagType itagTypeWanted,
            final java.util.function.Function<ItagInfo, T> streamBuilderHelper,
            final String streamTypeExceptionMessage) throws ParsingException {
        try {
            final String videoId = getId();
            final List<T> streamList = new ArrayList<>();

            // First pass: collect all ItagInfo objects without deobfuscating URLs
            final List<ItagInfo> itagInfoList = new ArrayList<>();

            java.util.stream.Stream.of(
                     /*
                    Use the iosStreamingData object first because there is no n param and no
                    signatureCiphers in streaming URLs of the iOS client
                    The androidStreamingData is used as second way as it isn't used on livestreams,
                    it doesn't return all available streams, and the Android client extraction is
                    more likely to break
                    As age-restricted videos are not common, use tvHtml5SimplyEmbedStreamingData
                    last, which will be the only one not empty for age-restricted content
                     */
                    new Pair<>(androidStreamingData, androidCpn),
                    new Pair<>(tvHtml5SimplyEmbedStreamingData, tvHtml5SimplyEmbedCpn)

            )
                    .flatMap(pair -> getStreamsFromStreamingDataKey(videoId, pair.getFirst(),
                            streamingDataKey, itagTypeWanted, pair.getSecond()))
                    .forEachOrdered(itagInfoList::add);

            // Second pass: batch deobfuscate all URLs if needed
            batchDeobfuscateItagUrls(videoId, itagInfoList);

            // Third pass: build stream objects
            for (final ItagInfo itagInfo : itagInfoList) {
                final T stream = streamBuilderHelper.apply(itagInfo);
                if (!Stream.containSimilarStream(stream, streamList)) {
                    streamList.add(stream);
                }
            }

            return streamList;
        } catch (final Exception e) {
            throw new ParsingException(
                    "Could not get " + streamTypeExceptionMessage + " streams", e);
        }
    }

    /**
     * Get the stream builder helper which will be used to build {@link AudioStream}s in
     * {@link #getItags(String, ItagItem.ItagType, java.util.function.Function, String)}
     *
     * <p>
     * The {@code StreamBuilderHelper} will set the following attributes in the
     * {@link AudioStream}s built:
     * <ul>
     *     <li>the {@link ItagItem}'s id of the stream as its id;</li>
     *     <li>{@link ItagInfo#getContent()} and {@link ItagInfo#getIsUrl()} as its content and
     *     and as the value of {@code isUrl};</li>
     *     <li>the media format returned by the {@link ItagItem} as its media format;</li>
     *     <li>its average bitrate with the value returned by {@link
     *     ItagItem#getAverageBitrate()};</li>
     *     <li>the {@link ItagItem};</li>
     *     <li>the {@link DeliveryMethod#DASH DASH delivery method}, for OTF streams, live streams
     *     and ended streams.</li>
     * </ul>
     * </p>
     *
     * <p>
     * Note that the {@link ItagItem} comes from an {@link ItagInfo} instance.
     * </p>
     *
     * @return a stream builder helper to build {@link AudioStream}s
     */
    @Nonnull
    private java.util.function.Function<ItagInfo, AudioStream> getAudioStreamBuilderHelper() {
        return (itagInfo) -> {
            final ItagItem itagItem = itagInfo.getItagItem();
            final AudioStream.Builder builder;
            try {
                final String randomString = UUID.randomUUID().toString().replaceAll("[^a-zA-Z]", "");
                builder = new AudioStream.Builder()
                        .setId(randomString)
                        .setContent(itagInfo.getContent() + (itagInfo.getIsUrl()?("&pppid="+getId()):""), itagInfo.getIsUrl())
                        .setMediaFormat(itagItem.getMediaFormat())
                        .setAverageBitrate(itagItem.getAverageBitrate())
                        .setItagItem(itagItem);
            } catch (ParsingException e) {
                throw new RuntimeException(e);
            }

            if (streamType == StreamType.LIVE_STREAM
                    || streamType == StreamType.POST_LIVE_STREAM
                    || !itagInfo.getIsUrl()) {
                // For YouTube videos on OTF streams and for all streams of post-live streams
                // and live streams, only the DASH delivery method can be used.
                builder.setDeliveryMethod(DeliveryMethod.DASH);
            }

            return builder.build();
        };
    }

    /**
     * Get the stream builder helper which will be used to build {@link VideoStream}s in
     * {@link #getItags(String, ItagItem.ItagType, java.util.function.Function, String)}
     *
     * <p>
     * The {@code StreamBuilderHelper} will set the following attributes in the
     * {@link VideoStream}s built:
     * <ul>
     *     <li>the {@link ItagItem}'s id of the stream as its id;</li>
     *     <li>{@link ItagInfo#getContent()} and {@link ItagInfo#getIsUrl()} as its content and
     *     and as the value of {@code isUrl};</li>
     *     <li>the media format returned by the {@link ItagItem} as its media format;</li>
     *     <li>whether it is video-only with the {@code areStreamsVideoOnly} parameter</li>
     *     <li>the {@link ItagItem};</li>
     *     <li>the resolution, by trying to use, in this order:
     *         <ol>
     *             <li>the height returned by the {@link ItagItem} + {@code p} + the frame rate if
     *             it is more than 30;</li>
     *             <li>the default resolution string from the {@link ItagItem};</li>
     *             <li>an {@link Utils#EMPTY_STRING empty string}.</li>
     *         </ol>
     *     </li>
     *     <li>the {@link DeliveryMethod#DASH DASH delivery method}, for OTF streams, live streams
     *     and ended streams.</li>
     * </ul>
     *
     * <p>
     * Note that the {@link ItagItem} comes from an {@link ItagInfo} instance.
     * </p>
     *
     * @param areStreamsVideoOnly whether the stream builder helper will set the video
     *                            streams as video-only streams
     * @return a stream builder helper to build {@link VideoStream}s
     */
    @Nonnull
    private java.util.function.Function<ItagInfo, VideoStream> getVideoStreamBuilderHelper(
            final boolean areStreamsVideoOnly) {
        return (itagInfo) -> {
            final ItagItem itagItem = itagInfo.getItagItem();
            final VideoStream.Builder builder;
            try {
                builder = new VideoStream.Builder()
                        .setId(String.valueOf(itagItem.id))
                        .setContent(itagInfo.getContent() + (itagInfo.getIsUrl()?("&pppid="+getId()):""), itagInfo.getIsUrl())
                        .setMediaFormat(itagItem.getMediaFormat())
                        .setIsVideoOnly(areStreamsVideoOnly)
                        .setItagItem(itagItem);
            } catch (ParsingException e) {
                throw new RuntimeException(e);
            }

            final String resolutionString = itagItem.getResolutionString();
            builder.setResolution(resolutionString != null ? resolutionString
                    : EMPTY_STRING);

            if (streamType != StreamType.VIDEO_STREAM || !itagInfo.getIsUrl()) {
                // For YouTube videos on OTF streams and for all streams of post-live streams
                // and live streams, only the DASH delivery method can be used.
                builder.setDeliveryMethod(DeliveryMethod.DASH);
            }

            return builder.build();
        };
    }

    @Nonnull
    private java.util.stream.Stream<ItagInfo> getStreamsFromStreamingDataKey(
            final String videoId,
            final JsonObject streamingData,
            final String streamingDataKey,
            @Nonnull final ItagItem.ItagType itagTypeWanted,
            @Nonnull final String contentPlaybackNonce) {
        if (streamingData == null || !streamingData.has(streamingDataKey)) {
            return java.util.stream.Stream.empty();
        }

        String contentLanguage = ServiceList.YouTube.getAudioLanguage();
        String foundLangCode = streamingData.getArray(streamingDataKey).stream().filter(element -> element instanceof JsonObject)
                .map(element -> (JsonObject) element).filter(data -> data.has("audioTrack") && data.getString("mimeType").contains("audio")).map(data -> data.getObject("audioTrack"))
                .filter(audioTrack -> audioTrack.has("id")).map(audioTrack -> audioTrack.getString("id"))
                .map(audioId -> audioId.split("\\.")[0].split("-")[0]).filter(langCode -> langCode.equals(contentLanguage))
                .findFirst().orElse(null);


        java.util.stream.Stream<ItagInfo> result = streamingData.getArray(streamingDataKey).stream()
                .filter(JsonObject.class::isInstance)
                .map(JsonObject.class::cast)
                .filter(data -> {
                    try {
                        if (data.getString("mimeType").contains("audio")) {
                            if (foundLangCode != null) {
                                return data.has("audioTrack") && data.getObject("audioTrack").has("id") &&
                                        data.getObject("audioTrack").getString("id").split("\\.")[0].split("-")[0].equals(foundLangCode);
                            } else {
                                if (!data.has("audioTrack")) return true;
                                return data.getObject("audioTrack").getString("displayName").contains("original");
                            }
                        }
                        return true;
                    } catch (final Exception ignored) {
                        return true;
                    }
                })
                .map(formatData -> {
                    try {
                        final ItagItem itagItem = ItagItem.getItag(formatData.getInt("itag"));
                        if (itagItem.itagType == itagTypeWanted) {
                            return buildAndAddItagInfoToList(videoId, formatData, itagItem,
                                    itagItem.itagType, contentPlaybackNonce);
                        }
                    } catch (final IOException | ExtractionException e) {
                        // if the itag is not supported and getItag fails, we end up here
                        e.printStackTrace();
                    }
                    return null;
                })
                .filter(Objects::nonNull);
        return result;
    }

    private ItagInfo buildAndAddItagInfoToList(
            @Nonnull final String videoId,
            @Nonnull final JsonObject formatData,
            @Nonnull final ItagItem itagItem,
            @Nonnull final ItagItem.ItagType itagType,
            @Nonnull final String contentPlaybackNonce) throws IOException, ExtractionException {
        String streamUrl;
        String obfuscatedSignature = null;

        if (formatData.has("url")) {
            streamUrl = formatData.getString("url");
        } else {
            // This url has an obfuscated signature - store it for batch processing
            final String cipherString = formatData.getString(CIPHER,
                    formatData.getString(SIGNATURE_CIPHER));
            final Map<String, String> cipher = Parser.compatParseMap(cipherString);
            obfuscatedSignature = cipher.getOrDefault("s", "");
            // Build URL without signature - will be added during batch deobfuscation
            streamUrl = cipher.get("url") + "&" + cipher.get("sp") + "=SIGNATURE_PLACEHOLDER";
        }

        // Add the content playback nonce to the stream URL
        streamUrl += "&" + CPN + "=" + contentPlaybackNonce;

        // Note: Signature and throttling parameter deobfuscation will be done
        // in batch by batchDeobfuscateItagUrls() method

        final JsonObject initRange = formatData.getObject("initRange");
        final JsonObject indexRange = formatData.getObject("indexRange");
        final String mimeType = formatData.getString("mimeType", EMPTY_STRING);
        final String codec = mimeType.contains("codecs")
                ? mimeType.split("\"")[1] : EMPTY_STRING;
        final int fps = formatData.getInt("fps", -1);

        itagItem.setBitrate(formatData.getInt("bitrate"));
        itagItem.setWidth(formatData.getInt("width"));
        itagItem.setHeight(formatData.getInt("height"));
        itagItem.setInitStart(Integer.parseInt(initRange.getString("start", "-1")));
        itagItem.setInitEnd(Integer.parseInt(initRange.getString("end", "-1")));
        itagItem.setIndexStart(Integer.parseInt(indexRange.getString("start", "-1")));
        itagItem.setIndexEnd(Integer.parseInt(indexRange.getString("end", "-1")));
        itagItem.setQuality(formatData.getString("quality"));
        itagItem.setCodec(codec);
        if (fps != -1) {
            itagItem.setFps(fps);
        }

        if (streamType == StreamType.LIVE_STREAM || streamType == StreamType.POST_LIVE_STREAM) {
            itagItem.setTargetDurationSec(formatData.getInt("targetDurationSec"));
        } else if (itagType == ItagItem.ItagType.VIDEO
                || itagType == ItagItem.ItagType.VIDEO_ONLY) {
            itagItem.setFps(formatData.getInt("fps"));
        } else if (itagType == ItagItem.ItagType.AUDIO) {
            // YouTube return the audio sample rate as a string
            itagItem.setSampleRate(Integer.parseInt(formatData.getString("audioSampleRate")));
            itagItem.setAudioChannels(formatData.getInt("audioChannels"));
        }

        // YouTube return the content length and the approximate duration as strings
        itagItem.setContentLength(Long.parseLong(formatData.getString("contentLength",
                String.valueOf(CONTENT_LENGTH_UNKNOWN))));
        itagItem.setApproxDurationMs(Long.parseLong(formatData.getString("approxDurationMs",
                String.valueOf(APPROX_DURATION_MS_UNKNOWN))));

        final ItagInfo itagInfo = new ItagInfo(streamUrl, itagItem);

        // Store the obfuscated signature in ItagInfo for batch processing
        if (obfuscatedSignature != null) {
            itagInfo.setObfuscatedSignature(obfuscatedSignature);
        }

        if (streamType == StreamType.VIDEO_STREAM) {
            itagInfo.setIsUrl(!formatData.getString("type", EMPTY_STRING)
                    .equalsIgnoreCase("FORMAT_STREAM_TYPE_OTF"));
        } else {
            // We are currently not able to generate DASH manifests for running
            // livestreams, so because of the requirements of StreamInfo
            // objects, return these streams as DASH URL streams (even if they
            // are not playable).
            // Ended livestreams are returned as non URL streams
            itagInfo.setIsUrl(streamType != StreamType.POST_LIVE_STREAM);
        }

        return itagInfo;
    }

    /**
     * Batch deobfuscate signatures and throttling parameters for all ItagInfo objects.
     * This replaces individual API calls with a single batch request.
     *
     * @param videoId      the video ID
     * @param itagInfoList list of ItagInfo objects to process
     * @throws ParsingException if batch deobfuscation fails
     */
    private void batchDeobfuscateItagUrls(@Nonnull final String videoId,
                                          @Nonnull final List<ItagInfo> itagInfoList)
            throws ParsingException {
        if (itagInfoList.isEmpty()) {
            return;
        }

        // Collect all unique signatures and throttling parameters
        // Use LinkedHashSet to preserve order and avoid duplicates
        final java.util.LinkedHashSet<String> uniqueSignatures = new java.util.LinkedHashSet<>();
        final java.util.LinkedHashSet<String> uniqueThrottlingParams = new java.util.LinkedHashSet<>();

        // Track which streams need which deobfuscations
        final List<StreamDeobfuscationInfo> streamInfos = new ArrayList<>();

        for (int i = 0; i < itagInfoList.size(); i++) {
            final ItagInfo itagInfo = itagInfoList.get(i);
            final String url = itagInfo.getContent();

            final String obfuscatedSig = itagInfo.getObfuscatedSignature();
            final String throttlingParam =
                YoutubeThrottlingParameterUtils.getThrottlingParameterFromStreamingUrl(url);

            // Create deobfuscation info for this stream
            final StreamDeobfuscationInfo info = new StreamDeobfuscationInfo(
                i, obfuscatedSig, throttlingParam);
            streamInfos.add(info);

            // Add to unique sets
            if (obfuscatedSig != null && !obfuscatedSig.isEmpty()) {
                uniqueSignatures.add(obfuscatedSig);
            }
            if (throttlingParam != null && !throttlingParam.isEmpty()) {
                uniqueThrottlingParams.add(throttlingParam);
            }
        }

        // If nothing to deobfuscate, return early
        if (uniqueSignatures.isEmpty() && uniqueThrottlingParams.isEmpty()) {
            return;
        }

        // Make batch API call
        final YoutubeApiDecoder.BatchDecodeResult result =
            YoutubeJavaScriptPlayerManager.deobfuscateBatch(
                videoId,
                new ArrayList<>(uniqueSignatures),
                new ArrayList<>(uniqueThrottlingParams));

        final Map<String, String> decodedSignatures = result.getSignatures();
        final Map<String, String> decodedThrottling = result.getNParameters();

        // Apply deobfuscated values to each stream
        for (final StreamDeobfuscationInfo info : streamInfos) {
            final ItagInfo itagInfo = itagInfoList.get(info.streamIndex);
            String updatedUrl = itagInfo.getContent();

            // Apply deobfuscated signature if needed
            if (info.obfuscatedSignature != null && !info.obfuscatedSignature.isEmpty()) {
                final String deobfuscatedSig = decodedSignatures.get(info.obfuscatedSignature);
                if (deobfuscatedSig != null) {
                    updatedUrl = updatedUrl.replace("SIGNATURE_PLACEHOLDER", deobfuscatedSig);
                }
            }

            // Apply deobfuscated throttling parameter if needed
            if (info.throttlingParam != null && !info.throttlingParam.isEmpty()) {
                final String deobfuscatedParam = decodedThrottling.get(info.throttlingParam);
                if (deobfuscatedParam != null) {
                    updatedUrl = updatedUrl.replace(info.throttlingParam, deobfuscatedParam);
                }
            }

            itagInfo.setContent(updatedUrl);
        }
    }

    /**
     * Helper class to track deobfuscation requirements for each stream.
     */
    private static class StreamDeobfuscationInfo {
        final int streamIndex;
        final String obfuscatedSignature;
        final String throttlingParam;

        StreamDeobfuscationInfo(final int streamIndex,
                                final String obfuscatedSignature,
                                final String throttlingParam) {
            this.streamIndex = streamIndex;
            this.obfuscatedSignature = obfuscatedSignature;
            this.throttlingParam = throttlingParam;
        }
    }

    @Nonnull
    @Override
    public List<Frameset> getFrames() throws ExtractionException {
        try {
            final JsonObject storyboards = playerResponse.getObject("storyboards");
            final JsonObject storyboardsRenderer = storyboards.getObject(
                    storyboards.has("playerLiveStoryboardSpecRenderer")
                            ? "playerLiveStoryboardSpecRenderer"
                            : "playerStoryboardSpecRenderer"
            );

            if (storyboardsRenderer == null) {
                return Collections.emptyList();
            }

            final String storyboardsRendererSpec = storyboardsRenderer.getString("spec");
            if (storyboardsRendererSpec == null) {
                return Collections.emptyList();
            }

            final String[] spec = storyboardsRendererSpec.split("\\|");
            final String url = spec[0];
            final List<Frameset> result = new ArrayList<>(spec.length - 1);

            for (int i = 1; i < spec.length; ++i) {
                final String[] parts = spec[i].split("#");
                if (parts.length != 8 || Integer.parseInt(parts[5]) == 0) {
                    continue;
                }
                final int totalCount = Integer.parseInt(parts[2]);
                final int framesPerPageX = Integer.parseInt(parts[3]);
                final int framesPerPageY = Integer.parseInt(parts[4]);
                final String baseUrl = url.replace("$L", String.valueOf(i - 1))
                        .replace("$N", parts[6]) + "&sigh=" + parts[7];
                final List<String> urls;
                if (baseUrl.contains("$M")) {
                    final int totalPages = (int) Math.ceil(totalCount / (double)
                            (framesPerPageX * framesPerPageY));
                    urls = new ArrayList<>(totalPages);
                    for (int j = 0; j < totalPages; j++) {
                        urls.add(baseUrl.replace("$M", String.valueOf(j)));
                    }
                } else {
                    urls = Collections.singletonList(baseUrl);
                }
                result.add(new Frameset(
                        urls,
                        /*frameWidth=*/Integer.parseInt(parts[0]),
                        /*frameHeight=*/Integer.parseInt(parts[1]),
                        totalCount,
                        /*durationPerFrame=*/Integer.parseInt(parts[5]),
                        framesPerPageX,
                        framesPerPageY
                ));
            }
            return result;
        } catch (final Exception e) {
            throw new ExtractionException("Could not get frames", e);
        }
    }

    @Nonnull
    @Override
    public Privacy getPrivacy() {
        return playerMicroFormatRenderer.getBoolean("isUnlisted")
                ? Privacy.UNLISTED
                : Privacy.PUBLIC;
    }

    @Nonnull
    @Override
    public String getCategory() {
        return playerMicroFormatRenderer.getString("category", EMPTY_STRING);
    }

    @Nonnull
    @Override
    public String getLicence() throws ParsingException {
        final JsonObject metadataRowRenderer = getVideoSecondaryInfoRenderer()
                .getObject("metadataRowContainer")
                .getObject("metadataRowContainerRenderer")
                .getArray("rows")
                .getObject(0)
                .getObject("metadataRowRenderer");

        final JsonArray contents = metadataRowRenderer.getArray("contents");
        final String license = getTextFromObject(contents.getObject(0));
        return license != null
                && "Licence".equals(getTextFromObject(metadataRowRenderer.getObject("title")))
                ? license
                : "YouTube license";
    }

    @Override
    public Locale getLanguageInfo() {
        return null;
    }

    @Nonnull
    @Override
    public List<String> getTags() {
        return JsonUtils.getStringListFromJsonArray(playerResponse.getObject("videoDetails")
                .getArray("keywords"));
    }

    @Nonnull
    @Override
    public List<StreamSegment> getStreamSegments() throws ParsingException {

        if (!nextResponse.has("engagementPanels")) {
            return Collections.emptyList();
        }

        final JsonArray segmentsArray = nextResponse.getArray("engagementPanels")
                .stream()
                // Check if object is a JsonObject
                .filter(JsonObject.class::isInstance)
                .map(JsonObject.class::cast)
                // Check if the panel is the correct one
                .filter(panel -> "engagement-panel-macro-markers-description-chapters".equals(
                        panel
                                .getObject("engagementPanelSectionListRenderer")
                                .getString("panelIdentifier")))
                // Extract the data
                .map(panel -> panel
                        .getObject("engagementPanelSectionListRenderer")
                        .getObject("content")
                        .getObject("macroMarkersListRenderer")
                        .getArray("contents"))
                .findFirst()
                .orElse(null);

        // If no data was found exit
        if (segmentsArray == null) {
            return Collections.emptyList();
        }

        final long duration = getLength();
        final List<StreamSegment> segments = new ArrayList<>();
        for (final JsonObject segmentJson : segmentsArray.stream()
                .filter(JsonObject.class::isInstance)
                .map(JsonObject.class::cast)
                .map(object -> object.getObject("macroMarkersListItemRenderer"))
                .collect(Collectors.toList())
        ) {
            final int startTimeSeconds = segmentJson.getObject("onTap")
                    .getObject("watchEndpoint").getInt("startTimeSeconds", -1);

            if (startTimeSeconds == -1) {
                throw new ParsingException("Could not get stream segment start time.");
            }
            if (startTimeSeconds > duration) {
                break;
            }

            final String title = getTextFromObject(segmentJson.getObject("title"));
            if (isNullOrEmpty(title)) {
                throw new ParsingException("Could not get stream segment title.");
            }

            final StreamSegment segment = new StreamSegment(title, startTimeSeconds);
            segment.setUrl(getUrl() + "?t=" + startTimeSeconds);
            if (segmentJson.has("thumbnail")) {
                final JsonArray previewsArray = segmentJson
                        .getObject("thumbnail")
                        .getArray("thumbnails");
                if (!previewsArray.isEmpty()) {
                    // Assume that the thumbnail with the highest resolution is at the last position
                    final String url = previewsArray
                            .getObject(previewsArray.size() - 1)
                            .getString("url");
                    segment.setPreviewUrl(fixThumbnailUrl(url));
                }
            }
            segments.add(segment);
        }
        return segments;
    }

    @Nonnull
    @Override
    public List<MetaInfo> getMetaInfo() throws ParsingException {
        return YoutubeParsingHelper.getMetaInfo(nextResponse
                .getObject("contents")
                .getObject("twoColumnWatchNextResults")
                .getObject("results")
                .getObject("results")
                .getArray("contents"));
    }
}
