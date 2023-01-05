package org.schabi.newpipe.extractor.services.niconico.search.filter;

import org.schabi.newpipe.extractor.search.filter.Filter;
import org.schabi.newpipe.extractor.search.filter.FilterGroup;
import org.schabi.newpipe.extractor.search.filter.FilterItem;
import org.schabi.newpipe.extractor.search.filter.SearchFiltersBase;
import org.schabi.newpipe.extractor.services.bilibili.search.filter.BilibiliFilters;
import org.schabi.newpipe.extractor.services.niconico.NiconicoService;

import java.util.Optional;

public final class NiconicoFilters extends SearchFiltersBase {

    private static final String ALL = "All";
    private static final String TAGS_ONLY = "TagsOnly";
    private static final String LIVES = "Lives";

    public NiconicoFilters() {
        init();
        build();
    }

    @Override
    public String evaluateSelectedFilters(final String searchString) {
        if (selectedSortFilter != null) {
            StringBuilder sortQuery = new StringBuilder();

            final Optional<FilterItem> ascendingFilter = selectedSortFilter.stream()
                    .filter(filterItem -> filterItem instanceof NiconicoSortOrderFilterItem)
                    .findFirst();
            boolean isAscending = ascendingFilter.isPresent();

            if (selectedContentFilter != null && !selectedContentFilter.isEmpty()) {
                final NiconicoFilters.NiconicoContentFilterItem contentItem =
                        // we assume that there is just one content filter
                        (NiconicoFilters.NiconicoContentFilterItem) selectedContentFilter.get(0);
                if (contentItem != null) {
                    if (!contentItem.query.isEmpty()) {
                        sortQuery = new StringBuilder("&" + contentItem.query);
                    }
                }
            }
            for (FilterItem sortItem : selectedSortFilter) {
                if (sortItem instanceof NiconicoSortFilterItem && !((NiconicoFilters.NiconicoSortFilterItem) sortItem).query.isEmpty()) {
                    sortQuery.append("&").append(((NiconicoSortFilterItem) sortItem).getQueryData(isAscending));
                }
            }
            return sortQuery.toString();
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
        final int contentFilterLiveRooms = builder.addFilterItem(
                new NiconicoFilters.NiconicoContentFilterItem(LIVES, ""));
        this.defaultContentFilterId = contentFilterAll;

        /* content filters with sort filters */
        addContentFilter(builder.createSortGroup(null, true, new FilterItem[]{
                builder.getFilterForId(contentFilterAll),
                builder.getFilterForId(contentFilterTagsOnly),
                builder.getFilterForId(contentFilterLiveRooms),
        }));

        /* 'Sort by' filter items */
        final int filterHottest = builder.addSortItem(
                new NiconicoSortFilterItem("Most Popular", "sort=h"));
        final int filterViewCount = builder.addSortItem(
                new NiconicoSortFilterItem("Views", "_sort=viewCounter"));
        final int filterBookmarkCount = builder.addSortItem(
                new NiconicoSortFilterItem("Bookmarks", "_sort=mylistCounter"));
        final int filterLikeCount = builder.addSortItem(
                new NiconicoSortFilterItem("Likes", "_sort=likeCounter"));
        final int filterCommentCount = builder.addSortItem(
                new NiconicoSortFilterItem("Comments", "_sort=commentCounter"));

        final int filterLength = builder.addSortItem(
                new NiconicoSortFilterItem("Length", "_sort=lengthSeconds"));

        final int filterPublishAt = builder.addSortItem(
                new NiconicoSortFilterItem("Publish Time", "_sort=startTime"));
        final int filterLastCommentedAt = builder.addSortItem(
                new NiconicoSortFilterItem("Last Comment Time", "_sort=lastCommentTime"));

        final int filterSortOrderAscending = builder.addSortItem(
                new NiconicoSortOrderFilterItem("Ascending"));

        final Filter allSortFilters = new Filter.Builder(new FilterGroup[]{
                builder.createSortGroup("Sort by", true, new FilterItem[]{
                        builder.getFilterForId(filterHottest),
                        builder.getFilterForId(filterViewCount),
                        builder.getFilterForId(filterCommentCount),
                        builder.getFilterForId(filterBookmarkCount),
                        builder.getFilterForId(filterLikeCount),
                        builder.getFilterForId(filterLength),
                        builder.getFilterForId(filterPublishAt),
                        builder.getFilterForId(filterLastCommentedAt),
                }),
                builder.createSortGroup("Sort order", false, new FilterItem[]{
                        builder.getFilterForId(filterSortOrderAscending)
                }),
        }).build();

        final Filter tagSortFilters = new Filter.Builder(new FilterGroup[]{
                builder.createSortGroup("Sort by", true, new FilterItem[]{
                        builder.getFilterForId(filterViewCount),
                        builder.getFilterForId(filterCommentCount),
                        builder.getFilterForId(filterBookmarkCount),
                        builder.getFilterForId(filterLikeCount),
                        builder.getFilterForId(filterLength),
                        builder.getFilterForId(filterPublishAt),
                        builder.getFilterForId(filterLastCommentedAt),
                }),
                builder.createSortGroup("Sort order", false, new FilterItem[]{
                        builder.getFilterForId(filterSortOrderAscending)
                }),
        }).build();
        addContentFilterSortVariant(contentFilterAll, allSortFilters);
        addContentFilterSortVariant(contentFilterTagsOnly, tagSortFilters);
    }

    private static class NiconicoSortFilterItem extends FilterItem {
        protected final String query;

        NiconicoSortFilterItem(final String name, final String query) {
            super(Filter.ITEM_IDENTIFIER_UNKNOWN, name);
            this.query = query;
        }

        public String getQueryData(boolean isAscending) {
            if (isAscending) {
                return query.replace("=", "=+");
            } else {
                return query.replace("=", "=-");
            }
        }
    }

    public static class NiconicoContentFilterItem extends FilterItem {
        private final String query;

        public NiconicoContentFilterItem(final String name, final String query) {
            super(Filter.ITEM_IDENTIFIER_UNKNOWN, name);
            this.query = query;
        }
    }

    private static class NiconicoSortOrderFilterItem extends FilterItem {
        NiconicoSortOrderFilterItem(final String name) {
            super(Filter.ITEM_IDENTIFIER_UNKNOWN, name);
        }
    }

}
