package org.schabi.newpipe.extractor.services.twitch.api;

import com.grack.nanojson.JsonParser;
import com.grack.nanojson.JsonParserException;

import org.schabi.newpipe.extractor.downloader.Downloader;
import org.schabi.newpipe.extractor.downloader.Response;
import org.schabi.newpipe.extractor.exceptions.ReCaptchaException;
import org.schabi.newpipe.extractor.services.twitch.TwitchService;
import org.schabi.newpipe.extractor.services.twitch.TwitchUtils;
import org.schabi.newpipe.extractor.services.twitch.data.Resolution;
import org.schabi.newpipe.extractor.services.twitch.data.TwitchVideoStream;
import org.schabi.newpipe.extractor.services.twitch.data.api.TwitchIntegrityToken;
import org.schabi.newpipe.extractor.services.twitch.data.api.TwitchResponseParser;
import org.schabi.newpipe.extractor.services.twitch.data.api.responses.TwitchBaseResponse;
import org.schabi.newpipe.extractor.services.twitch.data.api.responses.directory.TwitchDirectoryResponse;
import org.schabi.newpipe.extractor.services.twitch.data.api.responses.TwitchSideNavResponse;
import org.schabi.newpipe.extractor.services.twitch.data.api.responses.channel.TwitchChannelResponse;
import org.schabi.newpipe.extractor.services.twitch.data.api.responses.clip.TwitchClipPlaybackResponse;
import org.schabi.newpipe.extractor.services.twitch.data.api.responses.clip.TwitchClipResponse;
import org.schabi.newpipe.extractor.services.twitch.data.api.responses.clip.TwitchVideoPlayerMediaSessionManagerResponse;
import org.schabi.newpipe.extractor.services.twitch.data.api.responses.nowLive.TwitchNowLiveResponse;
import org.schabi.newpipe.extractor.services.twitch.data.api.responses.playbackToken.TwitchStreamPlaybackTokenResponse;
import org.schabi.newpipe.extractor.services.twitch.data.api.responses.playbackToken.TwitchVodPlaybackTokenResponse;
import org.schabi.newpipe.extractor.services.twitch.data.api.responses.search.TwitchSearchResponse;
import org.schabi.newpipe.extractor.services.twitch.data.api.responses.stream.TwitchStreamResponse;
import org.schabi.newpipe.extractor.services.twitch.data.api.responses.vod.TwitchVodResponse;
import org.schabi.newpipe.extractor.services.twitch.graphql.TwitchGQLTemplates;

import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.Charset;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;
import java.util.regex.Pattern;

import javax.annotation.Nonnull;

public final class UnauthorizedTwitchApiClient extends TwitchBaseApiClient {

    @Nonnull
    private final TwitchApiDataStore apiDataStore = new TwitchApiDataStore();

    private static void prepareDataStore(@Nonnull final TwitchApiDataStore dataStore) {
        dataStore.sessionId = TwitchApiHelper.generateRandomSessionId();
    }

    private void refreshIntegrityToken(@Nonnull final Downloader downloader) throws IOException, ReCaptchaException, JsonParserException {
        final var integrityRequestId = TwitchApiHelper.generateRandomRequestId();
        final var response = downloader.post(
                TwitchApiConstants.TWITCH_INTEGRITY_URL,
                TwitchApiHelper.generateIntegrityRequestHeaders(apiDataStore, integrityRequestId),
                null
        );
        if (!TwitchUtils.isSuccessfulResponseCode(response.responseCode()))
            throw new IOException(response.responseBody());
        final var jsonObject = JsonParser.object().from(response.responseBody());
        apiDataStore.integrityToken = new TwitchIntegrityToken(
                jsonObject.getString("token"),
                jsonObject.getLong("expiration")
        );
    }

    private <T extends TwitchBaseResponse<?>> T performRequest(@Nonnull final Downloader downloader,
                                                               final boolean requiresAuthentication,
                                                               final HttpRequestType requestType,
                                                               @Nonnull final Function<String, String> urlGenerator,
                                                               @Nonnull final Function<String, String> bodyGenerator,
                                                               @Nonnull final Function<Response, T> mapper) throws IOException, ReCaptchaException, JsonParserException {

        if (apiDataStore.sessionId == null)
            prepareDataStore(apiDataStore);

        if (apiDataStore.deviceId == null)
            fetchDeviceId(downloader);

        if (requiresAuthentication &&
                (apiDataStore.integrityToken == null || TwitchApiHelper.isIntegrityTokenExpired(apiDataStore.integrityToken))) {
            refreshIntegrityToken(downloader);
        }

        final var mapperResult = performRawRequest(
                downloader,
                requestType,
                urlGenerator,
                bodyGenerator,
                requestId -> requiresAuthentication
                        ? TwitchApiHelper.generateAuthenticatedRequestHeaders(apiDataStore, requestId)
                        : TwitchApiHelper.generateUnauthenticatedRequestHeaders(apiDataStore),
                mapper
        );

        if (mapperResult.getErrors() != null && mapperResult.getErrors().length > 0)
            throw new TwitchApiResponseException(mapperResult.getErrors());

        return mapperResult;
    }

