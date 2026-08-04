package org.schabi.newpipe.extractor.services.youtube.sabr;

import javax.annotation.Nonnull;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Deque;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

/** Observability state for a SABR session; it does not participate in requests. */
final class YoutubeSabrSessionDiagnostics {
    private static final int MAX_DIAGNOSTIC_CHARS = 32 * 1024;
    private static final int MAX_TRACE_EVENTS = 1024;

    private final Deque<String> diagnosticEvents = new ArrayDeque<>();
    private int diagnosticChars;
    private volatile long totalResponseBytes;
    private volatile long maxResponseBytes;
    private volatile long maxUmpPartBytes;
    private volatile long maxMediaPartPayloadBytes;
    private volatile long maxSegmentBytes;
    private volatile int maxSegmentsPerResponse;
    private final AtomicInteger maxStreamProtectionStatus = new AtomicInteger(-1);
    private volatile boolean traceEnabled;
    private final Object traceLock = new Object();
    private long traceResponseBytes;
    private long traceMediaPayloadBytes;
    private long traceControlPayloadBytes;
    private long traceUmpOverheadBytes;
    private final Deque<String> traceResponses = new ArrayDeque<>();

    synchronized void addEvent(@Nonnull final String event) {
        final String bounded = event.length() > MAX_DIAGNOSTIC_CHARS
                ? event.substring(0, MAX_DIAGNOSTIC_CHARS) : event;
        while (!diagnosticEvents.isEmpty()
                && diagnosticChars + bounded.length() > MAX_DIAGNOSTIC_CHARS) {
            diagnosticChars -= diagnosticEvents.removeFirst().length();
        }
        diagnosticEvents.addLast(bounded);
        diagnosticChars += bounded.length();
    }

    @Nonnull
    synchronized String getTrace() {
        return String.join(" | ", diagnosticEvents);
    }

    void recordResponse(@Nonnull final YoutubeSabrResponse result, final int requestNumber) {
        totalResponseBytes += result.getResponseBytes();
        maxResponseBytes = Math.max(maxResponseBytes, result.getResponseBytes());
        maxUmpPartBytes = Math.max(maxUmpPartBytes, result.getMaxPartBytes());
        maxMediaPartPayloadBytes = Math.max(maxMediaPartPayloadBytes,
                result.getMaxMediaPartPayloadBytes());
        maxSegmentBytes = Math.max(maxSegmentBytes, result.getMaxSegmentBytes());
        maxSegmentsPerResponse = Math.max(maxSegmentsPerResponse, result.getSegmentCount());
        maxStreamProtectionStatus.accumulateAndGet(
                result.getStreamProtectionStatus(), Math::max);
        if (!traceEnabled) {
            return;
        }
        final long umpOverheadBytes = Math.max(0,
                result.getResponseBytes() - result.getTotalPayloadBytes());
        synchronized (traceLock) {
            traceResponseBytes += result.getResponseBytes();
            traceMediaPayloadBytes += result.getMediaPayloadBytes();
            traceControlPayloadBytes += result.getControlPayloadBytes();
            traceUmpOverheadBytes += umpOverheadBytes;
            addBoundedTraceEvent(traceResponses, "request=" + requestNumber
                    + ",elapsedMs=" + result.getRequestElapsedMs()
                    + ",firstSegmentMs=" + result.getFirstSegmentElapsedMs()
                    + ",bytes=" + result.getResponseBytes()
                    + ",mediaBytes=" + result.getMediaPayloadBytes()
                    + ",segments=" + result.getSegmentCount());
        }
    }

    void setTraceEnabled(final boolean enabled) {
        traceEnabled = enabled;
    }

    long getTotalResponseBytes() { return totalResponseBytes; }
    long getMaxResponseBytes() { return maxResponseBytes; }
    long getMaxUmpPartBytes() { return maxUmpPartBytes; }
    long getMaxMediaPartPayloadBytes() { return maxMediaPartPayloadBytes; }
    long getMaxSegmentBytes() { return maxSegmentBytes; }
    int getMaxSegmentsPerResponse() { return maxSegmentsPerResponse; }
    int getMaxStreamProtectionStatus() { return maxStreamProtectionStatus.get(); }

    @Nonnull
    String getMemorySummary(final int requestNumber) {
        return "requestNumber=" + requestNumber
                + ", totalResponseBytes=" + totalResponseBytes
                + ", maxResponseBytes=" + maxResponseBytes
                + ", maxUmpPartBytes=" + maxUmpPartBytes
                + ", maxMediaPartPayloadBytes=" + maxMediaPartPayloadBytes
                + ", maxSegmentBytes=" + maxSegmentBytes
                + ", maxSegmentsPerResponse=" + maxSegmentsPerResponse;
    }

    @Nonnull
    YoutubeSabrSession.TraceSnapshot snapshot(final int requestNumber) {
        synchronized (traceLock) {
            return new YoutubeSabrSession.TraceSnapshot(traceResponseBytes, traceMediaPayloadBytes,
                    traceControlPayloadBytes, traceUmpOverheadBytes, 0, requestNumber,
                    Collections.emptyList(), Collections.emptyList(),
                    new ArrayList<>(traceResponses));
        }
    }

    private static void addBoundedTraceEvent(@Nonnull final Deque<String> events,
                                             @Nonnull final String value) {
        if (events.size() >= MAX_TRACE_EVENTS) {
            events.removeFirst();
        }
        events.addLast(value);
    }
}
