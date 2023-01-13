package org.schabi.newpipe.extractor.services.bilibili.extractors;

import com.grack.nanojson.JsonObject;
import com.grack.nanojson.JsonParserException;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import org.schabi.newpipe.extractor.StreamingService;
import org.schabi.newpipe.extractor.bulletComments.BulletCommentsExtractor;
import org.schabi.newpipe.extractor.bulletComments.BulletCommentsInfoItem;
import org.schabi.newpipe.extractor.bulletComments.BulletCommentsInfoItemsCollector;
import org.schabi.newpipe.extractor.downloader.Downloader;
import org.schabi.newpipe.extractor.exceptions.ExtractionException;
import org.schabi.newpipe.extractor.linkhandler.ListLinkHandler;
import org.schabi.newpipe.extractor.services.bilibili.BilibiliWebSocketClient;
import org.schabi.newpipe.extractor.services.bilibili.WatchDataCache;
import org.schabi.newpipe.extractor.services.bilibili.utils;

import javax.annotation.Nonnull;
import java.io.IOException;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.List;

import static org.schabi.newpipe.extractor.services.bilibili.BilibiliService.LIVE_BASE_URL;
import static org.schabi.newpipe.extractor.services.bilibili.BilibiliService.QUERY_VIDEO_BULLET_COMMENTS_URL;

public class BilibiliBulletCommentsExtractor extends BulletCommentsExtractor {
    private final int cid;
    private final long roomId;
    private final long startTime;
    private Document result;
    private BilibiliWebSocketClient webSocketClient;
    private boolean isLive = false;

    public BilibiliBulletCommentsExtractor(StreamingService service, ListLinkHandler uiHandler, WatchDataCache watchDataCache) {
        super(service, uiHandler);
        cid = watchDataCache.getCid();
        roomId = watchDataCache.getRoomId();
        startTime = watchDataCache.getStartTime();
    }

    @Override
    public void onFetchPage(@Nonnull Downloader downloader) throws IOException, ExtractionException {
        if (getUrl().contains(LIVE_BASE_URL)) {
            try {
                webSocketClient = new BilibiliWebSocketClient(roomId);
                webSocketClient.getWebSocketClient().connectBlocking();
                isLive = true;
            } catch (URISyntaxException | InterruptedException e) {
                throw new RuntimeException(e);
            }
            return;
        }
        result = Jsoup.parse(new String(utils.decompress(downloader.get(
                QUERY_VIDEO_BULLET_COMMENTS_URL + cid).rawResponseBody().bytes())));
    }

    @Override
    public List<BulletCommentsInfoItem> getLiveMessages() {
        final BulletCommentsInfoItemsCollector collector =
                new BulletCommentsInfoItemsCollector(getServiceId());
        ArrayList<JsonObject> messages = webSocketClient.getMessages();
        for (final JsonObject message : messages) {
            String cmd = message.getString("cmd");
            try {
                if (cmd.equals("DANMU_MSG")) {
                    collector.commit(new BilibiliLiveBulletCommentsInfoItemExtractor(message, startTime));
                } else if (cmd.contains("SUPER_CHAT_MESSAGE")) {
                    collector.commit(new BilibiliSuperChatInfoItemExtractor(message, startTime));
                }

            } catch (JsonParserException e) {
                throw new RuntimeException(e);
            }
        }
        return new InfoItemsPage<>(collector, null).getItems();
    }

    @Nonnull
    @Override
    public InfoItemsPage<BulletCommentsInfoItem> getInitialPage() throws IOException, ExtractionException {
        final BulletCommentsInfoItemsCollector collector =
                new BulletCommentsInfoItemsCollector(getServiceId());
        if (getUrl().contains(LIVE_BASE_URL)) {
            return new InfoItemsPage<>(collector, null);
        } else {
            if (result.select("state").text().equals("1")) {
                return new InfoItemsPage<>(collector, null);
            } else {
                Elements elements = result.select("d");
                for (final Element element : elements) {
                    collector.commit(new BilibiliBulletCommentsInfoItemExtractor(element));
                }
            }
            return new InfoItemsPage<>(collector, null);
        }
    }

    @Override
    public boolean isLive() {
        return isLive;
    }

    @Override
    public void disconnect() {
        webSocketClient.disconnect();
    }

    @Override
    public void reconnect() {
        if (webSocketClient != null && webSocketClient.getWebSocketClient().isClosed()) {
            try {
                webSocketClient.wrappedReconnect();
            } catch (URISyntaxException | InterruptedException e) {
                throw new RuntimeException(e);
            }
        }
    }
}
