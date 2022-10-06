package org.schabi.newpipe.extractor.services.niconico.extractors;

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
import org.schabi.newpipe.extractor.services.niconico.NiconicoService;
import org.schabi.newpipe.extractor.stream.AudioStream;
import org.schabi.newpipe.extractor.stream.Description;
import org.schabi.newpipe.extractor.stream.StreamExtractor;
import org.schabi.newpipe.extractor.stream.StreamInfoItemsCollector;
import org.schabi.newpipe.extractor.stream.StreamType;
import org.schabi.newpipe.extractor.stream.VideoStream;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

public class NiconicoStreamExtractor extends StreamExtractor {
    private JsonObject watch;
    private NiconicoWatchDataCache.WatchDataType type;
    private final NiconicoWatchDataCache niconicoWatchDataCache;
    private Document page = null;
    private Response response = null;

    public NiconicoStreamExtractor(final StreamingService service,
            final LinkHandler linkHandler,
            final NiconicoWatchDataCache niconicoWatchDataCache) {
        super(service, linkHandler);
        this.niconicoWatchDataCache = niconicoWatchDataCache;
    }

    @Override
    public long getViewCount() throws ParsingException {
        if (type == NiconicoWatchDataCache.WatchDataType.LOGIN) {
            return watch.getLong("view_counter");
        }
        return watch.getObject("video").getObject("count").getLong("view");
    }

    @Override
    public long getLength() throws ParsingException {
        if (type == NiconicoWatchDataCache.WatchDataType.LOGIN) {
            return watch.getLong("length_seconds");
        }
        return watch.getObject("video").getLong("duration");
    }

    @Override
    public long getLikeCount() throws ParsingException {
        if (type == NiconicoWatchDataCache.WatchDataType.LOGIN) {
            return watch.getLong("mylist_counter");
        }
        return watch.getObject("video").getObject("count").getLong("like");
    }

    @Nonnull
    @Override
    public Description getDescription() throws ParsingException {
        if (type == NiconicoWatchDataCache.WatchDataType.LOGIN) {
            return new Description(watch.getString("description"), 1);
        }
        return new Description(watch.getObject("video").getString("description"), 1);
    }

    @Nonnull
    @Override
    public String getThumbnailUrl() throws ParsingException {
        if (type == NiconicoWatchDataCache.WatchDataType.LOGIN) {
            return page.getElementsByClass("thumbnail").attr("src");
        }
        return watch.getObject("video").getObject("thumbnail").getString("url");
    }

    @Nonnull
    @Override
    public String getUploaderUrl() throws ParsingException {
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

    @Override
    public List<VideoStream> getVideoStreams() throws IOException, ExtractionException {
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

    @Override
    public List<VideoStream> getVideoOnlyStreams() throws IOException, ExtractionException {
        return Collections.emptyList();
    }

    @Override
    public StreamType getStreamType() throws ParsingException {
        return StreamType.VIDEO_STREAM;
    }

    @Nonnull
    @Override
    public List<String> getTags() throws ParsingException {
        final List<String> tags = new ArrayList<>();
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
        watch = niconicoWatchDataCache.refreshAndGetWatchData(downloader, getId());
        page = niconicoWatchDataCache.getLastPage();
        type = niconicoWatchDataCache.getLastWatchDataType();
        response = niconicoWatchDataCache.getLastResponse();
    }

    @Nonnull
    @Override
    public String getName() throws ParsingException {
        if (type == NiconicoWatchDataCache.WatchDataType.LOGIN) {
            return watch.getString("title");
        }
        return watch.getObject("video").getString("title");
    }

    private Boolean isChannel() {
        return watch.isNull("owner");
    }
}
