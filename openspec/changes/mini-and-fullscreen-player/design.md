# Design

## Context

See proposal.md for why. What this change builds on, from #42 and #43 and docs/architecture.md's [The player](../../../docs/architecture.md#the-player) and [Navigation](../../../docs/architecture.md#navigation):

- `:app`'s back stack holds SearchKey, then at most one PlayerKey. `openVideo` adds a PlayerKey only while SearchKey is on top. `close(key)` removes that entry's key, and `goBack` the top key while there is more than one.
- `PlayerRoute(videoId, onClose)` draws the video screen. Its top button, the keyboard_arrow_down glyph named "Close video", and the back gesture both remove the entry. Its strings file already notes that #44 makes the button minimize instead.
- PlayerViewModel keeps the playback ID of its latest start. Its `onCleared` calls `PlaybackConnection.stop(id)`, which stops the player and clears its items if the item is still that ID. Navigation 3 clears the ViewModel after the exit animation, and when the activity is destroyed for good.
- PlayerRoute keeps in its saved state whether playback started, the playback ID, the ID that has played, and the position. `startAction(started, known)` decides PLAY, WAIT_FOR_PLAY or NOTHING. At ON_STOP, `pausesOnLeave(changing, played)` pauses a playback that hasn't played yet, so its foreground service never starts from the background.
- MainActivity calls `PlaybackConnection.setAppVisible` in `onStart` and `onStop`, skipping configuration changes. PlaybackService turns the video track off while the app can't be seen.
- PlaybackConnection exposes the MediaController as `player`. SessionPlayer gives the session, and so the controller, the current item's title as `title` and its channel name as `artist`, once the video screen describes the playback. PlaybackItems keeps the VideoId behind each playback ID while its item is the player's.
- The manifest keeps MainActivity in portrait. Android ignores that on large screens. Every screen draws edge to edge, and pads its content with `WindowInsets.safeDrawing`, Search YouTube included.
- `DewfallTheme(darkTheme)` already takes a darkTheme parameter "for the fullscreen player". The theme doesn't set the design system's opacity tokens yet, and architecture.md says duration-scrim comes with its first component.
- `:feature:player-ui` doesn't depend on activity-compose. `:app` does, at 1.13.0.

### Facts checked

Checked on November 21, 2026:

