package org.schabi.newpipe.extractor.services.niconico.extractors;

import com.grack.nanojson.JsonArray;
import com.grack.nanojson.JsonObject;
import com.grack.nanojson.JsonParser;
import com.grack.nanojson.JsonParserException;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import org.schabi.newpipe.extractor.Page;
import org.schabi.newpipe.extractor.StreamingService;
import org.schabi.newpipe.extractor.downloader.Downloader;
import org.schabi.newpipe.extractor.exceptions.ExtractionException;
import org.schabi.newpipe.extractor.exceptions.ParsingException;
import org.schabi.newpipe.extractor.kiosk.KioskExtractor;
import org.schabi.newpipe.extractor.linkhandler.ListLinkHandler;
import org.schabi.newpipe.extractor.services.niconico.NiconicoService;
import org.schabi.newpipe.extractor.stream.StreamInfoItem;
import org.schabi.newpipe.extractor.stream.StreamInfoItemsCollector;

import java.io.IOException;

import javax.annotation.Nonnull;

public class NiconicoTrendExtractor extends KioskExtractor<StreamInfoItem> {
    private Document rss;
    private JsonArray data;

    public NiconicoTrendExtractor(final StreamingService streamingService,
                                  final ListLinkHandler linkHandler, final String kioskId) {
        super(streamingService, linkHandler, kioskId);
    }

    @Override
    public void onFetchPage(final @Nonnull Downloader downloader)
            throws IOException, ExtractionException {
        if(!getId().equals("Trending")){
            try {
                data = JsonParser.object().from(downloader.get(getUrl()).responseBody()).getObject("data").getArray("values");
                return ;
            } catch (JsonParserException e) {
                throw new RuntimeException(e);
            }
        }
        rss = Jsoup.parse(getDownloader().get(NiconicoService.DAILY_TREND_URL).responseBody());
    }

    @Nonnull
    @Override
    public InfoItemsPage<StreamInfoItem> getInitialPage()
            throws IOException, ExtractionException {
        final StreamInfoItemsCollector collector = new StreamInfoItemsCollector(getServiceId());
        if(!getId().equals("Trending")){
            for(int i = 0; i< data.size(); i++){
                collector.commit(new NiconicoLiveRecommendVideoExtractor(data.getObject(i)));
            }
            return new InfoItemsPage<>(collector, null);
        }
        final Elements arrays = rss.getElementsByTag("item");
        final String uploaderName = rss.getElementsByTag("dc:creator").text();
        final String uploaderUrl = rss.getElementsByTag("link").text();

        for (final Element e : arrays) {
            collector.commit(new NiconicoTrendRSSExtractor(e, uploaderName, uploaderUrl, null));
        }

        return new InfoItemsPage<>(collector, null);
    }

    @Override
    public InfoItemsPage<StreamInfoItem> getPage(final Page page)
            throws IOException, ExtractionException {
        return InfoItemsPage.emptyPage();
    }

    @Nonnull
    @Override
    public String getName() throws ParsingException {
        return getId();
    }
}
