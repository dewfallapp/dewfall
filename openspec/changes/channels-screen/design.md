# Design

## Context

After #47, #48 and #49, the app opens on the inbox (InboxKey at the root of one back stack in `:app`). Search YouTube, the video screen and channel pages open on top of it. The channels table holds the subscriptions, and the videos table holds the uploads that Dewfall saved, with an index on `(channel_id, published_at)`. ChannelDao already reads the subscriptions by name, ignoring case, and its comment leaves the order to this screen.

The design's "Channels" and "Channels, no channels yet" frames show the screen with a navigation bar of four destinations. The brief says the app has that bar. Downloads and Settings have no screen yet, so the maintainer chose a bar with two destinations until they do.

## Goals / Non-Goals

**Goals:**

- The Channels screen, its empty state, and the last upload line, read from the phone only.
- The navigation bar with Inbox and Channels, a back stack for each, "exit through home", and the mini player above the bar.
- Search YouTube opening from Channels and going back to it.

**Non-Goals:**

- Downloads and Settings destinations, the search sheet with subtitle search, the top bars' menu buttons, and the ⋮ menu on each of the inbox's cards. The maintainer chose no menu button on the Channels screen (answer 3), and none of the menus has a task yet, so this change takes none of them.
- Channel avatars as images. Rows use ChannelEmblem with the initial, as the channel page and the inbox do.
- "Import from Google Takeout" in the empty state, unless #51 is on main when this lands (see Risks).
- Fetching anything. The refresh is #52's.

## Decisions

### The read: one query in ChannelDao

ChannelDao gets `channelOverviews()`, a Flow of rows with the channel's columns plus the publish time and its approximate flag of the channel's last upload:

```sql
SELECT c.*,
  v.published_at AS last_published_at,
  v.published_approximate AS last_published_approximate
FROM channels c
LEFT JOIN videos v ON v.rowid = (
  SELECT v2.rowid FROM videos v2
  WHERE v2.channel_id = c.id AND v2.kind != 'UPCOMING' AND v2.published_at IS NOT NULL
  ORDER BY v2.published_at DESC, v2.rowid DESC
  LIMIT 1)
```

