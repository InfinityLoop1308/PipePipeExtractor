package org.schabi.newpipe.extractor.services.youtube.sabr;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;

/** Signed, immutable data contract for volatile WEB and MWEB SABR details. */
public final class SabrCompatibilityProfile {
    static final int MAX_PROTOBUF_FIELD_NUMBER = (1 << 29) - 1;
    static final int MAX_UMP_PART_TYPE = 65_535;
    public static final int FORMAT_VERSION = 1;
    public static final int EXTRACTOR_REVISION = 1;
    private static final int MAGIC = 0x53435031;

    public enum Capability {
        REQUEST_TEMPLATE_V1("request-template-v1"),
        RESPONSE_SCHEMA_V1("response-schema-v1"),
        RECOVERY_RULES_V1("recovery-rules-v1");

        @Nonnull private final String id;

        Capability(@Nonnull final String id) {
            this.id = id;
        }

        @Nonnull
        public String getId() {
            return id;
        }

        @Nonnull
        static Capability fromId(@Nonnull final String id) {
            for (final Capability capability : values()) {
                if (capability.id.equals(id)) {
                    return capability;
                }
            }
            throw new IllegalArgumentException("Unsupported SABR profile capability: " + id);
        }
    }

    private final long revision;
    private final long validFromMs;
    private final long validUntilMs;
    private final int minimumExtractorRevision;
    @Nonnull private final EnumSet<Capability> capabilities;
    @Nonnull private final Map<YoutubeSabrClientProfile, SabrCompatibilityProfileClient> clients;
    @Nonnull private final byte[] canonicalPayload;

    public SabrCompatibilityProfile(
            final long revision,
            final long validFromMs,
            final long validUntilMs,
            final int minimumExtractorRevision,
            @Nonnull final EnumSet<Capability> capabilities,
            @Nonnull final Map<YoutubeSabrClientProfile, SabrCompatibilityProfileClient> clients) {
        if (revision <= 0 || validFromMs < 0 || validUntilMs <= validFromMs
                || minimumExtractorRevision < 1
                || minimumExtractorRevision > EXTRACTOR_REVISION
                || !capabilities.containsAll(EnumSet.allOf(Capability.class))
                || clients.isEmpty() || clients.size() > 2) {
            throw new IllegalArgumentException("Invalid SABR compatibility profile metadata");
        }
        final EnumMap<YoutubeSabrClientProfile, SabrCompatibilityProfileClient> checked =
                new EnumMap<>(YoutubeSabrClientProfile.class);
        for (final Map.Entry<YoutubeSabrClientProfile, SabrCompatibilityProfileClient> entry
                : clients.entrySet()) {
            if (entry.getKey() == null || entry.getValue() == null
                    || entry.getKey() != entry.getValue().getClient()) {
                throw new IllegalArgumentException("Invalid SABR compatibility client map");
            }
            checked.put(entry.getKey(), entry.getValue());
        }
        this.revision = revision;
        this.validFromMs = validFromMs;
        this.validUntilMs = validUntilMs;
        this.minimumExtractorRevision = minimumExtractorRevision;
        this.capabilities = EnumSet.copyOf(capabilities);
        this.clients = Collections.unmodifiableMap(checked);
        canonicalPayload = encodeCanonical();
    }

    public long getRevision() {
        return revision;
    }

    public long getValidFromMs() {
        return validFromMs;
    }

    public long getValidUntilMs() {
        return validUntilMs;
    }

    public int getMinimumExtractorRevision() {
        return minimumExtractorRevision;
    }

    @Nonnull
    public EnumSet<Capability> getCapabilities() {
        return EnumSet.copyOf(capabilities);
    }

    @Nonnull
    public Map<YoutubeSabrClientProfile, SabrCompatibilityProfileClient> getClients() {
        return clients;
    }

    @Nullable
    public SabrCompatibilityProfileClient getClient(
            @Nonnull final YoutubeSabrClientProfile client) {
        return clients.get(client);
    }

    public boolean isValidAt(final long nowMs) {
        return nowMs >= validFromMs && nowMs < validUntilMs;
    }

    @Nonnull
    public byte[] serialize() {
        return canonicalPayload.clone();
    }

    @Nonnull
    private byte[] encodeCanonical() {
        try {
            final ByteArrayOutputStream bytes = new ByteArrayOutputStream();
            final DataOutputStream output = new DataOutputStream(bytes);
            output.writeInt(MAGIC);
            output.writeByte(FORMAT_VERSION);
            output.writeLong(revision);
            output.writeLong(validFromMs);
            output.writeLong(validUntilMs);
            output.writeInt(minimumExtractorRevision);
            final List<Capability> sortedCapabilities = new ArrayList<>(capabilities);
            sortedCapabilities.sort(Comparator.comparing(Capability::getId));
            output.writeByte(sortedCapabilities.size());
            for (final Capability capability : sortedCapabilities) {
                writeString(output, capability.getId());
            }
            final List<SabrCompatibilityProfileClient> sortedClients =
                    new ArrayList<>(clients.values());
            sortedClients.sort(Comparator.comparing(value -> value.getClient().getClientName()));
            output.writeByte(sortedClients.size());
            for (final SabrCompatibilityProfileClient client : sortedClients) {
                client.writeCanonical(output);
            }
            output.flush();
            return bytes.toByteArray();
        } catch (final IOException impossible) {
            throw new IllegalStateException(impossible);
        }
    }

    static void writeString(@Nonnull final DataOutputStream output,
                            @Nonnull final String value) throws IOException {
        final byte[] encoded = value.getBytes(StandardCharsets.UTF_8);
        output.writeInt(encoded.length);
        output.write(encoded);
    }
}
