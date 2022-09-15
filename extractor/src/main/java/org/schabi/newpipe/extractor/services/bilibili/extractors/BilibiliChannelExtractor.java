package org.schabi.newpipe.extractor.services.bilibili.extractors;

import static org.schabi.newpipe.extractor.services.bilibili.BilibiliService.getHeaders;

import com.grack.nanojson.JsonArray;
import com.grack.nanojson.JsonObject;
import com.grack.nanojson.JsonParser;
import com.grack.nanojson.JsonParserException;

import org.schabi.newpipe.extractor.Page;
import org.schabi.newpipe.extractor.StreamingService;
import org.schabi.newpipe.extractor.channel.ChannelExtractor;
import org.schabi.newpipe.extractor.comments.CommentsInfoItemsCollector;
import org.schabi.newpipe.extractor.downloader.Downloader;
import org.schabi.newpipe.extractor.exceptions.ExtractionException;
import org.schabi.newpipe.extractor.exceptions.ParsingException;
import org.schabi.newpipe.extractor.exceptions.ReCaptchaException;
import org.schabi.newpipe.extractor.linkhandler.ListLinkHandler;
import org.schabi.newpipe.extractor.services.bilibili.linkHandler.BilibiliSearchQueryHandlerFactory;
import org.schabi.newpipe.extractor.services.bilibili.search.filter.BilibiliFilters;
import org.schabi.newpipe.extractor.services.bilibili.utils;
import org.schabi.newpipe.extractor.stream.StreamInfoItem;
import org.schabi.newpipe.extractor.stream.StreamInfoItemsCollector;

import java.io.IOException;
import java.util.Collections;

import javax.annotation.Nonnull;

public class BilibiliChannelExtractor extends ChannelExtractor {
    JsonObject json = new JsonObject();
    JsonObject userJson = new JsonObject();
    JsonObject recordJson = new JsonObject();
    JsonObject liveJson = new JsonObject();
    boolean isRecordChannel = false;
    int recordId = -1;

    public BilibiliChannelExtractor(StreamingService service, ListLinkHandler linkHandler) {
        super(service, linkHandler);
    }

    @Override
    public void onFetchPage(@Nonnull Downloader downloader) throws IOException, ExtractionException {
        if(getUrl().contains("seriesdetail")){
            isRecordChannel = true;
            final String url = utils.getRecordApiUrl(getLinkHandler().getOriginalUrl());
            String response = downloader.get(url).responseBody();
            String userResponse = downloader.get("https://api.bilibili.com/x/web-interface/card?photo=true&mid="+utils.getMidFromRecordApiUrl(url)).responseBody();
            try {
                recordJson = JsonParser.object().from(response);
                userJson = JsonParser.object().from(userResponse);
            } catch (JsonParserException e) {
                e.printStackTrace();
            }
            return ;
        }
        final String url = utils.getChannelApiUrl(getUrl(), getLinkHandler().getId());
        String response = downloader.get(url).responseBody();
        String userResponse = downloader.get("https://api.bilibili.com/x/web-interface/card?photo=true&mid="+getId()).responseBody();
        try {
            json = JsonParser.object().from(response);
            userJson = JsonParser.object().from(userResponse);
            String liveResponse = downloader.get(new BilibiliSearchQueryHandlerFactory().getUrl(getName(),
                    Collections.singletonList(new BilibiliFilters.BilibiliContentFilterItem("live_room", "search_type=live_room")), null)).responseBody();
            liveJson = JsonParser.object().from(liveResponse);
            String recordResponse = downloader.get("https://api.bilibili.com/x/polymer/space/seasons_series_list?mid=" +getId() +"&page_num=1&page_size=10").responseBody();
            JsonArray series_list = JsonParser.object().from(recordResponse).getObject("data").getObject("items_lists").getArray("series_list");
            if(series_list.size() != 0){
                recordId = series_list.getObject(0).getObject("meta").getInt("series_id");
            }
        } catch (JsonParserException e) {
            e.printStackTrace();
        }
    }

    @Nonnull
    @Override
    public String getName() throws ParsingException {
        if(isRecordChannel){
            return "直播回放";
        }
        return userJson.getObject("data").getObject("card").getString("name");
    }

