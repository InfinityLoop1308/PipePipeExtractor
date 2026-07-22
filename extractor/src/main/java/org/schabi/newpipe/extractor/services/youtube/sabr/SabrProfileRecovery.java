package org.schabi.newpipe.extractor.services.youtube.sabr;

import javax.annotation.Nonnull;
import java.io.DataOutputStream;
import java.io.IOException;

/** Bounded recovery thresholds which may change without changing the SABR engine. */
public final class SabrProfileRecovery {
    public static final int MAXIMUM_OMISSIONS_LIMIT = 16;
    public static final long MAXIMUM_ELAPSED_MS_LIMIT = 120_000;
    public static final long FORWARD_THRESHOLD_MS_LIMIT = 300_000;
    public static final int RETRY_DELAY_MS_LIMIT = 5_000;

    private final int maximumOmissions;
    private final long maximumElapsedMs;
    private final long forwardThresholdMs;
    private final int retryDelayMs;

    public SabrProfileRecovery(final int maximumOmissions,
                               final long maximumElapsedMs,
                               final long forwardThresholdMs,
                               final int retryDelayMs) {
        if (maximumOmissions < 1 || maximumOmissions > MAXIMUM_OMISSIONS_LIMIT
                || maximumElapsedMs < 1 || maximumElapsedMs > MAXIMUM_ELAPSED_MS_LIMIT
                || forwardThresholdMs < 0
                || forwardThresholdMs > FORWARD_THRESHOLD_MS_LIMIT
                || retryDelayMs < 0 || retryDelayMs > RETRY_DELAY_MS_LIMIT) {
            throw new IllegalArgumentException("Invalid SABR recovery thresholds");
        }
        this.maximumOmissions = maximumOmissions;
        this.maximumElapsedMs = maximumElapsedMs;
        this.forwardThresholdMs = forwardThresholdMs;
        this.retryDelayMs = retryDelayMs;
    }

    public int getMaximumOmissions() {
        return maximumOmissions;
    }

    public long getMaximumElapsedMs() {
        return maximumElapsedMs;
    }

    public long getForwardThresholdMs() {
        return forwardThresholdMs;
    }

    public int getRetryDelayMs() {
        return retryDelayMs;
    }

    void writeCanonical(@Nonnull final DataOutputStream output) throws IOException {
        output.writeInt(maximumOmissions);
        output.writeLong(maximumElapsedMs);
        output.writeLong(forwardThresholdMs);
        output.writeInt(retryDelayMs);
    }
}
