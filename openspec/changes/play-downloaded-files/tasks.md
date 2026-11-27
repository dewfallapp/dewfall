# Tasks

One pull request covers every section, closes #56, and archives this change as its last commit.

## 1. The lookup

- [ ] 1.1 Add PlayableDownloads and PlayableDownload (Files, NotDownloaded, Incomplete with MISSING or SIZE, Unreadable) to `:core:data`'s download package, with its Room implementation over `DownloadDao.row` and DownloadFiles, an IO dispatcher qualifier provided as Dispatchers.IO, and the Hilt binding; verify `./gradlew :core:data:assembleDebug` builds
- [ ] 1.2 Add JVM tests with FakeDownloadDao and a temporary folder: separate files, a combined file, a row in each state that isn't DOWNLOADED, no row, a missing file, a folder in a file's place, an empty file, a length that differs from the row's recorded size, lengths that don't add up to `downloadedBytes`, recorded sizes that are null, missing MIME types, a DAO that throws, and a cancellation that propagates; verify `./gradlew :core:data:testDebugUnitTest` passes, and that each check's test fails when that check is taken out

## 2. The player

- [ ] 2.1 Make PlaybackTag sealed with Streamed and Downloaded, add SourcePlan.Files, and build it in PlaybackSources with FileDataSource and PlaybackLoadErrorPolicy; `refreshLinks` and `limitLiveVariants` read only a Streamed tag; add tests to PlaybackPartsTest for `sourcePlan` of a Downloaded tag with and without audio, and that a Downloaded tag is never live; verify `./gradlew :core:playback:testDebugUnitTest` passes
- [ ] 2.2 PlaybackResolver asks PlayableDownloads first, and PlaybackLog gets "Playing a download" and the line for a download that can't play, with its reason as a constant field; add resolver tests with fakes: Files gives a Downloaded item and never calls the stream links, NotDownloaded, Incomplete and Unreadable stream, and the log lines hold no ID or path; verify `./gradlew :core:playback:testDebugUnitTest` passes
- [ ] 2.3 Add `wakeModeFor(tag)` and set the wake mode on every current item change in PlaybackService, with tests; add PlaybackErrorsTest cases for ERROR_CODE_IO_FILE_NOT_FOUND and ERROR_CODE_IO_NO_PERMISSION mapping to OTHER; verify `./gradlew :core:playback:testDebugUnitTest` passes

## 3. Emulator tests

- [ ] 3.1 Add PlayFromFilesTest to `:app`'s androidTest: download TestVideo through DownloadsRepository, wait for Downloaded, turn `mediaReachable` off, open the video and check that its position passes 2 seconds with no new AssetMedia request; and a finished download whose audio file is deleted streams, with AssetMedia's counts going up and the status still Downloaded; update docs/testing.md; verify `ANDROID_SERIAL=emulator-5556 ./gradlew :app:connectedDebugAndroidTest` passes on Android 17 and Android 10, with every existing test

## 4. Docs and checks

- [ ] 4.1 Update docs/architecture.md: the player's sequence diagram and its sections for the lookup, the tag, the file source and the wake mode, and "What later tasks add" for #56 and #58; and DownloadFiles' and ADR 0010's mentions of #56 stay true; verify every link in the changed sections resolves
- [ ] 4.2 Hand checks with a real video on Android 17 and Android 10: a downloaded video plays in airplane mode, in the background with the screen off, and in the mini player; the notification shows its title; logcat from the app's processes holds no ID, title, file path or file name; after `run-as` deletes one of its files, the video streams and still says Downloaded. Write the results in the pull request. If YouTube blocks this network, ask the maintainer whether the checks can wait in an issue
- [ ] 4.3 Run every check the pull request workflow runs, `./gradlew spotlessCheck detekt lint test testDebugScreenshotTestDefaultTestSuite assembleDebug` and `python3 scripts/scan_screenshot_tests.py`, then the reviewer until Ready, then `python3 scripts/pre_push_gate.py` until PASS

## Workflow follow-up

- Archive this change with /opsx:archive as the pull request's last commit, then run the reviewer again on that commit.
- Hand the pull request to the merge desk session.
