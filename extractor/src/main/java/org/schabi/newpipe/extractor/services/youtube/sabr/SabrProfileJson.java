package org.schabi.newpipe.extractor.services.youtube.sabr;

import com.grack.nanojson.JsonArray;
import com.grack.nanojson.JsonObject;

import javax.annotation.Nonnull;
import java.math.BigInteger;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashSet;
import java.util.Set;

/** Strict bounded JSON accessors shared by compatibility profile parsers. */
final class SabrProfileJson {
    private static final int MAX_JSON_DEPTH = 16;
    private static final int MAX_STRUCTURE_TOKENS = 8192;

    private SabrProfileJson() {
    }

    static void validateStructure(@Nonnull final String encoded) {
        int depth = 0;
        int structureTokens = 0;
        final Deque<Set<String>> objectKeys = new ArrayDeque<>();
        for (int index = 0; index < encoded.length(); index++) {
            final char value = encoded.charAt(index);
            if (value == '"') {
                final int end = findStringEnd(encoded, index + 1);
                int following = end + 1;
                while (following < encoded.length()
                        && Character.isWhitespace(encoded.charAt(following))) {
                    following++;
                }
                if (following < encoded.length() && encoded.charAt(following) == ':'
                        && !objectKeys.isEmpty()) {
                    final String key = unescape(encoded, index + 1, end);
                    if (!objectKeys.peek().add(key)) {
                        throw new IllegalArgumentException(
                                "Duplicate SABR compatibility profile field: " + key);
                    }
                }
                index = end;
            } else if (value == '{' || value == '[') {
                if (++depth > MAX_JSON_DEPTH) {
                    throw new IllegalArgumentException("SABR profile JSON is too deeply nested");
                }
                if (value == '{') {
                    objectKeys.push(new HashSet<>());
                }
            } else if (value == ':' || value == ',') {
                if (++structureTokens > MAX_STRUCTURE_TOKENS) {
                    throw new IllegalArgumentException(
                            "SABR profile JSON has too many entries");
                }
            } else if (value == '}' || value == ']') {
                if (--depth < 0) {
                    throw new IllegalArgumentException("Malformed SABR compatibility profile");
                }
                if (value == '}') {
                    if (objectKeys.isEmpty()) {
                        throw new IllegalArgumentException(
                                "Malformed SABR compatibility profile");
                    }
                    objectKeys.pop();
                }
            }
        }
        if (depth != 0 || !objectKeys.isEmpty()) {
            throw new IllegalArgumentException("Malformed SABR compatibility profile");
        }
    }

    private static int findStringEnd(@Nonnull final String value, final int start) {
        boolean escaped = false;
        for (int index = start; index < value.length(); index++) {
            final char current = value.charAt(index);
            if (escaped) {
                escaped = false;
            } else if (current == '\\') {
                escaped = true;
            } else if (current == '"') {
                return index;
            }
        }
        throw new IllegalArgumentException("Malformed SABR compatibility profile string");
    }

    @Nonnull
    private static String unescape(@Nonnull final String value, final int start, final int end) {
        final StringBuilder result = new StringBuilder(end - start);
        for (int index = start; index < end; index++) {
            final char current = value.charAt(index);
            if (current != '\\') {
                result.append(current);
                continue;
            }
            if (++index >= end) {
                throw new IllegalArgumentException("Malformed SABR profile field escape");
            }
            final char escaped = value.charAt(index);
            switch (escaped) {
                case '"': case '\\': case '/': result.append(escaped); break;
                case 'b': result.append('\b'); break;
                case 'f': result.append('\f'); break;
                case 'n': result.append('\n'); break;
                case 'r': result.append('\r'); break;
                case 't': result.append('\t'); break;
                case 'u':
                    if (index + 4 >= end) {
                        throw new IllegalArgumentException("Malformed SABR profile Unicode escape");
                    }
                    try {
                        result.append((char) Integer.parseInt(
                                value.substring(index + 1, index + 5), 16));
                    } catch (final NumberFormatException error) {
                        throw new IllegalArgumentException(
                                "Malformed SABR profile Unicode escape", error);
                    }
                    index += 4;
                    break;
                default:
                    throw new IllegalArgumentException("Malformed SABR profile field escape");
            }
        }
        return result.toString();
    }

    static void requireKeys(@Nonnull final JsonObject object,
                            @Nonnull final String... keys) {
        final Set<String> expected = new HashSet<>();
        java.util.Collections.addAll(expected, keys);
        if (object.size() != expected.size() || !object.keySet().equals(expected)) {
            throw new IllegalArgumentException("Unexpected SABR compatibility profile fields");
        }
    }

    @Nonnull
    static JsonObject requireObject(@Nonnull final JsonObject parent,
                                    @Nonnull final String field) {
        final Object value = parent.get(field);
        if (!(value instanceof JsonObject)) {
            throw new IllegalArgumentException("SABR profile field is not an object: " + field);
        }
        return (JsonObject) value;
    }

    @Nonnull
    static JsonObject requireObject(@Nonnull final JsonArray parent, final int index) {
        final Object value = parent.get(index);
        if (!(value instanceof JsonObject)) {
            throw new IllegalArgumentException("SABR profile array item is not an object");
        }
        return (JsonObject) value;
    }

    @Nonnull
    static JsonArray requireArray(@Nonnull final JsonObject parent,
                                  @Nonnull final String field) {
        final Object value = parent.get(field);
        if (!(value instanceof JsonArray)) {
            throw new IllegalArgumentException("SABR profile field is not an array: " + field);
        }
        return (JsonArray) value;
    }

    @Nonnull
    static String requireString(@Nonnull final JsonObject parent,
                                @Nonnull final String field) {
        final Object value = parent.get(field);
        if (!(value instanceof String)) {
            throw new IllegalArgumentException("SABR profile field is not a string: " + field);
        }
        return (String) value;
    }

    static boolean requireBoolean(@Nonnull final JsonObject parent,
                                  @Nonnull final String field) {
        final Object value = parent.get(field);
        if (!(value instanceof Boolean)) {
            throw new IllegalArgumentException("SABR profile field is not a boolean: " + field);
        }
        return (Boolean) value;
    }

    static int requireInt(@Nonnull final JsonObject parent, @Nonnull final String field) {
        return exactInt(parent.get(field), field);
    }

    static int exactInt(final Object value, @Nonnull final String field) {
        final long exact = exactLong(value, field);
        if (exact < Integer.MIN_VALUE || exact > Integer.MAX_VALUE) {
            throw new IllegalArgumentException("SABR profile integer is out of range: " + field);
        }
        return (int) exact;
    }

    static long requireLong(@Nonnull final JsonObject parent, @Nonnull final String field) {
        return exactLong(parent.get(field), field);
    }

    private static long exactLong(final Object value, @Nonnull final String field) {
        if (value instanceof Integer || value instanceof Long) {
            return ((Number) value).longValue();
        }
        if (value instanceof BigInteger) {
            try {
                return ((BigInteger) value).longValueExact();
            } catch (final ArithmeticException error) {
                throw new IllegalArgumentException(
                        "SABR profile integer is out of range: " + field, error);
            }
        }
        throw new IllegalArgumentException(
                "SABR profile field is not an exact integer: " + field);
    }
}
