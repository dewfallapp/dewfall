# Proposal

## Why

Phase 6's second task, [#55](https://github.com/dewfallapp/dewfall/issues/55), is a Downloads screen with progress, pause and cancel ([roadmap](../../../docs/roadmap.md#phase-6-manual-downloads)). #54 built the download engine and the buttons on the video screen and in the inbox, but the user can't see every download in one place, can't pause a download that waits, can't cancel one, and can't remove a finished one to free space. The design brief puts all of that on one screen: "The storage meter at the top, then downloads with progress, pause, cancel and remove" ([brief](../../../docs/design/brief.md)), and the design's "Downloads" frame shows it ([screens](../../../docs/design/screens.md#downloads-and-storage)).

## What Changes

- **A Downloads screen**, the navigation bar's third destination, after Inbox and Channels, with the `download_for_offline` glyph that the design system's NavigationBar names. Settings joins the bar later, when its screen exists.
- **A top bar** with "Dewfall", "Downloads" under it, and a search button that opens Search YouTube, as on the inbox and the Channels screen. No menu button.
- **A "Downloading" section** with every download that isn't finished, in the order the user asked for them, in one card. Each row shows the video's thumbnail and length, its title and channel name, a progress bar once the size is known, and a status line:
  - downloading: "42%, 344 of 820 MB", as the design shows, or "Downloading" while the size is unknown, with "Pause" and "Cancel";
  - waiting: "Waiting to download", with "Pause" and "Cancel". Pausing a waiting download is new: until now only a running one could be paused;
  - paused: "Paused, 42%", with "Resume" and "Cancel";
  - failed: the error glyph and "Download failed" or "Not enough space on this phone", as the downloads spec words them, with "Try again" and "Cancel".
- **An "On this phone" section** with every finished download, the most recently finished first, in one card. Each row shows the thumbnail and length, the title, the channel name and the size, such as "940 MB", and a "Remove download" button.
- **Cancel** stops the download if it runs, and deletes the download and every file it has so far. **Remove download** deletes a finished download's files, and the message "Download removed" shows, as the design system's content rules pair the two words. Neither asks first. The video then shows the "Download" button again everywhere.
- **Tapping a row** opens the video screen, as every video row does. Until #56, the video streams.
- **An empty state** when there are no downloads: the download glyph in a round disc, "No downloads yet", then "Download a video from its screen or your inbox to keep it on this phone.", with no button and no suggestions. And **a message when the phone can't read its data**, as the inbox and the Channels screen have: "Your downloads can't be shown" and "Dewfall couldn't read what it keeps on this phone. Restarting the app may help."
- **The storage meter is #57's.** It goes directly under the top bar, and whichever of #55 and #57 lands second puts it there.
- **The navigation changes** that a third destination needs: the bar shows on the Downloads screen, Downloads keeps its own history, the back gesture on the Downloads screen returns to the inbox, and Search YouTube opens from the Downloads screen and goes back to it.
- **The repository** gets the list of downloads with their saved summaries, and `remove(videoId)`. The engine deletes the files of a download that is removed while it runs, once its writer has stopped, and a run deletes any folder that no download owns, such as one left by a process that ended mid-remove.

"The maintainer's answers" below records the choices among these that the maintainer made: neither Cancel nor Remove download asks first, "Resume", the empty state's words, and the search button and row taps.

## Capabilities

### New Capabilities

None.

### Modified Capabilities

- `downloads`: the Downloads screen, its sections, rows, buttons and messages; pausing a waiting download; cancelling and removing a download, and what happens to its files.
- `inbox`: the navigation bar gets the Downloads destination, which shows the bar, keeps its own history, goes back to the inbox, and keeps nothing private in the state Android saves beyond what the player, subscriptions and youtube-search specs allow.
- `list-images`: the Downloads screen's rows show thumbnails, as the other video rows do.
- `youtube-search`: Search YouTube also opens from the Downloads screen, its keyboard opens on each open from it, and its back arrow returns to it.

## Impact

- New modules `:feature:downloads-logic` (Kotlin/JVM: DownloadsState, DownloadsIntent, DownloadsEffect, the reducer and its tests, and the pure byte formatting) and `:feature:downloads-ui` (Android: the public `Downloads` composable, the screen, its previews and screenshot tests, DownloadsViewModel and its tests, string resources). settings.gradle.kts includes them.
- `:core:model`: Download, a video's summary with its download status and the times that order the sections.
- `:core:database`: DownloadDao gets `remove(videoId)`, which deletes a row and returns it as it was, and the list of folders the rows own, with DAO tests. The schema doesn't change.
- `:core:data`: DownloadsRepository gets `downloads()` and `remove(videoId)`; DownloadQueue gets the folders, with tests.
- `:core:download`: DownloadRunner deletes a removed download's folder after its writer stops, and deletes folders that no row owns at the start of a run, with tests.
- `:core:designsystem`: the `download_for_offline` glyph, outlined and filled, from Material Symbols Rounded under the Apache License 2.0 like the others.
- `:app`: the Downloads destination, its back stack and DownloadsKey, with unit tests, and emulator tests for the bar, cancel and remove.
- Docs: docs/architecture.md (modules and graph, navigation, downloads), docs/testing.md (emulator tests, hand checks), AGENTS.md (where things are, modules), README files of the new modules if the others have them.
- No new dependency, no schema change, no new network request, and nothing new is logged or leaves the phone.
- One pull request that closes #55.

## The maintainer's answers

On November 27, 2026, in this change's Claude Code session, in its question dialog, the maintainer answered four questions.

1. "#55 Downloads screen: the design has "Cancel" on running downloads and "Remove download" on finished ones, with no dialog. The design system pairs "Remove download" with the message "Download removed". Should either ask first?" Answer: "Neither asks (Recommended)". That option said: "Cancel stops the download and deletes its partial files at once. Remove download deletes the files at once and shows "Download removed" (no Undo). Matches the design."
2. "#55 Downloads screen: a paused download's row needs a button that goes on downloading (the design only shows a running one). Which word?" Answer: "Resume (Recommended)". That option said: "Row says "Paused, 42%" with "Resume" and "Cancel". A failed row says "Download failed" with "Try again" and "Cancel". A waiting row says "Waiting to download" with "Pause" and "Cancel"."
3. "#55 Downloads screen has no empty-state frame in the design. What should it say with no downloads?" Answer: "No downloads yet (Recommended)". That option said: "Download glyph in a round disc, "No downloads yet", then "Download a video from its screen or your inbox to keep it on this phone." No button, no suggestions."
4. "#55: the design puts a search button on the Downloads top bar (opens Search YouTube, as on Inbox and Channels), and rows open the video screen when tapped. Keep both?" Answer: "Keep both (Recommended)". That option said: "Search button opens Search YouTube and its back returns to Downloads. Tapping a row (not its buttons) opens the video screen; until #56 it streams."
