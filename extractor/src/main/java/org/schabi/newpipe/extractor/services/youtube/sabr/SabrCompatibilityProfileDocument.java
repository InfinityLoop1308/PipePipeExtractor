package org.schabi.newpipe.extractor.services.youtube.sabr;

import com.grack.nanojson.JsonObject;
import com.grack.nanojson.JsonParser;
import com.grack.nanojson.JsonParserException;

import javax.annotation.Nonnull;
import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.util.Base64;

/** Strict JSON delivery envelope for signed declarative compatibility profiles. */
public final class SabrCompatibilityProfileDocument {
    public static final int MAX_DOCUMENT_BYTES = 1024 * 1024;
    private static final int ED25519_SIGNATURE_BYTES = 64;

    private SabrCompatibilityProfileDocument() {
    }

    @Nonnull
    public static Parsed decode(@Nonnull final byte[] encoded) {
        if (encoded.length == 0 || encoded.length > MAX_DOCUMENT_BYTES) {
            throw new IllegalArgumentException("Invalid SABR compatibility profile size");
        }
        try {
            final String json = decodeUtf8(encoded);
            SabrProfileJson.validateStructure(json);
            final JsonObject root = JsonParser.object().from(json);
            SabrProfileJson.requireKeys(root, "format", "revision", "validFromMs",
                    "validUntilMs", "minimumExtractorRevision", "capabilities", "clients",
                    "keyId", "signature");
            if (SabrProfileJson.requireLong(root, "format")
                    != SabrCompatibilityProfile.FORMAT_VERSION) {
                throw new IllegalArgumentException("Unsupported SABR compatibility profile");
            }
            final SabrCompatibilityProfile profile = SabrCompatibilityProfileParser.parse(root);
            final String keyId = SabrProfileJson.requireString(root, "keyId");
            if (!keyId.matches("[A-Za-z0-9._-]{1,64}")) {
                throw new IllegalArgumentException("Invalid SABR profile key id");
            }
            final byte[] signature = Base64.getDecoder().decode(
                    SabrProfileJson.requireString(root, "signature"));
            if (signature.length != ED25519_SIGNATURE_BYTES) {
                throw new IllegalArgumentException("Invalid SABR profile signature size");
            }
            return new Parsed(profile, keyId, signature);
        } catch (final IllegalArgumentException error) {
            throw error;
        } catch (final JsonParserException | RuntimeException error) {
            throw new IllegalArgumentException("Malformed SABR compatibility profile", error);
        }
    }

    @Nonnull
    private static String decodeUtf8(@Nonnull final byte[] encoded) {
        try {
            return StandardCharsets.UTF_8.newDecoder()
                    .onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT)
                    .decode(ByteBuffer.wrap(encoded)).toString();
        } catch (final CharacterCodingException error) {
            throw new IllegalArgumentException("Malformed SABR profile UTF-8", error);
        }
    }

    public static final class Parsed {
        @Nonnull private final SabrCompatibilityProfile profile;
        @Nonnull private final String keyId;
        @Nonnull private final byte[] signature;

        Parsed(@Nonnull final SabrCompatibilityProfile profile,
               @Nonnull final String keyId,
               @Nonnull final byte[] signature) {
            this.profile = profile;
            this.keyId = keyId;
            this.signature = signature.clone();
        }

        @Nonnull public SabrCompatibilityProfile getProfile() { return profile; }
        @Nonnull public String getKeyId() { return keyId; }
        @Nonnull public byte[] getSignature() { return signature.clone(); }
    }
}
