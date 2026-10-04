package org.schabi.newpipe.extractor.services.twitch.api;

import com.grack.nanojson.JsonObject;
import com.grack.nanojson.JsonParserException;

import org.schabi.newpipe.extractor.downloader.Downloader;
import org.schabi.newpipe.extractor.exceptions.ReCaptchaException;
import org.schabi.newpipe.extractor.services.twitch.data.api.responses.clip.TwitchClipResponseInner;
import org.schabi.newpipe.extractor.services.twitch.data.api.responses.clip.TwitchHelixClipResponseInner;
import org.schabi.newpipe.extractor.services.twitch.data.api.responses.clip.TwitchHelixClipResponseInnerEntry;
import org.schabi.newpipe.extractor.services.twitch.data.api.responses.directory.TwitchDirectoryResponseEntry;
import org.schabi.newpipe.extractor.services.twitch.data.api.responses.directory.TwitchHelixDirectoryResponseInner;
import org.schabi.newpipe.extractor.services.twitch.data.api.responses.nowLive.TwitchHelixNowLiveResponseInner;
import org.schabi.newpipe.extractor.services.twitch.data.api.responses.nowLive.TwitchNowLiveResponseEntry;
import org.schabi.newpipe.extractor.services.twitch.data.api.responses.vod.TwitchHelixVodResponse;
import org.schabi.newpipe.extractor.services.twitch.data.api.responses.vod.TwitchHelixVodResponseEntry;

import java.io.IOException;
import java.util.Objects;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

public final class AuthorizedTwitchApiClient extends TwitchBaseApiClient {

    @Nullable
    private final String helixApiToken;

    public AuthorizedTwitchApiClient(@Nullable final String helixApiToken) {
        this.helixApiToken = helixApiToken;
    }

    public TwitchHelixNowLiveResponseInner getNowLiveInformation(@Nonnull final Downloader downloader, @Nullable final String cursor) throws JsonParserException, IOException, ReCaptchaException {
        return performRawRequest(downloader, HttpRequestType.GET,
                _ -> {
                    var url = TwitchApiConstants.TWITCH_HELIX_URL + "/streams";
                    if(cursor != null)
                        url += "?after=" + cursor;
                    return url;
                },
                _ -> null,
                _ -> TwitchApiHelper.buildHelixRequestHeaders(Objects.requireNonNull(helixApiToken), TwitchApiConstants.HELIX_CLIENT_ID),
                response -> {
                    final var obj = TwitchJsonHelper.parseJsonObjectFromString(response.responseBody());
                    final var data = obj.getArray("data");
                    final var pagination = obj.getObject("pagination");

                    return new TwitchHelixNowLiveResponseInner(
                        !pagination.isEmpty(),
                        pagination.getString("cursor"),
                        data.stream().map(o -> {
                            final var innerObj = (JsonObject)o;
                            return new TwitchNowLiveResponseEntry(
                                innerObj.getString("user_name"),
                                    innerObj.getString("user_login"),
                                    innerObj.getString("user_id"),
                                    innerObj.getString("title"),
                                    innerObj.getInt("viewer_count"),
                                    innerObj.getString("thumbnail_url")
                                        .replace("{width}", "440")
                                        .replace("{height}", "248"),
                                    innerObj.getString("game_name")
                            );
                        }).toList()
                    );
                }
        );
    }

    public TwitchHelixDirectoryResponseInner GetDirectoryInformation(@Nonnull final Downloader downloader, @Nonnull final String directoryName, @Nonnull final String categoryId, @Nullable final String cursor) throws JsonParserException, IOException, ReCaptchaException {
        return performRawRequest(downloader, HttpRequestType.GET,
                _ -> {
                    var url = TwitchApiConstants.TWITCH_HELIX_URL + "/streams?game_id=" + categoryId;
                    if(cursor != null)
                        url += "&after=" + cursor;
                    return url;
                },
                _ -> null,
                _ -> TwitchApiHelper.buildHelixRequestHeaders(Objects.requireNonNull(helixApiToken), TwitchApiConstants.HELIX_CLIENT_ID),
                response -> {
                    final var obj = TwitchJsonHelper.parseJsonObjectFromString(response.responseBody());
                    final var data = obj.getArray("data");
                    final var pagination = obj.getObject("pagination");

                    return new TwitchHelixDirectoryResponseInner(
                            !pagination.isEmpty(),
                            pagination.getString("cursor"),
                            directoryName,
                            categoryId,
                            data.stream().map(o -> {
                                final var innerObj = (JsonObject)o;
                                return new TwitchDirectoryResponseEntry(
                                        innerObj.getString("title"),
                                        innerObj.getString("game_id"),
                                        innerObj.getString("user_name"),
                                        innerObj.getString("user_login"),
                                        innerObj.getString("user_id"),
                                        innerObj.getString("thumbnail_url")
                                                .replace("{width}", "440")
                                                .replace("{height}", "248"),
                                        // This does not exist in the query. We could do another query here but that would
                                        // dramatically increase load times with very litte use
                                        false,
                                        innerObj.getInt("viewer_count")
                                );
                            }).toList()
                    );
                }
        );
    }

