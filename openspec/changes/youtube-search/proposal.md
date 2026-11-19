# Proposal

## Why

Phase 4's goal is that a user can search for any video and watch it. Today the app opens on a placeholder screen. The YouTube layer can already search, since Phase 3 put it behind Dewfall's own interfaces ([#34](https://github.com/dewfallapp/dewfall/issues/34)), but no screen calls it. This change adds the Search YouTube screen, issue [#40](https://github.com/dewfallapp/dewfall/issues/40), the first real screen and the first one built with MVI. The video screen, [#41](https://github.com/dewfallapp/dewfall/issues/41), opens from its results. As the first screen, it also sets the rule for large screens and resized windows that later screens follow, as the maintainer's comment on #40 asks.

## What Changes

- **The app opens on Search YouTube.** It replaces the placeholder screen, until Phase 5 makes the inbox the home screen and opens search from its top bar. The screen follows the "Search YouTube" frames in docs/design/, without their back arrow, because nothing comes before it yet. The keyboard opens the first time the screen opens, as the design system's search field says, and closes when a search is sent.
- **The search field** sends the search when the user presses the on-screen keyboard's search key or Enter on a hardware keyboard, not on every letter, so each search is one request to YouTube. The text never holds a line break. A clear button empties the field and shows the screen as it was before the search.
- **Before the first search** the screen says what it finds and that videos opened from it stream right away.
- **While a search runs** the screen shows that it is loading. A new search replaces the one that is running.
- **Results** show YouTube's videos in YouTube's order, without Shorts, as the design system's video row says, if the maintainer agrees. Each row shows a thumbnail box with the length, or a "Live" or "Upcoming" label instead, the title, the channel's name and when it was published, such as "7h ago" or "3 weeks ago", counted by the calendar the way YouTube counts.
- **No results** says that the search found no videos, and names the search text, as the design's "Search YouTube, no results" frame does.
- **Failures** say that YouTube can't be reached, for NETWORK, or that YouTube isn't loading, for the other categories, with "Try again", as docs/architecture.md plans. Telling "offline" apart comes with the first screen that needs it.
- **Large screens and resized windows.** Phones stay in portrait, as the brief says. Everywhere else, including tablets, unfolded foldables and split screen, the content sits in one centered column of readable width. docs/design/brief.md records the rule.
- **Phase 4 leaves out three parts of the design.** Channel results, with their Subscribe buttons, come with subscriptions in Phase 5. Real thumbnail images wait for the maintainer's decision on an image library, so each row shows a plain box, as the design frames do. The downloaded and subtitle marks come with Phases 6 and 8.
- **Tapping a video** opens the video screen once #41's change adds it. Until then a row has no action.
- **The first repository.** `:core:data` gains a search repository that the screen's ViewModel calls, as docs/architecture.md's "Reading from YouTube" describes.

## Capabilities

### New Capabilities

- `youtube-search`: searching YouTube from the app, and how the Search YouTube screen shows its state, results, empty results and failures. Phase 5 adds channel results to it, and Phase 8's subtitle search has its own area, transcript-search. A new area changes ADR 0004's list, so it waits for the maintainer.

### Modified Capabilities

None. No living spec exists yet.

## Impact

- New modules `:feature:youtube-search-logic`, plain Kotlin/JVM, and `:feature:youtube-search-ui`, an Android library. `:app` depends on the `-ui` module, and DewfallApp shows the screen instead of the placeholder. MainActivity asks for portrait.
- `:core:data` gains its first repository. It sees `:core:youtube`, and features see only `:core:data` and `:core:model`.
- No change to the YouTube layer, its saved responses or the daily check.
- Network: the same search requests the layer already sends. Nothing new is logged: no search text, title or ID, as ADR 0007 requires.
- Dependencies: getting a Hilt ViewModel inside Compose needs `androidx.lifecycle:lifecycle-viewmodel-compose` 2.11.0, the catalog's Lifecycle version, under the Apache License 2.0. It is a new artifact, so it waits for the maintainer. Without it, MainActivity gets the ViewModel with `by viewModels()`, and the ViewModel becomes public.
- Docs: docs/architecture.md, AGENTS.md's module list and table, docs/design/brief.md's Rules, ADR 0004 and openspec/config.yaml, as tasks.md lists line by line.
- Issues: a new issue tracks thumbnail images, so the question isn't lost.
