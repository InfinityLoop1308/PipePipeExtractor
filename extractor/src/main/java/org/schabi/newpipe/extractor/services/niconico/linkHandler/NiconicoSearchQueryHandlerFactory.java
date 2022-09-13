package org.schabi.newpipe.extractor.services.niconico.linkHandler;

import static org.schabi.newpipe.extractor.utils.Utils.UTF_8;

import org.schabi.newpipe.extractor.exceptions.ParsingException;
import org.schabi.newpipe.extractor.linkhandler.SearchQueryHandlerFactory;
import org.schabi.newpipe.extractor.services.niconico.NiconicoService;

import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;
import java.util.List;

public class NiconicoSearchQueryHandlerFactory extends SearchQueryHandlerFactory {
    public static final int ITEMS_PER_PAGE = 10;
    public static final String ALL = "all";
    public static final String TAGS = "Tags";

    private static final String SEARCH_URL = "https://api.search.nicovideo.jp/api/v2/snapshot/video/contents/search";

    // https://site.nicovideo.jp/search-api-docs/snapshot
    @Override
    public String getUrl(final String id,
            final List<String> contentFilters,
            final String sortFilter) throws ParsingException {
        try {
            String url = SEARCH_URL + "?q=" + URLEncoder.encode(id, UTF_8);

            url += "&targets=";
            if (contentFilters.isEmpty()) {
                url += "title,description,tags";
            } else {
                switch (contentFilters.get(0)) {
                    case TAGS:
                        url += "tagsExact";
                        break;
                    case ALL:
                    default:
                        url += "title,description,tags";
                        break;
                }
            }

            url += "&fields=contentId,title,userId,channelId"
                    + ",viewCounter,lengthSeconds,thumbnailUrl,startTime"
                    + "&_sort=" + "-viewCounter"
                    + "&_offset=0"
                    + "&_limit=" + ITEMS_PER_PAGE
                    + "&_context=" + URLEncoder.encode(NiconicoService.APP_NAME, UTF_8);

            return url;
        } catch (final UnsupportedEncodingException e) {
            throw new ParsingException("could not encode query.");
        }
    }

    @Override
    public String[] getAvailableContentFilter() {
        return new String[] {
                ALL,
                TAGS
        };
    }
}