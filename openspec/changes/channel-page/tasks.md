# Tasks

## 1. Decisions the maintainer approves first

- [x] 1.1 Get the maintainer's answers on what users would see, and record them under "Decisions" in design.md and in the pull requests: the card's and dialog's words until overnight downloads exist, opening the channel from a video minimizing it, the folded description, and whether channel results in Search YouTube and "Subscribed" on the video screen get issues of their own. Verify that design.md, proposal.md and the specs match the answers.
- [x] 1.2 For channel results in Search YouTube, which the maintainer wants as its own task, open the issue in the Phase 5 milestone and add it to the project board. Verify that `gh issue view` shows the milestone and the board. The issue is #222, on the board.
- [x] 1.3 Add #222's task to docs/roadmap.md's Phase 5 tasks, in a docs pull request of its own. Verify that the roadmap's line matches #222's title. It is pull request #223, merged as 6f775a1.
- [x] 1.4 Open an After release issue for "Subscribed" on the video screen, and note on #60 that Phase 7 gives the card the design's words back. Verify with `gh issue view`. The issue is #225, on the board.

## 2. The refactor pull request (Part of #47)

- [x] 2.1 Move `LengthLabel`, `lengthLabel()` and `initialOf()` to `:core:model`, and `offersTryAgain()` to `:core:model` as the property `YouTubeError.offersTryAgain`, with their tests. Update the callers in search and the player. Verify with `./gradlew :core:model:test :feature:youtube-search-logic:test :feature:player-logic:test`.
- [x] 2.2 Add `:core:ui`, an Android library with Compose, and move `VideoRowItem`, `lengthText`, `spokenLength` and `timeAgoText` there with their strings, prefixed `core_ui_`, taking plain values and a nullable channel name. Move the emblem to `:core:designsystem` as `ChannelEmblem(initial, size)`. Verify that `./gradlew testDebugScreenshotTestDefaultTestSuite` passes with every reference image unchanged.
- [x] 2.3 Update docs/architecture.md (the module graph and tables, `:core:ui` no longer "later", what `:core:model` and `:core:designsystem` hold, with `offersTryAgain` named as the one screen rule in `:core:model`, the two feature modules' rows) and AGENTS.md's module list. Verify that every module and type the docs name exists.
- [x] 2.4 Run every check the pull request workflow runs, the reviewer until Ready, and the gate until PASS. Open the pull request with "Part of #47.", and verify that `closingIssuesReferences` is empty. It is pull request #228.

## 3. The channel page's logic

- [x] 3.1 Add `:feature:channel-page-logic` with `ChannelState`, `LoadArea`, `Subscription`, `UploadRow`, `ChannelIntent` and `ChannelReducer` from design.md. Verify with reducer tests for every intent and rule that design.md lists under Tests, unsubscribing offline then subscribing again included, and `./gradlew :feature:channel-page-logic:test`.
- [x] 3.2 Add `VideoPage.channelId` in `:feature:player-logic`, from `VideoDetails.channelId`. Verify with a player reducer test.

## 4. Repositories

- [x] 4.1 Add `ChannelUploadsRepository` and its Hilt binding to `:core:data`. Verify with a unit test against a fake `ChannelUploadsRemoteDataSource`.
- [x] 4.2 After #48 merges, rebase onto main and use its SubscriptionsRepository, with `subscribedChannel(channelId)`. If its names differ from design.md, update design.md. Check that its `channels` table keeps the description and the avatar links, which the offline page shows. Check that its `subscribe` and `unsubscribe` run in an injected app-wide scope, with a test whose fake suspends the write until released and cancels the caller before releasing it. Check that the manifest sets data extraction rules that keep the database out of both `<cloud-backup>` and `<device-transfer>`, as the maintainer asked, keeps `allowBackup="false"`, and no longer has `tools:ignore="DataExtractionRules"` or the comment that names #68 for it. Check that #48's app-wide scope uses a SupervisorJob. If any of these is missing, add it here, before any code writes a subscription. Verify that `./gradlew :core:data:testDebugUnitTest` passes.

## 5. The channel page's UI

- [x] 5.1 Add `:feature:channel-page-ui` with `ChannelViewModel` and `ChannelArgs`. Verify with ViewModel tests from design.md's Tests, against fake repositories, with `Dispatchers.setMain`.
- [x] 5.2 Write the stateless `ChannelScreen` and `ChannelRoute`, following the "Channel page" frames and design.md's Screen section, with the arrow_back, check, add and chevron_right glyphs added to `:core:designsystem`. Every string in resources, every icon with a content description. Verify with previews and screenshot tests in light, dark, font scale 2, a tablet, and dynamic light and dark, for each state design.md lists, and look at every new reference image.

## 6. The app

- [x] 6.1 Add `ChannelKey` and `ChannelIdSerializer`, change `openVideo`, add `openChannel`, and let `close` take any NavKey, in `:app`. Add the channel entry to DewfallNavigation, and `onOpenChannel` to `PlayerRoute`, with the channel box's click, its "Open" and the channel's name label, and the chevron at its end. Verify with BackStack and NavKeys tests for each case design.md's Navigation section names, and record the video screen's reference images again, looking at each changed one.
- [x] 6.2 Add ChannelSubscribeTest to app/src/androidTest/, with the fakes that SearchThenPlayTest uses, as design.md's Tests describes, and describe it in docs/testing.md's emulator tests section. Verify that it passes on one emulator with `ANDROID_SERIAL=<serial> ./gradlew :app:connectedDebugAndroidTest`, with no other emulator running.
- [x] 6.3 Update docs/architecture.md (modules, the repositories in "Reading from YouTube", Navigation's keys and functions, the data flow with the database), AGENTS.md (feature/ row, module list) and the player spec's KDoc references. Verify that each named type exists in the code.

## 7. Checks

- [x] 7.1 Run `./gradlew spotlessApply`, then every check the pull request workflow runs: `./gradlew spotlessCheck detekt lint test testDebugScreenshotTestDefaultTestSuite assembleDebug` and `python3 scripts/scan_screenshot_tests.py`. Verify that all pass and that core/database/schemas/ is unchanged.
- [x] 7.2 Check by hand on an emulator with Android 10 and with the newest Android installed, one emulator at a time: search, open a video, tap its channel, see the page with the video in the mini player, subscribe, go back and open the channel again, close the app and open it again, then turn the network off and see the kept channel with Unsubscribe and the failure, unsubscribe through the dialog, rotate with the dialog open, fold and unfold the description, open an upload and go back, tap the mini player on the channel page, and listen to TalkBack: the card's new state once after each tap, and not again after a rotation. Check light, dark, a large font and a tablet-sized window. Verify that `adb logcat` shows no channel ID, name, description or title.
  - Done on November 22, 2026, on Android 17 (API 37, Dewfall_Pixel_6_API_37, started with `-feature -HardwareDecoder`) and Android 10 (API 29, Dewfall_Pixel_4_API_29_GA, dark mode, gesture navigation), one emulator at a time. ChannelSubscribeTest and the other emulator tests passed on both. FullscreenClearOfBarsTest skipped on the Android 10 emulator, which uses gesture navigation, as it is meant to.
  - By hand, on both: the channel page opened from a video's channel with the video playing on in the mini player; Subscribe, going back and opening the page again; Unsubscribe through the dialog, with Cancel and the back gesture closing it; the page of a followed channel offline, showing the kept channel, the card and the failure, then unsubscribing and subscribing again offline; Try again once online. On Android 17 also: More and Less; an upload's channel returning to the same page; still subscribed after a force stop; the dialog staying open across a theme change; a font scale of 2; dynamic color; process death, after `kill -9`, bringing back the same page. `adb logcat` held no channel name from Dewfall's process. The only lines with the channel's name came from the Bluetooth process, which reads the media session's title and artist, the exception that AGENTS.md records.
  - Covered by other tests instead of by hand: a tablet-sized window, by the Tablet screenshot; closing the dialog with a tap outside, which Material 3's AlertDialog sends to the same onDismissRequest as the back gesture, which was checked. FullscreenClearOfBarsTest needs three-button navigation on Android 10, so the emulator tests workflow's Android 10 job, which fails unless that test runs, covers the video screen's change there. Split screen wasn't checked.
  - ChannelSubscribeTest gained a check that the mini player shows on the channel page after the runs above. The emulator tests workflow runs it on this pull request, because it changes app/src/androidTest/.
  - Not done: listening to TalkBack. Turning TalkBack on with adb opened its first-run permission prompts, which adb can't answer reliably, so it was turned off again. The live region's text is in the screen's semantics, which the screenshot previews and the code show. Asked in this session's question dialog on November 22, 2026, how to handle the TalkBack check, the maintainer picked "Merge, I'll listen later (Recommended)": the pull request may merge after its checks, and the maintainer listens on a phone later, as docs/testing.md's hand checks describe. If TalkBack repeats the state or says nothing, a small fix follows.

## 8. The living spec

- [ ] 8.1 When archiving, widen the Purpose of openspec/specs/subscriptions/spec.md, which #48 wrote for keeping channels and videos on the phone, to cover the channel page and subscribing and unsubscribing from it, because archiving adds the requirements but leaves the Purpose as it is. Verify that `openspec validate --specs --strict` passes and the Purpose names the channel page.

## Workflow follow-up

- Run the reviewer until it says Ready, and the gate until PASS.
- Archive the change with /opsx:archive as the last commit of the channel page's pull request, then run the reviewer again on that commit.
- Open the pull request that says it closes #47, handle CI and CodeRabbit, and send it to the merge desk.
