# Design

## Context

See proposal.md for why. What this change builds on:

- The models already hold every size of each image. VideoSummary and VideoDetails have `thumbnails`, Channel has `avatars`, and VideoDetails has the channel's avatars, all as lists of Thumbnail (URL, and width and height when YouTube gives them). The `videos` and `channels` tables keep both lists as JSON, so saved videos and subscribed channels have them too (docs/architecture.md, Database tables).
- Thumbnail's KDoc and ADR 0007 count its URL as user data, so it is never logged.
- The rows that show a placeholder today: VideoRowItem in `:core:ui` (Search YouTube, the inbox, the channel page) draws a 72dp-wide 16:9 box on `surface-container-highest`, scaled with the font size. ChannelEmblem in `:core:designsystem` draws a circle on `tertiary-container` with a 1dp `outline-variant` ring and the initial, scaled with the font size. Search YouTube's channel rows, the inbox's cards, the Channels screen, the channel page's header, the video screen's channel row and the Takeout report use it.
- What each screen's state carries today: Search YouTube's ChannelRow has `avatars`, its VideoRow has no thumbnails. The channel page's `shownChannel` is a Channel with avatars, its UploadRow has no thumbnails. The inbox's InboxGroup has the channel's ID and name, and its videos are VideoSummary with thumbnails. The Channels screen has ChannelOverview, whose Channel has avatars. The video screen's VideoPage drops the avatars, as PlayerReducer's KDoc says.
- The design system: VideoRow's thumbnail "shows the cached thumbnail image; the placeholder is a plain `surface-container-highest` fill", and the `tertiary-container` token is the "channel emblem placeholder (the initial avatar) when no channel image is cached". The brief keeps real thumbnails, faces and channels out of public designs, and screenshot references in this repository are public.
- The saved YouTube responses in `core/youtube/src/test/resources/youtube/` hold image links on three hosts only: i.ytimg.com for video thumbnails, and yt3.ggpht.com and yt3.googleusercontent.com for avatars. NewPipeExtractor gives them as HTTPS links.
- #54, running in parallel, adds a download button to the video row and the video screen. Its download engine (PR #255) keeps each download's thumbnail links as JSON, as the `videos` table does, and saves no image file. Neither plan keeps thumbnails of downloaded videos on the phone: [#256](https://github.com/dewfallapp/dewfall/issues/256), filed on November 26, 2026 with the Phase 7 milestone and on the board, tracks that.

## Goals / Non-Goals

**Goals:**

- Real images in every list the proposal names, with the design's placeholders unchanged underneath.
- A host rule that one place enforces and a JVM test pins.
- Screenshot tests that never touch the network and never hold a real thumbnail.
- A loader that #256 can point at a file on the phone without a new library, once #256 allows files in ImageLinkRule.

**Non-Goals:**

- Thumbnails of downloaded videos when offline, beyond what the cache happens to hold. Nobody saves them yet; #256 tracks it.
- A large thumbnail on the video screen, such as a poster before playback starts. The player already shows the video.
- The Takeout import report's rows. Their channels were just imported and have no avatars yet, so they keep the emblem.
- A setting to turn images off or limit them to Wi-Fi. No spec asks for one, and the rows are small.
- Prefetching images ahead of the visible rows beyond what the lazy lists compose.

## Decisions

The ADR this change writes takes the next free number when it is written: 0010 is claimed by PR #255 (#54's download engine), so this change's ADR is 0011, unless #255 drops its ADR before this change merges. This design calls it "the new ADR".

### Coil 3 as the image library

Compose has no image loader of its own. Coil is one of the two loaders that Android's own guidance names for Compose, with Glide ([Loading images](https://developer.android.com/develop/ui/compose/graphics/images/loading), checked on November 26, 2026), is written in Kotlin with coroutines, and loads links, files and content URIs through one API, so #256 can hand it a saved file later.

