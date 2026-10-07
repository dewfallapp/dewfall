# 0005. Download deletion rules

## Status

Accepted

## Context

Dewfall downloads new videos from the user's channels every night. Without cleanup, the downloads would keep growing until the phone runs out of space. The app needs rules for when a downloaded video is deleted, and for what happens when storage runs short.

Three questions shaped the rules.

**When does a video count as watched?** People often stop before the end screen, outro or credits. Requiring 100 percent would leave many finished videos marked as unwatched. About 90 percent is a starting value that can be tuned if users report problems.

**When is a watched video deleted?** Deleting it the next night gives the user a day to rewatch something or go back to a part they missed, so a file never disappears the moment a video ends. Cleanup also runs in the same overnight job as downloads, while the phone is charging, so the app does no file work during the day.

**What happens when storage is full?** An unwatched video is one the user still wants, and they may be counting on having it offline. Deleting it silently would break that trust. Stopping downloads is visible and easy to fix: the user watches or removes something, or raises the limit. The storage limit is a promise the app never breaks.

## Decision

- A video counts as watched at about 90 percent.
- By default, the following night's cleanup deletes watched videos.
- Users can change this to delete right away, after a week, or never.
- Users can mark any video as "keep", and cleanup never deletes it.
- Users set a storage limit. When the limit is reached, the app stops downloading. It does not delete unwatched videos to make room.
- When downloads stop because of the storage limit, the app always tells the user. It never fails silently.
- Clearing unwatched videos after two weeks is an optional setting.
- Subtitles of watched videos are saved separately and are not deleted with the video file, so search inside videos still works afterward.

## Consequences

- When the storage limit is reached, new videos stop arriving until space is freed.
- The planned way to tell the user is a banner at the top of the inbox saying downloads are paused because the storage limit was reached, with buttons to manage downloads or raise the limit. The overnight download notification says the same thing. Videos that did not download stay in the inbox and stream when tapped. The exact wording and layout are decided in the Phase 7 OpenSpec change.
- Search inside an old video still finds the moment. Tapping the result streams the video from there. Offline, the app shows the matching text with a few lines around it.
- Cleanup runs in the overnight background job, so it shares the same conditions as downloads: Wi-Fi and charging.
