package org.schabi.newpipe.extractor.services.bilibili.linkHandler;

import java.util.List;
import java.util.regex.Pattern;

import org.schabi.newpipe.extractor.exceptions.ParsingException;
import org.schabi.newpipe.extractor.linkhandler.ListLinkHandlerFactory;
import org.schabi.newpipe.extractor.search.filter.FilterItem;

public class BilibiliChannelLinkHandlerFactory extends ListLinkHandlerFactory{
    
    public static final String baseUrl = "https://space.bilibili.com/";

    @Override
    public String getId(final String url) throws ParsingException {
        if (url.contains("mid=")) {
            return url.split("mid=")[1];
        }
        else if(url.contains(baseUrl)){
            return url.split(baseUrl)[1].split("\\?")[0];
        }
        else {
            throw new ParsingException("Not a bilibili channel link.");
        }
    }

    @Override
    public boolean onAcceptUrl(final String url) throws ParsingException {
        try {
            getId(url);
            return true;
        } catch (ParsingException e) {
            return false;
        }
    }

    @Override
    public String getUrl(String id, final List<FilterItem> contentFilter,
                         final List<FilterItem> sortFilter) throws ParsingException {
        return baseUrl + id;
    }


}
 