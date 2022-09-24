package org.schabi.newpipe.extractor.services.bilibili.extractors;

import static org.schabi.newpipe.extractor.services.bilibili.BilibiliService.getHeaders;

import com.grack.nanojson.JsonArray;
import com.grack.nanojson.JsonObject;
import com.grack.nanojson.JsonParser;
import com.grack.nanojson.JsonParserException;

import org.schabi.newpipe.extractor.MultiInfoItemsCollector;
import org.schabi.newpipe.extractor.Page;
import org.schabi.newpipe.extractor.StreamingService;
import org.schabi.newpipe.extractor.comments.CommentsExtractor;
import org.schabi.newpipe.extractor.comments.CommentsInfoItem;
import org.schabi.newpipe.extractor.comments.CommentsInfoItemExtractor;
import org.schabi.newpipe.extractor.comments.CommentsInfoItemsCollector;
import org.schabi.newpipe.extractor.downloader.Downloader;
import org.schabi.newpipe.extractor.exceptions.ExtractionException;
import org.schabi.newpipe.extractor.linkhandler.ListLinkHandler;
import org.schabi.newpipe.extractor.stream.StreamInfoItemsCollector;

import java.io.IOException;

import javax.annotation.Nonnull;

public class BilibiliCommentExtractor extends CommentsExtractor {
    JsonObject json = new JsonObject();

    public BilibiliCommentExtractor(StreamingService service, ListLinkHandler uiHandler) {
        super(service, uiHandler);
    }

    @Override
    public void onFetchPage(@Nonnull Downloader downloader) throws IOException, ExtractionException {
        String response = downloader.get(getUrl()).responseBody();
        try {
            json = JsonParser.object().from(response);
            json = json.getObject("data");
        } catch (JsonParserException e) {
            e.printStackTrace();
        }
    }

    @Nonnull
    @Override
    public InfoItemsPage<CommentsInfoItem> getInitialPage() throws IOException, ExtractionException {
        JsonArray results = json.getArray("replies");
        if(results.size() == 0){
            return new InfoItemsPage<>(new CommentsInfoItemsCollector(getServiceId()), null);
        }
        final CommentsInfoItemsCollector collector = new CommentsInfoItemsCollector(getServiceId());
        for (int i = 0; i< results.size(); i++){
            collector.commit(new BilibiliCommentsInfoItemExtractor(results.getObject(i), getUrl()));
        }
        int currentPage = 1;
        String nextPage = getUrl().replace(String.format("pn=%s", 1), String.format("pn=%s", String.valueOf(currentPage + 1)));
        return new InfoItemsPage<>(collector, new Page(nextPage));
    }

    @Override
    public InfoItemsPage<CommentsInfoItem> getPage(Page page) throws IOException, ExtractionException {
        final String html = getDownloader().get(page.getUrl(), getHeaders()).responseBody();
        try {
            json = JsonParser.object().from(html);
        } catch (JsonParserException e) {
            e.printStackTrace();
        }
        JsonArray results = json.getObject("data").getArray("replies");
        if(results.size() == 0){
            return new InfoItemsPage<>(new CommentsInfoItemsCollector(getServiceId()), null);
        }

        final CommentsInfoItemsCollector collector = new CommentsInfoItemsCollector(getServiceId());
        for (int i = 0; i< results.size(); i++){
            collector.commit(new BilibiliCommentsInfoItemExtractor(results.getObject(i), getUrl()));
        }
        String currentPageString = page.getUrl().split("pn=")[page.getUrl().split("pn=").length-1].split("&")[0];
        int currentPage = Integer.parseInt(currentPageString);
        String nextPage = getUrl().replace(String.format("pn=%s", 1), String.format("pn=%s", String.valueOf(currentPage + 1)));
        return new InfoItemsPage<>(collector, new Page(nextPage));
    }

    @Override
    public boolean isCommentsDisabled() throws ExtractionException {
        return getId().equals("LIVE");
    }
}
