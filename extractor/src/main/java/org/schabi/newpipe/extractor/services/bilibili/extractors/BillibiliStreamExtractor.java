package org.schabi.newpipe.extractor.services.bilibili.extractors;

import com.grack.nanojson.JsonArray;
import com.grack.nanojson.JsonObject;
import com.grack.nanojson.JsonParser;
import com.grack.nanojson.JsonParserException;
import org.schabi.newpipe.extractor.*;
import org.schabi.newpipe.extractor.downloader.Downloader;
import org.schabi.newpipe.extractor.exceptions.*;
import org.schabi.newpipe.extractor.linkhandler.LinkHandler;
import org.schabi.newpipe.extractor.localization.DateWrapper;
import org.schabi.newpipe.extractor.services.bilibili.BilibiliService;
import org.schabi.newpipe.extractor.services.bilibili.WatchDataCache;
import org.schabi.newpipe.extractor.services.bilibili.linkHandler.BilibiliChannelLinkHandlerFactory;
import org.schabi.newpipe.extractor.services.bilibili.utils;
import org.schabi.newpipe.extractor.services.niconico.NiconicoService;
import org.schabi.newpipe.extractor.stream.*;

import javax.annotation.Nonnull;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.*;

import static org.schabi.newpipe.extractor.services.bilibili.BilibiliService.*;

public class BillibiliStreamExtractor extends StreamExtractor {

    private JsonObject watch;
    int cid = 0;
    int duration = 0;
    JsonObject page = null;
    String bvid;
    WatchDataCache watchDataCache;
    private boolean isRoundPlay;
    private JsonObject playData;
    private String liveUrl;
    private JsonObject dataObject;
    private final List<VideoStream> videoOnlyStreams = new ArrayList<>();
    private final List<AudioStream> audioStreams = new ArrayList<>();
    private long playTime;
    private String currentRoundTitle;
    private long nextTimestamp;
    private int isPremiumContent;
    private JsonArray relatedPaidItems;
    private int isPaid;
    private JsonObject premiumData;
    private JsonArray dataArray = new JsonArray();

    public BillibiliStreamExtractor(StreamingService service, LinkHandler linkHandler, WatchDataCache watchDataCache) {
        super(service, linkHandler);
        this.watchDataCache = watchDataCache;
    }

    public static <T> T[] repeat(T[] arr, int newLength) {
        T[] dup = Arrays.copyOf(arr, newLength);
        for (int last = arr.length; last != 0 && last < newLength; last <<= 1) {
            System.arraycopy(dup, 0, dup, last, Math.min(last << 1, newLength) - last);
        }
        return dup;
    }

    @Override
    public String getThumbnailUrl() throws ParsingException {
        if(getStreamType() == StreamType.LIVE_STREAM){
            return watch.getString("cover_from_user").replace("http:", "https:");
        }
        if(isPremiumContent == 1){
            return watch.getString("cover").replace("http:", "https:");
        }
        return watch.getString("pic").replace("http:", "https:");
    }

    @Override
    public String getUploaderUrl() throws ParsingException {
        if(isPremiumContent == 1){
            if(premiumData.getObject("up_info")  == null){
                return null;
            }
            return BilibiliChannelLinkHandlerFactory.baseUrl + premiumData.getObject("up_info").getLong("mid");
        }
        if(getStreamType() == StreamType.LIVE_STREAM) {
            return BilibiliChannelLinkHandlerFactory.baseUrl + watch.getLong("uid");
        }
        return BilibiliChannelLinkHandlerFactory.baseUrl + watch.getObject("owner").getLong("mid");
    }

    @Override
    public String getUploaderName() throws ParsingException {
        if(isPremiumContent == 1){
            return Optional.ofNullable(premiumData.getObject("up_info").getString("uname")).orElse("BiliBili");
        }
        if(getStreamType() == StreamType.LIVE_STREAM) {
            return watch.getString("uname");
        }
        return watch.getObject("owner").getString("name");
    }

