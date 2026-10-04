package org.schabi.newpipe.extractor.services.twitch.graphql;

import com.grack.nanojson.JsonArray;
import com.grack.nanojson.JsonObject;
import com.grack.nanojson.JsonWriter;

import org.schabi.newpipe.extractor.services.twitch.TwitchUtils;
import org.schabi.newpipe.extractor.services.twitch.data.api.requests.TwitchGqlRequestBuilder;

import java.util.HashMap;
import java.util.List;

import javax.annotation.Nonnull;

public final class TwitchGQLTemplates {
    private static final String STREAM_PLAYBACK_ACCESS_TOKEN_TEMPLATE = "{\"query\": \"{\\n" +
            "            streamPlaybackAccessToken(\\n" +
            "                channelName: \\\"%s\\\",\\n" +
            "                params: {\\n" +
            "                    platform: \\\"web\\\",\\n" +
            "                    playerBackend: \\\"mediaplayer\\\",\\n" +
            "                    playerType: \\\"site\\\"\\n" +
            "                }\\n" +
            "            )\\n" +
            "            {\\n" +
            "                value\\n" +
            "                signature\\n" +
            "            }\\n" +
            "        }\\n" +
            "\"}";

    private static final String STREAMER_TEMPLATE = "{\"query\": \"{\\n" +
            "  user(login: \\\"%s\\\") {\\n" +
            "    displayName\\n" +
            "    login\\n" +
            "    id\\n" +
            "    stream {\\n" +
            "      title\\n" +
            "      viewersCount\\n" +
            "    }\\n" +
            "  }\\n" +
            "}\"}";

    private static final String NOW_LIVE_TEMPLATE = "{\"query\":\"{\\n" +
            "    streams(first: %d) {\\n" +
            "        edges {\\n" +
            "        node {\\n" +
            "            id\\n" +
            "            title\\n" +
            "            viewersCount\\n" +
            "            broadcaster {\\n" +
            "                displayName\\n" +
            "                login\\n" +
            "            }\\n" +
            "            game {\\n" +
            "                name\\n" +
            "            }\\n" +
            "        }\\n" +
            "        }\\n" +
            "    }\\n" +
            "}\"}";

    public static String getSideNavTemplate(final String channelName) {
        final var recommendationMap = new HashMap<String, Object>(TwitchUtils.mapOfNullables(
            "categorySlug", null,
            "channelName", channelName,
                "clientApp" , "twilight",
                "lastCategorySlug" , null,
                "lastChannelName" , "%s",
                "location", "channel",
                "pageviewContent" , "similar_channels",
                "pageviewContentType" , null,
                "pageviewLocation" , "channel"
        ));
        recommendationMap.put("pageviewMedium" , "twitch_socialcolumn");
        recommendationMap.put("platform" , "web");
        recommendationMap.put("previousPageviewContent" , null);
        recommendationMap.put("previousPageviewContentType" , null);
        recommendationMap.put("previousPageviewLocation" , "channel");
        recommendationMap.put("previousPageviewMedium" , null);
        recommendationMap.put("referrerDomain", "www.twitch.tv");
        recommendationMap.put("viewportHeight", 1286);
        recommendationMap.put("viewportWidth", 2560);
        return JsonWriter.string(new TwitchGqlRequestBuilder(
            "7984456538921edc68de00b6822e733fb31132d5f2e79818910caf8e7532a85b",
            "SideNav"
        )
            .withVariable("creatorAnniversariesFeature", false)
            .withVariable("withFreeformTags", false)
            .withVariable("input", new JsonObject(TwitchUtils.mapOfNullables(
                "contextChannelName", channelName,
                    "recommendationContext", new JsonObject(recommendationMap)
            )))
            .build()
        );
    }

    public static String getVideoPlayerMediaSessionManagerTemplate(final String vodId) {
        return JsonWriter.string(new TwitchGqlRequestBuilder(
                        "694c36677896425624f1293c9cb5aa4d08ed813993cf84c80d13d9380721fda2",
                        "VideoPlayerMediaSessionManager"
                )
                        .withVariable("clipSlug", "")
                        .withVariable("isClip", false)
                        .withVariable("isLive", false)
                        .withVariable("isVodOrCollection", true)
                        .withVariable("vodID", vodId)
                        .build()
        );
    }

    public static String getVideoPlayerMediaSessionClipManagerTemplate(final String clipSlug) {
        return JsonWriter.string(new TwitchGqlRequestBuilder(
                        "694c36677896425624f1293c9cb5aa4d08ed813993cf84c80d13d9380721fda2",
                        "VideoPlayerMediaSessionManager"
                )
                        .withVariable("clipSlug", clipSlug)
                        .withVariable("isClip", true)
                        .withVariable("isLive", false)
                        .withVariable("isVodOrCollection", false)
                        .withVariable("vodID", "")
                        .build()
        );
    }

    public static String getPlaybackAccessTokenTemplate(final String channelName) {
        return String.format(STREAM_PLAYBACK_ACCESS_TOKEN_TEMPLATE, channelName);
    }

    public static String getStream(final String channelName) {
        return String.format(STREAMER_TEMPLATE, channelName);
    }

