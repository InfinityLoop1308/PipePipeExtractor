package org.schabi.newpipe.extractor.services.bilibili.linkHandler;

import org.schabi.newpipe.extractor.exceptions.ParsingException;
import org.schabi.newpipe.extractor.linkhandler.ListLinkHandlerFactory;
import org.schabi.newpipe.extractor.search.filter.FilterItem;
import org.schabi.newpipe.extractor.services.bilibili.WatchDataCache;
import org.schabi.newpipe.extractor.services.bilibili.utils;

import java.util.List;

public class BilibiliCommentsLinkHandlerFactory extends ListLinkHandlerFactory {
    private WatchDataCache watchDataCache;
    public BilibiliCommentsLinkHandlerFactory(WatchDataCache watchDataCache) {
        super();
        this.watchDataCache = watchDataCache;
    }

    @Override
    public String getId(String url) throws ParsingException {
        if(url.contains("live.bilibili.com")){
            return "LIVE";
        }
        if(url.contains("bangumi/play/")){
            return watchDataCache.getBvid();
        }
        try {
            return utils.getPureBV(new BilibiliStreamLinkHandlerFactory().getId(url));
        } catch (ParsingException e) {
            e.printStackTrace();
        }
        if(!url.contains("https://api.bilibili.com/x/v2/reply") && url.contains("oid=")){
            throw new ParsingException("not a bilibili comment link");
        }
        if(url.contains("api.bilibili.com/x/v2/reply/reply")){
            return url.split("oid=")[1];
        }
        return url.split("oid=")[1].split("&")[0];
    }

    @Override
    public boolean onAcceptUrl(String url) throws ParsingException {
        try {
            getId(url);
            return true;
        } catch (final ParsingException e) {
            return false;
        }
    }

    @Override
    public String getUrl(String id, final List<FilterItem> contentFilter,
                         final List<FilterItem> sortFilter) throws ParsingException {
        id = id.startsWith("BV")? String.valueOf(utils.bv2av(id)) :id;
        if(id.contains("&root")){
            // I don't know why but pn must be placed in the end or nothing will be fetched
            return "https://api.bilibili.com/x/v2/reply/reply?type=1&ps=20&oid=" + id + "&pn=1";
        }
        return "https://api.bilibili.com/x/v2/reply?type=1&sort=1&oid="+ id + "&pn=1";
    }
}
