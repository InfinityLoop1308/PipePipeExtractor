package org.schabi.newpipe.extractor.services.media_ccc.linkHandler;

import org.schabi.newpipe.extractor.search.filter.FilterItem;

import org.schabi.newpipe.extractor.exceptions.ParsingException;
import org.schabi.newpipe.extractor.linkhandler.ListLinkHandlerFactory;

import java.util.List;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

public class MediaCCCConferencesListLinkHandlerFactory extends ListLinkHandlerFactory {
    @Override
    public String getId(final String url) throws ParsingException {
        return "conferences";
    }

    @Override
    public String getUrl(final String id, @Nonnull final List<FilterItem> contentFilter,
                         @Nullable final List<FilterItem> sortFilter) throws ParsingException {
        return "https://media.ccc.de/public/conferences";
    }

    @Override
    public boolean onAcceptUrl(final String url) {
        return url.equals("https://media.ccc.de/b/conferences")
                || url.equals("https://media.ccc.de/public/conferences")
                || url.equals("https://api.media.ccc.de/public/conferences");
    }
}
