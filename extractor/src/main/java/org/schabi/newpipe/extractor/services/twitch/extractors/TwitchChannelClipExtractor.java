package org.schabi.newpipe.extractor.services.twitch.extractors;

import com.grack.nanojson.JsonParserException;

import org.schabi.newpipe.extractor.InfoItem;
import org.schabi.newpipe.extractor.Page;
import org.schabi.newpipe.extractor.StreamingService;
import org.schabi.newpipe.extractor.channel.ChannelTabExtractor;
import org.schabi.newpipe.extractor.downloader.Downloader;
import org.schabi.newpipe.extractor.exceptions.ExtractionException;
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
import org.schabi.newpipe.extractor.services.twitch.data.api.pagination.TwitchPaginationHelper;
import org.schabi.newpipe.extractor.services.twitch.data.api.responses.clip.TwitchClipResponseInner;
import org.schabi.newpipe.extractor.services.twitch.data.api.responses.clip.TwitchHelixClipResponseInner;
import org.schabi.newpipe.extractor.stream.StreamInfoItem;
import org.schabi.newpipe.extractor.stream.StreamType;

import java.io.IOException;
import java.time.OffsetDateTime;
import java.util.Arrays;
import java.util.Collections;
import java.util.stream.Collectors;

import javax.annotation.Nonnull;

public final class TwitchChannelClipExtractor extends ChannelTabExtractor {

    @Nonnull
    private final TwitchApiClient apiClient;
    private TwitchClipResponseInner[] gqlResponse;
    private TwitchHelixClipResponseInner helixResponse;
    private Pagination pagination;
    private String channelName;
    private String channelId;

    public TwitchChannelClipExtractor(final @Nonnull StreamingService service,
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
                    && gqlResponse.length != 0)
                nextPage = new Page(TwitchUrlBuilder.buildChannelTabUrl(
                        channelName,
                        channelId,
                        TwitchChannelTabLinkType.CLIPS,
                        positionBasedPagination.shift(100)
                ));
        }

        if (pagination instanceof CursorBasedPagination) {
            if(helixResponse.hasMoreEntries())
                nextPage = new Page(TwitchUrlBuilder.buildChannelTabUrl(
                        channelName,
                        channelId,
                        TwitchChannelTabLinkType.CLIPS,
                        new CursorBasedPagination(helixResponse.cursor())
                ));
        }

        if(apiClient.getApiSettings().shouldUseHelixForClips()) {
            return new InfoItemsPage<>(Arrays.stream(helixResponse.entries()).map(res -> {
                final var item = new StreamInfoItem(getServiceId(),
                        TwitchUrlBuilder.buildClipUrlFromClipId(res.clipId()), res.clipTitle(),
                        StreamType.NONE);
                item.setThumbnailUrl(res.clipThumbnailUrl());
                item.setUploaderName(res.clipperName());
                item.setDuration(res.clipLength());
                item.setViewCount(res.clipViewerCount());
                item.setShortFormContent(true);
                item.setShortDescription("(" + res.clipperName() + ")");
                item.setUploadDate(new DateWrapper(OffsetDateTime.parse(res.uploadDateTimeString())));
                return item;
            }).collect(Collectors.toList()),
                    nextPage,
                    Collections.emptyList());
        }
        return new InfoItemsPage<>(Arrays.stream(gqlResponse).map(res -> {
            final var item = new StreamInfoItem(getServiceId(),
                    TwitchUrlBuilder.buildClipUrlFromClipId(res.clipId()), res.clipTitle(),
                    StreamType.NONE);
            item.setThumbnailUrl(res.clipThumbnailUrl());
            item.setUploaderName(res.clipperName());
            item.setDuration(res.clipLength());
            item.setViewCount(res.clipViewerCount());
            item.setShortFormContent(true);
            item.setShortDescription("(" + res.clipperName() + "), " + res.gameName());
            item.setUploadDate(new DateWrapper(OffsetDateTime.parse(res.uploadDateTimeString())));
            return item;
        }).collect(Collectors.toList()),
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
            Assertions.assertThat(() -> channelUrl.tabType() == TwitchChannelTabLinkType.CLIPS);
            channelName = channelUrl.channelLoginName();
            channelId = channelUrl.channelId();
            pagination = channelUrl.pagination();
            if(apiClient.getApiSettings().shouldUseHelixForClips()) {
                helixResponse = apiClient.getAuthorized().getClips(
                        downloader,
                        channelId,
                        ((CursorBasedPagination)pagination).cursor()
                );
            }
            else {
                gqlResponse = TwitchPaginationHelper.paginateArray(
                        apiClient.getUnauthorized().getTwitchClips(
                                downloader,
                                channelName,
                                ((PositionBasedPagination) pagination).offset() + ((PositionBasedPagination) pagination).size()
                        ).getData(), TwitchClipResponseInner.class, (PositionBasedPagination) pagination
                );
            }
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
