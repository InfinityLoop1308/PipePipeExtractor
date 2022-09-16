package org.schabi.newpipe.extractor.services.bilibili.search.filter;

import org.schabi.newpipe.extractor.search.filter.Filter;
import org.schabi.newpipe.extractor.search.filter.FilterItem;
import org.schabi.newpipe.extractor.search.filter.SearchFiltersBase;
import org.schabi.newpipe.extractor.services.bandcamp.search.filter.BandcampFilters;
import org.schabi.newpipe.extractor.services.niconico.search.filter.NiconicoFilters;

public class BilibiliFilters extends SearchFiltersBase {
    private static final String VIDEOS = "Video";
    private static final String LIVES = "LiveRoom";
    private static final String USERS = "Channel";

    public BilibiliFilters() {
        init();
        build();
    }

    public String evaluateSelectedContentFilters() {
        if (selectedSortFilter != null) {
            String sortQuery = "";

            if (selectedContentFilter != null && !selectedContentFilter.isEmpty()) {
                final BilibiliFilters.BilibiliContentFilterItem contentItem =
                        // we assume that there is just one content filter
                        (BilibiliFilters.BilibiliContentFilterItem) selectedContentFilter.get(0);
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
        final int contentFilterVideos = builder.addFilterItem(
                new BilibiliFilters.BilibiliContentFilterItem(VIDEOS, "search_type=video"));
        final int contentFilterLives = builder.addFilterItem(
                new BilibiliFilters.BilibiliContentFilterItem(LIVES, "search_type=live_room"));
        final int contentFilterUsers = builder.addFilterItem(
                new BilibiliFilters.BilibiliContentFilterItem(USERS, "search_type=bili_user"));

        this.defaultContentFilterId = contentFilterVideos;

        /* content filters with sort filters */
        addContentFilter(builder.createSortGroup(null, true, new FilterItem[]{
                builder.getFilterForId(contentFilterVideos),
                builder.getFilterForId(contentFilterLives),
                builder.getFilterForId(contentFilterUsers),
        }));
    }

    public static class BilibiliContentFilterItem extends FilterItem {
        private final String query;

        public BilibiliContentFilterItem(final String name, final String query) {
            super(Filter.ITEM_IDENTIFIER_UNKNOWN, name);
            this.query = query;
        }
    }
}
