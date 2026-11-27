# Tasks

One pull request, started from main after #55's pull request merges. It closes #57 and archives this change as its last commit.

## 1. The data layer

- [x] 1.1 Add `bytesInUse(): Flow<Long>` to DownloadDao, the sum of `downloaded_bytes` over every row, 0 with none; verify `./gradlew :core:database:assembleDebug` passes and core/database/schemas/ doesn't change
- [x] 1.2 Add DAO tests to `:core:database`'s androidTest: 0 with no rows, the sum of a downloaded, a downloading, a paused, a waiting and a failed row, and the sum after a row is deleted; verify `ANDROID_SERIAL=emulator-5556 ./gradlew :core:database:connectedDebugAndroidTest` passes on Android 10
- [x] 1.3 Add `bytesInUse()` to DownloadsRepository and RoomDownloadsRepository with `distinctUntilChanged`, and to FakeDownloadDao and every fake of DownloadsRepository; add a repository test that the Flow gives the sum and skips a repeated one; verify `./gradlew :core:data:testDebugUnitTest` passes

## 2. The screen

- [x] 2.1 Add `bytesInUse: BytesInUse` (Reading, Read(bytes), Unreadable) to #55's DownloadsState, the `BytesInUseRead` and `BytesInUseUnreadable` intents and their reducer cases, with reducer tests that the first stores the bytes, the second marks them unreadable, neither changes anything else, and #55's list reads keep the bytes; verify the `-logic` module's tests pass
- [x] 2.2 Read `bytesInUse()` in #55's ViewModel only while the state has a collector (WhileSubscribed over `stateSubscriptionCount`, joined to #55's read-while-shown if it has one), sending `BytesInUseUnreadable` when the read throws; ViewModel tests: the state follows the Flow while collected, the read stops with no collector and starts again with one, a throwing Flow gives unreadable bytes without a crash, and after that failure a collector that comes back within the stop timeout starts the read again; verify the `-ui` module's unit tests pass
- [x] 2.3 Use #55's `fileSizeText` from `:core:ui` for the amount; if #55 merged without it, add it there with the rules design.md names and JVM unit tests (0, under 10 of a unit, a unit boundary, a decimal comma locale). Add the "Offline storage" string and the StorageCard composable directly under the top app bar, above the list and above #55's empty state, with `surface-container-low`, `shape-medium`, `space-md` padding, `label-medium` and `label-large`, the FlowRow that wraps, and merged semantics; verify `./gradlew lint detekt` passes
- [x] 2.4 Add previews and screenshot tests for the card with an amount, with zero and with no amount yet, and a screen preview with no downloads that shows the card, in light, dark, large font and dynamic color, record the reference images and look at each one; verify `./gradlew testDebugScreenshotTestDefaultTestSuite` and `python3 scripts/scan_screenshot_tests.py` pass
- [x] 2.5 If #55 has an emulator test of the Downloads screen, add a check that the card says the sum of a downloaded and a paused row as one node; verify `ANDROID_SERIAL=emulator-5556 ./gradlew :app:connectedDebugAndroidTest` passes on Android 10 and Android 17

## 3. Docs and checks

- [x] 3.1 docs/architecture.md: name the storage card in the Downloads screen's section and `bytesInUse()` in DownloadsRepository's list, and make the "#57, the storage indicator" line say it counts from the rows; DownloadFiles.kt's KDoc: drop "#57 adds up their sizes"; docs/testing.md: the card's hand check; ADR 0010's "#57 adds up their sizes" stays, as design.md says; verify `git grep -n '#57'` shows no other stale line
- [x] 3.2 Hand check on emulator-5556, Android 17 and Android 10: the card's amount drops after a cancel and a remove, reads as one item, and fits or wraps at the largest font; record the results in the pull request. The real-download part of the planned check moved to 3.4. Done on Android 10 and Android 17 with a finished and a paused row written to the database, because YouTube blocks this network for real downloads: 3.6 GB, then 2.5 GB after Cancel, then 0 kB after Remove download, and at font scale 2.0 the card stays on one line on both phones (the 320dp screenshot shows the wrap). DownloadsScreenTest checks the one node a screen reader reads on both
- [x] 3.3 Run every check of the pull request workflow: `./gradlew spotlessCheck detekt lint test testDebugScreenshotTestDefaultTestSuite assembleDebug` and `python3 scripts/scan_screenshot_tests.py`; verify all pass
- [ ] 3.4 Moved to #258 by the maintainer's answer 4: on Android 17, the amount grows while a real download runs, without leaving the screen, and stays the same after "Pause"

## Workflow follow-up

- Run the reviewer until it says Ready, run `python3 scripts/pre_push_gate.py` and push only on PASS, open the pull request with "closes #57", archive this change with /opsx:archive as the last commit, run the reviewer again on it, and hand the pull request to the merge desk.
