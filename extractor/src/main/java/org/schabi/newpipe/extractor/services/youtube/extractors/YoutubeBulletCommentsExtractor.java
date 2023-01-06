package org.schabi.newpipe.extractor.services.youtube.extractors;

import static org.schabi.newpipe.extractor.services.youtube.YoutubeParsingHelper.getJsonPostResponse;
import static org.schabi.newpipe.extractor.services.youtube.YoutubeParsingHelper.prepareDesktopJsonBuilder;
import static org.schabi.newpipe.extractor.services.youtube.YoutubeParsingHelper.prepareIosMobileJsonBuilder;
import static org.schabi.newpipe.extractor.utils.Utils.UTF_8;

import com.grack.nanojson.JsonArray;
import com.grack.nanojson.JsonObject;
import com.grack.nanojson.JsonParser;
import com.grack.nanojson.JsonWriter;

import org.schabi.newpipe.extractor.Page;
import org.schabi.newpipe.extractor.StreamingService;
import org.schabi.newpipe.extractor.bulletComments.BulletCommentsExtractor;
import org.schabi.newpipe.extractor.bulletComments.BulletCommentsInfoItem;
import org.schabi.newpipe.extractor.bulletComments.BulletCommentsInfoItemsCollector;
import org.schabi.newpipe.extractor.downloader.Downloader;
import org.schabi.newpipe.extractor.exceptions.ExtractionException;
import org.schabi.newpipe.extractor.exceptions.ParsingException;
import org.schabi.newpipe.extractor.linkhandler.ListLinkHandler;
import org.schabi.newpipe.extractor.localization.ContentCountry;
import org.schabi.newpipe.extractor.localization.Localization;
import org.schabi.newpipe.extractor.services.youtube.WatchDataCache;
import org.schabi.newpipe.extractor.stream.StreamType;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.regex.Pattern;

import javax.annotation.Nonnull;

public class YoutubeBulletCommentsExtractor extends BulletCommentsExtractor {
    private JsonObject data;
    private String key;
    private StreamType streamType;
    private ScheduledExecutorService executor;
    private ArrayList<JsonObject> messages = new ArrayList<>();
    private ArrayList<JsonObject> SuperChatMessages = new ArrayList<>();
    private String lastContinuation;
    private ScheduledFuture<?> future;
    private boolean disabled = false;
    private long currentPlayPosition = 0;
    private boolean isLiveStream = false;
    private long lastFetchTime = 0;
    private long startTime;
    private String lastDataID;
    private String[] continuationKeyTexts = new String[]{
            "timedContinuationData", "invalidationContinuationData"
//           , "playerSeekContinuationData" , "liveChatReplayContinuationData"
    };
    private int messageCount = 0;
    private ArrayList<String> IDList= new ArrayList<>();

    public YoutubeBulletCommentsExtractor(StreamingService service, ListLinkHandler uiHandler, WatchDataCache streamType) {
        super(service, uiHandler);
        isLiveStream = streamType.streamType.equals(StreamType.LIVE_STREAM);
        startTime = streamType.startAt;
    }

