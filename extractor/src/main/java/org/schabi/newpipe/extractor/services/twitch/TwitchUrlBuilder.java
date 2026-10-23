package org.schabi.newpipe.extractor.services.twitch;

import org.jetbrains.annotations.Nullable;
import org.schabi.newpipe.extractor.services.twitch.data.TwitchChannelTabLinkType;
import org.schabi.newpipe.extractor.services.twitch.data.api.pagination.Pagination;
import org.schabi.newpipe.extractor.services.twitch.data.api.pagination.TwitchPaginationHelper;
import org.schabi.newpipe.extractor.services.twitch.url.UrlBuilder;

import javax.annotation.Nonnull;

public final class TwitchUrlBuilder {

    private static final String HTTPS_SCHEMA = "https";
    private static final String TWITCH_HOST = "twitch.tv";

    public static @Nonnull String buildSearchUrlFromSearchQuery(@Nonnull final String query,
                                                                @Nullable final String contentType) {
        final var builder = new UrlBuilder()
                .withSchema(HTTPS_SCHEMA)
                .withHost(TWITCH_HOST)
                .withPathSegments("search")
                .withQuery("term", query);

        if (contentType != null)
            builder.addQuery("type", contentType);

        return builder.build();
    }

    public static @Nonnull String buildChannelUrlFromChannelNameAndPagination(@Nonnull final String channelName,
                                                                              @Nonnull final String channelId,
                                                                              @Nonnull final Pagination pagination) {
        return buildChannelTabUrl(
                channelName, channelId, TwitchChannelTabLinkType.FRONT, pagination
        );
    }

    public static @Nonnull String buildStreamUrlFromChannelName(@Nonnull final String channelName,
                                                                @Nonnull final String channelId,
                                                                @Nonnull final Pagination pagination) {
        return buildChannelTabUrl(
                channelName, channelId, TwitchChannelTabLinkType.LIVE, pagination
        );
    }

    public static @Nonnull String buildCategoryUrlFromCategoryName(@Nonnull final String name,
                                                                   @Nonnull final String slug,
                                                                   @Nonnull final String id,
                                                                   @Nonnull final Pagination pagination) {
        return new UrlBuilder()
                .withSchema(HTTPS_SCHEMA)
                .withHost(TWITCH_HOST)
                .withPathSegments("directory", "category", slug)
                .addQuery("_id", id)
                .addQuery("_name", name)
                .addQuery("_pagination", TwitchPaginationHelper.createPaginationString(pagination))
                .build();
    }

    public static @Nonnull String buildClipUrlFromClipId(@Nonnull final String clipId) {
        return new UrlBuilder()
                .withSchema(HTTPS_SCHEMA)
                .withHost(TWITCH_HOST)
                .withPathSegments("clip", clipId)
                .build();
    }

    public static @Nonnull String buildVodUrlFromVodId(@Nonnull final String vodId) {
        return new UrlBuilder()
                .withSchema(HTTPS_SCHEMA)
                .withHost(TWITCH_HOST)
                .withPathSegments("videos", vodId)
                .build();
    }

    public static @Nonnull String buildChannelTabUrl(@Nonnull final String channelName,
                                                     @Nonnull final String channelId,
                                                     @Nonnull final TwitchChannelTabLinkType type,
                                                     @Nonnull final Pagination pagination) {
        final var builder = new UrlBuilder()
                .withSchema(HTTPS_SCHEMA)
                .withHost(TWITCH_HOST)
                .withQuery("_channelId", channelId)
                .addQuery("_pagination", TwitchPaginationHelper.createPaginationString(pagination))
                .withPathSegments(channelName);

        switch (type) {
            case CLIPS -> builder.addPathSegments("clips");
            case VIDEOS -> builder.addPathSegments("videos");
            case LIVE -> builder.addQuery("_type", "stream");
            case FRONT -> builder.addQuery("_type", "channel");
        }
        return builder.build();
    }

    public static @Nonnull String buildLiveKioskUrlFromPagination(final @Nonnull Pagination pagination) {
        return "live?" + TwitchPaginationHelper.createPaginationString(pagination);
    }
}
