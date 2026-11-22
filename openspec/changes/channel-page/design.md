# Design

## Context

See proposal.md for why. What this change builds on, as it stood before the refactor pull request #228 moved the shared pieces:

- **The YouTube layer.** `ChannelUploadsRemoteDataSource.latestUploads(channelId)` returns `ChannelUploads`: the `Channel` (ID, name, avatars, description) and its latest long-form videos and live streams, never Shorts, newest first with upcoming videos first ([A channel's latest uploads](../../../docs/architecture.md#a-channels-latest-uploads)). The channel's ID can differ from the one asked for, because YouTube sends some channels on to another. A closed channel fails with CHANNEL_CLOSED. Nothing in `:core:data` calls it yet.
- **The tables (#48).** #48's pull request, #226, merged as 6fec5be, added DewfallDatabase with `channels`, one row per subscribed channel, and `videos` and `watch_state`. It puts SubscriptionsRepository in `:core:data`'s `subscriptions` package, with `subscribedChannels()`, `subscribedChannel(channelId): Flow<SubscribedChannel?>`, `isSubscribed(channelId)`, `subscribe(channel: Channel)`, which does nothing when the user is already subscribed, and `unsubscribe(channelId)`. Unsubscribing deletes only the `channels` row. Videos and watch state stay. This change uses SubscriptionsRepository only, and adds no table.
- **Search YouTube's row.** `VideoRowItem` in `:feature:youtube-search-ui` draws a thumbnail box with the length tag, the title, the channel's name and the time ago. `LengthLabel` and `lengthLabel()` live in `:feature:youtube-search-logic`, with their tests. The text of lengths and times ago, with plurals, is in that module's strings.
- **The video screen's channel.** `:feature:player-logic` has `initialOf(name)` and `offersTryAgain(error)`. `PlayerScreen`'s private `Channel` composable draws the emblem and the name, and opens nothing. `VideoPage` holds the name and initial, not the channel's ID. `VideoDetails` has the ID.
- **Navigation.** `:app`'s back stack holds SearchKey, then at most one PlayerKey, which is always on top. `openVideo` adds a PlayerKey only while SearchKey is on top. `close(key)` removes that key. The mini player shows while no PlayerKey is on top, and opens its video with `openVideo`.
- **Module rules.** Feature modules never depend on each other. docs/architecture.md says `:core:ui` "gets created when two features first need such a piece", such as "the video row that the inbox shares with search". Counting that needs no text stays in `:core:model`.

## Goals / Non-Goals

**Goals:**

- The requirements in specs/subscriptions/spec.md and the changed ones in specs/player/spec.md, on Android 10 to 17, in light, dark and dynamic color, at a font scale of 2, and on phones, tablets and split screen.
- One copy of the video row and the channel emblem, shared by search, the video screen, the channel page and later the inbox.
- Subscriptions that the inbox (#49) and the Channels screen (#115) can read without the channel page.

**Non-Goals:**

- Channel results in Search YouTube, with their compact Subscribe buttons. #40's archived proposal plans them for Phase 5 (openspec/changes/archive/2026-11-19-youtube-search/proposal.md, "Phase 4 leaves out three parts of the design" and Capabilities), and they change another screen, so they get an issue of their own, which the maintainer approved.
- "Subscribed" under the channel on the video screen, as the "Player" frame shows it. #41's archived proposal says it comes with Phase 5 (openspec/changes/archive/2026-11-19-video-screen/proposal.md). The maintainer chose not to make it a Phase 5 task.
- Storing the channel's videos. Which videos the inbox shows, and how far back, is #49's and #52's decision (docs/design/brief.md's open questions, docs/architecture.md's "A channel's latest uploads"). So the page writes only the subscription.
- The design's download buttons, downloaded, watched and subtitle marks, and the overflow menu. Phases 6 to 8 bring what they show. The top row shows only the back arrow, with no overflow button.
- Avatar and thumbnail images (#176).
- The navigation bar (#49, #115).
- More than the first page of uploads. The page shows what the layer returns.

## Decisions

### The maintainer's answers

Asked in this session's question dialog on November 22, 2026. The maintainer then approved the whole OpenSpec change in the same dialog, picking "Approve (Recommended)" for a summary of what users would see, the inbox words, the minimized video, the folded description and the chevron included.

- **The card's words.** "Inbox wording": the card and the dialog speak of the inbox until Phase 7's overnight downloads, as Strings lists.
- **Opening the channel from a video.** "Goes to the mini player": the video keeps playing in the mini player, and Back from the channel page goes to the screen before the video.
- **The description.** "3 lines, then More".
- **Follow-up issues.** "Channels in search results" gets its own Phase 5 issue, #222, on the roadmap and the board. "Subscribed" on the video screen stays out of Phase 5. In a second round of questions, the maintainer asked for an issue in After release, so it isn't forgotten: #225.
- **Backups and phone transfers.** "Yes, now with #48": rules that keep the app's database out of Android's cloud backup and device-to-device transfer come with #48, before the first subscription is stored, instead of with #68 in Phase 7. The manifest has `allowBackup="false"`, but Android's [Auto Backup page](https://developer.android.com/identity/data/autobackup) says that on some manufacturers' devices it doesn't stop device-to-device transfers for apps that target Android 12 and later (checked November 22, 2026). `allowBackup="false"` stays, for Android 10 and 11, where data extraction rules don't apply. #47 relayed the answer to #48's session. #48 added them in its pull request, #226, in commit 6ea5b3e.
- **Merge order.** "Fine, no release mid-phase": the channel page may merge before the inbox (#49), so nightly builds may name an inbox that isn't there yet for a few days.
- **The card's words later.** A comment on #60 asks Phase 7's change to give the card and the dialog the design's overnight words back.

### Two pull requests

The shared pieces move first, in a refactor pull request that says "Part of #47.". It changes nothing that users see, so it has no OpenSpec change, and the screenshot tests of search and the video screen must pass with their reference images unchanged. It can merge while #48 is still open. The channel page follows in the pull request that closes #47, after #48 merges.

### What the refactor moves

| Piece | From | To | Why there |
|---|---|---|---|
| `LengthLabel`, `lengthLabel()` and their tests | `:feature:youtube-search-logic` | `:core:model` | Counting with no text, like TimeAgo |
| `initialOf()` and its tests | `:feature:player-logic` | `:core:model` | The same |
| `offersTryAgain()` and its tests | `:feature:player-logic` | `:core:model`, as the property `YouTubeError.offersTryAgain`, like PlaybackFailure's | Both pages decide "Try again" by one rule: NETWORK, BLOCKED and BROKEN. Its KDoc changes from "about this one video" to "about this one video or channel", and says that it is a rule for screens, kept in `:core:model` as the one plain Kotlin/JVM module both pages see |
| `VideoRowItem`, search's private `containerShape` as `rowGroupShape`, `lengthText`, `spokenLength`, `timeAgoText` and their strings | `:feature:youtube-search-ui` | new `:core:ui` | Needs string resources and plurals. It takes plain values, with `channelName: String?`, so the channel page leaves the name out |
| The emblem | `PlayerScreen` | `:core:designsystem`, as `ChannelEmblem(initial, size)` | Takes plain values, knows nothing about Dewfall's data |

`:core:ui` is an Android library with Compose, which depends on `:core:designsystem` and `:core:model`. Its strings are prefixed `core_ui_`. The search screen's `VideoRow` state class stays in its logic module, and its UI passes the row's fields to the shared composable. The player's publish line keeps its own strings, which say "Published 7 hours ago" in full words.

### The channel page's modules

`:feature:channel-page-logic`, plain Kotlin/JVM, package io.github.dewfallapp.feature.channelpage.logic, holds the state, intents and reducer. `:feature:channel-page-ui`, an Android library, package io.github.dewfallapp.feature.channelpage.ui, holds `ChannelRoute`, the screen, its previews and screenshot tests, `ChannelViewModel` and the strings. #115's Channels screen, the list of subscribed channels, is another feature with a pair of its own, which #115's change names, such as `:feature:channels-logic` and `:feature:channels-ui`. "channel-page" names the page itself, so the two don't get confused, and both last segments stay unique.

### Repositories

- `ChannelUploadsRepository` in `:core:data`'s new `channel` package, `fun interface` with `latestUploads(channelId)`, returns the layer's result unchanged, as the search and video details repositories do. Its Hilt module binds it.
- SubscriptionsRepository comes from #48. If its names change before it merges, this change follows them.
- **Writes that outlive the page.** A tap on Subscribe or Unsubscribe followed at once by the back gesture must still finish its write. Android's [coroutines best practices](https://developer.android.com/kotlin/coroutines/coroutines-best-practices), in "Creating coroutines in the business and data layer", put such work in the data layer, in an injected app-wide `CoroutineScope`, with `externalScope.launch { ... }.join()`, as their `bookmarkArticle` example does (checked November 22, 2026). #48 built SubscriptionsRepository's writes that way, in its `@ApplicationScope` scope, with `async { ... }.await()`, which also hands a failure back to a caller that still waits, and with a test that a write finishes when the caller is cancelled. The ViewModel calls `subscribe` and `unsubscribe` from `viewModelScope`.

### State

```kotlin
data class ChannelState(
    /** The ID the page was opened with. */
    val openedId: ChannelId,
    /** The ID YouTube answered for, when a load gave another one than [openedId]. */
    val answeredId: ChannelId? = null,
    val load: LoadArea,              // Loading(number) | Loaded(number, rows) | Failed(number, error)
    /** What the database said for each of the page's IDs: a missing key is not read yet, null is not subscribed. */
    val reads: Map<ChannelId, Channel?> = emptyMap(),
    /** The channel the page shows: the loaded one, else the stored one, kept after an unsubscribe. */
    val shownChannel: Channel? = null,
    val descriptionExpanded: Boolean = false,
    val confirmingUnsubscribe: Boolean = false,
) : MviState
```

The page's IDs are `openedId` and, after a redirect, `answeredId`. `subscription`, a function of the state, is Unknown while any of the page's IDs has no read, Subscribed with the stored channel when any read holds one, the answered ID's first, and NotSubscribed otherwise. So a user subscribed under an old ID that YouTube now sends on still sees "Subscribed", and when a redirect adds `answeredId`, the card hides until its read arrives, so it never shows one ID's state as the other's.

`shownChannel` is set by a successful load, and, after a load failed, by the stored channel that a `SubscriptionRead` holds, whichever comes last. While the channel loads, the page shows no channel and no card, as the spec's "Before the page knows" asks. Nothing clears it, so after an offline unsubscribe the header and card stay, and Subscribe still has a channel to keep. The card shows when `shownChannel` isn't null and `subscription` isn't Unknown. Rows are `UploadRow(id, title, duration, kind, published)`.

Intents: `LoadFinished(number, result)`, `TryAgain`, `SubscriptionRead(channelId, stored: Channel?)`, `Subscribe`, `UnsubscribeTapped`, `UnsubscribeConfirmed`, `UnsubscribeCancelled`, `DescriptionToggled`. The screen has no effects. Opening a video and going back start in the UI and change no state, so they go straight to `:app`, as AGENTS.md says.

- `LoadFinished` counts only for the running load number. A success whose channel ID differs from `openedId` sets `answeredId`. `SubscriptionRead` for an ID that isn't one of the page's is ignored.
- `Subscribe` changes no state. The ViewModel subscribes with `shownChannel`. The card changes when the database answers, through `SubscriptionRead`, so the card shows what is kept, never a guess.
- `UnsubscribeTapped` opens the dialog. `UnsubscribeConfirmed` and `UnsubscribeCancelled` close it.
- **What screen readers hear.** The card's status line is one node whose content description is "Subscribed to %1$s" or "Not subscribed to %1$s", followed by the line under it, such as "New videos show in your inbox.", with a polite live region, as the SubscriptionCard README asks. It is one lasting node in both states, whose text and content description change, not two `if` branches: a new node would be a new live region, and TalkBack may say nothing after the tap. The card's first appearance and a rotation make a new node, which TalkBack shouldn't read as a change. When the state changes, the text changes and TalkBack says it. No event is needed, so nothing waits in state or as an effect to be said again after a rotation. Android 16 deprecates `announceForAccessibility` and names Compose's `liveRegion` as the way "to inform the user of changes to critical UI" ([Android 16 behavior changes](https://developer.android.com/about/versions/16/behavior-changes-all#deprecating-disruptive-accessibility-announcements), checked November 22, 2026).

### The ViewModel

`ChannelViewModel` extends MviViewModel and takes the channel's ID through Hilt's assisted injection, with `ChannelArgs`, as PlayerViewModel takes PlayerArgs.

- It starts load 1 when it is made, and a new load for `TryAgain` when the reducer made a new number, as the search ViewModel does.
- It reads `subscribedChannel(id)` for each of the page's IDs, with `distinctUntilChanged` on the set of IDs and `flatMapLatest` over a merge of their flows, so a redirect adds the new ID, and dispatches `SubscriptionRead(id, stored)` for each answer.
- `Subscribe` calls `subscribe(shownChannel)`, whose ID is the answered one after a load. `UnsubscribeConfirmed` calls `unsubscribe(id)` for every page ID whose read holds a channel. Both run from `viewModelScope`. The repository makes them finish after the page closes, as Repositories says.
- It takes no dispatcher. The repositories are main-safe.

### Navigation

- `ChannelKey(channelId, entryId)` is a `@Serializable` NavKey like PlayerKey. `ChannelIdSerializer` saves the ID's text and restores it through `ChannelId`'s constructor. `toString` hides the ID. `entryId` is a random UUID's text and the entry's content key.
- `openVideo(videoId)` now adds a PlayerKey whenever the top key isn't a PlayerKey. Search rows, channel rows and the mini player use it. A second tap finds a PlayerKey on top and adds nothing, as before.
- `openChannel(from: PlayerKey, channelId)` does nothing unless `from` is on top. It removes `from`, as minimize does, and adds a ChannelKey, unless the key now on top is a ChannelKey for the same ID. A ChannelKey keeps the ID it was opened with. For a channel that YouTube sends on to another, a video from its page carries the answered ID, so tapping its channel opens a second page, for the channel the first page already shows. The player spec names only the same ID, and the case is rare enough to leave. So the back stack never holds two PlayerKeys, and a PlayerKey is always on top when there is one. A second tap finds `from` gone and does nothing.
- The channel page's back arrow calls `close(key)` with its own key, which `close` now takes as any NavKey, so a double tap goes back once.
- `PlayerRoute(videoId, onMinimize, onOpenChannel)`. `VideoPage` gains `channelId`, from `VideoDetails`. The channel box gets a click whose label is "Open" and the channel's name, as the "Player" frame's "Open Larkspur Lectures" (Player-light.dc.html). The frame puts that label on a 48dp control with a chevron_right glyph at the row's end. The plan puts the click on the whole channel box, a larger target, and draws the frame's chevron at its end as decoration, hidden from screen readers, so the box shows that it opens something. The video screen's reference images change for the chevron, and are recorded again in the channel page's pull request.

Alternative: keep the video screen under the channel page. The back stack could then hold two video screens for different videos, and the lower one would come back showing a playback that another video replaced. Rejected.

### The screen

- A LazyColumn in one column at most ReadableWidth wide, padded with `WindowInsets.safeDrawing`, after the "Channel page" frames: a top row with the back arrow, the header with a 64dp `ChannelEmblem` that grows with the font scale, the name in `headlineSmall` as a heading, the description in `bodyMedium`, then the SubscriptionCard as the design system's README describes, a "Latest uploads" heading, then a group on `surfaceContainerLow` with the rows.
- The description folds with `maxLines = 3` while `descriptionExpanded` is false. The "More" button shows when the folded text overflows, as its text layout reports, or while it is expanded.
- The unsubscribe dialog is Material 3's AlertDialog, shown while `confirmingUnsubscribe` is true.
- New glyphs from Material Symbols Rounded, under the Apache License 2.0 like the others in `:core:designsystem`: arrow_back, check, add and chevron_right.
- Loading shows a progress indicator with "Loading channel" for screen readers. A failure shows the design system's Banner with the message and "Try again" when `offersTryAgain`.

### Strings

Card: "Subscribed", "New videos show in your inbox.", "Unsubscribe", "Not subscribed", "Subscribe to see this channel's new videos in your inbox.", "Subscribe". Dialog: "Unsubscribe from %1$s?", "Its new videos stop showing in your inbox.", "Cancel", "Unsubscribe". Card status for screen readers: "Subscribed to %1$s", "Not subscribed to %1$s". Failures: "YouTube can't be reached. Check your connection.", "YouTube isn't loading right now.", "This channel was closed.", "This channel isn't available.", "Try again". Empty: "No videos to show", "This channel has no videos to show." Others: "Back", "Latest uploads", "More", "Less", "Open video", "Open %1$s", "Loading channel".

The design's card says "New videos download overnight while your phone charges." Overnight downloads come in Phase 7, so until then the card speaks of the inbox, and Phase 7's change gives it the design's words back. The dialog drops "Videos you've already downloaded stay until cleanup." for the same reason.

### Tests

- Reducer tests for every intent: load numbers, a stale answer, a redirect, SubscriptionRead for another ID, the card's conditions, a redirect that hides the card until the new ID is read, a subscription under the old ID that shows as Subscribed and is the one Unsubscribe removes, the stored channel on failure, `shownChannel` kept after unsubscribing offline and used to subscribe again, the dialog, the description, and Try again only after a failure that offers it.
- ViewModel tests with fake repositories and `Dispatchers.setMain`: one load at start and none after a configuration change, Try again loads once, a redirect adds a subscription read for the new ID and keeps the old one, Subscribe calls subscribe with the shown channel, offline too, and Unsubscribe calls unsubscribe only after the dialog, for each ID the user is subscribed under. The repository's test, in #48 or here, checks that a write finishes when its caller is cancelled.
- `:core:data` test for ChannelUploadsRepository against a fake data source.
- `:app` BackStack tests for the new `openVideo`, `openChannel` and `close`, and NavKeys tests for ChannelKey's saved form and hidden toString.
- Screenshot tests in light, dark, font scale 2, a tablet, and dynamic light and dark: not subscribed with uploads, subscribed, the dialog, the expanded description, loading, empty, a failure, and a subscribed channel offline.
- An emulator test, ChannelSubscribeTest, next to SearchThenPlayTest and with its fakes: search, open a video, tap its channel, see the channel page and the mini player, tap Subscribe and see "Subscribed", go back, open the channel again and still see "Subscribed". It covers "the main flows" that docs/testing.md asks of slow tests, with the real database. Restarting the app isn't part of it, because the hand check covers that.
- Player reducer tests for VideoPage's channel ID, and the video screen's screenshots recorded again with the chevron, each new image looked at.

## Risks / Trade-offs

- **A write that fails.** A write to the database can fail when SQLite does, such as on a full disk. #48's `await()` hands the failure back to the ViewModel's coroutine while the page is open. The ViewModel doesn't catch it, so the app crashes, as it does for any other database failure. The app-wide scope uses a SupervisorJob, so one failed write doesn't stop later ones.
- **Stored names go stale.** Loading the page writes nothing, and `subscribe` does nothing when the user is already subscribed, so a subscribed channel's stored name, description and avatar links change only when #52's refresh saves its uploads, which refreshes the channel's row in #48's design. Until then the offline page may show an old name.
- **Live regions.** Android asks that live regions "be used sparingly as they may generate announcements every time a View is updated" ([Android 16 behavior changes](https://developer.android.com/about/versions/16/behavior-changes-all#deprecating-disruptive-accessibility-announcements)). The card has one, on a line that changes only when the user subscribes or unsubscribes. TalkBack may also read a live region when the card first appears or after a rotation. No unit test can hear TalkBack, so the hand check listens on Android 10 and the newest Android: once after each tap, and not again after a rotation. If TalkBack also reads the line when the card first appears or after a rotation, the pull request says so with what was heard, and asks the maintainer whether to accept it or to loosen the spec's "once".
