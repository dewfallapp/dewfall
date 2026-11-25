# Proposal

## Why

The inbox shows only the videos that a channel page saved. Nothing fetches the new uploads of every subscribed channel, so after a Takeout import (#51) the inbox stays "all caught up", and a channel's new videos reach the inbox only when the user opens its page. This change adds the design's "Check for new videos" button, the manual refresh that Phase 5 lists ([#52](https://github.com/dewfallapp/dewfall/issues/52)). Phase 7's overnight run will reuse the same check.

## What Changes

- **A "Check for new videos" button** in the inbox's top bar, after the search button, as the design system's TopAppBar has it. It shows while the user subscribes to at least one channel and the inbox could be read. A tap checks every subscribed channel: it asks YouTube for each channel's latest uploads, one channel after another, and saves them as the channel page does. The inbox then updates on its own, as it already does after a save. There is no pull to refresh, and opening the app still sends no request.
- **While a check runs**, the line under "Dewfall" reads "Checking for new videos", the button's glyph turns slowly, unless the phone's animations are off, and the button can't be tapped. The check runs in the app, not in the screen, so it goes on when the user opens a video, a channel page or Search YouTube, and the inbox shows it still running on the way back. Only one check runs at a time.
- **When a check can't reach YouTube**, the inbox shows a calm banner above its cards, with "Try again": "Couldn't check for new videos. YouTube can't be reached. Check your connection." with the wifi_off glyph when the connection failed, or "Couldn't check for new videos because YouTube isn't loading." with the error glyph when YouTube refused or answered in a way the app can't read. The design's banners say downloads still play, which Phase 6 makes true, so their words wait until then. The banner goes when the next check starts. Videos already saved stay in the inbox.
- **A channel that fails on its own**, because YouTube closed it or can't show it, is skipped quietly, and the check goes on with the next. The inbox says nothing about it. The log keeps only counts.
- **A channel that moved.** When YouTube answers for a subscribed channel with another channel's ID, the check moves the subscription to that ID, keeping the time the user subscribed, and saves the uploads under it, so its new videos reach the inbox. If the user already subscribes under the new ID, the old subscription just goes. Videos saved under the old ID move with it, so they stay in the inbox as they were. This settles what #47 and #48 left to this task.
- **The data layer gets the check**, in `:core:data`, with no screen in it, so Phase 7's background job can run it too, as AGENTS.md asks.

## Capabilities

### New Capabilities

None.

### Modified Capabilities

- `inbox`: the top bar gains the "Check for new videos" button, the inbox shows a running check, and a banner when a check can't reach YouTube.
- `subscriptions`: what a check for new videos fetches and saves, how it handles a channel that fails on its own, and how it follows a channel that moved. The channel page's scenario for a moved channel now says the check moves the subscription.

## Impact

- `:core:model`: UploadsCheck, the check's state (running, and the failure of the last check, if any), and UploadsCheckFailure, as plain types that the inbox's reducer can read.
- `:core:data`: UploadsChecker, an interface with `state` and `start()`, and its implementation, a singleton that runs the check in the `@ApplicationScope` scope and logs counts only, with JVM tests. SubscriptionsRepository gains `moveSubscription(from, to)`.
- `:core:database`: ChannelDao gains a `moveSubscription` transaction, with DAO tests. The schema doesn't change.
- `:feature:inbox-logic`: InboxState gains the check's state, InboxIntent gains `CheckForNewVideos` and `CheckChanged`, with reducer tests.
- `:feature:inbox-ui`: the button, the "Checking for new videos" line, the banner, strings, previews, ViewModel tests and screenshot tests. The existing references with a top bar get the new button.
- `:core:designsystem`: the sync and wifi_off glyphs from Material Symbols Rounded, under the Apache License 2.0 like the others.
- `:app`: an emulator test that subscribes to a channel, taps "Check for new videos" against the fake YouTube layer, and finds its video in the inbox. No navigation change.
- Docs: docs/architecture.md (`:core:data`'s row and the inbox section, the redirect note under the database tables, the check itself), docs/testing.md (the inbox's hand checks and the emulator test count).
- No new dependency, no schema change. The check's requests go only to YouTube, through the YouTube layer, two for most channels, as docs/architecture.md's table says. Nothing new leaves the phone or is logged beyond counts.

## The maintainer's answers

On November 24, 2026, in this change's Claude Code session, in its question dialog, the maintainer answered four questions:

1. "#52 manual refresh: how does the user start a check for new videos? (Design: top bar 'Check for new videos' icon, turns slowly while running, line under 'Dewfall' reads 'Checking for new videos', can't tap while running.)" Answer: "Top bar button only (Recommended)". The option said: "As the design: sync icon in the inbox's top bar, shown when you subscribe to at least one channel. No pull-to-refresh, no check on app open (inbox spec says opening sends no request)."
2. "#52: what shows when the check can't reach YouTube? Design banners mention downloads, which don't exist until Phase 6." Answer: "Two calm banners (Recommended)". The option said: "No connection: wifi_off glyph, 'Couldn't check for new videos. YouTube can't be reached. Check your connection.' YouTube refusing or broken: error glyph, 'Couldn't check for new videos because YouTube isn't loading.' Both with 'Try again'. Gone when the next check starts. Videos already saved still show. Phase 6 adds the downloads sentence."
3. "#52: when only some channels fail (closed, removed, unavailable) and the rest are checked, what does the inbox say?" Answer: "Nothing (Recommended)". The option said: "Skip them quietly and check the rest; the log keeps counts only. A later task can mark such channels on the Channels screen. A connection or YouTube-wide failure stops the check and shows the banner."
4. "#52: YouTube sends some channel IDs on to another channel (moved channels, old Takeout IDs). Its new videos come back under the new ID and don't show for a subscription under the old one. What should a check do?" Answer: "Move the subscription (Recommended)". The option said: "Subscription moves to the new ID, keeping the time you subscribed, so its new videos show. If you already subscribe under the new ID too, the old one just goes. Videos saved under the old ID stay saved, out of the inbox."

Later on November 24, 2026, in the same session's question dialog, the maintainer approved this change:

5. "Approve the #52 plan (openspec/changes/inbox-refresh)? A 'Check for new videos' button in the inbox top bar (hidden with no channels or an unreadable inbox) runs one app-wide check, one channel after another, that goes on if you leave the inbox. While it runs: 'Checking for new videos' under 'Dewfall', turning glyph, no second tap. No connection or YouTube refusing: check stops, calm banner with 'Try again'. A single closed or broken channel is skipped quietly. A moved channel's subscription moves to the new ID with its original time. No new dependency, no schema change." Answer: "Approve (Recommended)". This is the approval that AGENTS.md's "Spec first" asks for.

Later on November 24, 2026, after the reviewer subagent asked about a case that the plan check had added after the approval, the maintainer answered a sixth question in the same session's question dialog:

6. "#52: after your approval, the plan check added one case. When no channel's answer can be read at all (e.g. you have 1 subscription and the app can't read YouTube's answer for it), or 3 channels in a row can't be read, the check stops and shows 'Couldn't check for new videos because YouTube isn't loading.' with Try again, since the app can't tell that apart from YouTube changing its pages. A single unreadable channel among working ones is still skipped quietly. OK?" Answer: "Keep the banner (Recommended)". The subscriptions spec's "A check skips a channel that fails on its own" follows it.

On November 25, 2026, after CodeRabbit's review of the pull request, the maintainer answered a seventh question in the same session's question dialog:

7. "#52: CodeRabbit points out that when a check moves a channel's subscription to its new ID, a video you started under the old ID leaves the inbox if YouTube's latest uploads no longer list it (your answer 4 said old videos stay saved but out of the inbox). The inbox spec otherwise promises a started video stays until marked watched. What should a move do with videos saved under the old ID?" Answer: "Move them too (Recommended)". The option said: "The same transaction moves the old ID's saved videos to the new ID, so they stay in the inbox exactly as before the move; watch state is kept (it's keyed by video)." It replaces answer 4's "out of the inbox". The subscriptions spec's "A check follows a channel that moved" follows it.
