package org.schabi.newpipe.extractor.services.peertube.linkHandler;

import org.schabi.newpipe.extractor.exceptions.ParsingException;
import org.schabi.newpipe.extractor.linkhandler.ChannelTabs;
import org.schabi.newpipe.extractor.linkhandler.ListLinkHandler;
import org.schabi.newpipe.extractor.linkhandler.ListLinkHandlerFactory;
import org.schabi.newpipe.extractor.search.filter.Filter;
import org.schabi.newpipe.extractor.search.filter.FilterItem;
import org.schabi.newpipe.extractor.utils.Utils;

import java.net.URL;
import java.net.URLEncoder;
import java.util.Collections;
import java.util.List;

import static org.schabi.newpipe.extractor.utils.Utils.UTF_8;
import static org.schabi.newpipe.extractor.utils.Utils.isNullOrEmpty;

public final class PeertubeChannelTabLinkHandlerFactory extends ListLinkHandlerFactory {
    private static final PeertubeChannelTabLinkHandlerFactory INSTANCE
            = new PeertubeChannelTabLinkHandlerFactory();

    private PeertubeChannelTabLinkHandlerFactory() {
    }

    public static PeertubeChannelTabLinkHandlerFactory getInstance() {
        return INSTANCE;
    }

    public static String getUrlSuffix(final String tab) throws ParsingException {
        switch (tab) {
            case ChannelTabs.VIDEOS:
                // Searching inside a channel or an account filters its video list
            case ChannelTabs.SEARCH:
                return "/videos";
            case ChannelTabs.PLAYLISTS:
                return "/video-playlists";
            case ChannelTabs.CHANNELS:
                return "/video-channels";
        }
        throw new ParsingException("tab " + tab + " not supported");
    }

    /**
     * Get the query a channel search URL searches for.
     *
     * @param url a channel tab URL, which may carry no query at all
     * @return the query, or an empty string when the URL carries none
     */
    public static String getSearchQueryFromUrl(final String url) throws ParsingException {
        try {
            final URL urlObj = Utils.stringToURL(url);
            final String query = Utils.getQueryValue(urlObj, "query");
            return query == null ? "" : query;
        } catch (final Exception e) {
            throw new ParsingException("Could not parse channel search query", e);
        }
    }

    /**
     * Append the query of a channel search to a channel tab URL.
     *
     * @param url   the URL of the {@link ChannelTabs#SEARCH} tab of a channel
     * @param query the query to search the channel for, which may be empty
     * @return the URL, with the query appended when there is one
     */
    public static String appendSearchQueryIfNeeded(final String url,
                                                   final String query) throws ParsingException {
        if (isNullOrEmpty(query)) {
            return url;
        }

        try {
            return url + "?query=" + URLEncoder.encode(query, UTF_8).replace("+", "%20");
        } catch (final Exception e) {
            throw new ParsingException("Could not encode channel search query", e);
        }
    }

    @Override
    public String getId(final String url) throws ParsingException {
        return PeertubeChannelLinkHandlerFactory.getInstance().getId(url);
    }

    @Override
    public String getUrl(final String id, final List<FilterItem> contentFilter, final List<FilterItem> sortFilter)
            throws ParsingException {
        return PeertubeChannelLinkHandlerFactory.getInstance().getUrl(id)
                + getUrlSuffix(contentFilter.get(0).getName());
    }

    @Override
    public String getUrl(final String id,  final List<FilterItem> contentFilter, final List<FilterItem> sortFilter,
                         final String baseUrl) throws ParsingException {
        return PeertubeChannelLinkHandlerFactory.getInstance().getUrl(id, null, null, baseUrl)
                + getUrlSuffix(contentFilter.get(0).getName());
    }

    /**
     * {@inheritDoc}
     *
     * <p>
     * A channel search is saved and restored as the URL of the video list of the channel with the
     * query appended, exactly like YouTube does, so a URL carrying a query has to come back as the
     * {@link ChannelTabs#SEARCH} tab instead of the plain video list.
     * </p>
     */
    @Override
    public ListLinkHandler fromUrl(final String url, final String baseUrl) throws ParsingException {
        if (url == null) {
            throw new IllegalArgumentException("url may not be null");
        }

        if (!ChannelTabs.SEARCH.equals(getTabFromUrl(url))) {
            return super.fromUrl(url, baseUrl);
        }

        final String id = getId(url);
        final List<FilterItem> contentFilter = Collections.singletonList(
                new FilterItem(Filter.ITEM_IDENTIFIER_UNKNOWN, ChannelTabs.SEARCH));
        return new ListLinkHandler(url,
                appendSearchQueryIfNeeded(getUrl(id, contentFilter, null, baseUrl),
                        getSearchQueryFromUrl(url)),
                id, contentFilter, null);
    }

    /**
     * Get the tab a channel tab URL points at, as far as it can be told from the URL alone.
     *
     * @param url a channel tab URL
     * @return {@link ChannelTabs#SEARCH} when the URL carries a query, {@code null} otherwise
     */
    private static String getTabFromUrl(final String url) throws ParsingException {
        return isNullOrEmpty(getSearchQueryFromUrl(url)) ? null : ChannelTabs.SEARCH;
    }

    @Override
    public boolean onAcceptUrl(final String url) throws ParsingException {
        return PeertubeChannelLinkHandlerFactory.getInstance().onAcceptUrl(url);
    }

}