- **Immersive mode.** [Hide system bars for immersive mode](https://developer.android.com/develop/ui/views/layout/immersive) uses `WindowCompat.getInsetsController(window, window.decorView)`, `hide(WindowInsetsCompat.Type.systemBars())` and `show(...)`. With `BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE`, a swipe from the edge shows the bars, which "overlay your app's content, might have some degree of transparency, and are automatically hidden after a short timeout". [Immersive content](https://developer.android.com/design/ui/mobile/guides/layout-and-content/immersive-content) asks for "tapping on the screen during video playback displays video playback controls and system bars", and "an overlay or scrim for overlaying text and controls".
- **Orientation.** For apps that target Android 17, as Dewfall does, `screenOrientation` and `setRequestedOrientation()` are ignored, with every portrait and landscape value, "on displays whose smallest width is greater than 600dp", in full screen and multi-window modes ([Restrictions on orientation and resizability are ignored](https://developer.android.com/about/versions/17/changes/ff-restrictions-ignored)). Android's recipe [App orientation restricted on phones but not on large screens](https://developer.android.com/develop/adaptive-apps/cookbook/orientation-restriction) sets `activity.requestedOrientation` from `LocalActivity.current` in Compose. `userLandscape`, ActivityInfo's `SCREEN_ORIENTATION_USER_LANDSCAPE`, is "Landscape orientation, but can be either normal or reverse landscape based on the device sensor and the user's preference" ([activity element](https://developer.android.com/guide/topics/manifest/activity-element#screen)), so it follows the rotation lock.
- **Back.** [Provide custom back navigation](https://developer.android.com/guide/navigation/custom-back) uses activity-compose's `BackHandler`, and "only the innermost one intercepts the back event". [Handle back gestures](https://developer.android.com/guide/navigation/navigation-event/handle-back) says handlers of the same priority run last in, first out. BackHandler in activity-compose 1.13.0 carries no deprecation, checked in its compiled classes.
- **Media3 1.11.1**, in the sources jar of the version the app uses:
  - media3-ui-compose's `rememberCurrentMediaItemState(player)` holds the current `mediaItem`, its `mediaMetadata` and `isLive`, updated on timeline, metadata and command changes, and set once when it is made ([CurrentMediaItemState.kt at 1.11.1](https://github.com/androidx/media/blob/1.11.1/libraries/ui_compose/src/main/java/androidx/media3/ui/compose/state/CurrentMediaItemState.kt), [PlayerStateObserver.kt at 1.11.1](https://github.com/androidx/media/blob/1.11.1/libraries/ui_compose/src/main/java/androidx/media3/ui/compose/state/PlayerStateObserver.kt)).
  - `PlayPauseButtonState.onClick` calls `Util.handlePlayPauseButtonAction`, whose play branch calls `prepare()` on an idle player, seeks to the default position on an ended one, then plays ([Util.java at 1.11.1](https://github.com/androidx/media/blob/1.11.1/libraries/common/src/main/java/androidx/media3/common/util/Util.java), `handlePlayButtonAction`). So play after a failure prepares again from the same position, as the notification's play does, and play at the end starts again.
  - `ContentFrame` scales the picture with `ContentScale.Fit` by default.
- **Accessibility timeouts.** Android 10 added `AccessibilityManager.getRecommendedTimeoutMillis`, which "provides support for user-defined timeouts for interactive and non-interactive UI elements" ([Android 10 features](https://developer.android.com/about/versions/10/features), "User-defined accessibility timeouts"). Compose's `LocalAccessibilityManager` exposes it as `calculateRecommendedTimeoutMillis` ([AccessibilityManager](https://developer.android.com/reference/kotlin/androidx/compose/ui/platform/AccessibilityManager)).

## Goals / Non-Goals

**Goals:**

- The requirements in specs/player/spec.md, on Android 10 to 17, in light, dark and dynamic color, at the largest font size, and on phones, tablets and split screen.
- Playback belongs to the app, not to the video screen, so the mini player, the notification and the video screen all control one playback.
- Playback state stays out of screen state (ADR 0002). The mini player and fullscreen read the player through media3-ui-compose's state holders.

**Non-Goals:**

- Picture-in-picture, casting, a queue or a next video.
- A picture or thumbnail in the mini player. The design has none, and the app loads no images yet (#176).
- Gestures: swiping the mini player, double tap to seek, pinch to zoom.
- Entering fullscreen by turning the phone. The brief keeps phones in portrait except the fullscreen player, and the prototype enters it with the button.
- Restoring the mini player after Android ends the process. Nothing plays then, and #50's stored positions may bring it later.
- Screenshot tests. #45 sets them up, and whichever of #44 and #45 merges second adds them for the mini player and fullscreen.

## Decisions

### Playback belongs to the app

Minimizing removes the video screen's entry from the back stack, as closing does today, but leaves playback alone. So PlayerViewModel's `onCleared` no longer stops it. It calls a new `PlaybackConnection.release(playbackId)`, which drops the ID's video only when it is neither the player's current item nor the start that waits for the connection, as the pure function `releaseDrops(waitingId, currentItemId, playbackId)` decides. A video whose links failed has no item, so minimizing it keeps nothing, as the privacy requirement's new scenario asks.

Stopping moves to the mini player's close button, which calls the existing `stop(playbackId)` with the player's current item. Removing the app from the recent apps and dismissing the paused notification stop it as today. "Don't keep activities" no longer stops background play, because a cleared ViewModel no longer stops playback.

Alternative: keep the video screen's entry on the back stack while minimized, under a custom Navigation 3 scene that draws it as a bar. That keeps one ViewModel, so the details aren't loaded again, but the back gesture on Search would then have to skip a hidden entry, and the mini player would belong to one entry instead of to the app. Rejected as more complex for one saved request.

### Minimizing

`PlayerRoute(videoId, onClose)` becomes `PlayerRoute(videoId, onMinimize)`, and its button is named "Minimize player", after the prototype. The glyph stays keyboard_arrow_down, as in the "Player" frames. `:app` calls the same `close(key)` for the button and `goBack` for the back gesture, so the double tap rule stays. BackStack.kt's KDoc says that closing an entry no longer stops playback.

### The mini player

`:feature:player-ui` exposes a second public composable, `MiniPlayer(onOpenVideo: (VideoId) -> Unit, modifier)`. It lives with the video screen because it shares the play and pause button, the strings and the way the screen reads the player. It isn't a screen on the back stack: it has no screen state and no ViewModel. It reads `PlaybackConnection.player`, and the controller through `rememberCurrentMediaItemState`, `rememberPlayPauseButtonState` and `rememberProgressStateWithTickCount`. Its play, pause and close are player commands, which go straight to PlaybackConnection, and its tap is an action that changes no state, which goes straight to `:app`'s navigation. "Refining the architecture rules" below says which rules this needs.

It gets PlaybackConnection through a small Hilt entry point, `EntryPointAccessors.fromApplication`, because a composable can't take an injected constructor, and the public composable takes only plain values and actions. Passing the connection in from `:app` would make `:app` hand a player object across the feature boundary.

- **The decisions in a pure function.** `miniPlayerContent(currentItemId, title, channelName, live)`, in `:feature:player-logic`, returns null when there is no current item, so the mini player draws nothing, and otherwise the playback ID that its tap and close act on, the title or null when it is empty or blank, so the UI shows "Now playing", the channel name or null, and whether it draws the progress line, which a live stream doesn't. Its unit tests cover each case. The composable only reads the controller into it and draws the answer.
- **When it shows.** `:app` composes it only while the back stack's top isn't a PlayerKey, as the pure function `showsMiniPlayer(backStack)` in BackStack.kt decides. MiniPlayer draws nothing while the controller is null, or while `miniPlayerContent` returns null. So it shows while an item exists, whatever its state, and goes when `stop` clears the items or the session ends.
- **What it shows**, after the "Mini player on the inbox" frames and the design system's elevation table: a `surfaceContainerHigh` bar with a 2dp line at its top, `primary` for what has played on `surfaceContainerHighest`, then the title in `titleMedium` and the channel name in `bodySmall` and `onSurfaceVariant`, a 40dp FilledIconButton in `primary` with the play_arrow or filled pause glyph, then an IconButton with the close glyph in `onSurfaceVariant`. The title and channel come from the controller's `mediaMetadata.title` and `artist`. "Now playing" is the name the design gives the mini player's region. The line ticks once per pixel of its width, is drawn in the draw phase, so its ticks redraw only the line, and is hidden from screen readers.
- **Opening the video.** The title and channel form one clickable area with the click label "Open video". It looks up the VideoId of `miniPlayerContent`'s playback ID with a new `PlaybackConnection.videoOf(playbackId)`, and calls `onOpenVideo`. `:app` passes `backStack.openVideo`, which already opens only while Search YouTube is on top.
- **Minimizing before the details load** cancels their request with the ViewModel, so the mini player says "Now playing" and the notification shows no title until the video screen opens again and loads them. The spec allows it. Keeping the request alive after the screen closes would need a scope outside the screen for one title, so this is a known trade-off.
- **Insets.** `:app` puts NavDisplay and the mini player in a Column. The mini player pads its content with the system bars and the display cutout, bottom and sides, but not the keyboard, so the keyboard covers it. The box above it measures the mini player's height and passes it to `consumeWindowInsets(PaddingValues(bottom = height))`, so Search YouTube's own `safeDrawing` padding drops the navigation bar and keeps only the part of the keyboard above the mini player. In a wider window the content sits in a centered column of ReadableWidth, and the background fills the width.

Alternatives: a Scaffold's bottom bar in `:app` would need its own inset handling for the same result. Putting the mini player inside each screen would repeat it in every feature.

### Refining the architecture rules

Pending the maintainer's answer to question 4. Two rules say less than this change needs:

- AGENTS.md's module rule, and docs/architecture.md's "Feature modules" and "Navigation", say a feature on `:app`'s back stack "exposes one public composable". `:feature:player-ui` would expose two: `PlayerRoute`, its screen, and `MiniPlayer`, a bar that `:app` places outside the back stack. Both take only plain values and actions, so features still never see each other or Navigation 3.
- AGENTS.md's MVI rule and ADR 0002's note of November 20, 2026 let "the video screen's player commands" go straight to PlaybackConnection. The mini player's play, pause and close are player commands too, from a bar with no screen state, where an intent would pass through a reducer with nothing to change.

So this change updates AGENTS.md's module and MVI rules, docs/architecture.md's "Feature modules" and "Navigation", and adds a dated note to ADR 0002, after the maintainer approves it, as the earlier notes were. Fullscreen's two intents go through the reducer, so they need no exception.

### Going on with a playback that already plays

`startAction` gains a third input and a fourth answer: `startAction(started, playbackIdKnown, playsThisVideo)`, where `playsThisVideo` says that the player's current item is a playback of this screen's VideoId. A first opening that finds it answers GO_ON: the route keeps that playback ID in its saved state as if it had started it, and starts nothing. This covers both the mini player's tap and opening the same video from search. PlayerViewModel gains `follow(playbackId)`, which the route calls for GO_ON and for NOTHING, so a ViewModel made again after "Don't keep activities" still describes and releases the playback.

### The leaving rule moves to the connection

Leaving the app must not start a video that hasn't played, from the video screen or the mini player. The mini player has no saved state that knows whether the video has played, so the rule moves from PlayerRoute's ON_STOP to `setAppVisible(false)`, which MainActivity already calls in `onStop`, skipping configuration changes. The connection listens to its controller and keeps the playback ID that last played. Hiding the app turns off the waiting start's play, and pauses the current item unless it is that ID, as the pure function `pauseOnHide(waitingId, currentItemId, playedId)` decides. `pausesOnLeave` and its tests move from `:feature:player-logic` to `:core:playback` as that function and its tests, with the same cases. PlayerRoute keeps saving the position at ON_STOP, and drops its saved `playedId`.

### The picture while minimized

Pending the maintainer's answer to question 2. PlaybackSessions gains a count of the video screens that hold the picture, and the service shows the picture only while the app can be seen and the count is above zero. PlayerViewModel holds the picture from its creation until `onCleared`, so a configuration change, such as entering fullscreen on a phone, never turns it off, and minimizing turns it off once the exit animation ends. The rule builds on today's: the same track parameter, the same short load when it comes back.

Alternative: count the composed video areas. A configuration change disposes and recomposes them, which would turn the picture off and on during the turn to landscape. Rejected.

### Fullscreen state

PlayerState gains `fullscreen: Boolean`, and PlayerIntent gains EnterFullscreen and ExitFullscreen, which the reducer applies. It is screen state, not a player command, so it goes through the reducer, with its tests. It lives in the ViewModel, so it survives the configuration change that turning to landscape causes. It doesn't survive the end of the process: the screen then comes back in portrait, waiting for play, as today. PlayerRoute shows FullscreenPlayer in place of PlayerScreen while it is on, and keeps its playback rules composed either way.

The fullscreen button sits at the end of the play and pause row, in both the seek bar layout and the live layout, as the "Player" frames show, only while the controller is connected.

### The fullscreen window

A `FullscreenWindow` effect in `:feature:player-ui`, from `LocalActivity.current`:

- On entering, it saves the activity's requested orientation with `rememberSaveable`, because Android keeps the landscape request for a recreated activity, so only the first value is the one to restore. It reads the bars' light or dark icons in each activity, because a recreated activity's `enableEdgeToEdge()` sets them again for the current theme. Then it sets `SCREEN_ORIENTATION_USER_LANDSCAPE`, sets `BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE`, turns `isAppearanceLightStatusBars` and `isAppearanceLightNavigationBars` off, so a bar swiped in over the black window has light icons in light mode too, and hides the system bars.
- On leaving, it restores the saved orientation and icons and shows the system bars, unless the activity is only changing configurations, because the new activity runs the effect again.
- `BackHandler(enabled = true)` sends ExitFullscreen while fullscreen shows. It is composed inside the entry, after NavDisplay's own handler, so it runs first.

Large screens ignore the orientation request, so fullscreen keeps the window's shape there, with black around the video. `:feature:player-ui` adds activity-compose for `LocalActivity` and `BackHandler`. `:app` already ships the same version, so the APK gains no library, and this asks no new dependency.

Alternative: `SCREEN_ORIENTATION_SENSOR_LANDSCAPE` turns either way even with the rotation lock on. USER_LANDSCAPE respects the user's lock, as Android's guidance prefers.

### The fullscreen layout

After the "Player full screen" frames, inside `DewfallTheme(darkTheme = true)`:

- The window is `scrim`, black in both schemes. The picture is ContentFrame with ContentScale.Fit, padded with `safeDrawing`, which is the display cutout while the bars are hidden, so no picture sits under it. Its shutter is `surfaceContainerHighest`, as on the video screen.
- At the top start, the title in `titleMedium` and the channel name in `bodySmall`, on `surface` at the design system's duration-scrim opacity, 0.8, in a `shapes.large` box at most 480dp wide, once the details show.
- At the bottom, one row on the same background: the play and pause button, the times, the seek bar, and the exit button with the fullscreen_exit glyph. A live stream says "Live" in place of the times and the seek bar. Loading shows the spinner over the picture. A failure shows the Banner in the picture's place, with "Try again" where it helps, and the row keeps only the exit button. Waiting after the session was gone shows the play button at the saved position, as the video area does.
- Both overlays sit 16dp inside the window's `safeDrawing` area, so nothing is under the cutout. The frames draw a 16:9 video on a 20:9 phone and put the overlays 16dp inside the picture. Dewfall plays videos of any shape, and a portrait video in landscape leaves a narrow picture with little room inside it, so the overlays follow the window's safe area instead of the picture. For a 16:9 video on a 16:9 window the two are the same.
- The screen stays on while the video plays, with the same `keepScreenOn` rule.

`:core:designsystem` gains `DewfallOpacity.durationScrim`, 0.8, and DesignTokensTest checks it against tokens.json, so the token reaches the code with its first use, as architecture.md says.

### Fullscreen controls that hide

Pending the maintainer's answer to question 3. The title and the controls show on entering, and hide `calculateRecommendedTimeoutMillis(3000, containsIcons = true, containsText = true, containsControls = true)` milliseconds after the last touch while the video plays. Every pointer event in fullscreen, seen in the initial pass without consuming it, restarts the timer. A tap on the picture toggles them. They stay while the play button shows, that is while paused, ended or failed, and while it loads. They appear and disappear without animation, because the design system's motion only answers a tap, and the hide answers a timeout.

### Strings and glyphs

New strings in `:feature:player-ui`: "Minimize player", which replaces "Close video", "Full screen", "Exit full screen", "Open video", "Close player" and "Now playing". The fullscreen and fullscreen_exit glyphs come from Material Symbols Rounded at the same commit as the other glyphs, weight 400, grade 0, optical size 24, outlined, with the same header comment, into `:core:designsystem`, whose KDoc and architecture.md row list them.

### Tests

- Reducer: EnterFullscreen and ExitFullscreen, and that neither touches the details.
- `startAction`: every combination, with GO_ON only on a first opening whose video plays.
- `:core:playback`: `releaseDrops`, `pauseOnHide`, with `pausesOnLeave`'s cases, and the picture count in PlaybackSessions, with the app hidden, no screen, one and two screens.
- `miniPlayerContent`: no current item, a title, an empty and a blank title, no channel name, and a live stream.
- `:app`: `showsMiniPlayer` with Search on top, a PlayerKey on top, and both.
- PlayerViewModelTest: `onCleared` releases instead of stopping, `follow` makes a later description reach the connection, and the ViewModel holds the picture until it is cleared.
- Hand checks on emulators with Android 10, 12L and 17, because the change touches `:core:playback`, the video area and the visibility signal. docs/testing.md's list gains the mini player, minimizing, stopping from the mini player, tapping the notification while minimized, the back gesture on Search on Android 10, which finishes the root activity there, fullscreen on a phone and a tablet, the bar icons swiped in over fullscreen in light mode, the back gesture in fullscreen, the cutout, the keyboard over the mini player, and the controls' timeout with the accessibility setting `accessibility_interactive_ui_timeout_ms`.

### Docs

AGENTS.md's module and MVI rules, and a dated note in ADR 0002, as "Refining the architecture rules" says. docs/architecture.md: "Feature modules" and Navigation's public composables and the Column, The theme's darkTheme sentence, `:core:designsystem`'s glyph list and DewfallOpacity, and The player: who owns playback, minimizing and the mini player, the release and leaving rules, the picture rule, fullscreen, and the corrected "Don't keep activities" and `onCleared` sentences. docs/testing.md: the hand checks, and the sentence about "Don't keep activities". The player spec's Purpose drops "Later changes add the mini and fullscreen players". AGENTS.md's "Where things are" names the mini player among feature/'s screens.

## Questions for the maintainer

Questions 1, 2, 3 and 5 are visible to users and not covered by the roadmap, the brief or the designs. Question 4 widens an ADR's decision, which needs the maintainer's dated approval.

1. **How does the user stop playback?** The design's mini player has no way to close it, and while the app shows, Android never stops a paused player. Recommendation: a close button at the mini player's end, named "Close player", which stops playback and removes the notification. Alternative: swipe the mini player away, with an accessibility action for screen readers.
2. **The picture while minimized.** Recommendation: turn the picture off while minimized, as in the background, which saves the data of a video-only file, at the cost of a short load when the video screen opens again. Alternative: keep fetching the picture, so reopening shows it at once.
3. **Fullscreen controls.** Recommendation: hide them 3 seconds after the last touch while the video plays, or later when the accessibility setting asks, and show them on a tap, as Android's immersive content guidance shows. Alternative: always show them.
4. **The architecture rules.** May `:feature:player-ui` expose a second public composable, the mini player, and may the mini player's play, pause and close go straight to PlaybackConnection, as the video screen's player commands do, with a dated note in ADR 0002 and the matching lines in AGENTS.md? Recommendation: yes. Alternative: give the mini player its own MVI state and ViewModel, whose intents would pass through a reducer that changes nothing.
5. **A paused, minimized video in the background.** When Android stops the player of a paused, minimized video in the background, the mini player goes away, and the position is lost until #50 stores positions. The video screen keeps its position in saved state, but the mini player has none. Recommendation: accept it for now. Alternative: keep the last minimized video and position in `:app`'s saved state and show a mini player that waits for play.

## Risks / Trade-offs

- [Turning to landscape recreates the activity, so the picture's SurfaceView attaches again and may flash black for a frame] → ContentFrame's shutter covers the gap, and the hand checks look for a pause in the sound.
- [The orientation saved before fullscreen may be wrong if the user enters fullscreen during another orientation request] → The video screen makes no other request, and the manifest's portrait is the only other value.
- [Moving the leaving rule changes code that #43's hand checks covered] → The same cases move with their tests, and the hand checks "Leaving early" and "Leaving from the mini player while the video loads" run again.
- [The mini player's tap loads the details again, one request to YouTube each time] → The same request as opening the video from search. No stream links are fetched again.
- [A wrong inset sum could hide the last search result behind the mini player, or leave a gap above the keyboard] → A hand check at the largest font size, with and without the keyboard, and on a phone with gesture and three-button navigation.

## Migration Plan

No data changes. The release has no users yet. Rolling back is a revert of the pull request.
