# Dewfall design brief

## The app

Dewfall is an Android app that works like a podcast app for YouTube. New videos from chosen channels download overnight while the phone charges on Wi-Fi, then play instantly, even offline. No recommendation feed. It is for people who want less YouTube time, learn from long videos, or have slow or expensive mobile data.

## Mood

Calm, quiet and uncluttered, like a podcast or reading app, not a video platform. It helps people watch what they chose, then put the phone down. Nothing is built to keep them watching. The inbox is a list you can finish. Empty means done, not a nudge to find more. The name comes from dew, which forms overnight and is there by morning.

## Rules

- Android phone app, Material 3, English only. Every screen in light and dark, portrait except the fullscreen player.
- Name colors by Material 3 role, such as primary, on-primary, surface and on-surface, and text by type scale, such as headline, title, body and label. Both map onto MaterialTheme.
- Dynamic color is on by default where the phone supports it. It swaps in wallpaper colors, so layouts must suit any palette, and color always pairs with an icon or text.
- Text wraps at large font sizes, never cut off.
- No YouTube logo or wordmark. Made-up channels, titles and plain placeholder thumbnails, never real channels, creators, faces or YouTube thumbnails, since the designs are public.

## Navigation

A navigation bar with four destinations: Inbox, Channels, Downloads and Settings. No count badges.

## Two searches

One search finds YouTube videos and channels. The other searches saved subtitles of watched videos. Users must always know which they are in.

A search button in the top bar of Inbox, Channels and Downloads opens a sheet with both searches. Each has a line saying what it covers:

- **Search YouTube.** Videos to stream and channels to follow. Needs the internet.
- **Search subtitles.** Moments in videos you have watched, from their saved subtitles. Works offline.

Settings has no search button.

## Screens

- **Onboarding.** How overnight downloads work. Google Takeout channel import, which reads only the subscriptions, as the screen promises: "Dewfall only needs the list of channels." Storage limit of 5, 10, 20 or 50 GB, with 10 GB preselected. Battery setup.
- **Inbox.** Home. New videos from subscribed channels, no suggestions or Shorts. Download buttons, downloaded badges, small marks for searchable subtitles. Manual refresh. No storage meter. Storage shows only as a banner when downloads pause at the limit, with "Raise limit" and "Manage downloads". Offer to download now, or stream, videos the overnight run missed.
- **Search.** Find videos to stream and channels to subscribe to, even without a Takeout file.
- **Player.** Portrait with title, channel and description. Fullscreen and mini. Download, keep from cleanup, mark as watched. The fullscreen player uses the dark scheme in both themes.
- **Channel page.** Latest uploads, subscribe and unsubscribe.
- **Downloads and storage.** The storage meter at the top, then downloads with progress, pause, cancel and remove.
- **Search inside videos.** Results show video, matching line and time, opening at that moment. Offline, text with a few lines around it.
- **Download notification.** Overnight progress. When downloads pause at the limit, the notification says so, with "Raise limit" and "Manage downloads", like the inbox banner.
- **Settings.** Storage limit. Delete watched videos next night by default, right away, after a week, or never. Clear unwatched videos after two weeks, off by default. How long to keep subtitles, forever by default. Data export and import. Copy debug info. Opt-in toggle to check GitHub for updates, GitHub build only. Overnight downloads always wait for Wi-Fi and charging, with no mobile data option.
- **Empty, error and offline states.** No channels yet, all caught up, no internet, YouTube not loading, downloads paused at the limit, no search results.

## Not in this round

Channel suggestions, playback speed, silence skipping, audio-only default, sleep timer, blocking, time limits, notes, add-ons like SponsorBlock, and the app icon.

## Open questions

- How far back the first overnight run reaches after an import. The prototype queues up to two recent videos per channel, and the Phase 5 and Phase 7 OpenSpec changes will decide it.
