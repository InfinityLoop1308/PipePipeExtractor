package org.schabi.newpipe.extractor.services.niconico.linkHandler;

import static org.schabi.newpipe.extractor.services.niconico.NiconicoService.MYLIST_PAGE_URL;

import org.schabi.newpipe.extractor.exceptions.ParsingException;
import org.schabi.newpipe.extractor.linkhandler.ListLinkHandlerFactory;
import org.schabi.newpipe.extractor.search.filter.FilterItem;

import java.util.List;
import java.util.regex.Pattern;

public class NiconicoPlaylistLinkHandlerFactory extends ListLinkHandlerFactory {
    @Override
    public String getId(String url) throws ParsingException {
        if(url.contains("/mylist/")) {
            return url.split("/mylist/")[1].split(Pattern.quote("?"))[0];
        }
        throw new ParsingException("failed to extract ID from mylist.");
    }

    @Override
    public String getUrl(String id, List<FilterItem> contentFilter, List<FilterItem> sortFilter) throws ParsingException {
        return MYLIST_PAGE_URL + id;
    }

    @Override
    public boolean onAcceptUrl(String url) throws ParsingException {
        try {
            getId(url);
            return true;
        }catch (Exception e){
            return false;
        }
    }
}
