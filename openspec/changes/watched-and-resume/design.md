# Design

## Context

See proposal.md for why. What is there today:

- PlaybackService, in `:core:playback`, owns one ExoPlayer, which plays on after the video screen closes, in the mini player and in the background. Screens reach it through PlaybackConnection, whose `prepare(videoId, play, startPositionMs)` starts a new playback under a random playback ID. `C.TIME_UNSET` means "from the start, or the live edge". The session's `onSetMediaItems` callback resolves the ID into a playable item in a coroutine, through PlaybackItems and PlaybackResolver.
- PlaybackItems maps playback IDs to video IDs. An entry lasts while its item is the player's. MediaControllerConnection's `stop`, which the mini player's close button calls, removes the entry before the stop reaches the service.
- PlayerRoute keeps a position in the screen's saved state, for the screen that comes back after process death. That stays as it is.
- `:core:playback` already depends on `:core:data`, so it can use WatchStateRepository. Its writes are main-safe and run in the `@ApplicationScope` scope, so a write that has started finishes even if its caller is cancelled.
- The player spec already allows the watch state in the app's database ("Video details stay private"), and #48 defined what `savePosition`, `markWatched` and `markUnwatched` do.

## Goals / Non-Goals

**Goals:**

- One place saves progress: the playback service, because playback outlives every screen.
- Saving costs a database write per pause or stop, plus one every 30 seconds while playing, never one per position tick.
- The watched rule is a pure function with unit tests.

**Non-Goals:**

