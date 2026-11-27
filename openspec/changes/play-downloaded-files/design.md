# Design

## Context

See proposal.md for why. What exists on main at a41d305:

- **Playback.** PlaybackService, in `:core:playback`, resolves a playback ID in `onSetMediaItems` through PlaybackResolver, which fetches stream links through StreamLinksRepository and picks formats with `chooseFormats`. The resolved MediaItem has a placeholder URI and a PlaybackTag, a plain class holding the FormatChoice and whether the network was metered. PlaybackSources, the player's MediaSource.Factory, turns the tag into a SourcePlan (Merged, Progressive, Hls or Refused) and builds ProgressiveMediaSources over OkHttpDataSource with the YouTube layer's MediaRequests. `refreshLinks` resolves again after a refused link (403, 410). The player has `C.WAKE_MODE_NETWORK`, which holds a wake lock and a Wi-Fi lock while it plays.
- **Downloads.** `:core:download` depends on `:core:playback`, for Decoders, so `:core:playback` can't depend on `:core:download`. Both depend on `:core:data`, which holds DownloadFiles (one folder per download under `noBackupFilesDir/downloads`, with `complete(folder, kind, mimeType)` naming `video.<ext>`, `audio.<ext>` or `media.<ext>`) and DownloadDao, whose `row(videoId)` reads one row. A DOWNLOADED row keeps its folder, the chosen formats' MIME types and sizes (null when YouTube gave none), and `downloadedBytes`, which VideoDownloader's `finish` sets to the sum of the complete files' lengths on disk. Its chooser used the player's own Decoders, so what it kept decodes here.
- **The fakes.** `:app`'s emulator tests replace the media clients with AssetMedia, which counts its requests per link and can refuse them all with FakeNetwork's `mediaReachable` off.

## Goals / Non-Goals

**Goals:**

- One check, in one place, of whether a video's download can play, used by every playback start, the refresh included.
- Playing from files changes no other player behavior: the session, the notification, saving the position and the picture off in the background stay as they are.
- Nothing of the files' paths reaches a log, the media session or the notification.

**Non-Goals:**

- The video screen's details offline: #262, as the maintainer chose in answer 1.
- Repairing a download whose files are missing, or changing its status: the Downloads screen (#55) can remove it, and Phase 7's cleanup deletes files.
- Switching a video that streams to its files in the middle of playback when its download finishes.

## Decisions

### 1. The lookup lives in `:core:data`

PlayableDownloads, an interface in `:core:data`'s download package, with one main-safe function, `playable(videoId): PlayableDownload`. Its Room implementation reads `DownloadDao.row`, then checks the files through DownloadFiles on an injected IO dispatcher (a qualifier, as Takeout's read dispatcher). It returns:

- `Files(media)`: the video and audio files, or the one combined file.
- `NotDownloaded`: no row, or a row that isn't DOWNLOADED.
- `Incomplete(reason)`: a DOWNLOADED row whose file is missing, not a regular file, empty, of another length than the row's recorded size when it has one, or whose files' lengths don't add up to `downloadedBytes`; or a row without the MIME types its files need. `reason` is an enum, MISSING or SIZE, so the log line can say which without naming a file.
- `Unreadable`: the database or the file system threw. Cancellation still propagates.

The check is a function of the row and a small file-reading interface, so its JVM tests run against a temporary folder. `:core:data` already owns DownloadFiles and the DAO, which keeps the knowledge of how files are named in one module, and `:core:playback` already depends on `:core:data`. Alternatives: the lookup in `:core:download` would make a module cycle; reading the DAO in `:core:playback` would put database and file-naming knowledge in the player. No DAO or schema change: `row(videoId)` exists. The DownloadsRepository interface stays as it is, because #55 edits it in parallel.

### 2. The resolver asks the lookup first

PlaybackResolver calls `playable(videoId)` before `streamLinks(videoId)`. With `Files`, it returns an item with the same placeholder URI and a tag that holds the files, and it never calls the stream links. With anything else it streams as today. PlaybackLog writes one info line, "Playing a download", or one line for an `Incomplete` (with its reason as a constant field) or `Unreadable` result, before streaming. Nothing names the video or a path. Because `refreshLinks` resolves through the same resolver, a refresh after the download finished plays the files; a file item never gets an HTTP status, so it never refreshes.

