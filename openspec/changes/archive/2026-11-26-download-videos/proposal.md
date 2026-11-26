# Proposal

## Why

Phase 6's goal is "Download a video and watch it without internet" ([roadmap](../../../docs/roadmap.md#phase-6-manual-downloads)). Today every video streams, and nothing can keep a copy on the phone. [#54](https://github.com/dewfallapp/dewfall/issues/54) adds the Download button on the video screen and in the inbox. It is the phase's first task, so it also lays the download code that #55 (the Downloads screen), #56 (play from the downloaded file), #57 (the storage indicator), #58 (the offline emulator test) and Phase 7's overnight job build on. The roadmap asks that this code never depend on a screen being open, because Phase 7 runs it from a background job.

## What Changes

- **A Download button on the video screen.** Under the publish line, in the row with "Mark as watched", a chip "Download" with the `download` glyph. While the video downloads it says "Downloading, 42%" with a small progress ring, and once the file is on the phone it says "Downloaded", selected, with a check, as the "Player" frames show for the downloaded state; the other states' words are new. A live stream, and a video that is about to start, have no Download chip, because they have no file to keep.
- **A Download button in the inbox.** Each video row in the inbox ends its meta line with the design system's download status control: an outlined button with the `download` glyph, which a screen reader hears as "Download"; while it downloads, a progress ring with "Downloading, 42%"; once downloaded, the round `check` mark, heard as "Downloaded". The "Inbox" frame shows the button and the mark.
- **Waiting, paused and failed.** A video that waits for its turn or for a connection says "Waiting to download". Tapping the progress ring, or "Downloading" on the video screen, pauses the download, which then says "Paused, 42%" and offers the download button again to go on from there. A download that can't finish says "Download failed", or "Not enough space on this phone" when the phone's storage is full, and the download button tries again. The design system has the downloading, downloaded and available states; these three are new words in the same style, which the maintainer's answers below settle.
- **Downloads go on without the screen.** A download runs in a background job of its own, not in a screen: Android's user-initiated data transfer job on Android 14 and later, and a WorkManager job below, so it goes on after the user leaves the video, closes the app or turns the screen off, and picks up where it stopped after the phone restarts or the connection comes back. A download that Android couldn't schedule while the app left the screen goes on the next time the app shows. Stopping Dewfall from Android's Task Manager counts as pausing its downloads, as the maintainer chose. One video downloads at a time, in the order the user asked. While a download runs, Android shows Dewfall's download notification, which says how far it is.
- **What a download keeps.** One video format and one audio format, the same kinds of files the player streams, chosen as the player chooses them, at most 720p, as the maintainer chose. A tap downloads on any connection, mobile data included, as the maintainer chose; Phase 7's overnight downloads still wait for Wi-Fi. The files go in the app's own storage, which no other app can read and which backups leave out. The phone keeps the title, channel, length and publish time with the download, so later screens can list it without asking YouTube.
- **Privacy.** A download sends requests only to YouTube, through the YouTube layer: the stream links call and the file's bytes from googlevideo.com, through the same rules as playback. No log line holds a video's ID, title, link or file name, and the work given to WorkManager carries nothing about the video, because WorkManager writes log lines of its own that the app can't turn off.
- **No change yet to playback.** A downloaded video still streams until #56 plays the file. Removing a download, the Downloads screen, the storage indicator and the offline test are #55, #57 and #58.

## Capabilities

### New Capabilities

- `downloads`: downloading a video to the phone, the queue, pausing and going on, failures, where the files live, what a download sends and logs, and the download notification. ADR 0004 names `downloads` as one of the living specs' areas.

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

After the maintainer subagent's third plan critique, also in the question dialog on November 26, 2026:

5. "#54 downloads: on Android 13+, the notification shade's Task Manager has a Stop button for running apps. If you press Stop for Dewfall during a download, what should happen the next time you open the app? (Android itself won't restart the job after that Stop.)" Answer: "Show it paused (Recommended)", whose option said: "The stop counts as a pause: the video shows "Paused, 42%" and goes on only when you tap the download button. Respects the Stop you pressed."

For the hand checks of pull request 1, also in the question dialog on November 26, 2026:

6. "#54 hand checks: this Mac has no Android 13 or 14 emulator images. Download them from Google's Android SDK repository with sdkmanager (system-images;android-33 and android-34, google_apis, arm64; roughly 1.5 GB each, about 3 GB disk), to check the Task Manager Stop on Android 13 (WorkManager path) and the first Android version with user-initiated jobs?" Answer: "Download both (Recommended)".
7. "#54 PR 1 (download engine): YouTube now blocks this Mac's network ("confirm you're not a bot") after a morning of real downloads, so 5 of the planned hand checks can't run yet: a reboot mid-download on Android 10 and 12L, offline-and-back on Android 10, and a swipe from Recents on Android 13/14 (plus a swipe while a download waits). Everything else passed on Android 10–17, plus all unit and emulator tests. What should I do?" Answer: "Merge, check later (Recommended)", whose option said: "Send PR 1 to the merge desk now; run those 5 checks once YouTube lets this network through, before PR 2 (the buttons) is sent, and record them in PR 2. The PR says exactly which checks are pending."
8. "#54 PR 1 correction: when you answered "Merge, check later", I said everything except 5 checks had passed. In fact ALL real-video hand checks ran on builds from before the last engine fixes (moving work off the main thread, freeing cache space, and putting a stopped download back to waiting, plus a new guard so a Task Manager Stop still pauses). Unit tests (all pass) and the emulator tests on Android 10 and 17 (pass) cover the final code, but no real video has run on it, because YouTube still blocks this network. What now?" Answer: "Merge, recheck all (Recommended)", whose option said: "Send PR 1 to the merge desk now; once YouTube lets this network through, run every real-video hand check again on the final code (plus the 5 pending ones) before PR 2 is sent, and record them in PR 2."