    @Override
    public void onFetchPage(@Nonnull Downloader downloader) throws IOException, ExtractionException {
        String response = downloader.get(getUrl()).responseBody();
        if(!isLiveStream &&!(response.contains("Streamed live on")
                && Pattern.compile("Streamed .* ago").matcher(response).find())){
            disabled = true;
            return ;
        }
        try {
            lastContinuation = JsonParser.object().from(response.split(Pattern.quote("var ytInitialData = "))[1]
                            .split(Pattern.quote(";</script>"))[0]).getObject("contents")
                    .getObject("twoColumnWatchNextResults").getObject("conversationBar")
                    .getObject("liveChatRenderer").getArray("continuations").getObject(0)
                    .getObject("reloadContinuationData").getString("continuation");
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void fetchMessage(){
        try {
            final byte[] json = JsonWriter.string(prepareDesktopJsonBuilder(Localization.DEFAULT,
                            ContentCountry.DEFAULT)
                            .value("continuation", lastContinuation)
                            .object("currentPlayerState")
                            .value("playerOffsetMs", String.valueOf(currentPlayPosition))
                            .end()
                            .done())
                    .getBytes(UTF_8);
            JsonObject result = getJsonPostResponse("live_chat/" + 
                    (isLiveStream? "get_live_chat":
                            "get_live_chat_replay"), json, Localization.DEFAULT);
            JsonObject liveChatContinuation = result.getObject("continuationContents").getObject("liveChatContinuation");
            JsonObject lastContinuationParent = liveChatContinuation
                    .getArray("continuations").getObject(isLiveStream?0:1);
            if(isLiveStream){
                for(String i: continuationKeyTexts){
                    if(lastContinuationParent.has(i)){
                        lastContinuation = lastContinuationParent.getObject(i).getString("continuation");
                        break;
                    }
                    if(i.equals(continuationKeyTexts[1])){
                        throw new ParsingException("Failed to get continuation data");
                    }
                }
            } else {
                lastContinuation = lastContinuationParent.getObject("playerSeekContinuationData")
                        .getString("continuation");
                if(lastContinuation == null){
                    throw new ParsingException("Failed to get continuation data");
                }
            }

            JsonArray actions = liveChatContinuation.getArray("actions");
            for(int i = 0; i < actions.size(); i++){
                JsonObject item = isLiveStream?
                        actions.getObject(i).getObject("addChatItemAction").getObject("item"):
                        actions.getObject(i).getObject("replayChatItemAction")
                                .getArray("actions").getObject(0)
                                .getObject("addChatItemAction").getObject("item");
                if(item.has("liveChatTextMessageRenderer")){
                    JsonObject temp = item.getObject("liveChatTextMessageRenderer");
                    String id = temp.getString("id");
                    if(!IDList.contains(id)){
                        messages.add(temp);
                        IDList.add(id);
                    }
                } else if (item.has("liveChatPaidMessageRenderer")) {
                    JsonObject temp = item.getObject("liveChatPaidMessageRenderer");
                    String id = temp.getString("id");
                    if(!IDList.contains(id)){
                        SuperChatMessages.add(temp);
                        IDList.add(id);
                    }
                }
            }
            System.out.println("Youtube BC - "+ "messageCount: " + messages.size() + " , currentTime: "+ currentPlayPosition);
            messageCount = messages.size();
        } catch (IOException | ExtractionException e) {
            throw new RuntimeException(e);
        }
    }

    @Nonnull
    @Override
    public InfoItemsPage<BulletCommentsInfoItem> getInitialPage() throws IOException, ExtractionException {
        executor = Executors.newSingleThreadScheduledExecutor();
        future = executor.scheduleAtFixedRate(this::fetchMessage, 1000, 1000, TimeUnit.MILLISECONDS);
        return null;
    }

    @Override
    public InfoItemsPage<BulletCommentsInfoItem> getPage(Page page) throws IOException, ExtractionException {
        return null;
    }

    @Override
    public boolean isLive() {
        return true;
    }

    @Override
    public List<BulletCommentsInfoItem> getLiveMessages() throws ParsingException {
        final BulletCommentsInfoItemsCollector collector =
                new BulletCommentsInfoItemsCollector(getServiceId());
        long currentTime = new Date().getTime();
//        if(lastFetchTime != 0 && currentTime - lastFetchTime < 1000){
//            return Collections.emptyList();
//        }
//        lastFetchTime = currentTime;
//        int cnt = 0;
//        while(messages.size() > 0 && cnt <= messageCount / 3){
//            cnt++;
//            collector.commit(new YoutubeBulletCommentsInfoItemExtractor(messages.remove(0)));
//        }
        for(JsonObject item: messages){
            collector.commit(new YoutubeBulletCommentsInfoItemExtractor(item, startTime));
        }
        for(JsonObject item: SuperChatMessages){
            collector.commit(new YoutubeSuperChatInfoItemExtractor(item, startTime));
        }
        messages.clear();
        SuperChatMessages.clear();
        return collector.getItems();
    }

    @Override
    public void disconnect() {
        if(!future.isCancelled()){
            future.cancel(true);
        }
    }

    @Override
    public void reconnect() {
        if(future != null && future.isCancelled()){
            future = executor.scheduleAtFixedRate(this::fetchMessage, 1000, 1000, TimeUnit.MILLISECONDS);
        }
    }

    @Override
    public boolean isDisabled() {
        return disabled;
    }

    @Override
    public void setCurrentPlayPosition(long currentPlayPosition) {
        this.currentPlayPosition = currentPlayPosition;
    }
}
