package org.schabi.newpipe.extractor.services.youtube.sabr;

import javax.annotation.Nonnull;
import java.io.DataOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/** Immutable WEB or MWEB compatibility variant compiled from a signed document. */
public final class SabrCompatibilityProfileClient {
    public static final class MediaParts {
        private final int header;
        private final int payload;
        private final int end;

        public MediaParts(final int header, final int payload, final int end) {
            if (!validPart(header) || !validPart(payload) || !validPart(end)
                    || header == payload || header == end || payload == end) {
                throw new IllegalArgumentException("Invalid SABR media part types");
            }
            this.header = header;
            this.payload = payload;
            this.end = end;
        }

        private static boolean validPart(final int value) {
            return value >= 0 && value <= SabrCompatibilityProfile.MAX_UMP_PART_TYPE
                    && (value < 10 || value > 12) && (value < 30 || value > 67);
        }

        public int getHeader() {
            return header;
        }

        public int getPayload() {
            return payload;
        }

        public int getEnd() {
            return end;
        }

        void writeCanonical(@Nonnull final DataOutputStream output) throws IOException {
            output.writeInt(header);
            output.writeInt(payload);
            output.writeInt(end);
        }
    }

    @Nonnull private final YoutubeSabrClientProfile client;
    @Nonnull private final MediaParts mediaParts;
    @Nonnull private final List<SabrProfileRequestField> initialRequest;
    @Nonnull private final List<SabrProfileRequestField> followingRequest;
    @Nonnull private final List<SabrProfileResponseMapping> responseMappings;
    @Nonnull private final SabrProfileRecovery recovery;
    @Nonnull private final List<SabrProfileRule> rules;

    public SabrCompatibilityProfileClient(
            @Nonnull final YoutubeSabrClientProfile client,
            @Nonnull final MediaParts mediaParts,
            @Nonnull final List<SabrProfileRequestField> initialRequest,
            @Nonnull final List<SabrProfileRequestField> followingRequest,
            @Nonnull final List<SabrProfileResponseMapping> responseMappings,
            @Nonnull final SabrProfileRecovery recovery,
            @Nonnull final List<SabrProfileRule> rules) {
        if (client != YoutubeSabrClientProfile.WEB && client != YoutubeSabrClientProfile.MWEB) {
            throw new IllegalArgumentException("Compatibility profiles support WEB and MWEB only");
        }
        SabrCompatibilityProfileValidator.validate(initialRequest, followingRequest,
                responseMappings, rules, mediaParts);
        this.client = client;
        this.mediaParts = Objects.requireNonNull(mediaParts);
        this.initialRequest = immutableCopy(initialRequest);
        this.followingRequest = immutableCopy(followingRequest);
        this.responseMappings = immutableCopy(responseMappings);
        this.recovery = Objects.requireNonNull(recovery);
        this.rules = immutableCopy(rules);
    }

    @Nonnull
    private static <T> List<T> immutableCopy(@Nonnull final List<T> values) {
        return Collections.unmodifiableList(new ArrayList<>(values));
    }

    @Nonnull
    public YoutubeSabrClientProfile getClient() {
        return client;
    }

    @Nonnull
    public MediaParts getMediaParts() {
        return mediaParts;
    }

    @Nonnull
    public List<SabrProfileRequestField> getInitialRequest() {
        return initialRequest;
    }

    @Nonnull
    public List<SabrProfileRequestField> getFollowingRequest() {
        return followingRequest;
    }

    @Nonnull
    public List<SabrProfileResponseMapping> getResponseMappings() {
        return responseMappings;
    }

    @Nonnull
    public SabrProfileRecovery getRecovery() {
        return recovery;
    }

    @Nonnull
    public List<SabrProfileRule> getRules() {
        return rules;
    }

    void writeCanonical(@Nonnull final DataOutputStream output) throws IOException {
        SabrCompatibilityProfile.writeString(output, client.getClientName());
        mediaParts.writeCanonical(output);
        writeRequestFields(output, initialRequest);
        writeRequestFields(output, followingRequest);
        output.writeInt(responseMappings.size());
        for (final SabrProfileResponseMapping mapping : responseMappings) {
            mapping.writeCanonical(output);
        }
        recovery.writeCanonical(output);
        output.writeInt(rules.size());
        for (final SabrProfileRule rule : rules) {
            rule.writeCanonical(output);
        }
    }

    private static void writeRequestFields(@Nonnull final DataOutputStream output,
                                           @Nonnull final List<SabrProfileRequestField> fields)
            throws IOException {
        output.writeInt(fields.size());
        for (final SabrProfileRequestField field : fields) {
            field.writeCanonical(output);
        }
    }
}
