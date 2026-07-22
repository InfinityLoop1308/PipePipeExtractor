package org.schabi.newpipe.extractor.services.youtube.sabr;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.List;

/** Applies precompiled response paths to small UMP control parts. */
final class SabrProfileResponseMapper {
    @Nonnull private final List<SabrProfileResponseMapping> mappings;

    SabrProfileResponseMapper(@Nonnull final List<SabrProfileResponseMapping> mappings) {
        this.mappings = mappings;
    }

    @Nonnull
    SabrProfileMappedResponse map(@Nonnull final SabrDecodedResponse response)
            throws SabrProtocolException {
        final SabrProfileMappedResponse.Builder builder = new SabrProfileMappedResponse.Builder();
        for (final SabrProfileResponseMapping mapping : mappings) {
            if (mapping.getTarget().name().startsWith("MEDIA_HEADER_")) {
                continue;
            }
            boolean partSeen = false;
            SabrProfileWireValue selected = null;
            for (final UmpPart part : response.getParts()) {
                if (part.getType() == mapping.getPartType()) {
                    partSeen = true;
                    final SabrProfileWireValue current = SabrProfileWireValue.find(
                            part.getRawData(), mapping);
                    if (current != null) selected = current;
                }
            }
            if (selected != null) {
                builder.apply(mapping.getTarget(), selected);
            } else if (partSeen && mapping.isRequired()) {
                throw new SabrProtocolException("Required SABR response mapping is absent: "
                        + mapping);
            }
        }
        return builder.build(response);
    }
}
