# Tasks

## 1. The reader

- [x] 1.1 Add the plain Kotlin/JVM module `:core:takeout` (dewfall.jvm.library, depending on `:core:model`) to settings.gradle.kts, with TakeoutReader, TakeoutFile and TakeoutChannel as design.md describes: zip or CSV by the first bytes, the table rules, blank lines, header, duplicates, the zip table with the most channels, the 8 MiB limit counted on bytes read, the 256 MiB limit on a .zip file, IllegalArgumentException from ZipInputStream rethrown as ZipException, UTF-8 with U+FFFD for bad bytes, IOException only; verify `./gradlew :core:takeout:build` passes
- [x] 1.2 Add TakeoutReaderTest with made-up files built in the test, one test or more for each reading scenario in the delta spec and each row of design.md's line table, plus a .zip entry over the limit followed by a real table, a deflated entry whose local header claims no size, a truncated .zip file, a .zip file with one name byte set to 0xFF in both its local header and its central directory, a .zip file over 256 MiB (one stored entry whose name doesn't end in .csv, from a stream that makes its bytes, not a file, so the test stays fast), an empty .zip file, a file of random bytes, and a stored (not deflated) entry; verify `./gradlew :core:takeout:test` passes
- [x] 1.3 Run the reader against the maintainer's real .csv and .zip files in a JVM test that is never committed and takes the files' paths from a system property, with the files kept outside the repository, run with `--no-build-cache --no-configuration-cache`, and assertions and failure messages that print counts only; compare counts only, and report them only in the hand-off; then delete the test, revert any build-script edit that passed the property, and delete `:core:takeout`'s compiled test classes, build/test-results and test reports; verify `git status` shows no trace of it

## 2. Subscribing in one step

- [x] 2.1 Add `subscribeAll` to ChannelDao with tests in ChannelDaoTest (new count, existing rows keep name and time, 1,200 channels, an empty list); verify the generated ChannelDao_Impl under core/database/build/generated/ksp/ calls `performSuspending` with `inTransaction` true for it, and verify `ANDROID_SERIAL=<serial> ./gradlew :core:database:connectedDebugAndroidTest` passes on one emulator
- [x] 2.2 Add `subscribeAll(channels): Int` to SubscriptionsRepository and RoomSubscriptionsRepository, one time of subscribing for the batch, in the application scope, with tests in its JVM test (count, time, empty list, finishes when the caller is cancelled); verify `./gradlew :core:data:testDebugUnitTest` passes

## 3. The import repository

- [x] 3.1 Add TakeoutImportResult and TakeoutImportFailure to `:core:model`, with KDoc that marks the channels as user data; verify `./gradlew :core:model:test` passes
- [x] 3.2 Add DocumentOpener with its ContentResolver binding, TakeoutImportRepository with its Room-backed implementation, an injected IO dispatcher, the stream wrapper that checks the caller's Job before each read, and the Dewfall.Takeout logger lines, and their Hilt module in `:core:data`, which now depends on `:core:takeout`; verify `./gradlew :app:assembleDebug` builds the Hilt graph
- [x] 3.3 Add ReaderTakeoutImportRepositoryTest with a fake DocumentOpener, a recording fake SubscriptionsRepository and a recording log writer: each failure reason, a ZipException mapped to NOT_READABLE and logged without its message, the counts, a stream that is always closed, cancelling during a read of a stream that never ends stops it and writes nothing, and no logged line holds a made-up ID, name or file link; verify `./gradlew :core:data:testDebugUnitTest` passes

## 4. The import screen

- [x] 4.1 Add `:feature:takeout-import-logic` with the state, intents and reducer as design.md describes, and a reducer test for every transition; verify `./gradlew :feature:takeout-import-logic:test` passes
- [x] 4.2 Add `:feature:takeout-import-ui` with TakeoutImportViewModel, the screen and its public `TakeoutImport(onBack, onDone)` composable, the picker asking for `*/*`, the strings and plurals, previews in src/main with made-up channels, and ViewModel tests with a fake repository; verify `./gradlew :feature:takeout-import-ui:testDebugUnitTest` passes
- [x] 4.3 Add the screenshot test wrappers, as the other screens do: Idle and Imported with all counts in light, dark, large font, dynamic light and dynamic dark, Imported on a tablet, Reading and each failure in light and dark, the no-channels failure at large font, and Imported with only new channels in light; record them with `./gradlew updateDebugScreenshotTestDefaultTestSuite`, look at every image, and verify `./gradlew testDebugScreenshotTestDefaultTestSuite` and `python3 scripts/scan_screenshot_tests.py` pass

## 5. Navigation and the emulator test

- [x] 5.1 After #49 merges, rebase onto main, add TakeoutImportKey and its entry in `:app`, add `onImportFromTakeout` and the "Import from Google Takeout" text button under "Search YouTube" in the inbox's "No channels yet" state, with its string and updated screenshot images, and connect it in `:app` (if #49 has already added the button, only connect it); turn this change's specs/inbox/spec.md into a MODIFIED delta of the inbox spec's "The inbox says when it is empty", with that requirement's full text from main; check that the result screen's "in Channels" wording still fits, since #115 adds that screen; verify on one emulator that the button opens the screen and back and Done return to the inbox
- [x] 5.2 Add TakeoutImportTest in `:app`'s androidTest: from the empty inbox, open the import, answer the picker through espresso-intents (added to the version catalog and `:app`'s androidTest dependencies) with a made-up .csv file in the app's cache folder, and check the report and that the channels are subscribed, with an in-memory database through Hilt; verify `ANDROID_SERIAL=<serial> ./gradlew :app:connectedDebugAndroidTest` passes
- [x] 5.3 Check by hand on one emulator with made-up .zip and .csv files in Downloads, as design.md's Tests section says, in light, dark and the largest font; record the results in the pull request

## 6. Docs

- [x] 6.1 Update docs/architecture.md (module graph and tables, data flow, a section on the Takeout import), AGENTS.md (modules and where things are), docs/testing.md (the hand check, and the made-up IDs rule for the reader's tests), docs/breakage.md (what to check when the import finds no channels in a real export) and PRIVACY.md (the import reads a file you pick and sends it nowhere); verify each doc names only files and commands that exist
- [x] 6.2 Run `./gradlew spotlessApply`, then the pull request workflow's all-checks command and `python3 scripts/scan_screenshot_tests.py`, and verify they pass and `git status core/database/schemas` is clean after the build

## Workflow follow-up

- Run the reviewer subagent until it says Ready, and the pre-push gate until it ends with PASS.
- Archive the change with /opsx:archive as the PR's last commit, and run the reviewer again on that commit.
- Open the PR, which closes #51, wait for CI and CodeRabbit, then send it to the merge desk.
