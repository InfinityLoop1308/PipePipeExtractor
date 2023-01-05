package org.schabi.newpipe.extractor.services.niconico.extractors;

import com.grack.nanojson.JsonArray;
import com.grack.nanojson.JsonObject;
import com.grack.nanojson.JsonParser;
import com.grack.nanojson.JsonParserException;

import org.java_websocket.client.WebSocketClient;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import org.schabi.newpipe.extractor.InfoItem;
import org.schabi.newpipe.extractor.InfoItemExtractor;
import org.schabi.newpipe.extractor.InfoItemsCollector;
import org.schabi.newpipe.extractor.MediaFormat;
import org.schabi.newpipe.extractor.StreamingService;
import org.schabi.newpipe.extractor.downloader.Downloader;
import org.schabi.newpipe.extractor.downloader.Response;
import org.schabi.newpipe.extractor.exceptions.ContentNotAvailableException;
import org.schabi.newpipe.extractor.exceptions.ExtractionException;
import org.schabi.newpipe.extractor.exceptions.LiveNotStartException;
import org.schabi.newpipe.extractor.exceptions.PaidContentException;
import org.schabi.newpipe.extractor.exceptions.ParsingException;
import org.schabi.newpipe.extractor.exceptions.ReCaptchaException;
import org.schabi.newpipe.extractor.linkhandler.LinkHandler;
import org.schabi.newpipe.extractor.services.niconico.NicoWebSocketClient;
import org.schabi.newpipe.extractor.services.niconico.NiconicoService;
import org.schabi.newpipe.extractor.stream.AudioStream;
import org.schabi.newpipe.extractor.stream.DeliveryMethod;
import org.schabi.newpipe.extractor.stream.Description;
import org.schabi.newpipe.extractor.stream.StreamExtractor;
import org.schabi.newpipe.extractor.stream.StreamInfoItemsCollector;
import org.schabi.newpipe.extractor.stream.StreamType;
import org.schabi.newpipe.extractor.stream.VideoStream;

import java.io.IOException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

public class NiconicoStreamExtractor extends StreamExtractor {
    private JsonObject watch;
    private NiconicoWatchDataCache.WatchDataType type;
    private final NiconicoWatchDataCache niconicoWatchDataCache;
    private Document page = null;
    private Response response = null;
    private String liveUrl;
    private String liveMessageServer;
    private String liveThreadId;
    private JsonObject liveData;
    private Document liveResponse;
    private JsonObject liveDataRoot;
    private boolean isHlsStream;

    public NiconicoStreamExtractor(final StreamingService service,
            final LinkHandler linkHandler,
            final NiconicoWatchDataCache niconicoWatchDataCache) {
        super(service, linkHandler);
        this.niconicoWatchDataCache = niconicoWatchDataCache;
    }

    @Override
    public long getViewCount() throws ParsingException {
        if(getStreamType() == StreamType.LIVE_STREAM){
            return liveData.getObject("statistics").getLong("watchCount");
        }
        if (type == NiconicoWatchDataCache.WatchDataType.LOGIN) {
            return watch.getLong("view_counter");
        }
        return watch.getObject("video").getObject("count").getLong("view");
    }

    @Override
    public long getLength() throws ParsingException {
        if(getStreamType() == StreamType.LIVE_STREAM){
            return -1;
        }
        if (type == NiconicoWatchDataCache.WatchDataType.LOGIN) {
            return watch.getLong("length_seconds");
        }
        return watch.getObject("video").getLong("duration");
    }

    @Override
    public long getLikeCount() throws ParsingException {
        if(getStreamType() == StreamType.LIVE_STREAM){
            return -1;
        }
        if (type == NiconicoWatchDataCache.WatchDataType.LOGIN) {
            return watch.getLong("mylist_counter");
        }
        return watch.getObject("video").getObject("count").getLong("like");
    }

    @Nonnull
    @Override
    public Description getDescription() throws ParsingException {
        if(getStreamType() == StreamType.LIVE_STREAM){
            return new Description(liveData.getString("description"), 1);
        }
        if (type == NiconicoWatchDataCache.WatchDataType.LOGIN) {
            return new Description(watch.getString("description"), 1);
        }
        return new Description(watch.getObject("video").getString("description"), 1);
    }

    @Nonnull
    @Override
    public String getThumbnailUrl() throws ParsingException {
        if(getStreamType() == StreamType.LIVE_STREAM){
            return liveData.getObject("thumbnail").getString("small").replace("http:", "https:");
        }
        if (type == NiconicoWatchDataCache.WatchDataType.LOGIN) {
            return page.getElementsByClass("thumbnail").attr("src")
                    .replace("http:", "https:");
        }
        return watch.getObject("video").getObject("thumbnail").getString("url")
                .replace("http:", "https:");
    }

