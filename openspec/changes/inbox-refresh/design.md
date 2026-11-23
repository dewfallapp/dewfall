# Design

## Context

See proposal.md for why. What this change builds on, all on main at efd858c:

- ChannelUploadsRepository (`:core:data`) returns `YouTubeResult<ChannelUploads>` for one channel ID. The uploads' channel ID can differ from the one asked for, after a redirect. Most channels take two requests, as docs/architecture.md's request table says.
- The YouTube layer runs every NewPipeExtractor call under one process-wide Mutex ("One call at a time" in docs/architecture.md), so two channel reads never run in parallel, whoever starts them.
- StoredVideosRepository's `saveUploads(uploads)` merges the uploads into `videos` and refreshes the details of the channel row with the uploads' ID, if it is subscribed. It never subscribes. Every write runs in the `@ApplicationScope` scope.
- SubscriptionsRepository's `subscribedChannels()` is a Flow of every subscribed channel, by name. ChannelDao is an interface. The `channels` table holds one row per subscription, with `subscribed_at`.
- YouTubeError has three categories that mean nothing from YouTube loads now (NETWORK, BLOCKED, BROKEN) and six that concern one video or channel.
- InboxViewModel reads the subscription count and the inbox's videos only while its state has a collector, through `SharingStarted.WhileSubscribed(5_000)` over MviViewModel's `stateSubscriptionCount`. InboxReducer has no effects. InboxScreen's top bar is a private Row with the title column and the search IconButton.
- `:core:designsystem` has Banner (glyph, message, optional text action, polite live region) and the error glyph.
- The design system's TopAppBar puts the sync button after search on the inbox only, turns its glyph once per 1.6 s, linear, while syncing, with "Checking for new videos" under "Dewfall", skips the turn when the system's animation scale is 0, and blocks taps while a sync runs. Its Banner component gives the offline and YouTube kinds.

## Goals / Non-Goals

**Goals:**

- One check in the data layer that any caller can start, which runs apart from any screen, so Phase 7's overnight job can run the same code.
- The inbox shows the check's progress and its failure, and nothing when it worked.
- A subscription under a moved channel's old ID ends up under the new ID.

**Non-Goals:**

- Running the check on a schedule, in WorkManager, or after a Takeout import. Phase 7 adds the overnight run, and the import's own change decides whether it starts a check.
- Showing which channels failed. The maintainer chose to say nothing (answer 3).
- A count of checked channels, or a progress bar. The design's words are "Checking for new videos".
- Moving a subscription when the channel page loads a moved channel. The page keeps its save under the answered ID, and the next check moves the subscription.
- Keeping the check's state across process death.

## Decisions

### UploadsChecker lives in `:core:data`, as an app-wide singleton

