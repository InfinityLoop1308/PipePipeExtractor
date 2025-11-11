package org.schabi.newpipe.extractor.services.youtube;

import org.schabi.newpipe.extractor.exceptions.ParsingException;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.List;
import java.util.Map;

/**
 * Manage the extraction and the usage of YouTube's player JavaScript needed data in the YouTube
 * service.
 *
 * <p>
 * YouTube restrict streaming their media in multiple ways by requiring their HTML5 clients to use
 * a signature timestamp, and on streaming URLs a signature deobfuscation function for some
 * contents and a throttling parameter deobfuscation one for all contents.
 * </p>
 *
 * <p>
 * This class provides access to methods which allows to get base JavaScript player's signature
 * timestamp and to deobfuscate streaming URLs' signature and/or throttling parameter of HTML5
 * clients using the PipePipe API.
 * </p>
 */
public final class YoutubeJavaScriptPlayerManager {

    @Nullable
    private static String cachedPlayerId;
    @Nullable
    private static Integer cachedSignatureTimestamp;

    private YoutubeJavaScriptPlayerManager() {
    }

    /**
     * Get the signature timestamp of the base JavaScript player file.
     *
     * <p>
     * A valid signature timestamp sent in the payload of player InnerTube requests is required to
     * get valid stream URLs on HTML5 clients for videos which have obfuscated signatures.
     * </p>
     *
     * <p>
     * The signature timestamp is extracted from the JavaScript player file and cached.
     * </p>
     *
     * <p>
     * The result of the extraction is cached until {@link #clearAllCaches()} is called, making
     * subsequent calls faster.
     * </p>
     *
     * @param videoId the video ID used to get the JavaScript base player file (an empty one can be
     *                passed, even it is not recommend in order to spoof better official YouTube
     *                clients)
     * @return the signature timestamp of the base JavaScript player file
     * @throws ParsingException if the extraction of the signature timestamp failed
     */
    @Nonnull
    public static Integer getSignatureTimestamp(@Nonnull final String videoId)
            throws ParsingException {
        // Return the cached result if it is present
        if (cachedSignatureTimestamp != null) {
            return cachedSignatureTimestamp;
        }

        try {
            // The signature timestamp is still needed for InnerTube player requests
            // We extract it from the JavaScript file (only the timestamp, not the decode functions)
            final String playerCode = YoutubeJavaScriptExtractor.extractJavaScriptPlayerCode(videoId);
            cachedSignatureTimestamp = Integer.valueOf(YoutubeSignatureUtils.getSignatureTimestamp(playerCode));
        } catch (final Exception e) {
            throw new ParsingException("Could not get signature timestamp", e);
        }

        return cachedSignatureTimestamp;
    }

    /**
     * Deobfuscate a signature of a streaming URL using the PipePipe API.
     *
     * <p>
     * Obfuscated signatures are only present on streaming URLs of some videos with HTML5 clients.
     * </p>
     *
     * @param videoId             the video ID used to get the JavaScript base player ID (an
     *                            empty one can be passed, even it is not recommend in order to
     *                            spoof better official YouTube clients)
     * @param obfuscatedSignature the obfuscated signature of a streaming URL
     * @return the deobfuscated signature
     * @throws ParsingException if the extraction of the player ID or the API call failed
     */
    @Nonnull
    public static String deobfuscateSignature(@Nonnull final String videoId,
                                              @Nonnull final String obfuscatedSignature)
            throws ParsingException {
        extractPlayerIdIfNeeded(videoId);
        return YoutubeApiDecoder.decodeSignature(cachedPlayerId, obfuscatedSignature);
    }

