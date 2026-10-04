package org.schabi.newpipe.extractor.services.twitch.data.api.pagination;

import org.jetbrains.annotations.Nullable;

public class CursorBasedPagination extends Pagination {

    public static final CursorBasedPagination DEFAULT_CURSOR_PAGINATION = new CursorBasedPagination(null);

    @Nullable
    private final String cursor;

    public CursorBasedPagination(@Nullable String cursor) {
        this.cursor = cursor;
    }

    @Nullable
    public String cursor() {
        return cursor;
    }
}
