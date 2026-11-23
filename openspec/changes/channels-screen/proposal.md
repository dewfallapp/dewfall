# Proposal

## Why

Phase 5 lets the user subscribe from a channel page (#47), keeps the subscriptions on the phone (#48) and shows their new videos in the inbox (#49). But the user can't see which channels they follow, or get back to one channel's page, without finding a video of it first. The design brief gives the app a Channels screen, reached from a navigation bar, that lists the subscribed channels, each opening its channel page ([#115](https://github.com/dewfallapp/dewfall/issues/115)).

## What Changes

- **A Channels screen.** It lists every subscribed channel, by name, in one card, under a count such as "4 channels", as the design's "Channels" frame does. Each row shows the channel's emblem, its name, when it last uploaded, such as "Last upload yesterday", and a chevron. Tapping a row opens that channel's page.
- **When the channel last uploaded** comes from the videos Dewfall saved: the newest saved video of the channel that has come out, Shorts and live streams included, upcoming premieres and streams left out. When nothing of the channel is saved yet, such as right after a Takeout import, the row shows no such line. Showing the screen sends no request to YouTube. The maintainer chose this, as "The maintainer's answers" below quotes.
- **An empty state**, from the design's "Channels, no channels yet" frame: "No channels yet", "Find channels with Search YouTube. Their new videos show in your inbox." and a "Search YouTube" button. The words leave out overnight downloads until Phase 7, as the inbox does. The design's "Import from Google Takeout" link is added by whichever of this change and #51's lands second, as the inbox's is.
- **A top bar** with "Dewfall", "Channels" under it, and a search button that opens Search YouTube. No menu button, as on the inbox.
- **A navigation bar** with two destinations, Inbox and Channels. Downloads and Settings join it when their screens exist, in Phases 6 and 7 and later. The bar shows on the inbox, the Channels screen and channel pages. It hides on Search YouTube and the video screen, as the design's frames do. The mini player sits just above it.
- **Each destination keeps its own history.** Leaving Channels for Inbox and coming back shows Channels as it was, such as a channel page opened from it. The back gesture on the Channels screen returns to the inbox, so the user always leaves the app through the inbox. Tapping the destination that already shows returns it to its first screen.
- **Search YouTube opens from Channels too**, and its back arrow returns to the screen it opened from. This changes two of the youtube-search requirements that the inbox change adds.
- **The subscriptions get a read for the screen**, in `:core:data`'s SubscriptionsRepository and `:core:database`'s ChannelDao: each subscribed channel with its last upload, in one query, so the screen updates on its own when a subscription or a save changes.

## Capabilities

### New Capabilities

None.

### Modified Capabilities

- `subscriptions`: the Channels screen: what it lists and in what order, the last upload line, its empty state, its taps, and how it fits any window. The subscriptions spec already holds the channel page, which the screen opens.
- `inbox`: the navigation bar between the inbox and the Channels screen, its history for each destination, and the mini player above it. The inbox change (#49) adds this spec before this change lands.
- `youtube-search`: Search YouTube also opens from the Channels screen, and its back arrow returns to the screen it opened from.

## Impact

- New modules `:feature:channels-logic` (Kotlin/JVM: ChannelsState, ChannelsIntent, the reducer and its tests) and `:feature:channels-ui` (Android: the public `Channels` composable, the screen, its previews and screenshot tests, ChannelsViewModel and its tests, string resources). settings.gradle.kts includes them.
- `:core:model`: ChannelOverview, a subscribed channel with the publish time of its last upload.
- `:core:database`: ChannelDao gets the query, with DAO tests in its androidTest. The schema doesn't change.
- `:core:data`: SubscriptionsRepository gets `channelOverviews()`, with tests.
- `:core:designsystem`: the navigation bar's icons, outlined and filled, from Material Symbols Rounded under the Apache License 2.0 like the others.
- `:app`: the navigation bar, a back stack for each destination, ChannelsKey, and BackStack.kt's functions for them, with unit tests. Emulator tests for the bar, the back gesture and opening a channel from the list.
- Docs: docs/architecture.md (modules and graph, navigation), docs/testing.md (emulator tests, hand checks), AGENTS.md (where things are, modules).
- No new dependency, no schema change, no new network request, and nothing new is logged or leaves the phone.
- It lands in two pull requests: the read, "Part of #115", which needs nothing from #49's screen; then the screen and the bar, "closes #115", after #49's inbox screen merges, because the bar sits on the inbox.

## The maintainer's answers

On November 23, 2026, in this change's Claude Code session, in its question dialog, the maintainer answered four questions. The last is the approval that AGENTS.md's "Spec first" asks for.

1. "#115 Channels screen: the design reaches it from a bottom bar with Inbox, Channels, Downloads and Settings, but Downloads and Settings don't exist until later phases. How should the user reach Channels until then?" Answer: "Bar: Inbox + Channels (Recommended)". That option said: "Bottom bar with two tabs now; Downloads and Settings join when built. Bar shows on Inbox, Channels and channel pages, hidden on Search YouTube and the video screen, like the design. Each tab keeps its own history; Back from Channels goes to Inbox; tapping the current tab returns to its top. Mini player sits above the bar."
2. "Each Channels row in the design says 'Last upload yesterday'. Dewfall only knows this from videos it has saved (on subscribe, opening a channel page, later the refresh in #52). Show that line?" Answer: "Show when known (Recommended)". That option said: "From the newest saved video that has come out (not an upcoming premiere or stream; Shorts count). No line when nothing is saved yet, such as right after a Takeout import."
3. "Channels empty state: 'No channels yet' / 'Find channels with Search YouTube. Their new videos show in your inbox.' with a Search YouTube button, no overnight-download words until Phase 7 (as the inbox does). 'Import from Google Takeout' is added by whichever of #115 and #51 lands second. Top bar: 'Dewfall' / 'Channels' with a search button to Search YouTube, no menu button. OK?" Answer: "Yes (Recommended)".
4. "Plan for #115 (openspec/changes/channels-screen): a Channels screen listing subscribed channels by name with a count and 'Last upload …' from saved videos, each row opening its channel page; empty state as agreed; a bottom bar with Inbox and Channels, one history per tab, Back from Channels to Inbox, tap current tab to return to its first screen; Search YouTube opens from Channels too and goes back there. Two PRs: the read first, then screen + bar after #49's inbox screen merges. Approve this plan?" Answer: "Approve (Recommended)".
