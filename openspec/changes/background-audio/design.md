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
- **Other controllers.** Platform controllers, such as the system UI's, connect to the session as legacy controllers, through `onConnectAsync`, the first time they send a command, and each command is checked against the commands that connection accepted ([MediaSessionLegacyStub](https://github.com/androidx/media/blob/1.11.1/libraries/session/src/main/java/androidx/media3/session/MediaSessionLegacyStub.java), `handleControllerTaskOnHandler` and `dispatchSessionTaskWithPlayerCommand`). So today's own-package rule would make the system's media controls do nothing. The platform session advertises the media notification controller's commands that the player also has (the same file, where it builds the PlaybackStateCompat). `ControllerInfo.isTrusted` is true for the app itself, the system, holders of MEDIA_CONTENT_CONTROL or STATUS_BAR_SERVICE, and apps with an enabled notification listener ([MediaSession](https://github.com/androidx/media/blob/1.11.1/libraries/session/src/main/java/androidx/media3/session/MediaSession.java)).
- **Media buttons.** `MediaSessionImpl.onMediaButtonEvent` asks `Callback.onMediaButtonEvent` first. Otherwise it sends play, pause, next, previous, fast forward, rewind or stop as the media notification controller, and a double tap of play and pause as next ([MediaSessionImpl](https://github.com/androidx/media/blob/1.11.1/libraries/session/src/main/java/androidx/media3/session/MediaSessionImpl.java)). Each one is refused when that controller doesn't have the command ([MediaSessionStub](https://github.com/androidx/media/blob/1.11.1/libraries/session/src/main/java/androidx/media3/session/MediaSessionStub.java), `queueSessionTaskWithPlayerCommand`).
- **The media button receiver.** When the app has a manifest MediaButtonReceiver, or on Android 11 and lower, Media3 looks for a service with the `androidx.media3.session.MediaSessionService` action and gives the platform session a `PendingIntent.getForegroundService` for it. With neither, it registers a receiver at runtime, which dies with the process (MediaSessionLegacyStub's constructor). A play on an empty player calls `Callback.onPlaybackResumption`, whose default fails, and then nothing plays (MediaSessionImpl, `handleMediaControllerPlayRequestInternal`).
- **Removing the app from the recent apps.** `onTaskRemoved` keeps the service running only while it is in the foreground and playing, and otherwise calls `pauseAllPlayersAndStopSelf` (MediaSessionService).
- **A refused foreground start.** When Android refuses to start the foreground service, Media3 calls `MediaSessionService.Listener.onForegroundServiceStartNotAllowedException`, and does nothing else (MediaSessionService, `onUpdateNotificationInternal`).
- **Wake mode.** `setWakeMode` holds its locks while the player is ready or buffering with play on, and is meant for playback with the screen off ([ExoPlayer](https://github.com/androidx/media/blob/1.11.1/libraries/exoplayer/src/main/java/androidx/media3/exoplayer/ExoPlayer.java)). WAKE_MODE_NETWORK adds a WifiLock, which "on API 33 and below" also keeps Wi-Fi in high-power mode ([C](https://github.com/androidx/media/blob/1.11.1/libraries/common/src/main/java/androidx/media3/common/C.java)). It needs only the WAKE_LOCK permission ([WifiLockManager](https://github.com/androidx/media/blob/1.11.1/libraries/common/src/main/java/androidx/media3/common/util/WifiLockManager.java)), which media3-exoplayer already merges.
- **A deselected track.** A ProgressiveMediaPeriod with no selected track cancels its loading ([ProgressiveMediaPeriod](https://github.com/androidx/media/blob/1.11.1/libraries/exoplayer/src/main/java/androidx/media3/exoplayer/source/ProgressiveMediaPeriod.java), `selectTracks`). So turning the video track off stops fetching a video's video file.
- **BluetoothValidationActivity** satisfies a probe of Bluetooth's MediaPlayerList on Android 16 and 17 "to recognize AVRCP browsable media services" ([commit d5c7a55](https://github.com/androidx/media/commit/d5c7a55462), [BluetoothValidationActivity](https://github.com/androidx/media/blob/1.11.1/libraries/session/src/main/java/androidx/media3/session/BluetoothValidationActivity.java)).

### What Android asks

Checked on November 21, 2026:

- A media playback foreground service declares `foregroundServiceType="mediaPlayback"` and the FOREGROUND_SERVICE_MEDIA_PLAYBACK permission, has no runtime prerequisite ([foreground service types](https://developer.android.com/develop/background-work/services/fgs/service-types#media)), and also needs FOREGROUND_SERVICE ([background playback guide](https://developer.android.com/media/media3/session/background-playback)).
- Media session notifications need no POST_NOTIFICATIONS permission ([notification permission](https://developer.android.com/develop/ui/views/notifications/notification-permission#exemptions)). Media notifications stay non-dismissible while their service runs in the foreground, which Android 14 changed for other foreground notifications ([Android 14 behavior changes](https://developer.android.com/about/versions/14/behavior-changes-all#non-dismissable-notifications)).
- On Android 17, audio playback and audio focus fail silently for an app that has neither a visible activity nor a foreground service. An app that targets API 37, as Dewfall does, also needs a foreground service with while-in-use capabilities, which one started while the app is visible, or from a notification or a media key, has. The page recommends Media3's MediaSessionService, and gives `adb shell cmd audio set-enable-hardening throw` to test it ([background audio hardening](https://developer.android.com/about/versions/17/changes/bg-audio)).
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
- **Tapping it.** The session gets `setSessionActivity` with an immutable PendingIntent for the app's launch intent, from `PackageManager.getLaunchIntentForPackage`, so `:core:playback` doesn't name MainActivity. A launch intent brings the app's existing task to the front as it was, as the launcher does.
- **Timeout and idle.** The service keeps Media3's defaults: 10 minutes in the foreground after a pause, and a notification after a stop or an error, until the user dismisses it.
- **Alternative:** a notification of Dewfall's own through a custom MediaNotification.Provider. Rejected, because Android 13 and later ignore it and draw the controls from the session.

### What the notification shows

The notification shows the video's title and channel name, and no artwork, as "The maintainer's answers" in the pull request records. Android 13 and later read them from the session, so the session must hold them:

1. When the details load, or a new playback ID starts while they show, PlayerViewModel calls `PlaybackConnection.describe(playbackId, title, channelName)`. The ViewModel already runs the screen's side effects, knows the details from its state, and keeps the latest playback ID, so the route sends nothing new and ADR 0002's rules don't change.
2. PlaybackItems keeps the title and channel name next to the VideoId, under the same playback ID, and drops them with it.
3. SessionPlayer's state gives the current item's title and channel name as its current MediaMetadata's `title` and `artist`, and nothing else. Each item in its timeline still holds only its playback ID. When a description arrives, PlaybackService tells SessionPlayer to rebuild its state, so the session sees it.
4. With no artwork, Media3's bitmap loader fetches nothing.

So the platform session holds the playback ID, the title and the channel name, and never the video ID or a link. Apps with notification access, the lock screen and Bluetooth devices can read the title and channel name while the playback lasts. PRIVACY.md says so.

- **Alternative:** no title, so nothing about the video leaves the app. Then the notification and the system's media player show no title. The maintainer chose between the two.

### Who may control playback

`onConnectAsync` accepts three kinds of controller, decided by a pure function from the controller's package and `isTrusted`:

- **The app's own package**: the screens' MediaController and Media3's media notification controller. They keep the default commands.
- **Trusted controllers from other packages**: Android's system UI, which sends the taps of Android 13's media controls, the Bluetooth service, and apps with notification access. They get only COMMAND_PLAY_PAUSE, COMMAND_SEEK_IN_CURRENT_MEDIA_ITEM, COMMAND_STOP, and the read commands COMMAND_GET_CURRENT_MEDIA_ITEM, COMMAND_GET_TIMELINE and COMMAND_GET_METADATA, and no session commands. So none of them can set an item, change tracks or change the volume.
- **Any other controller** is rejected, as today.

Apps with notification access can read every notification anyway, and the user turned that access on. Others can't reach the session's token, because the service has no intent filter.

### Buttons

- PlaybackService drops its `onMediaButtonEvent` override, so Media3 handles media buttons through the media notification controller.
- SessionPlayer removes COMMAND_SEEK_TO_PREVIOUS, COMMAND_SEEK_TO_PREVIOUS_MEDIA_ITEM, COMMAND_SEEK_TO_NEXT, COMMAND_SEEK_TO_NEXT_MEDIA_ITEM, COMMAND_SEEK_BACK and COMMAND_SEEK_FORWARD from its available commands. So the notification shows no skip buttons on Android 10 to 12, the platform session advertises none, and next, previous, fast forward and rewind, including a double tap of play and pause, do nothing. A previous button would otherwise restart a long video, and no screen uses those commands.
- The commands it removes are a pure function's list of Media3's command constants, which are plain ints, so its test runs on the JVM.

### No intent filter, checked again

The service stays without the MediaSessionService intent filter. With it, on Android 10 and 11 Media3 gives the platform session a `getForegroundService` PendingIntent to the service. After the process ends, a headset's play button would start the service as a foreground service. Its player is empty, `onPlaybackResumption` fails by default, nothing plays, so Media3 posts no notification and the service never calls `startForeground`, which crashes the app. Turning the notification on doesn't change that path. Without the filter, Media3 registers a runtime receiver, which dies with the process, and notification buttons still reach the service, because Media3's PendingIntents name its class ([PlaybackPendingIntentBuilder](https://github.com/androidx/media/blob/1.11.1/libraries/session/src/main/java/androidx/media3/session/PlaybackPendingIntentBuilder.java)). Playback resumption would need the filter or a receiver, and stored positions, which are a non-goal.

### BluetoothValidationActivity stays removed

It exists for AVRCP browsing of a MediaLibraryService on Android 16 and 17. Dewfall has no browsable library, and Bluetooth sends play and pause as media key events, which reach the session without it. So the manifest keeps removing it, with a comment that says why. A real-phone check on #46 covers a Bluetooth headset's button.

### Wake mode

The player sets `setWakeMode(C.WAKE_MODE_NETWORK)`. Every video streams today, and the Wi-Fi lock keeps the connection steady with the screen off on Android 13 and lower. It needs no new permission. The locks last only while the player plays or buffers with play on.

- **Alternative:** WAKE_MODE_LOCAL, today's default. Rejected, because Media3 describes it for apps that "do not require low-latency Wifi access". Phase 6's local files could use it.

### The picture in the background

While no Dewfall screen can be seen, the player turns the video track off with `setTrackTypeDisabled(C.TRACK_TYPE_VIDEO, true)`, so a video's video file isn't fetched or decoded. A live stream's HLS segments carry sound and picture together, so it keeps fetching them, but decodes no picture.

- **Who knows.** MainActivity, which is Hilt's entry point, calls `PlaybackConnection.setAppVisible(true)` in `onStart`, and `setAppVisible(false)` in `onStop` unless `isChangingConfigurations`. The connection keeps it in PlaybackSessions, and PlaybackService applies it to its own ExoPlayer, building on its current track selection parameters, so the live stream's variant override stays. A configuration change keeps the picture.
- **Why `:app`.** Whether the app can be seen doesn't depend on which screen shows, so #44's mini and fullscreen players follow the same rule. `:app` depends on `:core:playback` for the interface.
- **The cost.** Turning the track back on makes the video file load from the current position, so the sound may pause for a moment when the user comes back.
- **Alternatives:** the video screen's ON_STOP, rejected because the mini player would need it moved. Turning the track off when no surface is attached, rejected because a configuration change replaces the surface and would reload the picture. ProcessLifecycleOwner, rejected because lifecycle-process would be a new dependency.

### Leaving the app

PlayerRoute's `PauseOnStop` becomes `SaveOnStop`. In ON_STOP, unless the activity only changes configurations:

- It saves the position, with today's rule: only from this screen's item, and not from an idle player.
- It pauses only a playback that hasn't played yet. The route keeps, in its saved state, the playback ID that has played, set when the player plays with this screen's item. A pure function in `:feature:player-logic`, `leaveAction(changingConfigurations, played)`, returns PAUSE, KEEP_PLAYING or NOTHING, with its tests. PlaybackConnection's `pause` already pauses a start that waits for the connection, or whose item is still being resolved.

Media3 starts the foreground service as soon as the player buffers with play on, while the user still sees the app. A video that hasn't started yet would try to start it from the background, which Android 12 and later refuse. As a safety net, PlaybackService's `MediaSessionService.Listener` pauses the player in `onForegroundServiceStartNotAllowedException`.

**Positions.** The route also saves the position whenever this screen's item pauses or ends, through a Player listener, while the screen exists. So when Android stops the paused player in the background, coming back waits at the position where it paused, not where the user left the app. Android writes the saved state only when the activity stops, so after the process ends the screen comes back at the position it had when the user last left the app. #50's stored resume positions will fix that.

### Stopping

- Closing the video screen stops playback, as today: PlayerViewModel's `onCleared` stops it and clears the items, and Media3 removes the notification.
- PlaybackService overrides `onTaskRemoved` to call `pauseAllPlayersAndStopSelf()`, so removing Dewfall from the recent apps stops playback even while it plays in the foreground. The service isn't bound, because the controllers connect with the session's own token, so `stopSelf` ends it.
- Dismissing a paused notification after the service left the foreground sends stop, so the player stops, as Media3 does by default.

### Failures in the background

- A playback that failed keeps its notification, and its play button prepares the player again from the same position, through Media3's `Util.handlePlayButtonAction`. PlaybackService clears PlaybackSessions' failure when the player's error goes away, in `onPlayerErrorChanged(null)`, so the video screen doesn't show an old failure over a video that plays again.
- The platform session's error message, which car displays and watches may show, comes from `:core:playback`'s string resources, with the words the maintainer approved for OTHER in #42: "This video stopped playing." The cleaning stays a pure function, which now takes the message.

### Android 17's background audio

The foreground service starts while the app is visible, or from a notification or a media key, so it has while-in-use capabilities. Media3 keeps it in the foreground through pauses under 10 minutes, as the page asks. A hand check runs with `adb shell cmd audio set-enable-hardening throw` on Android 17.

### How #44 fits

- The notification, the buttons, the controller rules, the wake mode and the picture rule live in `:core:playback` and `:app`, not in the video screen.
- The mini player can read the title and channel name from its controller's MediaMetadata.
- #44 decides whether closing the video screen still stops playback, or hands it to the mini player. The stop rule stays in PlayerViewModel until then.

### How the tests run

The new decisions are pure functions of plain inputs, as [How the playback tests run](../../../docs/architecture.md#how-the-playback-tests-run) asks, because Media3's commands and metadata touch Android code on the stub android.jar:

- the controller kind from the package and `isTrusted`, and the command constants each kind gets
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
