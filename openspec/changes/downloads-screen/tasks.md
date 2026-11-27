# Tasks

One pull request, "closes #55". The archive commit goes last, before merge.

## 1. The data: list and remove

- [ ] 1.1 Add `Download(video, status, requestedAt, finishedAt)` to `:core:model` with KDoc saying its video is user data; verify `./gradlew :core:model:test` passes
- [ ] 1.2 Add `DownloadDao.remove(videoId): DownloadEntity?` as a `@Transaction` that reads and deletes, and `folders(): List<String>`, `folderOf(videoId)` and `isFolderOwned(folder)`, with the KDoc rule that no write may touch a DOWNLOADED row because its `updated_at` is its finish time, and DAO tests in `:core:database`'s androidTest: remove returns the row as it was and deletes it, returns null for a missing row, a claim after removing the only waiting row finds nothing, `downloads()` emits without the row, `folders()` lists every row's folder, and a pause and a request leave a DOWNLOADED row's `updated_at` as it was; verify `ANDROID_SERIAL=<serial> ./gradlew :core:database:connectedDebugAndroidTest` passes on an Android 10 emulator and `git status core/database/schemas` is clean
- [ ] 1.3 Add `DownloadFiles.folders()`, the `@DownloadFilesDispatcher` qualifier provided as Dispatchers.IO, `downloads()` and `remove(videoId)` to DownloadsRepository and RoomDownloadsRepository, and `folders()`, `folderOf(videoId)` and `isFolderOwned(folder)` to DownloadQueue and RoomDownloadQueue, and the new functions in every fake of both interfaces; tests: `downloads()` maps every state with the summary and `finishedAt` only for DOWNLOADED, `remove` deletes the folder for WAITING, PAUSED, FAILED and DOWNLOADED rows and not for DOWNLOADING, does nothing for a missing row, and finishes after its caller is cancelled, `DownloadFiles.folders()` lists only directories; verify `./gradlew :core:data:testDebugUnitTest` passes, and that each remove test fails when its line is removed
- [ ] 1.4 In DownloadRunner, end an attempt also when the video's row names another folder, delete the attempt's folder after it ends when no row names it, and sweep folders that no row names after `resetDownloading` and before the first claim, logging only a count; RunnerTest cases: a row removed mid-attempt stops it, its folder is deleted after the attempt ended (the fake writer records the order), and the next row starts; a row removed as its attempt finishes has its folder deleted; a remove and a new download of the same video during the attempt stop it, delete the old folder and keep the new one; the sweep deletes unnamed folders, keeps named ones, and runs before the first claim; verify `./gradlew :core:download:testDebugUnitTest` passes, and that each test fails when its line is removed
- [ ] 1.5 Update docs/architecture.md's downloads section (the repository's new functions, removing, the sweep, the #55 line in "What later tasks add") and ADR 0010 only if a decision it records changes; verify each names only functions that exist

## 2. Shared UI

- [ ] 2.1 Add the pure `fileSize(bytes)` to `:core:ui` with `fileSizeText(bytes)` and `fileSizeOfText(done, total)` and their string resources, unless #57 already landed one, then reuse that; unit tests for 0, 999 kB, 1 MB, 9.95 MB, 10 MB, 344 of 820 MB and 1.2 GB; verify `./gradlew :core:ui:testDebugUnitTest` passes
- [ ] 2.2 Add the optional `supporting` slot to VideoRowItem, under the meta line inside the merged node; verify `./gradlew :core:ui:testDebugScreenshotTestDefaultTestSuite :feature:inbox-ui:testDebugScreenshotTestDefaultTestSuite` pass with unchanged references
- [ ] 2.3 Add `ic_download_for_offline` and `ic_download_for_offline_filled` to `:core:designsystem` from Material Symbols Rounded, with the same header as the other icons; verify `./gradlew :core:designsystem:lint` passes

## 3. The Downloads logic