    public TwitchHelixClipResponseInner GetClips(@Nonnull final Downloader downloader, @Nonnull final String channelId, @Nullable final String cursor) throws JsonParserException, IOException, ReCaptchaException {
        return performRawRequest(downloader, HttpRequestType.GET,
                _ -> {
                    var url = TwitchApiConstants.TWITCH_HELIX_URL + "/clips?broadcaster_id=" + channelId;
                    if(cursor != null)
                        url += "&after=" + cursor;
                    return url;
                },
                _ -> null,
                _ -> TwitchApiHelper.buildHelixRequestHeaders(Objects.requireNonNull(helixApiToken), TwitchApiConstants.HELIX_CLIENT_ID),
                response -> {
                    final var obj = TwitchJsonHelper.parseJsonObjectFromString(response.responseBody());
                    final var data = obj.getArray("data");
                    final var pagination = obj.getObject("pagination");

                    return new TwitchHelixClipResponseInner(
                            !pagination.isEmpty(),
                            pagination.getString("cursor"),
                            data.stream().map(o -> {
                                final var innerObj = (JsonObject)o;
                                return new TwitchHelixClipResponseInnerEntry(
                                        innerObj.getString("thumbnail_url"),
                                        innerObj.getString("game_id"),
                                        innerObj.getString("title"),
                                        innerObj.getString("id"),
                                        innerObj.getString("created_at"),
                                        innerObj.getString("creator_name"),
                                        innerObj.getInt("view_count"),
                                        // For some reason the api returns fractions of seconds here
                                        Math.round(innerObj.getFloat("duration"))
                                );
                            }).toArray(TwitchHelixClipResponseInnerEntry[]::new)
                    );
                }
       );
    }

    public TwitchHelixVodResponse getVods(@Nonnull final Downloader downloader, @Nonnull final String channelId, @Nullable final String cursor) throws JsonParserException, IOException, ReCaptchaException {
        return performRawRequest(downloader, HttpRequestType.GET,
                _ -> {
                    var url = TwitchApiConstants.TWITCH_HELIX_URL + "/videos?user_id=" + channelId;
                    if(cursor != null)
                        url += "&after=" + cursor;
                    return url;
                },
                _ -> null,
                _ -> TwitchApiHelper.buildHelixRequestHeaders(Objects.requireNonNull(helixApiToken), TwitchApiConstants.HELIX_CLIENT_ID),
                response -> {
                    final var obj = TwitchJsonHelper.parseJsonObjectFromString(response.responseBody());
                    final var data = obj.getArray("data");
                    final var pagination = obj.getObject("pagination");

                    return new TwitchHelixVodResponse(
                            !pagination.isEmpty(),
                            pagination.getString("cursor"),
                            data.stream().map(o -> {
                                final var innerObj = (JsonObject)o;
                                return new TwitchHelixVodResponseEntry(
                                        innerObj.getString("id"),
                                        innerObj.getString("user_name"),
                                        innerObj.getString("user_login"),
                                        innerObj.getString("user_id"),
                                        innerObj.getString("created_at"),
                                        innerObj.getString("thumbnail_url")
                                                .replace("{width}", "440")
                                                .replace("{height}", "248"),
                                        innerObj.getInt("view_count"),
                                        innerObj.getString("duration")
                                );
                            }).toArray(TwitchHelixVodResponseEntry[]::new)
                    );
                }
        );
    }
}