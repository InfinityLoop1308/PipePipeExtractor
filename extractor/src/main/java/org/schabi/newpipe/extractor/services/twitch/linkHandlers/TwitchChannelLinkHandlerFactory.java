package org.schabi.newpipe.extractor.services.twitch.linkHandlers;

import org.schabi.newpipe.extractor.exceptions.ParsingException;
import org.schabi.newpipe.extractor.linkhandler.ListLinkHandlerFactory;
import org.schabi.newpipe.extractor.search.filter.FilterItem;
import org.schabi.newpipe.extractor.services.twitch.TwitchUrlParser;

import java.util.List;

public final class TwitchChannelLinkHandlerFactory extends ListLinkHandlerFactory {
    @Override
    public String getId(String url) throws ParsingException, UnsupportedOperationException {
        return url;
    }

    @Override
    public String getUrl(String id, List<FilterItem> contentFilter, List<FilterItem> sortFilter) throws ParsingException, UnsupportedOperationException {
        return id;
    }

    @Override
    public boolean onAcceptUrl(String urlString) throws ParsingException {
        try {
            TwitchUrlParser.parseChannelUrl(urlString);
            return true;
        } catch (Exception e) {
            return false;
        }
    }
}