`:core:data` gets `interface UploadsChecker { val state: StateFlow<UploadsCheck>; fun start() }` and its implementation, bound `@Singleton` by Hilt. `start()` returns at once. If no check runs, it launches one in the `@ApplicationScope` scope, the same scope the repositories' writes use, as Google's [coroutines best practices](https://developer.android.com/kotlin/coroutines/coroutines-best-practices#create-coroutines-data-layer) show for work "not bound to a particular screen". So the check goes on when the user leaves the inbox, and AGENTS.md's rule that sync logic doesn't depend on a screen holds. The singleton's StateFlow is how every screen sees the same check: the inbox shows a running check when the user comes back. `start()` claims the check before it launches anything, with `compareAndSet` on the state's MutableStateFlow from not running to running, so a second `start()` while one runs finds it taken and does nothing, and only the coroutine that claimed it resets it. The reset is in the launched job's completion handler, which runs however the check ended, cancellation included, so the button always comes back.

The loop itself is a `suspend fun check()` inside the class, which `start()` launches. Phase 7's worker can call it through a second method on the interface when it comes. This change adds only what the inbox uses.

Alternatives:
- The loop in InboxViewModel. Rejected: leaving the inbox, or the ViewModel's end, would cancel it, and the background job couldn't reuse it.
- A WorkManager one-time request now. Rejected for now. Expedited work suits a task the user starts, and its quotas apply only while the app is in the background ([Define work requests, expedited work](https://developer.android.com/develop/background-work/background-tasks/persistent/getting-started/define-work#expedited), checked on November 24, 2026), so it would work. But the background constraints and the worker belong to Phase 7, which will call the same check. It would also mean a second place to read progress from, WorkInfo, for no gain while the app is open.

### One channel after another, in the order of `subscribedChannels()`

The check takes one snapshot of `subscribedChannels().first()` when it starts, and asks for each channel in turn. Parallel requests would only queue on the YouTube layer's Mutex, and one at a time is the gentlest load on YouTube. Because kotlinx.coroutines' Mutex is fair, as its `lock` KDoc says, "suspended callers are resumed in first-in-first-out order" ([Mutex.kt](https://github.com/Kotlin/kotlinx.coroutines/blob/master/kotlinx-coroutines-core/common/src/sync/Mutex.kt), checked on November 24, 2026), a search or a channel page that the user opens during a check waits for at most the one channel read that holds it.

If two subscriptions are answered under the same ID, such as a moved channel subscribed under both IDs, the check keeps a set of the IDs YouTube answered for, and skips a later snapshot entry whose ID is already in it. That saves the second request only when the entry under the new ID comes after the old one by name. When the new ID comes first, the old ID's request still reaches the same channel once more, and its answer is saved again, which changes nothing.

The snapshot is ordered by name, so a check that stops early always covers the same first channels. A "least recently checked first" order needs a column that nothing else needs yet, so it waits for Phase 7, which can add it while the database is still at version 1 or with a migration.

### What each failure does

| YouTubeError | What the check does | Failure it ends with |
|---|---|---|
| NETWORK | Stops at once | CANT_REACH_YOUTUBE |
| BLOCKED | Stops at once | YOUTUBE_NOT_LOADING |
| BROKEN | Skips the channel, goes on, but stops after 3 BROKEN answers in a row | YOUTUBE_NOT_LOADING if it stopped, or if no channel was read, else none |
| PRIVATE, AGE_RESTRICTED, COUNTRY_BLOCKED, MEMBERS_OR_PAID, CHANNEL_CLOSED, UNAVAILABLE | Skips the channel, goes on | none |

NETWORK and BLOCKED stop the check because every later request would fail the same way, and BLOCKED gets worse with more requests. BROKEN doesn't stop it, because the layer can fail to read one odd channel while the others work, and a check that always stopped at the same broken channel would never reach the ones after it in the name order. When YouTube changes its pages, every channel answers BROKEN, and a check over 700 channels would send about 1,400 requests that all fail. So 3 BROKEN answers in a row stop the check, as a sign that YouTube changed, and so does a check where BROKEN is the only answer any channel got, such as with one subscription. Both end with the YouTube banner. Three odd channels in a row in the name order are unlikely, and the next check tries them again. The table lives in one function of plain inputs, with a JVM test per row.

### A failed read or write on the phone stops the check quietly

The check reads `subscribedChannels().first()` and writes through `moveSubscription` and `saveUploads`, and any of them can throw, such as on a full disk or a damaged database. The `@ApplicationScope` scope has a SupervisorJob and no exception handler, so an exception that left the check would reach the thread's default handler and crash the app. So the check's coroutine gets a CoroutineExceptionHandler, which takes any exception but a cancellation, on purpose broader than Google's coroutines guidance usually likes, because Room and SQLite throw several unrelated types for a full disk or a damaged file, and the class's KDoc says so. A child of the scope's SupervisorJob handles its own exception with it. The check then stops, sends no more requests, logs "Uploads check failed on the phone" with the exception's class, as the logger's error field allows, and ends with no failure, so no banner shows. A write that failed once would most likely fail for the next channel too. The inbox needs no message of its own: if its reads fail too, it already says "Your inbox can't be shown". This is the channel page's rule on main, whose `writeFailed` handler keeps a failed save from crashing the app and changes nothing on screen. UploadsCheckerTest covers a throwing read, a throwing move and a throwing save, each ending with `running` false, no failure and no further request.

The two failures are a Dewfall enum in `:core:model`, UploadsCheckFailure, because the inbox's reducer, in a plain Kotlin/JVM module, reads it. The check's state is `data class UploadsCheck(val running: Boolean = false, val failure: UploadsCheckFailure? = null)` in `:core:model`. Starting a check sets `running` and clears `failure`. Ending sets both.

### A moved channel's subscription moves in one transaction

When the uploads' channel ID differs from the one asked for, the check calls `SubscriptionsRepository.moveSubscription(from, to)`, then saves the uploads. ChannelDao gains a `@Transaction` function with a body: it reads the old row, and if there is one, inserts a copy under the new ID with the new name, avatars and description and the old `subscribed_at`, ignoring a conflict, then deletes the old row. So:

- A subscription under the old ID moves with its time of subscribing, and the inbox's rule for new videos counts from that time.
- If the new ID is already subscribed, the insert does nothing, the new row keeps its own time, and the old row goes.
- If the user unsubscribed from the old ID during the check, there is no old row, nothing is inserted, and the following save doesn't subscribe, so the user's choice stays.

The move runs first so the save then finds the new row subscribed and refreshes its details, as for any channel. The same transaction moves the videos saved under the old ID to the new ID, so a started video that YouTube's latest uploads no longer list stays in the inbox, as the maintainer chose after CodeRabbit's review (answer 7). Their watch state stays, because it is keyed by video ID. A video that YouTube now lists under the new ID gets its new `channel_id` from the save, as VideoMerge already does.

Room 3's [Transaction](https://developer.android.com/reference/kotlin/androidx/room3/Transaction) reference, checked on November 24, 2026, documents `@Transaction` only on "a non-abstract function of an abstract Dao class", and says nothing about an interface function with a body. ChannelDao is an interface, so whether Room 3's KSP processor accepts it there is unknown. Task 1.1 tries it before writing anything else. Room 3.0.3's compiler accepted it, and ChannelDaoTest's move cases pass on Android 10, so the fallback wasn't needed. Had Room 3 rejected it, the repository would have run the three DAO calls inside the database's write transaction, `withWriteTransaction`, which the [Room 2 to 3 migration guide](https://developer.android.com/training/data-storage/room/migration-2-to-3) names, instead. Turning ChannelDao into an abstract class would also work, but #51 and #115 add functions to the interface at the same time. The repository runs the move in the `@ApplicationScope` scope like its other writes.

### The inbox reads the check's state with its other reads

InboxState gains `check: UploadsCheck = UploadsCheck()`. InboxIntent gains:

- `CheckForNewVideos`, which the button and "Try again" dispatch. The reducer leaves the state alone, and `runSideEffects` calls `checker.start()`. It is an intent, not a direct call, because it starts business logic, as AGENTS.md's MVI rule asks.
- `CheckChanged(check)`, a result of the ViewModel's read of `checker.state`. The reducer copies it into the state.

The ViewModel merges `checker.state.map(::CheckChanged)` with the existing reads inside the same `WhileSubscribed` switch, so it reads the check only while the inbox shows, and gets the current value at once when it shows again. The `catch` that turns a failed read into Unreadable stays on the database reads only, because the checker's StateFlow never throws.

What the screen shows follows from the state, in small properties on InboxState that the reducer test covers:

- The button shows when `content` is Videos or CaughtUp.
- The line under "Dewfall" reads "Checking for new videos" when `check.running`, else "Inbox".
- The banner shows when the button shows, `check.running` is false and `check.failure` isn't null.

InboxState is not saved for process death, as today, so a check leaves nothing in the saved state.

### The top bar and the banner

The top bar gets an IconButton with the sync glyph after search, `enabled = !check.running`, with the content description "Check for new videos". While running, the glyph turns through `rememberInfiniteTransition`, 1,600 ms per turn, linear, as the design system says. The screen reads `Settings.Global.ANIMATOR_DURATION_SCALE` once per check and skips the rotation when it is 0, because the design system asks for the turn to be skipped then, and an explicit check doesn't depend on how Compose treats infinite animations at that scale. The subtitle Text gets a polite live region, so a screen reader hears "Checking for new videos" when a check starts and "Inbox" when it ends.

The banner is `:core:designsystem`'s Banner, as the first item of the list for Videos and above the message for CaughtUp, inside the readable width. Its glyph is wifi_off in onSurfaceVariant for CANT_REACH_YOUTUBE and error in the error color for YOUTUBE_NOT_LOADING, as the design system's Banner gives them. Banner requires a glyph description, which the strings give as "No connection" and "Error", and its action is "Try again".

### The glyphs

`:core:designsystem` gets sync and wifi_off from Material Symbols Rounded, weight 400, grade 0, optical size 24, outlined, at the same commit as the other icons, 10ecc14790, with the same header and license note. The sync glyph is the one the design's Inbox frame draws.

### Logging

UploadsChecker takes a Logger. It logs one line when a check starts, with the count of channels, and one when it ends, with counts of channels saved, moved and skipped, and the failure's name when it stopped early, through LogField.count and LogField.state. It never logs a channel's ID or name, or a video. The YouTube layer already logs each call's category, as today. Dewfall's lint checks enforce the constant text.

### Tests

- UploadsCheckerTest, on the JVM in `:core:data`, with fake repositories and a TestScope's background scope as the application scope: each row of the failure table, 3 BROKEN in a row stopping, BROKEN apart from each other not stopping, a throwing read, move and save each stopping with no failure and `running` false, the order of requests and that none follows NETWORK or BLOCKED, the save after a move, the skip of an already answered ID, one check at a time, the state's transitions, a cleared failure on start, a check that finishes after the starter's scope is cancelled, and the log lines' fields with a recording LogWriter.
- ChannelDao's DAO tests, on an emulator: the move with and without a row under the new ID, with no old row, and that the old row's time stays.
- RoomSubscriptionsRepositoryTest: the move maps the channel.
- InboxReducerTest: CheckChanged, CheckForNewVideos leaving the state alone, and the three properties for each content.
- InboxViewModelTest: the button's intent calls `start()`, the check's state reaches the state only while collected, and a read failure still shows Unreadable while the check's state keeps arriving.
- Screenshot tests: a running check, each banner over cards, the YouTube banner over "You're all caught up", in light, dark, large font and dynamic color. Existing references with a top bar change, because the button appears.
- One emulator test, InboxCheckTest: subscribe to the fake channel through SubscriptionsRepository with nothing saved, then with FakeNetwork's YouTube off tap "Check for new videos" and find the connection banner, then with YouTube back tap "Try again" and find the channel's videos in the inbox and the banner gone.

## Risks / Trade-offs

- [A large Takeout import, such as 700 channels, makes a check take many minutes, about two requests per channel] → The user can keep using the app, and a search waits for at most one channel read. Phase 7's overnight run does the regular work. A later change can add a count if hand checks show the wait confuses.
- [A check started near a rate limit may get BLOCKED partway] → It stops at once and keeps what it saved. The next check starts again from the first channel by name, which repeats requests for channels already saved. Accepted until Phase 7 adds a last-checked time.
- [Room 3 may not accept `@Transaction` on an interface function with a body] → Task 1.1 checks it first, with the database's write transaction as the fallback.
- [A moved channel's old videos stay under the old ID, out of the inbox] → What the user watched stays, keyed by video ID. YouTube lists recent videos under the new ID, so the save brings them back under it.
- [The process ends during a check] → The saved channels stay saved, nothing shows the check, and the user can start one again. No state is lost that the user made.

- [A manual check on mobile data with many subscriptions uses data, about two pages of a channel each] → The user starts it by hand and sees it run. The background constraints, unmetered and charging, belong to Phase 7's run, and this check sends no video data. Accepted.

- [A check keeps running when the user leaves the app, on any network, until it ends or Android stops or freezes the process] → The user started it. AGENTS.md's "unmetered and charging" rule is for scheduled background jobs, which Phase 7 adds. Accepted.
- [With one subscription whose answer the app can't read, the check shows "YouTube isn't loading"] → One answer can't tell a broken channel from a YouTube change. The maintainer chose to keep the banner, as proposal.md's answer 6 quotes.

## Migration Plan

None. The schema doesn't change, and the move uses existing columns.
