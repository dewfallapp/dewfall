# Tasks

## 1. The search logic

- [x] 1.1 In `:feature:youtube-search-logic`, add ChannelRow, rename Results' `rows` to `videos` and give it `channels`, add `subscribedIds` and `confirmingUnsubscribe` to YouTubeSearchState, and the intents SubscriptionsRead, Subscribe, UnsubscribeTapped, UnsubscribeConfirmed and UnsubscribeCancelled, as design.md says; add YouTubeSearchReducerTest cases for each rule design.md's Tests lists; verify `./gradlew :feature:youtube-search-logic:test` passes

## 2. The shared dialog

- [x] 2.1 Move UnsubscribeDialog to `:core:ui` with `onConfirm` and `onDismiss`, its strings renamed with the `core_ui_` prefix and the same words, use it from the channel page, and remove the channel page's three strings it no longer uses; verify `./gradlew :core:ui:lint :feature:channel-page-ui:testDebugUnitTest :feature:channel-page-ui:testDebugScreenshotTestDefaultTestSuite` passes with the channel page's reference images unchanged (`git status feature/channel-page-ui` shows no changed image)

## 3. The search screen

- [x] 3.1 Give YouTubeSearchViewModel SubscriptionsRepository: read the subscribed IDs for its whole life, subscribe with the row's ID, name and avatars and no description, and unsubscribe the confirmed ID; add YouTubeSearchViewModelTest cases with a fake SubscriptionsRepository and Dispatchers.setMain; update LayerYouTubeSearchRepository's KDoc; verify `./gradlew :feature:youtube-search-ui:testDebugUnitTest` passes
- [x] 3.2 Draw the "Channels" heading and group, the channel row with its FlowRow, the compact button with its semantics, and the dialog for `confirmingUnsubscribe`; add `onOpenChannel` to YouTubeSearch and the route; change the strings from design.md's Words table; verify `./gradlew :feature:youtube-search-ui:lint` passes
- [x] 3.3 Update the previews with made-up channels, add the only-channels and dialog previews and their @PreviewTest wrappers, record the reference images again with `./gradlew :feature:youtube-search-ui:updateDebugScreenshotTestDefaultTestSuite`, and look at every new and changed image, at the large font and the tablet too; verify `./gradlew :feature:youtube-search-ui:testDebugScreenshotTestDefaultTestSuite` and `python3 scripts/scan_screenshot_tests.py` pass

## 4. Navigation and the emulator test

- [x] 4.1 Generalize `openChannel` to `from: NavKey`, wire YouTubeSearch's `onOpenChannel` in DewfallNavigation, and add BackStackTest cases for opening from search and a double tap; verify `./gradlew :app:testDebugUnitTest` passes
- [x] 4.2 Make FakeYouTubeModule's search also answer with the test channel and an excerpt only the search row shows, add SearchChannelTest as design.md describes, with its check of the saved state Bundle, and run every emulator test on one Android 10 emulator, with the Mac's one-emulator limit; verify `ANDROID_SERIAL=<serial> ./gradlew :app:connectedDebugAndroidTest` passes
- [x] 4.3 Update docs/architecture.md (Search YouTube's dependencies on SubscriptionsRepository and `:core:ui`'s dialog, and `openChannel`'s use from search) and docs/testing.md (SearchChannelTest, and hand checks for channel results: dark, large font, split screen, TalkBack hearing "Subscribed to"); verify each names only files and functions that exist

## 5. Checks

- [x] 5.1 Run the channel results' hand checks from docs/testing.md on one emulator, and record what they found for the pull request
  - Emulator tests, November 23, 2026: on Dewfall_Pixel_4_API_29_GA (Android 10), 11 tests, 10 passed, 1 skipped, 0 failures. On Dewfall_Pixel_6_API_37 (Android 17), started without a window, SearchChannelTest, ChannelSubscribeTest and the four ChannelInitialTest cases passed, and the five tests that play video failed with NOT_DECODABLE or timeouts waiting for playback, with the Mac's load average between 23 and 45. This change doesn't touch playback, and the emulator tests workflow runs all of them on the pull request.
  - The first runs found a real bug: the compact button's cleared semantics merged into the clickable row, so a screen reader would have read and tapped the row and the button as one, and the test's tap opened the channel page. The button now keeps its own merging boundary.
  - Android 10, dark mode, a real search for "NASA": six channel rows, excerpts read cleanly, NASA Goddard's included. Subscribe turned the button into "Subscribed" at once. "Subscribed" opened the dialog naming the channel; the back gesture closed it with no change; "Unsubscribe" turned the row back to "Subscribe". Tapping the row opened NASA's page, and back showed the same results. At font scale 2 the buttons moved under the text and nothing was cut off. After `am kill` from the background, the screen came back with "NASA" in the field and the message from before the first search. `adb logcat` held no channel name, ID or excerpt.
  - Android 17, light mode with dynamic color, split screen with Dewfall above Settings: the channel rows fitted, Subscribe worked, the dialog in light mode named the channel, and a tap outside closed it with no change.
  - Android 17, November 23, 2026, after the merge check asked for the rest of docs/testing.md's list, with a real search for "NASA": the dialog's Cancel button closed it and the row still said "Subscribed". With the dialog open, a theme change from light to dark kept it open, for NASA. Two taps sent at once on NASA's row, which the row had just subscribed to, opened one channel page, which said "Subscribed", and one back gesture returned to the same results, with the row still saying "Subscribed". Unsubscribing on NASA's page, through its dialog, then going back, showed "Subscribe" in the row. In a tablet-sized window, 1600x2560 at density 240 (about 1066dp wide), the content sat in one centered column with nothing cut off. The emulator's size, density and theme were reset afterwards, and the app uninstalled.
  - Not done: listening to TalkBack, which these emulator images don't have. Asked in this session's question dialog on November 23, 2026, the maintainer answered "Merge, I'll listen later (Recommended)".
- [x] 5.2 Run `./gradlew spotlessApply`, then the pull request workflow's all-checks command and `python3 scripts/scan_screenshot_tests.py`; verify they pass
  - November 23, 2026: the pre-push gate's full build, which runs the same checks, passed on the branch before the archive commit ("PASS: the gate passed."); the pull request shows the gate's output for its head, and `python3 scripts/scan_screenshot_tests.py` printed "PASS 98 files".

## Workflow follow-up

- Run the reviewer subagent until it says Ready, and the pre-push gate until it ends with PASS.
- Archive the change with /opsx:archive as the pull request's last commit, and run the reviewer again on that commit.
- List in the pull request where the words differ from the design's frames, and why.
- Open the pull request, which closes #222, wait for CI and CodeRabbit, then send it to the merge desk.
