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

import com.grack.nanojson.JsonArray;
import com.grack.nanojson.JsonObject;
import com.grack.nanojson.JsonWriter;
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
import org.schabi.newpipe.extractor.stream.*;
import org.schabi.newpipe.extractor.utils.JsonUtils;
import org.schabi.newpipe.extractor.utils.Pair;
import org.schabi.newpipe.extractor.utils.Utils;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.io.IOException;
import java.net.MalformedURLException;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import static org.schabi.newpipe.extractor.services.youtube.ItagItem.APPROX_DURATION_MS_UNKNOWN;
import static org.schabi.newpipe.extractor.services.youtube.ItagItem.CONTENT_LENGTH_UNKNOWN;
import static org.schabi.newpipe.extractor.services.youtube.YoutubeParsingHelper.*;
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

    private JsonObject videoPrimaryInfoRenderer;
    private JsonObject videoSecondaryInfoRenderer;
    private int ageLimit = -1;
    private StreamType streamType;

    // We need to store the contentPlaybackNonces because we need to append them to videoplayback
    // URLs (with the cpn parameter).
    // Also because a nonce should be unique, it should be different between clients used, so
    // three different strings are used.
    private String tvHtml5SimplyEmbedCpn;
    private String androidCpn;
    private String iosCpn;

    private JSONObject proxyData;

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
        return null;
    }

    @Nonnull
    @Override
    public String getThumbnailUrl() throws ParsingException {
        return null;
    }

    @Nonnull
    @Override
    public List<Image> getThumbnails() throws ParsingException {
        return null;
    }

    @Nonnull
    @Override
    public Description getDescription() throws ParsingException {
        return null;
    }

    @Override
    public int getAgeLimit() throws ParsingException {
        if (ageLimit != -1) {
            return ageLimit;
        }

        final boolean ageRestricted = getVideoSecondaryInfoRenderer()
                .getObject("metadataRowContainer")
                .getObject("metadataRowContainerRenderer")
                .getArray("rows")
                .stream()
                // Only JsonObjects allowed
                .filter(JsonObject.class::isInstance)
                .map(JsonObject.class::cast)
                .flatMap(metadataRow -> metadataRow
                        .getObject("metadataRowRenderer")
                        .getArray("contents")
                        .stream()
                        // Only JsonObjects allowed
                        .filter(JsonObject.class::isInstance)
                        .map(JsonObject.class::cast))
                .flatMap(content -> content
                        .getArray("runs")
                        .stream()
                        // Only JsonObjects allowed
                        .filter(JsonObject.class::isInstance)
                        .map(JsonObject.class::cast))
                .map(run -> run.getString("text", EMPTY_STRING))
                .anyMatch(rowText -> rowText.contains("Age-restricted"));

        ageLimit = ageRestricted ? 18 : NO_AGE_LIMIT;
        return ageLimit;
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

    @Nonnull
    @Override
    public String getUploaderUrl() throws ParsingException {
        return null;
    }

    @Nonnull
    @Override
    public String getUploaderName() throws ParsingException {
        return null;
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
    public List<AudioStream> getAudioStreams() throws ExtractionException {
        return new ArrayList<>();
    }

    @Override
    public List<VideoStream> getVideoStreams() throws ExtractionException {
        return new ArrayList<>();
    }

    @Override
    public List<VideoStream> getVideoOnlyStreams() throws ExtractionException {
        return new ArrayList<>();
    }

    @Override
    @Nonnull
    public List<SubtitlesStream> getSubtitlesDefault() throws ParsingException {
        return getSubtitles(MediaFormat.TTML);
    }

    @Override
    @Nonnull
    public List<SubtitlesStream> getSubtitles(final MediaFormat format) throws ParsingException {
        // TODO: re-support this
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

                subtitlesToReturn.add(new SubtitlesStream.Builder()
                        .setContent(cleanUrl + "&fmt=" + format.getSuffix(), true)
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

        if (translatableSubtitle != null && !hasFoundContentLanguage) {
            subtitlesToReturn.add(new SubtitlesStream.Builder()
                    .setContent(translatableSubtitle.getContent() + "&tlang=" + contentLanguage, true)
                    .setMediaFormat(format)
                    .setLanguageCode(contentLanguage)
                    .setAutoGenerated(false)
                    .build());
        }

        return subtitlesToReturn;
    }

    @Override
    public StreamType getStreamType() {
        return streamType;
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
                        }
                        return null;
                    })
                    .filter(Objects::nonNull)
                    .forEach(collector::commit);

            if (ServiceList.YouTube.getFilterTypes().contains("related_item")) {
                collector.applyBlocking(ServiceList.YouTube.getStreamKeywordFilter(), ServiceList.YouTube.getStreamChannelFilter());
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

    /*//////////////////////////////////////////////////////////////////////////
    // Fetch page
    //////////////////////////////////////////////////////////////////////////*/

    private static final String FORMATS = "formats";
    private static final String ADAPTIVE_FORMATS = "adaptiveFormats";
    private static final String STREAMING_DATA = "streamingData";
    private static final String PLAYER = "player";
    private static final String NEXT = "next";


    @Override
    public void onFetchPage(@Nonnull final Downloader downloader)
            throws IOException, ExtractionException {

        final String videoId = getId();
        final Localization localization = getExtractorLocalization();
        final ContentCountry contentCountry = getExtractorContentCountry();

        if (!ServiceList.YouTube.getProxyEnabled()) {
            errors.clear();
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
                if (nextDataCall.isFinished() && dislikeCall.isFinished()) {
                    break;
                }
            } while (TimeUnit.NANOSECONDS.toSeconds(System.nanoTime() - startTime) <= 5);
        } else {
            Map<String, List<String>> headers = new HashMap<>();
            headers.put("Authorization", Collections.singletonList("Bearer " + ServiceList.YouTube.getProxyToken()));
            try {
                Response proxyResp = downloader.get("https://api.pipepipe.dev/get-youtube-stream?id=" + getId(), headers);
                proxyData = new JSONObject(proxyResp.responseBody());
                if (proxyResp.responseCode()  >= 400) {
                    throw new ExtractionException("Error: " + proxyData.getString("error"));
                }
                nextResponse = JsonUtils.toJsonObject(getValidJsonResponseBody(proxyResp)).getArray("results").getObject(1).getObject("data");
                dislikeData = proxyData.getJSONArray("results").getJSONObject(2).getJSONObject("data");
            } catch (Exception e) {
                throw new ExtractionException("Proxy failed: " + e.getMessage());
            }
        }
    }

    @Override
    public JSONObject getExtraData() {
        if (proxyData != null) {
            try {
                return proxyData.getJSONArray("results").getJSONObject(0).getJSONObject("data");
            } catch (JSONException e) {
                e.printStackTrace();
            }
        }
        return null;
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
    @Override
    public List<Frameset> getFrames() throws ExtractionException {
        // TODO: re-support this
        if(true)
        return null;
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
                : "YouTube licence";
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
