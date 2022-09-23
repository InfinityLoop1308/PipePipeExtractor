package org.schabi.newpipe.extractor.services.bilibili.linkHandler;

import org.schabi.newpipe.extractor.exceptions.ParsingException;
import org.schabi.newpipe.extractor.linkhandler.ListLinkHandlerFactory;
import org.schabi.newpipe.extractor.search.filter.FilterItem;
import org.schabi.newpipe.extractor.services.bilibili.WatchDataCache;
import org.schabi.newpipe.extractor.services.bilibili.linkHandler.BilibiliStreamLinkHandlerFactory;
import org.schabi.newpipe.extractor.services.bilibili.utils;

import java.util.List;

public class BilibiliBulletCommentsLinkHandlerFactory extends ListLinkHandlerFactory {
    private int cid;
    public BilibiliBulletCommentsLinkHandlerFactory(WatchDataCache watchDataCache) {
        this.cid = watchDataCache.getCid();
    }

    @Override
    public String getId(String url) throws ParsingException {
        if(url.contains("live.bilibili.com")){
            throw new ParsingException("not a bilibili comment link");
        }
        return utils.getPureBV(new BilibiliStreamLinkHandlerFactory().getId(url));
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
        return String.format("https://api.bilibili.com/x/v2/dm/web/seg.so?oid=%s&type=1&segment_index=1", cid);
    }
}
