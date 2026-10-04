package org.schabi.newpipe.extractor.services.twitch.linkHandlers;

import org.schabi.newpipe.extractor.exceptions.ParsingException;
import org.schabi.newpipe.extractor.linkhandler.ChannelTabs;
import org.schabi.newpipe.extractor.linkhandler.ListLinkHandlerFactory;
import org.schabi.newpipe.extractor.search.filter.FilterItem;
import org.schabi.newpipe.extractor.services.twitch.TwitchUrlBuilder;
import org.schabi.newpipe.extractor.services.twitch.TwitchUrlParser;
import org.schabi.newpipe.extractor.services.twitch.api.TwitchApiSettings;
import org.schabi.newpipe.extractor.services.twitch.data.TwitchChannelTabLinkType;
import org.schabi.newpipe.extractor.services.twitch.data.api.pagination.CursorBasedPagination;
import org.schabi.newpipe.extractor.services.twitch.data.api.pagination.PositionBasedPagination;
import org.schabi.newpipe.extractor.services.twitch.data.url.ChannelUrl;

import java.util.List;

public class TwitchChannelTabLinkHandlerFactory extends ListLinkHandlerFactory {
    private final TwitchApiSettings apiSettings;
    private ChannelUrl channelUrl;

    public TwitchChannelTabLinkHandlerFactory(TwitchApiSettings apiSettings) {
        this.apiSettings = apiSettings;
    }

    @Override
    public String getUrl(String id, List<FilterItem> contentFilter, List<FilterItem> sortFilter) throws ParsingException {
        return TwitchUrlBuilder.buildChannelTabUrl(
                channelUrl.channelLoginName(),
                channelUrl.channelId(),
                channelUrl.tabType(),
                apiSettings.shouldUseHelixForClips()
                        ? CursorBasedPagination.DEFAULT_CURSOR_PAGINATION
                        : PositionBasedPagination.DEFAULT_POSITION_PAGINATION
        );
    }

    @Override
    public String getId(String url) throws ParsingException {
        return url;
    }

    @Override
    public boolean onAcceptUrl(String url) throws ParsingException {
        try {
            channelUrl = TwitchUrlParser.parseChannelUrl(url);
            return true;
        } catch (Exception ignored) {
            return false;
        }
    }
}
