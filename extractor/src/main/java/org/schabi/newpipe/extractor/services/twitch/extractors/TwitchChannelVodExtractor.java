package org.schabi.newpipe.extractor.services.twitch.extractors;

import com.grack.nanojson.JsonParserException;

import org.schabi.newpipe.extractor.InfoItem;
import org.schabi.newpipe.extractor.Page;
import org.schabi.newpipe.extractor.StreamingService;
import org.schabi.newpipe.extractor.channel.ChannelTabExtractor;
import org.schabi.newpipe.extractor.downloader.Downloader;
import org.schabi.newpipe.extractor.exceptions.ExtractionException;
import org.schabi.newpipe.extractor.exceptions.ParsingException;
import org.schabi.newpipe.extractor.linkhandler.ListLinkHandler;
import org.schabi.newpipe.extractor.localization.DateWrapper;
import org.schabi.newpipe.extractor.services.twitch.Assertions;
import org.schabi.newpipe.extractor.services.twitch.TwitchUrlBuilder;
import org.schabi.newpipe.extractor.services.twitch.TwitchUrlParser;
import org.schabi.newpipe.extractor.services.twitch.api.TwitchApiClient;
import org.schabi.newpipe.extractor.services.twitch.data.TwitchChannelTabLinkType;
import org.schabi.newpipe.extractor.services.twitch.data.api.pagination.CursorBasedPagination;
import org.schabi.newpipe.extractor.services.twitch.data.api.pagination.Pagination;
import org.schabi.newpipe.extractor.services.twitch.data.api.pagination.PositionBasedPagination;
import org.schabi.newpipe.extractor.services.twitch.data.api.responses.vod.TwitchVodResponseInner;
import org.schabi.newpipe.extractor.stream.StreamInfoItem;
import org.schabi.newpipe.extractor.stream.StreamType;

import java.io.IOException;
import java.time.OffsetDateTime;
import java.util.Arrays;
import java.util.Collections;
import java.util.stream.Collectors;

import javax.annotation.Nonnull;

public final class TwitchChannelVodExtractor extends ChannelTabExtractor {
    @Nonnull
    private final TwitchApiClient apiClient;
    private TwitchVodResponseInner[] response;
    private Pagination pagination;
    private String channelName;
    private String channelId;

    public TwitchChannelVodExtractor(final @Nonnull StreamingService service,
                                     final @Nonnull ListLinkHandler linkHandler,
                                     final @Nonnull TwitchApiClient apiClient) {
        super(service, linkHandler);
        this.apiClient = apiClient;
    }

    @Nonnull
    @Override
    public InfoItemsPage<InfoItem> getInitialPage() throws IOException, ExtractionException {
        Page nextPage = null;

        if (pagination instanceof PositionBasedPagination positionBasedPagination) {
            if (positionBasedPagination.offset() + positionBasedPagination.size() < 100
                    && response.length != 0)
                nextPage = new Page(TwitchUrlBuilder.buildChannelTabUrl(
                        channelName,
                        channelId,
                        TwitchChannelTabLinkType.CLIPS,
                        positionBasedPagination.shift(100)
                ));
        }

        if (pagination instanceof CursorBasedPagination cursorBasedPagination) {
            // TODO
        }

        return new InfoItemsPage<>(Arrays.stream(response).map(res -> {
                    final var item = new StreamInfoItem(
                            getServiceId(),
                            TwitchUrlBuilder.buildVodUrlFromVodId(res.getVodId()),
                            res.getVodTitle(),
                            StreamType.POST_LIVE_STREAM
                    );
                    item.setThumbnailUrl(res.getVodThumbnailUrl());
                    try {
                        item.setUploaderName(getId());
                    } catch (ParsingException e) {
                        item.setUploaderName("");
                    }
                    item.setDuration(res.getVodLength());
                    item.setViewCount(res.getVodViewerCount());
                    item.setShortDescription(res.getGameName());
                    item.setUploadDate(new DateWrapper(OffsetDateTime.parse(res.getUploadDateTimeString())));
                    return item;
                }
        ).collect(Collectors.toList()),
                nextPage,
                Collections.emptyList());
    }

    @Override
    public InfoItemsPage<InfoItem> getPage(Page page) throws IOException, ExtractionException {
        fetchData(getDownloader(), page.getUrl());
        return getInitialPage();
    }

    private void fetchData(@Nonnull final Downloader downloader, @Nonnull final String url) throws IOException, ExtractionException {
        try {
            final var channelUrl = TwitchUrlParser.parseChannelUrl(url);
            Assertions.assertThat(() -> channelUrl.tabType() == TwitchChannelTabLinkType.VIDEOS);
            channelName = channelUrl.channelLoginName();
            channelId = channelUrl.channelId();
            pagination = channelUrl.pagination();
            response = apiClient.getUnauthorized().getTwitchVods(
                    downloader,
                    channelName,
                    ((PositionBasedPagination) pagination).offset() + ((PositionBasedPagination) pagination).size()).getData();
        } catch (JsonParserException e) {
            throw new IOException(e);
        }
    }

    @Override
    public void onFetchPage(@Nonnull Downloader downloader) throws IOException,
            ExtractionException {
        fetchData(downloader, getUrl());
    }
}
