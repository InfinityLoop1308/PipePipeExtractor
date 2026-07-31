package org.schabi.newpipe.extractor.services.youtube.sabr;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.schabi.newpipe.extractor.NewPipe;
import org.schabi.newpipe.extractor.localization.ContentCountry;
import org.schabi.newpipe.extractor.localization.Localization;
import org.schabi.newpipe.extractor.services.youtube.YoutubeSessionPoToken;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;

class YoutubeSabrProbeSessionPoTokenTest {
    private static final Localization LOCALIZATION = new Localization("en", "US");
    private static final ContentCountry CONTENT_COUNTRY = new ContentCountry("US");

    @AfterEach
    void tearDown() {
        NewPipe.setYoutubeSessionPoTokenProvider(null);
    }

    @Test
    void rejectsExplicitPoTokenWithoutVisitorData() {
        final AtomicInteger providerCalls = installAutomaticPair();

        assertThrows(IllegalArgumentException.class,
                () -> YoutubeSabrProbe.resolvePlayerIdentity(YoutubeSabrClientProfile.MWEB,
                        "2.test", LOCALIZATION, CONTENT_COUNTRY,
                        "explicit-token", null));

        assertEquals(0, providerCalls.get());
    }

    @Test
    void rejectsExplicitVisitorDataWithoutPoToken() {
        final AtomicInteger providerCalls = installAutomaticPair();

        assertThrows(IllegalArgumentException.class,
                () -> YoutubeSabrProbe.resolvePlayerIdentity(YoutubeSabrClientProfile.MWEB,
                        "2.test", LOCALIZATION, CONTENT_COUNTRY,
                        null, "explicit-visitor"));

        assertEquals(0, providerCalls.get());
    }

    @Test
    void missingExplicitPairUsesCompleteAutomaticPair() {
        final AtomicInteger providerCalls = installAutomaticPair();

        final YoutubeSabrProbe.PlayerIdentityPair resolved =
                YoutubeSabrProbe.resolvePlayerIdentity(YoutubeSabrClientProfile.MWEB,
                        "2.test", LOCALIZATION, CONTENT_COUNTRY, null, null);

        assertEquals("automatic-token", resolved.playerPoToken);
        assertEquals("automatic-visitor", resolved.visitorData);
        assertEquals(1, providerCalls.get());
    }

    @Test
    void completeExplicitPairIsUsedWithoutProvider() {
        final AtomicInteger providerCalls = installAutomaticPair();

        final YoutubeSabrProbe.PlayerIdentityPair resolved =
                YoutubeSabrProbe.resolvePlayerIdentity(YoutubeSabrClientProfile.MWEB,
                        "2.test", LOCALIZATION, CONTENT_COUNTRY,
                        "explicit-token", "explicit-visitor");

        assertEquals("explicit-token", resolved.playerPoToken);
        assertEquals("explicit-visitor", resolved.visitorData);
        assertEquals(0, providerCalls.get());
    }

    @Test
    void sabrRequestNumberStartsAtZero() {
        assertEquals("https://example.com/sabr?alr=yes&cpn=cpn&rn=0",
                YoutubeSabrProbe.withSabrSessionParameters(
                        "https://example.com/sabr", "cpn", 0));
        assertEquals("https://example.com/sabr?alr=yes&cpn=cpn&rn=1",
                YoutubeSabrProbe.withSabrSessionParameters(
                        "https://example.com/sabr?rn=99", "cpn", 1));
    }

    @Test
    void mwebSabrRequestsKeepProtobufHeaders() {
        final YoutubeSabrInfo info = new YoutubeSabrInfo(YoutubeSabrClientProfile.MWEB,
                "video", "cpn", "2.test", "visitor", "https://example.com/sabr",
                null, Collections.emptyList());

        final Map<String, List<String>> headers = YoutubeSabrProbe.buildSabrHeaders(info);

        assertEquals(Collections.singletonList("application/x-protobuf"),
                headers.get("Content-Type"));
        assertEquals(Collections.singletonList("application/vnd.yt-ump"),
                headers.get("Accept"));
        assertEquals(Collections.singletonList("identity"), headers.get("Accept-Encoding"));
        assertFalse(headers.containsKey("Origin"));
        assertFalse(headers.containsKey("Referer"));
    }

    private static AtomicInteger installAutomaticPair() {
        final AtomicInteger calls = new AtomicInteger();
        NewPipe.setYoutubeSessionPoTokenProvider((clientName, clientVersion, userAgent,
                                                  localization, contentCountry, loggedIn) -> {
            assertEquals("MWEB", clientName);
            assertEquals("2.test", clientVersion);
            assertEquals(YoutubeSabrClientProfile.MWEB.getUserAgent(), userAgent);
            calls.incrementAndGet();
            return new YoutubeSessionPoToken("automatic-visitor", "automatic-token");
        });
        return calls;
    }
}
