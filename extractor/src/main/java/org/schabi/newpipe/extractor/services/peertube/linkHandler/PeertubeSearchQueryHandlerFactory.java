package org.schabi.newpipe.extractor.services.peertube.linkHandler;

import org.schabi.newpipe.extractor.search.filter.Filter;
import org.schabi.newpipe.extractor.search.filter.FilterItem;
import org.schabi.newpipe.extractor.services.peertube.search.filter.PeertubeFilters;

import org.schabi.newpipe.extractor.ServiceList;
import org.schabi.newpipe.extractor.exceptions.ParsingException;
import org.schabi.newpipe.extractor.linkhandler.SearchQueryHandlerFactory;
import org.schabi.newpipe.extractor.services.peertube.PeertubeHelpers;

import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;
import java.util.List;
import java.util.Optional;

import static org.schabi.newpipe.extractor.utils.Utils.UTF_8;

public final class PeertubeSearchQueryHandlerFactory extends SearchQueryHandlerFactory {

    public static final String VIDEOS = "videos";
    public static final String CHANNELS = "channels";
    public static final String PLAYLISTS = "playlists";
    public static final String SEPIA_VIDEOS = "sepia_videos"; // sepia is the global index
    public static final String SEPIA_BASE_URL = "https://sepiasearch.org";
    public static final String SEARCH_ENDPOINT_VIDEOS = "/api/v1/search/videos";
    public static final String SEARCH_ENDPOINT_CHANNELS = "/api/v1/search/video-channels";
    public static final String SEARCH_ENDPOINT_PLAYLISTS = "/api/v1/search/video-playlists";

    private static PeertubeSearchQueryHandlerFactory instance = null;
    private final PeertubeFilters searchFilters = new PeertubeFilters();

    private PeertubeSearchQueryHandlerFactory() { }

    /**
     * Singleton to get the same objects of filters during search.
     *
     * The sort filter holds a variable search parameter: (filter.getQueryData())
     * @return
     */
    public static synchronized PeertubeSearchQueryHandlerFactory getInstance() {
        if (instance == null) {
            instance = new PeertubeSearchQueryHandlerFactory();
        }
        return instance;
    }

    @Override
    public String getUrl(final String searchString,
                         final List<FilterItem> selectedContentFilter,
                         final List<FilterItem> selectedSortFilters) throws ParsingException {

        final String baseUrl;
        final Optional<FilterItem> sepiaFilter =
                PeertubeHelpers.getSepiaFilter(selectedSortFilters);
        if (sepiaFilter.isPresent()) {
            baseUrl = SEPIA_BASE_URL;
        } else {
            baseUrl = ServiceList.PeerTube.getBaseUrl();
        }

        return getUrl(searchString, selectedContentFilter, selectedSortFilters, baseUrl);
    }

    @Override
    public String getUrl(final String searchString,
                         final List<FilterItem> selectedContentFilter,
                         final List<FilterItem> selectedSortFilter,
                         final String baseUrl) throws ParsingException {
        try {
            searchFilters.setSelectedSortFilter(selectedSortFilter);
            searchFilters.setSelectedContentFilter(selectedContentFilter);

            final String endpoint = getEndpoint(selectedContentFilter);
            // The video-channels and video-playlists endpoints only understand search, start,
            // count and a couple of sort keys; the video filters (isLive, nsfw, duration*,
            // startDate, most sort keys) are answered with a 400 or ignored, so they are only
            // appended to the videos endpoint.
            final String filterQuery = SEARCH_ENDPOINT_VIDEOS.equals(endpoint)
                    ? searchFilters.evaluateSelectedFilters(null)
                    : "";

            return baseUrl + endpoint + "?search=" + URLEncoder.encode(searchString, UTF_8)
                    + filterQuery;
        } catch (final UnsupportedEncodingException e) {
            throw new ParsingException("Could not encode query", e);
        }
    }

    /**
     * Get the search endpoint the selected content filter has to be searched on.
     *
     * <p>
     * PeerTube has one search endpoint per kind of content; there is no {@code resultType}
     * parameter which would let {@code /api/v1/search/videos} answer with channels or playlists.
     * </p>
     *
     * @param selectedContentFilter the selected content filters, which may be null or empty
     * @return the path of the endpoint to search on
     */
    private static String getEndpoint(final List<FilterItem> selectedContentFilter) {
        if (selectedContentFilter == null || selectedContentFilter.isEmpty()) {
            return SEARCH_ENDPOINT_VIDEOS;
        }

        switch (selectedContentFilter.get(0).getName()) {
            case CHANNELS:
                return SEARCH_ENDPOINT_CHANNELS;
            case PLAYLISTS:
                return SEARCH_ENDPOINT_PLAYLISTS;
            default:
                return SEARCH_ENDPOINT_VIDEOS;
        }
    }

    @Override
    public Filter getAvailableContentFilter() {
        return searchFilters.getContentFilters();
    }

    @Override
    public Filter getAvailableSortFilter() {
        return searchFilters.getSortFilters();
    }

    @Override
    public Filter getContentFilterSortFilterVariant(final int contentFilterId) {
        return searchFilters.getContentFilterSortFilterVariant(contentFilterId);
    }

    @Override
    public FilterItem getFilterItem(final int filterId) {
        return searchFilters.getFilterItem(filterId);
    }
}
