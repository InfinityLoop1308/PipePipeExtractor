package org.schabi.newpipe.extractor.services.bilibili.linkHandler;

import java.util.List;

import org.schabi.newpipe.extractor.exceptions.ParsingException;
import org.schabi.newpipe.extractor.linkhandler.ListLinkHandlerFactory;
import org.schabi.newpipe.extractor.search.filter.FilterItem;

public class BilibiliFeedLinkHandlerFactory extends ListLinkHandlerFactory{

    @Override
    public String getUrl(String id, final List<FilterItem> contentFilter,
                         final List<FilterItem> sortFilter) throws ParsingException {
        // TODO Auto-generated method stub
        return "https://www.bilibili.com";
    }

    @Override
    public String getId(String url) throws ParsingException {
        // TODO Auto-generated method stub
        return "Trending";
    }

    @Override
    public boolean onAcceptUrl(String url) throws ParsingException {
        // TODO Auto-generated method stub
        return url.equals("https://www.bilibili.com");
    }
    
}