    @Override
    public List<AudioStream> getAudioStreams() throws IOException, ExtractionException {
        List<AudioStream>result = new ArrayList<>();
        int length = videoOnlyStreams.size();
        for(AudioStream item: audioStreams){
            result.addAll(Collections.nCopies(length, item));
        }
        return result;
    }

    public List<AudioStream> getAudioStreamsForDownloader() throws IOException, ExtractionException {
        if(getStreamType() == StreamType.LIVE_STREAM && !isRoundPlay){
            return null;
        }
        JsonArray audioObjects = dataObject.getArray("audio");
        if(dataObject.getObject("dolby").getArray("audio").size() != 0){
            audioObjects.addAll(0, dataObject.getObject("dolby").getArray("audio"));
        }
        if(dataObject.getObject("flac").getObject("audio").size() != 0){
            audioObjects.add(0, dataObject.getObject("flac").getObject("audio"));
        }
        ArrayList<AudioStream> audioStreamsForDownloader = new ArrayList<>();
        for (int i = 0; i < audioObjects.size(); i++) {
            JsonObject audioObject = audioObjects.getObject(i);
            audioStreamsForDownloader.add(new AudioStream.Builder().setId("bilibili-"+bvid+"-audio")
                    .setContent(audioObject.getString("baseUrl"),true)
                    .setMediaFormat(MediaFormat.M4A).setQuality(getBitrate(audioObject.getInt("id"))).build());
        }
        return audioStreamsForDownloader;
    }

    @Override
    public List<VideoStream> getVideoOnlyStreams() throws IOException, ExtractionException {
        int videoSize = videoOnlyStreams.size();
        return Arrays.asList(repeat(videoOnlyStreams.toArray(new VideoStream[videoSize]),
                audioStreams.size() * videoSize));
    }

    @Override
    public List<VideoStream> getVideoStreams() throws IOException, ExtractionException {
        if(isRoundPlay || getStreamType() != StreamType.LIVE_STREAM){
            if(dataArray.size() > 0){
                final List<VideoStream> videoStreams = new ArrayList<>();
                for (int j = 0; j < dataArray.size(); j++) {
                    String resolution = BilibiliService.getResolution(dataArray.getObject(j).getInt("id"));
                    videoStreams.add(new VideoStream.Builder().setContent(dataArray.getObject(j).getString("base_url"),true)
                            .setId("bilibili-"+ watch.getLong("cid"))
                            .setIsVideoOnly(false).setResolution(resolution)
                            .setDeliveryMethod(DeliveryMethod.PROGRESSIVE_HTTP).build());
                    JsonArray backupUrl = dataArray.getObject(j).getArray("backup_url");
                    for(int i = 0 ;i < backupUrl.size(); i++){
                        videoStreams.add(new VideoStream.Builder().setContent(backupUrl.getString(i),true)
                                .setId("bilibili-"+ watch.getLong("cid"))
                                .setIsVideoOnly(false).setResolution(resolution)
                                .setDeliveryMethod(DeliveryMethod.PROGRESSIVE_HTTP).build());
                    }
                }
                return videoStreams;
            }
            return null;
        }
        final List<VideoStream> videoStreams = new ArrayList<>();

        videoStreams.add(new VideoStream.Builder().setContent(liveUrl,true)
                .setId("bilibili-"+watch.getLong("uid") +"-live")
                .setIsVideoOnly(false).setResolution("720p")
                .setDeliveryMethod(DeliveryMethod.PROGRESSIVE_HTTP).build());
        return videoStreams;
    }

    @Nonnull
    @Override
    public String getHlsUrl() throws ParsingException {
        return "";
    }

    public void buildStreams() throws ExtractionException {
        if (dataObject.getArray("audio").size() == 0) {
            dataArray = dataObject.getArray("video");
            return;
        }
        buildVideoOnlyStreamsArray();
        buildAudioStreamsArray();
    }

