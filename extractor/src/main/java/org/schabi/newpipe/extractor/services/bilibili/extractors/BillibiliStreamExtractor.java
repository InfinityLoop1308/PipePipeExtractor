package org.schabi.newpipe.extractor.services.bilibili.extractors;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import com.grack.nanojson.JsonArray;
import com.grack.nanojson.JsonObject;
import com.grack.nanojson.JsonParser;
import com.grack.nanojson.JsonParserException;

import org.schabi.newpipe.extractor.InfoItem;
import org.schabi.newpipe.extractor.InfoItemExtractor;
import org.schabi.newpipe.extractor.InfoItemsCollector;
import org.schabi.newpipe.extractor.ListExtractor;
import org.schabi.newpipe.extractor.MediaFormat;
import org.schabi.newpipe.extractor.StreamingService;
import org.schabi.newpipe.extractor.downloader.Downloader;
import org.schabi.newpipe.extractor.exceptions.ExtractionException;
import org.schabi.newpipe.extractor.exceptions.ParsingException;
import org.schabi.newpipe.extractor.exceptions.ReCaptchaException;
import org.schabi.newpipe.extractor.linkhandler.LinkHandler;
import org.schabi.newpipe.extractor.services.bilibili.linkHandler.BilibiliStreamLinkHandlerFactory;
import org.schabi.newpipe.extractor.services.bilibili.utils;
import org.schabi.newpipe.extractor.stream.AudioStream;
import org.schabi.newpipe.extractor.stream.Description;
import org.schabi.newpipe.extractor.stream.StreamExtractor;
import org.schabi.newpipe.extractor.stream.StreamInfoItemsCollector;
import org.schabi.newpipe.extractor.stream.StreamType;
import org.schabi.newpipe.extractor.stream.VideoStream;

public class BillibiliStreamExtractor extends StreamExtractor {

    private JsonObject watch;
    int cid = 0;
    int duration = 0;
    String id = "";
    JsonObject page = null;
    public BillibiliStreamExtractor(StreamingService service, LinkHandler linkHandler) {
        super(service, linkHandler);
        //TODO Auto-generated constructor stub
    }

    @Override
    public String getThumbnailUrl() throws ParsingException {
        // TODO Auto-generated method stub
        return watch.getString("pic").replace("http:", "https:");
    }

    @Override
    public String getUploaderUrl() throws ParsingException {
        // TODO Auto-generated method stub
        return "https://api.bilibili.com/x/space/arc/search?pn=1&ps=10&mid="  +watch.getObject("owner").getLong("mid");
    }

    @Override
    public String getUploaderName() throws ParsingException {
        // TODO Auto-generated method stub
        return watch.getObject("owner").getString("name");
    }

    @Override
    public List<AudioStream> getAudioStreams() throws IOException, ExtractionException {
         final List<AudioStream> audioStreams = new ArrayList<>();
         String bvid = watch.getString("bvid");
         String response = getDownloader().get("https://api.bilibili.com/x/player/playurl"+"?cid="+cid+"&bvid="+bvid+"&fnval=16").responseBody();
         JsonObject responseJson = new JsonObject();
         try {
             responseJson =  JsonParser.object().from(response);
         } catch (JsonParserException e) {
             // TODO Auto-generated catch block
             e.printStackTrace();
         }
         JsonArray audioArray =responseJson.getObject("data").getObject("dash").getArray("audio") ;
         String url = audioArray.getObject(0).getString("baseUrl");
         audioStreams.add(new AudioStream.Builder().setId("bilibili-"+bvid+"-audio").setContent(url,true).setMediaFormat(MediaFormat.M4A).setAverageBitrate(192000).build());
         return audioStreams;
    }

    @Override
    public List<VideoStream> getVideoStreams() throws IOException, ExtractionException {
        return null;
    }

    @Override
    public List<VideoStream> getVideoOnlyStreams() throws IOException, ExtractionException {
        final List<VideoStream> videoStreams = new ArrayList<>();
         String bvid = watch.getString("bvid");
         String response = getDownloader().get("https://api.bilibili.com/x/player/playurl"+"?cid="+cid+"&bvid="+bvid+"&fnval=16").responseBody();
         JsonObject responseJson = new JsonObject();
         try {
             responseJson =  JsonParser.object().from(response);
         } catch (JsonParserException e) {
             // TODO Auto-generated catch block
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
         videoStreams.add(new VideoStream.Builder().setContent(url,true).setMediaFormat( MediaFormat.MPEG_4).setId("bilibili-"+bvid+"-video").setIsVideoOnly(true).setResolution("720p").build());
        return videoStreams;
    }

    @Override
    public StreamType getStreamType() throws ParsingException {
        // TODO Auto-generated method stub
        return StreamType.VIDEO_STREAM;
    }

    @Override
    public void onFetchPage(Downloader downloader) throws IOException, ExtractionException {
        String url = getLinkHandler().getOriginalUrl();
        id =  utils.getPureBV(getId());
        url = utils.getUrl(url, id);
        final String response = downloader.get(url).responseBody();
        try {
            watch = JsonParser.object().from(response).getObject("data");
        } catch (JsonParserException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        }
        page = watch.getArray("pages").getObject(Integer.parseInt(getLinkHandler().getUrl().split("p=")[1].split("&")[0])-1);
        cid = page.getInt("cid");
        duration = page.getInt("duration");
    }

    @Override
    public String getName() throws ParsingException {
        String title = watch.getString("title");
        if(watch.getArray("pages").size() > 1){
            title += " | P" + page.getInt("page") + " "+ page.getString("part");
        }
        return title;
    }
    @Override
    public long getLength(){
        return duration;
    }
    @Override
    public String getUploaderAvatarUrl (){
        return watch.getObject("owner").getString("face").replace("http:", "https:");
    }
    @Override
    public Description getDescription(){
        return new Description(watch.getString("desc"), Description.PLAIN_TEXT);
    }
    @Override
    public long getViewCount(){
        return watch.getObject("stat").getLong("view");
    }
    @Override
    public long getLikeCount(){
        return watch.getObject("stat").getLong("coin");
    }
    @Override
    public InfoItemsCollector<? extends InfoItem, ? extends InfoItemExtractor>getRelatedItems(){
        InfoItemsCollector collector = new StreamInfoItemsCollector(getServiceId());
        String response = null;
        try {
            response = getDownloader().get("https://api.bilibili.com/x/player/pagelist?bvid="+id).responseBody();
        } catch (IOException e) {
            e.printStackTrace();
        } catch (ReCaptchaException e) {
            e.printStackTrace();
        }
        try {
            JsonObject relatedJson = JsonParser.object().from(response);
            JsonArray relatedArray = relatedJson.getArray("data");
            if(relatedArray.size()== 1){
                response = getDownloader().get("https://api.bilibili.com/x/web-interface/archive/related?bvid="+ id).responseBody();
                relatedJson = JsonParser.object().from(response);
                relatedArray = relatedJson.getArray("data");
                for(int i=0;i<relatedArray.size();i++){
                    collector.commit(new BilibiliRelatedInfoItemExtractor(relatedArray.getObject(i)));
                }
                return collector;
            }
            for(int i=0;i<relatedArray.size();i++){
                collector.commit(new BilibiliRelatedInfoItemExtractor(relatedArray.getObject(i), id, getThumbnailUrl(), String.valueOf(i+1)));
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
}
