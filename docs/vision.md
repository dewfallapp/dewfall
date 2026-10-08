# Dewfall vision

This page says what Dewfall is, who it is for, and what it will not do. If a feature does not fit on this page, it does not belong in the first version.

## What Dewfall is

Dewfall is a YouTube client for Android that works like a podcast app. You pick the channels you care about. While your phone charges on Wi-Fi at night, the app downloads their new videos. In the morning they play instantly, with or without internet.

There is no recommendation feed. The app helps people watch what they chose and then put the phone down.

The name comes from dew. It forms quietly overnight and is there in the morning, the same way new videos arrive in the app.

## Who it is for

- People who feel YouTube takes too much of their time.
- People who learn from long videos, such as lectures, tutorials and podcasts.
- People with slow or expensive mobile data.

## The first version

The first version has four parts.

1. **Channels delivered.** New uploads from your channels download overnight, with a storage limit and automatic cleanup.
2. **A calm home screen.** An inbox of new videos from your channels, with no suggestions and no Shorts.
3. **Search inside videos.** The app saves the subtitles of every watched video, so you can search for something that was said and jump to that moment.
4. **Normal search and streaming.** Any video can be found and watched right away without downloading.

### Overnight downloads

Downloads run as background jobs, and only while the phone is on Wi-Fi and charging. If a job fails, it retries on its own. The app does not run constantly.

### Cleanup

A video counts as watched at about 90 percent, and the next night's cleanup deletes it. You can change this to right away, after a week, or never. You can also mark any video as "keep".

When the storage limit is reached, the app stops downloading. It does not delete unwatched videos to make room. Clearing unwatched videos after two weeks is an optional setting.

### Subtitle search

Subtitles stay saved after the video file is deleted, so search keeps working. Tapping an old result streams the video from that moment. Without internet, the app shows the matching text with a few lines around it.

## After the first version

### Channel suggestions

There is no feed. The app suggests channels only when you ask, through similar channels, "more like this" on a video, and a weekly list of channel suggestions that you turn on yourself. These suggestions come after the first release.

## Non-goals

- No recommendation feed.
- No accounts.
- No ads.
- No analytics.

These stay out unless this document is changed on purpose.
