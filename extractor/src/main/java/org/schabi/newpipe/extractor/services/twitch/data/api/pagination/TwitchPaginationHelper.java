package org.schabi.newpipe.extractor.services.twitch.data.api.pagination;

import com.grack.nanojson.JsonObject;
import com.grack.nanojson.JsonWriter;

import org.schabi.newpipe.extractor.services.twitch.TwitchUtils;
import org.schabi.newpipe.extractor.services.twitch.api.TwitchJsonHelper;

import java.lang.reflect.Array;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Base64;
import java.util.Map;

import javax.annotation.Nonnull;

public final class TwitchPaginationHelper {
    public static @Nonnull String createPaginationString(final @Nonnull Pagination pagination) {

        JsonObject paginationObject;

        if(pagination instanceof CursorBasedPagination cursorBasedPagination)
            paginationObject = new JsonObject(TwitchUtils.mapOfNullables(
                "cursor", cursorBasedPagination.cursor()
            ));
        else if(pagination instanceof PositionBasedPagination positionBasedPagination)
            paginationObject = new JsonObject(Map.of(
                "offset", positionBasedPagination.offset(),
                "size", positionBasedPagination.size()
            ));
        else throw new UnsupportedOperationException();

        return Base64.getEncoder().encodeToString(
                StandardCharsets.UTF_8.encode(JsonWriter.string(paginationObject)).array()
        );
    }

    public static @Nonnull Pagination getPaginationFromString(final @Nonnull String paginationString) {
        final var paginationObject = TwitchJsonHelper.parseJsonObjectFromString(
                new String(Base64.getDecoder().decode(paginationString), StandardCharsets.UTF_8).trim()
        );

        if(paginationObject.has("cursor"))
            return new CursorBasedPagination(paginationObject.getString("cursor"));

        return new PositionBasedPagination(
                paginationObject.getInt("offset"),
                paginationObject.getInt("size")
        );
    }

    public static <T> T[] paginateArray(T[] toPaginate, Class<T> clazz, PositionBasedPagination pagination) {
        final var targetArraySize = Math.clamp(toPaginate.length - pagination.offset(), 0, pagination.size());
        if(targetArraySize == 0)
            //noinspection unchecked
            return (T[])Array.newInstance(clazz, 0);
        @SuppressWarnings("unchecked") final var targetArray = (T[])Array.newInstance(
                clazz,
                Math.min(toPaginate.length - pagination.offset(), pagination.size())
        );
        System.arraycopy(
                toPaginate,
                pagination.offset(),
                targetArray,
                0,
                Math.min(toPaginate.length - pagination.offset(), pagination.size())
        );
        return targetArray;
    }
}