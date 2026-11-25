# 0011. Images load with Coil from YouTube's image hosts

## Status

Accepted

## Context

Lists showed a plain box where a video's thumbnail goes, and a circle with the channel's initial where its picture goes (#176). The YouTube layer already returns every size of each image, as links. Compose has no image loader of its own.

The images live on YouTube's image hosts, not on the hosts that the YouTube layer's client allows. AGENTS.md said that network calls go to YouTube through the YouTube layer, and that features reach YouTube only through `:core:data`'s repositories. ADR 0009 says that changing the HTTP client needs a new ADR, and that every request to YouTube asks for British English.

On November 22, 2026, in the coordinating session, the maintainer answered "no I agree with you" to this recommendation: "#176, thumbnails, moves to Phase 6. Thumbnails may load from i.ytimg.com, counted as "YouTube" under the product rule, as long as the YouTube layer supplies the links." On November 26, 2026, in the question dialog of #176's session, the maintainer approved the OpenSpec change list-images, which names the three hosts below, the 64 MiB cache and Coil 3.6.3, and answered that images load on any connection.

## Decision

**Coil 3 loads every image.** Coil is one of the two loaders that Android's guidance names for Compose ([Loading images](https://developer.android.com/develop/ui/compose/graphics/images/loading), checked on November 26, 2026). It is written in Kotlin with coroutines, and it loads links and files through one API. Coil 3.6.3 came out on September 18, 2026, under Apache 2.0. Its only change since 3.6.2 fixes builds with AGP 9.4's R8 ([CHANGELOG at 3.6.3](https://github.com/coil-kt/coil/blob/3.6.3/CHANGELOG.md), checked on November 26, 2026), and the app builds with AGP 9.5.1. The alternatives were Glide, whose Compose artifact has only pre-releases ([Maven Central](https://repo1.maven.org/maven2/com/github/bumptech/glide/compose/), newest 1.0.0-beta11, checked on November 26, 2026), Landscapist, which wraps Coil or Glide or offers a newer engine of its own ([its README](https://github.com/skydoves/landscapist), checked on November 26, 2026), and a hand-written loader, which would have to solve sizing, caching and cancellation again.

**Only YouTube's image hosts.** The app loads an image only from an `https` link with no user info and the default port, whose host is exactly i.ytimg.com, yt3.ggpht.com or yt3.googleusercontent.com. `isYouTubeImageLink` in `:core:model` decides it, next to the list of the three hosts. Exact names, because the saved YouTube responses hold image links on these three only, and a suffix rule would let a forged link reach other Google hosts.

**Two checks.** In `:app`, ImageLinkRule, a Coil interceptor, runs before anything else for every request. It lets through a `java.io.File`, which only the app's own code makes, for #256, and a link only when `isYouTubeImageLink` allows it. So a `file:`, `content:`, `data:` or `android.resource:` link never reaches Coil's own fetchers. It fails any other request with RefusedImageLink, whose message is constant text. Behind it, the image client's ImageHostRule checks each request's URL again, before DNS or a connection.

**A second HTTP client, outside the YouTube layer.** The image client is Dewfall's own OkHttpClient, apart from the layer's: it follows no redirects, keeps no cookies and no cache of its own, sends OkHttp's own User-Agent, and asks for British English with `Accept-Language: en-GB`. It names no country: an image's bytes hold no text, and the image links need no country. OkHttp's User-Agent was kept on purpose: the image hosts don't need the layer's headers, and a client that looks like a browser would claim to be something it isn't. The layer's own client, and ADR 0009's decision about it, stay as they are. The layer's interfaces don't change: it gives the links, and the screens pass them to `:core:ui`.

**The loader.** An App Startup initializer in `:app`, ImageLoaderInitializer, sets Coil's singleton, so the emulator tests, which run under HiltTestApplication, use the same loader as the app. The loader finds no fetcher on its own (`serviceLoaderEnabled(false)`), has only the network fetcher with the image client, keeps up to 64 MiB of images in the app's cache folder, and sets no logger, so no image link reaches a log (ADR 0007).

**The exception to the architecture rule.** Image loads are the one way a feature reaches YouTube without a `:core:data` repository: a screen hands `:core:ui` the links that the layer gave, and `:app`'s loader fetches them. Coil makes no request of its own.

**Previews.** Screenshot references are public, so they never show a real image. Previews give image links on a made-up host, and `:core:ui`'s PreviewImages draws every image as a plain fill of the theme's `primary` color, where the design's public frames show the initial or the plain box. Coil uses that handler only in previews. Screenshots stay off the network for a second reason too: only `:app` has a network fetcher, so `:core:ui` and the feature modules have none on their classpath. A change that moves coil-network-okhttp into another module has to keep it that way.

## Consequences

- Rows show real thumbnails and channel pictures. While an image loads, when it can't, and when there is none, the design's placeholders show.
- If YouTube moves images to another host, rows show placeholders. The live YouTube check fails with IMAGE_HOST_UNKNOWN, and the fix is a host in `:core:model`'s list with its test.
- The app sends image requests straight to the three hosts. PRIVACY.md says what they carry, and that the cache stays on the phone.
- Coil 3.6.3 asks for OkHttp 4.12.0, and the app ships OkHttp 5.5.1. The build resolves one OkHttp, and the hand check loads real images through it.
- Thumbnails of downloaded videos stay on the phone only while the cache holds them. #256 tracks keeping them.
- Changing the hosts, the loader or the image client needs a note here or a new ADR.
