# Tasks

## 1. Models

- [x] 1.1 Add SubscribedChannel and WatchState to `:core:model`, with KDoc that marks them as user data, and verify `./gradlew :core:model:test` passes

## 2. The database

- [x] 2.1 In `:core:database`'s build, apply Kotlin's serialization plugin, add kotlinx-serialization-json, set the androidTest runner with `disableAnalytics=true`, and add the androidTest dependencies from the version catalog; verify `./gradlew :core:database:assembleDebug` resolves them
- [x] 2.2 Add DatabaseConverters (Instant, Duration, thumbnail lists as JSON, with a fixed message for a list that can't be read) and the merge rule (`mergeVideo`, `mergeChannelDetails`), with DatabaseConvertersTest and VideoMergeTest on the JVM, one test for each part of the rule, and verify `./gradlew :core:database:testDebugUnitTest` passes
- [x] 2.3 Add ChannelEntity, VideoEntity with its index, WatchStateEntity, with KDoc that marks them as user data, the three DAOs, DewfallDatabase at version 1 and `buildDewfallDatabase`; verify the build writes core/database/schemas/io.github.dewfallapp.core.database.DewfallDatabase/1.json with the three tables, and commit it
- [x] 2.4 Add ChannelDaoTest, VideoDaoTest, WatchStateDaoTest and DatabaseFileTest in `:core:database`'s androidTest, one test for each spec scenario the DAOs decide, a save of more than 500 videos and the order of `channelVideos`, and verify `ANDROID_SERIAL=<serial> ./gradlew :core:database:connectedDebugAndroidTest` passes on one emulator, with no SQLite error lines in logcat during the run

- [x] 2.5 Try one DAO test as a JVM unit test with androidx.sqlite:sqlite-bundled-jvm, at the version Room 3.0.3 resolves and at least 14 days old, as a test-only dependency, run `./gradlew :core:database:testDebugUnitTest`, write down whether it runs and what fails, and remove the try before committing; verify `git status` shows no trace of it

## 3. The repositories

- [x] 3.1 Add DatabaseModule in `:core:data` (the database as a singleton, its DAOs, `Clock.System`, and the `@ApplicationScope` CoroutineScope), and verify `./gradlew :app:assembleDebug` builds the Hilt graph
- [x] 3.2 Add SubscriptionsRepository, StoredVideosRepository and WatchStateRepository with their Room implementations and Hilt bindings, every write run in the `@ApplicationScope` scope, and their JVM tests with recording fake DAOs, a fixed Clock, and a test per repository that a write finishes when the caller is cancelled; verify `./gradlew :core:data:testDebugUnitTest` passes
- [x] 3.3 Add app/src/main/res/xml/data_extraction_rules.xml and `android:dataExtractionRules`, drop DataExtractionRules from the manifest's `tools:ignore` and update its comment, update docs/architecture.md's lint paragraph and PRIVACY.md, add BackupOffTest in `:app`'s unit tests for the manifest and the rules, and verify `./gradlew :app:lint` passes and `./gradlew :app:testDebugUnitTest --tests '*BackupOffTest'` passes

## 4. CI and docs

- [x] 4.1 Make .github/workflows/emulator-tests.yml run `:core:database:connectedDebugAndroidTest` with `:app`'s tests on both API levels, add `core/database/**` and `core/model/**` to its pull request paths and `:core:database`'s report folders to its upload step, and verify the workflow run on the PR passes
- [x] 4.2 Update docs/architecture.md (core modules table and text, build settings, data flow, database tables, error handling), docs/testing.md (fast tests list with the answer from 2.5, emulator tests, the workflow, database migration tests), docs/roadmap.md (the testing section's fast tests) and AGENTS.md (where things are, commands), and verify each doc names only files and commands that exist
- [x] 4.3 Run `./gradlew spotlessApply`, then the pull request workflow's all-checks command and `python3 scripts/scan_screenshot_tests.py`, and verify they pass and `git status core/database/schemas` is clean after the build

## Workflow follow-up

- Run the reviewer subagent until it says Ready, and the pre-push gate until it ends with PASS.
- Archive the change with /opsx:archive as the PR's last commit, and run the reviewer again on that commit.
- Open the PR, which closes #48, wait for CI and CodeRabbit, then send it to the merge desk.
