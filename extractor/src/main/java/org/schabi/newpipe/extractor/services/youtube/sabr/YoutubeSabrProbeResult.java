package org.schabi.newpipe.extractor.services.youtube.sabr;

import javax.annotation.Nonnull;

public final class YoutubeSabrProbeResult {
    @Nonnull
    private final YoutubeSabrInfo info;
    @Nonnull
    private final SabrDecodedResponse decodedResponse;
    private final int responseCode;
    private final int responseBodyLength;
    @Nonnull
    private final String contentType;

    YoutubeSabrProbeResult(@Nonnull final YoutubeSabrInfo info,
                           @Nonnull final SabrDecodedResponse decodedResponse,
                           final int responseCode,
                           final int responseBodyLength,
                           @Nonnull final String contentType) {
        this.info = info;
        this.decodedResponse = decodedResponse;
        this.responseCode = responseCode;
        this.responseBodyLength = responseBodyLength;
        this.contentType = contentType;
    }

    @Nonnull
    public YoutubeSabrInfo getInfo() {
        return info;
    }

    @Nonnull
    public SabrDecodedResponse getDecodedResponse() {
        return decodedResponse;
    }

    public int getResponseCode() {
        return responseCode;
    }

    public int getResponseBodyLength() {
        return responseBodyLength;
    }

    @Nonnull
    public String getContentType() {
        return contentType;
    }
}
