package org.schabi.newpipe.extractor.services.bilibili.extractors;

import static org.schabi.newpipe.extractor.services.bilibili.BilibiliService.QUERY_USER_INFO_URL;
import static org.schabi.newpipe.extractor.services.bilibili.BilibiliService.getHeaders;

import com.grack.nanojson.JsonArray;
import com.grack.nanojson.JsonObject;
import com.grack.nanojson.JsonParser;
import com.grack.nanojson.JsonParserException;

import org.schabi.newpipe.extractor.Page;
import org.schabi.newpipe.extractor.StreamingService;
import org.schabi.newpipe.extractor.downloader.Downloader;
import org.schabi.newpipe.extractor.exceptions.ExtractionException;
import org.schabi.newpipe.extractor.exceptions.ParsingException;
import org.schabi.newpipe.extractor.linkhandler.ListLinkHandler;
import org.schabi.newpipe.extractor.playlist.PlaylistExtractor;
import org.schabi.newpipe.extractor.services.bilibili.linkHandler.BilibiliChannelLinkHandlerFactory;
import org.schabi.newpipe.extractor.services.bilibili.utils;
import org.schabi.newpipe.extractor.stream.StreamInfoItem;
import org.schabi.newpipe.extractor.stream.StreamInfoItemsCollector;

import java.io.IOException;

import javax.annotation.Nonnull;

public class BilibiliPlaylistExtractor extends PlaylistExtractor {
    public JsonObject data;
    public String type;
    private JsonObject userData;

    public BilibiliPlaylistExtractor(StreamingService service, ListLinkHandler linkHandler) {
        super(service, linkHandler);
    }

    @Override
    public void onFetchPage(@Nonnull Downloader downloader) throws IOException, ExtractionException {
        try {
            data = JsonParser.object().from(getDownloader().get(getLinkHandler().getUrl(), getHeaders()).responseBody()).getObject("data");
            type = getLinkHandler().getUrl().contains("seasons_archives") ? "seasons_archives" : "archives";
            String userResponse = getDownloader().get(QUERY_USER_INFO_URL + utils.getMidFromRecordApiUrl(getLinkHandler().getUrl()), getHeaders()).responseBody();
            userData = JsonParser.object().from(userResponse);
        } catch (JsonParserException e) {
            throw new RuntimeException(e);
        }
    }

    @Nonnull
    @Override
    public String getName() throws ParsingException {
        return getLinkHandler().getUrl().split("name=")[1].split("&")[0];
    }

    @Nonnull
    @Override
    public InfoItemsPage<StreamInfoItem> getInitialPage() throws IOException, ExtractionException {
        return getPage(new Page(getUrl() + "&username=" + getUploaderName()));
    }

    @Override
    public InfoItemsPage<StreamInfoItem> getPage(Page page) throws IOException, ExtractionException {
        type = getLinkHandler().getUrl().contains("seasons_archives") ? "seasons_archives" : "archives";
        try {
            if (!(page.getUrl().contains("pn=1") || page.getUrl().contains("page_num=1"))) {
                data = JsonParser.object().from(getDownloader().get(page.getUrl(), getHeaders()).responseBody()).getObject("data");
            }
        } catch (JsonParserException e) {
            throw new RuntimeException(e);
        }
        JsonArray results = data.getArray("archives");
        if (results.size() == 0) {
            return new InfoItemsPage<>(new StreamInfoItemsCollector(getServiceId()), null);
        }
        final StreamInfoItemsCollector collector = new StreamInfoItemsCollector(getServiceId());
        for (int i = 0; i < results.size(); i++) {
            collector.commit(new BilibiliChannelInfoItemExtractor(results.getObject(i), page.getUrl().split("username=")[1], null));
        }
        return new InfoItemsPage<>(collector, new Page(utils.getNextPageFromCurrentUrl(page.getUrl(), type.equals("seasons_archives") ? "page_num" : "pn", 1)));
    }

    @Override
    public String getUploaderUrl() throws ParsingException {
        return BilibiliChannelLinkHandlerFactory.baseUrl + utils.getMidFromRecordApiUrl(getLinkHandler().getUrl());
    }

    @Override
    public String getUploaderName() throws ParsingException {
        return userData.getObject("data").getObject("card").getString("name");
    }

    @Override
    public String getUploaderAvatarUrl() throws ParsingException {
        return userData.getObject("data").getObject("card").getString("face").replace("http:", "https:");
    }

    @Override
    public boolean isUploaderVerified() throws ParsingException {
        return false;
    }

    @Override
    public long getStreamCount() throws ParsingException {
        return data.getObject("page").getLong("total");
    }
}
