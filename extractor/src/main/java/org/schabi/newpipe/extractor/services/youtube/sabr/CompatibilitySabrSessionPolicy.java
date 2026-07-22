package org.schabi.newpipe.extractor.services.youtube.sabr;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/** Selects the signed WEB or MWEB compatibility variant once for a SABR session. */
public final class CompatibilitySabrSessionPolicy implements SabrSessionPolicy {
    @Nonnull private final SabrCompatibilityProfile profile;
    @Nonnull private final SabrMediaProtocol mediaProtocol = new SabrMediaProtocol() {
        @Override
        public int getHeaderPartType() {
            return selectedMediaProtocol().getHeaderPartType();
        }

        @Override
        public int getMediaPartType() {
            return selectedMediaProtocol().getMediaPartType();
        }

        @Override
        public int getEndPartType() {
            return selectedMediaProtocol().getEndPartType();
        }

        @Nonnull
        @Override
        public SabrMediaHeader decodeHeader(@Nonnull final byte[] payload)
                throws SabrProtocolException {
            return selectedMediaProtocol().decodeHeader(payload);
        }
    };
    @Nullable private SabrSessionPolicy selected;

    public CompatibilitySabrSessionPolicy(@Nonnull final SabrCompatibilityProfile profile) {
        this.profile = profile;
    }

    @Nonnull
    @Override
    public synchronized Result evaluate(@Nonnull final State state, @Nonnull final Event event)
            throws SabrProtocolException {
        return select(event).evaluate(state, event);
    }

    @Nonnull
    @Override
    public synchronized DemandRoute evaluateDemandRoute(@Nonnull final DemandRouteEvent event)
            throws SabrProtocolException {
        return requireSelected().evaluateDemandRoute(event);
    }

    @Nonnull
    @Override
    public synchronized DemandResponseDecision evaluateDemandResponse(
            @Nonnull final DemandResponseEvent event) throws SabrProtocolException {
        return requireSelected().evaluateDemandResponse(event);
    }

    @Nonnull
    @Override
    public SabrMediaProtocol getMediaProtocol() {
        return mediaProtocol;
    }

    @Nonnull
    private SabrSessionPolicy select(@Nonnull final Event event)
            throws SabrProtocolException {
        if (selected != null) {
            return selected;
        }
        if (!(event instanceof RequestEvent)) {
            throw new SabrProtocolException("SABR profile client is not selected");
        }
        final RequestEvent request = (RequestEvent) event;
        final YoutubeSabrClientProfile client = request.getProfileRequestData()
                .getClientProfile();
        final SabrCompatibilityProfileClient configured = profile.getClient(client);
        if (configured == null) {
            selected = new BuiltinSabrSessionPolicy();
            return selected;
        }
        selected = new ProfiledSabrSessionPolicy(configured);
        return selected;
    }

    @Nonnull
    private SabrSessionPolicy requireSelected() throws SabrProtocolException {
        if (selected == null) {
            throw new SabrProtocolException("SABR profile client is not selected");
        }
        return selected;
    }

    @Nonnull
    private synchronized SabrMediaProtocol selectedMediaProtocol() {
        return selected == null ? SabrMediaProtocol.builtin() : selected.getMediaProtocol();
    }

    @Override
    public synchronized void close() {
        if (selected != null) {
            selected.close();
        }
    }
}
