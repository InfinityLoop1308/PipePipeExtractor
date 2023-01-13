package org.schabi.newpipe.extractor.services.bilibili.extractors;

import static org.schabi.newpipe.extractor.services.bilibili.BilibiliService.getHeaders;
import static org.schabi.newpipe.extractor.services.bilibili.utils.getNextPageFromCurrentUrl;

import com.grack.nanojson.JsonArray;
import com.grack.nanojson.JsonObject;
import com.grack.nanojson.JsonParser;
import com.grack.nanojson.JsonParserException;

import org.schabi.newpipe.extractor.InfoItem;
import org.schabi.newpipe.extractor.MultiInfoItemsCollector;
import org.schabi.newpipe.extractor.Page;
import org.schabi.newpipe.extractor.StreamingService;
import org.schabi.newpipe.extractor.channel.ChannelTabExtractor;
import org.schabi.newpipe.extractor.downloader.Downloader;
import org.schabi.newpipe.extractor.exceptions.ExtractionException;
import org.schabi.newpipe.extractor.linkhandler.ListLinkHandler;

import java.io.IOException;

import javax.annotation.Nonnull;

public class BilibiliChannelTabExtractor extends ChannelTabExtractor {
    public BilibiliChannelTabExtractor(StreamingService service, ListLinkHandler linkHandler) {
        super(service, linkHandler);
    }

    @Override
    public void onFetchPage(@Nonnull Downloader downloader) throws IOException, ExtractionException {

    }

    @Nonnull
    @Override
    public InfoItemsPage<InfoItem> getInitialPage() throws IOException, ExtractionException {
        return getPage(new Page(getLinkHandler().getUrl()));
    }

    @Override
    public InfoItemsPage<InfoItem> getPage(Page page) throws IOException, ExtractionException {
        final MultiInfoItemsCollector collector = new MultiInfoItemsCollector(getServiceId());
        String response = getDownloader().get(page.getUrl(), getHeaders()).responseBody();
        try {
            JsonObject data = JsonParser.object().from(response).getObject("data");
            JsonArray seasons_list = data.getObject("items_lists").getArray("seasons_list");
            JsonArray series_list = data.getObject("items_lists").getArray("series_list");
            if(seasons_list.size() + series_list.size() == 0){
                return new InfoItemsPage<>(collector, null);
            }
            for(int i=0; i< seasons_list.size();i++){
                collector.commit(new BilibiliPlaylistInfoItemExtractor(seasons_list.getObject(i), "seasons_archives"));
            }
            for(int i=0; i< series_list.size();i++){
                collector.commit(new BilibiliPlaylistInfoItemExtractor(series_list.getObject(i), "archives"));
            }
        } catch (JsonParserException e) {
            throw new RuntimeException(e);
        }
        return new InfoItemsPage<>(collector, new Page(getNextPageFromCurrentUrl(page.getUrl(), "page_num", 1)));
    }
}