    private void fetchDeviceId(@Nonnull final Downloader downloader) throws IOException, ReCaptchaException {
        final var response = downloader.get(TwitchService.BaseUrl);
        if (!TwitchUtils.isSuccessfulResponseCode(response.responseCode()))
            throw new IOException(response.responseBody());

        final var headers = response.responseHeaders().get("set-cookie");

        final var targetCookie = headers
                .stream()
                .map(TwitchApiHelper::parseCookieFromHeaderValue)
                .filter(cookie -> cookie.name().equals("unique_id_durable"))
                .findAny();

        if (targetCookie.isEmpty())
            throw new IOException("Unable to get cookie \"unique_id_durable\"");

        apiDataStore.deviceId = targetCookie.get().value();
    }

    public @Nonnull TwitchSearchResponse getSearchResponse(@Nonnull final Downloader downloader, @Nonnull final String query) throws IOException, ReCaptchaException, JsonParserException {
        return performRequest(
                downloader, false, HttpRequestType.POST,
                _ -> TwitchApiConstants.TWITCH_GQL_URL,
                requestId -> TwitchGQLTemplates.getSearchResult(requestId, query),
                response -> TwitchResponseParser.parseFromJson(TwitchJsonHelper.parseJsonObjectFromString(response.responseBody()), TwitchSearchResponse.class)
        );
    }

    public @Nonnull TwitchNowLiveResponse getNowLiveInformation(final @Nonnull Downloader downloader, final int count) throws IOException, ReCaptchaException, JsonParserException {
        return performRequest(downloader, false, HttpRequestType.POST,
                _ -> TwitchApiConstants.TWITCH_GQL_URL,
                _ -> TwitchGQLTemplates.getNowLive(count),
                response -> TwitchResponseParser.parseFromJson(TwitchJsonHelper.parseJsonObjectFromString(response.responseBody()), TwitchNowLiveResponse.class)
        );
    }

    public @Nonnull TwitchStreamResponse getStreamInformation(final @Nonnull Downloader downloader, final @Nonnull String channelName) throws IOException, ReCaptchaException, JsonParserException {
        return performRequest(downloader, false, HttpRequestType.POST,
                _ -> TwitchApiConstants.TWITCH_GQL_URL,
                _ -> TwitchGQLTemplates.getStream(channelName),
                response -> TwitchResponseParser.parseFromJson(TwitchJsonHelper.parseJsonObjectFromString(response.responseBody()), TwitchStreamResponse.class)
        );
    }

    public @Nonnull TwitchStreamPlaybackTokenResponse getPlaybackToken(final @Nonnull Downloader downloader, final @Nonnull String channelName) throws IOException, ReCaptchaException, JsonParserException {
        return performRequest(downloader, false, HttpRequestType.POST,
                _ -> TwitchApiConstants.TWITCH_GQL_URL,
                _ -> TwitchGQLTemplates.getPlaybackAccessTokenTemplate(channelName),
                response -> TwitchResponseParser.parseFromJson(TwitchJsonHelper.parseJsonObjectFromString(response.responseBody()), TwitchStreamPlaybackTokenResponse.class)
        );
    }

