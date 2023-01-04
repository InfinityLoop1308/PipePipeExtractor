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
import org.schabi.newpipe.extractor.linkhandler.ChannelTabs;
import org.schabi.newpipe.extractor.linkhandler.ListLinkHandler;
import org.schabi.newpipe.extractor.search.filter.Filter;
import org.schabi.newpipe.extractor.search.filter.FilterItem;
import org.schabi.newpipe.extractor.services.bilibili.linkHandler.BilibiliSearchQueryHandlerFactory;
import org.schabi.newpipe.extractor.services.bilibili.search.filter.BilibiliFilters;
import org.schabi.newpipe.extractor.services.bilibili.utils;
import org.schabi.newpipe.extractor.stream.StreamInfoItem;
import org.schabi.newpipe.extractor.stream.StreamInfoItemsCollector;

import java.io.IOException;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import javax.annotation.Nonnull;

public class BilibiliChannelExtractor extends ChannelExtractor {
    JsonObject json = new JsonObject();
    JsonObject userJson = new JsonObject();
    JsonObject liveJson = new JsonObject();

    public BilibiliChannelExtractor(StreamingService service, ListLinkHandler linkHandler) {
        super(service, linkHandler);
    }

    @Override
    public void onFetchPage(@Nonnull Downloader downloader) throws IOException, ExtractionException {
        final String url = utils.getChannelApiUrl(getUrl(), getLinkHandler().getId());
        String response = downloader.get(url, getHeaders()).responseBody();
        String userResponse = downloader.get("https://api.bilibili.com/x/web-interface/card?photo=true&mid="+getId(), getHeaders()).responseBody();
        try {
            json = JsonParser.object().from(response);
            userJson = JsonParser.object().from(userResponse);
            String liveResponse = downloader.get("https://api.live.bilibili.com/room/v1/Room/get_status_info_by_uids?uids[]=" + getId()).responseBody();
            liveJson = JsonParser.object().from(liveResponse);
            if(json.getInt("code") != 0 || userJson.getInt("code") != 0 || liveJson.getInt("code") != 0){
                throw new ExtractionException("Error occurs during fetching channel content. That normally happen because your IP got temporarily banned.");
            }
        } catch (JsonParserException e) {
            e.printStackTrace();
        }
    }

    @Nonnull
    @Override
    public String getName() throws ParsingException {
        return userJson.getObject("data").getObject("card").getString("name");
    }

    @Nonnull
    @Override
    public InfoItemsPage<StreamInfoItem> getInitialPage() throws IOException, ExtractionException {
        JsonArray results;
        results = json.getObject("data").getObject("list").getArray("vlist");

        final StreamInfoItemsCollector collector = new StreamInfoItemsCollector(getServiceId());
        if(results.size() == 0){
            return new InfoItemsPage<>(collector, null);
        }
        if(liveJson.getObject("data").getObject(getId()).getInt("live_status") != 0){
            collector.commit(new BilibiliLiveInfoItemExtractor(liveJson.getObject("data").getObject(getId()), 1));
        }
        for (int i = 0; i< results.size(); i++){
            collector.commit(new BilibiliChannelInfoItemExtractor(results.getObject(i), getName(),getAvatarUrl()));
        }
        int currentPage = 1;
        String currentUrl;
        currentUrl = getUrl();
        if(!currentUrl.contains("pn=")){
            currentUrl += "?pn=1";
        }
        String nextPage = currentUrl.replace(String.format("pn=%s", 1), String.format("pn=%s", String.valueOf(currentPage + 1)));
        return new InfoItemsPage<>(collector, new Page(nextPage));
    }

    @Override
    public InfoItemsPage<StreamInfoItem> getPage(Page page) throws IOException, ExtractionException {
        String response = getDownloader().get(utils.getChannelApiUrl(page.getUrl(), getId()), getHeaders()).responseBody();
        String userResponse = getDownloader().get("https://api.bilibili.com/x/web-interface/card?photo=true&mid="+getId(), getHeaders()).responseBody();
        try {
            json = JsonParser.object().from(response);
            userJson = JsonParser.object().from(userResponse);

        } catch (JsonParserException e) {
            e.printStackTrace();
        }
        JsonArray results;
        results = json.getObject("data").getObject("list").getArray("vlist");

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
        return "";
    }

    @Override
    public String getParentChannelUrl() throws ParsingException {
        return "";
    }

    @Override
    public String getParentChannelAvatarUrl() throws ParsingException {
        return "";
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
    @Nonnull
    @Override
    public List<ListLinkHandler> getTabs() throws ParsingException {
        String url = "https://api.bilibili.com/x/polymer/space/seasons_series_list?mid=" +getLinkHandler().getId() +"&page_num=1&page_size=10";
        return Collections.singletonList(
                new ListLinkHandler(url, url, getLinkHandler().getId(),
                        Collections.singletonList(new FilterItem(Filter.ITEM_IDENTIFIER_UNKNOWN, ChannelTabs.PLAYLISTS)), null)
        );
    }
}