- Version 3.6.3, released on September 18, 2026, by the Last-Modified date of its POM on Maven Central ([coil-compose listing](https://repo1.maven.org/maven2/io/coil-kt/coil3/coil-compose/), checked on November 26, 2026), 69 days before November 26, 2026. Apache 2.0, by its POM's license block.
- Artifacts: `io.coil-kt.coil3:coil-compose` in `:core:ui`, and `io.coil-kt.coil3:coil-network-okhttp` in `:app`. Their POMs ([coil-compose-android](https://repo1.maven.org/maven2/io/coil-kt/coil3/coil-compose-android/3.6.3/coil-compose-android-3.6.3.pom), [coil-compose-core-android](https://repo1.maven.org/maven2/io/coil-kt/coil3/coil-compose-core-android/3.6.3/coil-compose-core-android-3.6.3.pom), [coil-core-android](https://repo1.maven.org/maven2/io/coil-kt/coil3/coil-core-android/3.6.3/coil-core-android-3.6.3.pom), [coil-network-okhttp-android](https://repo1.maven.org/maven2/io/coil-kt/coil3/coil-network-okhttp-android/3.6.3/coil-network-okhttp-android-3.6.3.pom), checked on November 26, 2026) bring coil-core, coil-android, coil-compose-core, coil-network-core, Okio, kotlinx-coroutines, OkHttp (which the app already ships at 5.5.1, and Gradle keeps the higher version), Accompanist's drawablepainter, and AndroidX artifacts (lifecycle-runtime, core-ktx, annotation, appcompat-resources, exifinterface, profileinstaller), plus JetBrains' Compose wrappers at 1.12.0, among them foundation, foundation-layout, animation, runtime, ui, ui-text and ui-util, and the artifacts they bring in turn, such as ui-geometry, ui-graphics, ui-unit, runtime-saveable and androidx.window, which depend on the AndroidX Compose artifacts of the same version. The Compose BOM 2026.09.00 pins AndroidX Compose at 1.12.1, so the app's Compose doesn't change, but the wrappers are new artifacts. Every one is Apache 2.0. The pull request lists each artifact that `./gradlew :app:dependencies --configuration releaseRuntimeClasspath` shows as new, and each version that Coil raises, with its license and release date for the 14-day rule, under "New dependencies".
- Coil sends nothing anywhere on its own: no analytics, no reporting. It reaches the network only through a network fetcher, which it finds through Java's service loader or takes from the app's ImageLoader ([Network images](https://coil-kt.github.io/coil/network/), checked on November 26, 2026). `ImageLoader.Builder.serviceLoaderEnabled(false)` turns the discovery off ([imageLoaders.kt at 3.6.3](https://github.com/coil-kt/coil/blob/3.6.3/coil-core/src/commonMain/kotlin/coil3/imageLoaders.kt), checked on November 26, 2026).

Alternatives: Glide (Java, View-first, and its Compose artifact has only pre-releases, the newest 1.0.0-beta11 of November 16, 2026, [Maven Central](https://repo1.maven.org/maven2/com/github/bumptech/glide/compose/), checked on November 26, 2026), Landscapist (its README offers a wrapper over Coil or Glide, and, as `landscapist-core`, a Kotlin Multiplatform loading engine of its own with memory and disk caching, checked on November 26, 2026 at github.com/skydoves/landscapist; Coil is the one Android's guidance names, and has the larger user base and track record on Android), or a hand-written loader with OkHttp and BitmapFactory (sizing, caching, memory and cancellation are the hard parts, and Coil already does them).

### The image loader lives in `:app`, with its own HTTP client

An App Startup initializer in `:app`, ImageLoaderInitializer, sets Coil's singleton with `SingletonImageLoader.setSafe` ([Image loaders](https://coil-kt.github.io/coil/image_loaders/), checked on November 26, 2026, which also describes the Factory on the Application class), so every AsyncImage uses one ImageLoader that `:app` builds. An initializer, not DewfallApplication implementing `SingletonImageLoader.Factory`, because the emulator tests run under HiltTestApplication, and there a Factory on DewfallApplication would never run and Coil would fall back to its default loader, with no link rule and ServiceLoader discovery on. The manifest's InitializationProvider already exists, for removing emoji2's initializer.

- A Coil Interceptor, ImageLinkRule, runs first for every request, before any fetcher. A String, a Coil Uri, an Android Uri or an OkHttp HttpUrl passes only when it is a YouTube image link, as `:core:model`'s `isYouTubeImageLink` decides. Every other kind of data fails, a `java.io.File` included, because the list-images spec allows only links that the YouTube layer gave. So a `file://`, `content://`, `data:` or `android.resource://` string from a strange link or a saved row never reaches Coil's local fetchers.
- `isYouTubeImageLink(link: String)` in `:core:model`, a pure function next to the list of the three hosts: true only for an absolute `https` link, with no user info, the default port, and a host that is exactly one of the three, compared case-insensitively. The live YouTube check reuses it, so a move of images to a fourth host shows up in the daily check instead of only as placeholders.

- Coil's ServiceLoader discovery is turned off, and the only network fetcher is `OkHttpNetworkFetcherFactory` with Dewfall's own OkHttpClient, so no other fetcher can register itself.
- The OkHttp client checks again, as a second line: an application interceptor, ImageHostRule, lets a request through only when `isYouTubeImageLink` is true for its URL. Any other request fails with an IOException whose message is constant text and names no URL, before DNS or a connection. Exact names, not suffixes, because the saved responses show only these three, and a wider rule would let a forged link reach any Google host.
- `followRedirects(false)` and `followSslRedirects(false)`, so a redirect can't lead the request elsewhere. Coil treats the 3xx as a failure, and the placeholder stays.
- An `Accept-Language: en-GB` header on every request, from a second interceptor, because the maintainer counts the image hosts as YouTube, and AGENTS.md and ADR 0009 say every request to YouTube asks for British English. It names no country, as MediaRequests and the subtitle download don't (ADR 0009's notes), for a reason of its own: an image's bytes hold no text, and the image hosts take no country.
- No cookie jar (OkHttp's default keeps none), no cache of OkHttp's own, and OkHttp's own User-Agent. It is a separate client from the YouTube layer's, which `:app`'s main code doesn't see, and which carries YouTube's API headers that image hosts don't need. ADR 0009 says that changing the layer's HTTP client needs a new ADR, so the new ADR records this second client outside the layer, why the maintainer's answer of November 22, 2026 allows it, and that the layer's own client decision stands.
- A disk cache of 64 MiB in `cacheDir/images`, and Coil's default memory cache. Coil 3 by default "does not respect Cache-Control headers" ([Network images](https://coil-kt.github.io/coil/network/), checked on November 26, 2026), so it keeps an image until the cache drops it, whatever headers the host sends, which suits thumbnails that don't change.
- No Coil logger (ImageLoader.Builder's `logger` is null unless set, [ImageLoader.kt at 3.6.3](https://github.com/coil-kt/coil/blob/3.6.3/coil-core/src/commonMain/kotlin/coil3/ImageLoader.kt), checked on November 26, 2026), so Coil writes nothing to logcat, and no image URL can reach a log (ADR 0007). A failed image needs no line of its own: the placeholder already shows it, and the YouTube layer's lines cover the calls that gave the links.

ImageHostRule and the language interceptor are plain classes in `:app` with JVM tests that run them against made-up requests through a fake Interceptor.Chain, with no network. ImageLinkRule's decision is a plain function of the request's data, tested the same way. It fails a refused request with its own exception type, RefusedImageLink, whose message is constant text. An emulator test checks the wiring under HiltTestApplication with links that Coil's default loader would load: a `file://` string to a small image that the test writes in cacheDir, and an `android.resource://` link to one of the app's drawables. Both must end in an ErrorResult whose throwable is RefusedImageLink, and the singleton's disk cache folder must be cacheDir/images, so the test fails if the default loader is in use.

Alternatives: putting the loader in `:core:data` (it builds no HTTP client of its own today, and only hands the player MediaRequests' Call.Factory, and the rule is app-wide configuration, like the logger's writers in `:app`), or routing images through the YouTube layer's downloader (that would make `:core:ui` depend on the layer, or need a repository for each image; the maintainer's answer allows direct image requests when the layer supplies the links).

### Picking a size

`List<Thumbnail>.forWidth(widthPx)` in `:core:model`, a pure extension function, follows the spec's rule: among sizes with a width, the smallest that is at least as wide, or the widest; when no size has a width, the first one; null for an empty list. JVM tests cover each case of the spec, ties, and an empty list. The composables compute the width in pixels from the box's width in dp, the font scale they already use (never below 1), and the screen's density, so the choice doesn't wait for layout. That arithmetic is a small pure function in `:core:ui`, `imageWidthPx(widthDp, fontScale, density)`, rounded up, with a JVM test.

### Placeholders under the image

The image draws on top of the existing placeholder instead of replacing it:

- VideoRowItem gains a `thumbnails: List<Thumbnail>` parameter. Its box keeps its background, and an AsyncImage with `ContentScale.Crop` fills it, under the length tag. AsyncImage draws nothing while loading or after a failure, so the box shows through, with no placeholder or error painter to keep in step with the theme.
- `ChannelAvatar(name, avatars, size)` in `:core:ui` draws ChannelEmblem with `initialOf(name)` and the image over it, clipped to the circle, then the ring again on top so the picture doesn't cover it. It replaces ChannelEmblem at every call site the spec names. The Takeout report keeps ChannelEmblem.
- Both images have `contentDescription = null`. The emblem already clears its semantics, and the row's merged text is what screen readers read.
- No crossfade. A fade adds motion to every row on every scroll, and the design gives none.

When the list is empty, the composable asks Coil for nothing: a pure function in `:core:ui`, `imageLink(thumbnails, widthPx)`, returns the chosen size's link or null, with a JVM test for the empty list, and the composable adds an AsyncImage only for a link.

### Data for each screen

- Search YouTube: VideoRow gains `thumbnails`, which the reducer copies from VideoSummary. ChannelRow already has avatars.
- Channel page: UploadRow gains `thumbnails`. The header uses `shownChannel`'s avatars, which the page's load or the subscription already fills.
- Inbox: `InboxIntent.Loaded` gains the subscribed channels' avatars by channel ID, which the ViewModel reads from the same `subscribedChannels()` flow that gives the count, and InboxGroup gains `channelAvatars`. A channel without saved avatars gets an empty list. Reducer tests cover the mapping, and the ViewModel test covers an avatar change reaching the state.
- Channels screen: reads the avatars from ChannelOverview's Channel. No state change.
- Video screen: VideoPage gains `channelAvatars` from VideoDetails, and PlayerReducer's KDoc drops "which no image shows yet" for them.

### Previews and screenshot tests

The previews' sample data gives image links on a made-up host, `https://images.example.invalid/…`, with made-up names, never a real video ID or a YouTube host, because the screenshot references are public (docs/design/brief.md). The preview handler never fetches them.

`:core:ui` gets `PreviewImages`, a composable that provides Coil's `LocalAsyncImagePreviewHandler` with a fixed, made-up image: a bitmap filled with the theme's `primary` color role, which differs from both placeholders' colors, so a screenshot tells image from placeholder at a glance. Each changed screen's previews wrap their content in it, so the existing @PreviewTest wrappers stay one call each, and every theme variant (light, dark, large font, dynamic color with the fixed wallpaper) shows the image. Previews whose rows have no image links show the placeholder, so one preview per screen keeps that state covered. AsyncImage uses the handler only in inspection mode ([LocalAsyncImagePreviewHandler.kt and internal/utils.kt at 3.6.3](https://github.com/coil-kt/coil/blob/3.6.3/coil-compose-core/src/commonMain/kotlin/coil3/compose/LocalAsyncImagePreviewHandler.kt), and [Compose](https://coil-kt.github.io/coil/compose/), checked on November 26, 2026), so the app never sees it. Without a handler, a preview would try the request as normal, so screenshots stay off the network for two reasons: every preview with links installs PreviewImages, and no network fetcher is on `:core:ui`'s or a feature module's classpath, because coil-network-okhttp is only in `:app`. The new ADR says so, so a later move of that dependency is checked.

### Offline, later

Coil loads a `java.io.File` without the network fetcher, so ImageHostRule wouldn't apply to it. ImageLinkRule refuses a File for now, because the spec allows only links from the YouTube layer. When #256 saves image files, it lets ImageLinkRule allow a File, which only the app's code makes, and changes the spec, while `file://` and `content://` strings stay refused. #54 stores no image files: its downloads table keeps a JSON snapshot of the video's thumbnail links, as the `videos` table does, so a downloaded video's row passes those links to the same loader, and the disk cache serves them offline when it still holds them. When #256 saves thumbnail files, the row can take the file first and fall back to the links. This change leaves VideoRowItem's image source behind one private function, so such a task adds a parameter without touching the box or the tag. #54 will add a download status slot at the end of the row's meta line, apart from the box. Whichever of the two lands second rebases, as PR #255's design says. This change will likely land first, so #54 builds on the `thumbnails` parameter. Both may touch the manifest's InitializationProvider and app startup, so a rebase may meet a conflict there.

## Risks / Trade-offs

- [AGENTS.md's architecture rule says features reach YouTube only through `:core:data`'s repositories] → Image loads become its one exception, written into that rule, the new ADR and docs/architecture.md's Network access: a screen hands `:core:ui` links that the layer gave, and `:app`'s loader fetches them, behind ImageLinkRule and ImageHostRule. Coil makes no request of its own, which that section's list of libraries says.

- [YouTube moves images to another host] → The rule refuses it and rows show placeholders. The live YouTube check fails with a reason of its own when a set's thumbnail or avatar link isn't on the three hosts, so the daily check catches it for search and uploads, the hand run for video details, and breakage.md's steps apply. Adding a host is a one-line change to the list in `:core:model` with its test, which docs/testing.md's IMAGE_HOST_UNKNOWN row says.
- [Images on mobile data] → A thumbnail at 72dp asks for the smallest size that fits the phone's density, usually one of YouTube's small sizes of a few KB, and the disk cache avoids loading it twice.
- [The cache reveals what the user browsed] → It stays in the app's private cache folder, which other apps can't read and backups leave out. PRIVACY.md says so.
- [Coil's OkHttp is 4.12.0 ([coil-network-okhttp-android POM](https://repo1.maven.org/maven2/io/coil-kt/coil3/coil-network-okhttp-android/3.6.3/coil-network-okhttp-android-3.6.3.pom), checked on November 26, 2026) and the app ships 5.5.1] → No source says that Coil 3.6.3 was tested with OkHttp 5. The build resolves one OkHttp, 5.5.1, the app's JVM test builds the real client, and the hand check on a phone loads real images through it. A failure there would show as placeholders, and the hand check would catch it.
- [Screenshots change for every list] → Expected. Each changed reference image is looked at before committing, as docs/testing.md asks.

## Migration Plan

No data migration. The tables already hold the image lists. Rolling back removes the images and the cache folder's use. Android clears an unused cache folder on its own.
