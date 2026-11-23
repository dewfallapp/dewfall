# Design

## Context

See proposal.md for why. What this change builds on, on main at e395eb5, after #235 merged:

- **The YouTube layer.** `SearchRemoteDataSource.search(query)` returns `SearchResults(channels, videos)` from one request. `searchResultsOf` in `NewPipeSearch.kt` maps each `ChannelInfoItem` to a `Channel` (ID, name, avatars, description), dropping one with no ID or name. The description is YouTube's `descriptionSnippet`, an excerpt that YouTube cuts with "...": the saved SEARCH_NASA response holds 5 channels, each with such an excerpt, and SearchReplayTest already checks that the layer reads channels with avatars and drops none. So the layer, its saved responses and the daily check don't change.
- **The repository.** `LayerYouTubeSearchRepository` passes the layer's result through unchanged. Its KDoc says Phase 5 may give it work "such as marking the channels that the user follows".
- **Search YouTube.** The reducer turns a successful result into `ResultsArea.Results(request, rows)` of `VideoRow`, leaving out Shorts and repeated IDs, or `NoResults` when no row is left, so channels are dropped today. `SearchResultsArea` draws one LazyColumn with a "Videos" heading and one rounded group of rows. The screen's ViewModel takes only `YouTubeSearchRepository`.
- **Subscriptions (#47, #48).** `SubscriptionsRepository` in `:core:data` has `subscribedChannels(): Flow<List<SubscribedChannel>>`, `subscribe(channel)`, which keeps the first subscription when there is one, and `unsubscribe(channelId)`. Both writes finish in the application scope when the caller is cancelled. The channel page's card, `SubscriptionCard.kt`, holds the page's `UnsubscribeDialog`, with strings `channel_page_unsubscribe_title`, `channel_page_unsubscribe_body`, `channel_page_unsubscribe` and `channel_page_cancel`. The card itself keeps using `channel_page_unsubscribe`.
- **Shared UI.** `:core:ui` holds the video row and its strings, prefixed `core_ui_`. `:core:designsystem` holds `ChannelEmblem(initial, size, textStyle)`, and `:core:model` holds `initialOf(name)`. The design has `ic_add` and `ic_check` already.
- **Navigation.** `:app`'s `openChannel(from: PlayerKey, channelId)` opens a channel page only while that video screen is on top, and removes it. `YouTubeSearch(onOpenVideo, modifier)` is the search screen's one public composable.
- **Saving uploads.** On main, nothing outside tests calls `StoredVideosRepository.saveUploads`, which #235 gave a read, `inboxVideos()`, too: ChannelViewModel only reads `subscribedChannel` and writes `subscribe` and `unsubscribe`. So no screen yet refreshes a kept channel's details ("Saving a channel's uploads refreshes its details" in the subscriptions spec) or saves its videos. #49's second pull request makes the channel page save a subscribed channel's uploads whenever a load finishes, and #52's refresh saves every subscribed channel's.
- **The inbox (#49).** The inbox change, openspec/changes/inbox/, which #235 merged and which stays open until #49's third pull request, says in its design.md that a Subscribe from #222's channel results saves no videos until #52's refresh or a visit to the channel's page. Its later pull requests change `:app`'s back stack (an InboxKey under SearchKey) and give `YouTubeSearch` an `onBack` action. This change touches the same files, so whichever merges second rebases.

## Goals / Non-Goals

**Goals:**

- The requirements in specs/youtube-search/spec.md and the changed one in specs/subscriptions/spec.md, on Android 10 to 17, in light, dark and dynamic color, at a font scale of 2, and on phones, tablets and split screen.
- The subscribed state of each row always comes from the database, so it never shows a guess and follows a change made on the channel page.

**Non-Goals:**

- Fetching a channel's uploads when the user subscribes from search. #49's plan leaves that to #52's refresh and to the channel page.
- Following a channel that YouTube sends on to another. Search gives each channel's current ID. A subscription kept under an older ID shows "Subscribe" in search, as #52 decides how to follow moved channels.
- Avatar images (#176). Rows show the emblem with the initial, as the frames do.
- More than the first page of results.

## Decisions

### State in the reducer, writes in the ViewModel

`:feature:youtube-search-logic` gains:

- `ChannelRow(id: ChannelId, name: String, excerpt: String, avatars: List<Thumbnail>)`. The avatar links are kept only so Subscribe can store them, as the channel page does.
- `ResultsArea.Results(request, channels: List<ChannelRow>, videos: List<VideoRow>)`. `rows` is renamed `videos`. The reducer keeps channels in YouTube's order and drops a repeated ID, as it does for videos. `NoResults` comes only when both lists are empty.
- In `YouTubeSearchState`: `subscribedIds: Set<ChannelId>?`, null until the database first answers, and `confirmingUnsubscribe: ChannelId?`, the channel whose dialog is open. Both sit outside `results`, so a new search keeps what the database said. `Submit` and `Clear` close the dialog, though the dialog's scrim blocks both while it is open.
- Intents: `SubscriptionsRead(ids)`, `Subscribe(channelId)`, `UnsubscribeTapped(channelId)`, `UnsubscribeConfirmed(channelId)` and `UnsubscribeCancelled`. `Subscribe` changes no state. `UnsubscribeTapped` opens the dialog only while the results hold that channel and `subscribedIds` holds its ID. `UnsubscribeConfirmed` and `UnsubscribeCancelled` close it. `UnsubscribeConfirmed` carries the ID because `MviViewModel.dispatch` runs side effects after the reducer, when the dialog's ID is already gone.

This is the channel page's rule (`ChannelReducer`'s KDoc): a tap writes, and the button changes when the database answers. Alternatives: an optimistic flag in the state would show a guess, and the spec asks the button to follow the database whichever screen changed it.

The ViewModel, on creation, collects `subscriptions.subscribedChannels()` mapped to a set of IDs, with `distinctUntilChanged`, and dispatches `SubscriptionsRead` for each set, for its whole life in `viewModelScope`. The query reads one small table, and Room reruns it only when `channels` changes. Alternatives: `isSubscribed(id)` per row would start a Flow per channel and restart them on each search. Marking the results in the repository, as its KDoc suggested, would make `search` a Flow that outlives the request, and its KDoc changes to say the ViewModel marks them.

On `Subscribe(id)`, the ViewModel finds the row in the current results, and only when `subscribedIds` is known and lacks the ID, calls `subscribe(Channel(id, name, avatars, description = ""))`. The excerpt is left out, because the channel page shows a kept channel's description as the whole description when YouTube can't be reached, and "Saving a channel's uploads refreshes its details" fills it in once something saves the channel's uploads: the channel page after #49's second pull request, or #52's refresh. Until then it stays empty. The maintainer chose this knowing that (proposal.md, answer 5). On `UnsubscribeConfirmed(id)` it calls `unsubscribe(id)`. Each write runs in `viewModelScope.launch`, and the repository finishes it in the application scope if the user leaves the screen. The ViewModel takes `SubscriptionsRepository` beside `YouTubeSearchRepository`, and no dispatcher.

### The channel row

After the "Search YouTube, results for sourdough" frames: a "Channels" heading in `titleSmall` and `onSurfaceVariant`, like "Videos", then one rounded group on `surfaceContainerLow`, built with `rowGroupShape` from `:core:ui` as the video rows are, then "Videos". Each row: `ChannelEmblem` at 36dp, the name in `titleSmall`, which wraps, the excerpt in `bodySmall` and `onSurfaceVariant` with at most two lines and an ellipsis, with each run of spaces, tabs and line breaks shown as one space, because the saved NASA answer's excerpts hold such runs and two lines leave no room to waste, and the button at the end, 8dp apart, with 8dp padding. The row is clickable, with the click label "Open channel", and its name is a heading for screen readers, as the frames' `h3` is.

At a large font the button would squeeze the name into a sliver. So the row is a `FlowRow` of two items: the emblem with the text, which takes the remaining width but at least 160dp, and the button, which wraps under the text, at the end, when that minimum doesn't fit. The LargeFont screenshot shows it. Alternatives: switching layouts on `fontScale` misses narrow split-screen windows at the default size; `BoxWithConstraints` in every list item adds a subcomposition per row.

### The compact button

One Material 3 `Button` whose colors and border change with the state: `filledTonalButtonColors` with no border for "Subscribe", `outlinedButtonColors` with a 1dp `outline` border for "Subscribed", as the SubscriptionCard README's "In lists" asks. An 18dp `ic_add` or `ic_check` glyph, hidden from screen readers, then the word. Its semantics replace the children's: the content description "Subscribe to" or "Subscribed to" with the channel's name, the click label "Subscribe" or "Unsubscribe", and a polite live region. One composable in both states keeps one semantics node, so when the description changes, TalkBack says the new one once, as the channel page's status line does. A configuration change makes a new node, which isn't a change, so nothing is said again. The button shows only when `subscribedIds` is known. Material's minimum touch target keeps it 48dp tall to touch.

### One Unsubscribe dialog

`UnsubscribeDialog(channelName, onConfirm, onDismiss)` moves from `:feature:channel-page-ui` to `:core:ui`, with its strings renamed `core_ui_unsubscribe_title`, `core_ui_unsubscribe_body`, `core_ui_unsubscribe` and `core_ui_cancel`, and the same words. The channel page passes its intents as the two lambdas, and its dialog screenshot must pass with its reference image unchanged. `channel_page_unsubscribe_title`, `_body` and `channel_page_cancel` are removed. `channel_page_unsubscribe` stays for the card. Alternatives: a second copy in the search module would let the two dialogs drift apart, and feature modules can't depend on each other.

### Opening a channel

`openChannel(from: PlayerKey, channelId)` becomes `openChannel(from: NavKey, channelId)`: it opens nothing unless `from` is on top, removes `from` only when it is a video screen, so the video goes on in the mini player as today, and opens no second page for the same ID right under it. Search YouTube passes its own key, so a second tap finds the channel page on top and opens nothing. `YouTubeSearch` gains `onOpenChannel: (ChannelId) -> Unit`, and `DewfallNavigation` wires it. BackStackTest gains cases for opening from search, a double tap, and the existing video cases kept.

### Words

| Where | Words |
|---|---|
| Line under the title | "Videos to stream and channels to follow" (the frame's) |
| Before the first search | "Find a video or a channel", then "Videos you open from here stream right away. Subscribe to a channel to see its new videos in your inbox." The frame's second sentence speaks of overnight downloads, which come with Phase 7, as on the channel page and the inbox |
| Headings | "Channels", "Videos" |
| Button | "Subscribe", "Subscribed"; spoken "Subscribe to %1$s", "Subscribed to %1$s" |
| Row's click label | "Open channel" |
| No results | "Nothing on YouTube matches “%1$s”. Check the spelling, or try fewer words." (the frame's) |
| Dialog | Unchanged from the channel page |

### Tests

- `YouTubeSearchReducerTest`: channels kept in order and once, only channels, only Shorts and nothing giving NoResults, the subscribed set, the dialog opening only for a subscribed channel in the results, closing on confirm, cancel, a new search and clearing, and Subscribe changing nothing.
- `YouTubeSearchViewModelTest`, with a fake SubscriptionsRepository: the read set reaches the state and follows changes, Subscribe stores the row's ID, name and avatars with no description, Subscribe does nothing for a subscribed channel or before the first read, and Unsubscribe removes the confirmed ID.
- Screenshot tests: results with channels in light, dark, large font, tablet, dynamic light and dark (the existing set), only channels in light, the dialog in dark, before the first search and no results with their new words recorded again. Every changed image is looked at.
- `BackStackTest` as above.
- A new emulator test, `SearchChannelTest`: FakeYouTubeModule's search also answers with the test channel and an excerpt that only the search row shows. It searches, taps "Subscribe" in the channel row, waits for "Subscribed" and checks the repository, then unsubscribes through the dialog. It opens the channel page from the row's excerpt, subscribes there, goes back, and finds "Subscribed" in the row. Last, with the page open again, it asks the activity to save its state into a Bundle, checks that the Bundle holds the saved back stack, so an empty Bundle can't pass, and checks that no string in it holds the channel's name, excerpt or description, or the test videos' IDs or titles, while it does hold the channel's ID, as "Search text stays private" says. Saving moves the activity's lifecycle back to CREATED, so it comes last. The inbox change's task 6.2 plans the same check in InboxFromChannelTest. It unsubscribes the test channel before and after, as ChannelSubscribeTest does.

## Risks / Trade-offs

- [#49's later pull requests and this one both change `BackStack.kt`, `DewfallNavigation.kt`, `YouTubeSearch` and its screenshots.] → Whichever merges second rebases, keeps both actions, and records the screenshots again. The generalized `openChannel` works from any key on top, the inbox's too, and whichever merges second adds a BackStackTest case for opening a channel from the inbox.
- [A subscription from search keeps no description, so an offline channel page shows none. If this merges before #49's second pull request and #52, nothing fills it in yet, even after the page loads online.] → The spec says so, the maintainer accepted it (answer 5), and it's better than showing a cut excerpt as the description. The online page always shows the description YouTube sends.
- [A user subscribed under an ID that YouTube now sends on to another channel sees "Subscribe" on that channel in search, and tapping it keeps a second subscription under the new ID, so the inbox and the Channels screen count the channel twice.] → It needs a moved channel and an old subscription, and the user can unsubscribe the old one on its page, which covers both IDs. #52 decides how to follow moved channels, and it should merge such pairs. The spec's "A channel that moved" scenario in search says so.
- [Subscribing from search fills the inbox only after the channel's page opens or #52 refreshes.] → #49's plan chose it, and the inbox says "You're all caught up" in the meantime, which is true.
- [TalkBack may not announce a live region change on a button it has focus on.] → The channel page's status line works this way already. The hand checks include TalkBack on an emulator, and the pull request says whether it was heard.
- [YouTube may list many channels for a broad search.] → The page shows what YouTube's first page gives; the NASA recording has 5, and the videos follow under their heading.