    public @Nonnull TwitchVideoStream[] getM3U8PlaybackUrl(final @Nonnull Downloader downloader, final @Nonnull String channelName, final @Nonnull String playbackTokenSignature, final @Nonnull String playbackTokenValue) throws IOException, ReCaptchaException, JsonParserException {
        final var masterM3U8Url = TwitchApiConstants.TWITCH_USHER_URL + "/api/v2/channel/hls/" + channelName + ".m3u8?allow_source=true&allow_audio_only=false&allow_spectre=true&p=1003155&platform=web&player=twitchweb&supported_codecs=av1,h265,h264&playlist_include_framerate=false&sig=" + playbackTokenSignature + "&token=" + URLEncoder.encode(playbackTokenValue, Charset.defaultCharset());
        return performRawRequest(downloader, HttpRequestType.GET,
                _ -> masterM3U8Url,
                _ -> null,
                _ -> TwitchApiHelper.generateUnauthenticatedRequestHeaders(apiDataStore),
                response -> {
                    final var content = response.responseBody();
                    final var lineQueue = new ArrayDeque<>(List.of(content.split("\n")));
                    final var commentBuffer = new ArrayList<String>();
                    final var streams = new ArrayList<TwitchVideoStream>();
                    while (!lineQueue.isEmpty()) {
                        final var line = lineQueue.pop();
                        if (line.startsWith("#")) commentBuffer.add(line);
                        else {
                            final var resolutionPattern = Pattern.compile("RESOLUTION=(\\d+)x(\\d+)");
                            Resolution resolution = null;
                            for (final var bufferedLine : commentBuffer) {
                                final var matcher = resolutionPattern.matcher(bufferedLine);
                                if (matcher.find()) resolution = new Resolution(
                                        Integer.parseInt(matcher.group(1)),
                                        Integer.parseInt(matcher.group(2))
                                );
                            }
                            if (resolution != null)
                                streams.add(new TwitchVideoStream(
                                        resolution,
                                        line
                                ));
                            commentBuffer.clear();
                        }
                    }
                    return streams.toArray(TwitchVideoStream[]::new);
                });
    }

    public @Nonnull TwitchChannelResponse getTwitchChannel(final @Nonnull Downloader downloader, final @Nonnull String channelName) throws IOException, ReCaptchaException, JsonParserException {
        return performRequest(downloader, false, HttpRequestType.POST,
                _ -> TwitchApiConstants.TWITCH_GQL_URL,
                _ -> TwitchGQLTemplates.getChannel(channelName),
                response -> TwitchResponseParser.parseFromJson(TwitchJsonHelper.createJsonObjectFromArray(TwitchJsonHelper.parseJsonArrayFromString(response.responseBody())), TwitchChannelResponse.class)
        );
    }

    public @Nonnull TwitchVodResponse getTwitchVods(final @Nonnull Downloader downloader, final @Nonnull String channelName, final int count) throws IOException, ReCaptchaException, JsonParserException {
        return performRequest(downloader, false, HttpRequestType.POST,
                _ -> TwitchApiConstants.TWITCH_GQL_URL,
                _ -> TwitchGQLTemplates.getVods(channelName, count),
                response -> TwitchResponseParser.parseFromJson(TwitchJsonHelper.parseJsonObjectFromString(response.responseBody()), TwitchVodResponse.class)
        );
    }


    public @Nonnull TwitchClipResponse getTwitchClips(final @Nonnull Downloader downloader, final @Nonnull String channelName, final int count) throws IOException, ReCaptchaException, JsonParserException {
        return performRequest(downloader, false, HttpRequestType.POST,
                _ -> TwitchApiConstants.TWITCH_GQL_URL,
                _ -> TwitchGQLTemplates.getClips(channelName, count),
                response -> TwitchResponseParser.parseFromJson(TwitchJsonHelper.parseJsonObjectFromString(response.responseBody()), TwitchClipResponse.class)
        );
    }

    public @Nonnull TwitchClipPlaybackResponse getClipPlaybackToken(final @Nonnull Downloader downloader, final @Nonnull String clipSlug) throws IOException, ReCaptchaException, JsonParserException {
        return performRequest(downloader, false, HttpRequestType.POST,
                _ -> TwitchApiConstants.TWITCH_GQL_URL,
                _ -> TwitchGQLTemplates.getClipPlayback(clipSlug),
                response -> TwitchResponseParser.parseFromJson(TwitchJsonHelper.parseJsonObjectFromString(response.responseBody()), TwitchClipPlaybackResponse.class)
        );
    }

    public @Nonnull TwitchVodPlaybackTokenResponse getVodPlaybackToken(final @Nonnull Downloader downloader, final @Nonnull String vodId) throws IOException, ReCaptchaException, JsonParserException {
        return performRequest(downloader, false, HttpRequestType.POST,
                _ -> TwitchApiConstants.TWITCH_GQL_URL,
                _ -> TwitchGQLTemplates.getVodPlaybackTokenTemplate(vodId),
                response -> TwitchResponseParser.parseFromJson(TwitchJsonHelper.parseJsonObjectFromString(response.responseBody()), TwitchVodPlaybackTokenResponse.class)
        );
    }

