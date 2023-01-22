package org.schabi.newpipe.extractor.services.niconico.extractors;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import org.schabi.newpipe.extractor.Page;
import org.schabi.newpipe.extractor.StreamingService;
import org.schabi.newpipe.extractor.downloader.Downloader;
import org.schabi.newpipe.extractor.exceptions.ExtractionException;
import org.schabi.newpipe.extractor.exceptions.ParsingException;
import org.schabi.newpipe.extractor.linkhandler.ListLinkHandler;
import org.schabi.newpipe.extractor.localization.Localization;
import org.schabi.newpipe.extractor.playlist.PlaylistExtractor;
import org.schabi.newpipe.extractor.stream.StreamInfoItem;
import org.schabi.newpipe.extractor.stream.StreamInfoItemsCollector;

import javax.annotation.Nonnull;
import java.io.IOException;

public class NiconicoSeriesExtractor extends PlaylistExtractor {
    private Elements data;
    private String uploaderName;
    private String uploaderUrl;
    private String avatar;
    private int count;
    private String name;

    public NiconicoSeriesExtractor(StreamingService service, ListLinkHandler linkHandler) {
        super(service, linkHandler);
    }

    @Override
    public void onFetchPage(@Nonnull Downloader downloader) throws IOException, ExtractionException {
        Document response = Jsoup.parse(getDownloader().get(getLinkHandler().getUrl(), Localization.DEFAULT).responseBody());
        uploaderName = response.select(".SeriesAdditionalContainer-ownerName").text();
        uploaderUrl = response.select(".SeriesAdditionalContainer-ownerName").attr("href");
        avatar = response.select(".UserIcon-image").attr("src");
        count = Integer.parseInt(response.select(".SeriesDetailContainer-bodyMeta").text().split(" video")[0].split("Total ")[1]);
        name = response.select(".SeriesDetailContainer-bodyTitle").text();
        data = response.select("div.NC-MediaObject.NC-VideoMediaObject.SeriesVideoListContainer-video > div.NC-MediaObject-main");
    }

    @Nonnull
    @Override
    public String getName() throws ParsingException {
        return name;
    }

    @Nonnull
    @Override
    public InfoItemsPage<StreamInfoItem> getInitialPage() throws IOException, ExtractionException {
        final StreamInfoItemsCollector collector = new StreamInfoItemsCollector(getServiceId());
        for(Element element: data){
            collector.commit(new NiconicoSeriesContentItemExtractor(element, uploaderUrl, uploaderName));
        }
        return new InfoItemsPage<>(collector, null);
    }

    @Override
    public InfoItemsPage<StreamInfoItem> getPage(Page page) throws IOException, ExtractionException {
        return null;
    }

    @Override
    public String getUploaderUrl() throws ParsingException {
        return uploaderUrl;
    }

    @Override
    public String getUploaderName() throws ParsingException {
        return uploaderName;
    }

    @Override
    public String getUploaderAvatarUrl() throws ParsingException {
        return avatar;
    }

    @Override
    public boolean isUploaderVerified() throws ParsingException {
        return false;
    }

    @Override
    public long getStreamCount() throws ParsingException {
        return count;
    }
}
