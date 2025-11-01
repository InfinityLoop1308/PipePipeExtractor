package org.schabi.newpipe.extractor.services.youtube.extractors;

/*
 * Created by Christian Schabesberger on 12.08.17.
 *
 * Copyright (C) Christian Schabesberger 2018 <chris.schabesberger@mailbox.org>
 * YoutubeTrendingExtractor.java is part of NewPipe.
 *
 * NewPipe is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * NewPipe is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with NewPipe.  If not, see <http://www.gnu.org/licenses/>.
 */

import com.grack.nanojson.JsonArray;
import com.grack.nanojson.JsonObject;
import com.grack.nanojson.JsonParser;
import com.grack.nanojson.JsonWriter;

import org.schabi.newpipe.extractor.Page;
import org.schabi.newpipe.extractor.ServiceList;
import org.schabi.newpipe.extractor.StreamingService;
import org.schabi.newpipe.extractor.downloader.Downloader;
import org.schabi.newpipe.extractor.downloader.Response;
import org.schabi.newpipe.extractor.exceptions.ExtractionException;
import org.schabi.newpipe.extractor.exceptions.ParsingException;
import org.schabi.newpipe.extractor.kiosk.KioskExtractor;
import org.schabi.newpipe.extractor.linkhandler.ListLinkHandler;
import org.schabi.newpipe.extractor.localization.TimeAgoParser;
import org.schabi.newpipe.extractor.stream.StreamInfoItem;
import org.schabi.newpipe.extractor.stream.StreamInfoItemsCollector;

import java.io.IOException;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicReference;

import javax.annotation.Nonnull;

import static org.schabi.newpipe.extractor.services.youtube.YoutubeParsingHelper.*;
import static org.schabi.newpipe.extractor.services.youtube.YoutubeService.getTempLocalization;
import static org.schabi.newpipe.extractor.utils.Utils.UTF_8;
import static org.schabi.newpipe.extractor.utils.Utils.isNullOrEmpty;

public class YoutubeTrendingExtractor extends KioskExtractor<StreamInfoItem> {
    private JsonObject initialData;
    private JsonObject tempData;

    public YoutubeTrendingExtractor(final StreamingService service,
                                    final ListLinkHandler linkHandler,
                                    final String kioskId) {
        super(service, linkHandler, kioskId);
    }

