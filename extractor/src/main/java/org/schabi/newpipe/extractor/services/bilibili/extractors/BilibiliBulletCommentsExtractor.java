package org.schabi.newpipe.extractor.services.bilibili.extractors;

import com.grack.nanojson.JsonObject;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import org.schabi.newpipe.extractor.Page;
import org.schabi.newpipe.extractor.StreamingService;
import org.schabi.newpipe.extractor.bulletComments.BulletCommentsExtractor;
import org.schabi.newpipe.extractor.bulletComments.BulletCommentsInfoItem;
import org.schabi.newpipe.extractor.bulletComments.BulletCommentsInfoItemsCollector;
import org.schabi.newpipe.extractor.downloader.Downloader;
import org.schabi.newpipe.extractor.exceptions.ExtractionException;
import org.schabi.newpipe.extractor.linkhandler.ListLinkHandler;
import org.schabi.newpipe.extractor.services.bilibili.WatchDataCache;
import org.schabi.newpipe.extractor.services.bilibili.utils;

import java.io.IOException;

import javax.annotation.Nonnull;

public class BilibiliBulletCommentsExtractor extends BulletCommentsExtractor {
    private int cid;
    private Document result;

    public BilibiliBulletCommentsExtractor(StreamingService service, ListLinkHandler uiHandler, WatchDataCache watchDataCache) {
        super(service, uiHandler);
        this.cid = watchDataCache.getCid();
    }

    JsonObject json = new JsonObject();

    @Override
    public void onFetchPage(@Nonnull Downloader downloader) throws IOException, ExtractionException {
        if(getUrl().contains("live.bilibili.com")){
            return ;
        }
        result = Jsoup.parse(new String(utils.decompress(downloader.get(
                "https://api.bilibili.com/x/v1/dm/list.so?oid="+cid).rawResponseBody().bytes())));
    }

    @Nonnull
    @Override
    public InfoItemsPage<BulletCommentsInfoItem> getInitialPage() throws IOException, ExtractionException {
        final BulletCommentsInfoItemsCollector collector =
                new BulletCommentsInfoItemsCollector(getServiceId());
        if(getUrl().contains("live.bilibili.com")){
            return new InfoItemsPage<>(collector, null);
        }else{
            if(result.select("state").text().equals("1")){
                return new InfoItemsPage<>(collector, null);
            }
            else{
                Elements elements = result.select("d");
                for(final Element element:elements){
                    collector.commit(new BilibiliBulletCommentsInfoItemExtractor(element));
                }
            }
            return new InfoItemsPage<>(collector, null);
        }
    }

    @Override
    public InfoItemsPage<BulletCommentsInfoItem> getPage(Page page) throws IOException, ExtractionException {
        return null;
    }
}
