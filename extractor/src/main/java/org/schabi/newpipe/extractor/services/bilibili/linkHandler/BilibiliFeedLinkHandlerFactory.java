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
            case "Recommended Videos":
            default:
                return "https://www.bilibili.com";
            case "Top 100":
                return "https://api.bilibili.com/x/web-interface/ranking/v2";
            case "Recommended Lives":
                return "https://live.bilibili.com/all";
        }
    }

    @Override
    public String getId(String url) throws ParsingException {
        switch (url){
            case "https://www.bilibili.com":
                return "Recommended Videos";
            case "https://live.bilibili.com/all":
                return "Recommended Lives";
            case "https://api.bilibili.com/x/web-interface/ranking/v2":
                return "Top 100";
            default:
                return null;
        }
    }

    @Override
    public boolean onAcceptUrl(String url) throws ParsingException {
        return url.equals("https://www.bilibili.com")
                || url.equals("https://live.bilibili.com/all")
                || url.equals("https://api.bilibili.com/x/web-interface/ranking/v2");
    }
    
}