    public void buildAudioStreamsArray()throws ExtractionException{
        if(getStreamType() == StreamType.LIVE_STREAM && !isRoundPlay){
            return ;
        }
        boolean hasDolby = dataObject.getObject("dolby").getArray("audio").size() != 0;
        JsonObject audioObject = dataObject.getArray("audio").getObject(0);
        if(dataObject.getObject("flac").getObject("audio").size() != 0){
            audioObject = dataObject.getObject("flac").getObject("audio");
        }
        if(hasDolby){
            audioObject = dataObject.getObject("dolby").getArray("audio").getObject(0);
        }
        JsonArray backupUrls = audioObject.getArray("backupUrl");
        audioStreams.add(new AudioStream.Builder().setId("bilibili-"+bvid+"-audio")
                .setContent(audioObject.getString("baseUrl"),true)
                .setMediaFormat(hasDolby?MediaFormat.M4A:null).setAverageBitrate(192000).build());
        for(int j = 0; j < backupUrls.size();j++){
            audioStreams.add(new AudioStream.Builder().setId("bilibili-"+bvid+"-audio")
                    .setContent(backupUrls.getString(j),true)
                    .setMediaFormat(MediaFormat.M4A).setAverageBitrate(192000).build());
        }
    }

    public void buildVideoOnlyStreamsArray() throws ExtractionException {
        if(getStreamType() == StreamType.LIVE_STREAM && !isRoundPlay){
            return ;
        }
        JsonArray videoArray = dataObject.getArray("video") ;
        ArrayList<VideoStream> h264Streams = new ArrayList<>();
        for(int i=0; i< videoArray.size(); i++){
             JsonObject object = videoArray.getObject(i);
             int code = object.getInt("id");
             boolean isH264 = object.getString("codecs").contains("avc");
             String resolution = BilibiliService.getResolution(code);
             (isH264?h264Streams:videoOnlyStreams).add(new VideoStream.Builder().setContent(object.getString("baseUrl"),true)
                     .setMediaFormat( MediaFormat.MPEG_4).setId("bilibili-"+bvid+"-video")
                     .setIsVideoOnly(true).setResolution(resolution).build());
             JsonArray backupUrls = object.getArray("backupUrl");
             for(int j = 0; j< backupUrls.size();j++){
                 (isH264?h264Streams:videoOnlyStreams).add(new VideoStream.Builder().setContent(backupUrls.getString(j),true)
                         .setMediaFormat( MediaFormat.MPEG_4).setId("bilibili-"+bvid+"-video")
                         .setIsVideoOnly(true).setResolution(resolution).build());
             }
         }
        // append h264 streams to videoOnlyStreams
        videoOnlyStreams.addAll(0, h264Streams);
    }

    @Override
    public StreamType getStreamType() throws ParsingException {
        if(getLinkHandler().getOriginalUrl().contains("live.bilibili.com")){
            return StreamType.LIVE_STREAM;
        }
        return StreamType.VIDEO_STREAM;
    }

