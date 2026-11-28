# Design

## Context

See proposal.md for why. What exists on main once #56's PR #263 has merged:

- **The video screen.** PlayerViewModel, in `:feature:player-ui`, asks VideoDetailsRepository for the details once, and the reducer in `:feature:player-logic` puts the answer in `PlayerState.details`, a DetailsArea: `Loading(number)`, `Shown(page)` or `Failed(number, error)`. "Try again" after a failure whose error `offersTryAgain` starts request `number + 1`. The screen draws Shown as the title, the publish line, the chip row, the channel and the description; Failed as the failure's Banner.
- **What reads the shown page.** `PlayerState.shownDownload` shows the download chip only with Shown, for a video that isn't live. `sharesFailure` puts a playback failure of the same category into the details' message, whose "Try again" retries both. `describePlayback` in the ViewModel gives the playback the page's title and channel name through `PlaybackConnection.describe`, which the media notification and the mini player read from the session. The route gives fullscreen the Shown page for its title. The ViewModel keeps the Shown answer's VideoDetails as `shownDetails`, which the download chip's "Download" turns into the summary that `DownloadsRepository.download` saves.
- **What a download keeps.** A `downloads` row keeps the summary saved when the download was asked for: the ID, title, channel ID and name, length, publish time (with whether it is approximate), kind and thumbnail links. `DownloadDao.download(videoId)` is a Flow of the row, and RoomDownloadsRepository's `toDownload()` already turns a row into a VideoSummary. No description and no channel picture.
- **Offline playback.** Since #56, a finished download plays from its files, and the player spec's "Opening a downloaded video offline" says the details area then says YouTube can't be reached, with "Try again". #262 replaces that part.

## Goals / Non-Goals

**Goals:**

- Offline, a downloaded video's screen, notification, mini player and fullscreen show its saved title and channel name, and the screen its publish line and chips.
- The saved details never hide YouTube's: as soon as a request succeeds, YouTube's details show.
- No database change and no new dependency.

**Non-Goals:**

- Saving the description or the channel's picture with a download. The table has neither, and the database stays as it is.
- Showing saved details for a video with no download, such as one saved with a channel's uploads. The issue names downloads, and those videos can't play offline anyway.
- Trying again by itself when the connection comes back.

## Decisions

### 1. The saved summary comes from the download's row

DownloadsRepository gains `fun savedVideo(videoId: VideoId): Flow<VideoSummary?>`: the summary saved with the video's download, whatever the download's state, or null when the video has no download. RoomDownloadsRepository maps `downloadDao.download(videoId.value)` through the existing `toDownload().video`, with `distinctUntilChanged`. It is a Flow, as `status` is, so a download that is asked for or removed while the screen shows changes what it shows. Alternatives: reading `downloads()` and filtering would read every row on every change; one Flow of the whole Download, replacing `status`, would change the chip's code for no gain.

### 2. Which failures and which downloads

The saved details show only when the request failed with `YouTubeError.NETWORK`, "YouTube can't be reached", which is what #262 asks for. Any other failure shows as today: for PRIVATE, UNAVAILABLE and the like, YouTube's answer is newer than the saved summary; BLOCKED and BROKEN, which also offer "Try again", show the failure as today, as the maintainer chose (proposal.md, answer 3). The code takes the set of errors from one function. Any download row counts, whatever its state, because every row holds the summary from the moment it was asked for. While the saved summary can't be read, the failure shows as today. The ViewModel has no Logger, as today: a Flow that fails sends SavedVideoChanged(null) and writes no log line, and the download chip's own read reports the database's state.

### 3. The reducer makes the Saved area

PlayerState gains `savedVideo: VideoSummary? = null`, and PlayerIntent gains `SavedVideoChanged(video: VideoSummary?)`, which the ViewModel sends from `savedVideo(videoId)`, and with null when the Flow fails. DetailsArea gains `Saved(number: Long, error: YouTubeError, page: VideoPage)`: request `number` failed with `error`, and the screen shows the saved `page`.

After every intent the reducer settles the area with one rule, so the order in which the failure and the summary arrive doesn't matter:

- Failed with an error that shows saved details, and a saved summary: Saved, with the page made from the summary.
- Saved, and the summary is gone: Failed, with the same number and error.
- Saved, and the summary changed: Saved, with the new page.
- Anything else stays as it is.

