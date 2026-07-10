<div align="center">

# LevyraExtractor

**The extraction engine behind Levyra.**

[![Release](https://img.shields.io/github/v/release/LUC4N3X/LevyraExtractor?style=flat-square&logo=github)](https://github.com/LUC4N3X/LevyraExtractor/releases)
[![JitPack](https://img.shields.io/jitpack/version/com.github.LUC4N3X/LevyraExtractor?style=flat-square)](https://jitpack.io/#LUC4N3X/LevyraExtractor)
[![Java 17](https://img.shields.io/badge/Java-17-ED8B00?style=flat-square&logo=openjdk&logoColor=white)](https://openjdk.org/projects/jdk/17/)
[![GPL-3.0](https://img.shields.io/badge/License-GPL--3.0-blue?style=flat-square)](LICENSE)

A NewPipe/PipePipe-based extractor adapted for Levyra, with modern YouTube stream resolution, SABR support and better playback diagnostics.

[Features](#features) · [Installation](#installation) · [Usage](#basic-usage) · [Credits](#credits) · [License](#license)

</div>

---

## What is it?

LevyraExtractor is the component that helps **Levyra** find metadata and playable media streams.

It builds on the excellent NewPipe and PipePipe ecosystem, then adds the pieces Levyra needs for more reliable YouTube playback: Android VR client handling, SABR preflight, format selection, caching and clear fallback information.

This is an independent downstream project. It is not an official NewPipe, PipePipe or Metrolist repository.

## Features

- Dedicated `LevyraYoutubeResolver`
- Android VR Innertube client
- SABR stream discovery
- Smart audio and video format selection
- Short-lived preflight cache
- Shared resolution for identical concurrent requests
- SponsorBlock and Return YouTube Dislike data
- Streaming downloader support
- Timing, cache and fallback diagnostics

Supported services include **YouTube, SoundCloud, Bandcamp, PeerTube, media.ccc.de, BiliBili and NicoNico**.

```text
Request → Cache → Resolve → Select format → Return stream
```

## Installation

Requires **Java 17** and a concrete NewPipe `Downloader` implementation.

Add JitPack to `settings.gradle.kts`:

```kotlin
dependencyResolutionManagement {
    repositories {
        google()
        mavenCentral()
        maven("https://jitpack.io")
    }
}
```

Add the dependency:

```kotlin
dependencies {
    implementation("com.github.LUC4N3X.LevyraExtractor:extractor:v1.0.0-levyra.6")
}
```

## Basic usage

```java
import org.schabi.newpipe.extractor.NewPipe;
import org.schabi.newpipe.extractor.levyra.LevyraResolveRequest;
import org.schabi.newpipe.extractor.levyra.LevyraResolvedStream;
import org.schabi.newpipe.extractor.levyra.LevyraYoutubeResolver;
import org.schabi.newpipe.extractor.localization.ContentCountry;
import org.schabi.newpipe.extractor.localization.Localization;
import org.schabi.newpipe.extractor.services.youtube.sabr.YoutubeSabrClientProfile;

NewPipe.init(downloader, new Localization("it", "IT"), new ContentCountry("IT"));
NewPipe.setYoutubePlayerClient("android_vr");

LevyraYoutubeResolver resolver = new LevyraYoutubeResolver(downloader);

LevyraResolveRequest request = LevyraResolveRequest
        .forVideoId("VIDEO_ID")
        .setVideoMode(false)
        .setRequireStreamingDownloader(true)
        .setPreferMp4Audio(true)
        .setProfile(YoutubeSabrClientProfile.ANDROID_VR)
        .build();

LevyraResolvedStream stream = resolver.resolveSabrPreflight(request);

if (!stream.isResolved()) {
    throw new IllegalStateException(stream.getDiagnostics().getFallbackReason());
}

String audioUrl = stream.getAudioUrl();
```

The host application provides the `Downloader` and manages timeouts, retries, cancellation and streaming response cleanup.

## Build

```bash
chmod +x gradlew
./gradlew clean build
```

On Windows PowerShell:

```powershell
.\gradlew.bat clean build
```

## Credits

LevyraExtractor exists thanks to the work of many open-source developers. This repository keeps the original copyright and license notices inside the source tree.

| Project | Contribution |
| --- | --- |
| [NewPipeExtractor](https://github.com/TeamNewPipe/NewPipeExtractor) | Original extractor architecture, service contracts and core implementation |
| [NewPipe](https://github.com/TeamNewPipe/NewPipe) | Original application and ecosystem |
| [PipePipe](https://codeberg.org/NullPointerException/PipePipe) | Extended downstream ecosystem |
| [PipePipeExtractor](https://github.com/InfinityLoop1308/PipePipeExtractor) | Main downstream extractor lineage |
| [MetrolistExtractor](https://github.com/MetrolistGroup/MetrolistExtractor) | Reference for modern YouTube client and playback handling |
| [Metrolist](https://github.com/MetrolistGroup/Metrolist) | Open-source YouTube Music client ecosystem |
| [SponsorBlock](https://github.com/ajayyy/SponsorBlock) | Community skip-segment data |
| [Return YouTube Dislike](https://github.com/Anarios/return-youtube-dislike) | Community like and dislike statistics |

Special thanks to **Christian Schabesberger**, **Team NewPipe**, and all PipePipe, Metrolist and downstream contributors whose work remains part of this codebase.

The project also uses open-source libraries including [jsoup](https://jsoup.org/), [OkHttp](https://square.github.io/okhttp/), [Wire](https://square.github.io/wire/), [Protocol Buffers](https://github.com/protocolbuffers/protobuf), [nanojson](https://github.com/mmastrac/nanojson), [Java-WebSocket](https://github.com/TooTallNate/Java-WebSocket), Brotli, Apache Commons and SpotBugs Annotations.

All names, trademarks and source code remain the property of their respective owners and are distributed under their applicable licenses.

## Disclaimer

Upstream services can change without notice, so extraction may occasionally require updates.

Use this project responsibly and respect copyright, service terms and applicable laws. LevyraExtractor is not affiliated with Google, YouTube, Team NewPipe, PipePipe or Metrolist.

## License

Distributed under the **GNU General Public License v3.0**.

See [`LICENSE`](LICENSE) for the complete license text.

---

<div align="center">

Built for **Levyra** by [LUC4N3X](https://github.com/LUC4N3X)

</div>