    @Override
    public void onFetchPage(@Nonnull Downloader downloader) throws IOException, ExtractionException {
        watchDataCache.init(getUrl());
        if(getStreamType() == StreamType.LIVE_STREAM){
            String response = downloader.get("https://api.live.bilibili.com/room/v1/Room/room_init?id=" + getId()).responseBody();
            try {
                JsonObject responseJson = JsonParser.object().from(response);
                JsonObject data = responseJson.getObject("data");
                String uid = String.valueOf(data.getLong("uid"));
                if(data.size() == 0){
                    throw new ExtractionException("Can not get live room info. Error message: " + responseJson.getString("msg"));
                }
                response = downloader.get("https://api.live.bilibili.com/room/v1/Room/get_status_info_by_uids?uids[]=" + uid).responseBody();
                watch = JsonParser.object().from(response).getObject("data").getObject(uid);
                watchDataCache.setRoomId(data.getLong("room_id"));
                watchDataCache.setStartTime(data.getLong("live_time"));
                switch (data.getInt("live_status")){
                    case 0:
                        throw new LiveNotStartException("Live is not started.");
                    case 2:
                        long timestamp = getOriginalUrl().contains("timestamp=")?
                                Long.parseLong(getOriginalUrl().split("timestamp=")[1].split("&")[0]): new Date().getTime();
                        isRoundPlay = true;
                        response = downloader.get(
                                String.format("https://api.live.bilibili.com/live/getRoundPlayVideo?room_id=%s&a=%s&type=flv",
                                        data.getLong("room_id"), timestamp)).responseBody();
                        responseJson = JsonParser.object().from(response).getObject("data");
                        if(responseJson.getLong("cid") < 0){
                            throw new ContentNotAvailableException("Round playing is not available at this moment.");
                        }
                        playTime = responseJson.getLong("play_time");
                        currentRoundTitle = responseJson.getString("title");
                        currentRoundTitle = currentRoundTitle.split("-")[1] + currentRoundTitle.split("-")[2];
                        bvid = responseJson.getString("bvid");
                        response = getDownloader().get("https://api.bilibili.com/x/player/playurl"+"?cid="
                                + responseJson.getLong("cid")+"&bvid="+ bvid
                                +"&fnval=2000&qn=120&fourk=1", getHeaders()).responseBody();
                        playData =  JsonParser.object().from(response);
                        dataObject = playData.getObject("data").getObject("dash");
                        buildStreams();
                        nextTimestamp = timestamp + dataObject.getLong("duration") * 1000;
                    case 1:
                        response = getDownloader().get("https://api.live.bilibili.com/room/v1/Room/playUrl?qn=10000&platform=web&cid=" + getId(), getHeaders()).responseBody();
                        liveUrl = JsonParser.object().from(response).getObject("data").getArray("durl").getObject(0).getString("url");
                }
            } catch (JsonParserException e) {
                e.printStackTrace();
            }
            return ;
        }
        if (getUrl().contains("bangumi/play/")) {
            isPremiumContent = 1;
            int type = getId().startsWith("ss") ? 0 : 1;
            String response;
            try{
                 response = downloader.get("https://api.bilibili.com/pgc/view/web/season?"
                        + (type == 0? "season_id=": "ep_id=")+ getId().substring(2), getHeaders()).responseBody();
            }catch (Exception e){
                throw new ContentNotAvailableException("Unknown reason");
            }

            try {
                premiumData = JsonParser.object().from(response).getObject("result");
                relatedPaidItems = premiumData
                        .getArray("episodes");
                if(type == 0){
                    watch = relatedPaidItems.getObject(0);
                } else {
                    for(int i = 0; i < relatedPaidItems.size(); i++){
                        JsonObject temp = relatedPaidItems.getObject(i);
                        if(temp.getString("share_url").endsWith(getId())){
                            watch = temp;
                            break;
                        }
                    }
                    if(watch == null){
                        throw new ExtractionException("Not found id in series data");
                    }
                }
                bvid = watch.getString("bvid");
                cid = watch.getInt("cid");
                watchDataCache.setCid(cid);
                watchDataCache.setBvid(bvid);
                duration = watch.getInt("duration") / 1000;
                isPaid = watch.getObject("rights").getInt("pay");
            } catch (JsonParserException e) {
                e.printStackTrace();
            }
        } else {
            String url = getLinkHandler().getOriginalUrl();
            bvid =  utils.getPureBV(getId());
            url = utils.getUrl(url, bvid);
            String response = downloader.get(url).responseBody();
            try {
                watch = JsonParser.object().from(response).getObject("data");
            } catch (JsonParserException e) {
                e.printStackTrace();
            }
            page = watch.getArray("pages").getObject(Integer.parseInt(getLinkHandler().getUrl().split("p=")[1].split("&")[0])-1);
            cid = page.getInt("cid");
            watchDataCache.setCid(cid);
            duration = page.getInt("duration");
            isPaid = watch.getObject("rights").getInt("pay");
        }

        String baseUrl = isPremiumContent != 1 ? FREE_VIDEO_BASE_URL : PAID_VIDEO_BASE_URL;
        Map<String, List<String>> headers = getHeaders();
        if(ServiceList.BiliBili.getTokens() != null){
            headers.put("Cookie", Collections.singletonList(ServiceList.BiliBili.getTokens()));
        }
        String response = getDownloader().get(baseUrl + "?cid=" + cid + "&bvid=" + bvid + "&fnval=2000&qn=120&fourk=1", headers).responseBody();
        try {
            playData =  JsonParser.object().from(response);
            switch (playData.getInt("code")){
                case 0:
                    break;
                case -10403:
                default:
                    String message = playData.getString("message");
                    if(message.contains("地区")){
                        throw new GeographicRestrictionException(message);
                    }
                    throw new ContentNotAvailableException(message);
            }
            JsonObject dataParentObject = (isPremiumContent == 1 ? playData.getObject("result") : playData.getObject("data"));
            dataObject = dataParentObject.getObject("dash");
            if(dataObject.size() == 0){
                throw new PaidContentException("Paid content");
                //dataArray = dataParentObject.getArray("durl");
            } else {
                buildStreams();
            }
            if(isPaid == 1 && videoOnlyStreams.size() + audioStreams.size() == 0){
                throw new PaidContentException("Paid content");
            }
        } catch (JsonParserException e) {
            e.printStackTrace();
        }
    }

