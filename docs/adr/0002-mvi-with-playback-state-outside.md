# 0002. Use MVI for screen state, with playback state kept outside it

## Status

Accepted

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
- An Intent sealed interface for what the user does.
- One-time Effects.
- A reducer, a pure function that takes the old state and an intent and returns a new state.

MVI still uses Android's ViewModel. The ViewModel runs side effects. Every reducer has unit tests.

Playback state never goes into screen state. The player lives in a Media3 MediaSessionService and exposes playback state as its own stream. Position updates run only as often as the visible UI needs them.

## Consequences

- Most screen logic sits in reducers, which can be tested in milliseconds without a phone.
- The screen does not redraw every time the playback position changes.
- A screen that shows playback reads from two sources: its own MVI state and the player's playback stream.
- Every screen needs more code, with State, Intent and Effect types even for simple screens.
- A single state object can cause extra redraws if it changes too often. That is why playback position stays outside screen state.
- Contributors who only know MVVM have more to learn.
- There is no official Android MVI library, so Dewfall maintains its own small base classes.