    @Override
    public void onFetchPage(@Nonnull final Downloader downloader)
            throws IOException, ExtractionException {

        Exception lastException = null;

        for (int attempt = 0; attempt < 3; attempt++) {
            final byte[] body = JsonWriter.string(
                            prepareDesktopJsonBuilder(getExtractorLocalization(),
                                    getExtractorContentCountry())
                                    .value("browseId",
                                            getId().equals("Trending") ? "FEtrending"
                                                    : "UC4R8DWoMoI7CAwX8_LjQHig")
                                    .done())
                    .getBytes(UTF_8);

            final byte[] bodyTemp = JsonWriter.string(
                            prepareDesktopJsonBuilder(getTempLocalization(),
                                    getExtractorContentCountry())
                                    .value("browseId",
                                            getId().equals("Trending") ? "FEtrending"
                                                    : "UC4R8DWoMoI7CAwX8_LjQHig")
                                    .done())
                    .getBytes(UTF_8);

            final CountDownLatch latch = new CountDownLatch(2);

            final AtomicReference<JsonObject> refNormal = new AtomicReference<>();
            final AtomicReference<JsonObject> refTemp = new AtomicReference<>();
            final AtomicReference<Exception> refError = new AtomicReference<>();

            getJsonPostResponseAsync("browse", body, getExtractorLocalization(), new Downloader.AsyncCallback() {
                @Override
                public void onSuccess(Response response) {
                    try {
                        if (response.responseCode() == 400) {
                            refError.set(new IOException("HTTP 400 Bad Request"));
                        } else {
                            refNormal.set(JsonParser.object().from(response.responseBody()));
                        }
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

            getJsonPostResponseAsync("browse", bodyTemp, getTempLocalization(), new Downloader.AsyncCallback() {
                @Override
                public void onSuccess(Response response) {
                    try {
                        if (response.responseCode() == 400) {
                            refError.set(new IOException("HTTP 400 Bad Request"));
                        } else {
                            refTemp.set(JsonParser.object().from(response.responseBody()));
                        }
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
                throw new IOException("Interrupted while waiting for responses", e);
            }

            if (refError.get() != null) {
                lastException = refError.get();
                if (attempt == 2) {
                    if (lastException instanceof IOException) throw (IOException) lastException;
                    if (lastException instanceof ExtractionException) throw (ExtractionException) lastException;
                    throw new ExtractionException("Failed to fetch page after 3 attempts", lastException);
                }
                continue;
            }

            initialData = refNormal.get();
            tempData = refTemp.get();
            return;
        }
    }


    @Override
    public InfoItemsPage<StreamInfoItem> getPage(final Page page) {
        return InfoItemsPage.emptyPage();
    }

    @Nonnull
    @Override
    public String getName() throws ParsingException {
        final JsonObject header = initialData.getObject("header");
        String name = null;
        if (header.has("feedTabbedHeaderRenderer")) {
            name = getTextAtKey(header.getObject("feedTabbedHeaderRenderer"), "title");
        } else if (header.has("c4TabbedHeaderRenderer")) {
            name = getTextAtKey(header.getObject("c4TabbedHeaderRenderer"), "title");
        } else if (header.has("carouselHeaderRenderer")) {
            name = header.getObject("carouselHeaderRenderer").getArray("contents").getObject(0)
                    .getObject("topicChannelDetailsRenderer").getObject("title").getString("simpleText");
        }

        if (isNullOrEmpty(name)) {
            name = "Unknown";
        }
        return name;
    }

    @Nonnull
    @Override
    public InfoItemsPage<StreamInfoItem> getInitialPage() throws ParsingException {
        final StreamInfoItemsCollector collector = new StreamInfoItemsCollector(getServiceId());
        final TimeAgoParser timeAgoParser = getTimeAgoParser();

        /* pre-compute once – this is the only place that may throw */
        final JsonObject tabRenderer = getTrendingTabRenderer();
        final JsonObject tabContent = tabRenderer.getObject("content");

        /* ------------------------------------------------------------------
         * Helper that no longer throws ParsingException
         * ------------------------------------------------------------------ */
        java.util.function.BiConsumer<String, String> flatCollect = (rendererKey, videoKey) -> {
            if (!tabContent.has(rendererKey)) return;

            final JsonArray contents = tabContent.getObject(rendererKey).getArray("contents");
            final JsonArray tempContents = tempData.getObject("contents")
                    .getObject("twoColumnBrowseResultsRenderer")
                    .getArray("tabs").getObject(0)
                    .getObject("tabRenderer")
                    .getObject("content")
                    .getObject(rendererKey)
                    .getArray("contents");

            for (int i = 0; i < contents.size() && i < tempContents.size(); i++) {
                final JsonObject outer = contents.getObject(i);
                final JsonObject outerTemp = tempContents.getObject(i);

                JsonObject video = null;
                JsonObject videoTemp = null;

                if ("richGridRenderer".equals(rendererKey)) {
                    if (outer.has("richItemRenderer")) {
                        video = outer.getObject("richItemRenderer")
                                .getObject("content")
                                .getObject("videoRenderer");
                        videoTemp = outerTemp.getObject("richItemRenderer")
                                .getObject("content")
                                .getObject("videoRenderer");
                    }
                } else if ("sectionListRenderer".equals(rendererKey)) {
                    if (!outer.has("itemSectionRenderer")) continue;

                    final JsonArray innerContents =
                            outer.getObject("itemSectionRenderer").getArray("contents");
                    final JsonArray innerTempContents =
                            outerTemp.getObject("itemSectionRenderer").getArray("contents");

                    for (int j = 0; j < innerContents.size() && j < innerTempContents.size(); j++) {
                        final JsonObject shelf = innerContents.getObject(j);
                        final JsonObject shelfTemp = innerTempContents.getObject(j);
                        if (!shelf.has("shelfRenderer")) continue;

                        final JsonArray items =
                                shelf.getObject("shelfRenderer")
                                        .getObject("content")
                                        .getObject("expandedShelfContentsRenderer")
                                        .getArray("items");
                        final JsonArray itemsTemp =
                                shelfTemp.getObject("shelfRenderer")
                                        .getObject("content")
                                        .getObject("expandedShelfContentsRenderer")
                                        .getArray("items");

                        for (int k = 0; k < items.size() && k < itemsTemp.size(); k++) {
                            video = items.getObject(k).getObject("videoRenderer");
                            videoTemp = itemsTemp.getObject(k).getObject("videoRenderer");
                            if (video != null && videoTemp != null) {
                                collector.commit(new YoutubeStreamInfoItemExtractor(
                                        video, timeAgoParser, videoTemp));
                            }
                        }
                    }
                    continue; // already handled above
                }

                if (video != null && videoTemp != null) {
                    collector.commit(new YoutubeStreamInfoItemExtractor(
                            video, timeAgoParser, videoTemp));
                }
            }
        };

        /* ------------------------------------------------------------------
         * 1-4 extraction attempts (unchanged, but now use tabContent)
         * ------------------------------------------------------------------ */
        if (tabContent.has("richGridRenderer")) {
            flatCollect.accept("richGridRenderer", "videoRenderer");
        }

        if (tabContent.has("sectionListRenderer")) {
            flatCollect.accept("sectionListRenderer", "videoRenderer");
        }

        if (collector.getItems().isEmpty()) {
            flatCollect.accept("sectionListRenderer", "gridVideoRenderer");
        }

        if (collector.getItems().isEmpty()) {
            final JsonArray contents = tabContent.getObject("richGridRenderer").getArray("contents");
            final JsonArray tempContents = tempData.getObject("contents")
                    .getObject("twoColumnBrowseResultsRenderer")
                    .getArray("tabs").getObject(0)
                    .getObject("tabRenderer")
                    .getObject("content")
                    .getObject("richGridRenderer")
                    .getArray("contents");

            for (int i = 0; i < contents.size() && i < tempContents.size(); i++) {
                JsonObject section = contents.getObject(i);
                JsonObject sectionTemp = tempContents.getObject(i);
                if (!section.has("richSectionRenderer")) continue;

                JsonObject shelf = section.getObject("richSectionRenderer")
                        .getObject("content")
                        .getObject("richShelfRenderer");
                JsonObject shelfTemp = sectionTemp.getObject("richSectionRenderer")
                        .getObject("content")
                        .getObject("richShelfRenderer");

                JsonArray videos = shelf.getArray("contents");
                JsonArray videosTemp = shelfTemp.getArray("contents");

                for (int j = 0; j < videos.size() && j < videosTemp.size(); j++) {
                    JsonObject item = videos.getObject(j);
                    JsonObject itemTemp = videosTemp.getObject(j);
                    if (!item.has("richItemRenderer")) continue;

                    collector.commit(new YoutubeStreamInfoItemExtractor(
                            item.getObject("richItemRenderer")
                                    .getObject("content")
                                    .getObject("videoRenderer"),
                            timeAgoParser,
                            itemTemp.getObject("richItemRenderer")
                                    .getObject("content")
                                    .getObject("videoRenderer")));
                }
            }
        }

        if (collector.getItems().isEmpty()) {
            throw new ParsingException("Could not get trending page");
        }

        if (ServiceList.YouTube.getFilterTypes().contains("recommendations")) {
            collector.applyBlocking(ServiceList.YouTube.getFilterConfig());
        }
        return new InfoItemsPage<>(collector, null);
    }


    private JsonObject getTrendingTabRenderer() throws ParsingException {
        return initialData.getObject("contents")
                .getObject("twoColumnBrowseResultsRenderer")
                .getArray("tabs")
                .stream()
                .filter(JsonObject.class::isInstance)
                .map(JsonObject.class::cast)
                .map(tab -> tab.getObject("tabRenderer"))
                .filter(tabRenderer -> tabRenderer.getBoolean("selected"))
                // There should be at most one tab selected
                .findFirst()
                .orElseThrow(() -> new ParsingException("Could not get \"Now\" trending tab"));
    }
}
