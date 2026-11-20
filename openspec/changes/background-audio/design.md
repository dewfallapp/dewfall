# Design

## Context

See proposal.md for why. What this change builds on, from [#42](https://github.com/dewfallapp/dewfall/issues/42) and docs/architecture.md's [The player](../../../docs/architecture.md#the-player):

- PlaybackService, a MediaSessionService with one ExoPlayer, posts no notification: `onUpdateNotificationAsync` returns an immediate future without calling super, so it never runs in the foreground.
- Its manifest entry is unexported and has no intent filter. The manifest removes media3-session's BluetoothValidationActivity.
- The session's `onConnectAsync` accepts only controllers from the app's own package. Its `onMediaButtonEvent` returns true for every event, so media buttons do nothing.
- The session's player is SessionPlayer, a ForwardingSimpleBasePlayer that shows the session each item as its random playback ID only, with no metadata, and every error with a constant message.
- PlaybackConnection hands screens a MediaController built from the session's own token. PlaybackItems keeps the VideoId behind each playback ID while its playback lasts.
- The player sets no wake mode, so ExoPlayer 1.11.1 picks WAKE_MODE_LOCAL ([ExoPlayerImpl at 1.11.1](https://github.com/androidx/media/blob/1.11.1/libraries/exoplayer/src/main/java/androidx/media3/exoplayer/ExoPlayerImpl.java)).
- PlayerRoute pauses in ON_STOP, through `PauseOnStop`, unless the activity only changes configurations, and saves the position then. PlayerViewModel's `onCleared` stops playback when the screen closes.
- The maintainer decided on November 19, 2026, that until #43 and #44 closing the video screen stops playback and leaving the app pauses it.

### What Media3 1.11.1 does

Checked on November 21, 2026, in the source at tag 1.11.1:

- **The notification.** MediaSessionService posts a media notification and starts the foreground service, with the type FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK, while its player plays. It stays in the foreground for `DEFAULT_FOREGROUND_SERVICE_TIMEOUT_MS`, 600,000 ms, after playback pauses, stops, fails or ends. It removes the notification when the player has no items or the session goes ([MediaSessionService](https://github.com/androidx/media/blob/1.11.1/libraries/session/src/main/java/androidx/media3/session/MediaSessionService.java), [MediaNotificationManager](https://github.com/androidx/media/blob/1.11.1/libraries/session/src/main/java/androidx/media3/session/MediaNotificationManager.java)). The [background playback guide](https://developer.android.com/media/media3/session/background-playback) says the same.
- **What it shows.** [DefaultMediaNotificationProvider](https://github.com/androidx/media/blob/1.11.1/libraries/session/src/main/java/androidx/media3/session/DefaultMediaNotificationProvider.java) takes its title from the metadata's `title`, its text from `artist`, and its large icon from the artwork through the session's bitmap loader. It sets `VISIBILITY_PUBLIC`, the session's activity as the content intent, and a delete intent that sends stop through the media notification controller. Its channel is `default_channel_id`, named "Now playing" in Media3's resources, its notification ID is 1001, and its small icon is Media3's `media3_icon_circular_play`.
- **Android 13 and later** draw the media controls from the media session's metadata and playback state, not from the notification, and send a tap through `MediaController.TransportControls` ([media controls](https://developer.android.com/media/implement/surfaces/mobile)). The background playback guide warns that a notification provider's own changes "only take effect before API 33". So the notification can show only what the session holds.
- **Other controllers.** Platform controllers connect to the session as legacy controllers the first time they send a command, and each command is checked against the commands that connection accepted ([MediaSessionLegacyStub](https://github.com/androidx/media/blob/1.11.1/libraries/session/src/main/java/androidx/media3/session/MediaSessionLegacyStub.java), `handleControllerTaskOnHandler` and `dispatchSessionTaskWithPlayerCommand`).
  - Most of them go through `onConnectAsync`, so today's own-package rule rejects them.
  - The system UI, `com.android.systemui`, doesn't. While the media notification controller is connected, Media3 answers its connection with the platform session's result, without calling `onConnectAsync`, and treats its commands as the media notification controller's ([MediaSessionImpl](https://github.com/androidx/media/blob/1.11.1/libraries/session/src/main/java/androidx/media3/session/MediaSessionImpl.java), `onConnectOnHandler` and `resolveControllerInfoForCallback`).
  - That platform result, and the actions the platform session advertises, are the media notification controller's commands that the player also has (MediaSessionLegacyStub, `getPlatformConnectionResult` and where it builds the PlaybackStateCompat). The background playback guide's section "Use the media notification controller" restricts that controller's commands in `onConnectAsync` for this reason ([background playback guide](https://developer.android.com/media/media3/session/background-playback)).
  - `ControllerInfo.isTrusted` is true for the app itself, the system, holders of MEDIA_CONTENT_CONTROL or STATUS_BAR_SERVICE, and apps with an enabled notification listener ([MediaSession](https://github.com/androidx/media/blob/1.11.1/libraries/session/src/main/java/androidx/media3/session/MediaSession.java)).
- **Media buttons.** `MediaSessionImpl.onMediaButtonEvent` asks `Callback.onMediaButtonEvent` first. Otherwise it sends play, pause, next, previous, fast forward, rewind or stop as the media notification controller, and a double tap of play and pause as next ([MediaSessionImpl](https://github.com/androidx/media/blob/1.11.1/libraries/session/src/main/java/androidx/media3/session/MediaSessionImpl.java)). Each one is refused when that controller doesn't have the command ([MediaSessionStub](https://github.com/androidx/media/blob/1.11.1/libraries/session/src/main/java/androidx/media3/session/MediaSessionStub.java), `queueSessionTaskWithPlayerCommand`).
- **The media button receiver.** When the app has a manifest MediaButtonReceiver, or on Android 11 and lower, Media3 looks for a service with the `androidx.media3.session.MediaSessionService` action and gives the platform session a `PendingIntent.getForegroundService` for it. With neither, it registers a receiver at runtime, which dies with the process (MediaSessionLegacyStub's constructor). A play on an empty player calls `Callback.onPlaybackResumption`, whose default fails, and then nothing plays (MediaSessionImpl, `handleMediaControllerPlayRequestInternal`).
- **Removing the app from the recent apps.** `onTaskRemoved` keeps the service running only while it is in the foreground and playing, and otherwise calls `pauseAllPlayersAndStopSelf` (MediaSessionService).
- **A refused foreground start.** When Android refuses to start the foreground service, Media3 calls `MediaSessionService.Listener.onForegroundServiceStartNotAllowedException`, and does nothing else (MediaSessionService, `onUpdateNotificationInternal`).
- **Wake mode.** `setWakeMode` holds its locks while the player is ready or buffering with play on, and is meant for playback with the screen off ([ExoPlayer](https://github.com/androidx/media/blob/1.11.1/libraries/exoplayer/src/main/java/androidx/media3/exoplayer/ExoPlayer.java)). [C](https://github.com/androidx/media/blob/1.11.1/libraries/common/src/main/java/androidx/media3/common/C.java) calls WAKE_MODE_LOCAL "suitable for applications that play media with the screen off, but do not require low-latency Wifi access while the screen is on". WAKE_MODE_NETWORK adds a WifiLock, and "on API 33 and below, this mode also puts the Wifi in 'high-power' mode, which may help maintain a steady Wifi connection during screen off playback on some devices". It needs only the WAKE_LOCK permission ([WifiLockManager](https://github.com/androidx/media/blob/1.11.1/libraries/common/src/main/java/androidx/media3/common/util/WifiLockManager.java)), which media3-exoplayer already merges.
- **A deselected track.** A ProgressiveMediaPeriod with no selected track cancels its loading ([ProgressiveMediaPeriod](https://github.com/androidx/media/blob/1.11.1/libraries/exoplayer/src/main/java/androidx/media3/exoplayer/source/ProgressiveMediaPeriod.java), `selectTracks`). So turning the video track off stops fetching a video-only file. A file with sound and picture, which FormatChoice can choose as `Combined`, keeps its sound track selected, so it keeps loading.
- **BluetoothValidationActivity** satisfies a probe of Bluetooth's MediaPlayerList on Android 16 and 17 "to recognize AVRCP browsable media services" ([commit d5c7a55](https://github.com/androidx/media/commit/d5c7a55462), [BluetoothValidationActivity](https://github.com/androidx/media/blob/1.11.1/libraries/session/src/main/java/androidx/media3/session/BluetoothValidationActivity.java)).

### What Android asks

Checked on November 21, 2026:

- A media playback foreground service declares `foregroundServiceType="mediaPlayback"` and the FOREGROUND_SERVICE_MEDIA_PLAYBACK permission, has no runtime prerequisite ([foreground service types](https://developer.android.com/develop/background-work/services/fgs/service-types#media)), and also needs FOREGROUND_SERVICE ([background playback guide](https://developer.android.com/media/media3/session/background-playback)).
- Media session notifications need no POST_NOTIFICATIONS permission ([notification permission](https://developer.android.com/develop/ui/views/notifications/notification-permission#exemptions)). Media notifications stay non-dismissible while their service runs in the foreground, which Android 14 changed for other foreground notifications ([Android 14 behavior changes](https://developer.android.com/about/versions/14/behavior-changes-all#non-dismissable-notifications)).
- On Android 17, audio playback and audio focus fail silently for an app that has neither a visible activity nor a foreground service. An app that targets API 37, as Dewfall does, also needs a foreground service with while-in-use capabilities, which one started while the app is visible, or from a notification or a media key, has. The page says an app that uses Media3's MediaSessionService "is not likely to be impacted", and gives `adb shell cmd audio set-enable-hardening throw` to test it. It also asks apps to keep the service through transient failures under 10 minutes, and to stop it after "a pause event from the UMO, or a media key event" ([background audio hardening](https://developer.android.com/about/versions/17/changes/bg-audio)).
- Starting a foreground service from the background is allowed when the user interacts with a notification ([restrictions on background starts](https://developer.android.com/develop/background-work/services/fgs/restrictions-bg-start)).

## Goals / Non-Goals

**Goals:**

- Sound that plays on with the app in the background and the screen off, as the roadmap's "Done when" asks.
- A media notification and media buttons that play and pause, from Media3's own notification code.
- Nothing about the video in the media session beyond what the notification shows.
- Rules that don't depend on the video screen, so #44's players reuse them.

**Non-Goals:**

- Playback resumption: a manifest MediaButtonReceiver, `onPlaybackResumption`, and the system's media resumption after a reboot. They need the last video stored on the device, which Phase 5's resume positions ([#50](https://github.com/dewfallapp/dewfall/issues/50)) bring.
- A browsable library, Android Auto and Wear OS browsing, which need a MediaLibraryService.
- Artwork, which waits for image loading ([#176](https://github.com/dewfallapp/dewfall/issues/176)).
- Extra notification buttons, such as seeking 10 seconds, a sleep timer, picture-in-picture and audio offload.
- The mini and fullscreen players, and whether closing the video screen keeps playing in the mini player: #44.

## Decisions

### Media3's notification and foreground service

PlaybackService drops its `onUpdateNotificationAsync` override, so Media3 posts its media notification and runs the service in the foreground while a video plays. The manifest entry gets `android:foregroundServiceType="mediaPlayback"`, and `:core:playback`'s manifest declares FOREGROUND_SERVICE and FOREGROUND_SERVICE_MEDIA_PLAYBACK. Both are normal permissions, so Android doesn't ask the user.

- **Provider.** `setMediaNotificationProvider` with DefaultMediaNotificationProvider, built with the channel ID `playback`, so Phase 7's download notification can have its own channel. The channel keeps Media3's name, "Now playing", and the notification keeps ID 1001, which the download notification must not reuse. The small icon stays Media3's play icon until [#120](https://github.com/dewfallapp/dewfall/issues/120) adds the app's icon. The change comments on #120 to set it there too.
- **Tapping it.** The session gets `setSessionActivity` with an immutable PendingIntent that `:app` provides through Hilt, behind a small interface in `:core:playback`, so `:core:playback` doesn't name MainActivity. Its intent names MainActivity with the launcher's action, category and new-task flag, so it brings the app's existing task to the front as it was, as the launcher does. `PackageManager.getLaunchIntentForPackage` won't do, because debug builds have a second launcher activity, the YouTube layer's debug screen, and Android doesn't say which one it returns.
- **Timeout and idle.** The service keeps Media3's defaults: 10 minutes in the foreground after a pause, and a notification after a stop or an error, until the user dismisses it.
- **Alternative:** a notification of Dewfall's own through a custom MediaNotification.Provider. Rejected, because Android 13 and later ignore it and draw the controls from the session.

### What the notification shows

The notification shows the video's title and channel name, and no artwork, as "The maintainer's answers" in the pull request records. Android 13 and later read them from the session, so the session must hold them:

1. When the details load, or a new playback ID starts while they show, PlayerViewModel calls `PlaybackConnection.describe(playbackId, title, channelName)`. The ViewModel already runs the screen's side effects, knows the details from its state, and keeps the latest playback ID, so the route sends nothing new and ADR 0002's rules don't change.
2. PlaybackItems keeps the title and channel name next to the VideoId, under the same playback ID, and drops them with it.
3. SessionPlayer's state gives the current item's title and channel name as its current MediaMetadata's `title` and `artist`, and nothing else. Each item in its timeline still holds only its playback ID. When a description arrives, PlaybackService tells SessionPlayer to rebuild its state, so the session sees it.
4. With no artwork, Media3's bitmap loader fetches nothing.

So the platform session holds the playback ID, the title and the channel name, and never the video ID or a link. Apps with notification access, the lock screen and Bluetooth devices, such as car displays and watches, can read the title and channel name while the playback lasts.

**A product rule bends.** ADR 0007 counts titles and channel names as user data, and AGENTS.md's product rules say "User data stays on the device". A Bluetooth device is off the device. So this is an exception to that rule, which AGENTS.md says to ask about. The maintainer's answers in the pull request record it. If approved, the build writes the exception, with its date, into AGENTS.md's product rules, and PRIVACY.md tells users.

- **Alternative:** no title, so nothing about the video leaves the app and the rule holds. Then the notification and the system's media player show no title. The maintainer chose between the two.

### Who may control playback

`onConnectAsync` sorts controllers into four kinds, with a pure function of three inputs: whether `session.isMediaNotificationController(controller)`, whether the package is the app's own, and `isTrusted`.

- **The media notification controller**, Media3's own controller from the app's package. It gets the playback commands below and no session commands. Media3 sends media buttons as this controller, answers the system UI's connection with its commands, and advertises them in the platform session. So the system UI, the notification's buttons, headset and Bluetooth buttons, and every app that reads the platform session get only these.
- **The app's other controllers**: the screens' MediaController. It keeps the default commands, which the screens need to set items.
- **Trusted controllers from other packages**: the Bluetooth service and apps with notification access, which go through `onConnectAsync`. They get the same playback commands and no session commands.
- **Any other controller** is rejected, as today.

The playback commands are COMMAND_PLAY_PAUSE, COMMAND_PREPARE, which some controllers send before play, though play itself prepares an idle player through Media3's `Util.handlePlayButtonAction` ([Util](https://github.com/androidx/media/blob/1.11.1/libraries/common/src/main/java/androidx/media3/common/util/Util.java)), COMMAND_STOP, which the notification's dismissal sends, COMMAND_SEEK_IN_CURRENT_MEDIA_ITEM, for the seek bar of Android 13's media controls, and the read commands COMMAND_GET_CURRENT_MEDIA_ITEM, COMMAND_GET_TIMELINE, which the notification needs to know there is an item, and COMMAND_GET_METADATA. So none of them can set an item, change tracks, the speed or the volume, skip or seek by steps.

Apps with notification access can read every notification anyway, and the user turned that access on. Others can't reach the session's token, because the service has no intent filter.

### Buttons

- PlaybackService drops its `onMediaButtonEvent` override, so Media3 handles media buttons through the media notification controller, with its playback commands. So next, previous, fast forward and rewind, including a double tap of play and pause, are refused. A previous button would otherwise restart a long video.
- On Android 10 to 12, DefaultMediaNotificationProvider builds the notification's buttons from the session's player's commands, not the controller's: previous, play and pause, and next ([DefaultMediaNotificationProvider](https://github.com/androidx/media/blob/1.11.1/libraries/session/src/main/java/androidx/media3/session/DefaultMediaNotificationProvider.java), `getMediaButtons`). So SessionPlayer also removes COMMAND_SEEK_TO_PREVIOUS, COMMAND_SEEK_TO_PREVIOUS_MEDIA_ITEM, COMMAND_SEEK_TO_NEXT and COMMAND_SEEK_TO_NEXT_MEDIA_ITEM, and the notification shows no buttons that do nothing. It keeps COMMAND_SEEK_BACK and COMMAND_SEEK_FORWARD for the screens, which #44's fullscreen player may use.
- Both command lists are pure functions' lists of Media3's command constants, which are plain ints, so their tests run on the JVM.

### No intent filter, checked again

The service stays without the MediaSessionService intent filter. With it, on Android 10 and 11 Media3 gives the platform session a `getForegroundService` PendingIntent to the service. After the process ends, a headset's play button would start the service as a foreground service. Its player is empty, `onPlaybackResumption` fails by default, nothing plays, so Media3 posts no notification and the service never calls `startForeground`, which crashes the app. The notification doesn't change that path, though it adds a second one, which the next section handles. Without the filter, Media3 registers a runtime receiver, which dies with the process, and notification buttons still reach the service, because Media3's PendingIntents name its class ([PlaybackPendingIntentBuilder](https://github.com/androidx/media/blob/1.11.1/libraries/session/src/main/java/androidx/media3/session/PlaybackPendingIntentBuilder.java)). Playback resumption would need the filter or a receiver, and stored positions, which are a non-goal.

### A notification left behind by an ended process

On Android 10 to 12 the notification's buttons are PendingIntents to the service. For play, Media3 makes them with `PendingIntent.getForegroundService` while the player isn't playing (DefaultActionFactory, `createMediaActionPendingIntent`, which sets `setStartAsForegroundService(!getPlayWhenReady())`, and [PlaybackPendingIntentBuilder](https://github.com/androidx/media/blob/1.11.1/libraries/session/src/main/java/androidx/media3/session/PlaybackPendingIntentBuilder.java)). Each intent's data is the session's URI, made from the session's ID (MediaSessionImpl, `createSessionUri`).

**The path.** After 10 minutes paused, Media3 leaves the foreground and keeps the notification. If Android then ends the process without `onDestroy`, such as a low-memory kill, the notification stays. Tapping its play button starts PlaybackService in a new process as a foreground service. With the default session ID, an empty string, `onStartCommand` finds the new session by its URI and sends it play. The player is empty, nothing plays, Media3 posts no notification, and the service never calls `startForeground`, so the app crashes. Media3's guard against stale intents, `SELF_INTENT_UID_KEY`, covers only its own start intents.

**The fix.**
- The session gets a new random ID in each process, with `MediaSession.Builder.setId`, a random UUID's text. So an intent from an earlier process names a URI that matches no session.
- `onStartCommand` then asks `onGetSession` with a media button caller. PlaybackService's `onGetSession` returns null for every caller, because Dewfall's controllers connect with the session's own token and never call it. Its only other caller is a binding with a SessionToken that names the service, which needs the intent filter (MediaSessionService, `onBind`).
- With no session, Media3 calls `stopSelfSafely` on a service that hasn't processed its first start intent: it posts a hidden shutdown notification, calls `startForeground` with it, removes it and stops the service (MediaSessionService, `onStartCommand` and `stopSelfSafely`). So nothing crashes and nothing plays.
- Before returning null to a media button caller, `onGetSession` cancels the media notification's ID when its own player has no item. Then this process shows no notification of its own, so one with that ID can only be left from an ended process, and the stale notification goes away. A notification of this process with an item would have replaced the stale one, buttons and all, under the same ID.

The decisions are a pure function with its own test: from whether the caller is a media button and whether the player has an item, to whether to cancel. A hand check on Android 10 runs the path, as tasks.md lists.

### BluetoothValidationActivity stays removed

Its commit says it exists so that Bluetooth recognizes AVRCP browsable media services on Android 16 and 17. Dewfall has no browsable library, so the manifest keeps removing it, with a comment that says why. Whether a Bluetooth device's play and pause reach the session as media key events or through the platform session's controls, both go through the media notification controller's or a trusted controller's playback commands. A real-phone check on #46 covers a Bluetooth headset's buttons.

### Wake mode

The player sets `setWakeMode(C.WAKE_MODE_NETWORK)`. Every video streams today, and on Android 13 and lower the Wi-Fi lock's high-power mode "may help maintain a steady Wifi connection during screen off playback on some devices". It needs no new permission. The locks last only while the player plays or buffers with play on.

- **Alternative:** WAKE_MODE_LOCAL, today's default, which also keeps playing with the screen off. Rejected, because a Wi-Fi connection that drops to save power would stall a stream that the user can't see. Phase 6's local files could use it.

### The picture in the background

While no Dewfall screen can be seen, the player turns the video track off with `setTrackTypeDisabled(C.TRACK_TYPE_VIDEO, true)`, so no picture is decoded. When the sound and the picture come in separate files, as FormatChoice usually picks them, the video-only file isn't fetched either. A `Combined` format, one file with both, and a live stream's HLS segments, which carry both, can't drop the picture's bytes, so they keep loading.

- **Who knows.** MainActivity, which is Hilt's entry point, calls `PlaybackConnection.setAppVisible(true)` in `onStart`, and `setAppVisible(false)` in `onStop` unless `isChangingConfigurations`. The connection keeps it in PlaybackSessions, and PlaybackService applies it to its own ExoPlayer, building on its current track selection parameters, so the live stream's variant override stays. A configuration change keeps the picture.
- **Why `:app`.** Whether the app can be seen doesn't depend on which screen shows, so #44's mini and fullscreen players follow the same rule. `:app` depends on `:core:playback` for the interface.
- **The cost.** Turning the track back on makes the video file load from the current position, so the sound may pause for a moment when the user comes back.
- **Alternatives:** the video screen's ON_STOP, rejected because the mini player would need it moved. Turning the track off when no surface is attached, rejected because a configuration change replaces the surface and would reload the picture. ProcessLifecycleOwner, rejected: lifecycle-process 2.11.0 is already on the release runtime classpath as a transitive dependency, checked on November 21, 2026, but `:core:playback` would have to declare it, and it dispatches ON_STOP only after a delay ([ProcessLifecycleOwner](https://developer.android.com/reference/androidx/lifecycle/ProcessLifecycleOwner)). The app has one activity, which gives the same signal at once.

### Leaving the app

PlayerRoute's `PauseOnStop` becomes `SaveAndPauseOnStop`. In ON_STOP, unless the activity only changes configurations:

- It saves the position, with today's rule: only from this screen's item, and not from an idle player.
- It pauses only a playback that hasn't played yet. The route keeps, in its saved state, the playback ID that has played, set when the player plays with this screen's item. A pure function in `:feature:player-logic`, `leaveAction(changingConfigurations, played)`, returns PAUSE, KEEP_PLAYING or NOTHING, with its tests. PlaybackConnection's `pause` already pauses a start that waits for the connection, or whose item is still being resolved.

Media3 starts the foreground service as soon as the player buffers with play on, while the user still sees the app. A video that hasn't started yet would try to start it from the background, which Android 12 and later refuse. As a safety net, PlaybackService's `MediaSessionService.Listener` pauses the player in `onForegroundServiceStartNotAllowedException`.

**Positions.** The route also saves the position whenever this screen's item pauses or ends, through a Player listener, while the screen exists. So when Android stops the paused player in the background, coming back waits at the position where it paused, not where the user left the app. Android writes the saved state only when the activity stops, so after the process ends the screen comes back at the position it had when the user last left the app. #50's stored resume positions will fix that.

### Stopping

- Closing the video screen stops playback, as today: PlayerViewModel's `onCleared` stops it and clears the items, and Media3 removes the notification.
- PlaybackService overrides `onTaskRemoved` to call `pauseAllPlayersAndStopSelf()`, so removing Dewfall from the recent apps stops playback even while it plays in the foreground. The service isn't bound, because the controllers connect with the session's own token, so `stopSelf` ends it.
- Dismissing a paused notification after the service left the foreground sends stop, so the player stops, as Media3 does by default.

### Failures in the background

- A playback that failed keeps its notification, and its play button prepares the player again from the same position, through `Util.handlePlayButtonAction`. PlaybackService clears PlaybackSessions' failure when the player's error goes away, in `onPlayerErrorChanged(null)`, so the video screen doesn't show an old failure over a video that plays again.
- The platform session's error message, which car displays and watches may show, comes from `:core:playback`'s string resources, with the words the maintainer approved for OTHER in #42: "This video stopped playing." The cleaning stays a pure function, which now takes the message.

### Android 17's background audio

The foreground service starts while the app is visible, or from a notification or a media key, so it has while-in-use capabilities. Media3 keeps it in the foreground for 10 minutes after any pause, not only after the transient failures the page names. That differs from the page's advice to stop it after a pause from the system's media controls or a media key. Dewfall keeps Media3's behaviour, because the page expects Media3 apps are "not likely to be impacted", and a pause the user means to undo soon then plays on without a new foreground start. A hand check runs with `adb shell cmd audio set-enable-hardening throw` on Android 17.

### How #44 fits

- The notification, the buttons, the controller rules, the wake mode and the picture rule live in `:core:playback` and `:app`, not in the video screen.
- The mini player can read the title and channel name from its controller's MediaMetadata.
- #44 decides whether closing the video screen still stops playback, or hands it to the mini player. The stop rule stays in PlayerViewModel until then.

### How the tests run

The new decisions are pure functions of plain inputs, as [How the playback tests run](../../../docs/architecture.md#how-the-playback-tests-run) asks, because Media3's commands and metadata touch Android code on the stub android.jar:

- the controller kind from whether it is the media notification controller, whether its package is the app's own, and `isTrusted`, and the command constants each kind gets
- the command constants SessionPlayer removes
- the session's error from an error code and a message
- PlaybackItems' descriptions and when they go
- `leaveAction` in `:feature:player-logic`
- PlayerViewModel's calls to `describe`, with its fake connection

The adapters, the service and the notification run only on a device, so docs/testing.md's player hand checks grow, as tasks.md lists.

### Docs

- docs/architecture.md: "The player" for the notification, the foreground service, the controller rules, the buttons, the intent filter, BluetoothValidationActivity, the wake mode, the picture rule, leaving and stopping. The module graph gets `:app` to `:core:playback`.
- docs/testing.md: the player's hand checks.
- PRIVACY.md: what the notification shares.
- AGENTS.md: the product rule "User data stays on the device" gets its exception for the title and channel name, with its date, as the maintainer approved.
- No ADR changes. ADR 0006 already names the mediaPlayback type and the notification permission's exemption, and the ViewModel's `describe` call is a side effect, as ADR 0002 allows.

## Risks / Trade-offs

- [The title shows on the lock screen] → Media3 sets VISIBILITY_PUBLIC, so the lock screen shows the title and channel name even when the user hides sensitive notification content. That is part of the maintainer's choice.
- [The sound pauses for a moment on coming back] → Turning the picture back on loads the video file from the current position. The data it saves in the background is worth more to the people with expensive data that vision.md names.
- [The position after process death] → It is where the user last left the app, not where the video paused in the background, until #50.
- [The notification shows inside the app too] → Media3 shows it whenever the player plays. Hiding it would need a notification of Dewfall's own, which Android 13 ignores.
- [The Wi-Fi lock costs battery] → Only while playing, and the screen is off then.
- [More `@UnstableApi`] → `isTrusted`, `pauseAllPlayersAndStopSelf`, the service listener and DefaultMediaNotificationProvider's builder are unstable APIs. They opt in file by file, as #42's code does, and Media3 updates run the hand checks.
- [Media3's small icon] → Until #120, the notification's icon is Media3's generic play icon, not Dewfall's.

## Migration Plan

None. Nothing is stored, and the app has no release yet.
