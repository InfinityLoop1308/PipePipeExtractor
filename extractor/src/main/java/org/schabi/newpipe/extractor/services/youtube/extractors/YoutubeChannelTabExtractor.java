package org.schabi.newpipe.extractor.services.youtube.extractors;

import com.grack.nanojson.JsonArray;
import com.grack.nanojson.JsonObject;
import com.grack.nanojson.JsonWriter;
import org.schabi.newpipe.extractor.*;
import org.schabi.newpipe.extractor.channel.ChannelTabExtractor;
import org.schabi.newpipe.extractor.downloader.Downloader;
import org.schabi.newpipe.extractor.downloader.Response;
import org.schabi.newpipe.extractor.exceptions.ExtractionException;
import org.schabi.newpipe.extractor.exceptions.ParsingException;
import org.schabi.newpipe.extractor.linkhandler.ChannelTabs;
import org.schabi.newpipe.extractor.linkhandler.ListLinkHandler;
import org.schabi.newpipe.extractor.search.filter.FilterItem;
import org.schabi.newpipe.extractor.services.youtube.YoutubeChannelHelper;
import org.schabi.newpipe.extractor.services.youtube.linkHandler.YoutubeChannelTabLinkHandlerFactory;
import org.schabi.newpipe.extractor.utils.JsonUtils;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.function.Consumer;

import static org.schabi.newpipe.extractor.services.youtube.YoutubeParsingHelper.ChannelResponseData;
import static org.schabi.newpipe.extractor.services.youtube.YoutubeParsingHelper.DISABLE_PRETTY_PRINT_PARAMETER;
import static org.schabi.newpipe.extractor.services.youtube.YoutubeParsingHelper.YOUTUBEI_V1_URL;
import static org.schabi.newpipe.extractor.services.youtube.YoutubeParsingHelper.addYoutubeHeaders;
import static org.schabi.newpipe.extractor.services.youtube.YoutubeParsingHelper.getChannelResponse;
import static org.schabi.newpipe.extractor.services.youtube.YoutubeParsingHelper.getValidJsonResponseBody;
import static org.schabi.newpipe.extractor.services.youtube.YoutubeParsingHelper.prepareDesktopJsonBuilder;
import static org.schabi.newpipe.extractor.services.youtube.YoutubeParsingHelper.resolveChannelId;
import static org.schabi.newpipe.extractor.utils.Utils.isNullOrEmpty;

public class YoutubeChannelTabExtractor extends ChannelTabExtractor {
    private JsonObject jsonResponse;
    private JsonObject tabData;

    private String redirectedChannelId;
    @Nullable
    private String visitorData;

    private boolean useVisitorData = false;
    private String channelId;
    @Nullable
    @SuppressWarnings("OptionalUsedAsFieldOrParameterType")
    protected Optional<YoutubeChannelHelper.ChannelHeader> channelHeader;

    public YoutubeChannelTabExtractor(final StreamingService service,
                                      final ListLinkHandler linkHandler) {
        super(service, linkHandler);
        try {
            useVisitorData = getName().equals(ChannelTabs.SHORTS);
        } catch (ParsingException e) {
        }
    }

    private String getChannelTabsParameters() throws ParsingException {
        final String name = getName();
        switch (name) {
            case ChannelTabs.VIDEOS:
                return "EgZ2aWRlb3PyBgQKAjoA";
            case ChannelTabs.SHORTS:
                return "EgZzaG9ydHPyBgUKA5oBAA%3D%3D";
            case ChannelTabs.LIVESTREAMS:
                return "EgdzdHJlYW1z8gYECgJ6AA%3D%3D";
            case ChannelTabs.ALBUMS:
                return "EghyZWxlYXNlc_IGBQoDsgEA";
            case ChannelTabs.PLAYLISTS:
                return "EglwbGF5bGlzdHPyBgQKAkIA";
            default:
                throw new ParsingException("Unsupported channel tab: " + name);
        }
    }

    @Override
    public void onFetchPage(@Nonnull final Downloader downloader) throws IOException,
            ExtractionException {

        final String channelIdFromId = resolveChannelId(super.getId());

        final String params = getChannelTabsParameters();

        final ChannelResponseData data = getChannelResponse(channelIdFromId,
                params, getExtractorLocalization(), getExtractorContentCountry());

        jsonResponse = data.responseJson;
        channelHeader = YoutubeChannelHelper.getChannelHeader(jsonResponse);
        channelId = data.channelId;
        if (useVisitorData) {
            visitorData = jsonResponse.getObject("responseContext").getString("visitorData");
        }
    }

    @Nonnull
    @Override
    public String getUrl() throws ParsingException {
        try {
            return YoutubeChannelTabLinkHandlerFactory.getInstance().getUrl("channel/" + getId(),
                    Collections.singletonList(new FilterItem(-1, getTab())), null);
        } catch (final ParsingException e) {
            return super.getUrl();
        }
    }

