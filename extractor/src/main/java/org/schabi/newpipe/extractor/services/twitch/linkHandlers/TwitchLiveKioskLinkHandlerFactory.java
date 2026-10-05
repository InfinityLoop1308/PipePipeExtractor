package org.schabi.newpipe.extractor.services.twitch.linkHandlers;

import org.schabi.newpipe.extractor.exceptions.ParsingException;
import org.schabi.newpipe.extractor.linkhandler.ListLinkHandlerFactory;
import org.schabi.newpipe.extractor.search.filter.FilterItem;
import org.schabi.newpipe.extractor.services.twitch.TwitchUrlBuilder;
import org.schabi.newpipe.extractor.services.twitch.TwitchUrlParser;
import org.schabi.newpipe.extractor.services.twitch.api.TwitchApiSettings;
import org.schabi.newpipe.extractor.services.twitch.data.api.pagination.CursorBasedPagination;
import org.schabi.newpipe.extractor.services.twitch.data.api.pagination.PositionBasedPagination;

import java.util.List;

public class TwitchLiveKioskLinkHandlerFactory extends ListLinkHandlerFactory {

    private final TwitchApiSettings apiSettings;

    public TwitchLiveKioskLinkHandlerFactory(TwitchApiSettings apiSettings) {
        this.apiSettings = apiSettings;
    }

    @Override
    public String getUrl(String id, List<FilterItem> contentFilter, List<FilterItem> sortFilter) throws ParsingException, UnsupportedOperationException {
        return TwitchUrlBuilder.buildLiveKioskUrlFromPagination(
                apiSettings.shouldUseHelixForKiosk()
                        ? CursorBasedPagination.DEFAULT_CURSOR_PAGINATION
                        : PositionBasedPagination.DEFAULT_POSITION_PAGINATION
        );
    }

    @Override
    public String getId(String url) throws ParsingException, UnsupportedOperationException {
        return "live";
    }

    @Override
    public boolean onAcceptUrl(String url) throws ParsingException {
        try {
            TwitchUrlParser.parsePaginationFromKioskUrl(url);
            return true;
        } catch (Exception ignored) {
            return false;
        }
    }
}
