package org.schabi.newpipe.extractor.levyra;

import org.schabi.newpipe.extractor.NewPipe;
import org.schabi.newpipe.extractor.downloader.Downloader;
import org.schabi.newpipe.extractor.exceptions.ExtractionException;
import org.schabi.newpipe.extractor.services.youtube.sabr.YoutubeSabrInfo;
import org.schabi.newpipe.extractor.services.youtube.sabr.YoutubeSabrProbe;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.io.IOException;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ConcurrentHashMap;

public final class LevyraYoutubeResolver {
    private static final long DEFAULT_PREFLIGHT_TTL_MS = 90_000L;

    private final Downloader downloader;
    private final SabrPreflightFetcher preflightFetcher;
    private final LevyraFormatPolicy formatPolicy;
    private final long preflightTtlMs;
    private final Map<String, CacheEntry> preflightCache = new ConcurrentHashMap<>();
    private final Map<String, CompletableFuture<LevyraSabrPreflight>> inFlight =
            new ConcurrentHashMap<>();

    public LevyraYoutubeResolver() {
        this(NewPipe.getDownloader(), null, null, DEFAULT_PREFLIGHT_TTL_MS);
    }

    public LevyraYoutubeResolver(@Nonnull final Downloader downloader) {
        this(downloader, null, null, DEFAULT_PREFLIGHT_TTL_MS);
    }

    public LevyraYoutubeResolver(@Nonnull final Downloader downloader,
                                 @Nullable final SabrPreflightFetcher preflightFetcher,
                                 @Nullable final LevyraFormatPolicy formatPolicy,
                                 final long preflightTtlMs) {
        this.downloader = downloader;
        this.preflightFetcher = preflightFetcher == null
                ? new DefaultSabrPreflightFetcher() : preflightFetcher;
        this.formatPolicy = formatPolicy == null ? new LevyraFormatPolicy() : formatPolicy;
        this.preflightTtlMs = Math.max(1_000L, preflightTtlMs);
    }

    @Nonnull
    public LevyraResolvedStream resolveSabrPreflight(@Nonnull final LevyraResolveRequest request) {
        final long startedNs = System.nanoTime();
        final boolean streamingSupported = downloader.supportsStreamingResponses();
        if (request.isRequireStreamingDownloader() && !streamingSupported) {
            return unresolved(
                    LevyraResolvedStream.Source.STREAMING_DOWNLOADER_REQUIRED,
                    startedNs,
                    streamingSupported,
                    false,
                    false,
                    "Downloader does not support true streaming responses");
        }

        final String key = request.cacheKey();
        final CacheEntry cached = preflightCache.get(key);
        if (cached != null && cached.isFresh()) {
            return fromPreflight(request, cached.preflight, startedNs, streamingSupported,
                    true, false);
        }

        final CompletableFuture<LevyraSabrPreflight> created = new CompletableFuture<>();
        final CompletableFuture<LevyraSabrPreflight> existing = inFlight.putIfAbsent(key, created);
        final boolean joined = existing != null;
        final CompletableFuture<LevyraSabrPreflight> future = joined ? existing : created;
        if (!joined) {
            try {
                final LevyraSabrPreflight preflight = preflightFetcher.fetch(request);
                preflightCache.put(key, new CacheEntry(preflight, System.currentTimeMillis()
                        + preflightTtlMs));
                created.complete(preflight);
            } catch (final Exception error) {
                created.completeExceptionally(error);
            } finally {
                inFlight.remove(key, created);
            }
        }

        try {
            return fromPreflight(request, future.join(), startedNs, streamingSupported,
                    false, joined);
        } catch (final CompletionException error) {
            return unresolved(
                    LevyraResolvedStream.Source.UNRESOLVED,
                    startedNs,
                    streamingSupported,
                    false,
                    joined,
                    rootMessage(error));
        }
    }

    @Nonnull
    private LevyraResolvedStream fromPreflight(
            @Nonnull final LevyraResolveRequest request,
            @Nonnull final LevyraSabrPreflight preflight,
            final long startedNs,
            final boolean streamingSupported,
            final boolean cacheHit,
            final boolean inFlightJoin) {
        final LevyraSabrPreflight.Format audio = formatPolicy.selectAudio(preflight, request);
        final LevyraSabrPreflight.Format video = request.isVideoMode()
                ? formatPolicy.selectVideo(preflight, request) : null;
        final boolean resolved = audio != null && (!request.isVideoMode() || video != null);
        final String reason = resolved ? null : "SABR preflight did not expose playable formats";
        final LevyraResolveDiagnostics diagnostics = diagnostics(startedNs, streamingSupported,
                cacheHit, inFlightJoin, reason);
        final LevyraResolvedStream.Builder builder = LevyraResolvedStream
                .builder(resolved ? LevyraResolvedStream.Source.SABR_PREFLIGHT
                        : LevyraResolvedStream.Source.UNRESOLVED, diagnostics)
                .resolved(resolved);
        if (audio != null) {
            builder.audio(audio.getUrl(), audio.getItag());
        }
        if (video != null) {
            builder.video(video.getUrl(), video.getItag(), video.getHeight());
        }
        return builder.build();
    }

    @Nonnull
    private LevyraResolvedStream unresolved(
            @Nonnull final LevyraResolvedStream.Source source,
            final long startedNs,
            final boolean streamingSupported,
            final boolean cacheHit,
            final boolean inFlightJoin,
            @Nonnull final String reason) {
        return LevyraResolvedStream
                .builder(source, diagnostics(startedNs, streamingSupported, cacheHit, inFlightJoin,
                        reason))
                .resolved(false)
                .build();
    }

    @Nonnull
    private static LevyraResolveDiagnostics diagnostics(
            final long startedNs,
            final boolean streamingSupported,
            final boolean cacheHit,
            final boolean inFlightJoin,
            @Nullable final String reason) {
        final long elapsedMs = java.util.concurrent.TimeUnit.NANOSECONDS.toMillis(
                System.nanoTime() - startedNs);
        return new LevyraResolveDiagnostics(streamingSupported, cacheHit, inFlightJoin,
                elapsedMs, reason);
    }

    @Nonnull
    private static String rootMessage(@Nonnull final CompletionException error) {
        final Throwable cause = error.getCause() == null ? error : error.getCause();
        return cause.getMessage() == null ? cause.getClass().getSimpleName() : cause.getMessage();
    }

    public interface SabrPreflightFetcher {
        @Nonnull
        LevyraSabrPreflight fetch(@Nonnull LevyraResolveRequest request)
                throws IOException, ExtractionException;
    }

    private static final class DefaultSabrPreflightFetcher implements SabrPreflightFetcher {
        @Nonnull
        @Override
        public LevyraSabrPreflight fetch(@Nonnull final LevyraResolveRequest request)
                throws IOException, ExtractionException {
            final YoutubeSabrInfo info = YoutubeSabrProbe.fetchSabrInfo(
                    request.getVideoId(),
                    request.getProfile(),
                    request.getLocalization(),
                    request.getContentCountry());
            return LevyraSabrPreflight.fromInfo(info);
        }
    }

    private static final class CacheEntry {
        private final LevyraSabrPreflight preflight;
        private final long expiresAtMs;

        private CacheEntry(@Nonnull final LevyraSabrPreflight preflight, final long expiresAtMs) {
            this.preflight = preflight;
            this.expiresAtMs = expiresAtMs;
        }

        private boolean isFresh() {
            return System.currentTimeMillis() < expiresAtMs;
        }
    }
}
