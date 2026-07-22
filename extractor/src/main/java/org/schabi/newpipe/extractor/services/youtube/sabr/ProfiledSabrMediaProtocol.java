package org.schabi.newpipe.extractor.services.youtube.sabr;

import javax.annotation.Nonnull;

/** Profile-selected UMP envelope with native bounded header decoding. */
final class ProfiledSabrMediaProtocol implements SabrMediaProtocol {
    @Nonnull private final SabrCompatibilityProfileClient.MediaParts mediaParts;
    @Nonnull private final SabrProfileMediaHeaderMapper headerMapper;

    ProfiledSabrMediaProtocol(@Nonnull final SabrCompatibilityProfileClient client) {
        mediaParts = client.getMediaParts();
        headerMapper = new SabrProfileMediaHeaderMapper(client.getResponseMappings());
    }

    @Override public int getHeaderPartType() { return mediaParts.getHeader(); }
    @Override public int getMediaPartType() { return mediaParts.getPayload(); }
    @Override public int getEndPartType() { return mediaParts.getEnd(); }

    @Nonnull
    @Override
    public SabrMediaHeader decodeHeader(@Nonnull final byte[] payload)
            throws SabrProtocolException {
        return headerMapper.decode(payload);
    }
}
