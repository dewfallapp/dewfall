# Dewfall design brief

## The app

Dewfall is an Android app that works like a podcast app for YouTube. New videos from chosen channels download overnight while the phone charges on Wi-Fi, then play instantly, even offline. No recommendation feed. It is for people who want less YouTube time, learn from long videos, or have slow or expensive mobile data.

## Mood

Calm, quiet and uncluttered, like a podcast or reading app, not a video platform. It helps people watch what they chose, then put the phone down. Nothing is built to keep them watching. The inbox is a list you can finish. Empty means done, not a nudge to find more. The name comes from dew, which forms overnight and is there by morning.

## Rules

- Android phone app, Material 3, English only. Every screen in light and dark, portrait except the fullscreen player.
- Name colors by Material 3 role, such as primary, on-primary, surface and on-surface, and text by type scale, such as headline, title, body and label. Both map onto MaterialTheme.
- Dynamic color swaps in wallpaper colors. Layouts must suit any palette, so color always pairs with an icon or text.
- Text wraps at large font sizes, never cut off.
- No YouTube logo or wordmark. Made-up channels, titles and plain placeholder thumbnails, never real channels, creators, faces or YouTube thumbnails, since the designs are public.

## Two searches

One search finds YouTube videos and channels. The other searches saved subtitles of watched videos. Users must always know which they are in.

## Screens

- **Onboarding.** How overnight downloads work, Google Takeout channel import, storage limit, battery setup.
- **Inbox.** Home. New videos from subscribed channels, no suggestions or Shorts. Storage indicator on top. Download buttons, downloaded badges, small marks for searchable subtitles. Manual refresh. Storage-limit banner when downloads pause, with buttons to manage downloads or raise the limit. Offer to download now, or stream, videos the overnight run missed.
- **Search.** Find videos to stream and channels to subscribe to, even without a Takeout file.
- **Player.** Portrait with title, channel and description. Fullscreen and mini. Download, keep from cleanup, mark as watched.
- **Channel page.** Latest uploads, subscribe and unsubscribe.
- **Downloads and storage.** Progress, pause, cancel, remove, space used.
- **Search inside videos.** Results show video, matching line and time, opening at that moment. Offline, text with a few lines around it.
- **Download notification.** Overnight progress and storage-limit message.
- **Settings.** Storage limit. Delete watched videos next night by default, right away, after a week, or never. Clear unwatched videos after two weeks. How long to keep subtitles. Data export and import. Copy debug info. Opt-in toggle to check GitHub for updates, GitHub build only. Overnight downloads always wait for Wi-Fi and charging, with no mobile data option.
- **Empty, error and offline states.** No channels yet, all caught up, no internet, YouTube not loading, downloads paused at the limit, no search results.

## Not in this round

Channel suggestions, playback speed, silence skipping, audio-only default, sleep timer, blocking, time limits, notes, add-ons like SponsorBlock, and the app icon.
