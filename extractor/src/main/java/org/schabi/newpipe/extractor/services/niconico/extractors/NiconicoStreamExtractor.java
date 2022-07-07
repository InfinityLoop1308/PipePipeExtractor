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

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

public class NiconicoStreamExtractor extends StreamExtractor {
    private JsonObject watch;
    private int type = 0;

    private Document page = null;

    public NiconicoStreamExtractor(final StreamingService service,
                                   final LinkHandler linkHandler) {
        super(service, linkHandler);
    }

    @Override
    public long getViewCount() throws ParsingException {
        if(type == 1){
            return watch.getLong("view_counter");
        }
        return watch.getObject("video").getObject("count").getLong("view");
    }

    @Override
    public long getLength() throws ParsingException {
        if(type == 1){
            return watch.getLong("length_seconds");
        }
        return watch.getObject("video").getLong("duration");
    }

    @Override
    public long getLikeCount() throws ParsingException {
        if(type == 1){
            return watch.getLong("mylist_counter");
        }
        return  watch.getObject("video").getObject("count").getLong("like");
    }

    @Nonnull
    @Override
    public Description getDescription() throws ParsingException {
        if(type == 1){
            return new Description(watch.getString("description"), 1);
        }
        return new Description(watch.getObject("video").getString("description"), 1);
    }

    @Nonnull
    @Override
    public String getThumbnailUrl() throws ParsingException {
        if(type == 1){
            return page.getElementsByClass("thumbnail").attr("src");
        }
        return watch.getObject("video").getObject("thumbnail").getString("url");
    }

    @Nonnull
    @Override
    public String getUploaderUrl() throws ParsingException {
        if(type == 1){
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
        if(type == 1){
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
        if(type == 1){
            return getThumbnailUrl();
        }
        if (isChannel()) {
            return  watch.getObject("channel")
                    .getObject("thumbnail").getString("url");
        }
        return watch.getObject("owner").getString("iconUrl");
    }

    @Override
    public List<AudioStream> getAudioStreams() throws IOException, ExtractionException {
        return Collections.emptyList();
    }

    @Override
    public List<VideoStream> getVideoStreams() throws IOException, ExtractionException {
        final List<VideoStream> videoStreams = new ArrayList<>();
        videoStreams.add(new VideoStream.Builder().setContent("https://www.nicovideo.jp/watch/"+ getLinkHandler().getId(),  true).setId("Niconico-"+getId()).setIsVideoOnly(false).setMediaFormat(MediaFormat.MPEG_4).setResolution("360p").build());
        return  videoStreams;
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
        if(type == 1){
            return tags;
        }
        final JsonArray items = watch.getObject("tag").getArray("items");
        for (int i = 0; i < items.size(); i++) {
            tags.add(items.getObject(i).getString("name"));
        }

        return tags;
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

        for (final Element e: videos) {
            collector.commit(new NiconicoRelationVideoExtractor(e));
        }

        return collector;
    }

    @Override
    public void onFetchPage(final @Nonnull Downloader downloader)
            throws IOException, ExtractionException {
        final String url = "https://www.nicovideo.jp/watch/"+ getLinkHandler().getId();
        final Response response = downloader.get(url, null, NiconicoService.LOCALE);
        page = Jsoup.parse(response.responseBody());
        try {
            Element element = page.getElementById("js-initial-watch-data");
            if(element == null){
                type = 1; //need login
            }
            if(type == 1){
                watch = JsonParser.object().from(page.getElementsByClass("content WatchAppContainer").attr("data-video"));
            }
            else{
                watch = JsonParser.object().from(
                        page.getElementById("js-initial-watch-data").attr("data-api-data"));
            }
        } catch (final JsonParserException e) {
            throw new ExtractionException("could not extract watching page");
        }
    }

    @Nonnull
    @Override
    public String getName() throws ParsingException {
        if(type == 1){
            return watch.getString("title");
        }
        return watch.getObject("video").getString("title");
    }

    private Boolean isChannel() {
        return watch.isNull("owner");
    }
}