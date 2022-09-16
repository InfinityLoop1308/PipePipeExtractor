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
            StringBuilder sortQuery = new StringBuilder();

            if (selectedContentFilter != null && !selectedContentFilter.isEmpty()) {
                final BilibiliFilters.BilibiliContentFilterItem contentItem =
                        // we assume that there is just one content filter
                        (BilibiliFilters.BilibiliContentFilterItem) selectedContentFilter.get(0);
                if (contentItem != null) {
                    if (!contentItem.query.isEmpty()) {
                        sortQuery = new StringBuilder("&" + contentItem.query);
                    }
                }
            }
            for (FilterItem sortItem : selectedSortFilter) {
                if (!((BilibiliSortFilterItem) sortItem).query.isEmpty()) {
                    sortQuery.append("&").append(((BilibiliSortFilterItem) sortItem).query);
                }
            }
            return sortQuery.toString();
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
        final int filterDuartionAll = builder.addSortItem(
                new BilibiliSortFilterItem("All", "duration=0")
        );
        final int filterDuartionShort = builder.addSortItem(
                new BilibiliSortFilterItem("< 10 min", "duration=1")
        );
        final int filterDuartionMedium = builder.addSortItem(
                new BilibiliSortFilterItem("10-30 min", "duration=2")
        );
        final int filterDuartionLong = builder.addSortItem(
                new BilibiliSortFilterItem("30-60 min", "duration=3")
        );
        final int filterDuartionExtraLong = builder.addSortItem(
                new BilibiliSortFilterItem("> 60 min", "duration=4")
        );
        final Filter videoSortFilters = new Filter.Builder(new FilterGroup[]{
                builder.createSortGroup("Sort by", true, new FilterItem[]{
                        builder.getFilterForId(filterOverallScore),
                        builder.getFilterForId(filterLatest),
                        builder.getFilterForId(filterViewCount),
                        builder.getFilterForId(filterBulletCommentCount),
                        builder.getFilterForId(filterCommentCount),
                        builder.getFilterForId(filterBookmarkCount),
                }),
                builder.createSortGroup("Duration", true, new FilterItem[]{
                        builder.getFilterForId(filterDuartionAll),
                        builder.getFilterForId(filterDuartionShort),
                        builder.getFilterForId(filterDuartionMedium),
                        builder.getFilterForId(filterDuartionLong),
                        builder.getFilterForId(filterDuartionExtraLong),
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
