package org.schabi.newpipe.extractor.services.bilibili.extractors;

import com.grack.nanojson.JsonArray;
import com.grack.nanojson.JsonObject;
import com.grack.nanojson.JsonParser;
import com.grack.nanojson.JsonParserException;
import org.schabi.newpipe.extractor.Page;
import org.schabi.newpipe.extractor.StreamingService;
import org.schabi.newpipe.extractor.channel.ChannelExtractor;
import org.schabi.newpipe.extractor.downloader.Downloader;
import org.schabi.newpipe.extractor.exceptions.ExtractionException;
import org.schabi.newpipe.extractor.exceptions.ParsingException;
import org.schabi.newpipe.extractor.linkhandler.ChannelTabs;
import org.schabi.newpipe.extractor.linkhandler.ListLinkHandler;
import org.schabi.newpipe.extractor.search.filter.Filter;
import org.schabi.newpipe.extractor.search.filter.FilterItem;
import org.schabi.newpipe.extractor.services.bilibili.utils;
import org.schabi.newpipe.extractor.stream.StreamInfoItem;
import org.schabi.newpipe.extractor.stream.StreamInfoItemsCollector;

import javax.annotation.Nonnull;
import java.io.IOException;
import java.util.Collections;
import java.util.List;

import static org.schabi.newpipe.extractor.services.bilibili.BilibiliService.*;
import static org.schabi.newpipe.extractor.services.bilibili.utils.getNextPageFromCurrentUrl;

public class BilibiliChannelExtractor extends ChannelExtractor {
    JsonObject videoData = new JsonObject();
    JsonObject userData = new JsonObject();
    JsonObject liveData = new JsonObject();

    public BilibiliChannelExtractor(StreamingService service, ListLinkHandler linkHandler) {
        super(service, linkHandler);
    }

    @Override
    public void onFetchPage(@Nonnull Downloader downloader) throws IOException, ExtractionException {
        String videoResponse = utils.getUserVideos(getUrl(), getLinkHandler().getId(), downloader);
        try {
            String userResponse = downloader.get(QUERY_USER_INFO_URL + getId(), getUpToDateHeaders()).responseBody();
            videoData = JsonParser.object().from(videoResponse);
            userData = JsonParser.object().from(userResponse);
            String liveResponse = downloader.get(QUERY_LIVEROOM_STATUS_URL + getId()).responseBody();
            liveData = JsonParser.object().from(liveResponse);
        } catch (JsonParserException e) {
            e.printStackTrace();
        }
    }

    @Nonnull
    @Override
    public String getName() throws ParsingException {
        return userData.getObject("data").getObject("card").getString("name");
    }

    @Nonnull
    @Override
    public InfoItemsPage<StreamInfoItem> getInitialPage() throws IOException, ExtractionException {
        JsonArray results;
        results = videoData.getObject("data").getObject("list").getArray("vlist");

        final StreamInfoItemsCollector collector = new StreamInfoItemsCollector(getServiceId());
        if (results.size() == 0) {
            return new InfoItemsPage<>(collector, null);
        }
        if (liveData.getObject("data").getObject(getId()).getInt("live_status") != 0) {
            collector.commit(new BilibiliLiveInfoItemExtractor(liveData.getObject("data").getObject(getId()), 1));
        }
        for (int i = 0; i < results.size(); i++) {
            collector.commit(new BilibiliChannelInfoItemExtractor(results.getObject(i), getName(), getAvatarUrl()));
        }
        return new InfoItemsPage<>(collector, new Page(getNextPageFromCurrentUrl
                (getUrl(), "pn", 1, true, "1", "?")));
    }

    @Override
    public InfoItemsPage<StreamInfoItem> getPage(Page page) throws IOException, ExtractionException {
        String videoResponse = utils.getUserVideos(page.getUrl(), getId(), getDownloader());
        try {
            String userResponse = getDownloader().get(QUERY_USER_INFO_URL + getId(), getUpToDateHeaders()).responseBody();
            videoData = JsonParser.object().from(videoResponse);
            userData = JsonParser.object().from(userResponse);

        } catch (JsonParserException e) {
            e.printStackTrace();
        }
        JsonArray results;
        results = videoData.getObject("data").getObject("list").getArray("vlist");

        if (results.size() == 0) {
            return new InfoItemsPage<>(new StreamInfoItemsCollector(getServiceId()), null);
        }
        final StreamInfoItemsCollector collector = new StreamInfoItemsCollector(getServiceId());
        for (int i = 0; i < results.size(); i++) {
            collector.commit(new BilibiliChannelInfoItemExtractor(results.getObject(i), getName(), getAvatarUrl()));
        }
        return new InfoItemsPage<>(collector, new Page(getNextPageFromCurrentUrl(page.getUrl(), "pn", 1)));
    }

    @Override
    public String getAvatarUrl() throws ParsingException {
        return userData.getObject("data").getObject("card").getString("face").replace("http:", "https:");
    }

    @Override
    public String getBannerUrl() throws ParsingException {
        return userData.getObject("data").getObject("space").getString("l_img").replace("http:", "https:");
    }

    @Override
    public long getSubscriberCount() throws ParsingException {
        return userData.getObject("data").getObject("card").getLong("fans");
    }

    @Override
    public String getDescription() throws ParsingException {
        return userData.getObject("data").getObject("card").getString("sign");
    }

    @Nonnull
    @Override
    public String getUrl() throws ParsingException {
        return super.getUrl();
    }

    @Nonnull
    @Override
    public List<ListLinkHandler> getTabs() throws ParsingException {
        String url = "https://api.bilibili.com/x/polymer/space/seasons_series_list?mid=" + getLinkHandler().getId() + "&page_num=1&page_size=10";
        return Collections.singletonList(
                new ListLinkHandler(url, url, getLinkHandler().getId(),
                        Collections.singletonList(new FilterItem(Filter.ITEM_IDENTIFIER_UNKNOWN, ChannelTabs.PLAYLISTS)), null)
        );
    }
}
