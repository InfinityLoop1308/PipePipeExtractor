package org.schabi.newpipe.extractor.services.twitch.linkHandlers;

import org.schabi.newpipe.extractor.exceptions.ParsingException;
import org.schabi.newpipe.extractor.linkhandler.ListLinkHandlerFactory;
import org.schabi.newpipe.extractor.search.filter.FilterItem;
import org.schabi.newpipe.extractor.services.twitch.TwitchUrlParser;

import java.util.List;

public class TwitchPlaylistLinkHandlerFactory extends ListLinkHandlerFactory {

    @Override
    public String getUrl(String id, List<FilterItem> contentFilter, List<FilterItem> sortFilter) throws ParsingException {
        return id;
    }

    @Override
    public String getId(String url) throws ParsingException {
        return url;
    }

    @Override
    public boolean onAcceptUrl(String url) throws ParsingException {
        try {
            TwitchUrlParser.parseCategoryUrl(url);
            return true;
        } catch (Exception ignored) {
            return false;
        }
    }
}
