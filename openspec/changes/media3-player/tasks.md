# Tasks

## 1. Decisions the maintainer approved first

- [ ] 1.1 The maintainer approved them on November 19, 2026. Add to the catalog:
  - Media3 1.11.1, on one version: media3-exoplayer, media3-exoplayer-hls, media3-session, media3-ui-compose, media3-datasource-okhttp, and media3-common-ktx, which `:feature:player-ui` uses directly for `Player.listen`
  - a dependency constraint that raises Guava to 33.7.2-android

  Give each a comment that says what uses it and the approval date. Verify by comparing `./gradlew -q :app:dependencies --configuration releaseRuntimeClasspath` before and after:
  - List every artifact that is new or changes version, with its license and release date, and check each against AGENTS.md's license and 14-day rules and GitHub's advisory database. Ask the maintainer about any that proposal.md doesn't name before going on.
  - Do the same for debugRuntimeClasspath, and check that both resolve the same versions, with Guava at 33.7.2-android.
  - Compare the merged release manifest before and after. Check that it adds only what proposal.md names: ACCESS_NETWORK_STATE, WAKE_LOCK, and the unexported PlaybackService with no intent filter, and no BluetoothValidationActivity.

## 2. The YouTube layer's media requests

- [ ] 2.1 Add MediaRequestRefused to `:core:model`: an IOException with constant text and an enum reason. Then add MediaRequests to `:core:youtube`, as design.md describes:
  - `progressive()`, a new Call.Factory for each progressive source: a POST with `x\0`, the Range header kept, `TE: trailers`, `rn` counting from 0, and the visionOS User-Agent from the original link, kept across redirects
  - the User-Agent read inside the stream links call, which holds the Mutex, and kept for MediaRequests, and MediaRequests' own check of the `c` parameter, so it calls no NewPipeExtractor code
  - `live()`, the Call.Factory for HLS: plain GET requests with NewPipeDownloader's own User-Agent and no client check
  - for both: an OkHttpClient with no redirects of its own, only HTTPS to googlevideo.com and its subdomains, at most 20 checked redirects, 307 and 308 keeping the method and body while 301 to 303 become a GET with no body, each redirect's response closed, and `Accept-Language: en-GB`
  - MediaRequestRefused for anything they won't send

  Make OkHttp an api dependency of `:core:youtube`. In `:core:data`, which keeps `:core:youtube` as an implementation dependency, add the `@ProgressiveMediaCalls` provider, unscoped, and the `@LiveMediaCalls` provider of OkHttp's Call.Factory. Verify with `./gradlew :core:youtube:test`. Tests drive the interceptors through a fake chain:
  - an allowed visionOS link
  - http
  - another host
  - a redirect to an allowed host keeping the User-Agent, and one to another host
  - 21 redirects, and a redirect without a Location
  - a 300, a 302 and a 307 after a POST
  - a progressive link of another client, and a progressive request before any stream links call
  - `rn` numbering from 0 for each factory, and a URL that already has `rn`
  - a made-up live manifest link in the path form of the saved set VIDEO_BIG_BUCK_BUNNY's `hlsManifestUrl`, with no `c` key, sent as a GET
  - a redirect response that is closed

  Check that no test name or error message holds a link.
- [ ] 2.2 Update docs/architecture.md's "Network access", saying when each fact about NewPipe v0.29.3 and Media3 1.11.1 was checked: googlevideo.com as a host the app contacts while playing, MediaRequests' rules with their sources, NewPipe's DASH main path and the fallback that MediaRequests follows, the User-Agent's Localization, the slower start, and ExoPlayer's platform diagnostics turned off. Update "Stream links" for MediaRequests. Verify that the text agrees with the code.

## 3. The stream links repository and the format choice

