package org.schabi.newpipe.extractor.services.niconico.search.filter;

import org.schabi.newpipe.extractor.search.filter.Filter;
import org.schabi.newpipe.extractor.search.filter.FilterItem;
import org.schabi.newpipe.extractor.search.filter.SearchFiltersBase;
import org.schabi.newpipe.extractor.services.bandcamp.search.filter.BandcampFilters;

public class NiconicoFilters extends SearchFiltersBase {

    private static final String ALL = "All";
    private static final String TAGS_ONLY = "TagsOnly";

    public NiconicoFilters() {
        init();
        build();
    }

    @Override
    public String evaluateSelectedContentFilters() {
        if (selectedSortFilter != null) {
            String sortQuery = "";

            if (selectedContentFilter != null && !selectedContentFilter.isEmpty()) {
                final NiconicoFilters.NiconicoContentFilterItem contentItem =
                        // we assume that there is just one content filter
                        (NiconicoFilters.NiconicoContentFilterItem ) selectedContentFilter.get(0);
                if (contentItem != null) {
                    if (!contentItem.query.isEmpty()) {
                        sortQuery = "&" + contentItem.query;
                    }
                }
            }
            return sortQuery;
        }
        return "";
    }

    @Override
    protected void init() {
        /* content filters */
        final int contentFilterAll = builder.addFilterItem(
                new NiconicoFilters.NiconicoContentFilterItem(ALL, "targets=title,description,tags"));
        final int contentFilterTagsOnly = builder.addFilterItem(
                new NiconicoFilters.NiconicoContentFilterItem(TAGS_ONLY, "targets=tagsExact"));
        this.defaultContentFilterId = contentFilterAll;

        /* content filters with sort filters */
        addContentFilter(builder.createSortGroup(null, true, new FilterItem[]{
                builder.getFilterForId(contentFilterAll),
                builder.getFilterForId(contentFilterTagsOnly),
        }));
    }

    public static class NiconicoContentFilterItem extends FilterItem {
        private final String query;

        public NiconicoContentFilterItem(final String name, final String query) {
            super(Filter.ITEM_IDENTIFIER_UNKNOWN, name);
            this.query = query;
        }
    }
}
