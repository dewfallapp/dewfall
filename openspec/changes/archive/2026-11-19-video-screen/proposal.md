# Proposal

## Why

Phase 4's goal is that a user can search for any video and watch it. Search YouTube shows results since [#40](https://github.com/dewfallapp/dewfall/issues/40), but tapping a row does nothing, because the app has one screen and no navigation. This change adds the video screen, issue [#41](https://github.com/dewfallapp/dewfall/issues/41): it opens from a search result and shows the video's title, channel, publish time and description. It is the screen that the Media3 player joins next, in [#42](https://github.com/dewfallapp/dewfall/issues/42), so it also brings the app's navigation between screens.

## What Changes

- **Tapping a video in Search YouTube opens its video screen.** Videos, live streams and upcoming videos all open it. Going back, with the close button or the system's back gesture, shows Search YouTube as it was, with the same text and results, and sends no new search.
- **The video screen** follows the "Player" frames in docs/design/, with the parts that exist today:
  - a close button at the top, the design's down arrow
  - the title
  - "Published 7 hours ago", "Published yesterday" or "Published 3 weeks ago", or "Live now" for a live stream on air
  - the channel's name next to a round emblem with its first letter, as the design system's ChannelHeader shows a channel that has no cached image
  - the full description, which the user can select and copy
- **While the details load** the screen says so. **A failure** says why, in words for each of YouTube's error categories. "Try again" shows only when trying again can help: when YouTube can't be reached, refuses to answer, or can't be read.
- **Phase 4 leaves out the parts of the "Player" frames that later changes add.** The video area, its controls and the length come with the player in #42 and #44. The channel's "Subscribed" status, the tap that opens its channel page, and "Mark as watched" come with Phase 5. "Downloaded" comes with Phase 6, "Keep" with Phase 7, and the subtitle mark with Phase 8. The overflow menu comes when it has something to hold. The emblem shows the channel's first letter until the thumbnail issue, #176, decides how images load.
- **Navigation.** The app gets a back stack, so screens open on top of each other and back works. It survives configuration changes and process death: after the system ends the app in the background, the user comes back to the same video.
- **"Today" for a time ago.** A row whose estimated publish time falls on today's date says "Yesterday" today. It will say "Today", and the video screen will say "Published today". The time-ago count moves to a shared place, because both screens use it.
- **A second repository.** `:core:data` gains a video details repository that the screen's ViewModel calls, as the search repository does for search.

## Capabilities

### New Capabilities

- `player`: the video screen, which opens a video and shows its details, its loading state and its failures. It is one of ADR 0004's living-spec areas, and #42 to #44 add playback to it.

### Modified Capabilities

- `youtube-search`: a row now opens the video screen, and a time ago reads "Today" for the same calendar day.

## Impact

- New modules `:feature:player-logic`, plain Kotlin/JVM, and `:feature:player-ui`, an Android library. `:app` depends on the `-ui` module and holds the navigation.
- `:feature:youtube-search-ui`'s public composable takes the action that opens a video. The time-ago count moves from `:feature:youtube-search-logic` to `:core:model`, with its tests.
- `:core:data` gains its second repository. No change to the YouTube layer, its saved responses or the daily check.
- Network: opening a video sends the video details requests the layer already sends, once per screen, and again only on "Try again" or after process death. Nothing new is logged: no title, name, description or ID, as ADR 0007 requires.
- Dependencies, which the maintainer approved on November 19, 2026, with this change and the glyph, as the "The maintainer's answers" section of pull request #178's description records, all under the Apache License 2.0:
  - `androidx.navigation3:navigation3-runtime` and `navigation3-ui` 1.2.1, released on November 4, 2026. They raise `androidx.navigationevent`, which the app already ships at 1.0.0 through Activity, to 1.1.1, released on May 6, 2026.
  - `androidx.lifecycle:lifecycle-viewmodel-navigation3` 2.11.0, the catalog's Lifecycle version, released on June 17, 2026.
  - The Kotlin serialization compiler plugin, a build tool at the Kotlin version, 2.4.20, and `org.jetbrains.kotlinx:kotlinx-serialization-core` 1.11.0, the catalog's version, released on April 9, 2026. The app already ships kotlinx-serialization-core 1.7.3 through SavedState.
  - One more Material Symbols Rounded glyph, the down arrow, from the same source and commit as the three that #40 added.
- ADR 0002 gets a dated note, which the maintainer approved with this change on November 19, 2026: an action that starts in the UI, changes no state and needs no business logic, such as opening or closing a screen, goes straight to the app's navigation, without an intent or an effect. design.md explains why.
- Docs: docs/architecture.md, and AGENTS.md's module list and table and its Modules and MVI rules, as tasks.md lists line by line.
