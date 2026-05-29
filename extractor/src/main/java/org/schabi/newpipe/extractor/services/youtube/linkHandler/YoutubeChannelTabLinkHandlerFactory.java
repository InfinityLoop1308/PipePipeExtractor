package org.schabi.newpipe.extractor.services.youtube.linkHandler;

import org.schabi.newpipe.extractor.exceptions.ParsingException;
import org.schabi.newpipe.extractor.linkhandler.ChannelTabs;
import org.schabi.newpipe.extractor.linkhandler.ListLinkHandlerFactory;
import org.schabi.newpipe.extractor.search.filter.Filter;
import org.schabi.newpipe.extractor.search.filter.FilterGroup;
import org.schabi.newpipe.extractor.search.filter.FilterItem;

import java.util.List;

import javax.annotation.Nonnull;

public final class YoutubeChannelTabLinkHandlerFactory extends ListLinkHandlerFactory {
    public static final String SORT_LATEST = "latest";
    public static final String SORT_POPULAR = "popular";
    public static final String SORT_OLDEST = "oldest";

    private static final YoutubeChannelTabLinkHandlerFactory INSTANCE =
            new YoutubeChannelTabLinkHandlerFactory();
    private final FilterGroup.Builder filterBuilder;
    private final Filter availableSortFilter;

    private YoutubeChannelTabLinkHandlerFactory() {
        filterBuilder = new FilterGroup.Builder();

        final int latestFilterId = filterBuilder.addSortItem(
                new FilterItem(Filter.ITEM_IDENTIFIER_UNKNOWN, SORT_LATEST));
        final int popularFilterId = filterBuilder.addSortItem(
                new FilterItem(Filter.ITEM_IDENTIFIER_UNKNOWN, SORT_POPULAR));
        final int oldestFilterId = filterBuilder.addSortItem(
                new FilterItem(Filter.ITEM_IDENTIFIER_UNKNOWN, SORT_OLDEST));

        availableSortFilter = new Filter.Builder(new FilterGroup[]{
                filterBuilder.createSortGroup("sortby", true, new FilterItem[]{
                        filterBuilder.getFilterForId(latestFilterId),
                        filterBuilder.getFilterForId(popularFilterId),
                        filterBuilder.getFilterForId(oldestFilterId),
                })
        }).build();
    }

    public static YoutubeChannelTabLinkHandlerFactory getInstance() {
        return INSTANCE;
    }

    public static String getUrlSuffix(final String tab) throws ParsingException {
        switch (tab) {
            case ChannelTabs.VIDEOS:
                return "/videos";
            case ChannelTabs.PLAYLISTS:
                return "/playlists";
            case ChannelTabs.PODCASTS:
                return "/podcasts";
            case ChannelTabs.LIVESTREAMS:
                return "/streams";
            case ChannelTabs.SHORTS:
                return "/shorts";
            case ChannelTabs.CHANNELS:
                return "/channels";
        }
        throw new ParsingException("tab " + tab + " not supported");
    }

    @Override
    public String getUrl(final String id,
                         @Nonnull final List<FilterItem> selectedContentFilter,
                         final List<FilterItem> selectedSortFilter)
            throws ParsingException {
        return "https://www.youtube.com/" + id
                + getUrlSuffix(selectedContentFilter.get(0).getName())
                + getSortUrlQuery(selectedSortFilter);
    }

    private static String getSortUrlQuery(final List<FilterItem> selectedSortFilter)
            throws ParsingException {
        if (selectedSortFilter == null || selectedSortFilter.isEmpty()) {
            return "";
        }

        final String selectedSortFilterName = selectedSortFilter.get(0).getName();
        switch (selectedSortFilterName) {
            case SORT_LATEST:
            case SORT_POPULAR:
            case SORT_OLDEST:
                return "?sort=" + selectedSortFilterName;
            default:
                throw new ParsingException("Unsupported YouTube channel tab sort filter: "
                        + selectedSortFilterName);
        }
    }

    @Override
    public Filter getAvailableSortFilter() {
        return availableSortFilter;
    }

    @Override
    public Filter getContentFilterSortFilterVariant(final int contentFilterId) {
        return availableSortFilter;
    }

    @Override
    public FilterItem getFilterItem(final int filterId) {
        return filterBuilder.getFilterForId(filterId);
    }

    @Override
    public String getId(final String url) throws ParsingException {
        return YoutubeChannelLinkHandlerFactory.getInstance().getId(url);
    }

    @Override
    public boolean onAcceptUrl(final String url) throws ParsingException {
        try {
            getId(url);
        } catch (final ParsingException e) {
            return false;
        }
        return true;
    }


}
