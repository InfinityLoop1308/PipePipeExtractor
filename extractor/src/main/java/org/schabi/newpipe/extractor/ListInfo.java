package org.schabi.newpipe.extractor;

import org.schabi.newpipe.extractor.search.filter.FilterItem;

import org.schabi.newpipe.extractor.linkhandler.ListLinkHandler;

import java.util.List;

public abstract class ListInfo<T extends InfoItem> extends Info {
    private List<T> relatedItems;
    private Page nextPage = null;
    private final List<FilterItem> contentFilters;
    private final List<FilterItem> sortFilter;

    public ListInfo(final int serviceId,
                    final String id,
                    final String url,
                    final String originalUrl,
                    final String name,
                    final List<FilterItem> contentFilter,
                    final List<FilterItem> sortFilter) {
        super(serviceId, id, url, originalUrl, name);
        this.contentFilters = contentFilter;
        this.sortFilter = sortFilter;
    }

    public ListInfo(final int serviceId,
                    final ListLinkHandler listUrlIdHandler,
                    final String name) {
        super(serviceId, listUrlIdHandler, name);
        this.contentFilters = listUrlIdHandler.getContentFilters();
        this.sortFilter = listUrlIdHandler.getSortFilter();
    }

    public List<T> getRelatedItems() {
        return relatedItems;
    }

    public void setRelatedItems(final List<T> relatedItems) {
        this.relatedItems = relatedItems;
    }

    public boolean hasNextPage() {
        return Page.isValid(nextPage);
    }

    public Page getNextPage() {
        return nextPage;
    }

    public void setNextPage(final Page page) {
        this.nextPage = page;
    }

    public List<FilterItem> getContentFilters() {
        return contentFilters;
    }

    public List<FilterItem> getSortFilter() {
        return sortFilter;
    }
}
