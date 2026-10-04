package org.schabi.newpipe.extractor.services.twitch;

import org.schabi.newpipe.extractor.search.filter.Filter;
import org.schabi.newpipe.extractor.search.filter.FilterItem;
import org.schabi.newpipe.extractor.search.filter.SearchFiltersBase;

public class TwitchSearchFilters extends SearchFiltersBase {

    public TwitchSearchFilters() {
        init();
        build();
    }

    protected void init() {
        /* content filters */
        final var allFilter = builder.addFilterItem(
                new FilterItem(Filter.ITEM_IDENTIFIER_UNKNOWN, "All"));
        final var streamsFilter = builder.addFilterItem(
                new FilterItem(Filter.ITEM_IDENTIFIER_UNKNOWN, "Streams"));
        final var channelsFilter = builder.addFilterItem(
                new FilterItem(Filter.ITEM_IDENTIFIER_UNKNOWN, "Channels"));
        final var videosFilter = builder.addFilterItem(
                new FilterItem(Filter.ITEM_IDENTIFIER_UNKNOWN, "Videos"));
        final var categoriesFilter = builder.addFilterItem(
                new FilterItem(Filter.ITEM_IDENTIFIER_UNKNOWN, "Categories"));

        // 'ALL' this is the default search content filter.
        this.defaultContentFilterId = allFilter;

        /* content filters with sort filters */
        addContentFilter(builder.createSortGroup(null, false, new FilterItem[]{
                builder.getFilterForId(allFilter),
                builder.getFilterForId(streamsFilter),
                builder.getFilterForId(channelsFilter),
                builder.getFilterForId(videosFilter),
                builder.getFilterForId(categoriesFilter)
        }));
    }
}