For CodeRabbit's two spec comments, also in the question dialog on November 26, 2026:

9. "#54 inbox spec, "Checking for new videos" scenario: CodeRabbit noted a download running during a check sends requests not "for the subscribed channels' uploads". The approved line says "the only requests that leave the phone go to YouTube, for the subscribed channels' uploads". Which wording?" Answer: "Keep phone-wide (Recommended)", whose option said: ""the check's requests go to YouTube, for the subscribed channels' uploads, no request leaves the phone for anywhere but YouTube, and the app's log holds no ..." Keeps the whole-phone privacy promise."
10. "#54 downloads spec, "Downloading from the video screen": the approved THEN says the screen shows "Downloaded" "once both files are on the phone". A video with one combined format keeps one file. Change to "once every file it keeps is on the phone"?" Answer: "Approve change (Recommended)", whose option said: ""once every file it keeps is on the phone": true for two files or one."

For pull request 2, also in the question dialog on November 26, 2026:

11. "#54 PR 2: the real-video hand checks ran today on Android 17, 13 and 12L and passed. They found two bugs, both now fixed: an empty file after a hard power-off, and the chips not lining up. Since 18:37 YouTube blocks this network again, so Android 10's reboot and offline checks (pending since answer 7) can't run. What now?" Answer: "Send PR 2, check 10 later (Recommended)", whose option said: "Send PR 2 to the merge desk now with today's results. Run Android 10's real-video checks once YouTube lets this network through, and record them on #54's follow-up."
12. "#54 PR 2: TalkBack can't be driven from adb on these emulators: taps activate, and swipes and Alt+Right don't move focus. How should the screen-reader behavior be checked?" Answer: "Add a semantics test (Recommended)", whose option said: "An emulator test asserts what TalkBack reads: a row's text is one node and its download control a separate node, "Downloaded" or "Download", and the waiting and downloaded chips have no click action."
13. "#54 PR 2: at 200% font on a phone, an inbox row's "Downloading, 42%" breaks mid-word ("Downloadin" / "g, 42%") beside its 48dp download control. What should happen?" Answer: "Hyphenate the status words (Recommended)", whose option said: "Turn on hyphenation for the meta line's status words only, giving "Download-" / "ing, 42%". The design system forbids hyphenation only in titles. The control stays at the end of the meta line, as the spec says."
14. "#54 PR 2, correcting my earlier question (answer 11 named only Android 10's reboot and offline): checks NOT run on the final code: all of Android 10's (finish, kill -9, offline, reboot, logs), all of Android 14's (incl. Task Manager Stop, shade, both swipes), the swipe while waiting on 13, both swipes on 12L. Send PR 2 with all of these on #258?" Answer: "Send PR 2, all on #258 (Recommended)", whose option said: "Send PR 2 now. #258 lists every check above, and they run on main once YouTube lets this network through. Android 17's checks, without the notification permission included, and Android 13's and 12L's other checks passed on the final code."
15. "#54 PR 2, a second correction: three more checks didn't run on the final code. On Android 17 my swipe while a download ran missed (Recents kept the task). On 12L I didn't check the logs. Android 10's largest font needs YouTube too. Put these three on #258 as well, and send PR 2?" Answer: "Add them to #258, send (Recommended)", whose option said: "#258 also gets Android 17's swipe while a download runs, 12L's logs, and Android 10's largest font. PR 2 and the archived tasks.md say exactly what ran and what waits."

For CodeRabbit's comment on pull request 2, also in the question dialog on November 26, 2026:

16. "#54 PR 2 (CodeRabbit): on Android 10 to 13 a reboot within seconds of a Download tap can lose WorkManager's schedule, so the download waits until the app opens. The downloads spec says it goes on by itself after a restart. How should this be resolved?" Answer: "Add a boot receiver (Recommended)", whose option said: "Dewfall's own BOOT_COMPLETED receiver, on Android 10 to 13 only, starts the queue after every boot, so a waiting download is scheduled even when WorkManager's was lost. The app already holds RECEIVE_BOOT_COMPLETED. At boot the app reads its database and sends nothing when no download waits."

Dependencies are the agents' to decide, as the maintainer said on November 21, 2026: "if you need dependencies, then don't ask for my approval. Check yourself and do whatever you feel is correct."