- [ ] 3.1 Add the `StreamLinksRepository` interface to `:core:data`, with an implementation that calls `StreamLinksRemoteDataSource` and returns its result unchanged, and its Hilt binding. Verify with a unit test against a fake data source, and `./gradlew :core:data:testDebugUnitTest`.
- [ ] 3.2 Add FormatChoice to `:core:model`, as design.md describes. Verify with `./gradlew :core:model:test`, with made-up StreamLinks:
  - the largest decodable format within 480 and within 1080
  - a portrait video
  - a hardware format chosen over a larger software one within the limit, and the lowest bitrate at the same size
  - a format with no codec, which counts as not decodable
  - a phone that can't decode the largest codec
  - no format within the limit, and no decodable format
  - a format with no size
  - the original audio track among dubbed ones, no ORIGINAL track, and no tracks at all
  - the highest and lowest audio bitrate
  - a format with sound and picture
  - a live stream with only a manifest
  - the live variants within the limit by their shorter side: a 1920x1080 variant within 1080 and outside 480, and the smallest variant when none is within

## 4. The playback module

- [ ] 4.1 Add `:core:playback`, an Android library with Hilt:
  - PlaybackItems
  - the decodable check with MediaCodecUtil and MediaCodecInfo
  - the metered check
  - the pure error mapping to PlaybackFailure, and the pure refresh decision

  Verify with unit tests:
  - 403 and 410 refresh once, and a second refusal stands
  - MediaRequestRefused maps to YOUTUBE, inside the exact chain OkHttpDataSource builds: HttpDataSourceException, IOException, ExecutionException, MediaRequestRefused
  - the shared failure shown once, with one "Try again" for both, as a pure function in `:feature:player-logic`
  - each PlaybackException code and YouTubeError maps to the category in design.md
- [ ] 4.2 Add PlaybackService. Verify that `./gradlew :core:playback:assembleDebug lint detekt` passes, and that the merged manifest matches task 1.1. It has:
  - the MediaSessionService with one ExoPlayer built with `setUsePlatformDiagnostics(false)`, Media3's logging turned off in `onCreate`, and the empty two-argument `onUpdateNotification`
  - a coroutine scope on an injected dispatcher, and PlaybackLog, injected into a field, which takes the Logger in its constructor
  - the audio attributes with audio focus, and pausing when the output becomes noisy
  - `onConnect` accepting only the app's own package, and `onMediaButtonEvent` returning true for every event
  - the service publishing its session to PlaybackSessions
  - `onSetMediaItems` resolving a playback ID with a SettableFuture
  - the MediaSource.Factory for merging, progressive and HLS sources from the tag, with OkHttpDataSource over a new `@ProgressiveMediaCalls` factory for each progressive source and the `@LiveMediaCalls` factory for HLS
  - the LoadErrorHandlingPolicy that doesn't retry 403, 410 or MediaRequestRefused
  - `onStartCommand` returning START_NOT_STICKY, and `onDestroy` releasing the session and the player and clearing PlaybackSessions and PlaybackItems
  - the one refresh, which goes back to the live edge for a live stream, and the live TrackSelectionOverride
  - PlaybackItems' entry removed when its item is replaced, cleared or stopped
  - one WARN log line for each failure, with the category as a state field and the error code as a count field
  - the unexported manifest entry with no intent filter, and the removed BluetoothValidationActivity
- [ ] 4.3 Add PlaybackConnection: the Hilt singleton that turns Media3's logging off in its constructor, starts PlaybackService, builds one MediaController for the application's context from the session's token in PlaybackSessions, starts the service again after it stopped, prepares a VideoId under a new playback ID, and exposes the PlaybackFailure stream. Verify with unit tests, through fakes, of the service's log line's fields and of PlaybackItems dropping an entry when its item is replaced or stopped.

## 5. The video screen

- [ ] 5.1 Add VideoArea to `:feature:player-ui`:
  - the 16:9 ContentFrame with its rounded corner overlay
  - the play and pause button, the seek bar with tick count, and the times with a one-second tick
  - "Live" for live streams
  - the loading indicator
  - the failure Banner with the words from design.md, and "Try again" where it applies
  - `rememberPlaybackStatus` in `:feature:player-ui`, for loading and live

  Opt in to `@UnstableApi` file by file. All strings go in resources, with a content description on every icon and on the seek bar. Verify with previews in light, dark, a font scale of 2 and a tablet-sized window, for playing, paused, live, loading and two failures, with a fake Player.
