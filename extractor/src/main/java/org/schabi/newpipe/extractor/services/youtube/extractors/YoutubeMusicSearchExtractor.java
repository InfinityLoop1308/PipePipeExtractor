package org.schabi.newpipe.extractor.services.youtube.extractors;

import static org.schabi.newpipe.extractor.services.youtube.YoutubeParsingHelper.*;
import static org.schabi.newpipe.extractor.services.youtube.linkHandler.YoutubeSearchQueryHandlerFactory.MUSIC_ALBUMS;
import static org.schabi.newpipe.extractor.services.youtube.linkHandler.YoutubeSearchQueryHandlerFactory.MUSIC_ARTISTS;
import static org.schabi.newpipe.extractor.services.youtube.linkHandler.YoutubeSearchQueryHandlerFactory.MUSIC_PLAYLISTS;
import static org.schabi.newpipe.extractor.services.youtube.linkHandler.YoutubeSearchQueryHandlerFactory.MUSIC_SONGS;
import static org.schabi.newpipe.extractor.services.youtube.linkHandler.YoutubeSearchQueryHandlerFactory.MUSIC_VIDEOS;
import static org.schabi.newpipe.extractor.utils.Utils.EMPTY_STRING;
import static org.schabi.newpipe.extractor.utils.Utils.UTF_8;
import static org.schabi.newpipe.extractor.utils.Utils.isNullOrEmpty;

import org.schabi.newpipe.extractor.services.youtube.search.filter.YoutubeFilters;
import com.grack.nanojson.JsonArray;
import com.grack.nanojson.JsonObject;
import com.grack.nanojson.JsonParser;
import com.grack.nanojson.JsonParserException;
import com.grack.nanojson.JsonWriter;

import org.schabi.newpipe.extractor.InfoItem;
import org.schabi.newpipe.extractor.MetaInfo;
import org.schabi.newpipe.extractor.Page;
import org.schabi.newpipe.extractor.StreamingService;
import org.schabi.newpipe.extractor.downloader.Downloader;
import org.schabi.newpipe.extractor.exceptions.ExtractionException;
import org.schabi.newpipe.extractor.exceptions.ParsingException;
import org.schabi.newpipe.extractor.exceptions.ReCaptchaException;
import org.schabi.newpipe.extractor.linkhandler.SearchQueryHandler;
import org.schabi.newpipe.extractor.localization.DateWrapper;
import org.schabi.newpipe.extractor.localization.TimeAgoParser;
import org.schabi.newpipe.extractor.MultiInfoItemsCollector;
import org.schabi.newpipe.extractor.services.youtube.YoutubeParsingHelper;
import org.schabi.newpipe.extractor.utils.JsonUtils;
import org.schabi.newpipe.extractor.utils.Parser;
import org.schabi.newpipe.extractor.utils.Utils;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.stream.Collectors;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

public class YoutubeMusicSearchExtractor extends YoutubeBaseSearchExtractor {
    private JsonObject initialData;

    public YoutubeMusicSearchExtractor(final StreamingService service,
                                       final SearchQueryHandler linkHandler) {
        super(service, linkHandler);
    }

    private String getSearchType() {
        final YoutubeFilters.MusicYoutubeContentFilterItem contentFilterItem =
                getSelectedContentFilterItem();
        if (contentFilterItem.getName() != null) {
            return contentFilterItem.getName();
        }
        return "";
    }

