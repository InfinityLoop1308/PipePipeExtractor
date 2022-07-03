// Created by Fynn Godau 2019, licensed GNU GPL version 3 or later

package org.schabi.newpipe.extractor.services.bilibili.linkHandler;

import static org.schabi.newpipe.extractor.utils.Utils.UTF_8;

import org.schabi.newpipe.extractor.exceptions.ParsingException;
import org.schabi.newpipe.extractor.linkhandler.SearchQueryHandlerFactory;

import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;
import java.util.List;

public class BilibiliSearchQueryHandlerFactory extends SearchQueryHandlerFactory {


    private static final String VIDEOS = "video";
    private static final String LIVE = "live_room";
    private static final String USER = "bili_user";
    private static final String SEARCH_URL = "https://api.bilibili.com/x/web-interface/search/type?search_type=";

    @Override
    public String getUrl(final String query, final List<String> contentFilters, final String sortFilter)
            throws ParsingException {
        try {
            if (!contentFilters.isEmpty()) {
                final String contentFilter = contentFilters.get(0);
                final String searchString = query;
                switch (contentFilter) {
                    case VIDEOS:
                    default:
                        return SEARCH_URL + VIDEOS + "&keyword=" + URLEncoder.encode(searchString, UTF_8) + "&page=1";
                    case LIVE:
                        return SEARCH_URL + LIVE + "&keyword=" + URLEncoder.encode(searchString, UTF_8) + "&page=1";
                    case USER:
                        return SEARCH_URL + USER + "&keyword=" + URLEncoder.encode(searchString, UTF_8) + "&page=1";
                }
            }

            return SEARCH_URL + VIDEOS + "&keyword=" + URLEncoder.encode(query, UTF_8) + "&page=1";

        } catch (final UnsupportedEncodingException e) {
            throw new ParsingException("query \"" + query + "\" could not be encoded", e);
        }
    }

    @Override
    public String[] getAvailableContentFilter() {
        return new String[]{
                VIDEOS,
                LIVE,
                USER
        };
    }
}
