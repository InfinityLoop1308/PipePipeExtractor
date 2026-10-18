package org.schabi.newpipe.extractor.services.peertube;

import com.grack.nanojson.JsonArray;
import com.grack.nanojson.JsonObject;
import org.schabi.newpipe.extractor.Image;
import org.schabi.newpipe.extractor.InfoItemsCollector;
import org.schabi.newpipe.extractor.Page;
import org.schabi.newpipe.extractor.exceptions.ContentNotAvailableException;
import org.schabi.newpipe.extractor.exceptions.ParsingException;
import org.schabi.newpipe.extractor.services.peertube.extractors.PeertubeChannelInfoItemExtractor;
import org.schabi.newpipe.extractor.services.peertube.extractors.PeertubePlaylistInfoItemExtractor;
import org.schabi.newpipe.extractor.services.peertube.extractors.PeertubeSepiaStreamInfoItemExtractor;
import org.schabi.newpipe.extractor.services.peertube.extractors.PeertubeStreamInfoItemExtractor;
import org.schabi.newpipe.extractor.utils.JsonUtils;
import org.schabi.newpipe.extractor.utils.Parser;
import org.schabi.newpipe.extractor.utils.Utils;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

public final class PeertubeParsingHelper {
    public static final String START_KEY = "start";
    public static final String COUNT_KEY = "count";
    public static final int ITEMS_PER_PAGE = 12;
    public static final String START_PATTERN = "start=(\\d*)";

    private PeertubeParsingHelper() {
    }

    public static void validate(final JsonObject json) throws ContentNotAvailableException {
        final String error = json.getString("error");
        if (!Utils.isBlank(error)) {
            throw new ContentNotAvailableException(error);
        }
    }

    public static OffsetDateTime parseDateFrom(final String textualUploadDate)
            throws ParsingException {
        try {
            return OffsetDateTime.ofInstant(Instant.parse(textualUploadDate), ZoneOffset.UTC);
        } catch (final DateTimeParseException e) {
            throw new ParsingException("Could not parse date: \"" + textualUploadDate + "\"", e);
        }
    }

    public static Page getNextPage(final String prevPageUrl, final long total) {
        final String prevStart;
        try {
            prevStart = Parser.matchGroup1(START_PATTERN, prevPageUrl);
        } catch (final Parser.RegexException e) {
            return null;
        }
        if (Utils.isBlank(prevStart)) {
            return null;
        }

        final long nextStart;
        try {
            nextStart = Long.parseLong(prevStart) + ITEMS_PER_PAGE;
        } catch (final NumberFormatException e) {
            return null;
        }

        if (nextStart >= total) {
            return null;
        } else {
            return new Page(prevPageUrl.replace(
                    START_KEY + "=" + prevStart, START_KEY + "=" + nextStart));
        }
    }

    /**
     * Get the id of an account, as used for the uploader ids of items.
     *
     * <p>
     * The id is {@code accounts/<name>@<host>}: the channel link handler id of the uploader URL
     * every item builds for its account. It is therefore the same on every instance the account
     * is seen from, and equal to the id of the account's channel page opened from an item.
     * </p>
     *
     * @param account an {@code account} or {@code ownerAccount} object
     * @return the id of the account, or {@code null} if the object has no name or host
     */
    @Nullable
    public static String getAccountId(@Nullable final JsonObject account) {
        if (account == null) {
            return null;
        }
        final String name = account.getString("name");
        final String host = account.getString("host");
        if (Utils.isNullOrEmpty(name) || Utils.isNullOrEmpty(host)) {
            return null;
        }
        return "accounts/" + name + "@" + host;
    }

    /**
     * Get the avatars of an owner account or a video channel.
     *
     * <p>
     * PeerTube 6.0 removed the singular {@code avatar} object every extractor used to read and
     * only serves an {@code avatars} array since then.
     * </p>
     *
     * @param baseUrl                          the base URL of the instance
     * @param ownerAccountOrVideoChannelObject an {@code account}, {@code ownerAccount} or
     *                                         {@code channel}/{@code videoChannel} object
     * @return an unmodifiable list of {@link Image}s, which may be empty
     */
    @Nonnull
    public static List<Image> getAvatarsFromOwnerAccountOrVideoChannelObject(
            @Nonnull final String baseUrl,
            @Nonnull final JsonObject ownerAccountOrVideoChannelObject) {
        return getImagesFromAvatarOrBannerArray(baseUrl,
                ownerAccountOrVideoChannelObject.getArray("avatars"));
    }