    @Nonnull
    @Override
    public String getUploaderUrl() throws ParsingException {
        if(getStreamType() == StreamType.LIVE_STREAM){
            return liveData.getObject("supplier").getString("pageUrl");
        }
        if (type == NiconicoWatchDataCache.WatchDataType.LOGIN) {
            return "";
        }
        if (isChannel()) {
            return NiconicoService.CHANNEL_URL
                    + watch.getObject("channel").getString("id");
        }
        return NiconicoService.USER_URL + watch.getObject("owner").getLong("id");
    }

    @Nonnull
    @Override
    public String getUploaderName() throws ParsingException {
        if(getStreamType() == StreamType.LIVE_STREAM){
            return liveData.getObject("supplier").getString("name");
        }
        if (type == NiconicoWatchDataCache.WatchDataType.LOGIN) {
            return getName();
        }
        if (isChannel()) {
            return watch.getObject("channel").getString("name");
        }
        return watch.getObject("owner").getString("nickname");
    }

    @Nonnull
    @Override
    public String getUploaderAvatarUrl() throws ParsingException {
        if(getStreamType() == StreamType.LIVE_STREAM){
            return liveResponse.select(".___resource___2_bdf").attr("src");
        }
        if (type == NiconicoWatchDataCache.WatchDataType.LOGIN) {
            return getThumbnailUrl();
        }
        if (isChannel()) {
            return watch.getObject("channel")
                    .getObject("thumbnail").getString("url");
        }
        return watch.getObject("owner").getString("iconUrl");
    }

    @Override
    public List<AudioStream> getAudioStreams() throws IOException, ExtractionException {
        return Collections.emptyList();
    }

    public void getLiveUrl() throws ExtractionException, IOException, JsonParserException {
        String url = getUrl();
        String responseBody = getDownloader().get(url).responseBody();
        liveResponse = Jsoup.parse(responseBody);
        String result = JsonParser.object().from(liveResponse
                .select("script#embedded-data").attr("data-props"))
                .getObject("site").getObject("relive").getString("webSocketUrl");
        NicoWebSocketClient nicoWebSocketClient = new NicoWebSocketClient(URI.create(result), NiconicoService.getWebSocketHeaders());
        NicoWebSocketClient.WrappedWebSocketClient webSocketClient = nicoWebSocketClient.getWebSocketClient();
        webSocketClient.connect();
        long startTime = System.nanoTime();
        do {
            liveUrl = nicoWebSocketClient.getUrl();
            liveMessageServer = nicoWebSocketClient.getServerUrl();
            liveThreadId = nicoWebSocketClient.getThreadId();
            if (liveUrl != null && liveMessageServer != null && liveThreadId != null) {
                webSocketClient.close();
                return ;
            }
        } while (TimeUnit.NANOSECONDS.toSeconds(System.nanoTime() - startTime) <= 10);
        webSocketClient.close();
        if(responseBody.contains("フォロワー限定")){
            throw new ContentNotAvailableException("The live is for followers only");
        } else if (responseBody.contains("非公開")) {
            throw new ContentNotAvailableException("タイムシフト非公開番組です");
        } else if (responseBody.contains("会員無料")) {
            throw new PaidContentException("Only available for premium users");
        }
        liveDataRoot = JsonParser.object().from(liveResponse.select("script#embedded-data")
                .first().attr("data-props"));
        liveData = liveDataRoot.getObject("program");
        if(getStartAt() - new Date().getTime() > 0){
            throw new LiveNotStartException("The live is not started yet");
        }
        throw new ExtractionException("Failed to get live url");
    }

    @Override
    public List<VideoStream> getVideoStreams() throws IOException, ExtractionException {
        if(getStreamType() == StreamType.LIVE_STREAM){
            final List<VideoStream> videoStreams = new ArrayList<>();
            videoStreams.add(new VideoStream.Builder().setContent(getUrl(),true)
                    .setId("Niconico-" + getId() +"-live").setIsVideoOnly(false)
                    .setResolution("Best").setDeliveryMethod(DeliveryMethod.HLS).build());
            return videoStreams;
        }
        final List<VideoStream> videoStreams = new ArrayList<>();
        final String content = NiconicoService.WATCH_URL + getLinkHandler().getId();
        final VideoStream videoStream = new VideoStream.Builder()
                .setContent(content, true).setId("Niconico-" + getId())
                .setIsVideoOnly(false)
                .setMediaFormat(MediaFormat.MPEG_4)
                .setDeliveryMethod(isHlsStream? DeliveryMethod.HLS : DeliveryMethod.PROGRESSIVE_HTTP)
                .setResolution("Best")
                .build();
        videoStreams.add(videoStream);
        return videoStreams;
    }

