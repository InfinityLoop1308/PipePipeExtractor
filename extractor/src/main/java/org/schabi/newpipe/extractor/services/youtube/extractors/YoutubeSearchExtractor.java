package org.schabi.newpipe.extractor.services.youtube.extractors;

import static org.schabi.newpipe.extractor.services.youtube.YoutubeParsingHelper.DISABLE_PRETTY_PRINT_PARAMETER;
import static org.schabi.newpipe.extractor.services.youtube.YoutubeParsingHelper.YOUTUBEI_V1_URL;
import static org.schabi.newpipe.extractor.services.youtube.YoutubeParsingHelper.getJsonPostResponseAsync;
import static org.schabi.newpipe.extractor.services.youtube.YoutubeParsingHelper.getTextFromObject;
import static org.schabi.newpipe.extractor.services.youtube.YoutubeParsingHelper.getValidJsonResponseBody;
import static org.schabi.newpipe.extractor.services.youtube.YoutubeParsingHelper.prepareDesktopJsonBuilder;
import static org.schabi.newpipe.extractor.services.youtube.YoutubeService.getTempLocalization;
import static org.schabi.newpipe.extractor.utils.Utils.UTF_8;
import static org.schabi.newpipe.extractor.utils.Utils.isNullOrEmpty;

import org.schabi.newpipe.extractor.*;
import org.schabi.newpipe.extractor.services.youtube.search.filter.YoutubeFilters;
import com.grack.nanojson.JsonArray;
import com.grack.nanojson.JsonBuilder;
import com.grack.nanojson.JsonObject;
import com.grack.nanojson.JsonParser;
import com.grack.nanojson.JsonParserException;
import com.grack.nanojson.JsonWriter;

import org.schabi.newpipe.extractor.downloader.Downloader;
import org.schabi.newpipe.extractor.downloader.Response;
import org.schabi.newpipe.extractor.exceptions.ExtractionException;
import org.schabi.newpipe.extractor.exceptions.ParsingException;
import org.schabi.newpipe.extractor.linkhandler.SearchQueryHandler;
import org.schabi.newpipe.extractor.localization.Localization;
import org.schabi.newpipe.extractor.localization.TimeAgoParser;
import org.schabi.newpipe.extractor.services.youtube.YoutubeParsingHelper;
import org.schabi.newpipe.extractor.utils.JsonUtils;

import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicReference;

import javax.annotation.Nonnull;

public class YoutubeSearchExtractor extends YoutubeBaseSearchExtractor {
    private JsonObject initialData;
    private JsonObject tempData;

    public YoutubeSearchExtractor(final StreamingService service,
                                  final SearchQueryHandler linkHandler) {
        super(service, linkHandler);
    }

    @Override
    public void onFetchPage(@Nonnull final Downloader downloader) throws IOException,
            ExtractionException {
        final String query = super.getSearchString();
        final Localization localization = getExtractorLocalization();

        // Get the search parameter for the request
        final YoutubeFilters.YoutubeContentFilterItem contentFilterItem =
                getSelectedContentFilterItem();
        final String params = contentFilterItem.getParams();

        final JsonBuilder<JsonObject> jsonBody = prepareDesktopJsonBuilder(localization,
                getExtractorContentCountry())
                .value("query", query);
        if (!isNullOrEmpty(params)) {
            jsonBody.value("params", params);
        }

        final JsonBuilder<JsonObject> jsonBodyTemp = prepareDesktopJsonBuilder(getTempLocalization(),
                getExtractorContentCountry())
                .value("query", query);
        if (!isNullOrEmpty(params)) {
            jsonBodyTemp.value("params", params);
        }

        final byte[] body = JsonWriter.string(jsonBody.done()).getBytes(UTF_8);
        final byte[] bodyTemp = JsonWriter.string(jsonBodyTemp.done()).getBytes(UTF_8);

        final CountDownLatch latch = new CountDownLatch(2);
        final AtomicReference<JsonObject> refNormal = new AtomicReference<>();
        final AtomicReference<JsonObject> refTemp = new AtomicReference<>();
        final AtomicReference<Exception> refError = new AtomicReference<>();

        getJsonPostResponseAsync("search", body, localization, new Downloader.AsyncCallback() {
            @Override
            public void onSuccess(Response response) {
                try {
                    final JsonObject json = JsonParser.object().from(response.responseBody());
                    refNormal.set(json);
                } catch (Exception e) {
                    refError.set(e);
                }
                latch.countDown();
            }

            @Override
            public void onError(Exception t) {
                refError.set(new IOException(t));
                latch.countDown();
            }
        });

        getJsonPostResponseAsync("search", bodyTemp, getTempLocalization(), new Downloader.AsyncCallback() {
            @Override
            public void onSuccess(Response response) {
                try {
                    final JsonObject json = JsonParser.object().from(response.responseBody());
                    refTemp.set(json);
                } catch (Exception e) {
                    refError.set(e);
                }
                latch.countDown();
            }

            @Override
            public void onError(Exception t) {
                refError.set(new IOException(t));
                latch.countDown();
            }
        });

        try {
            latch.await();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IOException("Interrupted while waiting for search responses", e);
        }

        if (refError.get() != null) {
            final Exception ex = refError.get();
            if (ex instanceof IOException) throw (IOException) ex;
            if (ex instanceof ExtractionException) throw (ExtractionException) ex;
            throw new ExtractionException("Unexpected error", ex);
        }

        initialData = refNormal.get();
        tempData = refTemp.get();
    }