- [ ] 5.2 Wire PlayerRoute to PlaybackConnection:
  - start on the entry's first opening, saving the playback ID
  - save the position in the route's saved state on ON_STOP
  - when the session is gone, after process death or after Android stopped the service, show the video paused at the saved position and fetch nothing until the user presses play
  - give every new playback ID, from the first start, a play after the session was gone, and "Try again", to both the saved state and PlayerViewModel
  - pause on ON_STOP, unless the activity is changing configurations
  - give PlayerViewModel, which gets PlaybackConnection in its constructor, the entry's playback ID, and have its `onCleared` clear the items only if the current item is that playback ID

  PlayerState doesn't change. Add `startAction` to `:feature:player-logic`. Verify with unit tests of `startAction` for each case, of closing after "Try again" stopping the sound, and of the clear decision, including a second video that started during the first one's exit, through a fake connection.

## 6. Docs

- [ ] 6.1 Update the docs this change makes stale, and verify that each line below now matches the code:
  - AGENTS.md: the Modules rule's list of modules.
  - docs/architecture.md:
    - the module table and graph with `:core:playback`, its edge to `:core:data` and none to `:core:youtube`, `:core:data`'s row with the two Call.Factory providers, and `:core:youtube`'s row with MediaRequests and OkHttp as an api dependency
    - `:core:model`'s row with FormatChoice
    - "The player" section from design.md
    - "Reading from YouTube" with the third repository
    - the sentences on where Compose and Hilt are set up
    - "Other libraries' own logs"
  - ADR 0002, ADR 0007, ADR 0008 and ADR 0009: the dated notes from design.md. Those on ADR 0002, ADR 0008 and ADR 0009 cite the maintainer's approval of November 20, 2026, and each says why it is an exception rather than a new decision. ADR 0007's carries out its own line on Media3's logs.
  - docs/architecture.md's MVI contract: the intents row and the playback state rule, as ADR 0002's note says.
  - AGENTS.md's rule that the player exposes playback state as its own Flow: through its MediaController, which screens read with media3-ui-compose's state holders or as Flows, at the rate the visible UI needs, never in screen state.
  - AGENTS.md's rule on the YouTube layer's functions: MediaRequests as its one exception. Its rule that every request names the device's country: the subtitle download and MediaRequests name none, as ADR 0009's notes say.
  - openspec/config.yaml's list of core modules.

## 7. Checks

- [ ] 7.1 Run every check that the pull request workflow runs: `./gradlew spotlessCheck detekt lint test assembleDebug`. Verify that it passes.
- [ ] 7.2 Check by hand on an emulator with Android 10, and with the newest Android installed:
  - Open a video and see it start playing, with sound and picture together. Seek, pause and play.
  - Play a video an hour or longer for several minutes at 1080p, and seek near its end.
  - Open a live stream and see "Live".
  - Play once on Wi-Fi and once on mobile data only, with `adb shell svc wifi disable`.
  - Turn the network off while playing, see the failure, then "Try again" with it on.
  - Close the screen and hear the sound stop. Go home and come back to a paused video.
  - Resize a split screen window, or change the theme, while it plays, and see it keep playing.
  - End the process in the background, and come back to a video that waits for play.
  - Start music in another app while a video plays, and see the video pause.
  - On a real phone, because an emulator can't do either: unplug headphones, or turn off a Bluetooth headset, while a video plays, and see it pause.
  - See no notification while playing.
  - Play, go home, and send `adb shell input keyevent KEYCODE_MEDIA_PLAY`: no sound. Then end the process with `adb shell am kill io.github.dewfallapp` and send the key again: the app doesn't start, and nothing crashes. Do both on Android 10 and on the newest Android.
  - Check that `adb shell dumpsys media_session` shows no video ID, title or link, both while playing and after a playback failure, whose message the platform session copies.
  - Play, go home, wait longer than Android's background window, or force it with `adb shell am make-uid-idle io.github.dewfallapp` if this Android has it, then come back: paused at the same position, and play goes on from it.
  - Check that `adb logcat` shows no link, ID or title, and no line from Media3's own tags. Lines from Android's media codecs, such as MediaCodec or CCodec, come from the platform, not from Media3.

## Workflow follow-up

- Archive the change with /opsx:archive as the last commit of the feature's pull request, then run the reviewer again on that commit. Update the living player spec's Purpose, which says later changes add playback, in the same commit.