    @Nonnull
    @Override
    public String getId() throws ParsingException {
        return YoutubeChannelHelper.getChannelId(channelHeader, jsonResponse, channelId);
    }

    protected String getChannelName() throws ParsingException {
        return YoutubeChannelHelper.getChannelName(
                channelHeader, jsonResponse,
                YoutubeChannelHelper.getChannelAgeGateRenderer(jsonResponse));
    }

    @Nonnull
    @Override
    public InfoItemsPage<InfoItem> getInitialPage() throws IOException, ExtractionException {
        final MultiInfoItemsCollector collector = new MultiInfoItemsCollector(getServiceId());

        Page nextPage = null;

        if (getTabData() != null) {
            final JsonObject tabContent = tabData.getObject("content");
            JsonArray items = tabContent
                    .getObject("sectionListRenderer")
                    .getArray("contents").getObject(0).getObject("itemSectionRenderer")
                    .getArray("contents").getObject(0).getObject("gridRenderer").getArray("items");

            if (items.isEmpty()) {
                items = tabContent.getObject("richGridRenderer").getArray("contents");

                if (items.isEmpty()) {
                    items = tabContent.getObject("sectionListRenderer").getArray("contents");
                }
            }

            final List<String> channelIds = new ArrayList<>();
            channelIds.add(getChannelName());
            channelIds.add(getUrl());
            final JsonObject continuation = collectItemsFrom(collector, items, channelIds);

            nextPage = getNextPageFrom(continuation, channelIds);
        }
        if (ServiceList.YouTube.getFilterTypes().contains("channels")) {
            collector.applyBlocking(ServiceList.YouTube.getFilterConfig());
        }
        return new InfoItemsPage<>(collector, nextPage);
    }

    @Override
    public InfoItemsPage<InfoItem> getPage(final Page page)
            throws IOException, ExtractionException {
        if (page == null || isNullOrEmpty(page.getUrl())) {
            throw new IllegalArgumentException("Page doesn't contain an URL");
        }

        final List<String> channelIds = page.getIds();

        final MultiInfoItemsCollector collector = new MultiInfoItemsCollector(getServiceId());
        final Map<String, List<String>> headers = new HashMap<>();
        addYoutubeHeaders(headers);

        final Response response = getDownloader().post(page.getUrl(), headers, page.getBody(),
                getExtractorLocalization());

        final JsonObject ajaxJson = JsonUtils.toJsonObject(getValidJsonResponseBody(response));

        final JsonObject sectionListContinuation = ajaxJson.getArray("onResponseReceivedActions")
                .getObject(0)
                .getObject("appendContinuationItemsAction");

        final JsonObject continuation = collectItemsFrom(collector, sectionListContinuation
                .getArray("continuationItems"), channelIds);
        if (ServiceList.YouTube.getFilterTypes().contains("channels")) {
            collector.applyBlocking(ServiceList.YouTube.getFilterConfig());
        }
        return new InfoItemsPage<>(collector,
                getNextPageFrom(continuation, channelIds));
    }

    @Nullable
    private JsonObject getTabData() throws ParsingException {
        if (this.tabData != null) {
            return this.tabData;
        }

        final String urlSuffix = YoutubeChannelTabLinkHandlerFactory.getUrlSuffix(getTab());

        final JsonArray tabs = jsonResponse.getObject("contents")
                .getObject("twoColumnBrowseResultsRenderer")
                .getArray("tabs");

        JsonObject foundTab = null;
        for (final Object tab : tabs) {
            if (((JsonObject) tab).has("tabRenderer")) {
                if (((JsonObject) tab).getObject("tabRenderer").getObject("endpoint")
                        .getObject("commandMetadata").getObject("webCommandMetadata")
                        .getString("url").endsWith(urlSuffix)) {
                    foundTab = ((JsonObject) tab).getObject("tabRenderer");
                    break;
                }
            }
        }

        // No tab
        if (foundTab == null) {
            return null;
        }

        // No content
        final JsonArray tabContents = foundTab.getObject("content").getObject("sectionListRenderer")
                .getArray("contents").getObject(0)
                .getObject("itemSectionRenderer").getArray("contents");
        if (tabContents.size() == 1 && tabContents.getObject(0).has("messageRenderer")) {
            return null;
        }

        this.tabData = foundTab;
        return foundTab;
    }

