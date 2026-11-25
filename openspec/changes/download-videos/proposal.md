# Proposal

## Why

Phase 6's goal is "Download a video and watch it without internet" ([roadmap](../../../docs/roadmap.md#phase-6-manual-downloads)). Today every video streams, and nothing can keep a copy on the phone. [#54](https://github.com/dewfallapp/dewfall/issues/54) adds the Download button on the video screen and in the inbox. It is the phase's first task, so it also lays the download code that #55 (the Downloads screen), #56 (play from the downloaded file), #57 (the storage indicator), #58 (the offline emulator test) and Phase 7's overnight job build on. The roadmap asks that this code never depend on a screen being open, because Phase 7 runs it from a background job.

## What Changes

- **A Download button on the video screen.** Under the publish line, in the row with "Mark as watched", a chip "Download" with the `download` glyph. While the video downloads it says "Downloading, 42%" with a small progress ring, and once the file is on the phone it says "Downloaded", selected, with a check, as the "Player" frames show for the downloaded state; the other states' words are new. A live stream, and a video that is about to start, have no Download chip, because they have no file to keep.
- **A Download button in the inbox.** Each video row in the inbox ends its meta line with the design system's download status control: an outlined button with the `download` glyph, which a screen reader hears as "Download"; while it downloads, a progress ring with "Downloading, 42%"; once downloaded, the round `check` mark, heard as "Downloaded". The "Inbox" frame shows the button and the mark.
- **Waiting, paused and failed.** A video that waits for its turn or for a connection says "Waiting to download". Tapping the progress ring, or "Downloading" on the video screen, pauses the download, which then says "Paused, 42%" and offers the download button again to go on from there. A download that can't finish says "Download failed", or "Not enough space on this phone" when the phone's storage is full, and the download button tries again. The design system has the downloading, downloaded and available states; these three are new words in the same style, which the maintainer's answers below settle.
- **Downloads go on without the screen.** A download runs in a background job of its own, not in a screen: Android's user-initiated data transfer job on Android 14 and later, and a WorkManager job below, so it goes on after the user leaves the video, closes the app or turns the screen off, and picks up where it stopped after the phone restarts or the connection comes back. A download that Android stopped for good, such as from its Task Manager, goes on the next time the app shows. One video downloads at a time, in the order the user asked. While a download runs, Android shows Dewfall's download notification, which says how far it is.
- **What a download keeps.** One video format and one audio format, the same kinds of files the player streams, chosen as the player chooses them, at most 720p, as the maintainer chose. A tap downloads on any connection, mobile data included, as the maintainer chose; Phase 7's overnight downloads still wait for Wi-Fi. The files go in the app's own storage, which no other app can read and which backups leave out. The phone keeps the title, channel, length and publish time with the download, so later screens can list it without asking YouTube.
- **Privacy.** A download sends requests only to YouTube, through the YouTube layer: the stream links call and the file's bytes from googlevideo.com, through the same rules as playback. No log line holds a video's ID, title, link or file name, and the work given to WorkManager carries nothing about the video, because WorkManager writes log lines of its own that the app can't turn off.
- **No change yet to playback.** A downloaded video still streams until #56 plays the file. Removing a download, the Downloads screen, the storage indicator and the offline test are #55, #57 and #58.

## Capabilities

### New Capabilities

- `downloads`: downloading a video to the phone, the queue, pausing and going on, failures, where the files live, what a download sends and logs, and the download notification. The roadmap names `downloads` as one of the living specs' areas.

### Modified Capabilities

- `player`: the video screen's chip row gets the Download chip, and the privacy requirement names downloads' requests.
- `inbox`: each video row ends with the download status control, and the privacy requirement allows the requests of a download the user started.

## Impact

- New Android library `:core:download`: the download job, the code that writes a format's bytes to a file in byte ranges, the queue, the notification, and the Hilt module that hands WorkManager the job. It depends on `:core:data`, `:core:model`, `:core:logging` and `:core:playback`, for the player's check of which formats the phone decodes.
- `:core:model`: DownloadStatus, the failure reasons and the download's format choice, with tests.
- `:core:database`: a `downloads` table and DownloadDao, with DAO tests. The database stays at version 1, because no public release exists yet.
- `:core:data`: DownloadsRepository, which features use to start, pause and follow downloads, and DownloadFiles, which knows where the files live, for #56 and #57 too.
- `:core:playback`: the format decoding check becomes public, so downloads choose formats the phone can play.
- `:app`: WorkManager starts on demand with Hilt's worker factory, and its startup initializer is removed from the manifest. The manifest declares the data sync foreground service type that the download notification needs.
- `:core:ui`: VideoRowItem gets a slot for the status control at the end of its meta line.
- `:feature:inbox-logic`, `:feature:inbox-ui`, `:feature:player-logic`, `:feature:player-ui`: the controls, intents, reducer cases, strings, previews and screenshot tests.
- `:core:designsystem`: the `download`, `schedule` and `pause` glyphs, if not already there.
- New dependency: androidx.work:work-runtime, which ships. Its license and transitive dependencies are listed in design.md.
- ADR 0010 records the download engine. docs/architecture.md gets its "Downloads and cleanup" section, docs/testing.md the download's tests and hand checks, and PRIVACY.md says what downloads store and send.
- AGENTS.md: the Modules rule names `:core:download`; the rule that background jobs run only on unmetered networks while charging says it covers automatic jobs, and that a download the user taps runs right away on any connection, charging or not, with a dated note quoting the maintainer's answers 2 and 4; the Room 3 rule points to ADR 0010 for the Room 2 inside WorkManager. docs/roadmap.md's decision 7 gets a dated note saying the same, and docs/vision.md's "Overnight downloads" one line saying a video the user downloads by hand starts right away.
- Two pull requests: the download code with its tests and no UI, then the buttons. The second one closes #54 and archives this change.

## The maintainer's answers

In this change's Claude Code session's question dialog, on November 26, 2026, the maintainer answered four questions:

1. "#54 downloads: what's the highest picture quality a download should keep? (Streaming already uses up to 1080p on Wi-Fi, 480p on mobile data. Rough sizes per hour of video: 480p ~0.4 GB, 720p ~0.8 GB, 1080p ~1.5 GB.)" Answer: "Up to 720p (Recommended)".
2. "#54 downloads: when the user taps Download while on mobile data, what should happen?" Answer: "Download right away (Recommended)", whose option said: "The tap is an explicit ask, on any connection. Overnight downloads (Phase 7) still use Wi-Fi only."
3. "#54 downloads: the design says tapping the inbox's progress ring pauses, but has no frame for a paused or failed download. Should #54 include pause and those states?" Answer: "Yes, include them (Recommended)", whose option said: "Tap ring = pause. Paused shows "Paused, 42%" + download button to go on; failed shows "Download failed" (or "Not enough space on this phone") + button to retry. Same on the video screen's chip."
4. "#54: approve the OpenSpec change "download-videos" (sent above: own download writer run by Android's user-initiated job on 14+ and WorkManager below, one video at a time, files in the app's private storage, notification "Downloading a video" with no title, Download chip on the video screen, status control in inbox rows), with your answers to the other questions?" Answer: "Approve (Recommended)". This is the approval that AGENTS.md's "Spec first" asks for.

Dependencies are the agents' to decide, as the maintainer said on November 21, 2026: "if you need dependencies, then don't ask for my approval. Check yourself and do whatever you feel is correct."
