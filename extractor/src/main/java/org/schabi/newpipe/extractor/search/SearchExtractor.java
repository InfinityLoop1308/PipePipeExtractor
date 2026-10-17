package org.schabi.newpipe.extractor.search;

import org.schabi.newpipe.extractor.*;
import org.schabi.newpipe.extractor.exceptions.ExtractionException;
import org.schabi.newpipe.extractor.exceptions.ParsingException;
import org.schabi.newpipe.extractor.linkhandler.SearchQueryHandler;

import javax.annotation.Nonnull;
import java.io.IOException;
import java.util.Collections;
import java.util.List;

import static org.schabi.newpipe.extractor.utils.Utils.isNullOrEmpty;

public abstract class SearchExtractor extends ListExtractor<InfoItem> {

    public static class NothingFoundException extends ExtractionException {
        public NothingFoundException(final String message) {
            super(message);
        }
    }

    public SearchExtractor(final StreamingService service, final SearchQueryHandler linkHandler) {
        super(service, linkHandler);
    }

    public String getSearchString() {
        return getLinkHandler().getSearchString();
    }

    /**
     * The search suggestion provided by the service.
     * <p>
     * This method also returns the corrected query if
     * {@link SearchExtractor#isCorrectedSearch()} is true.
     *
     * @return a suggestion to another query, the corrected query, or an empty String.
     */
    @Nonnull
    public String getSearchSuggestion() throws ParsingException {
        return "";
    }

    @Nonnull
    @Override
    public SearchQueryHandler getLinkHandler() {
        return (SearchQueryHandler) super.getLinkHandler();
    }

    @Nonnull
    @Override
    public String getName() {
        return getLinkHandler().getSearchString();
    }

    /**
     * Tell if the search was corrected by the service (if it's not exactly the search you typed).
     * <p>
     * Example: on YouTube, if you search for "pewdeipie",
     * it will give you results for "pewdiepie", then isCorrectedSearch should return true.
     *
     * @return whether the results comes from a corrected query or not.
     */
    public boolean isCorrectedSearch() throws ParsingException {
        return false;
    }

    /**
     * Meta information about the search query.
     * <p>
     * Example: on YouTube, if you search for "Covid-19",
     * there is a box with information from the WHO about Covid-19 and a link to the WHO's website.
     * @return additional meta information about the search query
     */
    @Nonnull
    public List<MetaInfo> getMetaInfo() throws ParsingException {
        return Collections.emptyList();
    }


    /**
     * Fetches and parses the very first page of search results.
     * This method should NOT apply any filters. Filtering is handled by the public getInitialPage().
     * @return An InfoItemsPage containing the raw items from the first page and a potential next page.
     */
    protected abstract InfoItemsPage<InfoItem> getInitialPageInternal() throws IOException, ExtractionException;

    /**
     * Fetches and parses a subsequent page of search results given a Page object.
     * This method should NOT apply any filters. Filtering is handled by the public getPage().
     * @param page The page descriptor for the page to fetch.
     * @return An InfoItemsPage containing the raw items from that page and a potential next page.
     */
    protected abstract InfoItemsPage<InfoItem> getPageInternal(final Page page) throws IOException, ExtractionException;

    @Override
    public final InfoItemsPage<InfoItem> getInitialPage() throws IOException, ExtractionException {
        return filterPage(getInitialPageInternal());
    }

    @Override
    public final InfoItemsPage<InfoItem> getPage(final Page page)
            throws IOException, ExtractionException {
        if (page == null || isNullOrEmpty(page.getUrl())) {
            throw new IllegalArgumentException("Page doesn't contain an URL");
        }
        return filterPage(getPageInternal(page));
    }

    private InfoItemsPage<InfoItem> filterPage(final InfoItemsPage<InfoItem> page) {
        if (!getService().getFilterTypes().contains("search_result")) {
            return page;
        }
        final MultiInfoItemsCollector collector = new MultiInfoItemsCollector(getServiceId());
        collector.addAll(page.getItems());
        collector.applyBlocking(getService().getFilterConfig());
        return new InfoItemsPage<>(collector.getItems(), page.getNextPage(), page.getErrors(),
                page.getFilteredCount() + collector.getFilteredCount());
    }
}
