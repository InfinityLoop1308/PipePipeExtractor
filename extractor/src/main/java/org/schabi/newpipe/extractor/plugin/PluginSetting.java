package org.schabi.newpipe.extractor.plugin;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/**
 * A user facing setting declared by a plugin in {@code plugin.json}. The host renders it and
 * stores the value in the plugin's {@link PluginPreferences} under {@link #getKey()}.
 */
public final class PluginSetting {

    public enum Type {
        STRING, BOOLEAN
    }

    @Nonnull
    private final String key;
    @Nonnull
    private final Type type;
    @Nonnull
    private final String title;
    @Nullable
    private final String summary;
    @Nullable
    private final String defaultValue;

    public PluginSetting(@Nonnull final String key,
                         @Nonnull final Type type,
                         @Nonnull final String title,
                         @Nullable final String summary,
                         @Nullable final String defaultValue) {
        this.key = key;
        this.type = type;
        this.title = title;
        this.summary = summary;
        this.defaultValue = defaultValue;
    }

    @Nonnull
    public String getKey() {
        return key;
    }

    @Nonnull
    public Type getType() {
        return type;
    }

    @Nonnull
    public String getTitle() {
        return title;
    }

    @Nullable
    public String getSummary() {
        return summary;
    }

    /**
     * @return the default value as string; {@code "true"}/{@code "false"} for booleans
     */
    @Nullable
    public String getDefaultValue() {
        return defaultValue;
    }
}