    @Nonnull
    @Override
    public String getHlsUrl() throws ParsingException {
        if(getStreamType() == StreamType.VIDEO_STREAM && !isHlsStream){
            return null;
        }
        return getUrl();
    }

    @Override
    public List<VideoStream> getVideoOnlyStreams() throws IOException, ExtractionException {
        return Collections.emptyList();
    }

    @Override
    public StreamType getStreamType() throws ParsingException {
        if(getUrl().contains("live.nicovideo.jp")){
            return StreamType.LIVE_STREAM;
        }
        return StreamType.VIDEO_STREAM;
    }

    @Nonnull
    @Override
    public List<String> getTags() throws ParsingException {
        final List<String> tags = new ArrayList<>();
        if(getStreamType() == StreamType.LIVE_STREAM){
            JsonArray data = liveData.getObject("tag").getArray("list");
            for(int i = 0; i< data.size();i++){
                tags.add(data.getObject(i).getString("text"));
            }
            return tags;
        }
        if (type == NiconicoWatchDataCache.WatchDataType.LOGIN) {
            return tags;
        }
        final JsonArray items = watch.getObject("tag").getArray("items");
        return items.stream()
                .filter(s -> s instanceof JsonObject)
                .map(s -> (JsonObject) s)
                .map(s -> s.getString("name"))
                .collect(Collectors.toList());
    }

    @Nullable
    @Override
    public InfoItemsCollector<? extends InfoItem, ? extends InfoItemExtractor> getRelatedItems()
            throws IOException, ExtractionException {
        final StreamInfoItemsCollector collector = new StreamInfoItemsCollector(
                getServiceId());
        if(getStreamType() == StreamType.LIVE_STREAM){
            String url =
                    "https://live.nicovideo.jp/front/api/v1/recommend-contents" +
                            "?recipe=live_watch_related_contents_user&v=1&site=nicolive&content_meta=true&frontend_id=9&tags=&user_id=";
            String uploaderUrl = getUploaderUrl();
            if(uploaderUrl == null || uploaderUrl.contains("/ch")){
                url = url.replace("live_watch_related_contents_user", "live_watch_related_contents_channel").replace("user_id", "channel_id");
                url += liveDataRoot.getObject("socialGroup").getString("id");
            }else{
                url += uploaderUrl.split("user/")[1];
            }
            try {
                JsonArray data = JsonParser.object().from(getDownloader().get(url).responseBody()).getObject("data").getArray("values");
                for(int i = 0; i< data.size();i++){
                    collector.commit(new NiconicoLiveRecommendVideoExtractor(
                            data.getObject(i), uploaderUrl, getUploaderName()));
                }
                return collector;
            } catch (JsonParserException e) {
                throw new RuntimeException(e);
            }
        }
        final String url = NiconicoService.RELATION_URL + getId();
        final Document response = Jsoup.parse(
                getDownloader().get(url, NiconicoService.LOCALE).responseBody());

        final Elements videos = response.getElementsByTag("video");

        for (final Element e : videos) {
            collector.commit(new NiconicoRelationVideoExtractor(e));
        }

        return collector;
    }

    @Override
    public void onFetchPage(final @Nonnull Downloader downloader)
            throws IOException, ExtractionException {
        if(getStreamType() == StreamType.LIVE_STREAM){
            try {
                getLiveUrl();
                niconicoWatchDataCache.setThreadId(liveThreadId);
                niconicoWatchDataCache.setThreadServer(liveMessageServer);
            } catch (JsonParserException e) {
                throw new RuntimeException(e);
            }
            return ;
        }
        watch = niconicoWatchDataCache.refreshAndGetWatchData(downloader, getId());
        isHlsStream = watch.getObject("media").getObject("delivery")
                .getObject("movie").getObject("session").getArray("protocols")
                .getString(0).equals("hls");
        page = niconicoWatchDataCache.getLastPage();
        type = niconicoWatchDataCache.getLastWatchDataType();
        response = niconicoWatchDataCache.getLastResponse();
    }

    @Nonnull
    @Override
    public String getName() throws ParsingException {
        if(getStreamType() == StreamType.LIVE_STREAM){
            return liveData.getString("title");
        }
        if (type == NiconicoWatchDataCache.WatchDataType.LOGIN) {
            return watch.getString("title");
        }
        return watch.getObject("video").getString("title");
    }

    private Boolean isChannel() {
        return watch.isNull("owner");
    }

    @Override
    public long getStartAt() throws ParsingException {
        if(getStreamType() == StreamType.LIVE_STREAM){
            return liveData.getLong("beginTime") * 1000;
        }
        return -1;
    }
}