- [ ] 3.1 Create `:feature:downloads-logic` (dewfall.jvm.library, api `:core:mvi` and `:core:model`) with DownloadsState, DownloadsList, DownloadsIntent, DownloadsEffect and DownloadsReducer, include it in settings.gradle.kts, and add DownloadsReducerTest (Loading at first, NoDownloads for an empty list, the split by state, queue order kept for "Downloading", newest finished first for "On this phone" with ties in queue order, Unreadable for a failed read and a read after it, Removed giving ShowRemoved, Pause, Resume and Remove changing nothing); verify `./gradlew :feature:downloads-logic:test` passes

## 4. The Downloads screen

- [ ] 4.1 Create `:feature:downloads-ui` (library, compose, screenshots, hilt) with DownloadsViewModel, DownloadsRoute, the public `Downloads(onOpenVideo, onOpenSearch)`, DownloadsScreen with the top bar, the two sections, the rows, their buttons, the progress bar, the empty state, the Unreadable message and the "Download removed" snackbar, string resources, and previews of every row state; include it in settings.gradle.kts; add DownloadsViewModelTest with a fake repository and Dispatchers.setMain (a read, an update, a read that throws and a later read, reading only while collected, Pause, Resume and Remove calling the repository, a write that throws leaving the state, ShowRemoved only after a finished remove returns and never for a cancel); verify `./gradlew :feature:downloads-ui:testDebugUnitTest` passes
- [ ] 4.2 Add screenshot tests: the list with every row state (light, dark, large font, dynamic light and dark), the empty state and the Unreadable message (light, dark, large font, dynamic light); record them and look at every image against the design's Downloads frames; verify `./gradlew :feature:downloads-ui:testDebugScreenshotTestDefaultTestSuite` and `python3 scripts/scan_screenshot_tests.py` pass

## 5. Navigation

- [ ] 5.1 Add DownloadsKey and Destination.DOWNLOADS, turn AppNavigation's two stacks into one per destination, and include DownloadsKey in BackStack.kt's first-key rule and `showsNavigationBar`; AppNavigationTest and BackStackTest cases for three destinations (switching keeps each stack, reselecting pops Downloads to its first key, back from DownloadsKey selects Inbox, close never removes a first key, the bar's and the mini player's visibility, a double tap on a row opens one video screen); verify `./gradlew :app:testDebugUnitTest` passes
- [ ] 5.2 Add the Downloads entry and bar item to DewfallNavigation with the new glyphs and `:app`'s label string; verify `./gradlew :app:assembleDebug :app:lint` passes
- [ ] 5.3 Add emulator tests with the in-memory database: DownloadsScreenTest (the bar opens Downloads with "Downloads" selected; back returns to the inbox; a row opens the video screen; Search YouTube from Downloads goes back to it; tapping the row of the video that plays in the mini player opens it playing on; "Remove download" on a finished row deletes the row and its folder and shows "Download removed"; "Cancel" on a paused row deletes its folder and shows no message; each row's text is one node and each button its own, with no click action on the text node apart from opening the video; the activity's saved state with the list showing holds no listed video's ID or title); verify `ANDROID_SERIAL=<serial> ./gradlew :app:connectedDebugAndroidTest` passes on an Android 10 emulator and an Android 17 one, with the Mac's one-emulator limit
- [ ] 5.4 Update docs/architecture.md (modules and graph, `:app`'s row, navigation for three destinations, the Downloads screen), docs/testing.md (emulator tests, "Checking the Downloads screen by hand"), and AGENTS.md (where things are, the module list, the navigation bar's destinations); verify each names only files and commands that exist

## 6. Checks

- [ ] 6.1 Run the Downloads screen's hand checks from docs/testing.md on one emulator: a real download paused, resumed and cancelled mid-way, with `ls` of the downloads folder before and after, a waiting download paused, a finished one removed, the largest font size, a wide window, and the logs holding no ID, title or file name; if YouTube blocks this network, ask the maintainer whether the real-video steps can wait in #258; record what they found in the pull request

## Workflow follow-up

- Archive the change with /opsx:archive as the pull request's last commit, then run the reviewer again on that commit.
