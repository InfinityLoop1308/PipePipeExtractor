package org.schabi.newpipe.extractor.services.niconico.extractors;

import com.grack.nanojson.JsonObject;
import com.grack.nanojson.JsonParser;
import com.grack.nanojson.JsonParserException;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.nodes.Entities;
import org.schabi.newpipe.extractor.ServiceList;
import org.schabi.newpipe.extractor.downloader.Downloader;
import org.schabi.newpipe.extractor.downloader.Response;
import org.schabi.newpipe.extractor.exceptions.ContentNotAvailableException;
import org.schabi.newpipe.extractor.exceptions.ExtractionException;
import org.schabi.newpipe.extractor.exceptions.GeographicRestrictionException;
import org.schabi.newpipe.extractor.exceptions.PaidContentException;
import org.schabi.newpipe.extractor.exceptions.ParsingException;
import org.schabi.newpipe.extractor.exceptions.ReCaptchaException;
import org.schabi.newpipe.extractor.services.niconico.NiconicoService;

import java.io.IOException;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;

import javax.annotation.Nonnull;

public class NiconicoWatchDataCache {
    String lastId;
    Response response;
    Document page;
    JsonObject watchData;
    WatchDataType watchDataType;
    private String threadServer;
    private String threadId;
    private long startAt;
    private String streamCookie="";

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
    public void invalidate(){
        lastId = null;
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
            HashMap<String, List<String>> headers = new HashMap<>();
            if(ServiceList.NicoNico.getTokens() != null){
                headers.put("Cookie", Collections.singletonList(ServiceList.NicoNico.getTokens()));
            }
            response = downloader.get(url, headers, NiconicoService.LOCALE);
        } catch (final IOException | ReCaptchaException e) {
            throw new ExtractionException("Could not get response.", e);
        }
        page = Jsoup.parse(response.responseBody());
        try {
            Element element = page.select("meta[name=\"server-response\"]").first();
            if (element == null) {
                watchDataType = WatchDataType.LOGIN; //need login
                if(response.responseBody().contains("チャンネル会員専用動画")){
                    throw new PaidContentException("Channel member limited videos");
                } else if (response.responseBody().contains("地域と同じ地域からのみ視聴")) {
                    throw new GeographicRestrictionException("Sorry, this video can only be viewed in the same region where it was uploaded.");
                } else if (response.responseBody().contains("この動画を視聴するにはログインが必要です。")) {
                    throw new PaidContentException("This video requires login to view.");
                }
                throw new ContentNotAvailableException(page.select("p.fail-message").text());
            } else {
                watchDataType = WatchDataType.GUEST;
            }
            if (watchDataType == WatchDataType.LOGIN) {
                watchData = JsonParser.object().from(
                        page.getElementsByClass("content WatchAppContainer")
                                .attr("data-video"));
            } else {
                watchData = JsonParser.object().from(
                        Entities.unescape(element.attr("content"))).getObject("data").getObject("response");
            }
        } catch (JsonParserException e) {
            throw new ParsingException("Failed to parse content");
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

    public long getStartAt() {
        return startAt;
    }

    public void setStartAt(long startAt) {
        this.startAt = startAt;
    }

    public String getStreamCookie() {
        if(ServiceList.NicoNico.getTokens() != null){
            String cookie = ServiceList.NicoNico.getTokens();
            if(cookie.endsWith(";")){
                cookie = cookie.substring(0, cookie.length() - 1);
            }
            cookie += ";" + streamCookie;
            return cookie;
        }
        return streamCookie;
    }

    public void setStreamCookie(String streamCookie) {
        this.streamCookie = streamCookie;
    }
}