    @Nonnull
    @Override
    public String getName() throws ParsingException {
        if (isRoundPlay){
            return getUploaderName() + "的投稿视频轮播";
        }
        String title = isPremiumContent == 1? watch.getString("share_copy"):watch.getString("title");

        if(getStreamType() != StreamType.LIVE_STREAM&& isPremiumContent != 1 && watch.getArray("pages").size() > 1){
            title = "P" + page.getInt("page") + " "+ page.getString("part") + " | " + title;
        }
        return title;
    }
    @Override
    public long getLength() throws ParsingException {
        if(getStreamType() == StreamType.LIVE_STREAM ){
            return -1;
        }
        return duration;
    }
    @Nonnull
    @Override
    public String getUploaderAvatarUrl () throws ParsingException {
        if(getStreamType() == StreamType.LIVE_STREAM){
            return watch.getString("face").replace("http:", "https:");
        }
        if(isPremiumContent == 1){
            try{
                return premiumData.getObject("up_info").getString("avatar").replace("http:", "https:");
            } catch (Exception e) {
                return "https://i2.hdslb.com/bfs/face/0c84b9f4ad546d3f20324809d45fc439a2a8ddab.jpg@240w_240h_1c_1s.webp";
            }

        }
        return watch.getObject("owner").getString("face").replace("http:", "https:");
    }
    @Nonnull
    @Override
    public Description getDescription() throws ParsingException {
        if(getStreamType() == StreamType.LIVE_STREAM){
            return null;
        }
        if(isPremiumContent == 1){
            return new Description(premiumData.getString("evaluate"), Description.PLAIN_TEXT);
        }
        return new Description(watch.getString("desc"), Description.PLAIN_TEXT);
    }


    @Override
    public long getViewCount() throws ParsingException {
        if(getStreamType() == StreamType.LIVE_STREAM){
            return watch.getLong("online");
        }
        if (isPremiumContent == 1){
            return premiumData.getObject("stat").getLong("views");
        }
        return watch.getObject("stat").getLong("view");
    }
    @Override
    public long getLikeCount() throws ParsingException {
        if(getStreamType() == StreamType.LIVE_STREAM){
            return -1;
        }
        if (isPremiumContent == 1){
            return premiumData.getObject("stat").getLong("coins");
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
        } catch (IOException | ReCaptchaException | JsonParserException e) {
            e.printStackTrace();
        }
        return tags;
    }

