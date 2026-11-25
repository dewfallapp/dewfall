# Tasks

The whole change lands in one pull request, which closes #52.

## 1. Moving a subscription

- [x] 1.1 Add ChannelDao's `moveSubscription(fromId, to)` as a `@Transaction` function with a body, as design.md says, and check first that Room 3's KSP processor accepts it on an interface; if it doesn't, run the three calls in the repository inside the database's write transaction instead and note it in design.md; add ChannelDaoTest cases (move with no row under the new ID keeping the old time, move onto an existing subscription keeping the new row's time, no old row changing nothing); update docs/testing.md's Database tests paragraph with the new count, emulator and date; verify `ANDROID_SERIAL=<serial> ./gradlew :core:database:connectedDebugAndroidTest` passes on an Android 10 emulator and `git status core/database/schemas` is clean
- [x] 1.2 Add `SubscriptionsRepository.moveSubscription(from, to)` in the `@ApplicationScope` scope, add it to every fake of the interface (`git grep ": SubscriptionsRepository"`), and add RoomSubscriptionsRepositoryTest cases for the mapping; verify `./gradlew :core:data:testDebugUnitTest` passes

## 2. The check

- [x] 2.1 Add UploadsCheck and UploadsCheckFailure to `:core:model` with KDoc; verify `./gradlew :core:model:test` passes
- [x] 2.2 Add UploadsChecker and its implementation to `:core:data`, bound `@Singleton`, with the snapshot, the failure table with the stop after 3 BROKEN in a row, the quiet stop on a failed read or write with `running` reset in the job's completion handler, the move before the save, the skip of answered IDs, one check at a time through `compareAndSet` (a test calls `start()` twice while a check runs) and the two log lines from design.md; add UploadsCheckerTest with the cases design.md lists; verify `./gradlew :core:data:testDebugUnitTest :core:data:lint` passes
- [x] 2.3 Update docs/architecture.md: `:core:data`'s row, a section on the check (the singleton, the order, the failure table, the move), the inbox section's sentence about #52, and the database section's sentence that #52 decides how refresh follows a redirect; verify each names only classes and functions that exist

## 3. The inbox

- [x] 3.1 Add `check` to InboxState with the three properties, and the intents CheckForNewVideos and CheckChanged, with InboxReducerTest cases for each content; verify `./gradlew :feature:inbox-logic:test` passes
- [x] 3.2 Add the sync and wifi_off glyphs to `:core:designsystem` from Material Symbols Rounded at commit 10ecc14790 with the usual header, and add both to docs/architecture.md's list of shared icons; verify `./gradlew :core:designsystem:lint` passes
- [x] 3.3 Make InboxViewModel take UploadsChecker, merge its state into the reads, and call `start()` for CheckForNewVideos; add InboxViewModelTest cases with a fake checker; verify `./gradlew :feature:inbox-ui:testDebugUnitTest` passes
- [x] 3.4 Add the button, the turning glyph with the animation-scale check, the subtitle's live region, the banner, the strings and previews to InboxScreen; add screenshot tests for a running check, each banner over cards, and the YouTube banner over "You're all caught up", in light, dark, large font and dynamic color; record the references again and look at every new and changed image; verify `./gradlew :feature:inbox-ui:testDebugScreenshotTestDefaultTestSuite` and `python3 scripts/scan_screenshot_tests.py` pass

## 4. On an emulator

- [x] 4.1 Add InboxCheckTest to `:app`'s androidTest as design.md describes, update docs/testing.md's emulator test list and count, and add the check to "Checking the inbox by hand"; verify `ANDROID_SERIAL=<serial> ./gradlew :app:connectedDebugAndroidTest` passes on an Android 10 emulator and then on an Android 17 emulator booted with `-feature -HardwareDecoder`, one at a time, with the Mac's one-emulator limit
- [x] 4.2 Run the hand checks against real YouTube on Android 10, in dark mode, and Android 17: a check with two subscribed channels, airplane mode for the connection banner and "Try again", the glyph turning only with animations on, and a log with counts only; record what they found for the pull request, and list there the checks of docs/testing.md's list that weren't run: leaving for Search YouTube during a check (a check of two channels ends in under 2 seconds, and UploadsCheckerTest covers a check that outlives its caller), TalkBack, and the largest font on a device (the LargeFont screenshot tests cover it)

## 5. Checks

- [x] 5.1 Run `./gradlew spotlessApply`, then the pull request workflow's all-checks command and `python3 scripts/scan_screenshot_tests.py`; verify they pass

## Workflow follow-up

- Run the reviewer subagent until it says Ready, and the pre-push gate until it ends with PASS.
- Archive the change with /opsx:archive as the PR's last commit, and run the reviewer again on that commit.
- List in the PR where the words differ from the design's frames, and why.
- Open the PR, which closes #52, wait for CI and CodeRabbit, then send it to the merge desk.
