package org.schabi.newpipe.extractor.services.bilibili.search.filter;

import org.schabi.newpipe.extractor.search.filter.Filter;
import org.schabi.newpipe.extractor.search.filter.FilterGroup;
import org.schabi.newpipe.extractor.search.filter.FilterItem;
import org.schabi.newpipe.extractor.search.filter.SearchFiltersBase;

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
            if (!selectedSortFilter.isEmpty()) {
                final BilibiliSortFilterItem sortItem =
                        // we assume that there is just one content filter
                        (BilibiliSortFilterItem) selectedSortFilter.get(0);
                if (sortItem != null) {
                    if (!sortItem.query.isEmpty()) {
                        sortQuery += "&" + sortItem.query;
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

        final int filterOverallScore = builder.addSortItem(
                new BilibiliSortFilterItem("Overall", "order=totalrank")
        );
        final int filterViewCount = builder.addSortItem(
                new BilibiliSortFilterItem("Views", "order=click")
        );
        final int filterLatest = builder.addSortItem(
                new BilibiliSortFilterItem("Latest", "order=pubdate")
        );
        final int filterBulletCommentCount = builder.addSortItem(
                new BilibiliSortFilterItem("Bullet Comments", "order=dm")
        );
        final int filterCommentCount = builder.addSortItem(
                new BilibiliSortFilterItem("Comments", "order=scores")
        );
        final int filterBookmarkCount = builder.addSortItem(
                new BilibiliSortFilterItem("Bookmarks", "order=stow")
        );
        final Filter videoSortFilters = new Filter.Builder(new FilterGroup[]{
                builder.createSortGroup("Sort by", true, new FilterItem[]{
                        builder.getFilterForId(filterOverallScore),
                        builder.getFilterForId(filterLatest),
                        builder.getFilterForId(filterViewCount),
                        builder.getFilterForId(filterBulletCommentCount),
                        builder.getFilterForId(filterCommentCount),
                        builder.getFilterForId(filterBookmarkCount),
                })
        }).build();
        addContentFilterSortVariant(contentFilterVideos, videoSortFilters);
    }

    public static class BilibiliContentFilterItem extends FilterItem {
        private final String query;

        public BilibiliContentFilterItem(final String name, final String query) {
            super(Filter.ITEM_IDENTIFIER_UNKNOWN, name);
            this.query = query;
        }
    }

    private static class BilibiliSortFilterItem extends FilterItem {
        protected final String query;

        BilibiliSortFilterItem(final String name, final String query) {
            super(Filter.ITEM_IDENTIFIER_UNKNOWN, name);
            this.query = query;
        }
    }
}