    public static String getSearchResult(final @Nonnull String requestId, final @Nonnull String query) {
        return JsonWriter.string(new TwitchGqlRequestBuilder(
                "a7c600111acc4d1b294eafa364600556227939e2ff88505faa73035b57a83b22",
                "SearchResultsPage_SearchResults"
            )
            .withVariable("requestID", requestId)
            .withVariable("query", query)
            .withVariable("platform", "web")
            .withVariable("options", new JsonObject(TwitchUtils.mapOfNullables(
                    "targets", null,
                    "shouldSkipDiscoveryControl", false
            )))
            .build());
    }

    public static String getNowLive(int count) {
        return String.format(NOW_LIVE_TEMPLATE, count);
    }

    public static String getChannel(final String channelName) {
        return JsonWriter.string(new JsonArray(List.of(
            new TwitchGqlRequestBuilder(
                    "0409584bcabf718836bf330c29d0ac9d9a58f9674f7684bcbfce1a3e8dcf93b2",
                    "HomeOfflineCarousel"
            )
                    .withVariable("channelLogin", channelName)
                    .withVariable("includeTrailerUpsell", false)
                    .withVariable("trailerUpsellVideoID", "601752619")
                    .build(),
            new TwitchGqlRequestBuilder("db0e7b54c5e75fcf7874cafca2dacde646344cbbd1a80a2488a7953176c87a68",
                    "ChannelAvatar"
            )
                    .withVariable("channelLogin", channelName)
                    .build(),
            new TwitchGqlRequestBuilder("fea4573a7bf2644f5b3f2cbbdcbee0d17312e48d2e55f080589d053aad353f11",
                "ChannelShell"
                )
                    .withVariable("login", channelName)
                    .build()
        )));
    }

    public static String getVods(final String channelName, final int count) {
        return JsonWriter.string(new TwitchGqlRequestBuilder(
                "67004f7881e65c297936f32c75246470629557a393788fb5a69d6d9a25a8fd5f",
                "FilterableVideoTower_Videos"
        )
                        .withVariable("includePreviewBlur", false)
                        // TODO This can actually be increase beyond this value (tested with 50). Might therefore be useful for unauthenticated pagination
                        .withVariable("limit", count)
                        .withVariable("channelOwnerLogin", channelName)
                        .withVariable("broadcastType", null)
                        .withVariable("videoSort", "TIME")
                        .build()
        );
    }

    public static String getClips(final @Nonnull String channelName, final int limit) {
        return JsonWriter.string(new TwitchGqlRequestBuilder(
                "1cd671bfa12cec480499c087319f26d21925e9695d1f80225aae6a4354f23088",
                "ClipsCards__User"
        )
                        .withVariable("login", channelName)
                        // TODO This can actually be increase beyond this value (tested with 50). Might therefore be useful for unauthenticated pagination
                        .withVariable("limit", limit)
                        .withVariable("criteria", new JsonObject(TwitchUtils.mapOfNullables(
                                "filter", "ALL_TIME",
                                "shouldFilterByDiscoverySetting", true
                        )))
                        .withVariable("cursor", null)
                .build()
        );
    }

    public static String getClipPlayback(final String clipSlug) {
        return JsonWriter.string(new TwitchGqlRequestBuilder(
                "4f35f1ac933d76b1da008c806cd5546a7534dfaff83e033a422a81f24e5991b3",
                "VideoAccessToken_Clip"
        )
                .withVariable("platform", "web")
                .withVariable("slug", clipSlug)
                .build()
        );
    }

    public static String getVodPlaybackTokenTemplate(final String vodId) {
        return JsonWriter.string(new TwitchGqlRequestBuilder(
                "ed230aa1e33e07eebb8928504583da78a5173989fadfb1ac94be06a04f3cdbe9",
                "PlaybackAccessToken"
        )
                        .withVariable("isLive", false)
                        .withVariable("login", "")
                        .withVariable("isVod", true)
                        .withVariable("vodID", vodId)
                        .withVariable("playerType", "site")
                        .withVariable("platform", "web")
                        .build()
        );
    }

    public static String getDirectoryTemplate(final String directorySlug, final int count) {
        return JsonWriter.string(new TwitchGqlRequestBuilder(
                "86bcceb4e8b1a51256ff8eed8bd8aae4acacf80d737efe904f84f3aeadf8cafd",
                "DirectoryPage_Game"
        )
                        .withVariable("imageWidth", 50)
                        .withVariable("slug", directorySlug)
                        .withVariable("sortTypeIsRecency", false)
                        // TODO This can actually be increase beyond this value (tested with 50). Might therefore be useful for unauthenticated pagination
                        .withVariable("limit", count)
                        .withVariable("includeCostreaming", false)
                        .withVariable("options", new JsonObject(TwitchUtils.mapOfNullables(
                                "sort", "RELEVANCE",
                                "recommendationsContext", new JsonObject(TwitchUtils.mapOfNullables(
                                        "platform", "web"
                                )),
                                "freeformTags", null,
                                "tags", new JsonArray(),
                                "broadcasterLanguages", new JsonArray(),
                                "systemFilters", new JsonArray()
                        )))
                        .build()
        );
    }
}
