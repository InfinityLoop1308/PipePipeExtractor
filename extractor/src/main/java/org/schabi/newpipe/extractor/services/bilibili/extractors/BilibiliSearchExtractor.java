package org.schabi.newpipe.extractor.services.bilibili.extractors;

import java.io.IOException;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import com.grack.nanojson.JsonArray;
import com.grack.nanojson.JsonObject;
import com.grack.nanojson.JsonParser;
import com.grack.nanojson.JsonParserException;

import org.schabi.newpipe.extractor.InfoItem;
import org.schabi.newpipe.extractor.MetaInfo;
import org.schabi.newpipe.extractor.MultiInfoItemsCollector;
import org.schabi.newpipe.extractor.Page;
import org.schabi.newpipe.extractor.StreamingService;
import org.schabi.newpipe.extractor.downloader.Downloader;
import org.schabi.newpipe.extractor.exceptions.ExtractionException;
import org.schabi.newpipe.extractor.exceptions.ParsingException;
import org.schabi.newpipe.extractor.exceptions.ReCaptchaException;
import org.schabi.newpipe.extractor.linkhandler.SearchQueryHandler;
import org.schabi.newpipe.extractor.search.SearchExtractor;

public class BilibiliSearchExtractor extends SearchExtractor{

    private JsonObject searchCollection;

    public BilibiliSearchExtractor(StreamingService service, SearchQueryHandler linkHandler) {
        super(service, linkHandler);
    }

    @Override
    public String getSearchSuggestion() throws ParsingException {
        return "";
    }

    @Override
    public boolean isCorrectedSearch() throws ParsingException {
        return false;
    }

    @Override
    public List<MetaInfo> getMetaInfo() throws ParsingException {
        return Collections.emptyList();
    }

    @Override
    public InfoItemsPage<InfoItem> getInitialPage() throws IOException, ExtractionException {
        if(searchCollection.getObject("data").getArray("result").size() == 0){
            return new InfoItemsPage<>(new MultiInfoItemsCollector(getServiceId()), null);
        }
        int currentPage = 1;
        String nextPage = getUrl().replace(String.format("page=%s", 1), String.format("page=%s", String.valueOf(currentPage + 1)));
        return new InfoItemsPage<>(getCommittedCollector(), new Page(nextPage));
    }

    private MultiInfoItemsCollector getCommittedCollector(){
        JsonArray result = searchCollection.getObject("data").getArray("result");
        final MultiInfoItemsCollector collector = new MultiInfoItemsCollector(getServiceId());
        for (int i = 0; i< result.size(); i++) {
            String type = result.getObject(i).getString("type");
            switch (type){
                case "video":
                    collector.commit(new BilibiliStreamInfoItemExtractor(result.getObject(i)));
                    break;
                case "live_room":
                    collector.commit(new BilibiliLiveInfoItemExtractor(result.getObject(i), 0));
                    break;
                case "bili_user":
                    collector.commit(new BilibiliSearchResultChannelInfoItemExtractor(result.getObject(i)));
                    break;
                case "media_bangumi":
                case "media_ft":
                    collector.commit(new BilibiliPremiumContentInfoItemExtractor(result.getObject(i)));
            }
        }
        return collector;
    }

    @Override
    public InfoItemsPage<InfoItem> getPage(Page page) throws IOException, ExtractionException {
        final String html = getDownloader().get(page.getUrl(), getSearchHeader()).responseBody();

        try {
            searchCollection = JsonParser.object().from(html);
        } catch (JsonParserException e) {
            e.printStackTrace();
        }

        if(searchCollection.getObject("data").getArray("result").size() == 0){
            return new InfoItemsPage<>(new MultiInfoItemsCollector(getServiceId()), null);
        }

        String currentPageString = page.getUrl().split("page=")[page.getUrl().split("page=").length-1];
        int currentPage = Integer.parseInt(currentPageString);
        String nextPage = page.getUrl().replace(String.format("page=%s", currentPageString), String.format("page=%s", String.valueOf(currentPage + 1)));
        return new InfoItemsPage<>(getCommittedCollector(), new Page(nextPage));
    }

    @Override
    public void onFetchPage(Downloader downloader) throws IOException, ExtractionException {
        final String response = getDownloader().get(
            getLinkHandler().getUrl(), getSearchHeader()).responseBody();
        try {
            searchCollection = JsonParser.object().from(response);
        } catch (final JsonParserException e) {
            throw new ExtractionException("could not parse search results.");
        }
    }

    public Map<String, List<String>> getSearchHeader() throws ParsingException, IOException, ReCaptchaException {
        Map<String, List<String>> tmpHeaders = getDownloader().get(getBaseUrl()).responseHeaders();
        return Map.of("Cookie",
                Collections.singletonList(tmpHeaders.get("set-cookie").stream()
                        .filter(s -> s.contains("buvid3=")).findFirst().get()));
    }
}
