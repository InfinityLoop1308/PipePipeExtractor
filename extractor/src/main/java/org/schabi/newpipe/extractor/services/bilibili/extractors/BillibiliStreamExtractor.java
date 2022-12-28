package org.schabi.newpipe.extractor.services.bilibili.extractors;

import static org.schabi.newpipe.extractor.services.bilibili.BilibiliService.getHeaders;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.List;
import java.util.zip.DataFormatException;

import com.grack.nanojson.JsonArray;
import com.grack.nanojson.JsonObject;
import com.grack.nanojson.JsonParser;
import com.grack.nanojson.JsonParserException;
import org.schabi.newpipe.extractor.InfoItem;
import org.schabi.newpipe.extractor.InfoItemExtractor;
import org.schabi.newpipe.extractor.InfoItemsCollector;
import org.schabi.newpipe.extractor.MediaFormat;
import org.schabi.newpipe.extractor.StreamingService;
import org.schabi.newpipe.extractor.downloader.Downloader;
import org.schabi.newpipe.extractor.exceptions.ExtractionException;
import org.schabi.newpipe.extractor.exceptions.ParsingException;
import org.schabi.newpipe.extractor.exceptions.ReCaptchaException;
import org.schabi.newpipe.extractor.linkhandler.LinkHandler;
import org.schabi.newpipe.extractor.localization.DateWrapper;
import org.schabi.newpipe.extractor.services.bilibili.WatchDataCache;
import org.schabi.newpipe.extractor.services.bilibili.linkHandler.BilibiliChannelLinkHandlerFactory;
import org.schabi.newpipe.extractor.services.bilibili.utils;
import org.schabi.newpipe.extractor.stream.AudioStream;
import org.schabi.newpipe.extractor.stream.DeliveryMethod;
import org.schabi.newpipe.extractor.stream.Description;
import org.schabi.newpipe.extractor.stream.StreamExtractor;
import org.schabi.newpipe.extractor.stream.StreamInfoItemsCollector;
import org.schabi.newpipe.extractor.stream.StreamType;
import org.schabi.newpipe.extractor.stream.SubtitlesStream;
import org.schabi.newpipe.extractor.stream.VideoStream;

import javax.annotation.Nonnull;

import fr.noop.subtitle.model.SubtitleParsingException;
import fr.noop.subtitle.srt.SrtObject;
import fr.noop.subtitle.srt.SrtParser;
import fr.noop.subtitle.ttml.TtmlWriter;

public class BillibiliStreamExtractor extends StreamExtractor {

    private JsonObject watch;
    int cid = 0;
    int duration = 0;
    String id = "";
    JsonObject page = null;

    String url;

    WatchDataCache watchDataCache;
    public BillibiliStreamExtractor(StreamingService service, LinkHandler linkHandler, WatchDataCache watchDataCache) {
        super(service, linkHandler);
        this.watchDataCache = watchDataCache;
    }

    @Override
    public String getThumbnailUrl() throws ParsingException {
        if(getStreamType() == StreamType.LIVE_STREAM){
            return watch.getString("cover_from_user").replace("http:", "https:");
        }
        return watch.getString("pic").replace("http:", "https:");
    }

    @Override
    public String getUploaderUrl() throws ParsingException {
        if(getStreamType() == StreamType.LIVE_STREAM) {
            return BilibiliChannelLinkHandlerFactory.baseUrl + watch.getLong("uid");
        }
        return BilibiliChannelLinkHandlerFactory.baseUrl  +watch.getObject("owner").getLong("mid");
    }

    @Override
    public String getUploaderName() throws ParsingException {
        if(getStreamType() == StreamType.LIVE_STREAM) {
            return watch.getString("uname");
        }
        return watch.getObject("owner").getString("name");
    }

