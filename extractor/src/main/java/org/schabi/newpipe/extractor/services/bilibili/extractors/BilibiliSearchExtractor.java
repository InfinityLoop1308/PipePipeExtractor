package org.schabi.newpipe.extractor.services.bilibili.extractors;

import java.io.IOException;
import java.util.Collections;
import java.util.List;

import com.grack.nanojson.JsonArray;
import com.grack.nanojson.JsonObject;
import com.grack.nanojson.JsonParser;
import com.grack.nanojson.JsonParserException;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import org.schabi.newpipe.extractor.InfoItem;
import org.schabi.newpipe.extractor.MetaInfo;
import org.schabi.newpipe.extractor.MultiInfoItemsCollector;
import org.schabi.newpipe.extractor.Page;
import org.schabi.newpipe.extractor.StreamingService;
import org.schabi.newpipe.extractor.channel.ChannelInfoItemsCollector;
import org.schabi.newpipe.extractor.downloader.Downloader;
import org.schabi.newpipe.extractor.exceptions.ExtractionException;
import org.schabi.newpipe.extractor.exceptions.ParsingException;
import org.schabi.newpipe.extractor.linkhandler.SearchQueryHandler;
import org.schabi.newpipe.extractor.search.SearchExtractor;

public class BilibiliSearchExtractor extends SearchExtractor{

    private JsonObject searchCollection;

    public BilibiliSearchExtractor(StreamingService service, SearchQueryHandler linkHandler) {
        super(service, linkHandler);
    }

    @Override
    public String getSearchSuggestion() throws ParsingException {
        // TODO Auto-generated method stub
        return "";
    }

    @Override
    public boolean isCorrectedSearch() throws ParsingException {
        // TODO Auto-generated method stub
        return false;
    }

    @Override
    public List<MetaInfo> getMetaInfo() throws ParsingException {
        // TODO Auto-generated method stub
        return Collections.emptyList();
    }

    @Override
    public InfoItemsPage<InfoItem> getInitialPage() throws IOException, ExtractionException {
        JsonArray result_videos = searchCollection.getObject("data").getArray("result").getObject(10).getArray("data");
        JsonArray result_users = searchCollection.getObject("data").getArray("result").getObject(2).getArray("data");
        JsonArray result = searchCollection.getObject("data").getArray("result");
        if(result_videos.size() + result_users.size() == 0){
            return new InfoItemsPage<>(new MultiInfoItemsCollector(getServiceId()), null);
        }
        for(int i=0;i<result.size();i++){
            if(result.getObject(i).getString("result_type").equals("bili_user")){
                result_users = searchCollection.getObject("data").getArray("result").getObject(i).getArray("data");
            }
        }

        final MultiInfoItemsCollector collector = new MultiInfoItemsCollector(getServiceId());
        for (int i = 0; i< result_users.size(); i++){
            collector.commit(new BilibiliSearchResultChannelInfoItemExtractor(result_users.getObject(i)));
        }
        for (int i = 0; i< result_videos.size(); i++){
            collector.commit(new BilibiliStreamInfoItemExtractor(result_videos.getObject(i)));
        }
        int currentPage = 1;
        String nextPage = getUrl().replace(String.format("page=%s", 1), String.format("page=%s", String.valueOf(currentPage + 1)));
        return new InfoItemsPage<>(collector, new Page(nextPage));
    }

    @Override
    public InfoItemsPage<InfoItem> getPage(Page page) throws IOException, ExtractionException {
        final String html = getDownloader().get(page.getUrl()).responseBody();

        

        try {
            searchCollection = JsonParser.object().from(html);
        } catch (JsonParserException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        }

        JsonArray result_users = searchCollection.getObject("data").getArray("result").getObject(2).getArray("data");
        JsonArray result_videos = searchCollection.getObject("data").getArray("result").getObject(10).getArray("data");
        if(result_videos.size() == 0){
            return new InfoItemsPage<>(new MultiInfoItemsCollector(getServiceId()), null);
        }
        final MultiInfoItemsCollector collector = new MultiInfoItemsCollector(getServiceId());
//        for (int i = 0; i< result_users.size(); i++){
//            collector.commit(new BilibiliSearchResultChannelInfoItemExtractor(result_users.getObject(i)));
//        }
        for (int i = 0; i< result_videos.size(); i++){
            collector.commit(new BilibiliStreamInfoItemExtractor(result_videos.getObject(i)));
        }


        String currentPageString = page.getUrl().split("page=")[page.getUrl().split("page=").length-1];
        int currentPage = Integer.parseInt(currentPageString);
        String nextPage = page.getUrl().replace(String.format("page=%s", currentPageString), String.format("page=%s", String.valueOf(currentPage + 1)));
        return new InfoItemsPage<>(collector, new Page(nextPage));
    }

    @Override
    public void onFetchPage(Downloader downloader) throws IOException, ExtractionException {
        // TODO Auto-generated method stub
        final String response = getDownloader().get(
            getLinkHandler().getUrl()).responseBody();
        try {
            searchCollection = JsonParser.object().from(response);
        } catch (final JsonParserException e) {
            throw new ExtractionException("could not parse search results.");
        }
    }
    
}
