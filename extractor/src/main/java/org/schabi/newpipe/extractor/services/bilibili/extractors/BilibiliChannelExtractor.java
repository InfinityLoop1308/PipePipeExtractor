package org.schabi.newpipe.extractor.services.bilibili.extractors;

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
import org.schabi.newpipe.extractor.stream.StreamInfoItem;
import org.schabi.newpipe.extractor.stream.StreamInfoItemsCollector;

import java.io.IOException;

import javax.annotation.Nonnull;

public class BilibiliChannelExtractor extends ChannelExtractor {
    JsonObject json = new JsonObject();
    JsonObject userJson = new JsonObject();

    public BilibiliChannelExtractor(StreamingService service, ListLinkHandler linkHandler) {
        super(service, linkHandler);
    }

    @Override
    public void onFetchPage(@Nonnull Downloader downloader) throws IOException, ExtractionException {
        final String url = getLinkHandler().getUrl();
        String response = downloader.get(url).responseBody();
        String userResponse = downloader.get("https://api.bilibili.com/x/web-interface/card?photo=true&mid="+getId()).responseBody();
        try {
            json = JsonParser.object().from(response);
            userJson = JsonParser.object().from(userResponse);
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
        JsonArray results = json.getObject("data").getObject("list").getArray("vlist");
        final StreamInfoItemsCollector collector = new StreamInfoItemsCollector(getServiceId());
        if(results.size() == 0){
            return new InfoItemsPage<>(collector, null);
        }
        for (int i = 0; i< results.size(); i++){
            collector.commit(new BilibiliChannelInfoItemExtractor(results.getObject(i)));
        }
        int currentPage = 1;
        String nextPage = getUrl().replace(String.format("pn=%s", 1), String.format("pn=%s", String.valueOf(currentPage + 1)));
        return new InfoItemsPage<>(collector, new Page(nextPage));
    }

    @Override
    public InfoItemsPage<StreamInfoItem> getPage(Page page) throws IOException, ExtractionException {
        String response = getDownloader().get(page.getUrl()).responseBody();
        String userResponse = getDownloader().get("https://api.bilibili.com/x/web-interface/card?photo=true&mid="+getId()).responseBody();
        try {
            json = JsonParser.object().from(response);
            userJson = JsonParser.object().from(userResponse);
        } catch (JsonParserException e) {
            e.printStackTrace();
        }
        JsonArray results = json.getObject("data").getObject("list").getArray("vlist");
        if(results.size() == 0){
            return new InfoItemsPage<>(new StreamInfoItemsCollector(getServiceId()), null);
        }
        final StreamInfoItemsCollector collector = new StreamInfoItemsCollector(getServiceId());
        for (int i = 0; i< results.size(); i++){
            collector.commit(new BilibiliChannelInfoItemExtractor(results.getObject(i)));
        }
        String currentPageString = page.getUrl().split("pn=")[1].split("&")[0];
        int currentPage = Integer.parseInt(currentPageString);
        String nextPage = getUrl().replace(String.format("pn=%s", 1), String.format("pn=%s", String.valueOf(currentPage + 1)));
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
        return null;
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
        return null;
    }

    @Override
    public String getParentChannelUrl() throws ParsingException {
        return null;
    }

    @Override
    public String getParentChannelAvatarUrl() throws ParsingException {
        return null;
    }

    @Override
    public boolean isVerified() throws ParsingException {
        return false;
    }
}
