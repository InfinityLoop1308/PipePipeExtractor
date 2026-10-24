package org.schabi.newpipe.extractor.plugin;

/**
 * Thrown when a plugin package or repository is malformed, fails verification or cannot be
 * loaded.
 */
public class PluginException extends Exception {
    public PluginException(final String message) {
        super(message);
    }

    public PluginException(final String message, final Throwable cause) {
        super(message, cause);
    }
}
