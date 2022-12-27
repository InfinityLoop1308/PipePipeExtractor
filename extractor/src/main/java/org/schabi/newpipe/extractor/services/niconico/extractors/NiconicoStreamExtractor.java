package org.schabi.newpipe.extractor.services.niconico.extractors;

import static org.schabi.newpipe.extractor.services.bilibili.BilibiliService.getHeaders;

import com.grack.nanojson.JsonArray;
import com.grack.nanojson.JsonObject;
import com.grack.nanojson.JsonParser;
import com.grack.nanojson.JsonParserException;

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
import org.schabi.newpipe.extractor.exceptions.ExtractionException;
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
import java.net.ProxySelector;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
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
    private JsonObject liveData;
    private Document liveResponse;

    public NiconicoStreamExtractor(final StreamingService service,
            final LinkHandler linkHandler,
            final NiconicoWatchDataCache niconicoWatchDataCache) {
        super(service, linkHandler);
        this.niconicoWatchDataCache = niconicoWatchDataCache;
    }

    @Override
    public long getViewCount() throws ParsingException {
        if(getStreamType() == StreamType.LIVE_STREAM){
            return liveData.getArray("interactionStatistic").getObject(0).getLong("userInteractionCount");
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
            return liveData.getArray("thumbnailUrl").getString(0);
        }
        if (type == NiconicoWatchDataCache.WatchDataType.LOGIN) {
            return page.getElementsByClass("thumbnail").attr("src");
        }
        return watch.getObject("video").getObject("thumbnail").getString("url");
    }

    @Nonnull
    @Override
    public String getUploaderUrl() throws ParsingException {
        if(getStreamType() == StreamType.LIVE_STREAM){
            return liveData.getObject("author").getString("url");
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
            return liveData.getObject("author").getString("name");
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

    public String getNicoUrl(final String url) {
        final Map<String, List<String>> headers = new HashMap<>();
        headers.put("Content-Type", Collections.singletonList("application/json"));
        final Downloader downloader = getDownloader();
        try {
            final JsonObject session = watch.getObject("media").getObject("delivery").getObject("movie");
            final JsonObject encryption = watch.getObject("media")
                    .getObject("delivery").getObject("encryption");
            final String s = NiconicoDMCPayloadBuilder
                    .buildJSON(session.getObject("session"), encryption);
            response = downloader.post("https://api.dmc.nico/api/sessions?_format=json",
                    headers, s.getBytes(StandardCharsets.UTF_8), NiconicoService.LOCALE);
            final JsonObject content = JsonParser.object().from(response.responseBody());
            final String contentURL = content.getObject("data").getObject("session")
                    .getString("content_uri");
            return String.valueOf(contentURL);
        } catch (ReCaptchaException | JsonParserException | IOException e) {
            e.printStackTrace();
        }
        return null;
    }

    public String getLiveUrl() throws ParsingException, IOException, ReCaptchaException, JsonParserException {
        String url = getUrl();
        liveResponse = Jsoup.parse(getDownloader().get(url).responseBody());
        String result = JsonParser.object().from(liveResponse
                .select("script#embedded-data").attr("data-props"))
                .getObject("site").getObject("relive").getString("webSocketUrl");
        Map<String, String> httpHeaders = new HashMap<String, String>();
        httpHeaders.put("Pragma", "no-cache");
        httpHeaders.put("Origin", "https://live.nicovideo.jp");
        httpHeaders.put("Accept-Language", "en-US,en;q=0.9");
        httpHeaders.put("User-Agent", "Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/108.0.0.0 Safari/537.36");
        httpHeaders.put("Upgrade", "websocket");
        httpHeaders.put("Cache-Control", "no-cache");
        httpHeaders.put("Connection", "Upgrade");
        httpHeaders.put("Sec-WebSocket-Version", "13");
        httpHeaders.put("Sec-WebSocket-Extensions", "permessage-deflate; client_max_window_bits");
        NicoWebSocketClient nicoWebSocketClient = new NicoWebSocketClient(URI.create(result), httpHeaders);
        nicoWebSocketClient.connect();
        long startTime = System.nanoTime();
        do {
            if (nicoWebSocketClient.hasUrl()) {
                nicoWebSocketClient.close();
                return nicoWebSocketClient.getUrl();
            }
        } while (TimeUnit.NANOSECONDS.toSeconds(System.nanoTime() - startTime) <= 10);
        throw new RuntimeException("Failed to get live url");
    }

    @Override
    public List<VideoStream> getVideoStreams() throws IOException, ExtractionException {
        if(getStreamType() == StreamType.LIVE_STREAM){
            final List<VideoStream> videoStreams = new ArrayList<>();
            videoStreams.add(new VideoStream.Builder().setContent(liveUrl,true)
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
                .setResolution("Best")
                .build();
        videoStream.setNicoDownloadUrl(getNicoUrl(content));
        videoStreams.add(videoStream);
        return videoStreams;
    }

    @Nonnull
    @Override
    public String getHlsUrl() throws ParsingException {
        if(getStreamType() != StreamType.LIVE_STREAM){
            return null;
        }
        return liveUrl;
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
            JsonArray data = liveData.getArray("keywords");
            for(int i = 0; i< data.size();i++){
                tags.add(data.getString(i));
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
            JsonObject author = liveData.getObject("author");
            if(author.getString("url") == null || author.getString("url").contains("/ch")){
                try {
                    url = url.replace("live_watch_related_contents_user", "live_watch_related_contents_channel").replace("user_id", "channel_id");
                    url += JsonParser.object().from(liveResponse
                            .select("script#embedded-data").attr("data-props")).getObject("socialGroup").getString("id");
                } catch (JsonParserException e) {
                    throw new RuntimeException(e);
                }
            }else{
                url += author.getString("url").split("user/")[1];
            }
            try {
                JsonArray data = JsonParser.object().from(getDownloader().get(url).responseBody()).getObject("data").getArray("values");
                for(int i = 0; i< data.size();i++){
                    collector.commit(new NiconicoLiveRecommendVideoExtractor(data.getObject(i)));
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
                liveUrl = getLiveUrl();
                liveData = JsonParser.object().from(liveResponse.select("script[type='application/ld+json']").first().data());
            } catch (JsonParserException e) {
                throw new RuntimeException(e);
            }
            return ;
        }
        watch = niconicoWatchDataCache.refreshAndGetWatchData(downloader, getId());
        page = niconicoWatchDataCache.getLastPage();
        type = niconicoWatchDataCache.getLastWatchDataType();
        response = niconicoWatchDataCache.getLastResponse();
    }

    @Nonnull
    @Override
    public String getName() throws ParsingException {
        if(getStreamType() == StreamType.LIVE_STREAM){
            return liveData.getString("name");
        }
        if (type == NiconicoWatchDataCache.WatchDataType.LOGIN) {
            return watch.getString("title");
        }
        return watch.getObject("video").getString("title");
    }

    private Boolean isChannel() {
        return watch.isNull("owner");
    }
}