    @Override
    public List<AudioStream> getAudioStreams() throws IOException, ExtractionException {
        if(getStreamType() == StreamType.LIVE_STREAM){
            return null;
        }
         final List<AudioStream> audioStreams = new ArrayList<>();
         String bvid = watch.getString("bvid");
         String response = getDownloader().get("https://api.bilibili.com/x/player/playurl"+"?cid="+cid+"&bvid="+bvid+"&fnval=16", getHeaders()).responseBody();
         JsonObject responseJson = new JsonObject();
         try {
             responseJson =  JsonParser.object().from(response);
         } catch (JsonParserException e) {
             e.printStackTrace();
         }
         JsonArray audioArray =responseJson.getObject("data").getObject("dash").getArray("audio") ;
         String url = audioArray.getObject(0).getString("baseUrl");
         audioStreams.add(new AudioStream.Builder().setId("bilibili-"+bvid+"-audio").setContent(url,true).setMediaFormat(MediaFormat.M4A).setAverageBitrate(192000).build());
         return audioStreams;
    }

    @Override
    public List<VideoStream> getVideoStreams() throws IOException, ExtractionException {
        if(getStreamType() != StreamType.LIVE_STREAM){
            return null;
        }
        final List<VideoStream> videoStreams = new ArrayList<>();

        try {
            if(url == null){
                String response = getDownloader().get("https://api.live.bilibili.com/room/v1/Room/playUrl?qn=10000&platform=h5&cid=" + getId(), getHeaders()).responseBody();
                url = JsonParser.object().from(response).getObject("data").getArray("durl").getObject(0).getString("url");
            }
            videoStreams.add(new VideoStream.Builder().setContent(url,true).setId("bilibili-"+watch.getLong("uid") +"-live").setIsVideoOnly(false).setResolution("720p").setDeliveryMethod(DeliveryMethod.HLS).build());
        } catch (JsonParserException e) {
            e.printStackTrace();
        }
        return videoStreams;
    }

    @Nonnull
    @Override
    public String getHlsUrl() throws ParsingException {
        if(getStreamType() != StreamType.LIVE_STREAM){
            return null;
        }
        try {
            if(url == null){
                String response = getDownloader().get("https://api.live.bilibili.com/room/v1/Room/playUrl?qn=10000&platform=h5&cid=" + getId(), getHeaders()).responseBody();
                url = JsonParser.object().from(response).getObject("data").getArray("durl").getObject(0).getString("url");
            }
        } catch (JsonParserException | IOException | ReCaptchaException e) {
            e.printStackTrace();
        }
        return url;
    }

    @Override
    public List<VideoStream> getVideoOnlyStreams() throws IOException, ExtractionException {
        if(getStreamType() == StreamType.LIVE_STREAM){
            return null;
        }
        final List<VideoStream> videoStreams = new ArrayList<>();
         String bvid = watch.getString("bvid");
         String response = getDownloader().get("https://api.bilibili.com/x/player/playurl"+"?cid="+cid+"&bvid="+bvid+"&fnval=16", getHeaders()).responseBody();
         JsonObject responseJson = new JsonObject();
         try {
             responseJson =  JsonParser.object().from(response);
         } catch (JsonParserException e) {
             e.printStackTrace();
         }
         String url = "";
         String desc ="";
         JsonArray videoArray =responseJson.getObject("data").getObject("dash").getArray("video") ;
         for(int i=0; i< videoArray.size(); i++){
             if(videoArray.getObject(i).getInt("id") > 64){
                 continue;
             }
            url = videoArray.getObject(i).getString("baseUrl");
         }
         videoStreams.add(new VideoStream.Builder().setContent(url,true).setMediaFormat( MediaFormat.MPEG_4).setId("bilibili-"+bvid+"-video").setIsVideoOnly(true).setResolution("Best").build());
        return videoStreams;
    }

    @Override
    public StreamType getStreamType() throws ParsingException {
        if(getLinkHandler().getOriginalUrl().contains("live.bilibili.com")){
            return StreamType.LIVE_STREAM;
        }
        return StreamType.VIDEO_STREAM;
    }

