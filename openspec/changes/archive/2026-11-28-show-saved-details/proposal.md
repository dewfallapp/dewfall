# Proposal

## Why

Phase 6's goal is "Download a video and watch it without internet" ([roadmap](../../../../docs/roadmap.md#phase-6-manual-downloads)). Since #56, a downloaded video plays from its files offline, but the video screen's details still come only from YouTube. So offline, under a video that plays, the details area says that YouTube can't be reached, with "Try again", and the media notification and the mini player show no title or channel name. [#262](https://github.com/dewfallapp/dewfall/issues/262) asks the screen to show the details saved with the download instead: the title, the channel and the publish time.

## What Changes

- **Saved details when YouTube can't be reached.** When the video screen's request for the details fails because YouTube can't be reached, and the video has a download, the screen shows the details saved with that download: the title, the publish line, the chips and the channel. Any download counts, whatever its state, because the summary is saved when the download is asked for. A failure for any other reason shows as today (answer 3).
- **The description.** The download keeps no description. In the description's place, the screen shows the failure's message as today, "YouTube can't be reached. Check your connection.", with "Try again", which asks YouTube again and shows the full details when it answers. (Answer 1.)
- **What the saved details leave out.** No channel picture, because the download keeps none, so the emblem with the initial shows. No "Live now", because a saved summary can't tell that a stream is on air, so the publish line counts from the saved time.
- **The chips.** The download chip and the watched chip show with the saved details, as with YouTube's, so the user can see that the video is downloaded, and mark it watched, offline. The download chip's taps work as with YouTube's details, with the saved summary.
- **The media notification, the mini player and fullscreen.** They take their title and channel name from the details that the screen shows, so offline they show the saved ones. (Answer 2.)
- **The download goes away.** If the download is removed while the saved details show, the screen shows the failure as today. The notification and the mini player keep the saved names for the rest of that playback, as they keep YouTube's.
- **Privacy.** Nothing new is kept or sent. The summary is already in the app's database, and the media session already holds a playing video's title and channel name.

## Capabilities

### New Capabilities

None.

### Modified Capabilities

- `player`: a new requirement for the saved details, and the requirements on failures, a downloaded video offline, the media notification, the mini player and fullscreen name them.

## Impact

- `:core:data`: DownloadsRepository gains `savedVideo(videoId)`, a Flow of the summary saved with the video's download, over the existing `DownloadDao.download` query. No database change. JVM tests.
- `:feature:player-logic`: PlayerState holds the saved summary, a new intent brings it, and DetailsArea gains `Saved`, which the reducer makes when a "can't be reached" failure and a saved summary meet, in either order. The download chip's rule and the shared-failure rule read it. Reducer tests.
- `:feature:player-ui`: the ViewModel follows the saved summary, describes the playback with the saved names, and downloads with the saved summary. The screen shows Saved, and fullscreen takes its title from it. ViewModel tests and screenshot tests in light, dark, large font and dynamic color.
- `:app`'s androidTest: PlayFromFilesTest's offline test checks the saved title and channel and the failure's message instead of the failure alone.
- The fakes of DownloadsRepository in the inbox's, the Downloads screen's and the player's tests gain the new function.
- docs/architecture.md: the player's sections; docs/roadmap.md: a line for #262 in Phase 6; docs/testing.md: the changed emulator test and a hand check.
- One pull request, which closes #262 and archives this change as its last commit. It builds on #56's PR #263, so it starts only after that one merges.

## The maintainer's answers

In this change's Claude Code session's question dialog, on November 28, 2026, after the maintainer subagent's first plan critique:

1. "#262: offline, a downloaded video's screen will show its saved title, publish line, chips and channel. The download keeps no description. What goes in the description's place?" Answer: "Failure message (Recommended)", whose option said: "The existing failure message, "YouTube can't be reached. Check your connection.", with "Try again", which loads the full details with the description. No new text."
2. "#262: should the media notification, the mini player and fullscreen show the saved title and channel name offline? Today they show none ("Now playing" in the mini player)." Answer: "Use saved names (Recommended)", whose option said: "They show the saved title and channel name, and keep them for the rest of that playback, as they keep YouTube's."
3. "#262: when should a downloaded video show its saved details instead of the failure?" Answer: "Can't be reached only (Recommended)", whose option said: "Only when YouTube can't be reached, as #262 says. "YouTube isn't loading right now" (blocked, or can't be read) shows the failure as today."
4. "#262: approve the OpenSpec change "show-saved-details" with your answers to the other questions? Any download (any state) counts; the download and watched chips show with the saved details; no channel picture or "Live now" offline; "Try again" shows loading, then YouTube's details or the saved ones again; removing the download brings back the failure. Build starts after the maintainer subagent says Build and #263 merges." Answer: "Approve (Recommended)", whose option said: "Build it as the change says, with the answers above." This is the approval that AGENTS.md's "Spec first" asks for.
