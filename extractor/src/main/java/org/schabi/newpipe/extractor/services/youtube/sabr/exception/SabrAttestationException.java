package org.schabi.newpipe.extractor.services.youtube.sabr.exception;

/** Indicates that YouTube rejected or could not complete the current attestation identity. */
public final class SabrAttestationException extends SabrProtocolException {
    public SabrAttestationException(final String message) {
        super(message);
    }
}
