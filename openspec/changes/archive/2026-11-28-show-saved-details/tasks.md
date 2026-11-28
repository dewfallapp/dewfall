# Tasks

One pull request covers every section, closes #262, and archives this change as its last commit. It starts after #56's PR #263 merges, from main rebased onto it.

## 0. Before building

- [x] 0.1 Rebase onto main after #263 merges; rebuild the MODIFIED requirements of specs/player/spec.md from main's openspec/specs/player/spec.md, word for word with only this change's edits; verify `openspec validate show-saved-details --strict` passes

## 1. The saved summary

- [x] 1.1 Add `savedVideo(videoId): Flow<VideoSummary?>` to DownloadsRepository and RoomDownloadsRepository, over `DownloadDao.download` and `toDownload().video`, with `distinctUntilChanged`; add it to the fakes in `:feature:inbox-ui`'s, `:feature:downloads-ui`'s and `:feature:player-ui`'s tests; add RoomDownloadsRepository tests: the row's summary in each download state, null with no row, a new value when the row is asked for and when it is removed; verify `./gradlew :core:data:testDebugUnitTest` passes

## 2. The reducer

- [x] 2.1 Add `PlayerState.savedVideo`, `PlayerIntent.SavedVideoChanged`, `DetailsArea.Saved(number, error, page)`, the settle rule after every intent, the page from a summary (no pictures, empty description, not live), `DetailsArea.page`, and make `shownDownload` and `sharesFailure` read Saved; keep the errors that show saved details in one function, NETWORK only, as answer 3 says
- [x] 2.2 Add reducer tests: failure then summary, summary then failure, each other error stays Failed, removal gives Failed with the same number, a changed summary gives a new page, "Try again" from Saved, success after Saved, a late answer while Saved, the page's fields; tests for `page`, `shownDownload` and `sharesFailure` with Saved; verify `./gradlew :feature:player-logic:test` passes, and that each rule's test fails when that rule is taken out

## 3. The ViewModel and the screen

- [x] 3.1 PlayerViewModel follows `savedVideo` and sends SavedVideoChanged, with null when the Flow fails; `describePlayback` reads `details.page`, and runs after SavedVideoChanged too; "Download" uses the saved summary while Saved shows; the route gives fullscreen `details.page`; add ViewModel tests: offline with a download gives Saved and describes the playback with the saved names, with the summary before and after the failure, a failing Flow leaves Failed, "Download" while Saved downloads the saved summary; verify `./gradlew :feature:player-ui:testDebugUnitTest` passes
- [x] 3.2 PlayerScreen draws Saved with Details and the failure's Banner in the description's place; add a preview of Saved and its screenshot wrapper in light, dark, large font and dynamic color; record the reference images with `./gradlew updateDebugScreenshotTestDefaultTestSuite` and look at each new image

## 4. Emulator tests and hand check

- [x] 4.1 Change PlayFromFilesTest's offline test to check the saved title, the channel name and the "can't be reached" message; update docs/testing.md; with the coordinating session's yes for emulator-5556, verify `ANDROID_SERIAL=emulator-5556 ./gradlew :app:connectedDebugAndroidTest` passes on Android 17 and Android 10, and that the changed assertion fails with the reducer's settle rule taken out
- [x] 4.2 Hand check on Android 17 with a real video: download it, turn on airplane mode, open it from the inbox: the saved details show with the message and "Try again"; minimize it: the mini player and the notification show the saved title and channel name; turn airplane mode off and tap "Try again": YouTube's details with the description show; logcat of the app's process holds no title. Write the results in the pull request

## 5. Docs and checks

- [x] 5.1 Update docs/architecture.md: the video screen's states, the saved summary, and "What later tasks add" for #262; add a line for #262 to Phase 6's tasks in docs/roadmap.md
- [x] 5.2 Run every check the pull request workflow runs, `./gradlew spotlessCheck detekt lint test testDebugScreenshotTestDefaultTestSuite assembleDebug` and `python3 scripts/scan_screenshot_tests.py`, then the reviewer until Ready, then `python3 scripts/pre_push_gate.py` until PASS

## Workflow follow-up

- Archive this change with /opsx:archive as the pull request's last commit, then run the reviewer again on that commit.
- Hand the pull request to the merge desk session.