    @Nonnull
    @Override
    public String getUrl() throws ParsingException {
        return super.getUrl() + "&gl=" + getExtractorContentCountry().getCountryCode();
    }

    @Nonnull
    @Override
    public String getSearchSuggestion() throws ParsingException {
        final JsonObject itemSectionRenderer = initialData.getObject("contents")
                .getObject("twoColumnSearchResultsRenderer").getObject("primaryContents")
                .getObject("sectionListRenderer").getArray("contents").getObject(0)
                .getObject("itemSectionRenderer");
        final JsonObject didYouMeanRenderer = itemSectionRenderer.getArray("contents").getObject(0)
                .getObject("didYouMeanRenderer");
        final JsonObject showingResultsForRenderer = itemSectionRenderer.getArray("contents")
                .getObject(0)
                .getObject("showingResultsForRenderer");

        if (!didYouMeanRenderer.isEmpty()) {
            return JsonUtils.getString(didYouMeanRenderer,
                    "correctedQueryEndpoint.searchEndpoint.query");
        } else if (showingResultsForRenderer != null) {
            return getTextFromObject(showingResultsForRenderer.getObject("correctedQuery"));
        } else {
            return "";
        }
    }

    @Override
    public boolean isCorrectedSearch() {
        final JsonObject showingResultsForRenderer = initialData.getObject("contents")
                .getObject("twoColumnSearchResultsRenderer").getObject("primaryContents")
                .getObject("sectionListRenderer").getArray("contents").getObject(0)
                .getObject("itemSectionRenderer").getArray("contents").getObject(0)
                .getObject("showingResultsForRenderer");
        return !showingResultsForRenderer.isEmpty();
    }

    @Nonnull
    @Override
    public List<MetaInfo> getMetaInfo() throws ParsingException {
        return YoutubeParsingHelper.getMetaInfo(
                initialData.getObject("contents").getObject("twoColumnSearchResultsRenderer")
                        .getObject("primaryContents").getObject("sectionListRenderer")
                        .getArray("contents"));
    }

    @Nonnull
    @Override
    public InfoItemsPage<InfoItem> getInitialPageInternal() throws IOException, ExtractionException {
        final MultiInfoItemsCollector collector = new MultiInfoItemsCollector(getServiceId());

        final JsonArray sections = initialData.getObject("contents")
                .getObject("twoColumnSearchResultsRenderer").getObject("primaryContents")
                .getObject("sectionListRenderer").getArray("contents");

        final JsonArray tempSections = tempData.getObject("contents")
                .getObject("twoColumnSearchResultsRenderer").getObject("primaryContents")
                .getObject("sectionListRenderer").getArray("contents");

        Page nextPage = null;

        for (int i = 0; i < sections.size(); i++) {
            final JsonObject section = sections.getObject(i);
            final JsonObject tempSection = i < tempSections.size() ? tempSections.getObject(i) : null;

            if (section.has("itemSectionRenderer")) {
                final JsonObject itemSectionRenderer = section.getObject("itemSectionRenderer");
                final JsonArray tempContents = tempSection != null && tempSection.has("itemSectionRenderer")
                        ? tempSection.getObject("itemSectionRenderer").getArray("contents")
                        : null;

                collectStreamsFrom(collector, itemSectionRenderer.getArray("contents"), tempContents);
            } else if (section.has("continuationItemRenderer")) {
                nextPage = getNextPageFrom(section.getObject("continuationItemRenderer"));
            }
        }
        return new InfoItemsPage<>(collector, nextPage);
    }