    /**
     * Get the banners of an account or a video channel.
     *
     * @param baseUrl                     the base URL of the instance
     * @param accountOrVideoChannelObject an account or video channel object
     * @return an unmodifiable list of {@link Image}s, which may be empty
     */
    @Nonnull
    public static List<Image> getBannersFromAccountOrVideoChannelObject(
            @Nonnull final String baseUrl,
            @Nonnull final JsonObject accountOrVideoChannelObject) {
        return getImagesFromAvatarOrBannerArray(baseUrl,
                accountOrVideoChannelObject.getArray("banners"));
    }

    /**
     * Convert an {@code avatars} or {@code banners} array into {@link Image}s.
     *
     * <p>
     * Every element carries an instance-relative {@code path} and, since PeerTube 8.0, an
     * absolute {@code fileUrl}, which is preferred because it also resolves for images stored on
     * an external object storage. The estimated resolution level is derived from the width.
     * </p>
     *
     * @param baseUrl              the base URL of the instance
     * @param avatarsOrBannersArray an {@code avatars} or {@code banners} array
     * @return an unmodifiable list of {@link Image}s, which may be empty
     */
    @Nonnull
    public static List<Image> getImagesFromAvatarOrBannerArray(
            @Nonnull final String baseUrl,
            @Nonnull final JsonArray avatarsOrBannersArray) {
        final List<Image> images = new ArrayList<>(avatarsOrBannersArray.size());
        for (final Object o : avatarsOrBannersArray) {
            if (!(o instanceof JsonObject)) {
                continue;
            }
            final JsonObject avatarOrBanner = (JsonObject) o;

            String url = avatarOrBanner.getString("fileUrl");
            if (Utils.isBlank(url)) {
                final String path = avatarOrBanner.getString("path");
                if (Utils.isBlank(path)) {
                    continue;
                }
                url = baseUrl + path;
            }

            final int width = avatarOrBanner.getInt("width", Image.WIDTH_UNKNOWN);
            images.add(new Image(url, avatarOrBanner.getInt("height", Image.HEIGHT_UNKNOWN), width,
                    Image.ResolutionLevel.fromHeight(width)));
        }
        return Collections.unmodifiableList(images);
    }

    /**
     * Get the thumbnails of a playlist or of a video object.
     *
     * <p>
     * A video carries a small {@code thumbnailPath} and a bigger {@code previewPath}; a playlist
     * only carries a {@code thumbnailPath}, and even that one is absent while the playlist has no
     * video yet. Both are returned when present, smallest first, so that a caller can pick the
     * size it needs and still get something when the other one is missing.
     * </p>
     *
     * @param baseUrl                    the base URL of the instance
     * @param playlistOrVideoItemObject  a playlist or video object
     * @return an unmodifiable list of {@link Image}s, which may be empty
     */
    @Nonnull
    public static List<Image> getThumbnailsFromPlaylistOrVideoItem(
            @Nonnull final String baseUrl,
            @Nonnull final JsonObject playlistOrVideoItemObject) {
        final List<Image> images = new ArrayList<>(2);

        final String thumbnailPath = playlistOrVideoItemObject.getString("thumbnailPath");
        if (!Utils.isBlank(thumbnailPath)) {
            images.add(new Image(baseUrl + thumbnailPath, Image.HEIGHT_UNKNOWN,
                    Image.WIDTH_UNKNOWN, Image.ResolutionLevel.LOW));
        }

        final String previewPath = playlistOrVideoItemObject.getString("previewPath");
        if (!Utils.isBlank(previewPath)) {
            images.add(new Image(baseUrl + previewPath, Image.HEIGHT_UNKNOWN,
                    Image.WIDTH_UNKNOWN, Image.ResolutionLevel.MEDIUM));
        }

        return Collections.unmodifiableList(images);
    }

    /**
     * Get the URL of the first image of a list, to be returned by the {@code String} image
     * getters which want the smallest known image.
     *
     * @param images a list of {@link Image}s, which may be empty
     * @return the URL of the first image, or {@code null} if the list is empty
     */
    @Nullable
    public static String getFirstImageUrl(@Nonnull final List<Image> images) {
        return images.isEmpty() ? null : images.get(0).getUrl();
    }

    /**
     * Get the URL of the biggest image of a list, to be returned by the {@code String} image
     * getters of the extractors.
     *
     * @param images a list of {@link Image}s, which may be empty
     * @return the URL of the widest image, or {@code null} if the list is empty
     */
    @Nullable
    public static String getBiggestImageUrl(@Nonnull final List<Image> images) {
        // Widths are unknown for thumbnails, so fall back on the estimated resolution level,
        // whose ordinals run from the highest quality to the lowest one
        return images.stream()
                .max(Comparator.comparingInt(Image::getWidth)
                        .thenComparing(image -> -image.getEstimatedResolutionLevel().ordinal()))
                .map(Image::getUrl)
                .orElse(null);
    }

