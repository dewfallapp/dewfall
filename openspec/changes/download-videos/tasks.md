# Tasks

Pull request 1, the download code, covers sections 1 to 5. Pull request 2, the buttons, covers sections 6 to 8, closes #54, and archives this change as its last commit.

## 1. Models

- [ ] 1.1 Add DownloadStatus, DownloadProgress (bytes on disk, total, percentage) and DownloadFailure to `:core:model`, with tests for the percentage at 0, part way, the total, and an unknown total; verify `./gradlew :core:model:test` passes
- [ ] 1.2 Add `DOWNLOAD_LIMIT` and `chooseDownloadFormats` beside `chooseFormats`, sharing its video and audio rules, with tests: largest within the limit, hardware first, a combined format, nothing decodable, links with only an HLS manifest, no audio track, and that `chooseFormats` still passes its own tests unchanged; verify `./gradlew :core:model:test` passes

## 2. The table

- [ ] 2.1 Add DownloadEntity and DownloadDao to `:core:database` (insert if absent, the status flows, the next waiting row, the state, format and progress updates guarded by state, the reset of DOWNLOADING rows), with DewfallDatabase's entity list and the version 1 schema file the build writes; verify `./gradlew :core:database:assembleDebug` writes the schema and the build passes
- [ ] 2.2 Add DownloadDaoTest to `:core:database`'s androidTest: queue order, a progress write that doesn't touch a PAUSED row, the reset, insert if absent; verify `ANDROID_SERIAL=<serial> ./gradlew :core:database:connectedDebugAndroidTest` passes on Android 10

## 3. The data layer

- [ ] 3.1 Add DownloadsRepository, DownloadQueue, DownloadScheduler and DownloadFiles to `:core:data`, with their Room implementations and Hilt bindings, writes in the `@ApplicationScope` scope, and JVM tests with fake DAOs: a write that finishes after its caller is cancelled, `download` of a new, waiting, paused, failed and downloaded video, `pause`, and the status mapping; verify `./gradlew :core:data:testDebugUnitTest` passes

## 4. The engine

- [ ] 4.1 Create `:core:download` (Android library, Hilt) and add it to settings.gradle.kts and `:app`; make Decoders public in `:core:playback`
- [ ] 4.2 Add FormatWriter, with JVM tests against a fake Call.Factory and a temporary folder: a fresh file in ranges, going on from a partial file, an unknown length read from Content-Range, 403 and 410 as LinkRefused, a range answer that starts at the wrong byte, a 200 for the whole file, cancellation between buffers keeping the bytes, and no read of more than one buffer at a time
- [ ] 4.3 Add DownloadRunner, with JVM tests against fakes of DownloadQueue, StreamLinksRepository, the writer's calls, the decoders and the free space: the order of the queue, one at a time, the reset at the start, every row of design.md's failure table, a link refresh that continues the parts, new links without the chosen itags, a pause during a range, a row added while the run ends, and the progress writes' rate
- [ ] 4.4 Add DownloadJobService and the UIDT scheduler (API 34+), DownloadWorker and the WorkManager scheduler (API 29 to 33), the Hilt module that picks one, DownloadNotifications, and the manifest entries; DewfallApplication implements `Configuration.Provider`, and `:app`'s manifest removes WorkManagerInitializer; verify `./gradlew :app:assembleDebug` builds and the merged manifest has the permissions and service types in design.md
- [ ] 4.5 Check the dependency: list every new artifact in `./gradlew :app:dependencies --configuration releaseRuntimeClasspath`, with its license and release date, and confirm WorkManager writes nothing to logcat during a download

## 5. Docs and checks for the download code

- [ ] 5.1 Write ADR 0010, the download engine, from docs/adr/0000-template.md
- [ ] 5.2 Fill docs/architecture.md's "Downloads and cleanup" section (the engine, the writer, the queue, the failure table, the API and what #55 to #58 add), the module graph and tables for `:core:download`, the database tables, Network access for downloads' requests, and the error handling TODO for downloads; update PRIVACY.md for the files and the table
- [ ] 5.3 Add DownloadTest to `:app`'s androidTest: with the fake YouTube layer and its asset media, download a video through DownloadsRepository and check the files' bytes, and pause and go on part way; update docs/testing.md; verify `ANDROID_SERIAL=<serial> ./gradlew :app:connectedDebugAndroidTest` passes on Android 17 and 10
- [ ] 5.4 Hand checks on Android 10, 12L and 17 with a real video: a download finishes, survives `am kill`, waits without a connection and goes on, and a reboot goes on; logcat holds no ID, title, link or file name, and nothing from WorkManager; the files are under noBackupFilesDir. Write the results in the PR
- [ ] 5.5 Run `./gradlew spotlessApply`, the pull request workflow's all-checks command and `python3 scripts/scan_screenshot_tests.py`; reviewer until Ready; the pre-push gate until PASS; open pull request 1

## 6. The video screen

- [ ] 6.1 Add DownloadChip state, the intents and reducer cases to `:feature:player-logic`, with tests; verify `./gradlew :feature:player-logic:test` passes
- [ ] 6.2 PlayerViewModel follows `status(videoId)` and calls `download` and `pause`, with tests against a fake repository; PlayerScreen shows the chip before the watched chip, with its strings, previews of every state, and screenshot references; verify the module's unit and screenshot tests pass

## 7. The inbox

- [ ] 7.1 Add the `download`, `schedule` and `pause` glyphs to `:core:designsystem`; add the `status` slot to VideoRowItem and DownloadStatusControl to `:core:ui`, with previews and screenshot tests of every state
- [ ] 7.2 InboxState gives each row its status, InboxViewModel combines `statuses()` and handles the intents, with reducer and ViewModel tests; InboxScreen fills the slot; previews and screenshot references; verify the modules' tests pass
- [ ] 7.3 Add an emulator test that taps a row's download button and waits for the check mark, and one that taps the video screen's chip; verify the emulator tests pass on Android 17 and 10

## 8. Docs, checks and archive

- [ ] 8.1 Update docs/architecture.md's video screen, inbox and `:core:ui` parts, and docs/testing.md's hand checks; run the hand checks on Android 10, 12L and 17 with TalkBack and the largest font, and write the results in the PR
- [ ] 8.2 Run the all-checks command, the reviewer until Ready, the gate until PASS; archive this change with /opsx:archive as the last commit, run the reviewer again, and open pull request 2, which closes #54
