package org.schabi.newpipe.extractor.services.twitch.api;

import org.schabi.newpipe.extractor.services.twitch.data.api.TwitchIntegrityToken;

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.stream.Collectors;

import javax.annotation.Nonnull;

public final class TwitchApiHelper {

    // This only works for length <=32 but that is all that is needed here
    private static @Nonnull String generateRandomLowercaseString(final int length) {
        return generateRandomString(length, true);
    }

    private static @Nonnull String generateRandomString(final int length,
                                                        final boolean allLowercase) {
        var s = UUID.randomUUID().toString().replace("-", "");
        if (allLowercase)
            s = s.toLowerCase();
        return s.substring(0, length);
    }

    public static @Nonnull String generateRandomRequestId() {
        return generateRandomString(32, false);
    }

    public static @Nonnull String generateRandomSessionId() {
        return generateRandomLowercaseString(16);
    }

    public static @Nonnull Map<String, List<String>> generateIntegrityRequestHeaders(
            @Nonnull final TwitchApiDataStore dataStore,
            @Nonnull final String requestId) {
        final var headers = new HashMap<>(generateUnauthenticatedRequestHeaders(dataStore));
        headers.put("Client-Request-Id", List.of(requestId));
        return headers;
    }

    public static boolean isIntegrityTokenExpired(@Nonnull final TwitchIntegrityToken token) {
        return token.expiration() <= System.currentTimeMillis();
    }

    public static @Nonnull Map<String, List<String>> generateUnauthenticatedRequestHeaders(
            @Nonnull final TwitchApiDataStore dataStore
    ) {
        return Map.of(
                "X-Device-Id", List.of(Objects.requireNonNull(dataStore.deviceId)),
                "Client-Id", List.of(TwitchApiConstants.GQL_CLIENT_ID),
                "Client-Session-Id", List.of(Objects.requireNonNull(dataStore.sessionId)),
                "Client-Version", List.of(TwitchApiConstants.CLIENT_VERSION)
        );
    }

    public static @Nonnull Map<String, List<String>> generateAuthenticatedRequestHeaders(
            @Nonnull final TwitchApiDataStore dataStore,
            @Nonnull final String requestId
    ) {
        final var headers = new HashMap<>(generateUnauthenticatedRequestHeaders(dataStore));
        headers.put("Client-Integrity",
                List.of(Objects.requireNonNull(dataStore.integrityToken).token()));
        headers.put("Client-Request-Id", List.of(Objects.requireNonNull(requestId)));
        return headers;
    }

    public static Cookie parseCookieFromHeaderValue(final @Nonnull String headerValue) {
        final var majorParts = headerValue.split("=", 2);
        final var minorParts = majorParts[1].split(";");
        return new Cookie(
                majorParts[0],
                minorParts[0],
                Arrays
                        .stream(minorParts)
                        .skip(1)
                        .collect(Collectors.toMap(
                                part -> part.split("=")[0].trim(),
                                part -> {
                                    final var parts = part.split("=");
                                    if (parts.length == 2)
                                        return parts[1].trim();
                                    return "";
                                }
                        ))
        );
    }

    public static @Nonnull Map<String, List<String>> buildHelixRequestHeaders(@Nonnull final String helixToken,
                                                                              @Nonnull final String clientId) {
        return Map.of(
                "Authorization", List.of("Bearer " + helixToken),
                "Client-Id", List.of(clientId)
        );
    }
}
