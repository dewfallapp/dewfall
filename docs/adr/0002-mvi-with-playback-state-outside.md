# 0002. Use MVI for screen state, with playback state kept outside it

## Status

Accepted

Changed in #136: the reducer can return effects, intents include work results, and effects are delivered as state.

Changed in #41, which the maintainer approved on November 19, 2026: an action that starts in the UI, changes no state and needs no business logic, such as opening or closing a screen, goes straight to the app's navigation, with no intent or effect. The UI calls the action its caller gave it, as Android's [UI events guidance](https://developer.android.com/topic/architecture/ui-layer/events) describes. Effects stay for instructions that start in the ViewModel.

Changed in #42, which the maintainer approved on November 20, 2026: the video screen's player commands go straight to the player through PlaybackConnection, not through intents. They are play, pause, seek, the playback "Try again", the first start, and play after the session was gone. The screen reads playback state through the MediaController with media3-ui-compose's state holders, at the rate the visible controls need, never in screen state. This is an exception to the intent rule, not a new decision: this ADR already keeps playback state out of screen state, and an intent for each tap would only pass through the reducer unchanged. See [The player](../architecture.md#the-player).

Changed in #44, which the maintainer approved on November 21, 2026: the same exception covers the mini player. It is a bar that `:app` places under the back stack, with no screen state and no ViewModel. Its play, pause and close go straight to the player through PlaybackConnection, and its tap, which opens the video screen, goes straight to `:app`'s navigation. It reads playback state through the MediaController with media3-ui-compose's state holders, never in screen state. So `:feature:player-ui` exposes two public composables, the video screen and the mini player. The fullscreen player's state goes through the video screen's reducer as usual. See [The mini player](../architecture.md#the-mini-player).

## Context

Every screen needs a clear way to hold its state and handle what the user does. Screen logic should be easy to test without a phone.

Plain MVVM usually spreads screen state across several separate observable fields. Those fields can change one at a time, which allows combinations that should never appear together. MVI avoids this:

- Each screen has one immutable state, so it can never show a half-updated mix of values.
- Every user action goes through a single entry point as an intent, so it is easy to trace why a screen changed.
- The reducer is a pure function, so most screen logic can be unit tested in milliseconds without a device.
- The pattern is the same on every screen, which helps contributors and AI coding agents.

Using MVI well was also one of the maintainer's goals for the project.

Video playback is different from the rest of the screen. The playback position changes several times a second. If that position sat inside one big screen state object, every update would redraw large parts of the screen and drain the battery.

## Decision

Every screen uses MVI:

- An immutable State.
- An Intent sealed interface for what the user does, and for the results of work the ViewModel runs, such as a load that finished.
- One-time Effects, held as pending UI state until the UI reports that it handled them, as Android's [UI events guidance](https://developer.android.com/topic/architecture/ui-layer/events) recommends.
- A reducer, a pure function that takes the old state and an intent and returns the new state and any one-time effects.

MVI still uses Android's ViewModel. The ViewModel runs side effects. Every reducer has unit tests.

Playback state never goes into screen state. The player lives in a Media3 MediaSessionService and exposes playback state as its own stream. Position updates run only as often as the visible UI needs them.

## Consequences

- Most screen logic sits in reducers, which can be tested in milliseconds without a phone.
- The screen does not redraw every time the playback position changes.
- A screen that shows playback reads from two sources: its own MVI state and the player's playback stream.
- Every screen needs more code, with State, Intent and Effect types even for simple screens.
- The UI must report each effect as handled. Pending effects are lost on process death.
- A single state object can cause extra redraws if it changes too often. That is why playback position stays outside screen state.
- Contributors who only know MVVM have more to learn.
- There is no official Android MVI library, so Dewfall maintains its own small base classes.
