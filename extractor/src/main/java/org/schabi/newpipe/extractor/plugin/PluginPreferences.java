package org.schabi.newpipe.extractor.plugin;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/**
 * Platform independent key-value storage for a single plugin.
 * Implementations must be thread safe.
 */
public interface PluginPreferences {

    /**
     * @return the stored value, or the default declared for this key in {@code plugin.json};
     * {@code null} if neither exists
     */
    @Nullable
    String getString(@Nonnull String key);

    /**
     * @return the stored value, or the default declared for this key in {@code plugin.json};
     * {@code false} if neither exists
     */
    boolean getBoolean(@Nonnull String key);

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
