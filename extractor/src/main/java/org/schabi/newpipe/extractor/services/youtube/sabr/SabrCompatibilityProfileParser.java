package org.schabi.newpipe.extractor.services.youtube.sabr;

import com.grack.nanojson.JsonArray;
import com.grack.nanojson.JsonObject;

import javax.annotation.Nonnull;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;

/** Converts the strict JSON shape into immutable native profile objects. */
final class SabrCompatibilityProfileParser {
    private SabrCompatibilityProfileParser() {
    }

    @Nonnull
    static SabrCompatibilityProfile parse(@Nonnull final JsonObject root) {
        return new SabrCompatibilityProfile(SabrProfileJson.requireLong(root, "revision"),
                SabrProfileJson.requireLong(root, "validFromMs"),
                SabrProfileJson.requireLong(root, "validUntilMs"),
                SabrProfileJson.requireInt(root, "minimumExtractorRevision"),
                parseCapabilities(SabrProfileJson.requireArray(root, "capabilities")),
                parseClients(SabrProfileJson.requireObject(root, "clients")));
    }

    @Nonnull
    private static EnumSet<SabrCompatibilityProfile.Capability> parseCapabilities(
            @Nonnull final JsonArray array) {
        if (array.isEmpty()
                || array.size() > SabrCompatibilityProfile.Capability.values().length) {
            throw new IllegalArgumentException("Invalid SABR profile capabilities");
        }
        final EnumSet<SabrCompatibilityProfile.Capability> capabilities =
                EnumSet.noneOf(SabrCompatibilityProfile.Capability.class);
        for (int index = 0; index < array.size(); index++) {
            final Object value = array.get(index);
            if (!(value instanceof String)
                    || !capabilities.add(SabrCompatibilityProfile.Capability.fromId(
                    (String) value))) {
                throw new IllegalArgumentException("Duplicate SABR profile capability");
            }
        }
        return capabilities;
    }

    @Nonnull
    private static Map<YoutubeSabrClientProfile, SabrCompatibilityProfileClient> parseClients(
            @Nonnull final JsonObject object) {
        if (object.isEmpty() || object.size() > 2) {
            throw new IllegalArgumentException("Invalid SABR compatibility clients");
        }
        final EnumMap<YoutubeSabrClientProfile, SabrCompatibilityProfileClient> result =
                new EnumMap<>(YoutubeSabrClientProfile.class);
        for (final String name : object.keySet()) {
            final YoutubeSabrClientProfile client = enumValue(
                    YoutubeSabrClientProfile.class, name, "compatibility client");
            if (client != YoutubeSabrClientProfile.WEB
                    && client != YoutubeSabrClientProfile.MWEB) {
                throw new IllegalArgumentException(
                        "Unsupported SABR compatibility client: " + name);
            }
            result.put(client, parseClient(client, SabrProfileJson.requireObject(object, name)));
        }
        return result;
    }

    @Nonnull
    private static SabrCompatibilityProfileClient parseClient(
            @Nonnull final YoutubeSabrClientProfile client,
            @Nonnull final JsonObject object) {
        SabrProfileJson.requireKeys(object, "mediaParts", "initialRequest", "followingRequest",
                "responseMappings", "recovery", "rules");
        final JsonObject media = SabrProfileJson.requireObject(object, "mediaParts");
        SabrProfileJson.requireKeys(media, "header", "payload", "end");
        return new SabrCompatibilityProfileClient(client,
                new SabrCompatibilityProfileClient.MediaParts(
                        SabrProfileJson.requireInt(media, "header"),
                        SabrProfileJson.requireInt(media, "payload"),
                        SabrProfileJson.requireInt(media, "end")),
                parseRequest(SabrProfileJson.requireArray(object, "initialRequest")),
                parseRequest(SabrProfileJson.requireArray(object, "followingRequest")),
                parseMappings(SabrProfileJson.requireArray(object, "responseMappings")),
                parseRecovery(SabrProfileJson.requireObject(object, "recovery")),
                parseRules(SabrProfileJson.requireArray(object, "rules")));
    }

