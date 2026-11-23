# Proposal

## Why

Phase 5's goal is that opening the app shows new videos from your channels. #48 keeps the subscribed channels, their videos and what the user watched on the phone, and #47 adds Subscribe on the channel page, but the app still opens on Search YouTube and no screen reads the saved videos. This change adds the inbox, the calm home screen that the vision and the design brief describe ([#49](https://github.com/dewfallapp/dewfall/issues/49)).

## What Changes

- **The inbox is the app's first screen.** It lists the new videos of the channels the user subscribes to, grouped in one card per channel, as the design's "Inbox" frame does. A tap on a video opens its video screen. A tap on a card's channel row opens that channel's page. The list ends with "That's everything for now." It has no suggestions, no Shorts, and nothing that keeps it going: once the user has watched everything, it is empty.
- **Which videos count as new.** A video is in the inbox when its channel is subscribed, it is a video that can be watched from start to end (not a Short, a live stream on air or a premiere that hasn't started), it isn't marked watched, and either it is one of the channel's 2 latest videos, or it came out after the user subscribed, or the user started watching it since subscribing. So subscribing gives a channel's 2 latest videos to start with, as the design's prototype does, and every later video after that. A newer video pushes an older one of those 2 out, unless the user started it. The maintainer chose this rule, as "The maintainer's answers" below quotes. Marking a video watched (#50) takes it out at once. A half-watched video stays.
- **Two empty states**, from the design: "No channels yet" with a "Search YouTube" button when the user subscribes to nothing, and "You're all caught up" when nothing is new. Their words leave out overnight downloads until Phase 7, as #47's channel page does.
- **A top bar** with "Dewfall", "Inbox" and a search button that opens Search YouTube. The design's refresh button comes with #52, and its menu button and navigation bar with the tasks that give them something to hold (#115 adds the Channels screen, the bar's second destination).
- **Search YouTube opens from the inbox** and gets a back arrow, as the design's frame has. It is no longer the first screen. **BREAKING** for the youtube-search spec's "The app opens on Search YouTube", which said it would hold until another screen became the home screen.
- **The channel page saves the uploads it shows** while the user subscribes to the channel: when the user taps Subscribe, and whenever the page of a subscribed channel loads. So subscribing fills the inbox with no request of its own. Fetching every channel's uploads, for a manual refresh and after a Takeout import, is #52's, and the overnight runs are Phase 7's. This change sends no new request to YouTube.
- **The saved videos get a read for the inbox**, in `:core:data`'s StoredVideosRepository and `:core:database`'s VideoDao, which applies the rule above in one query, so the inbox updates on its own when a save, a subscription or a watched mark changes.

## Capabilities

### New Capabilities

- `inbox`: the home screen: which videos it shows and in what order, its groups, its empty states, what its taps open, and how it fits any window. It is one of the living-spec areas that the project's config lists.

### Modified Capabilities

- `subscriptions`: the channel page, whose requirements #47 adds to this spec, saves the uploads it shows while the channel is subscribed, so subscribing fills the inbox.
- `player`: two scenarios that named Search YouTube as the screen under the mini player, and as where the notification returns to, now name the screen under the video screen, such as the inbox, Search YouTube or a channel page.
- `youtube-search`: Search YouTube is no longer the first screen. It opens from the inbox's search button, has a back arrow, and going back returns to the inbox.

## Impact

- New modules `:feature:inbox-logic` (Kotlin/JVM: InboxState, InboxIntent, the reducer that groups videos by channel, and its tests) and `:feature:inbox-ui` (Android: the public `Inbox` composable, the screen, its previews and screenshot tests, InboxViewModel and its tests, string resources). settings.gradle.kts includes them.
- `:core:database`: VideoDao gets the inbox query, with DAO tests in its androidTest. The schema doesn't change.
- `:core:data`: StoredVideosRepository becomes a plain interface with `inboxVideos()`, and the mapping from a saved row to VideoSummary. Its tests cover both.
- `:core:mvi`: MviViewModel gets a protected, read-only count of its state's collectors, so a ViewModel can read only while its screen shows, with tests.
- `:core:designsystem`: the subscriptions icon from Material Symbols Rounded for "No channels yet", under the Apache License 2.0 like the others. "You're all caught up" uses #50's done_all.
- `:core:data`: SubscribeWithUploads, which subscribes and then saves the uploads in the app-wide scope, with its tests.
- `:feature:channel-page-ui` (from #47): ChannelViewModel subscribes through SubscribeWithUploads, and saves the shown uploads while the channel is subscribed, with ViewModel tests.
- `:feature:youtube-search-ui`: a back arrow and an `onBack` action, and new reference images.
- `:app`: InboxKey becomes the back stack's first key, and BackStack.kt gets the functions that open Search YouTube and a channel page from the inbox, with their unit tests. The emulator tests start from the inbox, and a new one checks that subscribing on a channel page puts its video in the inbox.
- Docs: docs/architecture.md (modules and graph, navigation, the database repositories), docs/testing.md (emulator tests, and the inbox's hand checks), AGENTS.md (where things are, modules), and README.md if it names the first screen.
- No new dependency, no schema change, no new network request, and nothing new is logged or leaves the phone.
- It lands in three pull requests: the inbox read, "Part of #49", which needs nothing from #47; the channel page's save, "Part of #49", after #47 merges; then the screen, "closes #49".

## The maintainer's answers

On November 23, 2026, in this change's Claude Code session, in its question dialog, the maintainer answered four questions:

1. "Plan for #49: the app opens on a new Inbox with new videos from your subscribed channels, one card per channel, offline, no suggestions. Search YouTube opens from its search button with a back arrow. Subscribing on a channel page fills the inbox; the refresh button comes with #52. Approve this plan?" Answer: "Approve (Recommended)". This is the approval that AGENTS.md's "Spec first" asks for.
2. "Which videos of a subscribed channel should the inbox show? Plan: its 2 latest, plus every video after you subscribed, plus any you started since subscribing. An older one you never started leaves when 2 newer arrive. Watched ones leave at once." Answer: "2 latest + newer (Recommended)". The inbox spec's rules for new and started videos follow it.
3. "Should live streams that are on air, and premieres that haven't started, show in the inbox?" Answer: "Only once finished (Recommended)".
4. "Until downloads (Phase 7) and subtitle search (Phase 8) exist, the inbox drops the design's download words: '2 videos' instead of '2 of 2 downloaded', 'Nothing new from your 4 channels.', 'That's everything for now.', and its search button opens Search YouTube directly. OK?" Answer: "Yes (Recommended)".

On November 23, 2026, in the same session's question dialog, after the maintainer subagent's plan check asked for it, the maintainer answered a fifth question:

5. "When you mark an old video unwatched after subscribing to its channel, should it come back into the inbox? Under the plan it comes back only if you had started watching it (it has a saved position); a video marked watched without ever playing stays out." Answer: "Yes, if started (Recommended)". The inbox spec's "A video started since subscribing stays" and its "Marked unwatched after subscribing" scenario follow it.

Later that day, after the maintainer subagent's plan check asked what the inbox does when its read fails, the maintainer answered a sixth question in the same session's question dialog:

6. "If Dewfall can't read its own saved data when the inbox opens (rare: a damaged database or full storage), what should the inbox show instead of crashing the app?" Answer: "An error message (Recommended)". The option said: "The inbox shows the error glyph, "Your inbox can't be shown" and "Dewfall couldn't read what it keeps on this phone. Restarting the app may help." The search button still works." The inbox spec's "The inbox says when it can't be read" follows it.
