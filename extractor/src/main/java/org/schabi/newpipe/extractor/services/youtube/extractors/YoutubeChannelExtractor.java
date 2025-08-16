package org.schabi.newpipe.extractor.services.youtube.extractors;

import com.grack.nanojson.JsonArray;
import com.grack.nanojson.JsonObject;
import com.grack.nanojson.JsonParser;
import com.grack.nanojson.JsonWriter;
import org.schabi.newpipe.extractor.Image;
import org.schabi.newpipe.extractor.Page;
import org.schabi.newpipe.extractor.ServiceList;
import org.schabi.newpipe.extractor.StreamingService;
import org.schabi.newpipe.extractor.channel.ChannelExtractor;
import org.schabi.newpipe.extractor.downloader.Downloader;
import org.schabi.newpipe.extractor.downloader.Response;
import org.schabi.newpipe.extractor.exceptions.ContentNotSupportedException;
import org.schabi.newpipe.extractor.exceptions.ExtractionException;
import org.schabi.newpipe.extractor.exceptions.ParsingException;
import org.schabi.newpipe.extractor.linkhandler.ChannelTabs;
import org.schabi.newpipe.extractor.linkhandler.ListLinkHandler;
import org.schabi.newpipe.extractor.localization.TimeAgoParser;
import org.schabi.newpipe.extractor.search.filter.Filter;
import org.schabi.newpipe.extractor.search.filter.FilterItem;
import org.schabi.newpipe.extractor.services.youtube.YoutubeChannelHelper;
import org.schabi.newpipe.extractor.services.youtube.YoutubeParsingHelper;
import org.schabi.newpipe.extractor.services.youtube.linkHandler.YoutubeChannelLinkHandlerFactory;
import org.schabi.newpipe.extractor.services.youtube.linkHandler.YoutubeChannelTabLinkHandlerFactory;
import org.schabi.newpipe.extractor.stream.StreamInfoItem;
import org.schabi.newpipe.extractor.stream.StreamInfoItemsCollector;
import org.schabi.newpipe.extractor.utils.JsonUtils;
import org.schabi.newpipe.extractor.utils.Utils;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import java.util.stream.Collectors;

import static java.nio.charset.StandardCharsets.UTF_8;
import static java.util.Collections.emptyList;
import static org.schabi.newpipe.extractor.services.youtube.YoutubeParsingHelper.*;
import static org.schabi.newpipe.extractor.services.youtube.YoutubeService.getTempLocalization;
import static org.schabi.newpipe.extractor.utils.Utils.isNullOrEmpty;

