package org.schabi.newpipe.extractor.services.twitch.data.api.pagination;

import java.util.Objects;

public final class PositionBasedPagination extends Pagination {

    public static final PositionBasedPagination DEFAULT_POSITION_PAGINATION =
            new PositionBasedPagination(0, 20);

    private final int offset;
    private final int size;

    public PositionBasedPagination(int offset, int size) {
        this.offset = offset;
        this.size = size;
    }

    public PositionBasedPagination shift(final int maxLength) {
        return new PositionBasedPagination(offset + size, Math.min(size,
                maxLength - (offset + size)));
    }

    public int offset() {
        return offset;
    }

    public int size() {
        return size;
    }

    @Override
    public boolean equals(Object obj) {
        if (obj == this) return true;
        if (obj == null || obj.getClass() != this.getClass()) return false;
        var that = (PositionBasedPagination) obj;
        return this.offset == that.offset &&
                this.size == that.size;
    }

    @Override
    public int hashCode() {
        return Objects.hash(offset, size);
    }

    @Override
    public String toString() {
        return "PositionBasedPagination[" +
                "offset=" + offset + ", " +
                "size=" + size + ']';
    }

}