    @Override
    public InfoItemsCollector<? extends InfoItem, ? extends InfoItemExtractor>getRelatedItems() throws ParsingException {
        InfoItemsCollector collector = new StreamInfoItemsCollector(getServiceId());
        if(isPremiumContent == 1){
            for(int i = 0 ; i< relatedPaidItems.size(); i++){
                collector.commit(new BilibiliPremiumContentInfoItemExtractor(relatedPaidItems.getObject(i)));
            }
            return collector;
        }
        if(getStreamType() == StreamType.LIVE_STREAM){
            if(isRoundPlay){
                collector.commit(new BilibiliSameContentInfoItemExtractor(getUploaderName() + "的投稿视频轮播",
                        getUrl() + "?timestamp=" + nextTimestamp, getThumbnailUrl()
                        , getUploaderName(), getViewCount()));
                return collector;
            }
            return null;
        }
        String response = null;
        try {
            response = getDownloader().get("https://api.bilibili.com/x/player/pagelist?bvid="+bvid, getHeaders()).responseBody();
        } catch (IOException | ReCaptchaException e) {
            e.printStackTrace();
        }
        try {
            JsonObject relatedJson = JsonParser.object().from(response);
            JsonArray relatedArray = relatedJson.getArray("data");
            if(relatedArray.size()== 1){
                response = getDownloader().get("https://api.bilibili.com/x/web-interface/archive/related?bvid="+ bvid, getHeaders()).responseBody();
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
                                relatedArray.getObject(i), bvid, getThumbnailUrl(), String.valueOf(i+1), getUploaderName(), watch.getLong("ctime")));
            }
        } catch (JsonParserException | ParsingException | IOException | ReCaptchaException e) {
            e.printStackTrace();
        }
        return collector;
    }
    @SuppressWarnings("SimpleDateFormat")
    @Override
    public String getTextualUploadDate() throws ParsingException {
        if(getStreamType().equals(StreamType.LIVE_STREAM)){
            return null;
        }
        if(isPremiumContent == 1){
            return new SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(new Date(watch.getLong("pub_time")*1000));
        }
        return new SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(new Date(watch.getLong("ctime")*1000));
    }

    @Override
    public DateWrapper getUploadDate() throws ParsingException {
        if(getStreamType().equals(StreamType.LIVE_STREAM)){
            return null;
        }
        return new DateWrapper(LocalDateTime.parse(
                Objects.requireNonNull(getTextualUploadDate()), DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")).atOffset(ZoneOffset.ofHours(+8)));
    }
    @Nonnull
    @Override
    public List<SubtitlesStream> getSubtitlesDefault() throws IOException, ExtractionException {
        if(getStreamType().equals(StreamType.LIVE_STREAM) || isPremiumContent == 1){
            return new ArrayList<>();
        }
        JsonArray subtitles = watch.getObject("subtitle").getArray("list");
        int p = Integer.parseInt(getLinkHandler().getUrl().split("p=")[1].split("&")[0]);
        if(p > 1){
            try {
                subtitles  = JsonParser.object().from(getDownloader()
                        .get(GET_SUBTITLE_META_URL + "?cid=" + cid + "&bvid=" + bvid, getHeaders())
                        .responseBody()).getObject("data").getObject("subtitle").getArray("subtitles");
            } catch (JsonParserException e) {
                throw new RuntimeException(e);
            }
        }
        List<SubtitlesStream> subtitlesToReturn = new ArrayList<>();
        for(int i = 0; i< subtitles.size();i++){
            JsonObject subtitlesStream = subtitles.getObject(i);
            String bccResult = getDownloader()
                    .get((p>1?"https:":"") + subtitlesStream
                            .getString("subtitle_url")
                            .replace("http:","https:"), getHeaders()).responseBody();
            try {
                subtitlesToReturn.add(new SubtitlesStream.Builder()
                        .setContent(utils.bcc2srt(JsonParser.object().from(bccResult)),false)
                        .setMediaFormat(MediaFormat.SRT)
                        .setLanguageCode(subtitlesStream.getString("lan").replace("ai-",""))
                        .setAutoGenerated(subtitlesStream.getInt("ai_status") != 0)
                        .build());
            } catch (JsonParserException e) {
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
            return isRoundPlay? playTime : 0;
        }

    }

    @Override
    public boolean isSupportComments() throws ParsingException {
        return getStreamType() != StreamType.LIVE_STREAM;
    }

    @Override
    public boolean isSupportRelatedItems() throws ParsingException {
        return getStreamType() != StreamType.LIVE_STREAM;
    }

    @Override
    public boolean isRoundPlayStream() {
        return isRoundPlay;
    }

    @Override
    public long getStartAt() throws ParsingException {
        if(getStreamType() == StreamType.LIVE_STREAM && !isRoundPlay){
            return watch.getLong("live_time") * 1000;
        }
        return -1;
    }
}
