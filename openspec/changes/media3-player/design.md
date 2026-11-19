# Design

## Context

See proposal.md for why. The pieces this change builds on:

- The YouTube layer's `StreamLinksRemoteDataSource.streamLinks(videoId)` returns StreamLinks:
  - audio-only formats and video formats, each one file that ranged requests can read
  - an HLS manifest for live streams
  - when the links expire

  NewPipeExtractor v0.27.1 makes every link with its visionOS client, which gave no format with both sound and picture in the check. So a video plays as one audio and one video format together (docs/architecture.md, "Stream links").
- Requests to googlevideo.com must send the User-Agent of the client that made the link. Today only the daily check fetches from it, through its own OkHttp client:
  - It sends only HTTPS requests to googlevideo.com and its subdomains.
  - It follows at most three redirects, checking each one's host first.
  - It takes the User-Agent from NewPipeExtractor's YoutubeParsingHelper.

  docs/architecture.md's "Network access" leaves "what the player sends" to this plan.
- AGENTS.md's product rules send network calls to YouTube through the YouTube layer, and Phase 3 put all access to YouTube in that one module. Only `:core:youtube` may use NewPipeExtractor.
- NewPipe v0.29.3's player, which uses NewPipeExtractor v0.27.1, fetches googlevideo.com as follows ([YoutubeHttpDataSource](https://github.com/TeamNewPipe/NewPipe/blob/v0.29.3/app/src/main/java/org/schabi/newpipe/player/datasource/YoutubeHttpDataSource.java)):
  - every request as a POST with the two-byte body `x\0`
  - for progressive formats, the byte range in a Range header and an `rn` parameter that numbers the requests ([PlayerDataSource](https://github.com/TeamNewPipe/NewPipe/blob/v0.29.3/app/src/main/java/org/schabi/newpipe/player/helper/PlayerDataSource.java)): `rn` is added only to `/videoplayback` URLs that don't have one
  - for HLS, no `rn`
  - the visionOS User-Agent for visionOS links
  - no Accept-Language
  - at most 20 redirects
- ADR 0002 keeps playback state out of screen state: the player lives in a MediaSessionService and exposes its state as its own stream, updated only as often as the visible UI needs.
- The maintainer decided on November 19, 2026:
  - at most 480p on metered networks, and the best up to 1080p on unmetered ones, with no setting yet
  - playback starts when the screen opens
  - closing the screen stops playback, and leaving the app pauses it, until #43 and #44
- Media3 1.11.1, from its source at tag 1.11.1:
  - MediaSessionService posts a media notification and starts a foreground service whenever its player plays, unless it overrides `onUpdateNotification`.
  - A MediaController sends a MediaItem to the session as a Bundle, which drops the `tag`, and Player has no `setMediaSource`. So the session resolves items itself, in `MediaSession.Callback.onSetMediaItems`.
  - The session copies an item's `mediaId` into the platform session's METADATA_KEY_MEDIA_ID (LegacyConversions), which other apps with notification access can read.

## Goals / Non-Goals

**Goals:**

- One ExoPlayer in a MediaSessionService that doesn't depend on any screen, for #43 and #44 to reuse.
- Playback on the video screen with the frames' controls, and the playback state as its own stream.
- Every request for video bytes made by the YouTube layer's code, held to googlevideo.com over HTTPS.

**Non-Goals:**

- Background play, the media notification, and control from headset buttons or other apps. #43 adds them.
- The mini player and the fullscreen player. #44 adds them, with the frames' fullscreen button.
- A quality setting, captions, playback speed and audio track choice.
- Resuming where the user left off, which Phase 5 adds, and playing downloads, which Phase 6 adds.

## Decisions

### The YouTube layer makes the video requests

`:core:youtube` gains a public `MediaRequests`, an OkHttp `Call.Factory` for googlevideo.com, which `:core:data` provides to the player. Media3's OkHttpDataSource.Factory takes a Call.Factory, so the player's data source sends every request through the layer's code. The layer's interceptor:

- **Hosts.** It sends only HTTPS requests to googlevideo.com and its subdomains. The client follows no redirects itself. The interceptor follows at most 20, the same as NewPipe's, checks each Location's host before sending it, and closes each redirect's response, as the daily check's FirstBytes does, so OkHttp never warns about a leaked body, a warning that names the host.
- **Request shape.** It sends what NewPipe's player sends, as listed under Context.
  - A request for a `/videoplayback` URL becomes a POST with the body `x\0`, keeps Media3's Range header, and gets an `rn` parameter, numbered from 0 for each MediaRequests, when the URL has none.
  - A request for an HLS manifest or segment stays as NewPipe sends it, a POST with the same body and no `rn`.
- **User-Agent.** It sets the User-Agent from the link's client: `YoutubeParsingHelper.getVisionOsUserAgent` for a visionOS link, which `isVisionOsStreamingUrl` recognises.
  - It passes the layer's own Localization, en-GB, the one the player request that made the links named, so the User-Agent matches it. ADR 0009 asks every request to YouTube to name en-GB, so it also sends `Accept-Language: en-GB`. The bytes have no text, so the language changes nothing in them.
  - A link of any other client fails before anything is sent, because only a NewPipeExtractor update can make the layer know its User-Agent.
- **Refusals.** A refused request fails with MediaRequestRefused, a new IOException in `:core:model` whose message is constant text. The player maps it to YOUTUBE, BROKEN's words, so the screen says that YouTube isn't loading right now. Its reason is an enum: another host, http, too many redirects, or an unknown client.

- **Why the layer.** It keeps all of Dewfall's requests to YouTube in one module, as Phase 3 and AGENTS.md's product rules ask. The User-Agent comes from NewPipeExtractor, which only the layer may use. A NewPipeExtractor update that changes how links must be fetched then changes one module.
- **Alternative:** a client in `:core:playback`, with a User-Agent field on StreamLinks. Rejected, because it would put YouTube's request rules outside the layer, which the product rule forbids without the maintainer's approval.
- **OkHttp's version.** media3-datasource-okhttp asks for OkHttp 4.12.0, and the app ships 5.5.0, which Gradle keeps. OkHttp 5 keeps the binary API of OkHttp 4's non-alpha APIs ([OkHttp changelog](https://github.com/square/okhttp/blob/parent-5.5.0/CHANGELOG.md), 5.0.0-alpha.17: "We don't break binary compatibility with non-alpha APIs"). The build and the emulator checks run with 5.5.0.
- **Exposing OkHttp.** `:core:youtube` exposes OkHttp's Call.Factory type, so OkHttp becomes an api dependency of it. `:core:data` passes it on only to `:core:playback`, through a Hilt binding with a qualifier.

### A new module, :core:playback

`:core:playback` is an Android library with:
- PlaybackService, the MediaSessionService
- the decodable check and the network check
- PlaybackConnection, which hands the screen a MediaController

It depends on `:core:data`, for stream links and MediaRequests, `:core:model` and `:core:logging`. `:feature:player-ui` depends on it. The service's manifest entry merges from the library.

- **Why a core module.** Playback must not depend on a screen, and #43 and #44 use the same player. Features never depend on each other.

### Playing a video by an opaque ID

PlaybackConnection's `prepare(videoId, play)` makes a playback ID, a random UUID's text, and keeps the map from it to the VideoId in PlaybackItems, a singleton inside the process. It then sets a MediaItem whose `mediaId` is the playback ID, with no metadata and no URI. The session's `onSetMediaItems`:

1. looks the VideoId up in PlaybackItems
2. fetches StreamLinks through StreamLinksRepository in `:core:data`
3. chooses the formats
4. returns a MediaItem with the same playback ID, a placeholder URI `dewfall-playback:<playback ID>`, and the chosen links in its `tag`

The session's ExoPlayer has a MediaSource.Factory of `:core:playback`'s own, which reads the tag:
- two progressive files become a `MergingMediaSource(adjustPeriodTimeOffsets = true, clipDurations = true, ...)` of two ProgressiveMediaSources, as Media3's [media sources guide](https://developer.android.com/media/media3/exoplayer/media-sources) advises
- an HLS manifest becomes an HlsMediaSource
- a format with sound and picture becomes one ProgressiveMediaSource

The callback returns a Guava SettableFuture, which Guava already brings, completed from a coroutine in the service's scope, so no kotlinx-coroutines-guava is needed.

- **Why an opaque ID.** The platform session copies the `mediaId`, and other apps can read it, so a video ID there would leave the app. The playback ID says nothing about the video, no item has a title, and no stream link is ever an item's URI.
- **Who may connect.** The session's `onConnect` accepts only controllers from the app's own package, so headset buttons and other apps can't control playback until #43 decides they may. The manifest removes media3-session's BluetoothValidationActivity with `tools:node="remove"`, because there is no Bluetooth control yet. #43 adds it back if it keeps Bluetooth control.

### No notification until #43

PlaybackService overrides `onUpdateNotification` to do nothing, so it posts no notification and never starts a foreground service. Its manifest entry isn't exported, has no foregroundServiceType, and declares no foreground service permission. The app's own MediaController binds to it. Android may stop the service while the app is in the background, which is fine, because leaving the app pauses playback.

### Starting, pausing and stopping

PlaybackConnection is a Hilt singleton. It builds one MediaController for the application's context the first time a screen asks, and keeps it while the process lives, so a configuration change finds it as it was. The video screen's route:

- **Starts playback** the first time its back stack entry opens: `prepare(videoId, play = true)`. A rememberSaveable flag marks it as started, and it saves the playback ID too.
- **After process death** the flag comes back, but PlaybackItems is empty, so the route prepares a new playback ID with `play = false`, and the user presses play.
- **Pauses** in the entry's ON_STOP, unless the activity is only being recreated for a configuration change, which `Activity.isChangingConfigurations` says. It doesn't resume on ON_START, so coming back leaves the video paused.
- **Stops** when the entry's ViewModel is cleared. Navigation 3 does that only after the entry has left the back stack and its exit animation, so a video opened meanwhile could already be playing. So it clears the player's items only if the current item's `mediaId` is this entry's playback ID.

### Choosing the formats

FormatChoice is a pure function in `:core:model`, next to StreamLinks, as TimeAgo is: it needs nothing from Android, so ADR 0006 puts it in a plain Kotlin/JVM module. It takes the StreamLinks, the limit, whether the network is metered, and a function that says whether the phone decodes a format, and how: in hardware, in software, or not.

- **The limit.** The shorter side of the picture is at most 480 pixels on a metered network and at most 1080 on an unmetered one. That keeps portrait videos at the same quality as landscape ones: a 1080x1920 video counts as 1080p. A format with no size counts as larger than any limit.
- **Video.**
  1. Among decodable video formats within the limit, take the largest shorter side.
  2. Among those, prefer a format decoded in hardware over one decoded in software, which saves battery.
  3. Then take the lowest bitrate.
  4. If no format is within the limit, take the decodable one with the smallest shorter side.
  5. If none is decodable, fail with NO_DECODABLE_FORMAT.
- **Audio.**
  1. Take the formats of the ORIGINAL track. If no format has a track, take them all. If no track is ORIGINAL, take the track of the first audio format in YouTube's order.
  2. Among the decodable ones, take the highest bitrate on an unmetered network and the lowest on a metered one.
- **Both at once.** A chosen video format with `includesAudio` plays on its own.
- **Live.** A live stream plays its HLS manifest. The player's DefaultTrackSelector gets the same limit as a maximum video size in both orientations, and ExoPlayer picks among the manifest's variants.

The service supplies the two Android parts:

- **Metered.** ConnectivityManager's NetworkCapabilities: NET_CAPABILITY_NOT_METERED means unmetered. It is read when playback starts, and kept until playback starts again.
- **Decodable.** The same check that ExoPlayer's video and audio renderers make, so the choice and the renderer agree:
  - `MimeTypes.getMediaMimeType` turns the format's codec into a sample MIME type, such as video/x-vnd.on2.vp9 for vp9.
  - `MediaCodecUtil.getDecoderInfos` lists the decoders for it, and `MediaCodecInfo.isFormatSupported` checks each with a Format built from the codec, size and frame rate.
  - `MediaCodecInfo.hardwareAccelerated` says whether a supporting decoder works in hardware.

Alternative: let ExoPlayer choose among every format. Rejected, because progressive formats aren't an adaptive set, so ExoPlayer can only play the ones it is given.

### Expired links and errors

The service's player listener handles a source error once per item:

- **A refused link.** On HttpDataSource.InvalidResponseCodeException with 403 or 410, it fetches new links, swaps the item for the new one, seeks to the old position, and plays if it was playing. If the new links fail too, the error stands.
- **No retries of 403 or 410.** A LoadErrorHandlingPolicy of `:core:playback`'s own doesn't retry them, so the refresh happens at once. Other errors keep DefaultLoadErrorHandlingPolicy's retries.
- **The screen's error category.** PlaybackConnection maps the player's PlaybackException, and a failed resolution, to a PlaybackFailure, as its own stream. The mapping is a pure function with its own tests:
  - NETWORK: an IO network error, or a resolution that failed with NETWORK
  - YOUTUBE: MediaRequestRefused, BLOCKED or BROKEN, or a refused link after the refresh
  - UNAVAILABLE and the other per-video categories from the resolution
  - NOT_DECODABLE: the phone decodes no format
  - OTHER: anything else

  MediaRequestRefused is recognised by its class first, because Media3 reports any IOException from a data source as a network error.
- **What the screen says.** The same words as the details' failures, plus "This video can't play on this phone." for NOT_DECODABLE and "This video stopped playing." for OTHER, words the maintainer approved with this change. "Try again" shows for NETWORK, YOUTUBE and OTHER, and prepares the video again, playing from the last position.
- **When both fail.** The details' failure shows in the details' place, and the playback failure in the video area. Each "Try again" retries only its own part.
- **Logs.** Media3's own logging is off: `androidx.media3.common.util.Log.setLogLevel(Log.LOG_LEVEL_OFF)`.
  - It is set in PlaybackConnection's constructor and in PlaybackService's `onCreate`, before either makes its first Media3 call.
  - It is off because Media3's error lines append each throwable's message ([Log.java at 1.11.1](https://github.com/androidx/media/blob/1.11.1/libraries/common/src/main/java/androidx/media3/common/util/Log.java), `getThrowableString`), and messages can name the host or carry a link.
  - The service logs one WARN line for each failure through the Logger, with the PlaybackFailure, an enum, as a state field, and Media3's int error code as a count field. Both are allowed by ADR 0007.

### The controls on the screen

The video screen gets a VideoArea at the top of its column, as the "Player" frames show:
- the picture in a 16:9 box, drawn by media3-ui-compose's ContentFrame on its default SurfaceView. Compose's clip doesn't round a SurfaceView, so the box draws its rounded corners as an overlay in the background color.
- the seek bar
- the play and pause button, then the times, such as "18:24 / 1:14:02"

It reads the player through media3-ui-compose's state holders:
- `rememberPlayPauseButtonState` for the button
- `rememberProgressStateWithTickInterval` for the times, ticking once a second
- `rememberProgressStateWithTickCount`, with as many ticks as the seek bar has pixels, for the seek bar

So the position updates only as often as the visible controls need, as ADR 0002 asks, and none of it enters PlayerState. The state holders are `@UnstableApi`, so `:feature:player-ui` opts in, file by file. The seek bar is Material 3's Slider. Live streams show "Live" in place of the seek bar and the times. Loading shows a progress indicator over the box, and a failure shows the Banner in the box's place.

### Hosts and docs

docs/architecture.md:
- **"Network access"** gets googlevideo.com as a host the app contacts while a video plays, with MediaRequests' rules. It also notes that opening a video now sends the video details call and the stream links call, five requests each, one after the other behind the layer's Mutex. So the picture starts after both.
- **"The player"**, a TODO today, describes the service, the opaque IDs, the format choice, MediaRequests and the errors.

ADR 0007 gets a dated note that Media3's logging is off, which its line on other libraries' logs asks for.

## Risks / Trade-offs

- [Guava 33.3.1, which Media3 1.11.1 brings, has a high-severity advisory] → GHSA-xxph-c9ww-hj94, published on October 27, 2026: uncontrolled memory allocation when deserializing untrusted data. A dependency constraint raises Guava to 33.7.2-android, released on September 29, 2026, which fixes it and has no advisory.
- [Media3's `@UnstableApi`] → Parts of `:core:playback` and `:feature:player-ui` use it, so a Media3 update can change them; 1.12.0-alpha01 already changes two state holders. They opt in file by file, and Renovate's updates run the build and the emulator checks.
- [Slower start] → The details call and the stream links call wait for each other behind the layer's Mutex. The video area shows loading meanwhile. A later change can start the links call first if it matters.
- [The request shape follows NewPipe's player] → MediaRequests sends what NewPipe v0.29.3 sends for the same NewPipeExtractor, which is the evidence that full playback works. Task 7.2 plays a long video at 1080p for several minutes and seeks near its end, to check it in Dewfall.
- [Metered choice made at start] → A user who moves from Wi-Fi to mobile data keeps the higher format until the next video.
- [YouTube changes what it accepts] → A NewPipeExtractor update follows YouTube, as for the layer's own requests, and the daily check's first bytes show a refusal first.
- [WAKE_LOCK and ACCESS_NETWORK_STATE in the manifest] → Media3 merges them in, and the metered check needs ACCESS_NETWORK_STATE. Neither asks the user, and docs/architecture.md lists them.

## Migration Plan

None. Nothing is stored, and the app has no release yet.
