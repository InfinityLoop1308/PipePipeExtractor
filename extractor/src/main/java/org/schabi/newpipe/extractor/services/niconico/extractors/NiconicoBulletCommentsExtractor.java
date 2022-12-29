package org.schabi.newpipe.extractor.services.niconico.extractors;

import com.grack.nanojson.JsonObject;

import org.schabi.newpipe.extractor.Page;
import org.schabi.newpipe.extractor.StreamingService;
import org.schabi.newpipe.extractor.bulletComments.BulletCommentsExtractor;
import org.schabi.newpipe.extractor.bulletComments.BulletCommentsInfoItem;
import org.schabi.newpipe.extractor.bulletComments.BulletCommentsInfoItemsCollector;
import org.schabi.newpipe.extractor.downloader.Downloader;
import org.schabi.newpipe.extractor.exceptions.ExtractionException;
import org.schabi.newpipe.extractor.exceptions.ParsingException;
import org.schabi.newpipe.extractor.linkhandler.ListLinkHandler;
import org.schabi.newpipe.extractor.services.niconico.NicoWebSocketClient;
import org.schabi.newpipe.extractor.services.niconico.NiconicoService;

import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.List;

import javax.annotation.Nonnull;

public class NiconicoBulletCommentsExtractor extends BulletCommentsExtractor {

    private JsonObject watch;
    private NicoWebSocketClient webSocketClient;
    @Nonnull
    private final NiconicoWatchDataCache watchDataCache;
    @Nonnull
    private final NiconicoCommentsCache commentsCache;
    private boolean isLive = true;

    public NiconicoBulletCommentsExtractor(
            final StreamingService service,
            final ListLinkHandler uiHandler,
            @Nonnull final NiconicoWatchDataCache watchDataCache,
            @Nonnull final NiconicoCommentsCache commentsCache) {
        super(service, uiHandler);
        this.watchDataCache = watchDataCache;
        this.commentsCache = commentsCache;
    }

    @Override
    public void onFetchPage(@Nonnull final Downloader downloader)
            throws IOException, ExtractionException {
        if(watchDataCache.getThreadServer() == null){
            isLive = false;
            this.watch = watchDataCache.refreshAndGetWatchData(downloader, getId());
            return ;
        }
        try {
            webSocketClient =
                    new NicoWebSocketClient(new URI(watchDataCache.getThreadServer()), NiconicoService.getWebSocketHeaders());
            NicoWebSocketClient.WrappedWebSocketClient wrappedWebSocketClient = webSocketClient.getWebSocketClient();
            wrappedWebSocketClient.setThreadId(watchDataCache.getThreadId());
            watchDataCache.setThreadServer(null);
            watchDataCache.setThreadId(null);
            wrappedWebSocketClient.connect();
        } catch (URISyntaxException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public List<BulletCommentsInfoItem> getLiveMessages() throws ParsingException {
        final BulletCommentsInfoItemsCollector collector =
                new BulletCommentsInfoItemsCollector(getServiceId());
        ArrayList<JsonObject> messages = webSocketClient.getMessages();
        for(final JsonObject message:messages){
            collector.commit(new NiconicoBulletCommentsInfoItemExtractor(message, getUrl()));
        }
        return new InfoItemsPage<>(collector, null).getItems();
    }

    @Nonnull
    @Override
    public InfoItemsPage<BulletCommentsInfoItem> getInitialPage()
            throws IOException, ExtractionException {
        final BulletCommentsInfoItemsCollector collector =
                new BulletCommentsInfoItemsCollector(getServiceId());
        if(getId().contains("live.nicovideo.jp")){
            return new InfoItemsPage<>(collector, null);
        }
        for (final JsonObject comment : commentsCache
                .getComments(watch, getDownloader(), getId())) {
            collector.commit(new NiconicoBulletCommentsInfoItemExtractor(comment, getUrl()));
        }
        return new InfoItemsPage<>(collector, null);
    }

    @Override
    public InfoItemsPage<BulletCommentsInfoItem> getPage(final Page page)
            throws IOException, ExtractionException {
        return null;
    }
    @Override
    public boolean isLive() {
        return isLive;
    }

    @Override
    public void disconnect() {
        webSocketClient.getWebSocketClient().close(-1);
    }
}
