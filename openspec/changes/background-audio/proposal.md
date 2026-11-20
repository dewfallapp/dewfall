# Proposal

## Why

Phase 4 is done when "you can search for a video and watch it, and audio keeps playing with the screen off" ([roadmap](../../../docs/roadmap.md#phase-4-search-and-play)). Since [#42](https://github.com/dewfallapp/dewfall/issues/42), leaving the app or turning the screen off pauses the video, and the player shows no notification. This change adds issue [#43](https://github.com/dewfallapp/dewfall/issues/43): the sound plays on in the background, with a media notification that controls it. The mini and fullscreen players ([#44](https://github.com/dewfallapp/dewfall/issues/44)) build on the same service.

## What Changes

- **The sound plays on.** Going home, switching apps or turning the screen off keeps the sound playing. While the app can't be seen, the player turns the picture off, so it decodes no picture, and fetches none when the picture comes in its own file, as it usually does. Coming back shows the picture again after a short load. A video that hasn't started playing yet when the user leaves waits, paused, for them to come back.
- **Closing still stops.** Closing the video screen stops playback, as today, until #44 adds the mini player. Removing Dewfall from the recent apps stops it too.
- **A media notification.** While a video plays, and for a while after it pauses, Android shows a media notification with a play and pause button. Android 13 and later draw it as their own media player, with a seek bar. Tapping it opens Dewfall as it was. It shows the video's title and channel name, and no picture, because the app doesn't load images yet ([#176](https://github.com/dewfallapp/dewfall/issues/176)). The notification also shows while the user watches in the app, because Media3 shows it whenever the player plays.
- **An exception to a product rule.** To show the title, Android's media session must hold it, so the lock screen, Bluetooth devices such as car displays, and apps the user gave notification access can read the title and channel name. They are user data by ADR 0007, so this bends AGENTS.md's product rule "User data stays on the device". AGENTS.md and PRIVACY.md record the exception. The maintainer chose this on November 21, 2026, as "The maintainer's answers" in [pull request #198](https://github.com/dewfallapp/dewfall/pull/198) records, with the other decisions below.
- **Buttons.** Headset, Bluetooth and notification buttons play and pause. Next, previous, fast forward and rewind do nothing, because a single video has no next or previous one, and "previous" would restart a long video.
- **Other apps.** Android's system UI, the Bluetooth service and apps the user gave notification access may play, pause, seek and stop. No other app may connect, and none may change what plays.
- **A foreground service.** Android stops background playback that doesn't run in a foreground service of the type mediaPlayback, and Android 17 silences it ([background audio hardening](https://developer.android.com/about/versions/17/changes/bg-audio)). Media3 runs the service in the foreground while a video plays, and for 10 minutes after it pauses.
- **The screen off.** The player holds a wake lock and a Wi-Fi lock while it plays, so streaming goes on with the screen off.
- **What stays.** The media session still never names the video's ID or a link. After Android ends the process, a headset button still doesn't start the app, and playback still can't resume from the system's media controls.

## Capabilities

### New Capabilities

None.

### Modified Capabilities

- `player`: playback goes on in the background with a media notification; buttons and other apps play and pause; closing the screen or removing the app stops it; the media session holds the title and channel name.

## Impact

- `:core:playback`: PlaybackService runs in the foreground with Media3's media notification, accepts controllers from the system, Bluetooth and apps with notification access, lets buttons play and pause, holds a Wi-Fi lock, turns the picture off while the app can't be seen, and stops when the app is removed from the recent apps. The session's player gets the title and channel name, and loses the skip and seek-step commands.
- `:feature:player-ui` and `:feature:player-logic`: leaving the app no longer pauses a playing video. The ViewModel hands the details' title and channel name to the player.
- `:app`: MainActivity tells the player when the app can be seen, so the rule doesn't depend on any screen, and `:app` gives the service the PendingIntent that opens MainActivity from the notification. `:app` depends on `:core:playback`.
- The notification keeps Media3's generic play icon until #120 adds the app's icon, and its channel keeps Media3's name, "Now playing", which users see in Android's notification settings.
- Manifest: the FOREGROUND_SERVICE and FOREGROUND_SERVICE_MEDIA_PLAYBACK permissions, and `foregroundServiceType="mediaPlayback"` on PlaybackService. Both permissions are normal ones, so Android doesn't ask the user. The service stays unexported, with no intent filter, and BluetoothValidationActivity stays removed.
- No new dependency, no new host and no new log line.
- Privacy: the video's title and channel name reach Android's media session, the notification and the lock screen. Apps with notification access and Bluetooth devices, such as car displays, can read them. PRIVACY.md says so, and AGENTS.md's product rules record the exception.
- Docs: docs/architecture.md's "The player" and module graph, docs/testing.md's hand checks, PRIVACY.md and AGENTS.md's product rules. Issue #120 gets a note to give the notification the app's icon, and #46's real-phone checks change, because buttons now play and pause.