"Try again" from Saved starts request `number + 1`, as from Failed, so the screen shows that it is loading, then YouTube's details, or Saved again. A late answer still counts only for its own request. The page made from a summary has the summary's title, channel ID, channel name and publish time, no channel pictures, an empty description and `isLive = false`, because a saved summary can't tell that a stream is on air.

A Saved area, rather than a function that the UI applies to Failed and the summary, keeps the screen a plain `when` over DetailsArea and keeps every rule in reducer tests.

### 4. What reads the Saved area

A new extension, `DetailsArea.page: VideoPage?`, gives the page of Shown or Saved. `shownDownload` and the ViewModel's `describePlayback` use it, so the download chip shows and the notification and the mini player get the saved names, as the route's fullscreen title does. `runSideEffects` calls `describePlayback` after SavedVideoChanged as well as after DetailsFinished, when the area's page changed, so the names reach the session whichever of the summary and the failure comes last, and a summary that changes while YouTube's details show describes nothing again. `describe` is never cleared, as today: once a playback has the saved names, the notification and the mini player keep them for that playback, even if the download is removed, as they keep YouTube's after a later failure. Clearing them would need a new PlaybackConnection call for no gain, because the names are the same video's. `sharesFailure` treats Saved like Failed, so when playback fails for the same reason, such as a partial download offline, the screen says it once, in the details, and its "Try again" retries both. The chip's "Download" uses `shownDetails` as today, or, while Saved shows, the saved summary; for a video that has a row, `download` keeps the row's summary anyway.

### 5. The screen

PlayerScreen draws Saved with the same Details composable as Shown, which gains a slot for what goes in the description's place: for Saved, the failure's Banner, with "Try again" as the error offers it. The channel row shows the emblem, because the page has no pictures. Because the Banner sits inside the scrolling details, a large font never cuts it off. No new strings: the Banner uses the failure's text. The Banner's "Try again" under Saved calls the same `onTryAgainDetails` as Failed's, which goes through the route's `last` and the ViewModel's `tryAgainDetails`, so when playback failed the same way, such as a partial download offline, it retries the playback too.

### 6. Tests

- `:core:data`: RoomDownloadsRepository tests with FakeDownloadDao: `savedVideo` gives the row's summary in each state, null with no row, a new value when the row is asked for or removed.
- `:feature:player-logic`: reducer tests: a NETWORK failure, then the summary, gives Saved; the summary, then the failure, gives Saved; every other error stays Failed; the summary's removal gives Failed with the same number; a changed summary gives a new page; "Try again" from Saved gives Loading(number + 1); success after Saved gives Shown; a late answer changes nothing; the page from a summary has no pictures, no description and isn't live. Tests for `page`, `shownDownload` and `sharesFailure` with Saved.
- `:feature:player-ui`: ViewModel tests with the fakes: offline with a download, the state is Saved and the playback is described with the saved names, both when the summary comes before the failure and after it; a failing `savedVideo` leaves Failed; "Download" while Saved downloads with the saved summary.
- Screenshot tests of the Saved screen in light, dark, large font and dynamic color.
- `:app`'s PlayFromFilesTest: offline, the screen shows the saved title and channel and the "can't be reached" message. Run on Android 17 and Android 10 on emulator-5556.
- A hand check on Android 17 with a real video: a downloaded video in airplane mode shows its saved details, the notification and the mini player show its title and channel name, and logcat holds no title.

Each test of a rule fails when that rule is taken out of the reducer.

## The spec delta

The MODIFIED requirements are copies of #263's living spec with only this change's sentences added. Because #263 isn't merged yet, the delta is rebuilt from main's living spec, once #263 merges, by a script that copies each requirement word for word and applies only this change's edits, failing if any edited sentence is no longer there; then `openspec validate show-saved-details --strict` must pass.

## Risks / Trade-offs

- [The saved title can be older than YouTube's] → It shows only while YouTube can't be reached, and "Try again" replaces it as soon as YouTube answers.
- [The emulator test's assertions change] → The old assertion said the details area shows only the failure, which this change replaces on purpose; the new one checks the saved title, the channel and the failure's message, so it checks more.

## The maintainer's answers

proposal.md quotes them: the failure's message with "Try again" in the description's place (1), the saved names in the notification, the mini player and fullscreen (2), saved details only when YouTube can't be reached (3), and approval of this change (4).