    @Override
    public void onFetchPage(Downloader downloader) throws IOException, ExtractionException {
        if(getStreamType() == StreamType.LIVE_STREAM){
            String response = downloader.get("https://api.live.bilibili.com/room/v1/Room/room_init?id=" + getId()).responseBody();
            try {
                String uid = String.valueOf(JsonParser.object().from(response).getObject("data").getLong("uid"));
                response = downloader.get("https://api.live.bilibili.com/room/v1/Room/get_status_info_by_uids?uids[]=" + uid).responseBody();
                watch = JsonParser.object().from(response).getObject("data").getObject(uid);
            } catch (JsonParserException e) {
                e.printStackTrace();
            }
            return ;
        }
        String url = getLinkHandler().getOriginalUrl();
        id =  utils.getPureBV(getId());
        url = utils.getUrl(url, id);
        final String response = downloader.get(url).responseBody();
        try {
            watch = JsonParser.object().from(response).getObject("data");
        } catch (JsonParserException e) {
            e.printStackTrace();
        }
        page = watch.getArray("pages").getObject(Integer.parseInt(getLinkHandler().getUrl().split("p=")[1].split("&")[0])-1);
        cid = page.getInt("cid");
        watchDataCache.setCid(cid);
        duration = page.getInt("duration");
    }

    @Override
    public String getName() throws ParsingException {
        String title = watch.getString("title");
        if(getStreamType() != StreamType.LIVE_STREAM&& watch.getArray("pages").size() > 1){
            title += " | P" + page.getInt("page") + " "+ page.getString("part");
        }
        return title;
    }
    @Override
    public long getLength() throws ParsingException {
        if(getStreamType() == StreamType.LIVE_STREAM){
            return -1;
        }
        return duration;
    }
    @Override
    public String getUploaderAvatarUrl () throws ParsingException {
        if(getStreamType() == StreamType.LIVE_STREAM){
            return watch.getString("face").replace("http:", "https:");
        }
        return watch.getObject("owner").getString("face").replace("http:", "https:");
    }
    @Override
    public Description getDescription() throws ParsingException {
        if(getStreamType() == StreamType.LIVE_STREAM){
            return null;
        }
        return new Description(watch.getString("desc"), Description.PLAIN_TEXT);
    }


    @Override
    public long getViewCount() throws ParsingException {
        if(getStreamType() == StreamType.LIVE_STREAM){
            return watch.getLong("online");
        }
        return watch.getObject("stat").getLong("view");
    }
    @Override
    public long getLikeCount() throws ParsingException {
        if(getStreamType() == StreamType.LIVE_STREAM){
            return -1;
        }
        return watch.getObject("stat").getLong("coin");
    }

    @Nonnull
    @Override
    public List<String> getTags() throws ParsingException {
        List<String> tags = new ArrayList<>();
        if(getStreamType() == StreamType.LIVE_STREAM){
            tags = Arrays.asList((watch.getString("tag_name")+","+watch.getString("tags")).split(","));
        }
        try {
            JsonArray respArray = JsonParser.object().from(getDownloader().get("https://api.bilibili.com/x/tag/archive/tags?bvid=" + utils.getPureBV(getId()), getHeaders()).responseBody()).getArray("data");
            for(int i = 0; i< respArray.size(); i++){
                tags.add(respArray.getObject(i).getString("tag_name"));
            }
        } catch (IOException e) {
            e.printStackTrace();
        } catch (ReCaptchaException e) {
            e.printStackTrace();
        } catch (JsonParserException e) {
            e.printStackTrace();
        }
        return tags;
    }

