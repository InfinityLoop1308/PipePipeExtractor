package org.schabi.newpipe.extractor.services.niconico;

import com.grack.nanojson.JsonObject;
import com.grack.nanojson.JsonParser;
import com.grack.nanojson.JsonParserException;

import org.java_websocket.client.WebSocketClient;
import org.java_websocket.handshake.ServerHandshake;

import java.net.URI;
import java.util.Map;

public class NicoWebSocketClient  extends WebSocketClient {
    public String url;
    public NicoWebSocketClient(URI serverUri, Map<String, String> httpHeaders) {
        super(serverUri, httpHeaders);
    }

    @Override
    public void onOpen(ServerHandshake handshakedata) {
        send("{\"type\":\"startWatching\",\"data\":{\"stream\":{\"quality\":\"super_high\",\"protocol\":\"hls+fmp4\",\"latency\":\"low\",\"chasePlay\":false},\"room\":{\"protocol\":\"webSocket\",\"commentable\":true},\"reconnect\":false}}");
    }

    @Override
    public void onMessage(String message) {
        if (message.equals("{\"type\":\"ping\"}")){
            send("{\"type\":\"pong\"}");
            send("{\"type\":\"keepSeat\"}");
        }
        else {
            try {
                JsonObject data = JsonParser.object().from(message);
                if(data.getString("type").equals("stream")){
                    url = data.getObject("data").getString("uri");
                }
            } catch (JsonParserException e) {
                throw new RuntimeException(e);
            }
        }
    }

    @Override
    public void onClose(int code, String reason, boolean remote) {
        System.out.println("test");
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
}
