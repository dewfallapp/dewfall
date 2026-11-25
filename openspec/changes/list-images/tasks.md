# Tasks

## 1. Picking a size

- [ ] 1.1 Add `forWidth` for `List<Thumbnail>` to `:core:model`, as design.md's "Picking a size" says, with ThumbnailTest covering both scenarios of "An image asks for the size it fills", an exact match, ties, sizes with no width alone and mixed with sized ones, and an empty list; verify `./gradlew :core:model:test` passes

## 2. The image loader

- [ ] 2.1 Add Coil 3.6.3 (`coil-compose`, `coil-network-okhttp`) to gradle/libs.versions.toml with a comment, `coil-compose` to `:core:ui` and `coil-network-okhttp` plus OkHttp to `:app`; diff `./gradlew :app:dependencies --configuration releaseRuntimeClasspath` before and after, record every new artifact with its license and release date for the pull request, and verify `./gradlew :app:assembleDebug` passes
- [ ] 2.2 Add ImageHostRule and the ImageLoader factory to `:app`, with DewfallApplication implementing `SingletonImageLoader.Factory`: no ServiceLoader discovery, only Dewfall's OkHttp fetcher, no redirects, no cookies, `Accept-Language: en-GB` on every request, the 64 MiB disk cache in `cacheDir/images`, no Coil logger; verify `./gradlew :app:assembleDebug lint` passes
- [ ] 2.3 Add ImageHostRuleTest in `:app`'s unit tests with a fake Interceptor.Chain: each of the three hosts over HTTPS passes, plain HTTP, another host, a look-alike host (such as `i.ytimg.com.example.org` and `evil-ytimg.com`), a subdomain of an allowed host, and an IP address are refused without calling `proceed`, and the refusal's message holds no part of the URL; a test that the language interceptor adds `Accept-Language: en-GB` and keeps the URL as it was; plus a test that the built OkHttpClient follows no redirects and runs both interceptors, host rule first; verify `./gradlew :app:testDebugUnitTest` passes

## 3. The shared composables

- [ ] 3.1 Give VideoRowItem a `thumbnails` parameter and draw the image in its box under the length tag, with `contentDescription = null`, Crop, no crossfade, and no request for an empty list; verify `./gradlew :core:ui:assembleDebug` passes
- [ ] 3.2 Add ChannelAvatar to `:core:ui` (ChannelEmblem with the initial, the image clipped to the circle, the ring on top, hidden from screen readers) and PreviewImages, the preview handler with the fixed `primary`-filled image; add `:core:ui` previews of a row and an avatar with and without images, and verify `./gradlew :core:ui:lint detekt` passes
- [ ] 3.3 Update KDoc of VideoRowItem and ChannelEmblem (the box and the emblem are now placeholders under the image), and verify `./gradlew spotlessCheck` passes

## 4. The screens

- [ ] 4.1 Search YouTube: VideoRow gains `thumbnails` from VideoSummary, rows pass them, channel rows use ChannelAvatar with their avatars; reducer tests check the thumbnails reach the rows; previews wrapped in PreviewImages with links in their sample data, references recorded again; verify `./gradlew :feature:youtube-search-logic:test :feature:youtube-search-ui:testDebugUnitTest` passes
- [ ] 4.2 Channel page: UploadRow gains `thumbnails`, the header uses ChannelAvatar with `shownChannel`'s avatars; reducer tests check both; previews and references as in 4.1; verify `./gradlew :feature:channel-page-logic:test :feature:channel-page-ui:testDebugUnitTest` passes
- [ ] 4.3 Inbox: `InboxIntent.Loaded` gains the avatars by channel ID, InboxGroup gains `channelAvatars`, the ViewModel reads them from `subscribedChannels()`, the cards use ChannelAvatar and the rows pass thumbnails; reducer tests (avatars mapped, a channel with none gets an empty list) and a ViewModel test (an avatar change reaches the state); previews and references as in 4.1; verify `./gradlew :feature:inbox-logic:test :feature:inbox-ui:testDebugUnitTest` passes
- [ ] 4.4 Channels screen: rows use ChannelAvatar with the channel's avatars; previews and references as in 4.1; verify `./gradlew :feature:channels-ui:testDebugUnitTest` passes
- [ ] 4.5 Video screen: VideoPage gains `channelAvatars` from VideoDetails, `:feature:player-ui` depends on `:core:ui`, the channel row uses ChannelAvatar; PlayerReducer's KDoc and reducer tests updated; previews and references as in 4.1; verify `./gradlew :feature:player-logic:test :feature:player-ui:testDebugUnitTest` passes
- [ ] 4.6 Record the screenshot references again with `./gradlew updateDebugScreenshotTestDefaultTestSuite`, look at every changed image (light, dark, large font, dynamic color), confirm each shows the fixed fill where the sample data has links and the placeholder where it has none, and verify `./gradlew testDebugScreenshotTestDefaultTestSuite` and `python3 scripts/scan_screenshot_tests.py` pass
- [ ] 4.7 Run the emulator tests on one emulator, after asking the #54 session for the slot, and verify `ANDROID_SERIAL=<serial> ./gradlew :app:connectedDebugAndroidTest` passes (their fakes have no image links, so no image request leaves)

## 5. Docs

- [ ] 5.1 Write ADR 0010 "Images load with Coil from YouTube's image hosts" from docs/adr/0000-template.md, with the maintainer's answer of November 22, 2026, the host rule, the en-GB header, why a second client outside the YouTube layer is allowed as ADR 0009 asks for any client change, that the layer's own client stands, and the alternatives; add a dated note to ADR 0009 that points to ADR 0010; verify the ADR index or list in docs/ links it if one exists
- [ ] 5.2 Update PRIVACY.md (image requests to the three hosts, what they carry, the cache on the phone), AGENTS.md's product rule and openspec/config.yaml's context (images from YouTube's image hosts when the YouTube layer gives the links), AGENTS.md's en-GB bullet (image requests name no country, next to the subtitle download and MediaRequests), docs/roadmap.md's Phase 6 tasks (a line for thumbnails and avatars in lists), docs/architecture.md (Coil, the loader in `:app`, `forWidth`, ChannelAvatar, the replaced "Until then, rows show a plain box" paragraph, the `:core:ui` and `:app` rows), and docs/testing.md (a hand check: real thumbnails and avatars in each list on a phone, placeholders in airplane mode); verify every changed paragraph by reading it against the code
- [ ] 5.3 Hand check on one emulator with network: search, open a channel page, subscribe, refresh the inbox, open the Channels screen and a video; each shows real images, and with airplane mode on a fresh install each shows placeholders and still opens videos; record the result for the pull request, without screenshots of real thumbnails

## 6. Integration

- [ ] 6.1 Run `./gradlew spotlessCheck detekt lint test testDebugScreenshotTestDefaultTestSuite assembleDebug` and `python3 scripts/scan_screenshot_tests.py`, then `python3 scripts/pre_push_gate.py`; verify both end in success and PASS

## Workflow follow-up

- Run the reviewer subagent until Ready, then archive this change with /opsx:archive as the pull request's last commit, run the reviewer again on that commit, and hand the pull request to the merge desk.
