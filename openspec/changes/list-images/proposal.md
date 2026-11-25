# Proposal

## Why

Every list in Dewfall shows a plain box where a video's thumbnail goes, and a circle with a letter where a channel's picture goes. That makes a list of videos slow to scan, and two channels with the same first letter look alike. The YouTube layer already returns every size of each thumbnail and avatar, as docs/architecture.md describes. What's missing is loading the images ([#176](https://github.com/dewfallapp/dewfall/issues/176)).

## What Changes

- **Video thumbnails.** The video rows of Search YouTube, the inbox and the channel page show the video's thumbnail in the box, under the length tag, which stays.
- **Channel avatars.** The channel rows of Search YouTube, the inbox's cards, the Channels screen and the channel page's header show the channel's picture in the circle, with the ring the design gives it. The video screen's channel row does too.
- **While loading and on failure.** The design's placeholders stay: the plain `surface-container-highest` box and the emblem with the channel's initial. The image covers them once it has loaded. A missing, slow or failed image leaves the placeholder, with no error mark, and the rest of the row works as before. A channel with no saved avatar, such as one imported from a Takeout file before its first refresh, keeps its emblem.
- **Which size.** Each image asks for the smallest size that is at least as wide as the space it fills on the screen, in pixels, or the largest when none is that wide, as docs/architecture.md already plans.
- **Where images come from.** Images load only from the links that the YouTube layer gave, only over HTTPS, and only from YouTube's image hosts: i.ytimg.com, yt3.ggpht.com and yt3.googleusercontent.com. A link to any other host loads nothing. These requests go straight from the phone to those hosts, outside the YouTube layer, which the maintainer approved on November 22, 2026. They carry no cookies and no account, and nothing about the user beyond what any request carries, such as the phone's IP address.
- **On the phone.** Loaded images are kept in the app's cache folder, up to a fixed size, so a list shown again doesn't load them again. Android may clear that folder, and backups leave it out, as they leave out all app data.
- **The image library.** Coil 3, a new dependency. design.md records the license, release dates and every artifact it brings.

## Capabilities

### New Capabilities

- `list-images`: which rows show video thumbnails and channel avatars, what shows while an image loads or when it fails, which size each image asks for, and which hosts images may load from.

### Modified Capabilities

None. The specs of `inbox`, `subscriptions`, `youtube-search` and `player` describe a "thumbnail box" and a "round emblem with the first character of the channel's name". Both stay, as the placeholder under the image, so none of their requirements change. The new capability says what covers them.

## Impact

- New dependency: Coil 3.6.3 (`io.coil-kt.coil3:coil-compose`, `coil-network-okhttp`), Apache 2.0, with the artifacts design.md lists. It ships. It sends data only to the image hosts above, and only for links that the YouTube layer gave.
- `:core:model`: a helper that picks a thumbnail size for a width, with JVM tests.
- `:core:ui`: VideoRowItem takes the video's thumbnails and draws the image in its box, and a new ChannelAvatar draws a channel's picture over the design system's ChannelEmblem. A preview helper gives every preview a fixed, made-up image, so screenshots never need the network.
- `:app`: the app-wide image loader, with an HTTP client that refuses any host but the three above, follows no redirect and keeps no cookies, and a disk cache in the app's cache folder. JVM tests for the host rule.
- Feature modules: Search YouTube's video rows, the channel page's upload rows, the inbox's cards and the video screen's page carry thumbnails or avatars from the models, with reducer and ViewModel tests. The Channels screen already has them. Every changed screen's previews and screenshot tests show the fixed image in light, dark, large font and dynamic color.
- Docs: PRIVACY.md (image requests and the cache), docs/vision.md and AGENTS.md (the image hosts next to the YouTube layer in the network rule), docs/architecture.md (images, the dependency, the helper), a new ADR for image loading, and docs/testing.md (the hand check of real images).
- Offline: thumbnails of downloaded videos on the phone are #54's. The loader reads local files as well as links, so #54 can hand a row a saved file later without a new library.

## The maintainer's answers

On November 22, 2026, in the coordinating session, the maintainer answered "no I agree with you" to the coordinating session's recommendation: "#176, thumbnails, moves to Phase 6. Thumbnails may load from i.ytimg.com, counted as "YouTube" under the product rule, as long as the YouTube layer supplies the links." The coordinating session's brief for this change adds that the same answer covers the avatar hosts. That settles two of #176's open questions: the phase, and the image hosts.

On November 26, 2026, in this change's session's question dialog, the maintainer answered three questions:

1. "Plan for #176 (OpenSpec change list-images): video rows in Search YouTube, the inbox and channel pages show real thumbnails. Channel circles show the channel's picture in Search YouTube, inbox cards, the Channels screen and the channel page header. While an image loads, or if it fails, the current plain box or initial circle stays, with no error mark. Images load only from YouTube's image hosts (i.ytimg.com, yt3.ggpht.com, yt3.googleusercontent.com), over HTTPS, with no cookies and no redirects. They're cached on the phone (64 MiB). New library: Coil 3.6.3 (Apache 2.0). PRIVACY.md gets a section on this. Approve this plan?" Answer: "Approve (Recommended)". This is the approval that AGENTS.md's "Spec first" asks for.
2. "Should the video screen's channel row (under the player, next to the channel name) also show the channel's picture instead of the initial circle?" Answer: "Yes, there too (Recommended)".
3. "Should thumbnails and channel pictures load on mobile data too, or only on Wi-Fi?" Answer: "Any connection (Recommended)".
