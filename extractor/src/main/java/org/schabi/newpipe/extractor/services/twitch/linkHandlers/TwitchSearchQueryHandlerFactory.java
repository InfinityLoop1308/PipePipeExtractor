package org.schabi.newpipe.extractor.services.twitch.linkHandlers;

import org.schabi.newpipe.extractor.exceptions.ParsingException;
import org.schabi.newpipe.extractor.linkhandler.SearchQueryHandlerFactory;
import org.schabi.newpipe.extractor.search.filter.Filter;
import org.schabi.newpipe.extractor.search.filter.FilterGroup;
import org.schabi.newpipe.extractor.search.filter.FilterItem;
import org.schabi.newpipe.extractor.services.twitch.TwitchSearchFilters;
import org.schabi.newpipe.extractor.services.twitch.TwitchUrlBuilder;

import java.lang.reflect.Array;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;

public final class TwitchSearchQueryHandlerFactory extends SearchQueryHandlerFactory {

    private final TwitchSearchFilters searchFilters = new TwitchSearchFilters();

    @Override
    public String getUrl(String query,
                         List<FilterItem> contentFilter,
                         List<FilterItem> sortFilter) throws ParsingException, UnsupportedOperationException {
        String contentType = null;

        if(!contentFilter.isEmpty()) {
            final var stringifiedFilters = contentFilter.stream().map(filter ->
                    switch (filter.getIdentifier()) {
                        case 1 -> "channel_with_tag";
                        case 2 -> "channel";
                        case 3 -> "vod";
                        case 4 -> "game";
                        default -> null;
                    }).filter(Objects::nonNull).toList();
            if(!stringifiedFilters.isEmpty())
                contentType = String.join(",", stringifiedFilters);
        }

        return TwitchUrlBuilder.buildSearchUrlFromSearchQuery(query, contentType);
    }

    @Override
    public Filter getAvailableContentFilter() {
        return searchFilters.getContentFilters();
    }

    @Override
    public FilterItem getFilterItem(final int filterId) {
        return searchFilters.getFilterItem(filterId);
    }
}
