package org.schabi.newpipe.extractor.services.twitch.linkHandlers;

import org.schabi.newpipe.extractor.exceptions.ParsingException;
import org.schabi.newpipe.extractor.linkhandler.SearchQueryHandlerFactory;
import org.schabi.newpipe.extractor.search.filter.Filter;
import org.schabi.newpipe.extractor.search.filter.FilterGroup;
import org.schabi.newpipe.extractor.search.filter.FilterItem;
import org.schabi.newpipe.extractor.services.twitch.TwitchUrlBuilder;

import java.util.List;

public final class TwitchSearchQueryHandlerFactory extends SearchQueryHandlerFactory {
    @Override
    public String getUrl(String query,
                         List<FilterItem> contentFilter,
                         List<FilterItem> sortFilter) throws ParsingException, UnsupportedOperationException {
        return TwitchUrlBuilder.buildSearchUrlFromSearchQuery(query);
    }

    @Override
    public Filter getAvailableContentFilter() {
        return super.getAvailableContentFilter();
    }

    @Override
    public Filter getAvailableSortFilter() {
        return super.getAvailableSortFilter();
    }
}
