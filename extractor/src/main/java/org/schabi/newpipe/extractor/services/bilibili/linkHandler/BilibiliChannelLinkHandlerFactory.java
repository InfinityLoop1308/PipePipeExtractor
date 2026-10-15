package org.schabi.newpipe.extractor.services.bilibili.linkHandler;

import java.util.Collections;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.schabi.newpipe.extractor.exceptions.ParsingException;
import org.schabi.newpipe.extractor.linkhandler.ListLinkHandler;
import org.schabi.newpipe.extractor.linkhandler.ListLinkHandlerFactory;
import org.schabi.newpipe.extractor.search.filter.Filter;
import org.schabi.newpipe.extractor.search.filter.FilterGroup;
import org.schabi.newpipe.extractor.search.filter.FilterItem;

public class BilibiliChannelLinkHandlerFactory extends ListLinkHandlerFactory {

    public static final String baseUrl = "https://space.bilibili.com/";

    public static final String SORT_LATEST = "latest";
    public static final String SORT_POPULAR = "popular";

    private static final BilibiliChannelLinkHandlerFactory INSTANCE =
            new BilibiliChannelLinkHandlerFactory();
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

    public static BilibiliChannelLinkHandlerFactory getInstance() {
        return INSTANCE;
    }

    @Override
    public Filter getAvailableSortFilter() {
        return availableSortFilter;
    }

    @Override
    public String getId(String url) throws ParsingException {
        if (url == null || url.trim().isEmpty()) {
            throw new ParsingException("Not a bilibili channel link: empty URL.");
        }
        String trimmed = url.trim();
        Matcher midParamMatcher = Pattern.compile("[?&]mid=(\\d+)").matcher(trimmed);
        if (midParamMatcher.find()) {
            return midParamMatcher.group(1);
        }
        Matcher spacePathMatcher = Pattern.compile("(?:space\\.bilibili\\.com/|/space/)(\\d+)").matcher(trimmed);
        if (spacePathMatcher.find()) {
            return spacePathMatcher.group(1);
        }
        if (trimmed.matches("^\\d+$")) {
            return trimmed;
        }
        throw new ParsingException("Not a bilibili channel link: " + url);
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
            if (SORT_POPULAR.equalsIgnoreCase(sort) || "click".equalsIgnoreCase(sort)) {
                url += "?order=click";
            } else if (SORT_LATEST.equalsIgnoreCase(sort) || "pubdate".equalsIgnoreCase(sort)) {
                url += "?order=pubdate";
            }
        }
        return url;
    }

    @Override
    public ListLinkHandler fromUrl(final String url) throws ParsingException {
        List<FilterItem> sortFilter = null;
        if (url != null) {
            if (url.contains("order=click") || url.contains("sort=popular") || url.contains("sort=views")) {
                sortFilter = Collections.singletonList(new FilterItem(Filter.ITEM_IDENTIFIER_UNKNOWN, SORT_POPULAR));
            } else if (url.contains("order=pubdate") || url.contains("sort=latest") || url.contains("sort=newest")) {
                sortFilter = Collections.singletonList(new FilterItem(Filter.ITEM_IDENTIFIER_UNKNOWN, SORT_LATEST));
            }
        }
        String id = getId(url);
        String cleanUrl = getUrl(id, null, sortFilter);
        return new ListLinkHandler(url, cleanUrl, id, Collections.emptyList(), sortFilter);
    }
}
