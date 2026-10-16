package org.schabi.newpipe.extractor.services.bilibili.extractors;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.schabi.newpipe.extractor.NewPipe;
import org.schabi.newpipe.extractor.ServiceList;
import org.schabi.newpipe.extractor.downloader.CancellableCall;
import org.schabi.newpipe.extractor.downloader.Downloader;
import org.schabi.newpipe.extractor.downloader.Request;
import org.schabi.newpipe.extractor.downloader.Response;
import org.schabi.newpipe.extractor.exceptions.ParsingException;
import org.schabi.newpipe.extractor.linkhandler.ListLinkHandler;
import org.schabi.newpipe.extractor.search.filter.FilterItem;
import org.schabi.newpipe.extractor.services.bilibili.linkHandler.BilibiliChannelLinkHandlerFactory;

import javax.annotation.Nonnull;
import java.util.Collections;

import static org.junit.jupiter.api.Assertions.*;

class BilibiliChannelSortTest {

    private final BilibiliChannelLinkHandlerFactory factory = new BilibiliChannelLinkHandlerFactory();

    @BeforeAll
    static void setUp() {
        NewPipe.init(new Downloader() {
            @Override
            public Response execute(@Nonnull Request request) {
                return new Response(200, "OK", Collections.emptyMap(), "{\"code\":0,\"data\":{}}", new byte[0], request.url());
            }

            @Override
            public CancellableCall executeAsync(@Nonnull Request request, AsyncCallback callback) {
                return null;
            }
        });
    }

    @Test
    void parsesChannelIdAndDefaultUrl() throws ParsingException {
        String url = "https://space.bilibili.com/11545648";
        assertEquals("11545648", factory.getId(url));
        ListLinkHandler handler = factory.fromUrl(url);
        assertEquals("https://space.bilibili.com/11545648", handler.getUrl());
    }

    @Test
    void parsesPopularSortFromUrl() throws ParsingException {
        String url = "https://space.bilibili.com/11545648?order=click";
        assertEquals("11545648", factory.getId(url));

        ListLinkHandler handler = factory.fromUrl(url);
        assertNotNull(handler.getSortFilter());
        assertFalse(handler.getSortFilter().isEmpty());
        assertEquals(BilibiliChannelLinkHandlerFactory.SORT_POPULAR, handler.getSortFilter().get(0).getName());
        assertEquals("https://space.bilibili.com/11545648?order=click", handler.getUrl());

        BilibiliChannelExtractor extractor = new BilibiliChannelExtractor(ServiceList.BiliBili, handler);
        assertEquals("click", extractor.getSortOrder());
    }

    @Test
    void parsesLatestSortFromUrl() throws ParsingException {
        String url = "https://space.bilibili.com/11545648?order=pubdate";
        assertEquals("11545648", factory.getId(url));

        ListLinkHandler handler = factory.fromUrl(url);
        assertNotNull(handler.getSortFilter());
        assertFalse(handler.getSortFilter().isEmpty());
        assertEquals(BilibiliChannelLinkHandlerFactory.SORT_LATEST, handler.getSortFilter().get(0).getName());
        assertEquals("https://space.bilibili.com/11545648?order=pubdate", handler.getUrl());

        BilibiliChannelExtractor extractor = new BilibiliChannelExtractor(ServiceList.BiliBili, handler);
        assertEquals("pubdate", extractor.getSortOrder());
    }

    @Test
    void generatesSortUrlsFromSortFilter() throws ParsingException {
        FilterItem popular = new FilterItem(0, BilibiliChannelLinkHandlerFactory.SORT_POPULAR);
        String popularUrl = factory.getUrl("11545648", null, Collections.singletonList(popular));
        assertEquals("https://space.bilibili.com/11545648?order=click", popularUrl);

        FilterItem latest = new FilterItem(0, BilibiliChannelLinkHandlerFactory.SORT_LATEST);
        String latestUrl = factory.getUrl("11545648", null, Collections.singletonList(latest));
        assertEquals("https://space.bilibili.com/11545648?order=pubdate", latestUrl);
    }

    @Test
    void testVariedChannelUrlFormats() throws ParsingException {
        assertEquals("11545648", factory.getId("https://space.bilibili.com/11545648/video"));
        assertEquals("11545648", factory.getId("https://space.bilibili.com/11545648/video?order=click"));
        assertEquals("11545648", factory.getId("https://space.bilibili.com/11545648/channel/series"));
        assertEquals("11545648", factory.getId("https://space.bilibili.com/11545648#fav"));
        assertEquals("11545648", factory.getId("https://www.bilibili.com/space/11545648"));
        assertEquals("11545648", factory.getId("https://api.bilibili.com/x/space/wbi/arc/search?mid=11545648&pn=1"));
        assertEquals("11545648", factory.getId("11545648"));
    }
}
