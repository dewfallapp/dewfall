# Proposal

## Why

Phase 4's goal is that a user can search for any video and watch it. Since [#41](https://github.com/dewfallapp/dewfall/issues/41) a search result opens the video screen, which shows the video's details, but nothing plays. This change adds the player, issue [#42](https://github.com/dewfallapp/dewfall/issues/42): Media3's ExoPlayer inside a media session service, which streams the video on the video screen. The background audio and its notification ([#43](https://github.com/dewfallapp/dewfall/issues/43)), and the mini and fullscreen players ([#44](https://github.com/dewfallapp/dewfall/issues/44)), build on this service.

## What Changes

- **The video plays on the video screen.** A 16:9 video area sits above the title, as the "Player" frames show, with a play and pause button, a seek bar, and the position and length, such as "18:24 / 1:14:02". A live stream shows "Live" instead of the seek bar and the times.
- **It starts on its own.** Opening a video starts playing it, as the maintainer decided on November 19, 2026. A screen that comes back after process death waits for the user to press play, from the start.
- **Quality, as the maintainer decided on November 19, 2026.** On a metered network, such as mobile data, the player streams at most 480p. On an unmetered one, such as Wi-Fi, it streams the best format up to 1080p that the phone can decode. The limit counts the picture's shorter side, so portrait videos get the same quality. On a metered network it also takes the lowest audio bitrate, and on an unmetered one the highest, which the maintainer approved with this change. The choice is made when playback starts, and there is no setting yet.
- **Leaving, as the maintainer decided on November 19, 2026.** Closing the video screen stops playback. Leaving the app pauses it, and coming back leaves it paused. Background play and the mini player come with #43 and #44.
- **Links that expire.** YouTube's stream links stop working after some time. When a link is refused, the player fetches new links once and carries on from the same position.
- **Other sound, as the maintainer approved on November 20, 2026.** The player follows Android's audio focus rules: it makes way when a call or another app plays sound, and pauses when headphones are unplugged.
- **Failures say why.** A video that can't play says why, with "Try again" when trying again can help, as the details' failures do. A phone that can't decode any of the video's formats says so.
- **The media session service.** A new core module holds a MediaSessionService with one ExoPlayer, so playback doesn't depend on the screen, as ADR 0002 and the roadmap ask. The screen talks to it through a MediaController, and reads the playback state as its own stream, never as part of its MVI state. The service shows no notification yet, only the app itself may control it, and headset and Bluetooth buttons do nothing. #43 adds the notification and decides about buttons and other apps.
- **No playback diagnostics.** ExoPlayer would send each video's length, formats and errors to Android's media metrics, which Google may collect. The player turns that off, because the app does no analytics or tracking.
- **No video ID, title or link leaves the app.** The session names each item by a random playback ID, because Android shares a media session's item IDs with other apps that have notification access. Those apps and Bluetooth devices can still see the length, the position and whether it plays.
- **The player's network requests go through the YouTube layer.** The player fetches the video from googlevideo.com, YouTube's video host, which no app code has contacted before. The YouTube layer makes those requests for it, as AGENTS.md's product rules ask: it sends only HTTPS requests to googlevideo.com and its subdomains, checks every redirect's host before following it, and shapes each request as NewPipe's own player does on its fallback path: a progressive file with the User-Agent of the YouTube client that made the link, and a live stream with plain requests.
- **A third repository.** `:core:data` gains a stream links repository that the service calls.

## Capabilities

### New Capabilities

None.

### Modified Capabilities

- `player`: the video screen plays the video, with its controls, quality, starting and stopping, expired links and playback failures.

## Impact

- New module `:core:playback`, an Android library with the media session service, the decodable and network checks, and the connection that the screen uses. `:feature:player-ui` depends on it.
- The YouTube layer gains MediaRequests, the OkHttp client for googlevideo.com that the player's data source uses, so OkHttp becomes an api dependency of `:core:youtube`. No new request to YouTube's other hosts, and the saved responses and the daily check don't change.
- `:core:model` gains FormatChoice, a pure function that picks the formats, and MediaRequestRefused, the error for a request the layer won't send.
- Network: the app newly contacts googlevideo.com over HTTPS while a video plays. docs/architecture.md's "Network access" lists it.
- Logs: Media3's own logging is turned off, because its error messages can name the host or carry a link. The service logs a playback failure through the Logger with its category and Media3's error code only, as ADR 0007 asks.
- Start time: opening a video now sends the video details call and the stream links call, one after the other, so the picture starts after both.
- Dependencies, which the maintainer approved on November 19, 2026, with this change, as the "The maintainer's answers" section of pull request #180's description records, all under the Apache License 2.0. Media3 1.11.1 was released on September 10, 2026:
  - **Media3 1.11.1, released September 10, 2026:** `androidx.media3:media3-exoplayer`, `media3-exoplayer-hls`, `media3-session`, `media3-ui-compose` and `media3-datasource-okhttp`, and the Media3 modules they bring: media3-common, media3-common-ktx, media3-container, media3-database, media3-datasource, media3-decoder and media3-extractor.
  - **Guava:** media3-common brings Guava 33.3.1-android, which has a high-severity advisory, GHSA-xxph-c9ww-hj94. A dependency constraint raises it to 33.7.2-android, released on September 29, 2026, which fixes it.
  - **What Guava brings:**
    - failureaccess 1.0.3, released on March 19, 2025
    - listenablefuture raised from 1.0 to 9999.0-empty-to-avoid-conflict-with-guava, an empty artifact
    - jspecify raised from 1.0.0 to 1.0.1, released on July 29, 2026

    media3-common excludes Guava's annotation artifacts, such as error_prone_annotations and j2objc-annotations, so they don't ship.
  - **AndroidX:**
    - androidx.media 1.7.0, released on November 29, 2023
    - lifecycle-service, at the app's Lifecycle version, 2.11.0, released on June 17, 2026
    - exifinterface 1.3.6, released on February 3, 2023
    - concurrent-futures raised from 1.1.0 to 1.2.0, released on June 12, 2024

  This is the release runtime classpath that Gradle resolved on November 20, 2026, with the five Media3 libraries and the Guava constraint added. Task 1.1 checks it again when they go into the catalog.
  - media3-datasource-okhttp asks for OkHttp 4.12.0. The app already ships OkHttp 5.5.0 through the YouTube layer, and Gradle keeps 5.5.0.
- Manifest: Media3 adds the ACCESS_NETWORK_STATE and WAKE_LOCK permissions. media3-session's exported BluetoothValidationActivity is removed, because there is no Bluetooth control yet. The service itself isn't exported until #43 decides which other apps may control playback.
- Docs: docs/architecture.md's "The player" and "Network access" sections, the module table and graph, AGENTS.md's module list, openspec/config.yaml's list of core modules, and dated notes: in ADR 0007 on Media3's logs, which carries out that ADR's own line; in ADR 0002 on player commands and playback state skipping the screen's MVI; and in ADR 0008 and ADR 0009 on MediaRequests' IOException calls, its client for googlevideo.com, its redirects and its language. The maintainer approved the notes on ADR 0002, ADR 0008 and ADR 0009 on November 20, 2026. AGENTS.md's rule on the layer's functions names MediaRequests as its exception, and its country rule gets the exceptions that ADR 0009's notes record.
