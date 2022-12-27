package org.schabi.newpipe.extractor.services.bilibili.linkHandler;

import java.util.List;

import org.schabi.newpipe.extractor.exceptions.ParsingException;
import org.schabi.newpipe.extractor.linkhandler.ListLinkHandlerFactory;
import org.schabi.newpipe.extractor.search.filter.FilterItem;

public class BilibiliFeedLinkHandlerFactory extends ListLinkHandlerFactory{

    @Override
    public String getUrl(String id, final List<FilterItem> contentFilter,
                         final List<FilterItem> sortFilter) throws ParsingException {
        switch (id){
            case "Trending":
            default:
                return "https://www.bilibili.com";
            case "Recommend Lives":
                return "https://live.bilibili.com/all";
        }
    }

    @Override
    public String getId(String url) throws ParsingException {
        if(url.equals("https://www.bilibili.com")){
            return "Trending";
        }else if(url.equals("https://live.bilibili.com/all")){
            return "Recommend Lives";
        }
        return null;
    }

    @Override
    public boolean onAcceptUrl(String url) throws ParsingException {
        return url.equals("https://www.bilibili.com") || url.equals("https://live.bilibili.com/all");
    }
    
}
