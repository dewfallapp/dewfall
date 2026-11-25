# Design

## Context

See proposal.md for why. What this change builds on:

- The models already hold every size of each image. VideoSummary and VideoDetails have `thumbnails`, Channel has `avatars`, and VideoDetails has the channel's avatars, all as lists of Thumbnail (URL, and width and height when YouTube gives them). The `videos` and `channels` tables keep both lists as JSON, so saved videos and subscribed channels have them too (docs/architecture.md, Database tables).
- Thumbnail's KDoc and ADR 0007 count its URL as user data, so it is never logged.
- The rows that show a placeholder today: VideoRowItem in `:core:ui` (Search YouTube, the inbox, the channel page) draws a 72dp-wide 16:9 box on `surface-container-highest`, scaled with the font size. ChannelEmblem in `:core:designsystem` draws a circle on `tertiary-container` with a 1dp `outline-variant` ring and the initial, scaled with the font size. Search YouTube's channel rows, the inbox's cards, the Channels screen, the channel page's header, the video screen's channel row and the Takeout report use it.
- What each screen's state carries today: Search YouTube's ChannelRow has `avatars`, its VideoRow has no thumbnails. The channel page's `shownChannel` is a Channel with avatars, its UploadRow has no thumbnails. The inbox's InboxGroup has the channel's ID and name, and its videos are VideoSummary with thumbnails. The Channels screen has ChannelOverview, whose Channel has avatars. The video screen's VideoPage drops the avatars, as PlayerReducer's KDoc says.
- The design system: VideoRow's thumbnail "shows the cached thumbnail image; the placeholder is a plain `surface-container-highest` fill", and the `tertiary-container` token is the "channel emblem placeholder (the initial avatar) when no channel image is cached". The brief keeps real thumbnails, faces and channels out of public designs, and screenshot references in this repository are public.
- The saved YouTube responses in `core/youtube/src/test/resources/youtube/` hold image links on three hosts only: i.ytimg.com for video thumbnails, and yt3.ggpht.com and yt3.googleusercontent.com for avatars. NewPipeExtractor gives them as HTTPS links.
- #54, running in parallel, adds a download button to the video row and the video screen, and later keeps thumbnails of downloaded videos on the phone.

## Goals / Non-Goals

**Goals:**

- Real images in every list the proposal names, with the design's placeholders unchanged underneath.
- A host rule that one place enforces and a JVM test pins.
- Screenshot tests that never touch the network and never hold a real thumbnail.
- A loader that #54 can point at a file on the phone without a new library.

**Non-Goals:**

- Thumbnails of downloaded videos when offline, beyond what the cache happens to hold. #54 saves them.
- A large thumbnail on the video screen, such as a poster before playback starts. The player already shows the video.
- The Takeout import report's rows. Their channels were just imported and have no avatars yet, so they keep the emblem.
- A setting to turn images off or limit them to Wi-Fi. No spec asks for one, and the rows are small.
- Prefetching images ahead of the visible rows beyond what the lazy lists compose.

## Decisions

### Coil 3 as the image library

Compose has no image loader of its own. Coil is one of the two loaders that Android's own guidance names for Compose, with Glide ([Loading images](https://developer.android.com/develop/ui/compose/graphics/images/loading), checked on November 26, 2026), is written in Kotlin with coroutines, and loads links, files and content URIs through one API, so #54 can hand it a saved file later.

- Version 3.6.3, released on September 18, 2026, by the Last-Modified date of its POM on Maven Central, 69 days before November 26, 2026. Apache 2.0.
- Artifacts: `io.coil-kt.coil3:coil-compose` in `:core:ui`, and `io.coil-kt.coil3:coil-network-okhttp` in `:app`. Their POMs bring coil-core, coil-android, coil-compose-core, coil-network-core, Okio, kotlinx-coroutines, OkHttp (which the app already ships at 5.5.1, and Gradle keeps the higher version), Accompanist's drawablepainter, and AndroidX artifacts (lifecycle-runtime, core-ktx, annotation, appcompat-resources, exifinterface, profileinstaller), plus JetBrains' Compose wrappers at 1.12.0 (foundation, foundation-layout, animation, runtime, ui, ui-text, ui-util), which depend on the AndroidX Compose artifacts of the same version. The Compose BOM 2026.09.00 pins AndroidX Compose at 1.12.1, so the app's Compose doesn't change, but the wrappers are new artifacts. Every one is Apache 2.0. The pull request lists each artifact that `./gradlew :app:dependencies --configuration releaseRuntimeClasspath` shows as new, and each version that Coil raises, with its license and release date for the 14-day rule, under "New dependencies".
- Coil sends nothing anywhere on its own: no analytics, no reporting. It reaches the network only through the fetcher that the app gives it.