/*
 * Created by Christian Schabesberger on 25.07.16.
 *
 * Copyright (C) Christian Schabesberger 2018 <chris.schabesberger@mailbox.org>
 * YoutubeChannelExtractor.java is part of NewPipe.
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

public class YoutubeChannelExtractor extends ChannelExtractor {
    private JsonObject jsonResponse;
    private JsonObject videoTab;
    private List<ListLinkHandler> tabs;

    /**
     * Some channels have response redirects and the only way to reliably get the id is by saving it
     * <p>
     * "Movies & Shows":
     * <pre>
     * UCuJcl0Ju-gPDoksRjK1ya-w ┐
     * UChBfWrfBXL9wS6tQtgjt_OQ ├ UClgRkhTL3_hImCAmdLfDE4g
     * UCok7UTQQEP1Rsctxiv3gwSQ ┘
     * </pre>
     */

    // Constants of objects used multiples from channel responses
    private static final String IMAGE = "image";
    private static final String CONTENTS = "contents";
    private static final String CONTENT_PREVIEW_IMAGE_VIEW_MODEL = "contentPreviewImageViewModel";
    private static final String PAGE_HEADER_VIEW_MODEL = "pageHeaderViewModel";
    private static final String TAB_RENDERER = "tabRenderer";
    private static final String CONTENT = "content";
    private static final String METADATA = "metadata";
    private static final String AVATAR = "avatar";
    private static final String THUMBNAILS = "thumbnails";
    private static final String SOURCES = "sources";
    private static final String BANNER = "banner";

    private String redirectedChannelId;

    @SuppressWarnings("OptionalUsedAsFieldOrParameterType")
    private Optional<YoutubeChannelHelper.ChannelHeader> channelHeader;

    private String channelId;

    private JsonObject tempData;


    /**
     * If a channel is age-restricted, its pages are only accessible to logged-in and
     * age-verified users, we get an {@code channelAgeGateRenderer} in this case, containing only
     * the following metadata: channel name and channel avatar.
     *
     * <p>
     * This restriction doesn't seem to apply to all countries.
     * </p>
     */
    @Nullable
    private JsonObject channelAgeGateRenderer;

    public YoutubeChannelExtractor(final StreamingService service,
                                   final ListLinkHandler linkHandler) {
        super(service, linkHandler);
    }

    @Override
    public void onFetchPage(@Nonnull final Downloader downloader) throws IOException, ExtractionException {
        final String channelPath = super.getId();
        final String id = resolveChannelId(channelPath);

        final byte[] body = JsonWriter.string(prepareDesktopJsonBuilder(
                        getExtractorLocalization(), getExtractorContentCountry())
                        .value("browseId", id)
                        .value("params", "EgZ2aWRlb3PyBgQKAjoA")
                        .done())
                .getBytes(UTF_8);

        final byte[] bodyTemp = JsonWriter.string(prepareDesktopJsonBuilder(
                        getTempLocalization(), getExtractorContentCountry())
                        .value("browseId", id)
                        .value("params", "EgZ2aWRlb3PyBgQKAjoA")
                        .done())
                .getBytes(UTF_8);

        final CountDownLatch latch = new CountDownLatch(2);
        final AtomicReference<ChannelResponseData> refNormal = new AtomicReference<>();
        final AtomicReference<ChannelResponseData> refTemp = new AtomicReference<>();
        final AtomicReference<Exception> refError = new AtomicReference<>();

        getJsonPostResponseAsync("browse", body, getExtractorLocalization(), new Downloader.AsyncCallback() {
            @Override
            public void onSuccess(Response response) {
                try {
                    final JsonObject json = JsonParser.object().from(response.responseBody());
                    refNormal.set(new ChannelResponseData(json, id));
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
                    final JsonObject json = JsonParser.object().from(response.responseBody());
                    refTemp.set(new ChannelResponseData(json, id));
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
            throw new IOException("Interrupted while waiting for channel responses", e);
        }

        if (refError.get() != null) {
            final Exception ex = refError.get();
            if (ex instanceof IOException) throw (IOException) ex;
            if (ex instanceof ExtractionException) throw (ExtractionException) ex;
            throw new ExtractionException("Unexpected error", ex);
        }

        final ChannelResponseData normal = refNormal.get();
        final ChannelResponseData temp = refTemp.get();

        redirectedChannelId = normal.channelId;
        jsonResponse = normal.responseJson;
        tempData = temp.responseJson;

        channelHeader = YoutubeChannelHelper.getChannelHeader(jsonResponse);
        channelId = normal.channelId;
        channelAgeGateRenderer = YoutubeChannelHelper.getChannelAgeGateRenderer(jsonResponse);
    }


    @Nonnull
    @Override
    public String getUrl() throws ParsingException {
        try {
            return YoutubeChannelLinkHandlerFactory.getInstance().getUrl("channel/" + getId());
        } catch (final ParsingException e) {
            return super.getUrl();
        }
    }

    @Nonnull
    @Override
    public String getId() throws ParsingException {
        assertPageFetched();
        return YoutubeChannelHelper.getChannelId(channelHeader, jsonResponse, channelId);
    }

    @Nonnull
    @Override
    public String getName() throws ParsingException {
        assertPageFetched();
        return YoutubeChannelHelper.getChannelName(
                channelHeader, jsonResponse, channelAgeGateRenderer);
    }

    @Nonnull
    @Override
    public List<Image> getAvatars() throws ParsingException {
        assertPageFetched();
        if (channelAgeGateRenderer != null) {
            return Optional.ofNullable(channelAgeGateRenderer.getObject(AVATAR)
                            .getArray(THUMBNAILS))
                    .map(YoutubeParsingHelper::getImagesFromThumbnailsArray)
                    .orElseThrow(() -> new ParsingException("Could not get avatars"));
        }

        return channelHeader.map(header -> {
                    switch (header.headerType) {
                        case PAGE:
                            final JsonObject imageObj = header.json.getObject(CONTENT)
                                    .getObject(PAGE_HEADER_VIEW_MODEL)
                                    .getObject(IMAGE);

                            if (imageObj.has(CONTENT_PREVIEW_IMAGE_VIEW_MODEL)) {
                                return imageObj.getObject(CONTENT_PREVIEW_IMAGE_VIEW_MODEL)
                                        .getObject(IMAGE)
                                        .getArray(SOURCES);
                            }

                            if (imageObj.has("decoratedAvatarViewModel")) {
                                return imageObj.getObject("decoratedAvatarViewModel")
                                        .getObject(AVATAR)
                                        .getObject("avatarViewModel")
                                        .getObject(IMAGE)
                                        .getArray(SOURCES);
                            }

                            // Return an empty avatar array as a fallback
                            return new JsonArray();
                        case INTERACTIVE_TABBED:
                            return header.json.getObject("boxArt")
                                    .getArray(THUMBNAILS);

                        case C4_TABBED:
                        case CAROUSEL:
                        default:
                            return header.json.getObject(AVATAR)
                                    .getArray(THUMBNAILS);
                    }
                })
                .map(YoutubeParsingHelper::getImagesFromThumbnailsArray)
                .orElseThrow(() -> new ParsingException("Could not get avatars"));
    }

    @Nonnull
    @Override
    public List<Image> getBanners() {
        assertPageFetched();
        if (channelAgeGateRenderer != null) {
            return emptyList();
        }

        return channelHeader.map(header -> {
                    if (header.headerType == YoutubeChannelHelper.ChannelHeader.HeaderType.PAGE) {
                        final JsonObject pageHeaderViewModel = header.json.getObject(CONTENT)
                                .getObject(PAGE_HEADER_VIEW_MODEL);

                        if (pageHeaderViewModel.has(BANNER)) {
                            return pageHeaderViewModel.getObject(BANNER)
                                    .getObject("imageBannerViewModel")
                                    .getObject(IMAGE)
                                    .getArray(SOURCES);
                        }

                        // No banner is available (this should happen on pageHeaderRenderers of
                        // system channels), use an empty JsonArray instead
                        return new JsonArray();
                    }

                    return header.json
                            .getObject(BANNER)
                            .getArray(THUMBNAILS);
                })
                .map(YoutubeParsingHelper::getImagesFromThumbnailsArray)
                .orElse(emptyList());
    }

    @Override
    public String getAvatarUrl() throws ParsingException {
        return null;
    }


    @Override
    public String getFeedUrl() throws ParsingException {
        try {
            return YoutubeParsingHelper.getFeedUrlFrom(getId());
        } catch (final Exception e) {
            throw new ParsingException("Could not get feed url", e);
        }
    }

    @Override
    public long getSubscriberCount() throws ParsingException {
        assertPageFetched();
        if (channelAgeGateRenderer != null) {
            return UNKNOWN_SUBSCRIBER_COUNT;
        }

        if (channelHeader.isPresent()) {
            final YoutubeChannelHelper.ChannelHeader header = channelHeader.get();

            if (header.headerType == YoutubeChannelHelper.ChannelHeader.HeaderType.INTERACTIVE_TABBED) {
                // No subscriber count is available on interactiveTabbedHeaderRenderer header
                return UNKNOWN_SUBSCRIBER_COUNT;
            }

            final JsonObject headerJson = header.json;
            if (header.headerType == YoutubeChannelHelper.ChannelHeader.HeaderType.PAGE) {
                return getSubscriberCountFromPageChannelHeader(headerJson);
            }

            JsonObject textObject = null;

            if (headerJson.has("subscriberCountText")) {
                textObject = headerJson.getObject("subscriberCountText");
            } else if (headerJson.has("subtitle")) {
                textObject = headerJson.getObject("subtitle");
            }

            if (textObject != null) {
                try {
                    return Utils.mixedNumberWordToLong(getTextFromObject(textObject));
                } catch (final NumberFormatException e) {
                    return UNKNOWN_SUBSCRIBER_COUNT;
                }
            }
        }

        return UNKNOWN_SUBSCRIBER_COUNT;
    }

    private long getSubscriberCountFromPageChannelHeader(@Nonnull final JsonObject headerJson)
            throws ParsingException {
        final JsonObject metadataObject = headerJson.getObject(CONTENT)
                .getObject(PAGE_HEADER_VIEW_MODEL)
                .getObject(METADATA);

        if (!metadataObject.has("contentMetadataViewModel")) {
            return UNKNOWN_SUBSCRIBER_COUNT;
        }

        return metadataObject.getObject("contentMetadataViewModel")
                .getArray("metadataRows")
                .stream()
                .filter(JsonObject.class::isInstance)
                .map(JsonObject.class::cast)
                .flatMap(metadataRow -> metadataRow.getArray("metadataParts").stream())
                .filter(JsonObject.class::isInstance)
                .map(JsonObject.class::cast)
                .filter(metadataPart -> metadataPart.has("text"))
                .filter(metadataPart -> {
                    JsonObject textObject = metadataPart.getObject("text");
                    return textObject.has(CONTENT) &&
                            textObject.getString(CONTENT).toLowerCase().contains("subscriber");
                })
                .findFirst()
                .map(metadataPart -> {
                    try {
                        return Utils.mixedNumberWordToLong(metadataPart.getObject("text")
                                .getString(CONTENT));
                    } catch (NumberFormatException | ParsingException e) {
                        return UNKNOWN_SUBSCRIBER_COUNT;
                    }
                })
                .orElse(UNKNOWN_SUBSCRIBER_COUNT);
    }


    @Override
    public String getDescription() throws ParsingException {
        assertPageFetched();
        if (channelAgeGateRenderer != null) {
            return null;
        }

        try {
            if (channelHeader.isPresent()) {
                final YoutubeChannelHelper.ChannelHeader header = channelHeader.get();
                if (header.headerType == YoutubeChannelHelper.ChannelHeader.HeaderType.INTERACTIVE_TABBED) {
                    /*
                    In an interactiveTabbedHeaderRenderer, the real description, is only available
                    in its header
                    The other one returned in non-About tabs accessible in the
                    microformatDataRenderer object of the response may be completely different
                    The description extracted is incomplete and the original one can be only
                    accessed from the About tab
                     */
                    return getTextFromObject(header.json.getObject("description"));
                }
            }

            return jsonResponse.getObject(METADATA)
                    .getObject("channelMetadataRenderer")
                    .getString("description");
        } catch (final Exception e) {
            return null;
        }
    }


    @Override
    public String getParentChannelName() {
        return "";
    }

    @Override
    public String getParentChannelUrl() {
        return "";
    }

    @Override
    public String getParentChannelAvatarUrl() {
        return "";
    }

    @Override
    public boolean isVerified() throws ParsingException {
        assertPageFetched();
        if (channelAgeGateRenderer != null) {
            // Verified status is unknown with channelAgeGateRenderers, return false in this case
            return false;
        }

        return YoutubeChannelHelper.isChannelVerified(channelHeader.orElseThrow(() ->
                new ParsingException("Could not get verified status")));
    }

    @Nonnull
    @Override
    public List<ListLinkHandler> getTabs() throws ParsingException {
        getVideoTab();
        return tabs;
    }

    @Nonnull
    @Override
    public List<String> getTags() throws ParsingException {
        final JsonArray tags = jsonResponse.getObject("microformat")
                .getObject("microformatDataRenderer").getArray("tags");

        return tags.stream().map(Object::toString).collect(Collectors.toList());
    }

    @Nonnull
    @Override
    public InfoItemsPage<StreamInfoItem> getInitialPage() throws IOException, ExtractionException {
        final StreamInfoItemsCollector collector = new StreamInfoItemsCollector(getServiceId());

        Page nextPage = null;

        if (getVideoTab() != null) {
            final List<String> channelIds = new ArrayList<>();
            channelIds.add(getName());
            channelIds.add(getUrl());

            final JsonObject tempTab = findTabInTempData("videos");
            if (tempTab == null) {
                throw new ParsingException("Could not find matching video tab in tempData");
            }

            final JsonObject tempTabContent = tempTab.getObject("content");
            final JsonObject tabContent = getVideoTab().getObject("content");

            JsonArray items = tabContent
                    .getObject("sectionListRenderer")
                    .getArray("contents").getObject(0)
                    .getObject("itemSectionRenderer")
                    .getArray("contents").getObject(0)
                    .getObject("gridRenderer")
                    .getArray("items");

            JsonArray tempItems = tempTabContent
                    .getObject("sectionListRenderer")
                    .getArray("contents").getObject(0)
                    .getObject("itemSectionRenderer")
                    .getArray("contents").getObject(0)
                    .getObject("gridRenderer")
                    .getArray("items");

            if (items.isEmpty()) {
                items = tabContent.getObject("richGridRenderer").getArray("contents");
                tempItems = tempTabContent.getObject("richGridRenderer").getArray("contents");
            }

            final JsonObject continuation = collectStreamsFrom(collector, items, tempItems, channelIds);


            nextPage = getNextPageFrom(continuation, channelIds);
        }
        if (ServiceList.YouTube.getFilterTypes().contains("channels")) {
            collector.applyBlocking(ServiceList.YouTube.getFilterConfig());
        }
        return new InfoItemsPage<>(collector, nextPage);
    }

    @Override
    public InfoItemsPage<StreamInfoItem> getPage(final Page page) throws IOException, ExtractionException {
        if (page == null || isNullOrEmpty(page.getUrl())) {
            throw new IllegalArgumentException("Page doesn't contain an URL");
        }

        final List<String> channelIds = page.getIds();

        final StreamInfoItemsCollector collector = new StreamInfoItemsCollector(getServiceId());
        final Map<String, List<String>> headers = new HashMap<>();
        addYoutubeHeaders(headers);

        // Fetch main response
        final Response response = getDownloader().post(page.getUrl(), headers, page.getBody(),
                getExtractorLocalization());
        final JsonObject ajaxJson = JsonUtils.toJsonObject(getValidJsonResponseBody(response));

        // Fetch temp response
        final Response tempResponse = getDownloader().post(page.getUrl(), headers, replaceLocaleInBytes(page.getBody(), getTempLocalization().getLocalizationCode())
,
                getTempLocalization());
        final JsonObject tempAjaxJson = JsonUtils.toJsonObject(getValidJsonResponseBody(tempResponse));

        // Extract continuation items from both
        final JsonObject sectionListContinuation = ajaxJson.getArray("onResponseReceivedActions")
                .getObject(0)
                .getObject("appendContinuationItemsAction");

        final JsonObject tempSectionListContinuation = tempAjaxJson.getArray("onResponseReceivedActions")
                .getObject(0)
                .getObject("appendContinuationItemsAction");

        final JsonObject continuation = collectStreamsFrom(
                collector,
                sectionListContinuation.getArray("continuationItems"),
                tempSectionListContinuation.getArray("continuationItems"),
                channelIds
        );

        if (ServiceList.YouTube.getFilterTypes().contains("channels")) {
            collector.applyBlocking(ServiceList.YouTube.getFilterConfig());
        }

        return new InfoItemsPage<>(collector, getNextPageFrom(continuation, channelIds));
    }

    @Nullable
    private Page getNextPageFrom(final JsonObject continuations,
                                 final List<String> channelIds) throws IOException,
            ExtractionException {
        if (isNullOrEmpty(continuations)) {
            return null;
        }

        final JsonObject continuationEndpoint = continuations.getObject("continuationEndpoint");
        final String continuation = continuationEndpoint.getObject("continuationCommand")
                .getString("token");

        final byte[] body = JsonWriter.string(prepareDesktopJsonBuilder(getExtractorLocalization(),
                        getExtractorContentCountry())
                        .value("continuation", continuation)
                        .done())
                .getBytes(UTF_8);

        return new Page(YOUTUBEI_V1_URL + "browse?"
                + DISABLE_PRETTY_PRINT_PARAMETER, null, channelIds, null, body);
    }

    private byte[] replaceLocaleInBytes(final byte[] bodyBytes,
                                        final String targetLocale) throws ParsingException {
        try {
            // 1. bytes -> UTF-8 string
            final String bodyStr = new String(bodyBytes, StandardCharsets.UTF_8);

            // 2. parse JSON, replace "hl"
            final JsonObject body = JsonUtils.toJsonObject(bodyStr);
            body.getObject("context")
                    .getObject("client")
                    .put("hl", targetLocale);

            // 3. JSON -> UTF-8 bytes again
            return JsonWriter.string(body).getBytes(StandardCharsets.UTF_8);
        } catch (final Exception e) {
            throw new ParsingException("Could not replace locale in request body bytes", e);
        }
    }



    /**
     * Collect streams from an array of items
     *
     * @param collector  the collector where videos will be committed
     * @param videos     the array to get videos from
     * @param channelIds the ids of the channel, which are its name and its URL
     * @return the continuation object
     */
    private JsonObject collectStreamsFrom(@Nonnull final StreamInfoItemsCollector collector,
                                          @Nonnull final JsonArray videos,
                                          @Nonnull final JsonArray tempVideos,
                                          @Nonnull final List<String> channelIds)
    {
        collector.reset();

        final String uploaderName = channelIds.get(0);
        final String uploaderUrl = channelIds.get(1);
        final TimeAgoParser timeAgoParser = getTimeAgoParser();

        JsonObject continuation = null;

        for (int i = 0; i < videos.size() && i < tempVideos.size(); i++) {
            final JsonObject video = videos.getObject(i);
            final JsonObject videoTemp = tempVideos.getObject(i);

            if (video.has("gridVideoRenderer")) {
                collector.commit(new YoutubeStreamInfoItemExtractor(
                        video.getObject("gridVideoRenderer"),
                        timeAgoParser,
                        videoTemp.getObject("gridVideoRenderer")) {
                    @Override
                    public String getUploaderName() {
                        return uploaderName;
                    }

                    @Override
                    public String getUploaderUrl() {
                        return uploaderUrl;
                    }
                });
            } else if (video.has("richItemRenderer")) {
                final JsonObject content = video.getObject("richItemRenderer").getObject("content");
                final JsonObject contentTemp = videoTemp.getObject("richItemRenderer").getObject("content");

                collector.commit(new YoutubeStreamInfoItemExtractor(
                        content.getObject("videoRenderer"),
                        timeAgoParser,
                        contentTemp.getObject("videoRenderer")) {
                    @Override
                    public String getUploaderName() {
                        return uploaderName;
                    }

                    @Override
                    public String getUploaderUrl() {
                        return uploaderUrl;
                    }
                });
            } else if (video.has("continuationItemRenderer")) {
                continuation = video.getObject("continuationItemRenderer");
            }
        }


        return continuation;
    }

    @Nullable
    private JsonObject getVideoTab() throws ParsingException {
        if (this.videoTab != null) {
            return this.videoTab;
        }

        final JsonArray responseTabs = jsonResponse.getObject("contents")
                .getObject("twoColumnBrowseResultsRenderer")
                .getArray("tabs");

        JsonObject foundVideoTab = null;
        tabs = new ArrayList<>();

        final Consumer<String> addTab = tab -> {
            try {
                tabs.add(YoutubeChannelTabLinkHandlerFactory.getInstance().fromQuery(
                        redirectedChannelId, Collections.singletonList(new FilterItem(Filter.ITEM_IDENTIFIER_UNKNOWN, tab)), null));
            } catch (final ParsingException e) {
                e.printStackTrace();
            }
        };

        for (final Object tab : responseTabs) {
            if (((JsonObject) tab).has("tabRenderer")) {
                final JsonObject tabRenderer = ((JsonObject) tab).getObject("tabRenderer");
                final String tabUrl = tabRenderer.getObject("endpoint")
                        .getObject("commandMetadata").getObject("webCommandMetadata")
                        .getString("url");
                if (tabUrl != null) {
                    final String[] urlParts = tabUrl.split("/");
                    final String urlSuffix = urlParts[urlParts.length - 1];

                    switch (urlSuffix) {
                        case "videos":
                            addTab.accept(ChannelTabs.VIDEOS);
                            foundVideoTab = tabRenderer;
                            break;
                        case "playlists":
                            addTab.accept(ChannelTabs.PLAYLISTS);
                            break;
                        case "streams":
                            addTab.accept(ChannelTabs.LIVESTREAMS);
                            break;
                        case "shorts":
                            addTab.accept(ChannelTabs.SHORTS);
                            break;
                        case "channels":
                            addTab.accept(ChannelTabs.CHANNELS);
                            break;
                    }
                }
            }
        }

        if (foundVideoTab == null) {
            if (tabs.isEmpty()) {
                throw new ContentNotSupportedException("This channel has no supported tabs");
            }

            return null;
        }

        try {
            final String messageRendererText = getTextFromObject(foundVideoTab.getObject("content")
                    .getObject("sectionListRenderer").getArray("contents").getObject(0)
                    .getObject("itemSectionRenderer").getArray("contents").getObject(0)
                    .getObject("messageRenderer").getObject("text"));
            if (messageRendererText != null
                    && messageRendererText.equals("This channel has no videos.")) {
                return null;
            }
        } catch (final ParsingException ignored) {
        }

        this.videoTab = foundVideoTab;
        return foundVideoTab;
    }

    @Nullable
    private JsonObject findTabInTempData(final String tabSuffix) throws ParsingException {
        final JsonArray tabs = tempData.getObject("contents")
                .getObject("twoColumnBrowseResultsRenderer")
                .getArray("tabs");

        for (final Object tabObj : tabs) {
            final JsonObject tab = (JsonObject) tabObj;
            if (tab.has("tabRenderer")) {
                final JsonObject tabRenderer = tab.getObject("tabRenderer");
                final String tabUrl = tabRenderer.getObject("endpoint")
                        .getObject("commandMetadata")
                        .getObject("webCommandMetadata")
                        .getString("url", "");

                if (tabUrl != null && tabUrl.endsWith("/" + tabSuffix)) {
                    return tabRenderer;
                }
            }
        }
        return null;
    }

}