    @Override
    public void onFetchPage(@Nonnull final Downloader downloader)
            throws IOException, ExtractionException {
        final String url = "https://music.youtube.com/youtubei/v1/search?"
                + DISABLE_PRETTY_PRINT_PARAMETER;

        final YoutubeFilters.MusicYoutubeContentFilterItem contentFilterItem =
            getSelectedContentFilterItem();
        // if params be null (which never should happen), JsonWriter.string() can handle it
        final String params = contentFilterItem.getParams();

        // @formatter:off
        final byte[] json = JsonWriter.string()
            .object()
                .object("context")
                    .object("client")
                        .value("clientName", "WEB_REMIX")
                        .value("clientVersion", getYoutubeMusicClientVersion())
                        .value("hl", "en-GB")
                        .value("gl", getExtractorContentCountry().getCountryCode())
                        .value("platform", "DESKTOP")
                        .value("utcOffsetMinutes", 0)
                    .end()
                    .object("request")
                        .array("internalExperimentFlags")
                        .end()
                        .value("useSsl", true)
                    .end()
                    .object("user")
                        // TODO: provide a way to enable restricted mode with:
                        //  .value("enableSafetyMode", boolean)
                        .value("lockedSafetyMode", false)
                    .end()
                .end()
                .value("query", getSearchString())
                .value("params", params)
            .end().done().getBytes(StandardCharsets.UTF_8);
        // @formatter:on

        final Map<String, List<String>> headers = getYoutubeMusicHeaders();
        headers.put("Content-Type", Collections.singletonList("application/json"));

        final String responseBody = getValidJsonResponseBody(getDownloader().post(url, headers,
                json));

        try {
            initialData = JsonParser.object().from(responseBody);
        } catch (final JsonParserException e) {
            throw new ParsingException("Could not parse JSON", e);
        }
    }

    private List<JsonObject> getItemSectionRendererContents() {
        return initialData
                .getObject("contents")
                .getObject("tabbedSearchResultsRenderer")
                .getArray("tabs")
                .getObject(0)
                .getObject("tabRenderer")
                .getObject("content")
                .getObject("sectionListRenderer")
                .getArray("contents")
                .stream()
                .filter(JsonObject.class::isInstance)
                .map(JsonObject.class::cast)
                .map(c -> c.getObject("itemSectionRenderer"))
                .filter(isr -> !isr.isEmpty())
                .map(isr -> isr
                        .getArray("contents")
                        .getObject(0))
                .collect(Collectors.toList());
    }

    @Nonnull
    @Override
    public String getSearchSuggestion() throws ParsingException {
        for (final JsonObject obj : getItemSectionRendererContents()) {
            final JsonObject didYouMeanRenderer = obj
                    .getObject("didYouMeanRenderer");
            final JsonObject showingResultsForRenderer = obj
                    .getObject("showingResultsForRenderer");

            if (!didYouMeanRenderer.isEmpty()) {
                return getTextFromObject(didYouMeanRenderer.getObject("correctedQuery"));
            } else if (!showingResultsForRenderer.isEmpty()) {
                return JsonUtils.getString(showingResultsForRenderer,
                        "correctedQueryEndpoint.searchEndpoint.query");
            }
        }

        return "";
    }

    @Override
    public boolean isCorrectedSearch() throws ParsingException {
        return getItemSectionRendererContents()
                .stream()
                .anyMatch(obj -> obj.has("showingResultsForRenderer"));
    }

    @Nonnull
    @Override
    public List<MetaInfo> getMetaInfo() {
        return Collections.emptyList();
    }

    @Nonnull
    @Override
    public InfoItemsPage<InfoItem> getInitialPage() throws IOException, ExtractionException {
        final MultiInfoItemsCollector collector = new MultiInfoItemsCollector(getServiceId());

        final JsonArray contents = JsonUtils.getArray(JsonUtils.getArray(initialData,
                "contents.tabbedSearchResultsRenderer.tabs").getObject(0),
                "tabRenderer.content.sectionListRenderer.contents");

        Page nextPage = null;

        for (final Object content : contents) {
            if (((JsonObject) content).has("musicShelfRenderer")) {
                final JsonObject musicShelfRenderer = ((JsonObject) content)
                        .getObject("musicShelfRenderer");

                collectMusicStreamsFrom(collector, musicShelfRenderer.getArray("contents"));

                nextPage = getNextPageFrom(musicShelfRenderer.getArray("continuations"));
            }
        }

        return new InfoItemsPage<>(collector, nextPage);
    }

