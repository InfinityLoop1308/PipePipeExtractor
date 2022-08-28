package org.schabi.newpipe.extractor.services.bilibili.extractors;

import static org.schabi.newpipe.extractor.services.bilibili.BilibiliService.getHeaders;

import java.io.IOException;

import com.grack.nanojson.JsonArray;
import com.grack.nanojson.JsonObject;
import com.grack.nanojson.JsonParser;
import com.grack.nanojson.JsonParserException;

import org.schabi.newpipe.extractor.MultiInfoItemsCollector;
import org.schabi.newpipe.extractor.Page;
import org.schabi.newpipe.extractor.StreamingService;
import org.schabi.newpipe.extractor.downloader.Downloader;
import org.schabi.newpipe.extractor.exceptions.ExtractionException;
import org.schabi.newpipe.extractor.exceptions.ParsingException;
import org.schabi.newpipe.extractor.kiosk.KioskExtractor;
import org.schabi.newpipe.extractor.linkhandler.ListLinkHandler;
import org.schabi.newpipe.extractor.stream.StreamInfoItem;
import org.schabi.newpipe.extractor.stream.StreamInfoItemsCollector;

public class BilibiliFeedExtractor extends KioskExtractor<StreamInfoItem>{
    public BilibiliFeedExtractor(StreamingService streamingService, ListLinkHandler linkHandler, String kioskId) {
        super(streamingService, linkHandler, kioskId);
        //TODO Auto-generated constructor stub
    }

    private JsonObject response = new JsonObject();
    @Override
    public String getName() throws ParsingException {
        // TODO Auto-generated method stub
        return null;
    }

    @Override
    public InfoItemsPage<StreamInfoItem> getInitialPage() throws IOException, ExtractionException {
        JsonArray results = response.getObject("data").getArray("item");

        final StreamInfoItemsCollector collector = new StreamInfoItemsCollector(getServiceId());
        for (int i = 0; i< results.size(); i++){
            collector.commit(new BilibiliFeedInfoItemExtractor(results.getObject(i)));
        }
        return new InfoItemsPage<>(collector, null);
    }

    @Override
    public InfoItemsPage<StreamInfoItem> getPage(Page page) throws IOException, ExtractionException {
        // TODO Auto-generated method stub
        return null;
    }

    @Override
    public void onFetchPage(Downloader downloader) throws IOException, ExtractionException {
        // TODO Auto-generated method stub

        try {
            response = JsonParser.object().from(getDownloader().get("https://api.bilibili.com/x/web-interface/index/top/rcmd?fresh_type=3", getHeaders()).responseBody());
        } catch (JsonParserException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        }

    }
    
}