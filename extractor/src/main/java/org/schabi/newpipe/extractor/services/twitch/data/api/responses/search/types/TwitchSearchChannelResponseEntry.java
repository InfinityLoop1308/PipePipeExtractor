package org.schabi.newpipe.extractor.services.twitch.data.api.responses.search.types;

import org.schabi.newpipe.extractor.services.twitch.data.api.responses.search.TwitchSearchBaseResponseEntry;

import javax.annotation.Nonnull;

public final class TwitchSearchChannelResponseEntry extends TwitchSearchBaseResponseEntry {

    @Nonnull
    private final String channelName;

    @Nonnull
    private final String loginName;
    @Nonnull
    private final String channelId;
    @Nonnull
    private final String channelDescription;

    @Nonnull
    private final String channelAvatarUrl;

    private final boolean isChannelPartnered;

    private final int channelFollowerCount;

    public TwitchSearchChannelResponseEntry(
            @Nonnull String channelName,
            @Nonnull String loginName,
            @Nonnull String channelId,
            @Nonnull String channelDescription,
            @Nonnull String channelAvatarUrl,
            boolean isChannelPartnered,
            int channelFollowerCount) {
        this.channelName = channelName;
        this.loginName = loginName;
        this.channelId = channelId;
        this.channelDescription = channelDescription;
        this.channelAvatarUrl = channelAvatarUrl;
        this.isChannelPartnered = isChannelPartnered;
        this.channelFollowerCount = channelFollowerCount;
    }

    @Nonnull
    public String getChannelName() {
        return channelName;
    }

    @Nonnull
    public String getChannelDescription() {
        return channelDescription;
    }

    @Nonnull
    public String getChannelAvatarUrl() {
        return channelAvatarUrl;
    }

    public boolean isChannelPartnered() {
        return isChannelPartnered;
    }

    public int getChannelFollowerCount() {
        return channelFollowerCount;
    }

    @Nonnull
    public String getChannelId() {
        return channelId;
    }

    @Nonnull
    public String getLoginName() {
        return loginName;
    }
}
