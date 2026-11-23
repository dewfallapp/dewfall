# Tasks

Group 1 and the proposal go in the first pull request, "Part of #49", which needs nothing from #47. Task 0.1 was done in the first, once #234 merged. Group 5 goes in the second, "Part of #49". Groups 2 to 4, 6 and 7 go in the third, "closes #49", on top of main.

## 0. Check #47's facts

- [x] 0.1 After #47 merges, check against main each fact design.md takes from #47: ChannelKey and `openChannel(from: PlayerKey, channelId)`, `openVideo`'s condition, ic_arrow_back, ChannelSubscribeTest and FakeYouTubeModule's channel answer, Subscribe keeping the answered channel, and ChannelState holding the shown uploads; update the plan where one differs, and if one does, run the maintainer subagent's plan check again before building

## 1. The inbox read

- [x] 1.1 Add VideoDao's `inboxVideos(video)` query from design.md, and InboxQueryTest cases for each part of the rule (the 2 latest, a started video kept when pushed out of the 2 latest, published after subscribing, first saved later than the first video saved since subscribing, an estimate before subscribing, live, upcoming and Short left out, watched left out, partly watched kept, unsubscribed, subscribed again, the order, a watched mark that pulls no older video in, a Flow that emits again after a save, a subscription and a watched mark, a video started before subscribing left out, a video marked unwatched after subscribing kept, a row with no listing number never new by the first-save test, subscribing again with a first save that brings nothing, a video never played and marked unwatched after subscribing left out); verify `ANDROID_SERIAL=<serial> ./gradlew :core:database:connectedDebugAndroidTest` passes on an Android 10 emulator and `git status core/database/schemas` is clean (two cases added later run only in the emulator tests workflow, as docs/testing.md says)
- [x] 1.2 Time the query in an androidTest of `:core:database` that is skipped unless an instrumentation argument turns it on, with 300 channels of 300 videos and a watch state for a tenth of them, as the median of 5 runs after one warm-up, on the Android 10 emulator; verify the median is under 250 ms, and record it and the emulator for the pull request. If it isn't, stop and change the plan
- [x] 1.3 Make StoredVideosRepository a plain interface with `inboxVideos()`, map rows to VideoSummary in Mappings.kt, and add tests for the mapping and the repository with a fake DAO (order kept, `distinctUntilChanged`); update the fakes that were lambdas; verify `./gradlew :core:data:testDebugUnitTest` passes
- [x] 1.4 Update docs/architecture.md's database repositories table and StoredVideosRepository's KDoc, and verify they name only functions that exist

## 2. The inbox logic

