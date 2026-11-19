# Tasks

## 1. Decisions the maintainer approved first

- [ ] 1.1 The maintainer approved them on November 19, 2026. Add to the catalog:
  - Media3 1.11.1, on one version: media3-exoplayer, media3-exoplayer-hls, media3-session, media3-ui-compose and media3-datasource-okhttp
  - a dependency constraint that raises Guava to 33.7.2-android

  Give each a comment that says what uses it and the approval date. Verify by comparing `./gradlew -q :app:dependencies --configuration releaseRuntimeClasspath` before and after:
  - List every artifact that is new or changes version, with its license and release date, and check each against AGENTS.md's license and 14-day rules and GitHub's advisory database. Ask the maintainer about any that proposal.md doesn't name before going on.
  - Do the same for debugRuntimeClasspath, and check that both resolve the same versions, with Guava at 33.7.2-android.
  - Compare the merged release manifest before and after. Check that it adds only what proposal.md names: ACCESS_NETWORK_STATE, WAKE_LOCK and the unexported PlaybackService, and no BluetoothValidationActivity.

## 2. The YouTube layer's media requests

- [ ] 2.1 Add MediaRequestRefused to `:core:model`: an IOException with constant text and an enum reason. Then add MediaRequests to `:core:youtube`, the Call.Factory from design.md:
  - an OkHttpClient with no redirects of its own
  - an interceptor that allows only HTTPS to googlevideo.com and its subdomains, follows at most 20 checked redirects, and closes each redirect's response
  - the POST with `x\0` and the `rn` parameter for `/videoplayback`
  - the visionOS User-Agent with the layer's Localization, and `Accept-Language: en-GB`
  - MediaRequestRefused for anything it won't send

  Make OkHttp an api dependency of `:core:youtube`, and provide MediaRequests through `:core:data` with a qualifier. Verify with `./gradlew :core:youtube:test`. Tests drive the interceptor through a fake chain:
  - an allowed visionOS link
  - http
  - another host
  - a redirect to an allowed host, and one to another host
  - 21 redirects, and a redirect without a Location
  - a link of another client
  - `rn` numbering, and a URL that already has `rn`
  - an HLS URL
  - a redirect response that is closed

  Check that no test name or error message holds a link.
- [ ] 2.2 Update docs/architecture.md's "Network access": googlevideo.com as a host the app contacts while playing, MediaRequests' rules with their sources, the User-Agent's Localization, and the slower start. Update "Stream links" for MediaRequests. Verify that the text agrees with the code.

## 3. The stream links repository and the format choice

- [ ] 3.1 Add the `StreamLinksRepository` interface to `:core:data`, with an implementation that calls `StreamLinksRemoteDataSource` and returns its result unchanged, and its Hilt binding. Verify with a unit test against a fake data source, and `./gradlew :core:data:testDebugUnitTest`.
- [ ] 3.2 Add FormatChoice to `:core:model`, as design.md describes. Verify with `./gradlew :core:model:test`, with made-up StreamLinks:
  - the largest decodable format within 480 and within 1080
  - a portrait video
  - hardware over software at the same size, and the lowest bitrate at the same size
  - a phone that can't decode the largest codec
  - no format within the limit, and no decodable format
  - a format with no size
  - the original audio track among dubbed ones, no ORIGINAL track, and no tracks at all
  - the highest and lowest audio bitrate
  - a format with sound and picture
  - a live stream with only a manifest

## 4. The playback module

- [ ] 4.1 Add `:core:playback`, an Android library with Hilt:
  - PlaybackItems
  - the decodable check with MediaCodecUtil and MediaCodecInfo
  - the metered check
  - the pure error mapping to PlaybackFailure, and the pure refresh decision

  Verify with unit tests:
  - 403 and 410 refresh once, and a second refusal stands
  - MediaRequestRefused maps to YOUTUBE, even inside Media3's network error
  - each PlaybackException code and YouTubeError maps to the category in design.md
- [ ] 4.2 Add PlaybackService. Verify that `./gradlew :core:playback:assembleDebug lint detekt` passes, and that the merged manifest matches task 1.1. It has:
  - the MediaSessionService with one ExoPlayer, Media3's logging turned off in `onCreate`, and the empty `onUpdateNotification`
  - `onConnect` accepting only the app's own package
  - `onSetMediaItems` resolving a playback ID with a SettableFuture
  - the MediaSource.Factory for merging, progressive and HLS sources from the tag, with OkHttpDataSource over MediaRequests
  - the LoadErrorHandlingPolicy that doesn't retry 403 or 410
  - the one refresh, and the live track selector limit
  - the unexported manifest entry and the removed BluetoothValidationActivity
- [ ] 4.3 Add PlaybackConnection: the Hilt singleton that turns Media3's logging off in its constructor, builds one MediaController for the application's context, prepares a VideoId under a new playback ID, and exposes the PlaybackFailure stream and one WARN log line for each failure, with the category as a state field and the error code as a count field. Verify with a unit test of the log line's fields, through a fake Logger.

## 5. The video screen

- [ ] 5.1 Add VideoArea to `:feature:player-ui`:
  - the 16:9 ContentFrame with its rounded corner overlay
  - the play and pause button, the seek bar with tick count, and the times with a one-second tick
  - "Live" for live streams
  - the loading indicator
  - the failure Banner with the words from design.md, and "Try again" where it applies

  Opt in to `@UnstableApi` file by file. All strings go in resources, with a content description on every icon and on the seek bar. Verify with previews in light, dark, a font scale of 2 and a tablet-sized window, for playing, paused, live, loading and two failures, with a fake Player.
- [ ] 5.2 Wire PlayerRoute to PlaybackConnection:
  - start on the entry's first opening, saving the playback ID
  - prepare a new playback ID without playing after process death
  - pause on ON_STOP, unless the activity is changing configurations
  - clear the items when the entry's ViewModel is cleared, only if the current item is this entry's playback ID

  PlayerState doesn't change. Verify with unit tests of the start decision and the clear decision, including a second video that started during the first one's exit, through a fake connection.

## 6. Docs

- [ ] 6.1 Update the docs this change makes stale, and verify that each line below now matches the code:
  - AGENTS.md: the Modules rule's list of modules.
  - docs/architecture.md:
    - the module table and graph with `:core:playback`, and `:core:youtube`'s row with MediaRequests and OkHttp as an api dependency
    - `:core:model`'s row with FormatChoice
    - "The player" section from design.md
    - "Reading from YouTube" with the third repository
    - the sentences on where Compose and Hilt are set up
    - "Other libraries' own logs"
  - ADR 0007: a dated note that Media3's logging is off.
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
  - See no notification while playing.
  - Check that `adb shell dumpsys media_session` shows no video ID, title or link.
  - Check that `adb logcat` shows no link, ID or title, and no line from Media3.

## Workflow follow-up

- Archive the change with /opsx:archive as the last commit of the feature's pull request, then run the reviewer again on that commit.
