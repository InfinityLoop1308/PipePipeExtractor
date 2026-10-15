package org.schabi.newpipe.extractor.services.bilibili.linkHandler;

import org.junit.jupiter.api.Test;
import org.schabi.newpipe.extractor.exceptions.ParsingException;
import org.schabi.newpipe.extractor.linkhandler.ListLinkHandler;

import static org.junit.jupiter.api.Assertions.*;

class BilibiliPlaylistLinkHandlerFactoryTest {

    private final BilibiliPlaylistLinkHandlerFactory factory = new BilibiliPlaylistLinkHandlerFactory();

    @Test
    void acceptsMedialistCollectionUrl() throws ParsingException {
        String url = "https://www.bilibili.com/medialist/play/11545648?business=space_collection&business_id=649231";
        assertTrue(factory.onAcceptUrl(url));
        assertEquals("season:11545648:649231", factory.getId(url));

        ListLinkHandler handler = factory.fromUrl(url);
        assertEquals("season:11545648:649231", handler.getId());
        assertEquals("https://www.bilibili.com/medialist/play/11545648?business=space_collection&business_id=649231", handler.getUrl());
    }

    @Test
    void acceptsMedialistSeriesUrl() throws ParsingException {
        String url = "https://www.bilibili.com/medialist/play/11545648?business=space_series&business_id=12345";
        assertTrue(factory.onAcceptUrl(url));
        assertEquals("series:11545648:12345", factory.getId(url));

        ListLinkHandler handler = factory.fromUrl(url);
        assertEquals("series:11545648:12345", handler.getId());
        assertEquals("https://www.bilibili.com/medialist/play/11545648?business=space_series&business_id=12345", handler.getUrl());
    }

    @Test
    void acceptsSpaceCollectionDetailUrl() throws ParsingException {
        String url = "https://space.bilibili.com/11545648/channel/collectiondetail?sid=649231";
        assertTrue(factory.onAcceptUrl(url));
        assertEquals("season:11545648:649231", factory.getId(url));

        ListLinkHandler handler = factory.fromUrl(url);
        assertEquals("https://www.bilibili.com/medialist/play/11545648?business=space_collection&business_id=649231", handler.getUrl());
    }

    @Test
    void acceptsSpaceSeriesDetailUrl() throws ParsingException {
        String url = "https://space.bilibili.com/11545648/channel/seriesdetail?sid=12345";
        assertTrue(factory.onAcceptUrl(url));
        assertEquals("series:11545648:12345", factory.getId(url));

        ListLinkHandler handler = factory.fromUrl(url);
        assertEquals("https://www.bilibili.com/medialist/play/11545648?business=space_series&business_id=12345", handler.getUrl());
    }

    @Test
    void acceptsCustomUriSchemes() throws ParsingException {
        assertTrue(factory.onAcceptUrl("bilibili:season:11545648:649231"));
        assertEquals("season:11545648:649231", factory.getId("bilibili:season:11545648:649231"));

        assertTrue(factory.onAcceptUrl("bilibili:series:11545648:12345"));
        assertEquals("series:11545648:12345", factory.getId("bilibili:series:11545648:12345"));
    }

    @Test
    void acceptsLegacyApiUrls() throws ParsingException {
        String legacySeason = "https://api.bilibili.com/x/polymer/web-space/seasons_archives_list?mid=11545648&season_id=649231&sort_reverse=false&page_num=1&page_size=30";
        assertTrue(factory.onAcceptUrl(legacySeason));
        assertEquals("season:11545648:649231", factory.getId(legacySeason));

        String legacySeries = "https://api.bilibili.com/x/series/archives?mid=11545648&series_id=12345&only_normal=true&sort=desc&pn=1&ps=30";
        assertTrue(factory.onAcceptUrl(legacySeries));
        assertEquals("series:11545648:12345", factory.getId(legacySeries));
    }

    @Test
    void acceptsPartitionUrl() throws ParsingException {
        String partition = "https://api.bilibili.com/x/player/pagelist?bvid=BV1xx411c7mD";
        assertTrue(factory.onAcceptUrl(partition));
        assertEquals(partition, factory.getId(partition));
    }

    @Test
    void rejectsInvalidUrl() throws ParsingException {
        assertFalse(factory.onAcceptUrl("https://www.youtube.com/playlist?list=PL123"));
        assertFalse(factory.onAcceptUrl("https://space.bilibili.com/11545648/dynamic"));
        assertThrows(ParsingException.class, () -> factory.getId("https://www.youtube.com/playlist?list=PL123"));
    }

    @Test
    void testGetUrl() throws ParsingException {
        assertEquals("https://www.bilibili.com/medialist/play/11545648?business=space_collection&business_id=649231",
                factory.getUrl("season:11545648:649231", null, null));
        assertEquals("https://www.bilibili.com/medialist/play/11545648?business=space_collection&business_id=649231",
                factory.getUrl("bilibili:season:11545648:649231", null, null));
        assertEquals("https://www.bilibili.com/medialist/play/11545648?business=space_series&business_id=12345",
                factory.getUrl("series:11545648:12345", null, null));
        assertEquals("https://www.bilibili.com/medialist/play/11545648?business=space_series&business_id=12345",
                factory.getUrl("bilibili:series:11545648:12345", null, null));
    }
}