    @Nonnull
    @Override
    public InfoItemsPage<StreamInfoItem> getInitialPage() throws IOException, ExtractionException {
        JsonArray results;
        if(isRecordChannel){
            results = recordJson.getObject("data").getArray("archives");
        }
        else{
            results = json.getObject("data").getObject("list").getArray("vlist");
        }

        final StreamInfoItemsCollector collector = new StreamInfoItemsCollector(getServiceId());
        if(results.size() == 0){
            return new InfoItemsPage<>(collector, null);
        }
        if(!isRecordChannel && liveJson.getObject("data").getArray("result").size() > 0){
            collector.commit(new BilibiliLiveInfoItemExtractor(liveJson.getObject("data").getArray("result").getObject(0)));
        }
        for (int i = 0; i< results.size(); i++){
            collector.commit(new BilibiliChannelInfoItemExtractor(results.getObject(i), getName(),getAvatarUrl()));
        }
        int currentPage = 1;
        String currentUrl;
        if(isRecordChannel){
            currentUrl = getLinkHandler().getOriginalUrl();
            if(!currentUrl.contains("pn=")){
                currentUrl += "&pn=1";
            }
        }
        else{
            currentUrl = getUrl();
            if(!currentUrl.contains("pn=")){
                currentUrl += "?pn=1";
            }
        }
        String nextPage = currentUrl.replace(String.format("pn=%s", 1), String.format("pn=%s", String.valueOf(currentPage + 1)));
        return new InfoItemsPage<>(collector, new Page(nextPage));
    }

    @Override
    public InfoItemsPage<StreamInfoItem> getPage(Page page) throws IOException, ExtractionException {
        String response = getDownloader().get(utils.getChannelApiUrl(page.getUrl(), getId()), getHeaders()).responseBody();
        String userResponse = getDownloader().get("https://api.bilibili.com/x/web-interface/card?photo=true&mid="+getId(), getHeaders()).responseBody();
        isRecordChannel = getUrl().contains("seriesdetail");
        try {
            if(isRecordChannel){
                final String url = utils.getRecordApiUrl(page.getUrl());
                recordJson = JsonParser.object().from(getDownloader().get(url, getHeaders()).responseBody());
                userResponse = getDownloader().get("https://api.bilibili.com/x/web-interface/card?photo=true&mid="+utils.getMidFromRecordApiUrl(url), getHeaders()).responseBody();
            }
            else{
                json = JsonParser.object().from(response);
            }
            userJson = JsonParser.object().from(userResponse);

        } catch (JsonParserException e) {
            e.printStackTrace();
        }
        JsonArray results;
        if(isRecordChannel){
            results = recordJson.getObject("data").getArray("archives");
        }
        else{
            results = json.getObject("data").getObject("list").getArray("vlist");
        }

        if(results.size() == 0){
            return new InfoItemsPage<>(new StreamInfoItemsCollector(getServiceId()), null);
        }
        final StreamInfoItemsCollector collector = new StreamInfoItemsCollector(getServiceId());
        for (int i = 0; i< results.size(); i++){
            collector.commit(new BilibiliChannelInfoItemExtractor(results.getObject(i), getName(), getAvatarUrl()));
        }
        String currentPageString = page.getUrl().split("pn=")[1].split("&")[0];
        int currentPage = Integer.parseInt(currentPageString);
        String nextPage = page.getUrl().replace(String.format("pn=%s", currentPage), String.format("pn=%s", String.valueOf(currentPage + 1)));
        return new InfoItemsPage<>(collector, new Page(nextPage));
    }

    @Override
    public String getAvatarUrl() throws ParsingException {
        return userJson.getObject("data").getObject("card").getString("face").replace("http:", "https:");
    }

    @Override
    public String getBannerUrl() throws ParsingException {
        return userJson.getObject("data").getObject("space").getString("l_img").replace("http:", "https:");
    }

    @Override
    public String getFeedUrl() throws ParsingException {
        return "";
    }

    @Override
    public long getSubscriberCount() throws ParsingException {
        return userJson.getObject("data").getObject("card").getLong("fans");
    }

    @Override
    public String getDescription() throws ParsingException {
        return userJson.getObject("data").getObject("card").getString("sign");
    }

    @Override
    public String getParentChannelName() throws ParsingException {
        if(recordId == -1){
            return "";
        }
        return "点此查看直播回放";
    }

    @Override
    public String getParentChannelUrl() throws ParsingException {
        if(recordId == -1){
            return "";
        }
        return String.format("https://space.bilibili.com/%s/channel/seriesdetail?sid=%d", getId(), recordId);
    }

    @Override
    public String getParentChannelAvatarUrl() throws ParsingException {
        if(recordId == -1){
            return "";
        }
        else return getAvatarUrl();
    }

    @Override
    public boolean isVerified() throws ParsingException {
        return false;
    }

    @Nonnull
    @Override
    public String getUrl() throws ParsingException {
        return super.getUrl();
    }
}
