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
import org.schabi.newpipe.extractor.InfoItem;
import org.schabi.newpipe.extractor.InfoItemExtractor;
import org.schabi.newpipe.extractor.InfoItemsCollector;
import org.schabi.newpipe.extractor.MetaInfo;
import org.schabi.newpipe.extractor.MultiInfoItemsCollector;
import org.schabi.newpipe.extractor.Page;
import org.schabi.newpipe.extractor.StreamingService;
import org.schabi.newpipe.extractor.downloader.Downloader;
import org.schabi.newpipe.extractor.exceptions.ExtractionException;
import org.schabi.newpipe.extractor.exceptions.ParsingException;
import org.schabi.newpipe.extractor.linkhandler.SearchQueryHandler;
import org.schabi.newpipe.extractor.search.SearchExtractor;
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
            if(getLinkHandler().getUrl().contains(NiconicoService.SEARCH_URL)){
                return ;
            }
            searchCollection = JsonParser.object().from(response);
        } catch (final JsonParserException e) {
            throw new ExtractionException("could not parse search results.");
        }
    }

    @Nonnull
    @Override
    public InfoItemsPage<InfoItem> getInitialPage() throws IOException, ExtractionException {
        if(getLinkHandler().getUrl().contains(NiconicoService.SEARCH_URL)){
            return getPage(new Page(getUrl()));
        }
        if (searchCollection.getArray("data").size() == 0) {
            return new InfoItemsPage<>(collectItems(searchCollection),
                    null);
        }
        return new InfoItemsPage<>(collectItems(searchCollection),
                getNextPageFromCurrentUrl(getUrl()));
    }

    @Override
    public InfoItemsPage<InfoItem> getPage(final Page page)
            throws IOException, ExtractionException {
        if (page == null || isNullOrEmpty(page.getUrl())) {
            throw new IllegalArgumentException("page does not contain an URL.");
        }

        final String response = getDownloader().get(
                page.getUrl(), NiconicoService.LOCALE).responseBody();

        if(page.getUrl().contains(NiconicoService.SEARCH_URL)){
            Elements videos = Jsoup.parse(response).select("ul.list > li.item[data-nicoad-video]");
            final MultiInfoItemsCollector collector
                    = new MultiInfoItemsCollector(getServiceId());
            for (final Element e : videos) {
                collector.commit(new NiconicoSearchContentItemExtractor(e));
            }
            String currentPageString = page.getUrl().split("page=")[page.getUrl().split("page=").length-1];
            int currentPage = Integer.parseInt(currentPageString);
            String nextPage = page.getUrl().replace(String.format("page=%s", currentPageString), String.format("page=%s", String.valueOf(currentPage + 1)));
            return new InfoItemsPage<>(collector, new Page(nextPage));
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
