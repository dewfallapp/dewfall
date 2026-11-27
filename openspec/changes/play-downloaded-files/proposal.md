# Proposal

## Why

Phase 6's goal is "Download a video and watch it without internet" ([roadmap](../../../docs/roadmap.md#phase-6-manual-downloads)). Since #54, a video's files can be on the phone, but the player still streams every video, so a downloaded video can't play offline. [#56](https://github.com/dewfallapp/dewfall/issues/56) asks the player to play the downloaded files when they exist. #58's offline emulator test builds on it.

## What Changes

- **A downloaded video plays from the phone.** When the user plays a video whose download is done and whose files are all on the phone, the player plays those files instead of streaming. It asks YouTube for no stream links and fetches no bytes for it, so it plays with no connection, and it doesn't fetch the links again after hours, because files don't expire.
- **Anything less streams, as today.** A video that waits, downloads, is paused or failed streams, because its files are partial. So does a downloaded video whose files are missing or whose sizes don't match what the download recorded, or when the phone can't read its download. The download's status doesn't change in that case: it still says "Downloaded", and #55's Downloads screen can remove it.
- **Quality.** A downloaded video plays at the quality it was downloaded at, at most 720p, on any network. The network's limits, 480p on mobile data and 1080p on Wi-Fi, apply only to streaming.
- **When the choice is made.** The player picks the files or the stream when playback starts, as it picks formats today: when the video opens, after "Try again", and when expired links are fetched again. A video that streams while its download finishes goes on streaming until one of those.
- **Files that go away while playing.** If the player can no longer read the files, such as after the user removes the download, the video area says the video stopped playing, with "Try again", which streams it.
- **The details stay as they are.** The video screen's details still come from YouTube. Offline, the video plays from its files and the details area says that YouTube can't be reached, with "Try again", as the maintainer chose (answer 1). Showing the details saved with the download when YouTube can't be reached is [#262](https://github.com/dewfallapp/dewfall/issues/262), in Phase 6.
- **Privacy.** The files stay in the app's own storage, and no log line, media session or notification holds their paths.

## Capabilities

### New Capabilities

None.

### Modified Capabilities

- `player`: a new requirement that a downloaded video plays from its files, the quality rule applies only to streaming, the failure requirement covers a downloaded video with the network off and files that go away, and the privacy requirement says a downloaded video's playback sends nothing.
- `downloads`: the Purpose already says a download plays without internet. One requirement says that only a finished download, with all its files, plays, and that a partial one never does.

## Impact

- `:core:data`: a lookup of a downloaded video's complete files, PlayableDownloads, over the existing DownloadDao query and DownloadFiles, with JVM tests. No database change.
- `:core:playback`: PlaybackResolver asks the lookup before the stream links. The item's tag and PlaybackSources gain a plan for local files, played with Media3's FileDataSource, which comes with media3-exoplayer, so no new dependency. The service uses a local wake lock, without the Wi-Fi lock, while a file plays. JVM tests for the resolver, the source plan and the wake mode.
- `:app`'s androidTest: an emulator test that downloads the test video through the fakes and plays it with no media request, and one that streams when a downloaded file is missing.
- docs/architecture.md: the player's sequence and sections, and "What later tasks add"; docs/testing.md: the new emulator tests and the hand checks.
- One pull request, which closes #56 and archives this change as its last commit.

## The maintainer's answers

In this change's Claude Code session's question dialog, on November 27, 2026:

1. "#56 (play from the downloaded file): offline, a downloaded video will play from its file, but the video screen's details (title, channel, description) come from YouTube, so below the playing video the screen would say "YouTube can't be reached" with Try again. What should the details area show offline for a downloaded video?" Answer: "Keep the failure (Recommended)", whose option said: "#56 changes playback only. Offline, the video plays from its file and the details area says YouTube can't be reached, as today. I file a Phase 6 issue to show the details saved with the download (title, channel, publish time, no description) when YouTube can't be reached."