    @Nonnull
    private static List<SabrProfileRequestField> parseRequest(@Nonnull final JsonArray array) {
        final List<SabrProfileRequestField> result = new ArrayList<>();
        for (int index = 0; index < array.size(); index++) {
            final JsonObject item = SabrProfileJson.requireObject(array, index);
            SabrProfileJson.requireKeys(item, "field", "wireType", "source", "required");
            result.add(new SabrProfileRequestField(SabrProfileJson.requireInt(item, "field"),
                    enumValue(SabrProfileRequestField.WireType.class,
                            SabrProfileJson.requireString(item, "wireType"), "request wire type"),
                    enumValue(SabrProfileRequestField.Source.class,
                            SabrProfileJson.requireString(item, "source"), "request source"),
                    SabrProfileJson.requireBoolean(item, "required")));
        }
        return result;
    }

    @Nonnull
    private static List<SabrProfileResponseMapping> parseMappings(@Nonnull final JsonArray array) {
        final List<SabrProfileResponseMapping> result = new ArrayList<>();
        for (int index = 0; index < array.size(); index++) {
            final JsonObject item = SabrProfileJson.requireObject(array, index);
            SabrProfileJson.requireKeys(item, "partType", "target", "path", "wireType",
                    "required");
            result.add(new SabrProfileResponseMapping(
                    SabrProfileJson.requireInt(item, "partType"),
                    SabrProfileResponseMapping.Target.fromId(
                            SabrProfileJson.requireString(item, "target")),
                    parsePath(SabrProfileJson.requireArray(item, "path")),
                    enumValue(SabrProfileResponseMapping.WireType.class,
                            SabrProfileJson.requireString(item, "wireType"), "response wire type"),
                    SabrProfileJson.requireBoolean(item, "required")));
        }
        return result;
    }

    private static int[] parsePath(@Nonnull final JsonArray array) {
        final int[] path = new int[array.size()];
        for (int index = 0; index < path.length; index++) {
            path[index] = SabrProfileJson.exactInt(array.get(index), "response path");
        }
        return path;
    }

    private static SabrProfileRecovery parseRecovery(@Nonnull final JsonObject object) {
        SabrProfileJson.requireKeys(object, "maximumOmissions", "maximumElapsedMs",
                "forwardThresholdMs", "retryDelayMs");
        return new SabrProfileRecovery(SabrProfileJson.requireInt(object, "maximumOmissions"),
                SabrProfileJson.requireLong(object, "maximumElapsedMs"),
                SabrProfileJson.requireLong(object, "forwardThresholdMs"),
                SabrProfileJson.requireInt(object, "retryDelayMs"));
    }

    private static List<SabrProfileRule> parseRules(@Nonnull final JsonArray array) {
        final List<SabrProfileRule> result = new ArrayList<>();
        for (int index = 0; index < array.size(); index++) {
            final JsonObject item = SabrProfileJson.requireObject(array, index);
            SabrProfileJson.requireKeys(item, "whenAll", "actions");
            result.add(new SabrProfileRule(parseEnums(
                            SabrProfileJson.requireArray(item, "whenAll"),
                            SabrProfileRule.Predicate.class, "behavior predicate"),
                    parseEnums(SabrProfileJson.requireArray(item, "actions"),
                            SabrProfileRule.Action.class, "behavior action")));
        }
        return result;
    }

    private static <T extends Enum<T>> List<T> parseEnums(
            @Nonnull final JsonArray array, @Nonnull final Class<T> type,
            @Nonnull final String label) {
        final List<T> result = new ArrayList<>();
        for (int index = 0; index < array.size(); index++) {
            if (!(array.get(index) instanceof String)) {
                throw new IllegalArgumentException("Invalid SABR " + label);
            }
            result.add(enumValue(type, (String) array.get(index), label));
        }
        return result;
    }

    private static <T extends Enum<T>> T enumValue(@Nonnull final Class<T> type,
                                                    @Nonnull final String value,
                                                    @Nonnull final String label) {
        try {
            return Enum.valueOf(type, value);
        } catch (final IllegalArgumentException error) {
            throw new IllegalArgumentException("Unsupported SABR " + label + ": " + value,
                    error);
        }
    }
}
