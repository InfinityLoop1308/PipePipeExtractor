package org.schabi.newpipe.extractor.plugin;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/**
 * Platform independent key-value storage for a single plugin.
 * Implementations must be thread safe.
 */
public interface PluginPreferences {

    @Nullable
    String getString(@Nonnull String key, @Nullable String defaultValue);

    boolean getBoolean(@Nonnull String key, boolean defaultValue);

    void putString(@Nonnull String key, @Nullable String value);

    void putBoolean(@Nonnull String key, boolean value);

    /**
     * Register a listener that is called with the changed key whenever a value of this plugin
     * changes, including changes made by the host settings UI.
     */
    void addChangeListener(@Nonnull ChangeListener listener);

    interface ChangeListener {
        void onChanged(@Nonnull String key);
    }
}
