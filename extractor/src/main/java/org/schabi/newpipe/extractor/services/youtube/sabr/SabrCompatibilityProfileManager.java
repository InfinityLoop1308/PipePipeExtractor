package org.schabi.newpipe.extractor.services.youtube.sabr;

import org.bouncycastle.crypto.params.Ed25519PublicKeyParameters;
import org.bouncycastle.crypto.signers.Ed25519Signer;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/** Thread-safe key rotation, verification and monotonic profile activation boundary. */
public final class SabrCompatibilityProfileManager {
    private static final int ED25519_PUBLIC_KEY_BYTES = 32;
    @Nonnull private final Map<String, byte[]> publicKeys;
    @Nullable private volatile SabrCompatibilityProfile active;
    @Nullable private volatile SabrCompatibilityProfile previous;
    private long highestRevision;

    public SabrCompatibilityProfileManager(@Nonnull final Map<String, byte[]> publicKeys,
                                           final long minimumRevision) {
        if (publicKeys.isEmpty() || publicKeys.size() > 8 || minimumRevision < 0) {
            throw new IllegalArgumentException("Invalid SABR profile verifier");
        }
        final Map<String, byte[]> checked = new LinkedHashMap<>();
        for (final Map.Entry<String, byte[]> entry : publicKeys.entrySet()) {
            if (entry.getKey() == null || !entry.getKey().matches("[A-Za-z0-9._-]{1,64}")
                    || entry.getValue() == null
                    || entry.getValue().length != ED25519_PUBLIC_KEY_BYTES) {
                throw new IllegalArgumentException("Invalid SABR profile public key");
            }
            checked.put(entry.getKey(), entry.getValue().clone());
        }
        this.publicKeys = Collections.unmodifiableMap(checked);
        highestRevision = minimumRevision;
    }

    @Nonnull
    public synchronized SabrCompatibilityProfile verifyDocument(
            @Nonnull final byte[] document, final long nowMs) {
        final SabrCompatibilityProfile profile = verifySignature(document, nowMs);
        if (profile.getRevision() < highestRevision
                || profile.getRevision() == highestRevision
                && (active == null || active.getRevision() != highestRevision)) {
            throw new IllegalArgumentException("SABR profile rollback or validity check failed");
        }
        return profile;
    }

    @Nonnull
    private SabrCompatibilityProfile verifySignature(
            @Nonnull final byte[] document, final long nowMs) {
        final SabrCompatibilityProfileDocument.Parsed parsed =
                SabrCompatibilityProfileDocument.decode(document);
        final byte[] key = publicKeys.get(parsed.getKeyId());
        if (key == null) {
            throw new IllegalArgumentException("Unknown SABR profile signing key");
        }
        final SabrCompatibilityProfile profile = parsed.getProfile();
        if (!profile.isValidAt(nowMs)) {
            throw new IllegalArgumentException("SABR profile rollback or validity check failed");
        }
        final byte[] payload = profile.serialize();
        final Ed25519Signer verifier = new Ed25519Signer();
        verifier.init(false, new Ed25519PublicKeyParameters(key));
        verifier.update(payload, 0, payload.length);
        if (!verifier.verifySignature(parsed.getSignature())) {
            throw new IllegalArgumentException("Invalid SABR compatibility profile signature");
        }
        if (active != null && active.getRevision() == profile.getRevision()
                && !Arrays.equals(active.serialize(), profile.serialize())) {
            throw new IllegalArgumentException("Conflicting SABR profile revision");
        }
        return profile;
    }

    /** Restores the normal active generation at or above the monotonic revision floor. */
    @Nonnull
    public synchronized SabrCompatibilityProfile restoreDocument(
            @Nonnull final byte[] document, final long nowMs) {
        final SabrCompatibilityProfile restored = verifySignature(document, nowMs);
        if (restored.getRevision() < highestRevision) {
            throw new IllegalArgumentException("SABR profile rollback rejected");
        }
        active = restored;
        previous = null;
        highestRevision = restored.getRevision();
        return restored;
    }

    /** Restores the signed previous generation only behind a verified active generation. */
    @Nonnull
    public synchronized SabrCompatibilityProfile restorePreviousDocument(
            @Nonnull final byte[] document, final long nowMs) {
        final SabrCompatibilityProfile restored = verifySignature(document, nowMs);
        if (active == null || restored.getRevision() >= active.getRevision()) {
            throw new IllegalArgumentException("Invalid previous SABR profile generation");
        }
        previous = restored;
        return restored;
    }

    /** Restores a circuit-breaker fallback while retaining a higher revision floor. */
    @Nonnull
    public synchronized SabrCompatibilityProfile restoreFallbackDocument(
            @Nonnull final byte[] document, final long nowMs) {
        final SabrCompatibilityProfile restored = verifySignature(document, nowMs);
        if (active != null || restored.getRevision() >= highestRevision) {
            throw new IllegalArgumentException("Invalid SABR profile fallback generation");
        }
        active = restored;
        previous = null;
        return restored;
    }

    public synchronized void activate(@Nonnull final SabrCompatibilityProfile verified) {
        if (verified.getRevision() < highestRevision) {
            throw new IllegalArgumentException("SABR profile rollback rejected");
        }
        if (active != null && active.getRevision() == verified.getRevision()
                && !Arrays.equals(active.serialize(), verified.serialize())) {
            throw new IllegalArgumentException("Conflicting SABR profile revision");
        }
        if (active != null && active.getRevision() < verified.getRevision()) {
            previous = active;
        }
        active = verified;
        highestRevision = verified.getRevision();
    }

    @Nonnull
    public synchronized SabrCompatibilityProfile installDocument(
            @Nonnull final byte[] document, final long nowMs) {
        final SabrCompatibilityProfile verified = verifyDocument(document, nowMs);
        activate(verified);
        return verified;
    }

    @Nullable
    public synchronized SabrCompatibilityProfile current(final long nowMs) {
        if (active != null && active.isValidAt(nowMs)) {
            return active;
        }
        active = null;
        previous = null;
        return null;
    }

    public synchronized boolean deactivate(@Nonnull final SabrCompatibilityProfile expected) {
        if (active == expected) {
            active = previous;
            previous = null;
            return true;
        }
        return false;
    }

    public synchronized long getHighestRevision() {
        return highestRevision;
    }
}