    @Override
    public InfoItemsPage<InfoItem> getPage(final Page page)
            throws IOException, ExtractionException {
        if (page == null || isNullOrEmpty(page.getUrl())) {
            throw new IllegalArgumentException("Page doesn't contain an URL");
        }

        final MultiInfoItemsCollector collector = new MultiInfoItemsCollector(getServiceId());

        // @formatter:off
        final byte[] json = JsonWriter.string()
            .object()
                .object("context")
                    .object("client")
                        .value("clientName", "WEB_REMIX")
                        .value("clientVersion", getYoutubeMusicClientVersion())
                        .value("hl", "en-GB")
                        .value("gl", getExtractorContentCountry().getCountryCode())
                        .value("platform", "DESKTOP")
                        .value("utcOffsetMinutes", 0)
                    .end()
                    .object("request")
                        .array("internalExperimentFlags")
                        .end()
                        .value("useSsl", true)
                    .end()
                    .object("user")
                        // TODO: provide a way to enable restricted mode with:
                        //  .value("enableSafetyMode", boolean)
                        .value("lockedSafetyMode", false)
                    .end()
                .end()
            .end().done().getBytes(StandardCharsets.UTF_8);
        // @formatter:on

        final Map<String, List<String>> headers = getYoutubeMusicHeaders();
        headers.put("Content-Type", Collections.singletonList("application/json"));

        final String responseBody = getValidJsonResponseBody(getDownloader().post(page.getUrl(),
                headers, json));

        final JsonObject ajaxJson;
        try {
            ajaxJson = JsonParser.object().from(responseBody);
        } catch (final JsonParserException e) {
            throw new ParsingException("Could not parse JSON", e);
        }

        final JsonObject musicShelfContinuation = ajaxJson.getObject("continuationContents")
                .getObject("musicShelfContinuation");

        collectMusicStreamsFrom(collector, musicShelfContinuation.getArray("contents"));
        final JsonArray continuations = musicShelfContinuation.getArray("continuations");

        return new InfoItemsPage<>(collector, getNextPageFrom(continuations));
    }

    private void collectMusicStreamsFrom(final MultiInfoItemsCollector collector,
                                         @Nonnull final JsonArray videos) {
        final String searchType = getSearchType();
        videos.stream()
                .filter(JsonObject.class::isInstance)
                .map(JsonObject.class::cast)
                .map(item -> item.getObject("musicResponsiveListItemRenderer", null))
                .filter(Objects::nonNull)
                .forEachOrdered(infoItem -> {
                    final String displayPolicy = infoItem.getString(
                            "musicItemRendererDisplayPolicy", "");
                    if (displayPolicy.equals("MUSIC_ITEM_RENDERER_DISPLAY_POLICY_GREY_OUT")) {
                        // No info about URL available
                        return;
                    }

                    final JsonArray descriptionElements = infoItem.getArray("flexColumns")
                            .getObject(1)
                            .getObject("musicResponsiveListItemFlexColumnRenderer")
                            .getObject("text")
                            .getArray("runs");

                    switch (searchType) {
                        case MUSIC_SONGS:
                        case MUSIC_VIDEOS:
                            collector.commit(new YoutubeMusicSongOrVideoInfoItemExtractor(
                                    infoItem, descriptionElements, searchType));
                            break;
                        case MUSIC_ARTISTS:
                            collector.commit(new YoutubeMusicArtistInfoItemExtractor(infoItem));
                            break;
                        case MUSIC_ALBUMS:
                        case MUSIC_PLAYLISTS:
                            collector.commit(new YoutubeMusicAlbumOrPlaylistInfoItemExtractor(
                                    infoItem, descriptionElements, searchType));
                            break;
                    }
                });
    }

    @Nullable
    private Page getNextPageFrom(final JsonArray continuations) {
        if (isNullOrEmpty(continuations)) {
            return null;
        }

        final JsonObject nextContinuationData = continuations.getObject(0)
                .getObject("nextContinuationData");
        final String continuation = nextContinuationData.getString("continuation");

        return new Page("https://music.youtube.com/youtubei/v1/search?ctoken=" + continuation
                + "&continuation=" + continuation + "&" + DISABLE_PRETTY_PRINT_PARAMETER);
    }
}