- Showing progress or watched marks in lists. The inbox (#49), the channel page and the channels screen decide that. They read `watched_at IS NOT NULL` from the database.
- Cleanup of watched downloads (Phase 7) and a setting for the 90 percent rule.
- A "Mark as watched" action outside the video screen, such as in a row's menu.
- Changing the database, its DAOs or WatchStateRepository's functions.

## Decisions

### The playback service saves progress

PlaybackService gets a new class, WatchProgress, a `@Singleton` in `:core:playback`. The service tells it which video is current, and when to save. It is a singleton with its own scope, not the service's, so a save that the service asks for in `onDestroy` still reaches the repository after the service's scope is cancelled.

Alternatives considered:

- **The video screen saves.** It can't: playback goes on in the mini player and the background after the screen's ViewModel is gone.
- **MediaControllerConnection saves.** It lives in the same process and outlives screens, but it sees the player only through the session, and it doesn't hear the service's own pause on a failure or on removing the app. The service sees everything.

### When it saves

The service's player listener asks WatchProgress to save the current item's position when:

- play turns off (a pause from any screen, the notification, a headset, losing audio focus, or removing the app from the recent apps), while the player isn't idle;
- the position jumps while play is off, such as a seek while paused;
- the player reaches the end (it is then marked watched);
- the player becomes idle with no error, which is a stop, such as the mini player's close button;
- just before `onSetMediaItems` puts another video in its place;
- in `onDestroy`, before the player is released;
- every 30 seconds while it plays, from a coroutine in the service's scope that runs only while `isPlaying` is true.

It never saves from an idle player with an error, because after a failure the position may not be where the video was, as PlayerRoute's own saves already assume. It never saves a live stream, whose position means nothing the next day.

Each save reads the player inside the callback that names its trigger, `onPlayWhenReadyChanged`, `onPositionDiscontinuity` or `onPlaybackStateChanged`, which Media3 calls within the command that changed the player, never in `onEvents`, which comes after the looper message. The mini player's close sends `stop()` and then `clearMediaItems()`, which the session may run in one message, so `onEvents` could see the player already empty at position 0. `onPlaybackStateChanged` for the stop runs before the clear, with the position still there, as Player.stop() keeps it. ResumeTest catches a mistake here.

30 seconds is a trade-off between writes and what a crash or a dead battery loses. Every pause and stop saves exactly, so the interval matters only when the process ends without warning. A write is one small UPDATE in one transaction.

### Which video it saves for

WatchProgress keeps the video ID of the current item, which it reads from PlaybackItems when the current item changes, and whether the item is live, from its tag. It keeps it only while that item is the player's, like PlaybackItems does, and drops it when the item goes. It needs its own copy because MediaControllerConnection's `stop` removes the playback ID's entry before the stop reaches the service, and the save on stop happens after that. The player spec's privacy requirement allows the playing video's ID in the playback service while its playback lasts. This change makes it say that the ID goes once that playback's last save has run.

### Writes run in order

WatchProgress's public functions run on the main thread, where the service's listener and callbacks run. Each one reads the position there and puts one write into a Channel with no capacity limit, in call order, with `trySend`, which never suspends. One coroutine in WatchProgress's own scope takes the writes from the Channel one at a time, and finishes each write before it takes the next. So a pause's save and `onDestroy`'s save land in the order the service asked for them, whatever dispatcher the repository uses. The scope's dispatcher is PlaybackDispatchers' `main`, which Hilt injects, so a test replaces it.

The test makes the fake repository hold its first write until the test lets it go, asks for two saves, and checks that the second write hasn't started while the first is held. A version that launched each write on its own would fail it.

### A failed read or write

RoomWatchStateRepository rethrows what Room throws, such as SQLiteFullException on a full disk, which a download app can meet. No such failure may crash the app or stop saves:

- **Writes.** WatchProgress's consumer runs each write as a child coroutine in a `supervisorScope`, with a CoroutineExceptionHandler, and joins it before it takes the next write. A write that throws anything but cancellation ends only its own child. The handler logs one constant-text line through PlaybackLog, with the exception's class names and stack frames only, as ADR 0007 allows, and no video ID or position. The consumer then takes the next write, so a later save tries again.
- **The resume read.** `onSetMediaItems` reads the watch state with Flow's `catch` operator, which logs the same kind of line and gives no saved position, so the video starts from the start. The read runs inside the coroutine that already completes the future with the resolved item, so the future always completes.
- **The video screen.** PlayerViewModel's collection of the watch state uses `catch` the same way, dispatches `WatchStateUnreadable`, which sets `saved` to `Unreadable`, and the chip stays hidden. Its taps run with a CoroutineExceptionHandler, and the chip stays as it was. PlayerViewModel has no Logger, so these two log nothing; the service's line covers a database that fails.

None of these catches a particular exception class, so the JVM tests make the fake repository throw an ordinary IllegalStateException. Android's own SQLException can't be created in a local unit test, whose mockable android.jar throws "Method ... not mocked" ([local tests](https://developer.android.com/training/testing/local-tests)), and the plan adds no Robolectric or mocking library. The handler also catches exceptions nobody expects, so a bug in a save can't stop later saves without a log line.

### The watched rule

`countsAsWatched(position: Duration, length: Duration?): Boolean` in `:core:model`: true when the length is known and above zero, and the position is at least 90 percent of it. The 90 percent comes from docs/vision.md ("about 90 percent"), and the maintainer confirmed it.

### Playback marks a video only when it crosses 90 percent

If playback marked every save at or past 90 percent, a user who marked a video unwatched while it played at 95 percent would see it marked again at the next save, and from Phase 7 on, the next night's cleanup would delete it ([ADR 0005](../../../docs/adr/0005-download-deletion-rules.md)). So WatchProgress marks a video only when, during one playback, it sees the position below 90 percent and then at 90 percent or more, or when the player ends.

It looks at the position at each save, at each 30-second tick, when the item first becomes ready with a known length, and when the position jumps, such as a seek while playing. Looking doesn't write a position; only the save triggers above do. Each look updates one flag per playback: "seen below 90 percent". A look at 90 percent or more with the flag set marks the video and clears the flag. A playback that starts at or past 90 percent, such as one the user unmarked there, never has the flag set, so only the end marks it.

WatchProgress also follows the current video's watch state while it is the player's item, and clears the flag whenever the video becomes watched, by playback or by hand. So a user who crosses 90 percent, marks the video by hand and unmarks it before playback's next look isn't overruled by that look.

A live stream is never marked by playback, even at its end, because it has no length to count from and saves nothing.

The pure function `progressStep(belowSeen, positionMs, durationMs, ended)` in `:core:playback` returns the new flag and whether to mark. It calls `countsAsWatched`, so the 90 percent rule lives in one place. A JVM test covers a crossing, a start past 90 percent, an unmark after the crossing, a seek back below and forward again, an unknown length and the end.

### Only the end saves zero

The maintainer chose on November 22, 2026 that a video marked at 90 percent keeps its place, so a 3-hour video paused at 2:45 opens at 2:45. Only a video that played to its end saves position zero, so it opens from the start next time. So every save but the end's writes the position as it is, and no later step needs the video's length. Marking by hand keeps the position too, as #48's requirement says.

The end needs care, because PlaybackService pauses the player when it ends, and a stop keeps the position: Media3's [Player.stop()](https://developer.android.com/reference/androidx/media3/common/Player#stop()) "does not ... reset the playback position". So closing the mini player after the end would save the length. WatchProgress keeps an "at the end" flag per playback: set when the player ends, cleared when it plays again or the position jumps, such as play again from the start or a seek. Every save while it is set writes zero: the end's own save, a stop, a replace and `onDestroy`.

`progressSave(positionMs, atEnd)` turns a save into the position to write: zero at the end, otherwise the position, with negative or unknown positions as zero, as `savePosition` already does. Its test covers a save at the length with the flag set.

### Resuming happens in `onSetMediaItems`

When a start asks for `C.TIME_UNSET`, `onSetMediaItems` reads the video's watch state through WatchProgress while it resolves the links. WatchProgress puts the read into the same Channel as its writes, so it runs after every save the service asked for before it, such as the stop's save when the user closes the mini player and opens the video again at once. It starts from the saved position when there is one above zero and the item isn't live. A pure function, `startPositionFor(requestedMs, saved: Duration?, live)`, decides. An explicit start position wins. Media3's MediaSession.Callback reference allows `onSetMediaItems` to change the start position.

So every way of opening a video resumes it, and no screen has to wait for the database before it calls `prepare`. The read happens beside the network request for the links, so it adds no wait. The alternative, reading in PlayerViewModel before `prepare`, makes a synchronous call asynchronous, and the route keeps the playback ID that `prepare` returns.

### While the screen waits, the stored position wins

PlayerRoute keeps a position in the screen's saved state, which Android writes only when the activity stops. The screen waits for play in two cases: after process death, and after Android stopped the player in the background (`waiting = started && !known`). In both, a video that played on in the background after the activity stopped has a later position in the database, which every pause, stop and tick updates. docs/architecture.md says the screen's position stays "until #50 stores positions". So the screen now prefers the stored position:

- PlayerState holds `saved: SavedWatch`, which is one of three: `Unknown` until the database's first answer, `Unreadable` when the read failed, or `Known(watched, position)`, where `position` is the latest stored position, or null when the video has no watch state. It is the database's data, not the player's state: it changes only when a save or a mark lands, at most once every 30 seconds while the video plays, and the screen's state never follows the playing position. Keeping the latest position, rather than reading the database only when the screen starts to wait, keeps the route a plain function of its state, and a change every 30 seconds is far from the several times a second that ADR 0002 keeps out. docs/architecture.md and a dated note in ADR 0002 repeat this reason, so it isn't taken as a precedent for live positions in screen state.
- A pure function in `:feature:player-logic`, `waitingPosition(saved, screenPositionMs)`, returns a WaitingPosition with `shownMs`, the position that the waiting video area shows, and `startMs`, the one that play while waiting and "Try again" with no item of this screen start from:
  - `Known` with a position: that position, so a stored zero wins over the screen's length after the end.
  - `Known` with no watch state, or `Unreadable`: the screen's own position, so a failed read still waits at and plays from where the screen was.
  - `Unknown`: a null `startMs`, so PlayerRoute, in `:feature:player-ui`, passes Media3's `C.TIME_UNSET` and `onSetMediaItems` reads the database itself. The video area shows the screen's own position meanwhile. Both are `Long?` milliseconds with no Media3 sentinel, because `:feature:player-logic` is plain Kotlin/JVM with no Android or Media3 types (ADR 0006).
- Its test covers each of the four, and a video that resumed at 12:30, played to 40:00 and paused, where the latest answer, 40:00, wins.

Within one process the database is at most 30 seconds behind the screen's saved position: the screen saves on pause, end and ON_STOP, and the service saves on pause and end too, but not at ON_STOP while the video plays on, so until the next tick the database can hold a position up to 30 seconds older. Once the video plays on in the background, the database moves ahead. At the end the database holds zero and the screen the length, and zero is what the maintainer chose.

### The video screen's chip

PlayerViewModel collects `watchState(videoId)` for as long as it exists and dispatches `WatchStateChanged`. The chip's taps send `MarkWatched` or `MarkUnwatched`, which change no state; the ViewModel calls the repository, and the change comes back through the Flow, so the chip always shows what the database holds. Room answers in milliseconds, so the tap looks instant.

The design system's Chip component makes this control a toggle chip: "'Mark as watched' becomes 'Watched'" (docs/design/claude-design/design-system/components/Chip/README.md), and the "Player" frames draw it as a chip with `shape-medium` corners. The maintainer chose the design system's chip on November 22, 2026. So it is a `FilterChip`, as the Chip README's Compose section says, with `shape = MaterialTheme.shapes.medium`, the selected colors `secondaryContainer` and `onSecondaryContainer`, and an `outline` border while unselected. Unselected, it says "Mark as watched" with the `done_all` glyph, as in the frames. Selected, it says "Watched" with the `check` glyph, which replaces the other glyph, as the README says. It sits in a FlowRow, where Phase 6's "Downloaded" and Phase 7's "Keep" join it later.

It shows under the publish line once the details show and the first answer has come, so it never flips on open. The glyph sits next to its word, so it has no content description of its own, as the design system's Iconography allows. AGENTS.md says "every icon has a content description", so the maintainer chose on November 22, 2026 to let the design system's rule win, and this change updates AGENTS.md's line to say that a glyph next to its own word needs none. FilterChip gives the chip its selected state for screen readers. Two strings are new, "Mark as watched" and "Watched". The `done_all` and `check` glyphs are copied from Material Symbols Rounded into `:core:designsystem`, where every shared glyph lives, because `check` is the design system's selected-chip glyph, which Phase 7's Keep chip reuses.

### Tests

- `countsAsWatched`: below 90 percent, exactly 90 percent, above, unknown length, zero length.
- `progressStep`, `progressSave` and `startPositionFor`: each branch.
- The listener's decision of when to save, as a pure function over what changed and the player's state, so a JVM test checks each trigger and each "never", the stop after the end included.
- WatchProgress with a fake repository: the order of writes (the held-write test above), a failed write followed by one that lands, the end then a stop saving zero, the flag cleared by a hand mark, a live item, an item with no video, a replacement that fails and leaves the player empty and ended (no mark and no zero for the old video), that the video ID is gone once the stop's save has run, the mini player's close (a stop, then the items cleared) saving the position and marking nothing, and a link refresh, which replaces the item under the same playback ID, keeping the video ID and both flags.
- PlayerReducer: the four new intents. PlayerViewModel: the chip's state follows a fake repository's Flow, and the taps call it. `waitingPosition`, with the cases above.
- Screenshot tests: the details with the chip in each state, in the existing light, dark, large font and dynamic color previews.
- The emulator tests: see below.
- Hand checks on Android 10, 12L and the newest Android, as docs/testing.md asks for a `:core:playback` change: pause and reopen, close the mini player and reopen, remove the app from the recent apps and reopen, play to the end, the chip in both directions, the chip changing on its own past 90 percent, unmarking past 90 percent, the background listen before process death, and the resumed video paused in the background until Android stops the player.

### The emulator tests stay independent

The emulator tests use the real DatabaseModule, and four of them open the same 30-second test video. With this change, each test's stop saves its position, so the next test would resume from it, and SearchThenPlayTest's check that the video played past 3 seconds could pass without playing from the start. So `:app`'s androidTest gets a `@TestInstallIn` module that replaces DatabaseModule with an in-memory database, as DatabaseModule's KDoc anticipates. Hilt makes a new singleton component for each test, so each test starts with an empty database. The module replaces all of DatabaseModule, so it also provides the DAOs, the Clock and the `@ApplicationScope` scope, with a SupervisorJob as the real one has. It builds the database as `buildDewfallDatabase` does, with the same driver, except in memory. It names `DewfallDatabase` and the DAOs, which are `:core:database` types that `:core:data` keeps to itself (`implementation`), so app/build.gradle.kts adds `androidTestImplementation(project(":core:database"))`, with a comment, as it does for `:core:youtube`. The emulator tests workflow then runs on the pull request, because it changes the build.

One new emulator test, ResumeTest, checks the resume end to end through the real PlaybackService. It opens the 30-second test video, lets it play past 10 seconds, pauses it through PlaybackConnection, so it stays well below 27 seconds, where it would count as watched, minimizes it and closes the mini player. It then waits until the injected WatchStateRepository holds a position of at least 10 seconds, so the save has landed. It opens the video again, and checks the player's position at its first ready state after the reopen, read by a Player.Listener added before the reopen, not by polling: at least 10 seconds. A start from zero is at zero when it first becomes ready, so it can't pass. Task 4 runs `:app:connectedDebugAndroidTest`, and docs/testing.md#emulator-tests says so.

### Docs that named #50

docs/architecture.md names #50 in five places. The WatchStateRepository row in the repositories table says "#50, resume and mark as watched", and now names its users, WatchProgress and PlayerViewModel. The video screen's and the mini player's "until #50 stores positions" become what this change does. The intent filter paragraph says playback resumption "would need a receiver, which #50's stored positions could bring": this change stores positions but adds no playback resumption, because the player spec rules out a headset starting playback after the process ended, so the line says that. docs/architecture.md also says, in `:app`'s module row, that its androidTest sees only `:core:youtube`; in the DatabaseModule bullet, that `:app`'s emulator tests don't touch the database; and in the process's memory list, that no video ID stays "after the mini player closes it". Those three change too. The database section says "#50 decides whether a time from a video's details should win over a refresh": this change saves no video details, so the line hands the question to the first task that saves a played video's details, Phase 6's manual downloads.

### No new ADR

Nothing here is hard to reverse. The rule is a constant in one function, and no schema changes.

## Risks / Trade-offs

- [A process that ends without warning loses up to 30 seconds of position] → Every pause and stop saves exactly. The foreground service rarely ends while it plays.
- [Resuming at a position a few seconds into a sentence] → Accepted for now. A later change can add a short rewind.
- [A hand mark on a video with no watch state stores position zero; if the process ends before the next save, the waiting screen waits at zero, not the screen's own position] → Rare: it needs a mark within 30 seconds of the process ending. Accepted.
- [The chip could wait on the database] → Room's Flow answers before the details usually load, and the chip shows only once both are there.
- [#47's channel page also changes PlayerScreen and PlayerRoute] → The chip sits between the publish line and the channel box, away from the chevron #47 adds. Whichever merges second rebases.
- [A seek from 50 to 95 percent crosses 90 percent, so playback marks the video watched, and from Phase 7 on the next night's cleanup would delete it] → This follows docs/vision.md and ADR 0005. Phase 7 decides whether a seek should count. A hand check covers it.
- [The save before `onSetMediaItems` runs while the old video still plays, and the new video's links take a moment to resolve] → It can miss the old video's last few seconds. Accepted.
- [A user who seeks back from 95 percent to rewatch a part and pauses] → The pause saves that position, and the video stays watched, which is what "Watching again" asks.
- [The database can't be read or written, such as on a full disk] → Playback goes on, the resume starts from the start, and one constant-text line is logged, as "A failed read or write" says.

## Migration Plan

Nothing to migrate. Positions saved before this change don't exist, because nothing wrote them.
