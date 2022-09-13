package org.schabi.newpipe.extractor.services.niconico.linkHandler;

import static org.schabi.newpipe.extractor.utils.Utils.UTF_8;

import org.schabi.newpipe.extractor.exceptions.ParsingException;
import org.schabi.newpipe.extractor.linkhandler.SearchQueryHandlerFactory;
import org.schabi.newpipe.extractor.services.niconico.NiconicoService;

import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;
import java.util.HashMap;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class NiconicoSearchQueryHandlerFactory extends SearchQueryHandlerFactory {
    public static final int ITEMS_PER_PAGE = 10;
    public static final String ALL = "all";
    public static final String TAGS = "Tags";
    public static final String VIEW_COUNTER = "view_count";
    public static final String MYLIST_COUNTER = "mylist_count";
    public static final String LIKE_COUNTER = "like_count";
    public static final String LENGTH_SECONDS = "length";
    public static final String START_TIME = "posting_time";
    public static final String COMMENT_COUNTER = "comment_count";
    public static final String LAST_COMMENT_TIME = "last_comment_time";
    public static final String ASCENDING = "ascending";
    public static final String DESCENDING = "descending";

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
                    + "&_sort=" + getSortFilter(sortFilter)
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

    @Override
    public String[] getAvailableSortFilter() {
        final String[] fields = new String[] {
                VIEW_COUNTER,
                MYLIST_COUNTER,
                LIKE_COUNTER,
                LENGTH_SECONDS,
                START_TIME,
                COMMENT_COUNTER,
                LAST_COMMENT_TIME
        };
        final String[] orders = new String[] { ASCENDING, DESCENDING };
        final String[] sortFilters = new String[fields.length * orders.length];
        for (int i = 0; i < fields.length; i++) {
            for (int j = 0; j < orders.length; j++) {
                sortFilters[i * orders.length + j] = fields[i] + "_" + orders[j];
            }
        }
        ;
        return sortFilters;
    }

    private String getSortFilter(final String sortFilter) {
        final HashMap<String, String> SORT_FILTERS = new HashMap<String, String>() {
            {
                put(VIEW_COUNTER, "viewCounter");
                put(MYLIST_COUNTER, "mylistCounter");
                put(LIKE_COUNTER, "likeCounter");
                put(LENGTH_SECONDS, "lengthSeconds");
                put(START_TIME, "startTime");
                put(COMMENT_COUNTER, "commentCounter");
                put(LAST_COMMENT_TIME, "lastCommentTime");
            }
        };

        final HashMap<String, String> SORT_ORDERS = new HashMap<String, String>() {
            {
                put(DESCENDING, "-");
                put(ASCENDING, "+");
            }
        };

        if (sortFilter.isEmpty()) {
            return SORT_ORDERS.get(DESCENDING) + SORT_FILTERS.get(VIEW_COUNTER);
        }
        final Pattern pattern = Pattern.compile("^(.+)_(.+)$");
        final Matcher matcher = pattern.matcher(sortFilter);
        if (!matcher.find()) {
            throw new IllegalArgumentException("Invalid sort filter: " + sortFilter);
        }
        final String field = SORT_FILTERS.get(matcher.group(0));
        final String order = SORT_ORDERS.get(matcher.group(1));
        if (field == null || order == null) {
            throw new IllegalArgumentException("Invalid sort filter: " + sortFilter);
        }
        return order + field;
    }
}