package org.schabi.newpipe.extractor.services.twitch.data.api.responses.search.types;

import org.schabi.newpipe.extractor.services.twitch.data.api.responses.search.TwitchSearchBaseResponseEntry;

import javax.annotation.Nonnull;

public final class TwitchSearchGameResponseEntry extends TwitchSearchBaseResponseEntry {

    @Nonnull
    private final String gameName;

    @Nonnull
    private final String slug;
    private final String id;
    @Nonnull
    private final String gameBoxArtUrl;


    public TwitchSearchGameResponseEntry(@Nonnull String gameName, @Nonnull final String slug,
                                         final String id, @Nonnull String gameBoxArtUrl) {
        this.gameName = gameName;
        this.slug = slug;
        this.id = id;
        this.gameBoxArtUrl = gameBoxArtUrl;
    }

    @Nonnull
    public String getGameName() {
        return gameName;
    }

    @Nonnull
    public String getGameBoxArtUrl() {
        return gameBoxArtUrl;
    }

    @Nonnull
    public String getSlug() {
        return slug;
    }

    public String getId() {
        return id;
    }
}