The decoders aren't asked again for files: the download chose its formats with the same Decoders, and a decoder error still fails as NOT_DECODABLE through `failureOf`.

### 3. The tag names where the item plays from

PlaybackTag becomes a sealed interface with two plain classes, so their text still shows nothing: Streamed (the FormatChoice and whether the network was metered, as today) and Downloaded (the files). `live` stays a property of both; Downloaded is never live. `refreshLinks` and `limitLiveVariants` read only a Streamed tag. SourcePlan gains `Files(video, audio)`, with audio null for a combined file.

PlaybackSources builds a Files plan with `ProgressiveMediaSource.Factory(FileDataSource.Factory())` and the PlaybackLoadErrorPolicy, one source per file, merged like a streamed Separate plan, with `Uri.fromFile` as each child item's URI. FileDataSource reads only `file:` URIs, and the URIs come only from the resolver's tag. A controller can't bring its own: `onSetMediaItems` builds every item, with its tag, from the playback ID alone, and only the screens' controller may set items. Alternatives: DefaultDataSource would also open `http:` and `content:` URIs, against the rule that every request goes through the YouTube layer; a cache-backed source would need Media3's cache, which the engine doesn't use (ADR 0010). FileDataSource ships in media3-datasource, which media3-exoplayer already brings, so no dependency changes.

### 4. The wake mode follows the item

On every change of the current item, PlaybackService sets `C.WAKE_MODE_LOCAL` for a Downloaded tag and `C.WAKE_MODE_NETWORK` otherwise, from a pure function `wakeModeFor(tag)`. A file needs the CPU awake with the screen off, not the Wi-Fi radio. Leaving NETWORK would work, but keeps Wi-Fi in its high-performance mode for no reason while a download plays.

### 5. Failures

A file that can't be read while playing gives FileDataSource's IO error codes, such as ERROR_CODE_IO_FILE_NOT_FOUND, which `failureOf` already maps to OTHER: "the video stopped playing", with "Try again". That runs a new start, whose lookup finds the files missing and streams. PlaybackErrorsTest gets a case for the file codes.

### 6. Tests

- `:core:data` JVM tests of the check against a temporary folder: separate and combined files; each Incomplete case; a row in each other state; sizes null in the row; the DAO throwing; cancellation passing through.
- `:core:playback` JVM tests: the resolver with a fake lookup and a fake StreamLinksRepository (Files never calls the links; every other result streams; an Unreadable lookup streams), `sourcePlan` for both tag kinds, and `wakeModeFor`.
- `:app` emulator test PlayFromFilesTest, through the real job and the fakes: download TestVideo, wait for Downloaded, turn `mediaReachable` off, open the video and check it plays on past a few seconds with no new AssetMedia request; then a second test that deletes the audio file of a finished download and checks the video streams, with AssetMedia's counts going up, while the status still says Downloaded. #58 adds the whole offline flow on top.
- Hand checks on Android 10 and 17 with a real video: a downloaded video plays in airplane mode, in the background with the screen off and from the mini player, and logcat holds no path, ID or title; a downloaded video whose file was deleted with `run-as` streams.

## Risks / Trade-offs

- [A file that is complete in length but corrupt plays badly or fails] → The player fails it as it would a bad stream, and "Try again" plays the files again. The length checks catch the empty file that a hard power-off left on 12L during #54's checks, which the sync before the rename now prevents.
- [Reading the database and checking files adds time before every start] → One indexed row read and up to two file stats, on an IO thread, beside the saved position's read, against a network round trip for links today.
- [A missing file keeps saying "Downloaded"] → Accepted: the screen still works by streaming, and #55's remove fixes it. The answer to this plan's approval question can change it.
- [#55 may change DownloadDao or DownloadsRepository at the same time] → This change uses only the existing `row(videoId)` and adds new files, so a rebase touches at most docs/architecture.md's list.

## Migration Plan

None: no schema change. A download made before this change plays from its files at once.