    @Override
    public InfoItemsCollector<? extends InfoItem, ? extends InfoItemExtractor>getRelatedItems() throws ParsingException {
        if(getStreamType() == StreamType.LIVE_STREAM){
            return null;
        }
        InfoItemsCollector collector = new StreamInfoItemsCollector(getServiceId());
        String response = null;
        try {
            response = getDownloader().get("https://api.bilibili.com/x/player/pagelist?bvid="+id, getHeaders()).responseBody();
        } catch (IOException e) {
            e.printStackTrace();
        } catch (ReCaptchaException e) {
            e.printStackTrace();
        }
        try {
            JsonObject relatedJson = JsonParser.object().from(response);
            JsonArray relatedArray = relatedJson.getArray("data");
            if(relatedArray.size()== 1){
                response = getDownloader().get("https://api.bilibili.com/x/web-interface/archive/related?bvid="+ id, getHeaders()).responseBody();
                relatedJson = JsonParser.object().from(response);
                relatedArray = relatedJson.getArray("data");
                for(int i=0;i<relatedArray.size();i++){
                    collector.commit(new BilibiliRelatedInfoItemExtractor(relatedArray.getObject(i)));
                }
                return collector;
            }
            for(int i=0;i<relatedArray.size();i++){
                collector.commit(
                        new BilibiliRelatedInfoItemExtractor(
                                relatedArray.getObject(i), id, getThumbnailUrl(), String.valueOf(i+1), getUploaderName(), watch.getLong("ctime")));
            }
        } catch (JsonParserException | ParsingException e) {
            e.printStackTrace();
        } catch (IOException e) {
            e.printStackTrace();
        } catch (ReCaptchaException e) {
            e.printStackTrace();
        }
        return collector;
    }
    @Override
    public String getTextualUploadDate() throws ParsingException {
        if(getStreamType().equals(StreamType.LIVE_STREAM)){
            return new SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(new Date(watch.getLong("live_time")*1000));
        }
        return new SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(new Date(watch.getLong("ctime")*1000));
    }

    @Override
    public DateWrapper getUploadDate() throws ParsingException {
        return new DateWrapper(LocalDateTime.parse(
                getTextualUploadDate(), DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")).atOffset(ZoneOffset.ofHours(+8)));
    }
    @Nonnull
    @Override
    public List<SubtitlesStream> getSubtitlesDefault() throws IOException, ExtractionException {
        JsonArray subtitles = watch.getObject("subtitle").getArray("list");
        List<SubtitlesStream> subtitlesToReturn = new ArrayList<>();
        for(int i = 0; i< subtitles.size();i++){
            JsonObject subtitlesStream = subtitles.getObject(i);
            String bccResult = getDownloader()
                    .get(subtitlesStream
                            .getString("subtitle_url")
                            .replace("http:","https:"), getHeaders()).responseBody();
            try {
                String srt = utils.bcc2srt(JsonParser.object().from(bccResult));
                SrtParser parser = new SrtParser("utf-8");
                SrtObject subtitle = parser.parse(new ByteArrayInputStream(srt.getBytes()));
                TtmlWriter writer = new TtmlWriter();
                ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
                writer.write(subtitle, outputStream);
                String ttml = outputStream.toString().replace("<styling/>","<styling>\n" +
                        "            <style xml:id=\"s1\" tts:textAlign=\"center\" tts:extent=\"90% 90%\" tts:origin=\"5% 5%\" tts:displayAlign=\"after\"/>\n" +
                        "            <style xml:id=\"s2\" tts:fontSize=\".72c\" tts:backgroundColor=\"black\" tts:color=\"white\"/>\n" +
                        "        </styling>").replace("<layout/>","<layout>\n" +
                        "            <region xml:id=\"r1\" style=\"s1\"/>\n" +
                        "        </layout>");
                subtitlesToReturn.add(new SubtitlesStream.Builder()
                        .setContent(ttml,false)
                        .setMediaFormat(MediaFormat.TTML)
                        .setLanguageCode(subtitlesStream.getString("lan").replace("ai-",""))
                        .setAutoGenerated(subtitlesStream.getInt("ai_status") != 0)
                        .build());
            } catch (JsonParserException e) {
                throw new RuntimeException(e);
            } catch (SubtitleParsingException e) {
                throw new RuntimeException(e);
            }
        }
        return subtitlesToReturn;
    }

    @Override
    public long getTimeStamp() throws ParsingException {
        try{
            return Long.parseLong(getUrl().split("#timestamp=")[1]);
        }catch (Exception e){
            return 0;
        }

    }
}
