package org.schabi.newpipe.extractor.services.niconico.extractors;

import static org.schabi.newpipe.extractor.services.niconico.linkHandler.NiconicoSearchQueryHandlerFactory.ITEMS_PER_PAGE;
import static org.schabi.newpipe.extractor.utils.Utils.isNullOrEmpty;

import com.grack.nanojson.JsonObject;
import com.grack.nanojson.JsonParser;
import com.grack.nanojson.JsonParserException;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import org.schabi.newpipe.extractor.*;
import org.schabi.newpipe.extractor.downloader.Downloader;
import org.schabi.newpipe.extractor.exceptions.ExtractionException;
import org.schabi.newpipe.extractor.exceptions.ParsingException;
import org.schabi.newpipe.extractor.linkhandler.SearchQueryHandler;
import org.schabi.newpipe.extractor.search.SearchExtractor;
import org.schabi.newpipe.extractor.services.bilibili.utils;
import org.schabi.newpipe.extractor.services.niconico.NiconicoService;

import java.io.IOException;
import java.util.Collections;
import java.util.List;

import javax.annotation.Nonnull;

public class NiconicoSearchExtractor extends SearchExtractor {
    private JsonObject searchCollection;

    public NiconicoSearchExtractor(final StreamingService service,
                                   final SearchQueryHandler linkHandler) {
        super(service, linkHandler);
    }

    @Override
    public void onFetchPage(final @Nonnull Downloader downloader)
            throws IOException, ExtractionException {
        final String response = getDownloader().get(
                getLinkHandler().getUrl(), NiconicoService.LOCALE).responseBody();
        try {
            if(getLinkHandler().getUrl().contains(NiconicoService.SEARCH_URL)
                    ||getLinkHandler().getUrl().contains(NiconicoService.LIVE_SEARCH_URL)){
                return ;
            }
            searchCollection = JsonParser.object().from(response);
        } catch (final JsonParserException e) {
            throw new ExtractionException("could not parse search results.");
        }
    }

    @Nonnull
    @Override
    public InfoItemsPage<InfoItem> getInitialPageInternal() throws IOException, ExtractionException {
        if(getLinkHandler().getUrl().contains(NiconicoService.SEARCH_URL)
                || getLinkHandler().getUrl().contains(NiconicoService.LIVE_SEARCH_URL)
                || getLinkHandler().getUrl().contains(NiconicoService.PLAYLIST_SEARCH_API_URL)){
            return getPage(new Page(getUrl()));
        } else {
            // Video search without popular filter
            if (searchCollection.getArray("data").size() == 0) {
                return new InfoItemsPage<>(collectItems(searchCollection),
                        null);
            }
            return new InfoItemsPage<>(collectItems(searchCollection),
                    getNextPageFromCurrentUrl(getUrl()));
        }
    }

    @Override
    public InfoItemsPage<InfoItem> getPageInternal(final Page page)
            throws IOException, ExtractionException {
        if (page == null || isNullOrEmpty(page.getUrl())) {
            throw new IllegalArgumentException("page does not contain an URL.");
        }

        final String response = getDownloader().get(
                page.getUrl(), NiconicoService.LOCALE).responseBody();

        if(page.getUrl().contains(NiconicoService.SEARCH_URL)){
            Element serverResponse = Jsoup.parse(response).selectFirst("meta[name=server-response]");
            if (serverResponse == null) {
                throw new ParsingException("Could not find server-response meta tag");
            }
            try {
                JsonObject responseJson = null;
                responseJson = JsonParser.object().from(serverResponse.attr("content"));
                JsonObject data = responseJson.getObject("data").getObject("response")
                        .getObject("$getSearchVideoV2").getObject("data");

                final MultiInfoItemsCollector collector = new MultiInfoItemsCollector(getServiceId());

                for (Object item : data.getArray("items")) {
                    collector.commit(new NiconicoSearchContentItemExtractor((JsonObject) item));
                }

                if(data.getArray("items").isEmpty()){
                    return new InfoItemsPage<>(collector, null);
                }

                return new InfoItemsPage<>(collector, new Page(utils.getNextPageFromCurrentUrl(page.getUrl(), "page", 1)));
            } catch (JsonParserException e) {
                throw new RuntimeException(e);
            }

        } else if (page.getUrl().contains(NiconicoService.LIVE_SEARCH_URL)) {
            Elements lives = Jsoup.parse(response).select("div.program-search-result").first()
                    .select("ul[class*=program-card-list] > li[class*=___program-card___]");
            final MultiInfoItemsCollector collector
                    = new MultiInfoItemsCollector(getServiceId());
            for (final Element e : lives) {
                collector.commit(new NiconicoLiveSearchInfoItemExtractor(e));
            }
            if(lives.size() == 0){
                return new InfoItemsPage<>(collector, null);
            }
            return new InfoItemsPage<>(collector, new Page(utils.getNextPageFromCurrentUrl(page.getUrl(), "page", 1)));
        } else if (page.getUrl().contains(NiconicoService.PLAYLIST_SEARCH_API_URL)){
            final MultiInfoItemsCollector collector
                    = new MultiInfoItemsCollector(getServiceId());
            try {
                searchCollection = JsonParser.object().from(response).getObject("data");
                if (searchCollection.getArray("items").size() == 0) {
                    return new InfoItemsPage<>(collector, null);
                }
            } catch (final JsonParserException e) {
                throw new ParsingException("could not parse search results.");
            }
            for (Object item : searchCollection.getArray("items")) {
                collector.commit(new NiconicoPlaylistInfoItemExtractor((JsonObject) item));
            }
            return new InfoItemsPage<>(collector, new Page(utils.getNextPageFromCurrentUrl(page.getUrl(), "page", 1)));
        }

        try {
            searchCollection = JsonParser.object().from(response);
            if (searchCollection.getArray("data").size() == 0) {
                return new InfoItemsPage<>(collectItems(searchCollection),
                        null);
            }
        } catch (final JsonParserException e) {
            throw new ParsingException("could not parse search results.");
        }

        return new InfoItemsPage<>(collectItems(searchCollection),
                getNextPageFromCurrentUrl(page.getUrl()));
    }

    @Nonnull
    @Override
    public String getSearchSuggestion() throws ParsingException {
        return "";
    }

    @Override
    public boolean isCorrectedSearch() throws ParsingException {
        return false;
    }

    @Nonnull
    @Override
    public List<MetaInfo> getMetaInfo() throws ParsingException {
        return Collections.emptyList();
    }

    private InfoItemsCollector<InfoItem, InfoItemExtractor> collectItems(
            final JsonObject collection) {
        final MultiInfoItemsCollector collector
                = new MultiInfoItemsCollector(getServiceId());

        for (int i = 0; i < collection.getArray("data").size(); i++) {
            collector.commit(
                    new NiconicoStreamInfoItemExtractor(
                            collection.getArray("data").getObject(i)));
        }
        return collector;
    }

    private Page getNextPageFromCurrentUrl(final String currentUrl)
            throws ParsingException {
        final String offset = currentUrl.split("&_offset=")[1].split("&")[0];
        return new Page(currentUrl.replace("&_offset=" + offset + "&", "&_offset="
                + (Integer.parseInt(offset) + ITEMS_PER_PAGE) + "&"));
    }
}