    @Override
    public InfoItemsPage<InfoItem> getPageInternal(final Page page) throws IOException,
            ExtractionException {
        if (page == null || isNullOrEmpty(page.getUrl())) {
            throw new IllegalArgumentException("Page doesn't contain an URL");
        }

        final Localization localization = getExtractorLocalization();
        final MultiInfoItemsCollector collector = new MultiInfoItemsCollector(getServiceId());

        // @formatter:off
        final byte[] json = JsonWriter.string(prepareDesktopJsonBuilder(localization,
                getExtractorContentCountry())
                .value("continuation", page.getId())
                .done())
                .getBytes(UTF_8);

        final byte[] jsonTemp = JsonWriter.string(prepareDesktopJsonBuilder(getTempLocalization(),
                getExtractorContentCountry())
                .value("continuation", page.getId())
                .done())
                .getBytes(UTF_8);
        // @formatter:on

        final CountDownLatch latch = new CountDownLatch(2);
        final AtomicReference<JsonObject> refNormal = new AtomicReference<>();
        final AtomicReference<JsonObject> refTemp = new AtomicReference<>();
        final AtomicReference<Exception> refError = new AtomicReference<>();

        final String url = page.getUrl();
        final HashMap<String, List<String>> headers = new HashMap<>();

        getJsonPostResponseAsync("search", json, localization, new Downloader.AsyncCallback() {
            @Override
            public void onSuccess(Response response) {
                try {
                    final JsonObject ajaxJson = JsonParser.object().from(response.responseBody());
                    refNormal.set(ajaxJson);
                } catch (Exception e) {
                    refError.set(e);
                }
                latch.countDown();
            }

            @Override
            public void onError(Exception t) {
                refError.set(new IOException(t));
                latch.countDown();
            }
        });

        getJsonPostResponseAsync("search", jsonTemp, getTempLocalization(), new Downloader.AsyncCallback() {
            @Override
            public void onSuccess(Response response) {
                try {
                    final JsonObject ajaxJson = JsonParser.object().from(response.responseBody());
                    refTemp.set(ajaxJson);
                } catch (Exception e) {
                    refError.set(e);
                }
                latch.countDown();
            }

            @Override
            public void onError(Exception t) {
                refError.set(new IOException(t));
                latch.countDown();
            }
        });

        try {
            latch.await();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IOException("Interrupted while waiting for search page responses", e);
        }

        if (refError.get() != null) {
            final Exception ex = refError.get();
            if (ex instanceof IOException) throw (IOException) ex;
            if (ex instanceof ExtractionException) throw (ExtractionException) ex;
            throw new ExtractionException("Unexpected error", ex);
        }

        final JsonObject ajaxJson = refNormal.get();
        final JsonObject tempAjaxJson = refTemp.get();

        final JsonArray continuationItems = ajaxJson.getArray("onResponseReceivedCommands")
                .getObject(0).getObject("appendContinuationItemsAction")
                .getArray("continuationItems");

        final JsonArray tempContinuationItems = tempAjaxJson.getArray("onResponseReceivedCommands")
                .getObject(0).getObject("appendContinuationItemsAction")
                .getArray("continuationItems");

        final JsonArray contents = continuationItems.getObject(0)
                .getObject("itemSectionRenderer").getArray("contents");
        final JsonArray tempContents = tempContinuationItems.getObject(0)
                .getObject("itemSectionRenderer").getArray("contents");

        collectStreamsFrom(collector, contents, tempContents);
        return new InfoItemsPage<>(collector, getNextPageFrom(continuationItems.getObject(1)
                .getObject("continuationItemRenderer")));
    }

    private void collectStreamsFrom(final MultiInfoItemsCollector collector,
                                    final JsonArray contents,
                                    final JsonArray tempContents) throws NothingFoundException,
            ParsingException {
        final TimeAgoParser timeAgoParser = getTimeAgoParser();

        for (int i = 0; i < contents.size(); i++) {
            final JsonObject item = contents.getObject(i);
            final JsonObject tempItem = tempContents != null && i < tempContents.size()
                    ? tempContents.getObject(i) : null;

            if (item.has("backgroundPromoRenderer")) {
                throw new NothingFoundException(getTextFromObject(
                        item.getObject("backgroundPromoRenderer").getObject("bodyText")));
            } else if (item.has("videoRenderer")) {
                final JsonObject tempVideoRenderer = tempItem != null && tempItem.has("videoRenderer")
                        ? tempItem.getObject("videoRenderer") : null;
                collector.commit(new YoutubeStreamInfoItemExtractor(item
                        .getObject("videoRenderer"), timeAgoParser, tempVideoRenderer));
            } else if (item.has("channelRenderer")) {
                collector.commit(new YoutubeChannelInfoItemExtractor(item
                        .getObject("channelRenderer")));
            } else if (item.has("playlistRenderer")) {
                collector.commit(new YoutubePlaylistInfoItemExtractor(item
                        .getObject("playlistRenderer")));
            } else if (item.has("lockupViewModel")) {
                final JsonObject lockupViewModel = item.getObject("lockupViewModel");
                if ("LOCKUP_CONTENT_TYPE_PLAYLIST".equals(
                        lockupViewModel.getString("contentType"))) {
                    collector.commit(
                            new YoutubeMixOrPlaylistLockupInfoItemExtractor(lockupViewModel));
                }
            }
        }
    }

    private Page getNextPageFrom(final JsonObject continuationItemRenderer) throws IOException,
            ExtractionException {
        if (isNullOrEmpty(continuationItemRenderer)) {
            return null;
        }

        final String token = continuationItemRenderer.getObject("continuationEndpoint")
                .getObject("continuationCommand").getString("token");

        final String url = YOUTUBEI_V1_URL + "search?"
                + DISABLE_PRETTY_PRINT_PARAMETER;

        return new Page(url, token);
    }
}
