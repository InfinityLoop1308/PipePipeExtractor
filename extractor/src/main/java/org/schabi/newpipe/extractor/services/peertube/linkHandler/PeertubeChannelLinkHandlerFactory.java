package org.schabi.newpipe.extractor.services.peertube.linkHandler;

import org.schabi.newpipe.extractor.search.filter.FilterItem;

import org.schabi.newpipe.extractor.ServiceList;
import org.schabi.newpipe.extractor.exceptions.ParsingException;
import org.schabi.newpipe.extractor.linkhandler.ListLinkHandlerFactory;
import org.schabi.newpipe.extractor.utils.Parser;

import java.util.List;

public final class PeertubeChannelLinkHandlerFactory extends ListLinkHandlerFactory {

    private static final PeertubeChannelLinkHandlerFactory INSTANCE
            = new PeertubeChannelLinkHandlerFactory();
    private static final String ID_PATTERN = "((accounts|a)|(video-channels|c))/([^/?&#]*)";
    // From an URL, the pattern must be anchored on a path separator, otherwise the "a" and "c"
    // alternatives also match inside the host name (e.g. "kolektiva.media/c/foo" would give
    // "a/foo" instead of "c/foo", because of the "a" of "media").
    private static final String ID_URL_PATTERN = "/((accounts|a)|(video-channels|c))/([^/?&#]*)";
    public static final String API_ENDPOINT = "/api/v1/";

    private PeertubeChannelLinkHandlerFactory() {
    }

    public static PeertubeChannelLinkHandlerFactory getInstance() {
        return INSTANCE;
    }

    @Override
    public String getId(final String url) throws ParsingException {
        // Drop the leading path separator the pattern is anchored on
        return fixId(Parser.matchGroup(ID_URL_PATTERN, url, 0).substring(1));
    }

    @Override
    public String getUrl(final String id,
                         final List<FilterItem> contentFilters,
                         final List<FilterItem> searchFilter) throws ParsingException {
        return getUrl(id, contentFilters, searchFilter, ServiceList.PeerTube.getBaseUrl());
    }

    @Override
    public String getUrl(final String id,
                         final List<FilterItem> contentFilter,
                         final List<FilterItem> sortFilter,
                         final String baseUrl)
            throws ParsingException {
        if (id.matches(ID_PATTERN)) {
            return baseUrl + "/" + fixId(id);
        } else {
            // This is needed for compatibility with older versions were we didn't support
            // video channels yet
            return baseUrl + "/accounts/" + id;
        }
    }

    @Override
    public boolean onAcceptUrl(final String url) {
        // Only the first path segment counts, otherwise e.g. https://1drv.ms/w/c/... matches
        final String path;
        try {
            path = java.net.URI.create(url.trim()).getPath();
        } catch (final IllegalArgumentException e) {
            return false;
        }
        if (path == null) {
            return false;
        }
        final String p = path.startsWith("/api/v1/") ? path.substring(7) : path;
        return p.startsWith("/accounts/") || p.startsWith("/a/")
                || p.startsWith("/video-channels/") || p.startsWith("/c/");
    }

    /**
     * Fix id
     *
     * <p>
     * a/:accountName and c/:channelName ids are supported
     * by the PeerTube web client (>= v3.3.0)
     * but not by the API.
     * </p>
     *
     * @param id the id to fix
     * @return the fixed id
     */
    private String fixId(final String id) {
        if (id.startsWith("a/")) {
            return "accounts" + id.substring(1);
        } else if (id.startsWith("c/")) {
            return "video-channels" + id.substring(1);
        }
        return id;
    }
}
