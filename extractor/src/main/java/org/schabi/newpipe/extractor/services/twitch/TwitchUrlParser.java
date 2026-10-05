package org.schabi.newpipe.extractor.services.twitch;

import org.schabi.newpipe.extractor.services.twitch.data.TwitchChannelTabLinkType;
import org.schabi.newpipe.extractor.services.twitch.data.api.pagination.TwitchPaginationHelper;
import org.schabi.newpipe.extractor.services.twitch.data.url.CategoryUrl;
import org.schabi.newpipe.extractor.services.twitch.data.url.ChannelUrl;
import org.schabi.newpipe.extractor.services.twitch.data.url.SearchUrl;
import org.schabi.newpipe.extractor.utils.Utils;

import java.net.MalformedURLException;
import java.net.URL;
import java.util.Objects;

import javax.annotation.Nonnull;

public final class TwitchUrlParser {

    private static URL stringToUrl(@Nonnull final String urlString) {
        try {
            return new URL(urlString);
        } catch (MalformedURLException e) {
            throw new RuntimeException(e);
        }
    }

    private static @Nonnull String getRequiredQueryParameter(@Nonnull final URL url,
                                                             @Nonnull final String queryKey) {
        return Objects.requireNonNull(Utils.getQueryValue(url, queryKey));
    }

    private static @Nonnull String getPath(@Nonnull final URL url) {
        final var path = url.getPath();
        if (!path.isEmpty() && path.charAt(0) == '/')
            return path.substring(1);
        return path;
    }

    private static void ensureIsCorrectBaseUrl(@Nonnull final String urlString) {
        Assertions.assertThat(() -> Utils.removeMAndWWWFromUrl(urlString).startsWith(TwitchService.BaseUrl));
    }

    public static @Nonnull SearchUrl parseSearchUrl(@Nonnull final String urlString) {
        ensureIsCorrectBaseUrl(urlString);

        final var url = stringToUrl(urlString);

        final var contentType = Utils.getQueryValue(url, "type");

        final var contentTypes = contentType != null
                ? contentType.split(",")
                : null;

        return new SearchUrl(
                getRequiredQueryParameter(url, "term"),
                contentTypes
        );
    }

    /*
     * Parse channel url
     * e.g. https://twitch.tv/foo
     *   -> Since this link is ambiguous (could be live or frontpage) we append query _type
     *       -> _type == channel => Front
     *       -> _type == live => Live
     * other links do not have a query parameter since they can be identified
     * https://twitch.tv/foo/clips
     * https://twitch.tv/foo/videos
     * https://twitch.tv/foo/about       => not implemented
     * https://twitch.tv/foo/schedule    => not implemented
     * */
    public static @Nonnull ChannelUrl parseChannelUrl(@Nonnull final String urlString) {
        final var url = stringToUrl(urlString);

        final var path = getPath(url);
        Assertions.assertThat(() -> !path.isEmpty());

        final var pathParts = path.split("/");
        Assertions.assertThat(() -> pathParts.length == 1 || pathParts.length == 2);

        var tabType = TwitchChannelTabLinkType.FRONT;

        if (pathParts.length == 1) {
            final var queryValue = getRequiredQueryParameter(url, "_type");
            tabType = switch (queryValue) {
                case "channel" -> TwitchChannelTabLinkType.FRONT;
                case "stream" -> TwitchChannelTabLinkType.LIVE;
                default -> throw new IllegalStateException("Unexpected value: " + queryValue);
            };
        } else {
            tabType = switch (pathParts[1]) {
                case "clips" -> TwitchChannelTabLinkType.CLIPS;
                case "videos" -> TwitchChannelTabLinkType.VIDEOS;
                default -> throw new IllegalStateException("Unexpected value: " + path);
            };
        }


        return new ChannelUrl(
                pathParts[0],
                getRequiredQueryParameter(url, "_channelId"),
                tabType,
                TwitchPaginationHelper.getPaginationFromString(getRequiredQueryParameter(url,
                        "_pagination"))
        );
    }

    public static @Nonnull String parseClipIdFromClipUrl(@Nonnull final String urlString) {
        ensureIsCorrectBaseUrl(urlString);

        final var url = stringToUrl(urlString);

        Assertions.assertThat(() -> url.getQuery() == null);

        final var path = getPath(url);
        Assertions.assertThat(() -> !path.isEmpty());

        final var pathParts = path.split("/");
        Assertions.assertThat(() -> pathParts.length == 2);
        Assertions.assertThat(() -> pathParts[0].equals("clip"));

        return pathParts[1];
    }

    public static @Nonnull String parseVodIdFromVodUrl(@Nonnull final String urlString) {
        ensureIsCorrectBaseUrl(urlString);

        final var url = stringToUrl(urlString);

        Assertions.assertThat(() -> url.getQuery() == null);

        final var path = getPath(url);
        Assertions.assertThat(() -> !path.isEmpty());

        final var pathParts = path.split("/");
        Assertions.assertThat(() -> pathParts.length == 2);
        Assertions.assertThat(() -> pathParts[0].equals("videos"));
        return pathParts[1];
    }

    public static @Nonnull String parsePaginationFromKioskUrl(@Nonnull final String urlString) {
        Assertions.assertThat(() -> urlString.contains("?"));

        final var parts = urlString.split("\\?");
        Assertions.assertThat(() -> parts.length == 2);
        Assertions.assertThat(() -> parts[0].equals("live"));

        return parts[1];
    }

    public static @Nonnull CategoryUrl parseCategoryUrl(@Nonnull final String urlString) {
        ensureIsCorrectBaseUrl(urlString);

        final var url = stringToUrl(urlString);

        final var path = getPath(url);
        Assertions.assertThat(() -> !path.isEmpty());

        final var pathParts = path.split("/");
        Assertions.assertThat(() -> pathParts.length == 2 || pathParts.length == 3);
        Assertions.assertThat(() -> pathParts[0].equals("directory"));
        Assertions.assertThat(() -> pathParts[1].equals("category"));

        final var id = getRequiredQueryParameter(url, "_id");
        final var name = getRequiredQueryParameter(url, "_name");
        final var pagination = getRequiredQueryParameter(url, "_pagination");

        return new CategoryUrl(
                name,
                pathParts[2],
                id,
                TwitchPaginationHelper.getPaginationFromString(pagination));
    }
}
