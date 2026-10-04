package org.schabi.newpipe.extractor.services.twitch.api;

import com.grack.nanojson.JsonParserException;

import org.schabi.newpipe.extractor.downloader.Downloader;
import org.schabi.newpipe.extractor.downloader.Response;
import org.schabi.newpipe.extractor.exceptions.ReCaptchaException;
import org.schabi.newpipe.extractor.services.twitch.StringUtils;
import org.schabi.newpipe.extractor.services.twitch.TwitchUtils;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

import javax.annotation.Nonnull;

public class TwitchBaseApiClient {

    protected TwitchBaseApiClient() {}

    protected <T> T performRawRequest(@Nonnull final Downloader downloader,
                                      final HttpRequestType requestType,
                                      @Nonnull final Function<String, String> urlGenerator,
                                      @Nonnull final Function<String, String> bodyGenerator,
                                      @Nonnull final Function<String, Map<String, List<String>>> headerGenerator,
                                      @Nonnull final Function<Response, T> mapper) throws IOException, ReCaptchaException, JsonParserException {

        final var requestId = TwitchApiHelper.generateRandomRequestId();

        final var url = urlGenerator.apply(requestId);
        final var body = bodyGenerator.apply(requestId);
        final var headers = headerGenerator.apply(requestId);

        final var response = switch (requestType) {
            case GET -> downloader.get(url, headers);
            case POST -> downloader.post(url, headers, body != null
                    ? StringUtils.stringToBytes(body)
                    : null);
        };

        if (!TwitchUtils.isSuccessfulResponseCode(response.responseCode()))
            throw new ResponseCodeIsNotSuccessException(response.responseCode());

        return mapper.apply(response);
    }
}
