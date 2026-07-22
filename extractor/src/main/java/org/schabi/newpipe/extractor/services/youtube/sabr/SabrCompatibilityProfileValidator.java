package org.schabi.newpipe.extractor.services.youtube.sabr;

import javax.annotation.Nonnull;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.List;

final class SabrCompatibilityProfileValidator {
    private static final int MAX_REQUEST_FIELDS = 64;
    private static final int MAX_RESPONSE_MAPPINGS = 128;
    private static final int MAX_RULES = 32;

    private SabrCompatibilityProfileValidator() {
    }

    static void validate(@Nonnull final List<SabrProfileRequestField> initialRequest,
                         @Nonnull final List<SabrProfileRequestField> followingRequest,
                         @Nonnull final List<SabrProfileResponseMapping> responseMappings,
                         @Nonnull final List<SabrProfileRule> rules,
                         @Nonnull final SabrCompatibilityProfileClient.MediaParts mediaParts) {
        validateSize(initialRequest, MAX_REQUEST_FIELDS, "initial request");
        validateSize(followingRequest, MAX_REQUEST_FIELDS, "following request");
        validateSize(responseMappings, MAX_RESPONSE_MAPPINGS, "response mappings");
        validateSize(rules, MAX_RULES, "behavior rules");
        if (initialRequest.isEmpty() || followingRequest.isEmpty() || rules.isEmpty()
                || !rules.get(rules.size() - 1).getPredicates().isEmpty()) {
            throw new IllegalArgumentException("Incomplete SABR compatibility client");
        }
        validateRequestTemplate(initialRequest);
        validateRequestTemplate(followingRequest);
        validateMappings(responseMappings, mediaParts);
        validateRules(rules);
    }

    private static void validateRules(@Nonnull final List<SabrProfileRule> rules) {
        for (final SabrProfileRule rule : rules) {
            final List<SabrProfileRule.Predicate> predicates = rule.getPredicates();
            final List<SabrProfileRule.Action> actions = rule.getActions();
            if (actions.contains(SabrProfileRule.Action.APPLY_REDIRECT)
                    && !predicates.contains(SabrProfileRule.Predicate.HAS_REDIRECT)
                    || actions.contains(SabrProfileRule.Action.REFRESH_PO_TOKEN)
                    && !predicates.contains(SabrProfileRule.Predicate.PUMP_MODE)
                    || actions.contains(SabrProfileRule.Action.REQUIRE_PO_TOKEN)
                    && !predicates.contains(SabrProfileRule.Predicate.FETCH_SEGMENT_MODE)
                    || actions.contains(SabrProfileRule.Action.TRY_RELOAD)
                    && !predicates.contains(SabrProfileRule.Predicate.RELOAD_REQUESTED)) {
                throw new IllegalArgumentException("Unsafe SABR behavior rule");
            }
        }
        final SabrProfileRule fallback = rules.get(rules.size() - 1);
        if (fallback.getActions().size() != 1
                || fallback.getActions().get(0) != SabrProfileRule.Action.CONTINUE) {
            throw new IllegalArgumentException("SABR default behavior must continue");
        }
    }

    private static void validateRequestTemplate(
            @Nonnull final List<SabrProfileRequestField> fields) {
        final EnumSet<SabrProfileRequestField.Source> sources = EnumSet.noneOf(
                SabrProfileRequestField.Source.class);
        final HashSet<Integer> fieldNumbers = new HashSet<>();
        for (final SabrProfileRequestField field : fields) {
            if (field == null || !sources.add(field.getSource())
                    || !fieldNumbers.add(field.getField())) {
                throw new IllegalArgumentException("Duplicate SABR request source or field");
            }
        }
        if (!sources.contains(SabrProfileRequestField.Source.CLIENT_ABR_STATE)
                || !sources.contains(SabrProfileRequestField.Source.USTREAMER_CONFIG)
                || !sources.contains(SabrProfileRequestField.Source.CLIENT_CONTEXT)) {
            throw new IllegalArgumentException("SABR request template misses required sources");
        }
    }

    private static void validateMappings(
            @Nonnull final List<SabrProfileResponseMapping> mappings,
            @Nonnull final SabrCompatibilityProfileClient.MediaParts mediaParts) {
        final EnumSet<SabrProfileResponseMapping.Target> targets = EnumSet.noneOf(
                SabrProfileResponseMapping.Target.class);
        for (final SabrProfileResponseMapping mapping : mappings) {
            if (mapping == null || !targets.add(mapping.getTarget())) {
                throw new IllegalArgumentException("Duplicate SABR response target");
            }
            if (mapping.getPartType() == mediaParts.getPayload()
                    || mapping.getPartType() == mediaParts.getEnd()) {
                throw new IllegalArgumentException("SABR profiles cannot inspect media payloads");
            }
            if (mapping.getTarget().name().startsWith("MEDIA_HEADER_")
                    && mapping.getPartType() != mediaParts.getHeader()) {
                throw new IllegalArgumentException("Media mapping uses non-header UMP part");
            }
        }
    }

    private static void validateSize(@Nonnull final List<?> values, final int maximum,
                                     @Nonnull final String name) {
        if (values.size() > maximum) {
            throw new IllegalArgumentException("Too many SABR " + name);
        }
    }
}
