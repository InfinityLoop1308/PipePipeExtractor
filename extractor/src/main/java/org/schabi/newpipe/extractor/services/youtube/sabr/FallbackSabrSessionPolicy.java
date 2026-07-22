package org.schabi.newpipe.extractor.services.youtube.sabr;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.concurrent.atomic.AtomicBoolean;

/** Session-local circuit breaker which falls back atomically to bundled behavior. */
public final class FallbackSabrSessionPolicy implements SabrSessionPolicy {
    public interface FailureListener {
        void onProfileDisabled(@Nonnull Throwable failure);
    }

    @Nonnull private final SabrSessionPolicy profile;
    @Nonnull private final SabrSessionPolicy fallback;
    @Nullable private final FailureListener listener;
    @Nonnull private final AtomicBoolean disabled = new AtomicBoolean();

    public FallbackSabrSessionPolicy(@Nonnull final SabrSessionPolicy profile,
                                     @Nullable final FailureListener listener) {
        this(profile, new BuiltinSabrSessionPolicy(), listener);
    }

    public FallbackSabrSessionPolicy(@Nonnull final SabrSessionPolicy profile,
                                     @Nonnull final SabrSessionPolicy fallback,
                                     @Nullable final FailureListener listener) {
        this.profile = profile;
        this.fallback = fallback;
        this.listener = listener;
    }

    @Nonnull
    @Override
    public Result evaluate(@Nonnull final State state, @Nonnull final Event event)
            throws SabrProtocolException {
        if (disabled.get()) return fallback.evaluate(state, event);
        try {
            return profile.evaluate(state, event);
        } catch (final SabrProtocolException | RuntimeException failure) {
            disable(failure);
            return fallback.evaluate(state, event);
        }
    }

    @Nonnull
    @Override
    public DemandRoute evaluateDemandRoute(@Nonnull final DemandRouteEvent event)
            throws SabrProtocolException {
        if (disabled.get()) return fallback.evaluateDemandRoute(event);
        try {
            return profile.evaluateDemandRoute(event);
        } catch (final SabrProtocolException | RuntimeException failure) {
            disable(failure);
            return fallback.evaluateDemandRoute(event);
        }
    }

    @Nonnull
    @Override
    public DemandResponseDecision evaluateDemandResponse(
            @Nonnull final DemandResponseEvent event) throws SabrProtocolException {
        if (disabled.get()) return fallback.evaluateDemandResponse(event);
        try {
            return profile.evaluateDemandResponse(event);
        } catch (final SabrProtocolException | RuntimeException failure) {
            disable(failure);
            return fallback.evaluateDemandResponse(event);
        }
    }

    @Nonnull
    @Override
    public SabrMediaProtocol getMediaProtocol() {
        if (disabled.get()) {
            return fallback.getMediaProtocol();
        }
        final SabrMediaProtocol selected = profile.getMediaProtocol();
        final int headerPartType = selected.getHeaderPartType();
        final int mediaPartType = selected.getMediaPartType();
        final int endPartType = selected.getEndPartType();
        return new SabrMediaProtocol() {
            @Override public int getHeaderPartType() { return headerPartType; }
            @Override public int getMediaPartType() { return mediaPartType; }
            @Override public int getEndPartType() { return endPartType; }

            @Nonnull
            @Override
            public SabrMediaHeader decodeHeader(@Nonnull final byte[] payload)
                    throws SabrProtocolException {
                try {
                    return selected.decodeHeader(payload);
                } catch (final SabrProtocolException | RuntimeException failure) {
                    disable(failure);
                    throw new SabrRecoverableException(
                            "SABR compatibility profile media decoding failed", failure);
                }
            }
        };
    }

    public boolean isDisabled() {
        return disabled.get();
    }

    private void disable(@Nonnull final Throwable failure) {
        if (disabled.compareAndSet(false, true) && listener != null) {
            listener.onProfileDisabled(failure);
        }
    }

    @Override
    public void close() {
        try {
            profile.close();
        } finally {
            fallback.close();
        }
    }
}
