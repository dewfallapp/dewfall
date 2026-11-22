# Proposal

## Why

Phase 5's goal is that opening the app shows new videos from your channels. That needs a list of channels the user chose. Issue [#47](https://github.com/dewfallapp/dewfall/issues/47) is the first way to choose one: a channel page with Subscribe and Unsubscribe, as the design brief's "Channel page. Latest uploads, subscribe and unsubscribe." asks. Today the video screen shows the channel, but its spec says that tapping it does nothing "until channel pages exist". The YouTube layer can already read a channel and its latest uploads (#35), and #48, merged in pull request #226, added the database tables that keep subscriptions.

## What Changes

- **A channel page.** It follows the "Channel page, Kettle and Crumb" frames in docs/design/: a back arrow, the channel's emblem with its initial, its name and description, a card that says whether the user is subscribed, with the one action that changes it, then the channel's latest uploads.
- **Opening it.** Tapping the channel on the video screen opens the channel page. The video goes on in the mini player, as when the user minimizes it, so the back gesture on the channel page returns to the screen the video was opened from.
- **Subscribe and Unsubscribe.** Subscribe takes effect at once and keeps the channel on the phone. Unsubscribe asks first, in a dialog, then forgets the subscription. Both work offline once the page shows the channel. A screen reader hears the card's new state, such as "Subscribed to Kettle and Crumb".
- **Latest uploads.** The page lists the channel's latest long-form videos and live streams, without Shorts, newest first, in the same rows as Search YouTube's results but without the channel's name. Tapping a row opens its video screen.
- **Loading, failures and an empty channel.** The page says while it loads, says why it failed with "Try again" where trying again can help, and says when a channel has no videos. A channel the user subscribed to still shows its name, emblem and Unsubscribe when YouTube can't be reached.
- **What the page leaves out for now.** The design's download buttons, downloaded and watched marks and subtitle marks come with Phases 6 to 8. The top row has no overflow button, because its menu would have no item yet. The "Player" frame's "Subscribed" under the video screen's channel isn't part of this change either. Real avatar and thumbnail images wait for #176, so the page shows the initial and plain boxes, as the frames do. The navigation bar comes with the inbox (#49) and the Channels screen (#115).
- **A shared video row.** Search YouTube's video row, its length label and its time-ago text, and the video screen's channel emblem and initial, move to shared modules, so both features and the channel page use one copy. This is a refactor with no change that users see, in a pull request of its own before the channel page.
- **Storage.** #48's SubscriptionsRepository keeps subscriptions in its Room tables, and a new repository in `:core:data` reads a channel's latest uploads through the YouTube layer. The page stores no videos: filling the inbox's videos is #49's and #52's work.

## Capabilities

### New Capabilities

None.

### Modified Capabilities

- `subscriptions`: #48 created it, for keeping channels and their videos on the phone. It gains the channel page, and subscribing and unsubscribing from it, as added requirements, and its Purpose widens to cover them. #115's Channels screen and #51's Takeout import add their own requirements to it.
- `player`: "The video screen shows the channel" changes. Tapping the channel opens its channel page and minimizes the video.

## Impact

- New modules `:feature:channel-page-logic`, plain Kotlin/JVM, and `:feature:channel-page-ui`, an Android library. `:app` depends on the `-ui` module and adds a ChannelKey to its back stack.
- New module `:core:ui`, an Android library, which docs/architecture.md already plans for "the video row that the inbox shares with search". The length label and the channel's initial, which need no Android, move to `:core:model`. The channel emblem, which takes plain values, moves to `:core:designsystem`.
- `:core:data` gains ChannelUploadsRepository. SubscriptionsRepository and `:core:database` are #48's, and this change only uses them.
- `:feature:player-logic` keeps the channel's ID in VideoPage, and `PlayerRoute` takes an `onOpenChannel` action. The video screen's channel box gains the design's chevron, so its screenshots are recorded again.
- No change to the YouTube layer, its saved responses or the daily check. Network: the latest-uploads requests the layer already sends.
- Privacy: subscriptions are user data on the phone only, in the app's database. Nothing new is logged or sent. The channel ID of an open channel page is in the state Android saves for a process it ends, as a video's ID already is.
- No new dependency.
- Docs: docs/architecture.md and AGENTS.md's module list and feature row, as tasks.md lists. docs/roadmap.md already lists #222, the task the maintainer added, since pull request #223.
