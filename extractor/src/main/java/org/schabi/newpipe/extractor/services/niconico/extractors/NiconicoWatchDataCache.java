package org.schabi.newpipe.extractor.services.niconico.extractors;

import com.grack.nanojson.JsonObject;
import com.grack.nanojson.JsonParser;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.schabi.newpipe.extractor.downloader.Downloader;
import org.schabi.newpipe.extractor.downloader.Response;
import org.schabi.newpipe.extractor.exceptions.ExtractionException;
import org.schabi.newpipe.extractor.exceptions.ReCaptchaException;
import org.schabi.newpipe.extractor.services.niconico.NiconicoService;

import java.io.IOException;

import javax.annotation.Nonnull;

public class NiconicoWatchDataCache {
    String lastId;
    Response response;
    Document page;
    JsonObject watchData;
    WatchDataType watchDataType;
    private String threadServer;
    private String threadId;

    public enum WatchDataType {
        LOGIN(1),
        GUEST(0);

        private final int value;

        WatchDataType(final int value) {
            this.value = value;
        }

        public int getValue() {
            return value;
        }
    }

    @Nonnull
    public JsonObject refreshAndGetWatchData(final Downloader downloader,
                                             @Nonnull final String id) throws ExtractionException {
        if (lastId != null && lastId.equals(id)) {
            return watchData;
        }

        String url = NiconicoService.WATCH_URL + id;
        if(id.contains("live.nicovideo.jp")){
            url = id;
        }
        try {
            response = downloader.get(url, null, NiconicoService.LOCALE);
        } catch (final IOException | ReCaptchaException e) {
            throw new ExtractionException("Could not get response.", e);
        }
        page = Jsoup.parse(response.responseBody());
        try {
            final Element element = page.getElementById("js-initial-watch-data");
            if (element == null) {
                watchDataType = WatchDataType.LOGIN; //need login
            } else {
                watchDataType = WatchDataType.GUEST;
            }
            if (watchDataType == WatchDataType.LOGIN) {
                watchData = JsonParser.object().from(
                        page.getElementsByClass("content WatchAppContainer")
                                .attr("data-video"));
            } else {
                watchData = JsonParser.object().from(
                        page.getElementById("js-initial-watch-data")
                                .attr("data-api-data"));
            }
        } catch (final Exception e) {
            throw new ExtractionException("Could not extract watching page", e);
        }

        lastId = id;
        return watchData;
    }

    public JsonObject getLastWatchData() {
        return watchData;
    }

    public WatchDataType getLastWatchDataType() {
        return watchDataType;
    }

    public Document getLastPage() {
        return page;
    }

    public Response getLastResponse() {
        return response;
    }

    public String getThreadId() {
        return threadId;
    }

    public String getThreadServer() {
        return threadServer;
    }

    public void setThreadId(String threadId) {
        this.threadId = threadId;
    }

    public void setThreadServer(String threadServer) {
        this.threadServer = threadServer;
    }
}
