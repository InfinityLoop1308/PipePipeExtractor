package org.schabi.newpipe.extractor.services.bilibili.extractors;

import static org.schabi.newpipe.extractor.services.bilibili.BilibiliService.getHeaders;

import java.io.IOException;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

import com.grack.nanojson.JsonArray;
import com.grack.nanojson.JsonObject;
import com.grack.nanojson.JsonParser;
import com.grack.nanojson.JsonParserException;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
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

import javax.annotation.Nonnull;

public class BilibiliFeedExtractor extends KioskExtractor<StreamInfoItem>{
    public BilibiliFeedExtractor(StreamingService streamingService, ListLinkHandler linkHandler, String kioskId) {
        super(streamingService, linkHandler, kioskId);
    }

    private JsonObject response = new JsonObject();
    private Document document;
    @Nonnull
    @Override
    public String getName() throws ParsingException {
        return getId();
    }

    @Nonnull
    @Override
    public InfoItemsPage<StreamInfoItem> getInitialPage() throws IOException, ExtractionException {
        final StreamInfoItemsCollector collector = new StreamInfoItemsCollector(getServiceId());
        if(getId().equals("Trending")){
            JsonArray results = response.getObject("data").getArray("item");
            for (int i = 0; i< results.size(); i++){
                collector.commit(new BilibiliFeedInfoItemExtractor(results.getObject(i)));
            }
        }else if(getId().equals("Recommend Lives")){
            Elements elements = document.select("div.index_1Jokt5rg");
            for(final Element parent:elements){
                Elements lives = parent.children();
                for(final Element live: lives){
                    String views = live.select(".Item_3Iz_3buh").text();
                    int flag = 1;
                    if(views.contains("万")){
                        views = views.replace("万","");
                        flag = 10000;
                    } else if (views.contains("亿")) {
                        views = views.replace("亿","");
                        flag = 100000000;
                    }
                    collector.commit(new BilibiliRecommendLiveInfoItemExtractor(
                            "https:"+live.select("a").first().attr("href"),
                            live.select(".Item_2n7ef9LN.bg-bright-filter")
                                    .attr("style")
                                    .split("background-image:url\\(")[1].split("\\)")[0]
                                    .replace("http:","https:"),
                            live.select(".Item_2GEmdhg6").text(),
                            live.select(".Item_QAOnosoB").text(),
                            (long) (Double.parseDouble(views) * flag)
                    ));
                }
            }
        }

        return new InfoItemsPage<>(collector, null);
    }

    @Override
    public InfoItemsPage<StreamInfoItem> getPage(Page page) throws IOException, ExtractionException {
        return null;
    }

    @Override
    public void onFetchPage(Downloader downloader) throws IOException, ExtractionException {
        if(!getId().equals("Trending")){
            document = Jsoup.parse(downloader.get(getUrl(), getHeaders()).responseBody());
            return ;
        }
        try {
            response = JsonParser.object().from(getDownloader().get("https://api.bilibili.com/x/web-interface/index/top/rcmd?fresh_type=3", getHeaders()).responseBody());
        } catch (JsonParserException e) {
            e.printStackTrace();
        }
    }

}