    private void commitPlaylistLockup(@Nonnull final MultiInfoItemsCollector collector,
                                      @Nonnull final JsonObject playlistLockupViewModel,
//                                      @Nonnull final VerifiedStatus channelVerifiedStatus,
                                      @Nullable final String channelName,
                                      @Nullable final String channelUrl) {
        collector.commit(
                new YoutubeMixOrPlaylistLockupInfoItemExtractor(playlistLockupViewModel) {
                    @Override
                    public String getUploaderName() throws ParsingException {
                        return isNullOrEmpty(channelName) ? super.getUploaderName() : channelName;
                    }

//                    @Override
//                    public String getUploaderUrl() throws ParsingException {
//                        return isNullOrEmpty(channelUrl) ? super.getUploaderName() : channelUrl;
//                    }
//
//                    @Override
//                    public boolean isUploaderVerified() throws ParsingException {
//                        switch (channelVerifiedStatus) {
//                            case VERIFIED:
//                                return true;
//                            case UNVERIFIED:
//                                return false;
//                            default:
//                                return super.isUploaderVerified();
//                        }
//                    }
                });
    }



    @Nullable
    private JsonObject collectItemsFrom(@Nonnull final MultiInfoItemsCollector collector,
                                        @Nonnull final JsonArray items,
                                        @Nonnull final List<String> channelIds) throws ParsingException {
        JsonObject continuation = null;

        for (final Object object : items) {
            final JsonObject item = (JsonObject) object;
            final JsonObject optContinuation = collectItem(
                    collector, item, channelIds);
            if (optContinuation != null) {
                continuation = optContinuation;
            }
        }
        return continuation;
    }

    @Nullable
    private JsonObject collectItem(@Nonnull final MultiInfoItemsCollector collector,
                                   @Nonnull final JsonObject item,
                                   @Nonnull final List<String> channelIds) throws ParsingException {
        final Consumer<JsonObject> commitVideo = videoRenderer -> collector.commit(
                new YoutubeStreamInfoItemExtractor(videoRenderer, getTimeAgoParser()) {
                    @Override
                    public String getUploaderName() {
                        return channelIds.get(0);
                    }

                    @Override
                    public String getUploaderUrl() {
                        return channelIds.get(1);
                    }
                });

        if (item.has("gridVideoRenderer")) {
            commitVideo.accept(item.getObject("gridVideoRenderer"));
        } else if (item.has("richItemRenderer")) {
            final JsonObject richItem = item.getObject("richItemRenderer").getObject("content");

            if (richItem.has("videoRenderer")) {
                commitVideo.accept(richItem.getObject("videoRenderer"));

            } else if (richItem.has("reelItemRenderer")) {
                commitVideo.accept(richItem.getObject("reelItemRenderer"));
            } else if (richItem.has("shortsLockupViewModel")) {
                collector.commit(new YoutubeShortsInfoItemExtractor(
                        richItem.getObject("shortsLockupViewModel")
                ) {
                    @Override
                    public String getUploaderName() {
                        return channelIds.get(0);
                    }
                });
            }
        } else if (item.has("gridPlaylistRenderer")) {
            collector.commit(new YoutubePlaylistInfoItemExtractor(
                    item.getObject("gridPlaylistRenderer")) {
                @Override
                public String getUploaderName() {
                    return channelIds.get(0);
                }
            });
        } else if (item.has("gridChannelRenderer")) {
            collector.commit(new YoutubeChannelInfoItemExtractor(
                    item.getObject("gridChannelRenderer")));
        } else if (item.has("shelfRenderer")) {
            return collectItem(collector, item.getObject("shelfRenderer")
                    .getObject("content"), channelIds);
        } else if (item.has("itemSectionRenderer")) {
            return collectItemsFrom(collector, item.getObject("itemSectionRenderer")
                    .getArray("contents"), channelIds);
        } else if (item.has("horizontalListRenderer")) {
            return collectItemsFrom(collector, item.getObject("horizontalListRenderer")
                    .getArray("items"), channelIds);
        } else if (item.has("expandedShelfContentsRenderer")) {
            return collectItemsFrom(collector, item.getObject("expandedShelfContentsRenderer")
                    .getArray("items"), channelIds);
        } else if (item.has("continuationItemRenderer")) {
            return item.getObject("continuationItemRenderer");
        } else if (item.has("lockupViewModel")) {
            final JsonObject lockupViewModel = item.getObject("lockupViewModel");
            if ("LOCKUP_CONTENT_TYPE_PLAYLIST".equals(lockupViewModel.getString("contentType"))) {
                String channelName;
                try {
                     channelName = getChannelName();
                } catch (Exception e) {
                    channelName = channelIds.get(0);
                }
                commitPlaylistLockup(collector, lockupViewModel,
                        channelName, null);
            }
        }
        return null;
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
                .getBytes(StandardCharsets.UTF_8);

        return new Page(YOUTUBEI_V1_URL + "browse?"
                + DISABLE_PRETTY_PRINT_PARAMETER, null, channelIds, null, body);
    }
}