    public static void collectStreamsFrom(final InfoItemsCollector collector,
                                          final JsonObject json,
                                          final String baseUrl) throws ParsingException {
        collectStreamsFrom(collector, json, baseUrl, false);
    }

    /**
     * Collect stream from json with collector
     *
     * @param collector the collector used to collect information
     * @param json      the file to retrieve data from
     * @param baseUrl   the base Url of the instance
     * @param sepia     if we should use PeertubeSepiaStreamInfoItemExtractor
     */
    public static void collectStreamsFrom(final InfoItemsCollector collector,
                                          final JsonObject json,
                                          final String baseUrl,
                                          final boolean sepia) throws ParsingException {
        final JsonArray contents;
        try {
            contents = (JsonArray) JsonUtils.getValue(json, "data");
        } catch (final Exception e) {
            throw new ParsingException("Unable to extract list info", e);
        }

        for (final Object c : contents) {
            if (c instanceof JsonObject) {
                JsonObject item = (JsonObject) c;

                // PeerTube playlists have the stream info encapsulated in an "video" object
                if (item.has("video")) {
                    item = item.getObject("video");
                }

                final PeertubeStreamInfoItemExtractor extractor;
                if (sepia) {
                    extractor = new PeertubeSepiaStreamInfoItemExtractor(item, baseUrl);
                } else {
                    extractor = new PeertubeStreamInfoItemExtractor(item, baseUrl);
                }
                collector.commit(extractor);
            }
        }
    }

    public static void collectItemsFrom(final InfoItemsCollector collector,
                                        final JsonObject json,
                                        final String baseUrl) throws ParsingException {
        collectItemsFrom(collector, json, baseUrl, false);
    }

    /**
     * Collect items which may be streams, playlists or channels from a JSON response.
     *
     * <p>
     * PeerTube answers with the same {@code data} envelope whatever is being listed, so the type
     * of every element is derived from its shape: playlists carry {@code videosLength} and
     * channels carry {@code followingCount}, videos carry neither.
     * </p>
     *
     * @param collector the collector used to collect information
     * @param json      the JSON response to retrieve data from
     * @param baseUrl   the base URL of the instance
     * @param sepia     if the items are SepiaSearch results, which are served by their own
     *                  instance (streams use {@link PeertubeSepiaStreamInfoItemExtractor})
     */
    public static void collectItemsFrom(final InfoItemsCollector collector,
                                        final JsonObject json,
                                        final String baseUrl,
                                        final boolean sepia) throws ParsingException {
        final JsonArray contents;
        try {
            contents = (JsonArray) JsonUtils.getValue(json, "data");
        } catch (final Exception e) {
            throw new ParsingException("Unable to extract list info", e);
        }

        for (final Object c : contents) {
            if (c instanceof JsonObject) {
                JsonObject item = (JsonObject) c;

                // PeerTube playlists have the stream info encapsulated in an "video" object
                if (item.has("video")) {
                    item = item.getObject("video");
                }

                if (item.has("videosLength")) {
                    collector.commit(new PeertubePlaylistInfoItemExtractor(item,
                            sepia ? getSepiaResultBaseUrl(item, baseUrl) : baseUrl));
                } else if (item.has("followingCount")) {
                    collector.commit(new PeertubeChannelInfoItemExtractor(item, baseUrl));
                } else if (sepia) {
                    collector.commit(new PeertubeSepiaStreamInfoItemExtractor(item, baseUrl));
                } else {
                    collector.commit(new PeertubeStreamInfoItemExtractor(item, baseUrl));
                }
            }
        }
    }

    /**
     * Get the base URL of the instance a SepiaSearch result is served by.
     *
     * <p>
     * SepiaSearch only indexes content: the result's own instance is its embed URL without the
     * embed path. Used for every kind of SepiaSearch result, so the rule exists only once.
     * </p>
     *
     * @param item     a SepiaSearch result
     * @param fallback the base URL to use if the result has no usable embed URL
     * @return the base URL of the result's instance
     */
    public static String getSepiaResultBaseUrl(final JsonObject item, final String fallback) {
        final String embedUrl = item.getString("embedUrl");
        final String embedPath = item.getString("embedPath");
        if (Utils.isNullOrEmpty(embedUrl) || Utils.isNullOrEmpty(embedPath)
                || !embedUrl.endsWith(embedPath)) {
            return fallback;
        }
        return embedUrl.substring(0, embedUrl.length() - embedPath.length());
    }

}