    public @Nonnull TwitchVideoStream[] getM3U8VodPlaybackUrl(final @Nonnull Downloader downloader, final @Nonnull String vodId, final @Nonnull String playbackTokenSignature, final @Nonnull String playbackTokenValue) throws IOException, ReCaptchaException, JsonParserException {
        return performRawRequest(downloader, HttpRequestType.GET,
                sessionId -> TwitchApiConstants.TWITCH_USHER_URL + "/vod/v2/" + vodId + ".m3u8?acmb=eyJBcHBWZXJzaW9uIjoiMTFhYmE4MTYtZThlMi00M2MyLWJmOWUtNTNkMTNiM2EyYWEyIiwiQ2xpZW50QXBwIjoid2ViIn0%3D&allow_source=true&enable_score=true&include_unavailable=false&lang=en&multigroup_video=false&p=4876112&platform=web&player_backend=mediaplayer&play_session_id=" + sessionId + "&player_version=1.50.0-rc.4&playlist_include_framerate=true&reassignments_supported=true&sig=" + playbackTokenSignature + "&token=" + URLEncoder.encode(playbackTokenValue, Charset.defaultCharset()),
                _ -> null,
                _ -> TwitchApiHelper.generateUnauthenticatedRequestHeaders(apiDataStore),
                response -> {
                    final var content = response.responseBody();
                    final var lineQueue = new ArrayDeque<>(List.of(content.split("\n")));
                    final var commentBuffer = new ArrayList<String>();
                    final var streams = new ArrayList<TwitchVideoStream>();
                    while (!lineQueue.isEmpty()) {
                        final var line = lineQueue.pop();
                        if (line.startsWith("#")) commentBuffer.add(line);
                        else {
                            final var resolutionPattern = Pattern.compile("RESOLUTION=(\\d+)x(\\d+)");
                            Resolution resolution = null;
                            for (final var bufferedLine : commentBuffer) {
                                final var matcher = resolutionPattern.matcher(bufferedLine);
                                if (matcher.find()) resolution = new Resolution(
                                        Integer.parseInt(matcher.group(1)),
                                        Integer.parseInt(matcher.group(2))
                                );
                            }
                            if (resolution != null)
                                streams.add(new TwitchVideoStream(
                                        resolution,
                                        line
                                ));
                            commentBuffer.clear();
                        }
                    }
                    return streams.toArray(TwitchVideoStream[]::new);
                }
        );
    }

    public @Nonnull TwitchVideoPlayerMediaSessionManagerResponse getTwitchVideoPlayerMediaSessionManager(final @Nonnull Downloader downloader, final @Nonnull String vodId) throws IOException, ReCaptchaException, JsonParserException {
        return performRequest(downloader, false, HttpRequestType.POST,
                _ -> TwitchApiConstants.TWITCH_GQL_URL,
                _ -> TwitchGQLTemplates.getVideoPlayerMediaSessionManagerTemplate(vodId),
                response -> TwitchResponseParser.parseFromJson(TwitchJsonHelper.parseJsonObjectFromString(response.responseBody()), TwitchVideoPlayerMediaSessionManagerResponse.class)
        );
    }

    public @Nonnull TwitchVideoPlayerMediaSessionManagerResponse getTwitchVideoPlayerMediaSessionClipManager(final @Nonnull Downloader downloader, final @Nonnull String clipSlug) throws IOException, ReCaptchaException, JsonParserException {
        return performRequest(downloader, false, HttpRequestType.POST,
                _ -> TwitchApiConstants.TWITCH_GQL_URL,
                _ -> TwitchGQLTemplates.getVideoPlayerMediaSessionClipManagerTemplate(clipSlug),
                response -> TwitchResponseParser.parseFromJson(TwitchJsonHelper.parseJsonObjectFromString(response.responseBody()), TwitchVideoPlayerMediaSessionManagerResponse.class)
        );
    }

    public @Nonnull TwitchSideNavResponse getTwitchSideNavResponse(final @Nonnull Downloader downloader, final @Nonnull String channelName) throws IOException, ReCaptchaException, JsonParserException {
        return performRequest(downloader, false, HttpRequestType.POST,
                _ -> TwitchApiConstants.TWITCH_GQL_URL,
                _ -> TwitchGQLTemplates.getSideNavTemplate(channelName),
                response -> TwitchResponseParser.parseFromJson(TwitchJsonHelper.parseJsonObjectFromString(response.responseBody()), TwitchSideNavResponse.class)
        );
    }

    public @Nonnull TwitchDirectoryResponse getTwitchCategory(@Nonnull final Downloader downloader, @Nonnull final String categorySlug, final int count) throws JsonParserException, IOException, ReCaptchaException {
        return performRequest(downloader, false, HttpRequestType.POST,
                _ -> TwitchApiConstants.TWITCH_GQL_URL,
                _ -> TwitchGQLTemplates.getDirectoryTemplate(categorySlug, count),
                response -> TwitchResponseParser.parseFromJson(TwitchJsonHelper.parseJsonObjectFromString(response.responseBody()), TwitchDirectoryResponse.class)
        );
    }
}