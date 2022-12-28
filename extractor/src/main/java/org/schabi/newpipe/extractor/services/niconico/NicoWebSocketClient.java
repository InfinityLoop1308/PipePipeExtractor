package org.schabi.newpipe.extractor.services.niconico;

import com.grack.nanojson.JsonObject;
import com.grack.nanojson.JsonParser;
import com.grack.nanojson.JsonParserException;

import org.java_websocket.client.WebSocketClient;
import org.java_websocket.handshake.ServerHandshake;

import java.net.URI;
import java.util.ArrayList;
import java.util.Map;

import javax.annotation.Nonnull;

public class NicoWebSocketClient  extends WebSocketClient {
    public String url;
    public int type;
    private String serverUrl;
    private String threadId;
    private ArrayList<JsonObject> messages  = new ArrayList<JsonObject>();
    private int retryTimes = 0;
    private boolean shouldSkip = false;

    public NicoWebSocketClient(URI serverUri, Map<String, String> httpHeaders) {
        super(serverUri, httpHeaders);
        if(serverUri.toString().contains("wss://a.live2.nicovideo.jp/unama/wsapi/v2/watch/")){
            type = 0;
        }else{
            type = 1;
        }
    }
    public String getLivePingMessage(int page,@Nonnull String threadId){
        String result = "[{\"ping\":{\"content\":\"rs:0\"}},{\"ping\":{\"content\":\"ps:0\"}},{\"thread\":{\"thread\":\"M.fzhnxMC0bcSz8sz1cyGVdA\",\"version\":\"20061206\",\"user_id\":\"guest\",\"res_from\":-150,\"with_global\":1,\"scores\":1,\"nicoru\":0}},{\"ping\":{\"content\":\"pf:0\"}},{\"ping\":{\"content\":\"rf:0\"}}]";
        result = result.replace("rs:0","rs:"+page).replace("rf:0","rf:"+page)
                .replace("ps:0","ps:"+page*5).replace("pf:0","pf:"+page*5)
                .replace("M.fzhnxMC0bcSz8sz1cyGVdA", threadId);
        return result;
    }

    @Override
    public void onOpen(ServerHandshake handshakedata) {
        switch (type){
            case 0:
                send("{\"type\":\"startWatching\",\"data\":{\"stream\":{\"quality\":\"super_high\",\"protocol\":\"hls+fmp4\",\"latency\":\"low\",\"chasePlay\":false},\"room\":{\"protocol\":\"webSocket\",\"commentable\":true},\"reconnect\":false}}");
                break;
            case 1:
                shouldSkip = true;
                send(getLivePingMessage(retryTimes, threadId));
        }
    }

    @Override
    public void onMessage(String message) {
        if (message.equals("{\"type\":\"ping\"}")){
            send("{\"type\":\"pong\"}");
            send("{\"type\":\"keepSeat\"}");
        }
        else if(message.equals("{\"ping\":{\"content\":\"pf:0\"}}")){
            shouldSkip = false;
        }
        else {
            try {
                JsonObject data = JsonParser.object().from(message);
                if (data.has("chat") && !shouldSkip
                        && !data.getObject("chat").getString("content").startsWith("/nicoad")
                        && !data.getObject("chat").getString("content").startsWith("/info")) {
                    messages.add(data.getObject("chat"));
                }else if(data.has("type")){
                    if(data.getString("type").equals("stream")){
                        url = data.getObject("data").getString("uri");
                    }else if(data.getString("type").equals("room")){
                        serverUrl = data.getObject("data").getObject("messageServer").getString("uri");
                        threadId = data.getObject("data").getString("threadId");
                    }
                }
            } catch (JsonParserException ignored) {
                ;
            }
        }
    }

    @Override
    public void onClose(int code, String reason, boolean remote) {
        if(type != 0 && code != -1){
            retryTimes ++;
            reconnect();
        }
    }

    @Override
    public void onError(Exception ex) {
        System.out.println(ex);
    }
    public boolean hasUrl(){
        return url !=  null;
    }

    public String getUrl() {
        return url;
    }

    public String getServerUrl() {
        return serverUrl;
    }

    public String getThreadId() {
        return threadId;
    }

    public void setThreadId(String threadId) {
        this.threadId = threadId;
    }

    public ArrayList<JsonObject> getMessages() {
        ArrayList<JsonObject> temp = (ArrayList<JsonObject>) messages.clone();
        messages.clear();
        return temp;
    }
}
