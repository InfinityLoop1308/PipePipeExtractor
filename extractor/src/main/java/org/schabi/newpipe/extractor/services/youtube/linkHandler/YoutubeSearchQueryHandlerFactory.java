package org.schabi.newpipe.extractor.services.youtube.linkHandler;

import org.schabi.newpipe.extractor.exceptions.ParsingException;
import org.schabi.newpipe.extractor.linkhandler.SearchQueryHandlerFactory;
import org.schabi.newpipe.extractor.search.filter.FilterItem;
import org.schabi.newpipe.extractor.services.youtube.search.filter.YoutubeFilters;

import java.util.List;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

public final class YoutubeSearchQueryHandlerFactory extends SearchQueryHandlerFactory {

    private static YoutubeSearchQueryHandlerFactory instance = null;

    private YoutubeSearchQueryHandlerFactory() {
        super(new YoutubeFilters());
    }

    /**
     * Singleton to get the same objects of filters during search.
     * <p>
     * The content filter holds a variable search parameter: (filter.getParams())
     *
     * @return
     */
    @Nonnull
    public static synchronized YoutubeSearchQueryHandlerFactory getInstance() {
        if (instance == null) {
            instance = new YoutubeSearchQueryHandlerFactory();
        }
        return instance;
    }

    @Override
    public String getUrl(final String searchString,
                         @Nonnull final List<FilterItem> selectedContentFilter,
                         @Nullable final List<FilterItem> selectedSortFilter)
            throws ParsingException {
        searchFilters.setSelectedContentFilter(selectedContentFilter);
        searchFilters.setSelectedSortFilter(selectedSortFilter);
        return searchFilters.evaluateSelectedFilters(searchString);
    }
}
