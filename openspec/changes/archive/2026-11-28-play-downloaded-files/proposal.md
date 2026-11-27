# Proposal

## Why

Phase 6's goal is "Download a video and watch it without internet" ([roadmap](../../../../docs/roadmap.md#phase-6-manual-downloads)). Since #54, a video's files can be on the phone, but the player still streams every video, so a downloaded video can't play offline. [#56](https://github.com/dewfallapp/dewfall/issues/56) asks the player to play the downloaded files when they exist. #58's offline emulator test builds on it.

## What Changes

- **A downloaded video plays from the phone.** When the user plays a video whose download is done and whose files are all on the phone, the player plays those files instead of streaming. It asks YouTube for no stream links and fetches no bytes for it, so it plays with no connection, and it doesn't fetch the links again after hours, because files don't expire.
- **Anything less streams, as today.** A video that waits, downloads, is paused or failed streams, because its files are partial. So does a downloaded video whose files are missing or whose sizes don't match what the download recorded, or when a read error, such as a damaged database, stops the check of its download. A file that is there at its recorded size counts as on the phone even if it can't be opened, so it plays from the files and fails, as "Files that go away while playing" says. Checking the files never changes a download's status: a finished download whose files are missing or the wrong size still says "Downloaded", and #55's Downloads screen can remove it.
- **Quality.** A downloaded video plays at the quality it was downloaded at, at most 720p, on any network. The network's limits, 480p on mobile data and 1080p on Wi-Fi, apply only to streaming.
- **When the choice is made.** The player picks the files or the stream when playback starts, as it picks formats today: when the video opens, after "Try again", and when expired links are fetched again. A video that streams while its download finishes goes on streaming until one of those.
- **Files that go away while playing.** A file the player has open stays readable after it is deleted, such as by removing the download, so the video plays on from it. When the player has to open the file again, such as for a seek back past what it holds, the video area says the video stopped playing, with "Try again", which streams it once the file is gone. A file that is there but can't be read fails again on "Try again", until the download is removed.
- **The details stay as they are.** The video screen's details still come from YouTube. Offline, the video plays from its files and the details area says that YouTube can't be reached, with "Try again", as the maintainer chose (answer 1). Because the media notification and the mini player take their title and channel name from those details, offline they show none, as the player spec already says when details fail. Showing the details saved with the download when YouTube can't be reached, there too, is [#262](https://github.com/dewfallapp/dewfall/issues/262), in Phase 6.
- **Privacy.** The files stay in the app's own storage, and no log line, media session or notification holds their paths.

## Capabilities

### New Capabilities

None.

### Modified Capabilities

- `player`: a new requirement that a downloaded video plays from its files, the quality rule applies only to streaming, the failure requirement covers a downloaded video with the network off and files that go away, and the privacy requirements say a downloaded video's playback sends nothing and that the playback service holds the files' paths only while it plays.
- `downloads`: the Purpose already says a download plays without internet. One requirement says that only a finished download, with all its files, plays, and that a partial one never does.

## Impact

- `:core:data`: a lookup of a downloaded video's complete files, PlayableDownloads, over the existing DownloadDao query and DownloadFiles, with JVM tests. No database change.
- `:core:playback`: PlaybackResolver asks the lookup before the stream links. The item's tag and PlaybackSources gain a plan for local files, played with Media3's FileDataSource, which comes with media3-exoplayer, so no new dependency. The service uses a local wake lock, without the Wi-Fi lock, while a file plays. JVM tests for the resolver, the source plan and the wake mode.
- `:app`'s androidTest: emulator tests that download the test video through the fakes and play it offline with no media request, that stream it when a downloaded file is missing, and that fail on a seek after a file was deleted while playing, then stream on "Try again".
- docs/architecture.md: the player's sequence and sections, and "What later tasks add"; docs/testing.md: the new emulator tests and the hand checks.
- One pull request, which closes #56 and archives this change as its last commit.

## The maintainer's answers

In this change's Claude Code session's question dialog, on November 27, 2026:

1. "#56 (play from the downloaded file): offline, a downloaded video will play from its file, but the video screen's details (title, channel, description) come from YouTube, so below the playing video the screen would say "YouTube can't be reached" with Try again. What should the details area show offline for a downloaded video?" Answer: "Keep the failure (Recommended)", whose option said: "#56 changes playback only. Offline, the video plays from its file and the details area says YouTube can't be reached, as today. I file a Phase 6 issue to show the details saved with the download (title, channel, publish time, no description) when YouTube can't be reached."

After the maintainer subagent's third plan critique, which said "Verdict: Build", also in the question dialog, on November 28, 2026:

2. "#56: approve the OpenSpec change "play-downloaded-files" (draft PR #263; maintainer subagent said Build)? It plays a finished download from its files, with no stream-links or byte requests, at its downloaded quality on any network. Anything partial streams, as does a "Downloaded" video whose file is missing or the wrong size — its status stays "Downloaded" (#55 can remove it). A file deleted while playing plays on until the player reopens it (e.g. a seek), then "stopped playing" + Try again, which streams. Offline the details area and the notification/mini player keep showing no details (your answer 1; #262 follows up)." Answer: "Approve (Recommended)", whose option said: "Build it as the change says." This is the approval that AGENTS.md's "Spec first" asks for.

After the pull request's seventh review round, also in the question dialog:

3. November 28, 2026, after reviewer round 7. Question: "#56 (PR #263): the code and tests are settled, but reviewer rounds 5–7 and CodeRabbit keep finding new edge cases in the spec scenarios' wording. For example, a video paused in the mini player that then finishes downloading goes on streaming when opened, which "isn't already playing" doesn't cover. AGENTS.md says to stop and ask after two rounds of the same problem. How should I finish?" Answer: "One general rule (Recommended)", whose option said: "Fix round 7's four conflicts by adding one sentence to the requirements: the files-or-stream choice applies only when playback starts; a video the player already holds (playing, paused or failed) goes on as it is. Scenarios refer to that rule instead of each carrying qualifiers. Reviewer runs once more; wording-only edge cases after that go into a follow-up issue instead of blocking the merge."