Alternatives: Glide (Java, View-first, its Compose integration is still beta), Landscapist (a wrapper over Coil or Glide, one more layer), or a hand-written loader with OkHttp and BitmapFactory (sizing, caching, memory and cancellation are the hard parts, and Coil already does them).

### The image loader lives in `:app`, with its own HTTP client

DewfallApplication implements Coil's `SingletonImageLoader.Factory`, so every AsyncImage uses one ImageLoader that `:app` builds:

- Coil's ServiceLoader discovery is turned off, and the only network fetcher is `OkHttpNetworkFetcherFactory` with Dewfall's own OkHttpClient, so no other fetcher can register itself.
- That client has an application interceptor, ImageHostRule, that lets a request through only when its scheme is `https` and its host is exactly `i.ytimg.com`, `yt3.ggpht.com` or `yt3.googleusercontent.com`. Any other request fails with an IOException whose message is constant text and names no URL, before DNS or a connection. Exact names, not suffixes, because the saved responses show only these three, and a wider rule would let a forged link reach any Google host.
- `followRedirects(false)` and `followSslRedirects(false)`, so a redirect can't lead the request elsewhere. Coil treats the 3xx as a failure, and the placeholder stays.
- An `Accept-Language: en-GB` header on every request, from a second interceptor, because the maintainer counts the image hosts as YouTube, and AGENTS.md and ADR 0009 say every request to YouTube asks for British English. It names no country, as MediaRequests and the subtitle download don't (ADR 0009's notes): the bytes hold no text, and the links already carry what the call that made them asked for.
- No cookie jar (OkHttp's default keeps none), no cache of OkHttp's own, and OkHttp's own User-Agent. It is a separate client from the YouTube layer's, which `:app`'s main code doesn't see, and which carries YouTube's API headers that image hosts don't need. ADR 0009 says that changing the layer's HTTP client needs a new ADR, so ADR 0010 records this second client outside the layer, why the maintainer's answer of November 22, 2026 allows it, and that the layer's own client decision stands.
- A disk cache of 64 MiB in `cacheDir/images`, and Coil's default memory cache. Coil's default cache policy keeps an image until the cache drops it, whatever headers the host sends, which suits thumbnails that don't change.
- No Coil logger, so Coil writes nothing to logcat, and no image URL can reach a log (ADR 0007). A failed image needs no line of its own: the placeholder already shows it, and the YouTube layer's lines cover the calls that gave the links.

ImageHostRule and the language interceptor are plain classes in `:app` with JVM tests that run them against made-up requests through a fake Interceptor.Chain, with no network.

Alternatives: putting the loader in `:core:data` (it has no OkHttp and no network code today, and the rule is app-wide configuration, like the logger's writers in `:app`), or routing images through the YouTube layer's downloader (that would make `:core:ui` depend on the layer, or need a repository for each image; the maintainer's answer allows direct image requests when the layer supplies the links).

### Picking a size

`Thumbnail.forWidth` in `:core:model`, a pure function over `List<Thumbnail>` and a width in pixels, follows the spec's rule: among sizes with a width, the smallest that is at least as wide, or the widest; when no size has a width, the first one; null for an empty list. JVM tests cover each case of the spec, ties, and an empty list. The composables compute the width from the box's width in dp, the font scale they already use, and the screen's density, so the choice doesn't wait for layout.

### Placeholders under the image

The image draws on top of the existing placeholder instead of replacing it:

- VideoRowItem gains a `thumbnails: List<Thumbnail>` parameter. Its box keeps its background, and an AsyncImage with `ContentScale.Crop` fills it, under the length tag. AsyncImage draws nothing while loading or after a failure, so the box shows through, with no placeholder or error painter to keep in step with the theme.
- `ChannelAvatar(name, avatars, size)` in `:core:ui` draws ChannelEmblem with `initialOf(name)` and the image over it, clipped to the circle, then the ring again on top so the picture doesn't cover it. It replaces ChannelEmblem at every call site the spec names. The Takeout report keeps ChannelEmblem.
- Both images have `contentDescription = null`. The emblem already clears its semantics, and the row's merged text is what screen readers read.
- No crossfade. A fade adds motion to every row on every scroll, and the design gives none.

When the list is empty, the composable asks Coil for nothing.

### Data for each screen

- Search YouTube: VideoRow gains `thumbnails`, which the reducer copies from VideoSummary. ChannelRow already has avatars.
- Channel page: UploadRow gains `thumbnails`. The header uses `shownChannel`'s avatars, which the page's load or the subscription already fills.
- Inbox: `InboxIntent.Loaded` gains the subscribed channels' avatars by channel ID, which the ViewModel reads from the same `subscribedChannels()` flow that gives the count, and InboxGroup gains `channelAvatars`. A channel without saved avatars gets an empty list. Reducer tests cover the mapping, and the ViewModel test covers an avatar change reaching the state.
- Channels screen: reads the avatars from ChannelOverview's Channel. No state change.
- Video screen: VideoPage gains `channelAvatars` from VideoDetails, and PlayerReducer's KDoc drops "which no image shows yet" for them.

### Previews and screenshot tests

`:core:ui` gets `PreviewImages`, a composable that provides Coil's `LocalAsyncImagePreviewHandler` with a fixed, made-up image: a bitmap filled with the theme's `primary` color role, which differs from both placeholders' colors, so a screenshot tells image from placeholder at a glance. Each changed screen's previews wrap their content in it, so the existing @PreviewTest wrappers stay one call each, and every theme variant (light, dark, large font, dynamic color with the fixed wallpaper) shows the image. Previews whose rows have no image links show the placeholder, so one preview per screen keeps that state covered. AsyncImage uses the handler only in inspection mode, so the app never sees it.

### Offline, later

Coil loads a `java.io.File` or a `file://` or `content://` URI without the network fetcher, so ImageHostRule doesn't apply to them. #54 stores no image files: its downloads table keeps a JSON snapshot of the video's thumbnail links, as the `videos` table does, so a downloaded video's row passes those links to the same loader, and the disk cache serves them offline when it still holds them. If a later task saves thumbnail files, the row can take the file first and fall back to the links. This change leaves VideoRowItem's image source behind one private function, so such a task adds a parameter without touching the box or the tag. #54 will add a download status slot at the end of the row's meta line, apart from the box, and builds on this change's `thumbnails` parameter, as the two sessions agreed.

## Risks / Trade-offs

- [YouTube moves images to another host] → The rule refuses it and rows show placeholders. The daily YouTube check counts thumbnails, not hosts, so the hand check in docs/testing.md looks at real images, and breakage.md's steps apply. Adding a host is a one-line change with its test.
- [Images on mobile data] → A thumbnail at 72dp asks for the smallest size that fits the phone's density, usually one of YouTube's small sizes of a few KB, and the disk cache avoids loading it twice.
- [The cache reveals what the user browsed] → It stays in the app's private cache folder, which other apps can't read and backups leave out. PRIVACY.md says so.
- [Coil's OkHttp is 4.12.0 and the app ships 5.5.1] → No source says that Coil 3.6.3 was tested with OkHttp 5. The build resolves one OkHttp, 5.5.1, the app's JVM test builds the real client, and the hand check on a phone loads real images through it. A failure there would show as placeholders, and the hand check would catch it.
- [Screenshots change for every list] → Expected. Each changed reference image is looked at before committing, as docs/testing.md asks.

## Migration Plan

No data migration. The tables already hold the image lists. Rolling back removes the images and the cache folder's use. Android clears an unused cache folder on its own.
