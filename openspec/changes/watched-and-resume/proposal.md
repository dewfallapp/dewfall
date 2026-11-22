# Proposal

## Why

Phase 5 is done when "resuming a half-watched video starts where you stopped" ([roadmap](../../../docs/roadmap.md#phase-5-subscriptions-and-the-inbox)). Today every video starts from the start, unless the same screen comes back after Android ended the app. The database from #48 can already keep a position and a watched mark for each video, but nothing writes to it or reads from it. The inbox (#49) and the later cleanup ([ADR 0005](../../../docs/adr/0005-download-deletion-rules.md)) need to know which videos are watched, and [docs/vision.md](../../../docs/vision.md) says a video counts as watched at about 90 percent. This change does [#50](https://github.com/dewfallapp/dewfall/issues/50).

## What Changes

- **Playback saves where you stopped.** While a video plays, the player saves its position in the database when it pauses, when it stops or another video takes its place, when the playback service ends, and every 30 seconds while it plays, so a phone that turns off loses at most 30 seconds. It never saves on every tick of the seek bar. A live stream saves nothing, and neither does a player that failed.
- **Opening a video goes on from there.** Opening a video starts it from its saved position, even after the app was closed or the phone restarted. A video with no saved position starts from the start, and a live stream from its live edge, as today.
- **A video counts as watched near its end.** When playback goes past 90 percent of the video's length, or reaches its end, the video is marked watched. A video marked at 90 percent keeps its place. Only a video that played to its end saves position zero, so it plays from the start next time. Playback marks a video only when it crosses 90 percent, so a video the user marked unwatched past that point stays unwatched until it plays to its end.
- **While the screen waits, the stored position wins.** A video screen that waits for play, after the system ended the app or stopped the player, waits at the position stored in the database, which is later than the screen's own when the video played on in the background.
- **The emulator tests get their own database.** Each one starts with no saved positions, so one test's position never changes where the next one starts.
- **"Mark as watched" on the video screen.** The video screen gets the design system's toggle chip from the "Player" frames, under the title. Once the video is watched it says "Watched" with a check, and tapping it takes the mark away. It follows the database, so it changes when playback marks the video too. Marking by hand keeps the saved position.
- **The pure rule.** Whether a position counts as watched is a pure function in `:core:model`, so the player, its tests and later the inbox and cleanup share it.
- Playback state still stays out of screen state. The watched mark is database data, not playback state, so the video screen's state holds it.

## Capabilities

### New Capabilities

None.

### Modified Capabilities

- `player`: opening a video goes on from its saved position; playback saves the position and marks a video watched near its end; the video screen has the "Mark as watched" toggle chip. #48's requirements on what the database keeps stay as they are.

## Impact

- `:core:model`: the watched rule, `countsAsWatched`, and its tests.
- `:core:playback`: PlaybackService saves positions and marks videos through WatchStateRepository, which `:core:data` already has, and starts a video with no explicit start position from its saved one. New JVM tests for the decisions.
- `:feature:player-logic`: PlayerState holds the video's stored watch state, with intents for the database's answer and the chip, and a pure function that picks the stored or the screen's position. Tests for both.
- `:feature:player-ui`: PlayerViewModel follows the video's watch state and marks or unmarks it. PlayerScreen shows the chip. Two new strings, previews and screenshot test references for both states, and a ViewModel test.
- `:core:designsystem`: the `done_all` and `check` glyphs.
- `:app`'s androidTest: an in-memory database module for the emulator tests, and ResumeTest. app/build.gradle.kts adds `:core:database` to androidTest only, so the module can name the database's types. The emulator tests workflow runs on the pull request.
- No change to the database schema, the DAOs or WatchStateRepository's functions. No new dependency.
- Docs: docs/architecture.md (the player, the video screen, and the lines that name #50), docs/testing.md (the player's hand checks: opening a video again now resumes it), and AGENTS.md's icon line, which now lets a glyph next to its own word go without a content description, as the maintainer chose.
- Privacy: positions and marks stay in the app's own database, which the player spec's privacy requirement already allows. Nothing new is sent. One new constant-text failure line is logged when the database can't be read or written, with no video ID or position.
- The inbox (#49) reads the watched mark from the database as `watched_at IS NOT NULL`, as agreed with its session.

## The maintainer's answers

In this change's Claude Code session's question dialog, the maintainer answered eight questions, the first seven on November 22, 2026, and the eighth on November 23, 2026:

1. "Plan for #50: Dewfall saves where you stopped each video (on pause, stop, switching video, and every 30 s while playing) and opens it there next time, even after a restart. The video screen gets the "Mark as watched" button from the design. Approve this plan?" Answer: "Approve (Recommended)". This is the approval that AGENTS.md's "Spec first" asks for.
2. "When should playback mark a video watched on its own?" Answer: "At 90% (Recommended)".
3. "After a video is marked watched by playback (90% or end), where should it start next time you open it?" Answer: "From the start (Recommended)".
4. "Once a video is watched, what should the video screen's button say?" Answer: "Mark as unwatched (Recommended)". Answer 7 replaces it.
5. After the maintainer subagent's plan critique asked it: "Follow-up on #50: you chose that a video playback marked watched (at 90%) opens from the start next time. For long videos that throws away the place: a 3-hour video paused at 2:45 would reopen at 0:00. Which should it be?" Answer: "Keep place till end (Recommended)", whose option said: "Marked watched at 90%, but reopens where you left it. Only a video that played to its very end reopens from the start." It replaces answer 3 for videos marked at 90 percent.
6. After the second plan critique: "AGENTS.md says every icon has a content description, but the design system says a glyph next to its word needs none. For the "Mark as watched" button's glyph, which rule should win?" Answer: "Allow none, fix AGENTS (Recommended)", whose option said: "A glyph beside its own word gets no description, so screen readers say just "Mark as watched". This PR updates AGENTS.md's wording to match the design system."
7. After the third plan critique found that the design system already defines the control: "Correction on #50's button: I missed that the design system already defines it as a toggle chip, "Mark as watched" that becomes "Watched" with a check (selected look), in a row where Keep and Downloaded join later. Earlier you picked "Mark as unwatched" from my options. Which should the video screen use?" Answer: "Design system chip (Recommended)", whose option said: ""Mark as watched" toggle chip; once watched it shows "Watched" with a check and a filled look. Tapping it again unmarks. Matches the Player frames and the later Keep chip."
8. After the fifth plan critique: "Final check on #50's plan as revised after five critiques: resume from the saved place; marked watched when playback crosses 90% (keeps place) or ends (restarts next time); the design system's "Mark as watched" / "Watched" chip; a waiting screen uses the stored position; emulator tests get their own empty database. Approve the change as now written?" Answer: "Approve (Recommended)". This is the approval of the change as it now stands.
