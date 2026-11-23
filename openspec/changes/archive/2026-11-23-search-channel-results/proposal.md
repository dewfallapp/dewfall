# Proposal

## Why

Phase 5's goal is that opening the app shows new videos from your channels. Today the only way to subscribe is to search for a video, open it, then open its channel's page (#47). Search YouTube already gets the channels that match a search from YouTube, in the same request as the videos, but the screen drops them. Issue [#222](https://github.com/dewfallapp/dewfall/issues/222), which the maintainer made a Phase 5 task on November 22, 2026, asks Search YouTube to list those channels above the videos, each with a Subscribe button, and each opening its channel page.

## What Changes

- **Channel results.** A successful search lists the channels YouTube returned, in YouTube's order and each once, under a "Channels" heading above the "Videos" heading, as the "Search YouTube, results for sourdough" frames in docs/design/ show. Each row shows the channel's emblem with its initial, its name, and the short description YouTube gave with the result.
- **Subscribe in the row.** Each row ends with the design system's compact button: a tonal "Subscribe" with the add glyph, or an outlined "Subscribed" with the check glyph. "Subscribe" subscribes at once, as on the channel page. "Subscribed" asks first, in the channel page's Unsubscribe dialog. A screen reader hears the new state, such as "Subscribed to Kettle and Crumb".
- **Opening a channel.** Tapping a channel row, outside its button, opens that channel's page over Search YouTube. Going back shows the same text and results.
- **Only channels, or only videos.** A search that finds channels and no videos shows the channels alone. "No results" shows only when a search finds neither, and its words become the design's "Nothing on YouTube matches ...".
- **The screen's words.** The line under the title becomes the design's "Videos to stream and channels to follow", and the message before the first search becomes "Find a video or a channel", and says that a subscribed channel's new videos show in the inbox. The design's download words wait for Phase 7, as on the channel page and the inbox.
- **What subscribing from search keeps.** The channel's ID, name and avatar links. Not the description: search gives only an excerpt of it, which YouTube cuts with "...". Subscribing from search saves no videos either. On main today nothing saves a channel's uploads or refreshes a kept channel's details: #49's second pull request makes the channel page save the uploads of a subscribed channel when it loads, which also fills in the full description, and #52's refresh does the same for every channel. Until one of those merges, a channel subscribed from search keeps no description and brings no videos to the inbox, as #49's plan says.
- **A shared dialog.** The channel page's Unsubscribe dialog moves to `:core:ui`, so both screens use one copy. The channel page doesn't change for users.

## Capabilities

### New Capabilities

None.

### Modified Capabilities

- `youtube-search`: results gain channels with a Subscribe button and opening the channel page; "No results" applies only when nothing matches; the screen's words and its privacy requirement change to cover channels.
- `subscriptions`: "Subscriptions are kept on the phone" gains a subscription made with no description, as from search, and "Going back" gains a channel page opened from search.

## Impact

- `:feature:youtube-search-logic`: the results state gains channel rows, the subscribed channels and the open dialog; new intents; reducer tests.
- `:feature:youtube-search-ui`: the channel rows, the compact button, the dialog, new strings, previews and screenshot references; the ViewModel reads the subscribed channels and subscribes and unsubscribes through `:core:data`'s SubscriptionsRepository; `YouTubeSearch` takes an `onOpenChannel` action.
- `:core:ui`: gains the Unsubscribe dialog and its strings, which `:feature:channel-page-ui` uses instead of its own.
- `:app`: opening a channel page from Search YouTube on the back stack; an emulator test that subscribes from a channel row; FakeYouTubeModule's search also answers with the test channel.
- No change to the YouTube layer, its saved responses or the daily check: the layer already reads channels from a search, and SearchReplayTest already checks them. No new request to YouTube: the channels come with the search.
- Privacy: no new data leaves the phone. Subscribing keeps the channel on the phone, as the subscriptions spec says. A channel page opened from search saves its channel's ID in the state Android saves, as a channel page opened from a video does today.
- No new dependency.
- Docs: docs/architecture.md (Search YouTube's dependencies and `:core:ui`'s contents), docs/testing.md (the new emulator test and its hand checks).

## The maintainer's answers

On November 23, 2026, in this work's Claude Code session, in its question dialog, the maintainer answered four questions:

1. "Plan for #222 (openspec/changes/search-channel-results): Search YouTube lists the channels YouTube returns above the videos ("Channels", then "Videos"), each row with emblem, name, YouTube's 2-line description excerpt and a compact button. "Subscribe" subscribes at once; tapping the row opens the channel page; back returns to the same results. Subtitle becomes "Videos to stream and channels to follow"; "No results" only when neither channels nor videos match ("Nothing on YouTube matches ..."). Approve this plan?" Answer: "Approve (Recommended)".
2. "What should tapping "Subscribed" in a search result's channel row do?" Answer: "Ask, then unsubscribe (Recommended)".
3. "Subscribing from search keeps the channel without videos, so its videos reach the inbox only once you open its page or the refresh (#52) runs, as #49's plan says. OK, or fetch its latest uploads at once (one extra YouTube request per Subscribe)?" Answer: "No fetch (Recommended)".
4. "Subscribing from search keeps the channel's ID, name and avatar links, but not YouTube's description excerpt (it is cut with "..."). An offline channel page then shows no description until it loads once. OK?" Answer: "Keep no excerpt (Recommended)".

After the maintainer subagent's plan check found that question 4's premise didn't hold on main, the maintainer was asked again on November 23, 2026, in the same dialog:

5. "Correction to my earlier question: on main today, opening a channel page does NOT refresh a kept channel's description. That arrives only when #49's channel-page save (its 2nd PR) or #52's refresh merges. So a channel subscribed from search shows no description on an offline channel page until one of those lands and the page loads once. Still keep no excerpt?" Answer: "Keep no excerpt (Recommended)".

After the build, on November 23, 2026, in the same dialog:

6. "Channel results (#222): TalkBack isn't on the emulator images, so I couldn't listen to it. The spec says a screen reader hears each row's button as "Subscribe to (channel)" or "Subscribed to (channel)", and the new state once after a tap. Everything else was checked by hand on Android 10 and 17, split screen included. How should we handle the TalkBack check?" Answer: "Merge, I'll listen later (Recommended)".
