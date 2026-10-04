package org.schabi.newpipe.extractor.services.twitch.data.url;

import org.schabi.newpipe.extractor.services.twitch.data.api.pagination.Pagination;

import javax.annotation.Nonnull;

public record CategoryUrl(@Nonnull String categoryName,
                          @Nonnull String categorySlug,
                          @Nonnull String categoryId,
                          @Nonnull Pagination pagination) { }