- [x] 2.1 Create `:feature:inbox-logic` (dewfall.jvm.library, api `:core:mvi` and `:core:model`) with InboxState, InboxIntent, InboxGroup and InboxReducer, include it in settings.gradle.kts, and add InboxReducerTest (no channels, caught up with 1 and with 4 channels, groups in order of their newest video, each group's order kept, a channel name from its first video, a new Loaded replacing the old, videos winning over a count of 0, ReadFailed showing Unreadable and a later Loaded replacing it); verify `./gradlew :feature:inbox-logic:test` passes

## 3. The inbox screen

- [x] 3.1 Add the subscriptions icon to `:core:designsystem`, copied from Material Symbols Rounded at the same commit as the other icons, with the same header, and use #50's done_all; verify `./gradlew :core:designsystem:lint` passes
- [x] 3.2 Add `stateSubscriptionCount` to MviViewModel, with MviViewModelTest cases for a collector arriving and leaving; verify `./gradlew :core:mvi:test` passes
- [x] 3.3 Create `:feature:inbox-ui` (library, compose, screenshots, hilt) with InboxViewModel (reading while its state has a collector, through `SharingStarted.WhileSubscribed(5_000)`), InboxRoute, the public `Inbox(onOpenVideo, onOpenChannel, onOpenSearch)`, InboxScreen, the cards, the footer, the empty states, string resources with plurals, and previews; include it in settings.gradle.kts; add InboxViewModelTest with fake repositories and Dispatchers.setMain, including that reading stops 5 seconds after the last collector leaves and starts again when one comes, and that a read that throws shows Unreadable instead of crashing; verify `./gradlew :feature:inbox-ui:testDebugUnitTest` passes
- [x] 3.4 Add screenshot tests for the videos (light, dark, large font, tablet, dynamic light and dark), no channels (light, dark, large font, dynamic light) caught up (light, dark, large font, dynamic light) and unreadable (light, dark, large font), record them, and look at every image; verify `./gradlew :feature:inbox-ui:testDebugScreenshotTestDefaultTestSuite` passes and `python3 scripts/scan_screenshot_tests.py` passes

## 4. Search YouTube's back arrow

- [x] 4.1 Give YouTubeSearch an `onBack` action and its screen a back arrow named "Back", update its previews, record its screenshot references again and look at every changed image; verify `./gradlew :feature:youtube-search-ui:testDebugUnitTest :feature:youtube-search-ui:testDebugScreenshotTestDefaultTestSuite` passes

## 5. The channel page fills the inbox

- [x] 5.1 Add SubscribeWithUploads to `:core:data`, which subscribes and then saves the given uploads, if any, as one unit in the `@ApplicationScope` scope, with a test of the order and one that both writes finish after the caller is cancelled; make ChannelViewModel keep the latest successful load's ChannelUploads whole, call SubscribeWithUploads on Subscribe with it, and save it unchanged with `saveUploads` once for each load number when its answer and a Subscribed reading of the card both hold, also for a subscription under a moved channel's old ID, and never while the card doesn't say Subscribed, with a failed save or subscribe changing nothing on the page; add ChannelViewModelTest cases for each; verify `./gradlew :core:data:testDebugUnitTest :feature:channel-page-ui:testDebugUnitTest` passes, and `ANDROID_SERIAL=<serial> ./gradlew :app:connectedDebugAndroidTest` passes on an Android 10 emulator and then on an Android 17 emulator booted with `-feature -HardwareDecoder`, one at a time

## 6. The app opens on the inbox

- [x] 6.1 Add InboxKey as the back stack's first key, `openSearch()`, opening a channel from the inbox, and `close` keeping InboxKey, `openVideo` working with InboxKey and ChannelKey on top, wire `Inbox` and YouTubeSearch's `onBack` in DewfallNavigation, and add BackStackTest and NavKeysTest cases; verify `./gradlew :app:testDebugUnitTest` passes
- [x] 6.2 Make every emulator test that starts on Search YouTube, the six on main, ChannelSubscribeTest and ResumeTest among them, open it from the inbox through a shared helper, add the inbox to the player spec's "Opening the video that already plays goes on with it" in the player delta, and add InboxFromChannelTest, with its check of the saved state Bundle; verify `ANDROID_SERIAL=<serial> ./gradlew :app:connectedDebugAndroidTest` passes on an Android 10 emulator and then on an Android 17 emulator booted with `-feature -HardwareDecoder`, one at a time, with the Mac's one-emulator limit
- [x] 6.3 Update docs/architecture.md (the inbox query's pointer to openspec/specs/inbox/spec.md once this change is archived, the opening paragraph that calls Search YouTube the app's first screen, modules and graph, with `:core:ui` in `:feature:inbox-ui`'s dependencies, `:app`'s row, navigation, and the MVI contract's MviViewModel row for `stateSubscriptionCount`), docs/testing.md (emulator tests, what the new test checks, "Checking the inbox by hand"), AGENTS.md (where things are, the module list) and README.md if it names the first screen; verify each names only files and commands that exist

## 7. Checks

- [x] 7.1 Time the inbox query once on the Android 17 emulator too, as docs/testing.md says, and record it. Run the inbox's hand checks from docs/testing.md on one emulator, including scrolling down, ending the process with `adb shell am kill io.github.dewfallapp` from the background, and coming back to the same scroll position, and record what they found in the pull request
- [ ] 7.2 Run `./gradlew spotlessApply`, then the pull request workflow's all-checks command and `python3 scripts/scan_screenshot_tests.py`; verify they pass

## Workflow follow-up

- Run the reviewer subagent until it says Ready, and the pre-push gate until it ends with PASS.
- Archive the change with /opsx:archive as the PR's last commit, and run the reviewer again on that commit.
- List in the PR where the words differ from the design's frames, and why.
- Open the PR, which closes #49, wait for CI and CodeRabbit, then send it to the merge desk.
