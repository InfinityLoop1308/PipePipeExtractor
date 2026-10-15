package org.schabi.newpipe.extractor.services.bilibili.linkHandler;

import java.util.List;
import java.util.regex.Pattern;

import org.schabi.newpipe.extractor.exceptions.ParsingException;
import org.schabi.newpipe.extractor.linkhandler.ListLinkHandlerFactory;
import org.schabi.newpipe.extractor.search.filter.Filter;
import org.schabi.newpipe.extractor.search.filter.FilterGroup;
import org.schabi.newpipe.extractor.search.filter.FilterItem;

public class BilibiliChannelLinkHandlerFactory extends ListLinkHandlerFactory {

    public static final String baseUrl = "https://space.bilibili.com/";

    public static final String SORT_LATEST = "latest";
    public static final String SORT_POPULAR = "popular";

    private final Filter availableSortFilter;

    public BilibiliChannelLinkHandlerFactory() {
        final FilterGroup.Builder filterBuilder = new FilterGroup.Builder();
        final int latestFilterId = filterBuilder.addSortItem(
                new FilterItem(Filter.ITEM_IDENTIFIER_UNKNOWN, SORT_LATEST));
        final int popularFilterId = filterBuilder.addSortItem(
                new FilterItem(Filter.ITEM_IDENTIFIER_UNKNOWN, SORT_POPULAR));

        availableSortFilter = new Filter.Builder(new FilterGroup[]{
                filterBuilder.createSortGroup("sortby", true, new FilterItem[]{
                        filterBuilder.getFilterForId(latestFilterId),
                        filterBuilder.getFilterForId(popularFilterId)
                })
        }).build();
    }

    @Override
    public Filter getAvailableSortFilter() {
        return availableSortFilter;
    }

    @Override
    public String getId(String url) throws ParsingException {
        if (url.contains("mid=")) {
            return url.split("mid=")[1];
        }
        url = url.split("\\?")[0];
        if(url.endsWith("/")){
            url = url.substring(0, url.length() - 1);
        }
        if(url.contains(baseUrl) || url.contains("/space/")){
            String[] temp = url.split(Pattern.quote("/"));
            return temp[temp.length - 1];
        }
        else {
            throw new ParsingException("Not a bilibili channel link.");
        }
    }

    @Override
    public boolean onAcceptUrl(final String url) throws ParsingException {
        try {
            getId(url);
            return true;
        } catch (ParsingException e) {
            return false;
        }
    }

    @Override
    public String getUrl(String id, final List<FilterItem> contentFilter,
                         final List<FilterItem> sortFilter) throws ParsingException {
        String url = baseUrl + id;
        if (sortFilter != null && !sortFilter.isEmpty()) {
            String sort = sortFilter.get(0).getName();
            if (SORT_POPULAR.equals(sort)) {
                url += "?order=click";
            } else if (SORT_LATEST.equals(sort)) {
                url += "?order=pubdate";
            }
        }
        return url;
    }
}