    /**
     * Return a streaming URL with the throttling parameter of a given one deobfuscated, if it is
     * present, using the PipePipe API.
     *
     * <p>
     * The throttling parameter is present on all streaming URLs of HTML5 clients.
     * </p>
     *
     * <p>
     * If it is not given or deobfuscated, speeds will be throttled to a very slow speed (around 50
     * KB/s) and some streaming URLs could even lead to invalid HTTP responses such a 403 one.
     * </p>
     *
     * @param videoId      the video ID used to get the JavaScript base player ID (an empty one
     *                     can be passed, even it is not recommend in order to spoof better
     *                     official YouTube clients)
     * @param streamingUrl a streaming URL
     * @return the original streaming URL if it has no throttling parameter or a URL with a
     * deobfuscated throttling parameter
     * @throws ParsingException if the extraction of the player ID or the API call failed
     */
    @Nonnull
    public static String getUrlWithThrottlingParameterDeobfuscated(
            @Nonnull final String videoId,
            @Nonnull final String streamingUrl) throws ParsingException {
        final String obfuscatedThrottlingParameter =
                YoutubeThrottlingParameterUtils.getThrottlingParameterFromStreamingUrl(
                        streamingUrl);
        // If the throttling parameter is not present, return the original streaming URL
        if (obfuscatedThrottlingParameter == null) {
            return streamingUrl;
        }

        extractPlayerIdIfNeeded(videoId);

        final String deobfuscatedThrottlingParameter = YoutubeApiDecoder.decodeThrottlingParameter(
                cachedPlayerId, obfuscatedThrottlingParameter);

        return streamingUrl.replace(
                obfuscatedThrottlingParameter, deobfuscatedThrottlingParameter);
    }

    /**
     * Get the current cache size of decoded parameters from the API decoder.
     *
     * @return the current cache size
     */
    public static int getThrottlingParametersCacheSize() {
        return YoutubeApiDecoder.getCacheSize();
    }

    /**
     * Clear all caches.
     *
     * <p>
     * This method will clear the cached player ID and API decoder cache.
     * </p>
     *
     * <p>
     * The next time {@link #getSignatureTimestamp(String)},
     * {@link #deobfuscateSignature(String, String)} or
     * {@link #getUrlWithThrottlingParameterDeobfuscated(String, String)} is called, the player ID
     * will be fetched again.
     * </p>
     */
    public static void clearAllCaches() {
        cachedPlayerId = null;
        cachedSignatureTimestamp = null;
        YoutubeApiDecoder.clearCache();
    }

    /**
     * Clear all cached throttling parameters from the API decoder.
     *
     * <p>
     * The API decoder will be called again for these parameters if streaming URLs containing them
     * are passed in the future.
     * </p>
     */
    public static void clearThrottlingParametersCache() {
        YoutubeApiDecoder.clearCache();
    }

    /**
     * Batch deobfuscate multiple signatures and throttling parameters in a single API call.
     *
     * <p>
     * This method is more efficient than calling {@link #deobfuscateSignature(String, String)}
     * and {@link #getUrlWithThrottlingParameterDeobfuscated(String, String)} individually for
     * each stream, as it combines all parameters into a single API request.
     * </p>
     *
     * @param videoId          the video ID used to get the JavaScript base player ID
     * @param signatures       list of obfuscated signatures to decode (can be null or empty)
     * @param throttlingParams list of obfuscated throttling parameters to decode (can be null or empty)
     * @return a BatchDecodeResult containing decoded signatures and throttling parameters
     * @throws ParsingException if the extraction of the player ID or the API call failed
     */
    @Nonnull
    public static YoutubeApiDecoder.BatchDecodeResult deobfuscateBatch(
            @Nonnull final String videoId,
            @Nullable final List<String> signatures,
            @Nullable final List<String> throttlingParams) throws ParsingException {
        extractPlayerIdIfNeeded(videoId);
        return YoutubeApiDecoder.decodeBatch(cachedPlayerId, signatures, throttlingParams);
    }

    /**
     * Extract the player ID if it isn't already cached.
     *
     * @param videoId the video ID used to get the JavaScript base player ID (an empty one can be
     *                passed, even it is not recommend in order to spoof better official YouTube
     *                clients)
     * @throws ParsingException if the extraction of the player ID failed
     */
    private static void extractPlayerIdIfNeeded(@Nonnull final String videoId)
            throws ParsingException {
        if (cachedPlayerId == null) {
            cachedPlayerId = YoutubeJavaScriptExtractor.extractPlayerId(videoId);
        }
    }
}
