# Proposal

## Why

Phase 4's task list ends the player with "Mini player and fullscreen player" ([roadmap](../../../../docs/roadmap.md#phase-4-search-and-play)), issue [#44](https://github.com/dewfallapp/dewfall/issues/44). Today the video screen's top button closes the screen and stops playback, so the user can't look for the next video while one plays, and a video can only fill a 16:9 box above its details. The design has both missing sizes: "Mini player on the inbox" and "Player full screen" in [docs/design/screens.md](../../../../docs/design/screens.md#player).

## What Changes

- **Minimize instead of close.** The video screen's top button becomes "Minimize player", as the "Player" frames' prototype names it. It and the system's back gesture go back to the screen the video was opened from, as that screen was, and leave playback as it is: a playing video plays on, a paused one stays paused.
- **The mini player.** While the player has a video and the video screen doesn't show, a mini player sits at the bottom of the screen, today Search YouTube, as the "Mini player on the inbox" frames show: a thin progress line, the title and channel name, and a play and pause button. Tapping its title opens the video screen for that video, which goes on playing where it is, without starting again. Phase 5's navigation bar will sit under it.
- **Stopping.** Closing the video screen no longer stops playback. The user stops it with the mini player's close button, which the maintainer chose on November 21, 2026. Removing Dewfall from the recent apps still stops it, and so does dismissing the paused notification, as today.
- **Fullscreen.** A fullscreen button joins the video screen's controls, as the "Player" frames show. Fullscreen uses the dark scheme in light mode too, hides the system bars, turns a phone to landscape, and shows the video in the whole window, with black pillars around it. The title and channel name sit at the top, and the play and pause button, the times, the seek bar and an exit button at the bottom, as the "Player full screen" frames show. The exit button and the back gesture return to the video screen in portrait.
- **One playback for the whole app.** Leaving the app still pauses a video that hasn't played yet, now for the mini player too, so a video never starts in the background. Opening, from search, the video that the mini player plays goes on with it instead of starting it again.

## Capabilities

### New Capabilities

None.

### Modified Capabilities

- `player`: minimizing replaces closing; the mini player; stopping from the mini player; the fullscreen player; the leaving rule covers the mini player; the media session's title goes when the mini player closes, not when the video screen closes.

## Impact

- `:feature:player-ui`: the minimize button and the back gesture, the fullscreen button and the fullscreen player, and a new public composable, `MiniPlayer(onOpenVideo)`, which reads the player through its MediaController, never through screen state. `PlayerRoute(videoId, onClose)` becomes `PlayerRoute(videoId, onMinimize)`. Its ViewModel no longer stops playback when it is cleared.
- `:feature:player-logic`: PlayerState gains whether the screen is in fullscreen, with two intents and their reducer tests, the start decision gains going on with a playback that already plays the video, and the mini player's and fullscreen controls' decisions are pure functions with tests.
- `:core:playback`: PlaybackConnection gains the video of a playback ID, a release that keeps the player's own item, and the leaving rule, which moves here from the video screen.
- `:core:designsystem`: the fullscreen and fullscreen_exit glyphs from Material Symbols Rounded, under the Apache License 2.0 like the others.
- `:app`: shows the mini player under the back stack while no video screen is on top, and opens the video screen from it.
- `:feature:player-ui` adds activity-compose, for the back gesture in fullscreen and the activity. `:app` already ships it, so the APK gains no library.
- No new host, no new log line, and nothing new leaves the device.
- Rules: AGENTS.md's module and MVI rules and ADR 0002 gain the mini player, a second public composable whose player commands skip intents, which the maintainer approved on November 21, 2026 (design.md's question 4).
- Docs: docs/architecture.md's "Feature modules", "Navigation", "The theme" and "The player", docs/testing.md's hand checks, and the player spec.
- Screenshot tests of the mini player and the fullscreen player need #45's setup. If #45 merges first, this change adds them. If this change merges first, #45 adds them.
