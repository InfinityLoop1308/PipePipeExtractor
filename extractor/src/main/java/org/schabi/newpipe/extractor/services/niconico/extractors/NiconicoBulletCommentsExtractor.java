package org.schabi.newpipe.extractor.services.niconico.extractors;

import com.grack.nanojson.JsonObject;

import org.schabi.newpipe.extractor.Page;
import org.schabi.newpipe.extractor.StreamingService;
import org.schabi.newpipe.extractor.bulletComments.BulletCommentsExtractor;
import org.schabi.newpipe.extractor.bulletComments.BulletCommentsInfoItem;
import org.schabi.newpipe.extractor.bulletComments.BulletCommentsInfoItemsCollector;
import org.schabi.newpipe.extractor.downloader.Downloader;
import org.schabi.newpipe.extractor.exceptions.ExtractionException;
import org.schabi.newpipe.extractor.linkhandler.ListLinkHandler;

import java.io.IOException;

import javax.annotation.Nonnull;

public class NiconicoBulletCommentsExtractor extends BulletCommentsExtractor {

    private JsonObject watch;
    @Nonnull
    private final NiconicoWatchDataCache watchDataCache;
    @Nonnull
    private final NiconicoCommentsCache commentsCache;

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
        this.watch = watchDataCache.refreshAndGetWatchData(downloader, getId());
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
}
