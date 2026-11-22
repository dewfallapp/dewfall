# Design

## Context

See proposal.md for why. What this change builds on:

- #48's database: `channels` (one row while subscribed, with `subscribed_at`), `videos` (with `channel_id`, `kind`, `published_at`, `first_seen_at`, `listing_number`, `list_position`, an index on (`channel_id`, `published_at`)) and `watch_state` (with `watched_at`, null while not marked watched). No foreign keys. The schema stays at version 1, and this change doesn't touch it.
- VideoDao's `channelVideos` orders one channel's videos by `published_at DESC, listing_number DESC, list_position ASC, id ASC`, which the subscriptions spec's "Saved videos keep YouTube's order" describes, and which leaves videos with no time last on Android 10's SQLite 3.22. No repository exposes it.
- StoredVideosRepository is a `fun interface` with one write, `saveUploads(uploads)`. Its KDoc says reads come with the tasks that need them, such as the inbox. Every write runs in the `@ApplicationScope` scope, and Room's Flows pass through `distinctUntilChanged`.
- Today nothing calls `saveUploads`. #47's channel page (merged as #234) loads a channel's uploads through ChannelUploadsRepository, keeps only rows made from them in its state, with Shorts and repeated videos left out and no thumbnails (task 0.1 found this on main), and subscribes with SubscriptionsRepository, under the channel that YouTube answered for after a redirect.
- #52 is the manual refresh, which fetches every subscribed channel's uploads. Phase 7 adds the overnight runs. #51 imports subscriptions from a Takeout file, with only each channel's ID and name.
- `:app` holds the back stack in BackStack.kt and NavKeys.kt, with SearchKey as its first key. Since #234, openVideo adds a PlayerKey unless a PlayerKey is on top, and close(key) removes any key but SearchKey, so SearchKey always stays and the back stack never empties. #47 adds ChannelKey and `openChannel(from: PlayerKey, channelId)`.
- `:core:ui`'s VideoRowItem is the shared row, with `rowGroupShape` for rounded groups. `:core:designsystem` has ChannelEmblem, ReadableWidth, DewfallSpacing and the icons.
- The design's "Inbox" and "Flow 1 ends: inbox right after import" frames group the videos in one card per channel, with the channel's emblem, name and a count, and end with "That's everything for now." Their download marks, subtitle marks, "Missed last night" and "Next downloads tonight" belong to Phases 6 to 8. The design brief leaves "how far back the first overnight run reaches after an import" to the Phase 5 and Phase 7 changes, and the prototype "queues up to two recent videos per channel".
- The design brief's navigation bar has four destinations. In Phase 5 only the inbox exists, and #115 adds Channels.

## Goals / Non-Goals

**Goals:**

- The inbox as the first screen, reading only the phone, so it works offline and opens with no request.
- One rule for "new" that the database applies, so the inbox, and later the overnight runs that download what it shows, read the same thing.
- A rule that stays finishable: a new subscription adds at most 2 older videos, and marking a video watched never pulls an older one in.

**Non-Goals:**

