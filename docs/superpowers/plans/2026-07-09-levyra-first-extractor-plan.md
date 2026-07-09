# Levyra-First Extractor Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add a safe Levyra-first API that accelerates video/audio resolving with SABR preflight caching, in-flight request dedupe, and an explicit streaming downloader contract.

**Architecture:** Add a new `org.schabi.newpipe.extractor.levyra` package so existing NewPipe extractor APIs remain untouched. The resolver first tries SABR preflight when the active downloader supports real streaming responses, then falls back to `StreamInfo.getInfo(...)` with diagnostics instead of failing hard.

**Tech Stack:** Java 17, Gradle, JUnit 4, existing YouTube SABR classes, existing NewPipe downloader abstraction.

---

### Task 1: Test Harness

**Files:**
- Modify: `build.gradle`
- Modify: `extractor/build.gradle`

- [ ] Enable the existing `test` task and add JUnit 4 to the extractor module.
- [ ] Run `:extractor:test` and verify a newly added test can fail before implementation.

### Task 2: Streaming Contract

**Files:**
- Modify: `extractor/src/main/java/org/schabi/newpipe/extractor/downloader/Downloader.java`
- Test: `extractor/src/test/java/org/schabi/newpipe/extractor/levyra/LevyraYoutubeResolverTest.java`

- [ ] Add `supportsStreamingResponses()` defaulting to `false`.
- [ ] Add a test that a resolver request requiring streaming skips SABR preflight when the downloader does not support streaming.

### Task 3: Levyra Resolver API

**Files:**
- Create: `extractor/src/main/java/org/schabi/newpipe/extractor/levyra/LevyraResolveRequest.java`
- Create: `extractor/src/main/java/org/schabi/newpipe/extractor/levyra/LevyraResolvedStream.java`
- Create: `extractor/src/main/java/org/schabi/newpipe/extractor/levyra/LevyraResolveDiagnostics.java`
- Create: `extractor/src/main/java/org/schabi/newpipe/extractor/levyra/LevyraFormatPolicy.java`
- Create: `extractor/src/main/java/org/schabi/newpipe/extractor/levyra/LevyraYoutubeResolver.java`
- Test: `extractor/src/test/java/org/schabi/newpipe/extractor/levyra/LevyraYoutubeResolverTest.java`

- [ ] Add request/result/diagnostics value objects.
- [ ] Add resolver cache keyed by video id, profile, locale, and country.
- [ ] Add in-flight dedupe with `CompletableFuture`.
- [ ] Select audio/video formats from SABR preflight and return diagnostics.
- [ ] Preserve fallback path through `StreamInfo`.

### Task 4: Verification

**Files:**
- All changed files.

- [ ] Run `:extractor:test`.
- [ ] Run `:extractor:compileJava`.
- [ ] Run `git diff --check`.