- The query's SQL is one `const val` in ChannelDao, which `@Query` and the plan test both use, so the test checks the query the app runs.
- One subquery picks the row of the last upload, and the join reads its time and approximate flag, so both come from the same row. The videos table has a rowid, because its TEXT primary key isn't an INTEGER PRIMARY KEY, and SQLite keeps an index's entries in rowid order after its own columns ([Database File Format, section 2.5](https://www.sqlite.org/fileformat2.html): an index's key is "the columns that are being indexed followed by the key of the corresponding table row", which is the rowid for ordinary tables). So `ORDER BY published_at DESC, rowid DESC` matches the `(channel_id, published_at)` index read backwards, and the subquery stops at the first row that isn't upcoming, with no sort. Between two videos with the same time, the one with the higher rowid wins. That can only change the line when one of the two times is an estimate, such as "today" instead of "7 hours ago", which is harmless. On November 23, 2026, `EXPLAIN QUERY PLAN` in SQLite 3.54.0 on the Mac, with the schema's tables and index, showed the subquery as `SEARCH v2 USING INDEX index_videos_channel_id_published_at` with no sort, while the same subquery with `ORDER BY published_at DESC, published_approximate ASC`, which prefers an exact time in a tie, added `USE TEMP B-TREE FOR LAST TERM OF ORDER BY`. Android 10 ships an older SQLite, so the DAO test below checks the plan on the device.
- A DAO test runs `EXPLAIN QUERY PLAN` on the query and fails if any line says `USE TEMP B-TREE` or if no line names `index_videos_channel_id_published_at`, because SQLite doesn't promise the output's format and a check of absence alone could pass without checking anything, so a sort that creeps in fails a test on every Android version the test runs on, Android 10's SQLite included.
- The timing test uses #49's heavy-user data, 300 channels of 300 videos, 90,000 rows, so it would show a cost that grows with the videos.
- The query doesn't order the channels. The repository sorts them, below.
- Room stores VideoKind by name, so `'UPCOMING'` matches its built-in converter. A DAO test saves an upcoming video and checks it is skipped, so a change of storage fails a test.
- It lives in ChannelDao, not VideoDao, because it is a read of the subscriptions, and so it doesn't touch the file #49 changes.
- Room's invalidation tracker sees both tables in the query, so the Flow emits again on a subscription change or a save. A DAO test checks both.

Alternatives: a LEFT JOIN with MAX(published_at) and GROUP BY gives the time, but not the approximate flag of the same row without a second join. Reading every video in Kotlin costs more as saves grow.

### The model and the repository

- `:core:model` gets `ChannelOverview(subscription: SubscribedChannel, lastUpload: PublishedTime?)`, which keeps the subscription time the query already reads, for later uses such as sorting by it. PublishedTime is the type search rows and the channel page already use, so `timeAgo()` counts it as Search YouTube does, and an estimated time never shows minutes or hours.
- SubscriptionsRepository gets `fun channelOverviews(): Flow<List<ChannelOverview>>`, mapped in `:core:data` with `distinctUntilChanged()`, like `subscribedChannels()`. `subscribedChannels()` stays for the inbox and the import.
- The repository sorts, not the ViewModel, so every reader of the list gets the same order, as with `subscribedChannels()`, whose order the DAO sets. ChannelOverview keeps the whole subscription and publish time, so a later caller can sort another way.
- The repository sorts the list by name with `java.text.Collator` for the phone's current locale, at SECONDARY strength, so case doesn't count and an accented letter sorts next to its plain one: "Élodie" comes between "Delta" and "Falcon", not after "Zebra". Names that compare equal keep their order by channel ID. SQLite's `COLLATE NOCASE` folds only ASCII letters and orders the rest by byte, which puts accented and non-Latin names last. A subscriber has at most some hundreds of channels, so sorting in Kotlin on each emission costs little. The collator is made on each emission, so a change of the phone's language applies at the next emission: the next database change, or the next time the screen starts collecting, which is at most 5 seconds after it stops showing. Until then the old order can stay, which is minor. Repository tests cover case, an accented name and equal names, with the locale given to the sorting function so the tests don't depend on the machine.
- The future-time rule: `timeAgo()` returns null for a time after now, so the row shows no line. The query doesn't filter by the clock, which keeps it free of a parameter that changes every call.

### The feature modules

- `:feature:channels-logic`: `ChannelsState(list: ChannelsList)`, where ChannelsList is `Loading`, `NoChannels` or `Shown(channels: List<ChannelOverview>)`. One intent, `ChannelsRead(channels)`. No effects. The reducer picks NoChannels for an empty list. Reducer tests cover each case.
- `:feature:channels-ui`: ChannelsViewModel extends MviViewModel and collects `channelOverviews()` only while its state has a collector, with the `stateSubscriptionCount` and `WhileSubscribed(5_000)` pattern that #49's InboxViewModel uses, so the query doesn't rerun while the inbox covers a hidden Channels screen. ChannelsRoute reads the clock and the zone once per new list, as ChannelRoute does, so the screen stays a function of what it gets. So "Last upload today" can stay on screen past midnight until the list changes or the screen is opened again, as on the channel page, which is fine for a line this coarse.
- The public composable is `Channels(onOpenChannel: (ChannelId) -> Unit, onOpenSearch: () -> Unit, modifier)`. Opening a channel or Search YouTube changes no state, so those go straight to `:app`'s navigation with no intent, as AGENTS.md says.
- The list's LazyColumn and its `rememberLazyListState()` sit only in the Shown branch, so a restored scroll position waits for the list instead of being spent on the Loading state.
- The screen follows the frame: a large top app bar area with "Dewfall" and "Channels", the search IconButton, the count in `title-small`, and for the empty state the design's round disc with the subscriptions glyph above the title, with a content description, as #49's inbox empty state has, then one card of rows. Each row is ChannelEmblem with the initial, the name in `title-medium`, the last upload line in `body-medium`, and the chevron glyph, merged into one clickable item for screen readers with an "Open channel" click label. The rows share the inbox's group shape, `rowGroupShape` from `:core:ui`. Content sits in a centered column at most 640dp wide, with `safeDrawing` padding.
- The last upload line has its own strings, "Last upload %s", with spelled units such as "7 hours ago", and its own forms for TimeAgo's Today and Yesterday, "Last upload today" and "Last upload yesterday", as the frame writes them, because the rows' short forms, such as "7h ago", read badly in a sentence.

### Navigation: one back stack for each destination

This follows the [multiple back stacks recipe](https://developer.android.com/guide/navigation/navigation-3/recipes/multiple-backstacks) of Navigation 3:

- `:app` keeps two NavBackStacks, `rememberNavBackStack(InboxKey)` and `rememberNavBackStack(ChannelsKey)`, and the selected destination, an enum `Destination { INBOX, CHANNELS }`, in `rememberSaveable`. All three survive configuration changes and process death.
- Each back stack gets its own decorators (saveable state holder and ViewModel store) through `rememberDecoratedNavEntries`, so a hidden destination's entries keep their state and ViewModels. NavDisplay gets `entries`: the inbox's entries, then the Channels entries when Channels is selected. So the inbox always sits under Channels, and the back gesture on the Channels screen falls to it: "exit through home".
- A small class, `AppNavigation`, holds the two stacks and the selection, and owns the changes between them, so unit tests in `:app` can drive it without Compose. BackStack.kt's extension functions on one list stay, with their tests, and AppNavigation calls them on the selected destination's stack:
  - `select(destination)`: shows the other destination; on the selected one, removes every key above its first.
  - `openVideo`, `openChannel(from: NavKey, channelId)` and `close(key)` keep their rules and act on the selected destination's stack. `close` never removes a stack's first key. Since #239, `openChannel` adds a ChannelKey only while `from` is on top, and removes `from` first only when it is a PlayerKey, so it also serves the Channels list's rows (`from` = ChannelsKey), the inbox's channel rows (`from` = InboxKey) and Search YouTube's channel results (`from` = the SearchKey), and a double tap opens one page. No separate function for the list is needed, and #49's `openChannelFromInbox`, which has the same rule for InboxKey, folds into it.
  - `openSearch(from)`: adds a SearchKey only while `from`, InboxKey or ChannelsKey, is on top of its stack. It generalizes #49's `openSearch()`, which allows only InboxKey.
  - SearchKey has an entry ID, a random UUID's text, as PlayerKey and ChannelKey have, and it is the entry's content key. #49's inbox screen already made it so. Two Search YouTube entries can be on the two stacks at once: from the inbox, open Search YouTube, a video, then its channel, whose page shows the bar; tap Channels and open Search YouTube there. Navigation 3 treats entries with the same content key as the same content and shares their decorator state ([NavEntry](https://developer.android.com/reference/kotlin/androidx/navigation3/runtime/NavEntry)), so a `data object` key would give both entries one saved search field and one ViewModel. Each entry gets its own instead, and each stack's Search YouTube keeps its own text. Blocking a second Search YouTube was the other choice, but it would make the search button on Channels do nothing on that path, with no way for the user to tell why.
  - `goBack()`: removes the top key of the selected stack; on the first key of the Channels stack, selects Inbox.
- The bar shows while the top key of the selected stack is InboxKey, ChannelsKey or ChannelKey. The mini player shows while it isn't a PlayerKey, as now. A PlayerKey is only ever on top of the selected stack, because the bar hides on the video screen, so at most one exists across both stacks.
- DewfallNavigation keeps #242's layout: a Box with NavDisplay filling it and the mini player drawn over its bottom, and the `keepRoomForMiniPlayer` entry decorator, which pads every entry but the video screen's (FILLS_WINDOW) by the mini player's last height and takes that off the entry's window insets. The M3 NavigationBar joins the Box at the bottom while it shows, with the mini player aligned directly above it. The bar pads itself with the navigation bar inset, and while it shows, the mini player's box consumes the bottom system bar inset, so the mini player doesn't pad twice. The entries of the bar's screens, InboxKey, ChannelsKey and ChannelKey, carry a metadata flag, and the decorator keeps room for the bar's last measured height plus the mini player's at their bottom, and only the mini player's at the bottom of Search YouTube. The bar's last height stays while the bar hides for the video screen, as the mini player's does since #242, so a screen under the video screen keeps its size and a list at its end keeps its place. Switching between a screen with the bar and Search YouTube changes the mini player's measured height by the system bar inset, which only the screen that shows uses. One decorator for the whole bottom keeps #242's rule that a new screen gets the room without asking, and its fix for a list's place at its end when the video screen opens.
- The bar follows the design system's NavigationBar notes (docs/design/claude-design/design-system/components/NavigationBar/README.md): `surfaceContainer` behind it, `alwaysShowLabel` on, the indicator in `secondaryContainer`, the selected icon and label in `onSecondaryContainer` and `onSurface`, the others in `onSurfaceVariant`, and at large font sizes the bar grows rather than cutting off a label.
- `:app` has no screenshot tests, so the bar's colors, icons and large-font growth are checked by the emulator test at the largest font size and by the hand checks. Moving the bar into a module with screenshot tests would add a screenshot setup to `:core:designsystem` for one component, which this change doesn't need.
- NavigationBarItem shows the filled icon when selected and the outlined one otherwise, as the design system's iconography asks. The icons are Material Symbols Rounded `inbox` and `subscriptions`, outlined and filled, added to `:core:designsystem`. The labels sit next to their icons, so the icons have no content description of their own, as the iconography rule allows. The strings live in `:app`.

Alternatives: one back stack with the destinations' keys mixed in it loses a destination's history when the other is shown. A NavigationSuiteScaffold would bring a rail on wide windows, but the brief keeps one centered column on every window, so the bar stays a bar.

## Risks / Trade-offs

- [The bar sits on #49's inbox screen, which isn't merged yet] → The read lands first in its own pull request. The screen and the bar land after #49's screen merges, rebased on it.
- [#51 adds "Import from Google Takeout" to the inbox's empty state] → Whichever of #51 and this change lands second adds it to the Channels screen too, with `onImportFromTakeout` on `Channels(...)`, as agreed with #51's session.
- [The subqueries could slow down with many saved videos] → The index serves both, and the DAO test times the query with 300 channels of 300 videos, 90,000 rows, on Android 10, and checks its plan.
- [A two-destination bar is below Material's usual three to five, as the [NavigationBar reference](https://developer.android.com/reference/kotlin/androidx/compose/material3/NavigationBar.composable) says] → The maintainer chose it until Downloads and Settings exist. The bar's code takes a list of destinations, so adding them changes the list and the keys.
- [Process death restores two stacks] → An emulator test saves and restores DewfallNavigation's composition state with Compose's StateRestorationTester and checks the two stacks and the selection come back. That covers the back stacks and the selection, which live in saveable state, but not process death as a whole: the tester "tests only the restoration of the local state of the composable" and not the activity or its ViewModels ([StateRestorationTester](https://developer.android.com/reference/kotlin/androidx/compose/ui/test/junit4/StateRestorationTester)). So the scroll position after process death, where the list shows Loading before it loads again, is a hand check: scroll the Channels list down, end the process with `adb shell am kill` from the background, and come back. To make it hold, the screen keeps its LazyColumn and its `rememberLazyListState()` only in the Shown branch, as #49's inbox does, so the saved position is restored when the list first shows, not consumed by an empty Loading list.

## Migration Plan

No data migration. The schema doesn't change. Existing emulator tests that start on the inbox keep working, because the inbox stays the first screen.

## Open Questions

None. The maintainer's four answers are in the proposal.