- Fetching uploads from YouTube. #52 adds the refresh button, its progress and its errors, and fetches every subscribed channel. The design's "Inbox, offline" and "Inbox, YouTube not loading" states belong to it, because only a fetch can fail.
- Downloads, their marks and their banner (Phases 6 and 7), subtitle marks (Phase 8), and "Missed last night".
- The navigation bar (#115 adds it with the second destination), the search sheet with both searches (Phase 8 adds subtitle search, so until then the search button opens Search YouTube), and the menu buttons in the top bar and on each card.
- The "Import from Google Takeout" button of the "No channels yet" state. #51 adds it with its own action on `Inbox(...)`, if it starts the import from there.
- Following a channel that YouTube sends on to another one. #52 decides it, as its issue says.
- A way to dismiss a video without watching it. Marking it watched (#50) does that.

## Decisions

### Videos reach the store through the channel page, and the inbox fetches nothing

The channel page has already loaded a channel's uploads when the user taps Subscribe. Its state keeps only rows, which leave out what a save needs: the thumbnails, the Shorts and YouTube's places. So ChannelViewModel keeps the latest successful load's ChannelUploads whole, as the YouTube layer gave it, in a private field with the load's number, and every save passes that object unchanged. It saves on Subscribe, which counts as that load's save, and once for each load number when that load's answer and a reading of the card as "Subscribed" both hold, whichever comes last, as the subscriptions delta's "The channel page saves the uploads of a subscribed channel" says. Waiting for both matters after a redirect, when the card goes back to unknown until the database answers for the new ID. On Subscribe, the page calls one function in `:core:data`, SubscribeWithUploads, with the channel and the loaded uploads, or none when nothing loaded, such as offline. It runs `subscribe` and then `saveUploads` as one unit in the `@ApplicationScope` scope, as docs/architecture.md's "Writes outlive their caller" does for single writes, after Google's [coroutines best practices](https://developer.android.com/kotlin/coroutines/coroutines-best-practices#create-coroutines-data-layer), whose external scope is for work "not bound to a particular screen". So leaving the page right after the tap doesn't stop the save, and the order lives in the data layer, where any later caller that subscribes with uploads in hand gets it too: every video the save brings has a `first_seen_at` at or after `subscribed_at`, and the first save counts as the first since subscribing, as the rule below needs. Its repository test checks the order and that both writes finish after the caller is cancelled, and ChannelViewModelTest checks that the page calls it, and when. A save, or a subscribe with uploads, that throws changes nothing on the page: the ViewModel runs them with a CoroutineExceptionHandler that does nothing, as PlayerViewModel's marks do on main, because the card shows what the database holds, and a save now runs on every load of a subscribed channel's page. The page saves under the channel ID YouTube answered for, which is the ID #47's Subscribe keeps.

So subscribing from a channel page fills the inbox at once, with no request of its own, and the inbox is useful before #52. A Takeout import, or a Subscribe from #222's channel results, saves no videos until #52's refresh or a visit to the channel's page, and until then the inbox says "You're all caught up" with the number of channels, which is true: nothing new is known.

Alternatives:

- **Fetch when the inbox opens.** That sends requests every time the app opens, over mobile data too, which the vision's people with slow or expensive data don't want, and the app "does not run constantly". It also needs the refresh's progress and error states, which are #52's.
- **Fetch each new subscription's uploads from a process-wide watcher.** It would work for every way of subscribing, but it is #52's fetch under another trigger, and a Takeout import of hundreds of channels would start hundreds of requests at once, outside any screen and before #52 decides how to pace them.
- **Leave the store empty until #52.** Then the inbox shows nothing real until #52 merges, and no hand check could exercise it.

The fetch that #52 and Phase 7 need, a loop over subscribed channels that calls `latestUploads` and `saveUploads`, goes in `:core:data` with #52, where it doesn't depend on any screen.

### The rule for "new" lives in one query

VideoDao gets `inboxVideos(video: VideoKind)`, a Flow, with `VideoKind.VIDEO` passed in, so the enum's stored name isn't written by hand in SQL:

```sql
SELECT v.* FROM videos AS v
JOIN (
  SELECT c.id AS channel_id, c.subscribed_at AS subscribed_at,
    (SELECT l.id FROM videos AS l WHERE l.channel_id = c.id AND l.kind = :video
     ORDER BY l.published_at DESC, l.listing_number DESC, l.list_position ASC, l.id ASC
     LIMIT 1) AS latest,
    (SELECT l.id FROM videos AS l WHERE l.channel_id = c.id AND l.kind = :video
     ORDER BY l.published_at DESC, l.listing_number DESC, l.list_position ASC, l.id ASC
     LIMIT 1 OFFSET 1) AS second,
    (SELECT MIN(f.first_seen_at) FROM videos AS f
     WHERE f.channel_id = c.id AND f.listing_number IS NOT NULL
       AND f.first_seen_at >= c.subscribed_at) AS first_save
  FROM channels AS c
  LIMIT -1
) AS s ON s.channel_id = v.channel_id
LEFT JOIN watch_state AS w ON w.video_id = v.id
WHERE v.kind = :video
  AND w.watched_at IS NULL
  AND (
    v.published_at >= s.subscribed_at
    OR (v.listing_number IS NOT NULL AND v.first_seen_at > s.first_save)
    OR v.id = s.latest
    OR v.id = s.second
    OR (w.position_ms > 0 AND w.updated_at >= s.subscribed_at)
  )
ORDER BY v.published_at DESC, v.listing_number DESC, v.list_position ASC, v.id ASC
```

The subquery `s` works out each subscribed channel's facts once: its time of subscribing, its 2 latest videos, and the time of its first save since subscribing. Its `LIMIT -1` sets no limit, since "if the LIMIT expression evaluates to a negative value, then there is no upper bound on the number of rows returned" ([SELECT](https://www.sqlite.org/lang_select.html#limitoffset), checked on November 22, 2026), but it stops SQLite from flattening `s` into the outer query, because a subquery "does not use LIMIT or the outer query is not a join" is one of the flattener's conditions ([The SQLite Query Optimizer Overview](https://www.sqlite.org/optoverview.html#flattening), checked on November 22, 2026). Flattened, the three scalar subqueries would run again for every video, as SQLite reevaluates a correlated subquery "each time its result is required" ([SQL Language Expressions](https://www.sqlite.org/lang_expr.html), checked on November 23, 2026), and the work would grow with the square of each channel's videos. Not flattened, `s` runs once per channel, each scalar subquery reads one channel's rows through the (`channel_id`, `published_at`) index, and the outer query reads each video once.

- **Marked unwatched.** Marking a video unwatched also sets `updated_at`, so an old video the user had started, with a position past 0, and marks unwatched after subscribing comes into the inbox too. A video marked watched before it was ever played has position 0, so marking it unwatched doesn't bring it in. The inbox spec says so.
- **Started videos** stay: a `watch_state` row with a position past 0, no watched mark, and a last save at or after the time of subscribing means the user started or resumed the video since subscribing and didn't mark it watched. A video started before subscribing, from search or a channel page, doesn't pass this test, so a new subscription still brings at most 2 older videos. The `watched_at IS NULL` test keeps a video marked watched out, whatever its position. The inbox spec's "A started video that is no longer new" covers it.
- **The 2 latest** are counted among the channel's videos of kind VIDEO, watched or not. So marking one watched takes it out and pulls no older video in, and the list can be finished. They are the 2 latest now, not the 2 latest when the user subscribed: a newer video pushes the older of them out, unless the user started it. Keeping the first 2 until watched would need a column that remembers them, which is a schema change. The maintainer chose this rule on November 23, 2026, as proposal.md quotes.
- **Came out after subscribing** has two tests. The publish time catches what YouTube dates exactly, and what a refresh finds soon after. YouTube's relative times are estimates, such as "2 days ago" counted from the time of the save ([A channel's latest uploads](../../../docs/architecture.md#a-channels-latest-uploads)), so a video that came out after subscribing can get an estimate before it. The second test catches that: a video that Dewfall first saved later than the first video it saved for the channel since subscribing is new, whatever its estimate. `first_seen_at` is the same for every video of one save, because `toEntities` takes one `now`, so the videos the first save brings aren't new by it, and a video a later save brings is. Videos saved before the user subscribed, such as during an earlier subscription, have `first_seen_at` before `subscribed_at`, so they don't count. Both sides of this test count only rows a save of a channel's uploads wrote, with a `listing_number`, because the schema leaves `listing_number` null for "a video saved another way", such as a later phase's single download from search, which says nothing about whether the video is new. The test also rests on each save holding only the channel's latest uploads, as ChannelUploads does: a later save that held older pages of a channel would make them count as new, so a change that adds paging to a save changes this rule too. A row first kept another way keeps its `first_seen_at` when a save of uploads later holds it, and gets a `listing_number`, so it would then count for this test with its first time. Nothing keeps rows another way yet, and the later phase that adds such a save must look at this test again. One edge: after subscribing again, a first save that brings no video Dewfall didn't already keep leaves no trace, so the next save's new videos count as the first ones and pass only by the other tests. They are usually the channel's latest, so the 2 latest still show them. The inbox spec says so in "Subscribing again". Keeping the time of the first save since subscribing even when it brings nothing would need a column, a schema change, for a case that a subscription reaches only when Dewfall already keeps the channel's latest uploads, such as after subscribing again, or after a save of a channel the user didn't subscribe to, so this change doesn't add one, as CodeRabbit asked about on #235.
- **Subscribed only** through the JOIN on `channels`. Unsubscribing deletes the row, so the videos leave at once, and subscribing again sets a new `subscribed_at`, so the rule starts over.
- **SQLite 3.22.** Scalar subqueries with ORDER BY, LIMIT and OFFSET, MIN in a scalar subquery, and a subquery in FROM are part of SQLite's syntax with no version note ([SQL Language Expressions](https://www.sqlite.org/lang_expr.html), sections on IN, scalar and correlated subqueries, checked on November 22, 2026). No UPSERT, `NULLS LAST` or window function is used. The DAO tests run on Android 10 in CI, which proves it.
- **Room's Flow** re-emits when `videos`, `channels` or `watch_state` change, because the query reads all three. So a watched mark from #50, a subscription and a save update the inbox on their own.
- **Cost.** Saves never delete a row, so each channel's rows grow with every refresh, and Room reruns the query whenever `videos`, `channels` or `watch_state` changes ([Write asynchronous DAO queries](https://developer.android.com/training/data-storage/room/async-queries), checked on November 22, 2026), which after #50 includes saved positions. So task 1.2 times the query on the Android 10 emulator with a heavy user's data: 300 subscribed channels with 300 saved videos each, 90,000 rows, and a watch state for a tenth of them. The bound is 250 ms for one run, read from the time between the query starting and its rows being read, as the median of 5 runs after one warm-up, and the pull request records the result and the emulator. If it is slower, the query changes before the screen is built, for example by keeping each channel's facts in a table of their own, which would be a schema change and a new plan check. No new index, so the schema stays as it is.
- **Reruns while hidden.** NavDisplay's default SinglePaneSceneStrategy makes a scene of the back stack's last entry alone, and the scene's content is that entry's content ([SinglePaneScene.kt at 1.2.1](https://android.googlesource.com/platform/frameworks/support/+/65822252146d26e872acf03dc11bda43f11efa3b/navigation3/navigation3-ui/src/commonMain/kotlin/androidx/navigation3/scene/SinglePaneScene.kt), checked on November 22, 2026). So while the video screen, Search YouTube or a channel page is on top, the inbox isn't composed, and stops collecting its state. The ViewModel reads the database only while its state has a collector, with a 5 second grace for configuration changes, as below, so a position save while the video screen shows reruns nothing for the inbox. The state keeps the last answer, so the inbox shows it at once on the way back, then the fresh one. While the mini player plays over the inbox, the inbox is shown, so each position save reruns the query once, and task 1.2's bound applies to each run. On main, #50 saves a position every 30 seconds while a video plays, and on pause, a stop, a seek while paused and a change of video (docs/architecture.md, "Saving where a video was left"), so the query runs about twice a minute then.

Alternatives: computing the rule in Kotlin from every saved video would read the whole table on every change. A view or a stored flag would need a schema change.

### The repository returns a flat list, and the reducer groups it

StoredVideosRepository becomes a plain `interface` with `fun inboxVideos(): Flow<List<VideoSummary>>`, which maps each row to VideoSummary in Mappings.kt and passes through `distinctUntilChanged`. VideoSummary already holds what a row and a card need: the ID, title, channel ID and name, length, publish time, thumbnails and kind. Its `published` comes from `published_at` and `published_approximate`.

The reducer groups the list by channel ID, in the order each channel first appears. The list is sorted newest first, so the card with the newest video comes first, and each card keeps the query's order. The card's name is its first video's channel name, which every save refreshes. Grouping is pure, so it lives in `:feature:inbox-logic` with its tests, not in SQL or Compose.

### The inbox's MVI

- **InboxState** holds `content`: null until the first answer, then Videos (a list of InboxGroup, each with the channel ID, name and videos), NoChannels, CaughtUp with the number of subscribed channels, or Unreadable when the database couldn't be read. A null content draws an empty surface for the moment before Room's first answer, with no spinner, because the read is local. The LazyColumn and its rememberLazyListState live only in the Videos branch, so after process death the list is first laid out with its items, and the restored scroll position holds.
- **InboxIntent** has two intents, the results of the ViewModel's work: `Loaded(subscribedCount, videos)`, and `ReadFailed` when either read throws. Taps on a video, a card's channel row and the search buttons change no state, so they go straight to `:app`'s navigation as actions, as ADR 0002 says. The effect type is Nothing.
- **A read that fails.** The inbox is the first screen, so an exception from Room inside viewModelScope would crash the app at every start. The ViewModel catches it on the combined Flow, as PlayerViewModel does for its watch state on main (docs/architecture.md, "The watched chip"), and dispatches ReadFailed, which shows Unreadable: the error glyph, with the content description "Couldn't read the inbox" as AGENTS.md asks of every icon, "Your inbox can't be shown" and "Dewfall couldn't read what it keeps on this phone. Restarting the app may help.", with the top bar's search button still working. The maintainer chose this on November 23, 2026, as proposal.md quotes. The next time the inbox shows after its reads stopped, they start again, and a good answer replaces Unreadable. The inbox has no Logger, as PlayerViewModel has none, and logs nothing about it. InboxReducerTest and InboxViewModelTest, with a fake that throws, cover it.
- **InboxViewModel** reads in viewModelScope, as Android's [coroutines best practices](https://developer.android.com/kotlin/coroutines/coroutines-best-practices) ask of a ViewModel, and stops while nothing shows its state, as the UI layer's [state production](https://developer.android.com/topic/architecture/ui-layer/state-production) guide does with `SharingStarted.WhileSubscribed(5_000)` (both checked on November 22, 2026). MviViewModel holds the state itself, so it gets one protected, read-only addition: `stateSubscriptionCount`, its MutableStateFlow's `subscriptionCount`. InboxViewModel feeds it to `SharingStarted.WhileSubscribed(5_000).command(...)`, and while the command is START it combines `subscriptions.subscribedChannels()`, counted, with `storedVideos.inboxVideos()`, and dispatches Loaded for each pair. The route collects the state with `collectAsStateWithLifecycle`, so the count drops to 0 when the inbox stops or leaves the composition. MviViewModelTest covers the count. It takes no dispatcher. The two Flows come from separate queries, so for a moment they can disagree, such as a count of 0 with the last videos while the user unsubscribes. The reducer lets the videos win: a non-empty list shows the cards whatever the count, an empty list with a count of 0 shows "No channels yet", and an empty list with a count above 0 shows "You're all caught up". The next pair corrects it. InboxReducerTest covers the mismatch.
- **Never its own state.** InboxViewModel never collects its own `state`, as ChannelViewModel does to read its page IDs, because a collector of its own would keep `stateSubscriptionCount` above 0 and the reads would never stop. The test that reading stops 5 seconds after the last collector leaves would catch it.
- **The ViewModel's tests** check that each emission becomes the right state, and that a new emission replaces the last, with fake repositories and Dispatchers.setMain.

### The screen

- **InboxScreen** draws a Surface that fills the window, and inside the safe drawing insets a column at most ReadableWidth wide, centered: the top bar, then a LazyColumn of cards, keyed by channel ID and video ID, then the footer line. Its scroll state is rememberLazyListState, which saves the position across configuration changes and process death, and holds only indexes.
- **The top bar** shows "Dewfall" in titleLarge and "Inbox" in bodyMedium under it, as the design does, and an IconButton with the search icon, whose content description is "Search YouTube" until Phase 8 adds subtitle search.
- **A card** is a surfaceContainerLow box with the large shape, as the design's cards are, holding a channel row (ChannelEmblem with `initialOf(name)`, the name in titleSmall and the number of its videos in bodySmall, from plurals, such as "2 videos", because a started video in the card may not be new), which is one clickable node for screen readers, then a VideoRowItem for each video with `channelName = null`, each clickable.
- **The footer** says "That's everything for now." in bodyMedium, centered, below the last card, as the design's "Flow 1 ends: inbox right after import" frame does.
- **The empty states** follow the design's frames: a round surfaceContainerHigh disc with an icon, the title in headlineSmall, the body in bodyLarge, and for "No channels yet" a filled "Search YouTube" button with the search icon. The icons come from Material Symbols Rounded, subscriptions for "No channels yet", added to `:core:designsystem`'s drawables with the same header as the others, from the same commit of google/material-design-icons, because #115's navigation bar will use subscriptions for Channels too, and done_all for "You're all caught up", which #50 already added for the watched chip. AGENTS.md says every icon has a content description, so the disc's icon gets a short one, "Channels" and "Done", and the empty state's title, body and icon read in order.
- **Text.** Every string is in `:feature:inbox-ui`'s resources, with plurals for the card's count and for the number of channels. The words about downloads overnight wait for Phase 7, as #47's "inbox words" do.
- **Saved state.** rememberLazyListState saves only the first visible item's index and offset. LazyColumn also keeps saved state per item key, a video or channel ID, but when the activity saves its state for process death, SaveableStateHolder drops the entries that hold nothing, with `holder.savedStates.values.removeAll { it.isEmpty() }` ([SaveableStateHolder.kt](https://android.googlesource.com/platform/frameworks/support/+/androidx-main/compose/runtime/runtime-saveable/src/commonMain/kotlin/androidx/compose/runtime/saveable/SaveableStateHolder.kt), androidx-main, checked on November 22, 2026). The cards and rows hold no rememberSaveable state, as Search YouTube's rows don't, so the state Android saves holds no ID, as the inbox spec promises. InboxFromChannelTest checks it: with the test video in the inbox, it asks the activity to save its state into a Bundle, checks that the Bundle holds the saved back stack, by InboxKey's serialized name, so an empty Bundle can't pass, and checks that no value in it, at any depth, holds the test video's ID or title, or its channel's ID or name. A later change that adds saveable state to a row must use keys that hold no ID.
- **The design's words.** The frames say "You've watched everything from your 4 channels.", "That's everything from last night.", "2 of 2 downloaded" and "Their new videos download overnight and wait for you here." This change says "Nothing new from your 4 channels.", "That's everything for now.", "2 videos" and "Their new videos show here.", because there are no downloads until Phase 7, and after a Takeout import "watched everything" would be false. The pull request lists these for the maintainer.
- **Time.** InboxRoute reads the clock and the zone once per content, as the other routes do, so the screen stays a function of its inputs and the previews are fixed.
- **The public composable** is `Inbox(onOpenVideo, onOpenChannel, onOpenSearch, modifier)`.

### Navigation

- **InboxKey**, a `@Serializable data object`, is the back stack's first key: `rememberNavBackStack(InboxKey)`. SearchKey becomes a data class with an `entryId`, a random UUID's text, as PlayerKey and ChannelKey have, so a quick reopen of Search YouTube during its exit animation gets a new entry with a new ViewModel and saved state, and "opening Search YouTube again shows an empty field" holds. NavKeysTest checks its saved form.
- **`openSearch()`** adds SearchKey only while InboxKey is on top, so a double tap opens one screen.
- **Opening a channel from the inbox** adds a ChannelKey only while InboxKey is on top. #47's `openChannel(from: PlayerKey, channelId)` stays for the video screen.
- **`close(key)`** removes any key but InboxKey, so the inbox always stays and the back stack never empties. Search YouTube's back arrow calls it, so a double tap on the arrow removes SearchKey once and stops at the inbox.
- **`openVideo`** opens a video from any screen but the video screen: since #234 it adds a PlayerKey unless a PlayerKey is on top, so a tap on an inbox row opens the video and a second tap opens nothing. This change keeps it. BackStackTest checks it with InboxKey, SearchKey and ChannelKey on top.
- BackStackTest and NavKeysTest cover the new functions and InboxKey's saved form.

### Search YouTube's back arrow

YouTubeSearch gets an `onBack` action, and its screen an IconButton with ic_arrow_back from #47 and the content description "Back", before its title, as the design's frame has. Its previews and screenshot references change, and are recorded again.

### Emulator tests

- Every emulator test that starts on Search YouTube, the six on main, ChannelSubscribeTest and #50's ResumeTest among them, taps the inbox's search button first, through a shared helper next to StopPlayback.kt. The helper finds the button by its content description, "Search YouTube". In "No channels yet", the filled button says "Search YouTube" as its text, and its search icon sits next to that word, so, as #50's watched chip does on main (docs/architecture.md, "The watched chip"), the icon has no content description of its own and TalkBack reads the button once. A button's text isn't a content description, so the helper still finds exactly one node.
- A new emulator test, InboxFromChannelTest, opens the test video's channel page as #47's ChannelSubscribeTest does, subscribes, goes back to the inbox, waits until the test video shows there under its channel, and taps it to open the video screen. #50's TestDatabaseModule gives every test its own empty in-memory database, so it starts with no subscription and leaves nothing behind. FakeYouTubeModule's channel answers with TestVideo's videos, so no request leaves the emulator.
- `:core:database`'s InboxQueryTest has a test for each part of the rule: the 2 latest, published after subscribing, first seen in a later save, an estimate before subscribing, kinds, watched, partly watched, a started video pushed out of the 2 latest, unsubscribed, subscribed again, and the order.

### What the reviewer and the docs need

docs/architecture.md: the module graph's inbox arrows become solid, the feature modules table describes the real modules, `:app`'s row says it opens on the inbox, the navigation section names InboxKey and the new functions, and the database repositories table lists `inboxVideos()`. docs/testing.md: the emulator tests list, what the new test checks, and a "Checking the inbox by hand" section. AGENTS.md: "Where things are" and the module list.

## Risks / Trade-offs

- [A subscription kept under an ID that YouTube now sends on to another channel never matches the saved videos, which are saved under the new ID.] → #52 decides how a refresh follows the move, as its issue asks. The channel page subscribes under the answered ID, so a subscription made there matches. The inbox spec says nothing about moved channels until #52 does.
- [A clock set back makes a later save's `first_seen_at` earlier than the first save's, so its videos fail the second test.] → The publish time test still catches them when YouTube dates them after the subscription, and the 2 latest still show. The clock rarely goes back by more than a few seconds, from network time.
- [An older video the user wanted to watch, and never started, leaves the inbox when two newer ones arrive.] → The maintainer chose the rule, a started one stays, and the channel page still lists it.
- [After a Takeout import, the inbox says "You're all caught up" until #52 refreshes.] → True for what the phone knows, and #52 comes next in the phase. #51's session knows it.
- [A channel that YouTube sends on to another one.] → This change decides to leave it to #52, as design.md's Non-Goals say, although #52's comments ask #49 and #52 to decide it. The pull request says so.
- [#50 changed watch state.] → It merged as #236. The inbox only reads `watch_state`, and this change touches StoredVideosRepository, VideoDao and Mappings.kt's video mapping, not the watch state's repository, DAO or rules.
- [Turning StoredVideosRepository from a fun interface into an interface breaks a fake written as a lambda.] → Its only fake is in `:core:data`'s tests, which this change updates. Neither #234 nor #236 added one.
- [Two saves of one channel's uploads in the same millisecond would give their new videos the same `first_seen_at`, so a video the second brought would fail the first-save test.] → Each save follows its own request to YouTube, so two can't land in the same millisecond in practice. The publish time test and the 2 latest still apply. CodeRabbit raised it as unverified on #235.
- [The first-save test counts an old video as new when it first enters the saved list late, such as when a newer upload is deleted or made private and an older one moves into YouTube's first page, or when the first save skipped it as a parse error.] → About one video per such event, which the user can mark watched. The 2 latest rule doesn't change it.
- [The screenshot tests can't show the mini player over the inbox.] → The hand checks cover it, as they do for the other screens. The player spec's "A minimized video" and "Tapping the notification" scenarios named Search YouTube as the screen under the mini player, which a video opened from the inbox makes false, so the player delta changes them to the screen the video was opened from. Its other scenarios that name Search YouTube say the video came from its results, so they stay true. The inbox spec's "The mini player" scenario covers the inbox.
- [The plan rested on #47's code before it merged.] → Task 0.1 checked each fact against main after #234: the one that differed, the page's state keeping only rows, is in the decision above.

## Three pull requests

The work lands in three pull requests, so the query and its timing, which can change the plan, merge first, and the last one stays smaller:

1. **The inbox read** ("Part of #49"): this change's proposal, VideoDao's query with its DAO tests and its timing, and StoredVideosRepository's `inboxVideos()` with its tests and docs. It needs nothing from #47, so it can start before #47 merges.
2. **The channel page saves its uploads** ("Part of #49"): task 5.1 alone, after #47 merges and after task 0.1. The inbox has no screen yet, so it changes nothing a user sees until the third.
3. **The inbox screen** ("closes #49"): everything else, and the archive of this change as its last commit. Search YouTube's back arrow comes here, with the inbox it goes back to.

## Migration Plan

No schema change and no migration. A phone that has the #48 build keeps its data. Rolling back restores Search YouTube as the first screen.
