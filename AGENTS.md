# AGENTS.md

Instructions for AI coding agents and human contributors working on Dewfall. Read this file before doing anything in the repository.

## What Dewfall is

Dewfall is an Android app that works like a podcast app for YouTube. Users pick channels, and new videos download overnight while the phone charges on Wi-Fi, so they play instantly the next day, with or without internet. There is no recommendation feed.

The first version has four parts: overnight downloads with automatic cleanup, a calm inbox of new videos from subscribed channels, search inside the subtitles of watched videos, and normal search and streaming.

- Repository: github.com/dewfallapp/dewfall
- Package name: io.github.dewfallapp. Use it when the Android project is created, and never change it after that.
- License: GPLv3

## Current status

The current phase is Phase 5: subscriptions and the inbox. Its goal is that opening the app shows new videos from the user's channels. Add modules and code only when a Phase 5 task asks for them, and only what that task asks for.

docs/roadmap.md holds the plan: every phase and its tasks. The [Dewfall roadmap](https://github.com/orgs/dewfallapp/projects/1) project board shows what is done and what is next, with one milestone per phase and one issue per task.

This section is updated whenever a phase's milestone closes.

## Where things are

| Path | What it holds |
|---|---|
| docs/roadmap.md | Phases, tasks, and decisions already made |
| docs/vision.md | What the app is, who it is for, and its non-goals |
| docs/architecture.md | How the app is built |
| docs/adr/ | Architecture decision records, one file per decision |
| openspec/specs/ | Living specs: how the app behaves today, grouped by area |
| openspec/changes/ | Proposed and in-progress changes, one folder per feature |
| docs/design/ | Design brief, design system and exported screens |
| docs/testing.md, docs/release.md, docs/breakage.md | How to test, how to release, and what to do when YouTube breaks something |
| app/ | The app module, which ties the other modules together |
| app/src/androidTest/ | The emulator tests, with their fakes of the YouTube layer and their media files. docs/testing.md#emulator-tests explains them |
| core/ | Core modules that every feature can use. docs/architecture.md describes each one |
| core/database/schemas/ | The Room database schemas that the build writes, one JSON file per database version |
| core/youtube/src/test/resources/youtube/ | The saved YouTube responses that the YouTube layer's tests replay, which the recorder writes |
| feature/ | Feature modules, two for each feature. Today the Search YouTube screen, the video screen with the mini player and the fullscreen player, and the YouTube layer's debug screen, which only debug builds contain |
| feature/*-ui/src/screenshotTest/ | The screenshot tests: one-call @PreviewTest wrappers of the previews in src/main. The reference images they are compared with are in src/screenshotTestDefaultDebug/reference/ next to it. docs/testing.md#screenshot-tests explains them |
| build-logic/ | Convention plugins with the build settings that every module shares |
| lint-rules/ | Dewfall's own lint checks, which lint runs on every module of the app. They enforce ADR 0007's logging rules, and nothing from them ships |
| scripts/ | The pre-push gate, which checks every commit before a push, its scan of the saved YouTube responses, the scan of the screenshot test folders, the maintainer subagent's guard and checkout script, and their tests. docs/testing.md#the-pre-push-gate explains the gate |
| gradle/libs.versions.toml | The version of every plugin and library |
| config/detekt/detekt.yml | detekt's settings: the changes to its default rules, and the list of Compose rules |
| .editorconfig | Editor settings, with a Kotlin section that comes close to the formatter |
| .github/workflows/ | GitHub Actions workflows. pull-request.yml runs the checks on every pull request to main and every push to main. daily-youtube-check.yml runs the live YouTube check every day and reports failures as issues. emulator-tests.yml runs the emulator tests after every push to main, and on pull requests that change the tests, the dependencies or the build |
| renovate.json | Renovate's settings for the weekly dependency update pull requests. docs/testing.md explains them |
| .coderabbit.yaml | CodeRabbit's settings for its AI code reviews of pull requests. Its comments are advice, and CONTRIBUTING.md says how to ask for a review |
| .claude/ | Claude Code setup: the reviewer and maintainer subagents in agents/, plus the OpenSpec commands and skills. The maintainer subagent critiques plans and checks pull requests on the maintainer's behalf, from its own worktree. Its own commands only read, and it runs a pull request's gate and build, which run that pull request's code |

Some of these are still outlines or empty folders. Later phases fill them in.

## Product rules

These come from docs/vision.md. Do not break them, and ask before doing anything that bends them.

- No recommendation feed, accounts, ads, analytics or tracking.
- No dependency that sends data anywhere the user did not choose. Network calls go to YouTube through the YouTube layer, plus optional services the user turns on, such as SponsorBlock after release.
- User data stays on the device. Export and import go to a file the user picks. The one exception, which the maintainer approved on November 21, 2026: while a video plays or is paused, its title and channel name are in Android's media session, for the media notification, so the lock screen, Bluetooth devices and apps that may read notifications or control media can read them. PRIVACY.md says so.
- Never put "YouTube" or YouTube logos in the app's name, icon or package name (applicationId).
- Product behavior, such as the deletion rules, comes from the specs and ADRs. If a request conflicts with them, stop and ask.

## How to work

- Spec first. For any new feature or change in behavior, create an OpenSpec change with /opsx:propose and wait for approval before writing code. Docs, small fixes and refactors that do not change behavior do not need one. Neither do developer tools that only debug builds or CI contain, such as the YouTube layer's debug screen, the saved YouTube responses and the daily YouTube check, because release behavior doesn't change. Once the feature is built and reviewed, archive the OpenSpec change with /opsx:archive as the last commit of the feature's pull request, before merge. Run the reviewer again on that commit, because it writes the living specs.
- Record decisions. A decision that would be hard to reverse gets an ADR in docs/adr/, numbered in order and based on docs/adr/0000-template.md. When the structure of the app changes, update docs/architecture.md in the same pull request.
- Work on a branch and open a pull request. Never push to main. Name branches by type, such as feat/inbox-screen or docs/phase-0.
- Keep each pull request small, with one concern.
- When a new issue comes in, give it a milestone, either its phase or "After release", and add it to the [Dewfall roadmap](https://github.com/orgs/dewfallapp/projects/1) project board. Issues that the daily YouTube check opens arrive with neither, so add both by hand, with the current phase as the milestone. The one exception is Renovate's Dependency Dashboard, #142, which is not a task.
- Use conventional commit messages: feat, fix, docs, test, refactor, build, ci or chore. Example: "feat: add storage limit setting".
- Ask before adding a dependency. Anything that ships in the app must be compatible with GPLv3. Apache 2.0, MIT, BSD and LGPL are fine. So is MPL 2.0, unless the code carries the "Incompatible With Secondary Licenses" notice from MPL 2.0's Exhibit B. Bundled fonts may use the SIL Open Font License 1.1 and must ship with their copyright notice and license text. Build tools that never ship, such as Gradle plugins and the tools they download, may use any open source license or be in the public domain. Ask about anything else. Wait 14 days after a release before adopting it, unless the release fixes a security issue, or it is a NewPipeExtractor release that fixes a YouTube breakage. For a breakage fix, wait at least 3 days, and adopt it only when the daily check or a run by hand shows the breakage on main, the release's changes since the previous tag fix it, and the maintainer approves it, and the pull request says so. The exception covers NewPipeExtractor's own artifact only: any other artifact that the update adds or changes still waits 14 days. This applies to new dependencies and to updates. Renovate opens the update pull requests, as docs/testing.md describes.
- GitHub Actions are build tools too, so the same license rule applies. Pin each action to a full commit SHA with its version in a comment, such as `# v7.0.1`. Give workflows read-only permissions unless a job needs more.
- Never commit secrets, keystores, signing passwords or local.properties.
- Do not invent facts, URLs, email addresses or names. If something is missing, ask, or leave a clearly marked TODO.
- When a request is unclear, ask one question instead of guessing.

## Review before handing off

- Before every push, run the pre-push gate, `python3 scripts/pre_push_gate.py`, and push only when it ends with PASS. It checks every commit that the push sends, as docs/testing.md#the-pre-push-gate describes. Show its output for the commit you push in your hand-off. If it finds something private in a commit that is already pushed, stop and tell the maintainer, and don't rewrite pushed history without their approval.
- Before you stop for the maintainer's review, and before you open a pull request, run the reviewer subagent on your changes. Tell it what the task asked for, and which issue on the project board the work closes, if any.
- Fix everything under "Must fix", then run the reviewer again. If the same problem is still there after two rounds, stop and tell the maintainer instead of trying again.
- When you report back, include the reviewer's final verdict, what it found, what you changed, and its questions for the maintainer.
- Put the final verdict in the pull request description.
- Pull request descriptions follow the sections of .github/pull_request_template.md, because `gh pr create --body` doesn't apply the template.
- If you're not using Claude Code, go through the checklist in .claude/agents/reviewer.md yourself before handing off.
- In Claude Code, the maintainer subagent judges work on the maintainer's behalf: it critiques a plan before building, and checks a pull request before merge. Pass it only the numbers and the plan's path that .claude/agents/maintainer.md asks for, never a summary of the work. Its verdict is binding. Build only after "Verdict: Build", and merge only after "Verdict: Merge" on the pull request's current head commit. Its Build comes on top of the maintainer's approval of an OpenSpec change, which "Spec first" still asks for, and never replaces it. Every other verdict blocks, and a new push needs a new check.

## Architecture rules

These apply from Phase 2 on. A pull request that refines them updates this section too.

- Kotlin, Jetpack Compose, Material 3, Hilt, Room, WorkManager and Media3.
- Follow Android's current official guidance, such as the app architecture recommendations and the Compose docs on developer.android.com. If the android CLI is installed, `android docs search` and `android docs fetch` read the same guidance from the Android Knowledge Base. For any API, library or Gradle setting that may have changed, check the current docs instead of relying on memory. Don't use deprecated APIs in new code. If the guidance conflicts with a rule in this file, follow this file and point out the conflict.
- Modules: `:app`, the plain Kotlin/JVM modules `:core:logging`, `:core:model`, `:core:mvi` and `:core:youtube` (the YouTube layer), and the Android libraries `:core:data`, `:core:database`, `:core:designsystem` and `:core:playback` (the player). The Search YouTube screen adds `:feature:youtube-search-logic` and `:feature:youtube-search-ui`, and the video screen adds `:feature:player-logic` and `:feature:player-ui`. The YouTube layer's debug screen adds `:feature:youtube-debug-logic` and `:feature:youtube-debug-ui`, which `:app` adds to debug builds only, with debugImplementation. The plain Kotlin/JVM module `:lint-rules` holds Dewfall's own lint checks, which the module plugins add to every module's lint, and which never ship. docs/architecture.md describes each one. Shared UI goes in `:core:designsystem`, or in `:core:ui` once it needs domain models, as docs/architecture.md describes.
- Each feature has two modules: `:feature:<name>-logic`, plain Kotlin/JVM, with the State, Intent, Effect and reducer, and `:feature:<name>-ui`, an Android library with the screens and the ViewModel. Feature modules depend on core modules, never on another feature's modules. A feature's `-ui` module also depends on its own `-logic` module. Only `:app` sees Navigation 3: a feature whose screen sits on `:app`'s back stack exposes one public composable from its `-ui` module, which takes plain values and actions, such as `onOpenVideo`, and `:app` turns those actions into navigation. The one exception is `:feature:player-ui`, which also exposes the mini player, a bar that `:app` places under the back stack, as ADR 0002's note of November 21, 2026 records. The YouTube layer's debug screen has its own activity instead. Features reach YouTube only through repositories in `:core:data`, never through `:core:youtube`. The one exception is `:feature:youtube-debug-ui`, the debug screen, whose job is to call the YouTube layer's functions themselves. `:app`'s androidTest also depends on `:core:youtube`, for the layer's interfaces only, so the emulator tests can replace the layer with fakes. It is test code that ships nowhere, and no feature module can see the layer through it.
- Code that needs nothing from Android lives in plain Kotlin/JVM modules with no Android imports: the YouTube layer, the domain models, the MVI base types, the reducers and the logger. See ADR 0006.
- Only the YouTube layer module may use NewPipeExtractor. It maps every NewPipeExtractor type to Dewfall's own models, so nothing else in the app knows which library sits underneath.
- The YouTube layer's functions are main-safe suspend functions that return YouTubeResult and throw only CancellationException. No NewPipeExtractor exception leaves the module. Video and channel IDs are VideoId and ChannelId, never plain strings. See ADR 0008. The one exception is MediaRequests, the layer's client for the player's requests to googlevideo.com, whose calls throw IOException, as ADR 0008's note of November 20, 2026 says.
- Every request to YouTube asks for British English (en-GB), and for the device's country, or GB when YouTube doesn't support it. The subtitle download and MediaRequests name no country, because their links already carry the country of the call that made them, as ADR 0009's notes say. NewPipeExtractor reads some of YouTube's answers by their English text, so another language breaks the marks of upcoming streams and some error categories. See ADR 0009.
- Every screen uses MVI: an immutable State, an Intent sealed interface, one-time Effects, and a reducer that is a pure function. The ViewModel runs side effects. Every reducer has unit tests. An action that starts in the UI, changes no state and needs no business logic, such as opening or closing a screen, goes straight to `:app`'s navigation, with no intent or effect. Player commands from the video screen and the mini player, such as play, pause, seek and the mini player's close, go straight to the player through PlaybackConnection, not through intents, and they read playback state through the player's state holders. The mini player has no screen state and no ViewModel. See ADR 0002.
- Every screen's ViewModel extends MviViewModel from `:core:mvi`. Intents include the results of the ViewModel's work, and effects stay pending as state until the UI reports them handled. docs/architecture.md describes the MVI contract.
- Playback state never goes into screen state. The player lives in a MediaSessionService and exposes its state through its MediaController, which screens read with media3-ui-compose's state holders or as Flows. Position updates run only as often as the visible UI needs them.
- Download, sync and cleanup logic must not depend on any screen being open, because background WorkManager jobs reuse it. Background jobs run only on unmetered networks while charging.
- No hardcoded user-facing text. Every string goes in resources, and every icon has a content description.
- Colors come from MaterialTheme color roles. Never hardcode colors. The one exception is `:app`'s launch window theme, which Android draws before Compose can read MaterialTheme, so it copies Dewfall's surface color from tokens.json, and LaunchThemeTest checks the copy. Every screen must work in light, dark and dynamic color.
- Every screen draws edge to edge and handles window insets. Backgrounds may sit under the system bars, but no text or controls sit under the system bars or the display cutout. Android 16 and later give apps that target them no way to opt out. See ADR 0006.
- Annotation processors for Kotlin code, such as Hilt's and Room's compilers, run through KSP. The kotlin-kapt plugin doesn't work with AGP's built-in Kotlin, and Google recommends KSP over its com.android.legacy-kapt fallback. Ask first before adding kotlin-kapt or com.android.legacy-kapt.
- Log only through the Logger from `:core:logging`, which a class receives in its constructor. Messages, tags and field names are constant text: a string literal, a `const val`, or `+` of those, never a string template. A function that passes its own String parameter on as one of them marks it @ConstantText. Never log user data, as [ADR 0007](docs/adr/0007-how-the-app-logs.md#user-data) defines it. Never call android.util.Log outside LogcatWriter in `:app`, which calls only Log.println. Never write to standard output outside ConsoleLogWriter, which writes the lines of the recorder of saved YouTube responses and of the live YouTube check in `:core:youtube`'s tests, and never write to standard error or call printStackTrace anywhere. Never use NewPipeExtractor's ExtractorLogger, and never use another logging API, such as java.util.logging. Reducers never log. Dewfall's lint checks enforce these rules in main and test sources, and nothing can suppress them. Lint can't read the screenshot test folders, src/screenshotTest, so they hold only one-call wrappers of previews in src/main, and scripts/scan_screenshot_tests.py, which the gate and the pull request workflow run, checks that shape. See ADR 0007's note of November 21, 2026. Any change to the lint checks' two exceptions updates ADR 0007 in the same pull request, with a dated note.
- Use coroutines and Flow. Never use GlobalScope. Inject dispatchers so tests can replace them. The one exception is viewModelScope's Main dispatcher: ViewModels take no dispatcher, and their tests replace Main with Dispatchers.setMain.
- The database uses Room 3, in the androidx.room3 package. Room 2 is deprecated.
- Room schemas are exported into core/database/schemas/. Commit the schema files that the build writes, and never edit them by hand. The first public release is the first GitHub release, including a pre-release, published from a version tag. A tag made only to test the release workflow, with no published release, doesn't count, and nightly APKs don't count. Until then, the database stays at version 1, and a schema change needs no migration. From the first public release on, every schema change raises the version and needs a migration and a migration test. Never use destructive migrations.

## Testing rules

- New logic ships with tests in the same pull request.
- Write tests with kotlin.test's annotations and assertions. They run on JUnit 6 in plain Kotlin/JVM modules and on JUnit 4 in Android modules. docs/testing.md explains how to test reducers and ViewModels. Screenshot tests are the exception: they are @PreviewTest wrappers of previews, which the screenshot engine's own test engine runs.
- ViewModel tests replace the Main dispatcher with Dispatchers.setMain before each test and reset it after.
- Unit tests never touch the network. The YouTube layer is tested against saved responses. Live YouTube checks run in the scheduled CI workflow, or by hand with `./gradlew :core:youtube:liveYouTubeCheck`. Recording the saved responses is the one other step that reaches YouTube: a developer runs it by hand, and it never runs in CI. Neither runs in `./gradlew test`. docs/testing.md explains when to run each.
- Emulator tests replace the YouTube layer and the player's media clients with fakes through Hilt, so they never reach YouTube either. docs/testing.md#emulator-tests explains them.
- Screens get screenshot tests in light, dark and large font, and in dynamic color with a fixed wallpaper. docs/testing.md#screenshot-tests explains how to add, record and update them.
- Never delete, skip or weaken a test to make it pass. If a test looks wrong, explain why and ask.

## Commands

Run these from the repository root. The build needs JDK 17 installed, because every module compiles with a JDK 17 toolchain and Gradle never downloads one. It also needs the Android SDK with the Android 17 (API 37) platform and build tools 36.0.0. Gradle finds the SDK through the ANDROID_HOME environment variable, or through sdk.dir in local.properties. The pull request workflow and the emulator tests workflow install the same platform and build tools, so update .github/workflows/pull-request.yml and .github/workflows/emulator-tests.yml when either changes.

- Build the debug APK: `./gradlew assembleDebug`. The APK goes to app/build/outputs/apk/debug/app-debug.apk.
- Run the unit tests: `./gradlew test`. It runs the tests of the plain Kotlin/JVM modules and the debug unit tests of the Android modules.
- Run the emulator tests: `ANDROID_SERIAL=<serial> ./gradlew :app:connectedDebugAndroidTest`, with the serial of one emulator or phone from `adb devices`. Without ANDROID_SERIAL, they run on every device that adb sees. They install the debug app and the test APK and uninstall both at the end, so they remove a debug build that was installed before, with its data. docs/testing.md#running-them says when to run them.
- Run one module's unit tests: `./gradlew :core:mvi:test` for a plain Kotlin/JVM module, or `./gradlew :core:data:testDebugUnitTest` for an Android module. Add `--tests` with a class name to run one test class. The HTML report goes to build/reports/tests/ in the module.
- Check formatting: `./gradlew spotlessCheck`. It checks every Kotlin file and Kotlin build script in the repository, build-logic included, against ktfmt in kotlinlang style.
- Fix formatting: `./gradlew spotlessApply`. It rewrites the same files in place. Run it before you commit.
- Follow the Kotlin style that ktfmt doesn't decide, from [Android's Kotlin style guide](https://developer.android.com/kotlin/style-guide#braces) and [Kotlin's coding conventions](https://kotlinlang.org/docs/coding-conventions.html#loops). Every for, while and do has braces, even on one line. An if, used as a statement or an expression, skips braces only when all of it fits on one line and it has at most one else, such as `if (x) return` or `if (done) finish() else retry()`. A when branch skips them when its arrow and its whole body fit on one line, even when ktfmt wraps its conditions onto the lines above. The guide shows no branch whose conditions span lines, so that part is Dewfall's own reading. In main code, prefer for loops to forEach, unless the receiver is nullable or forEach is part of a longer call chain. Tests may use forEach.
- Run detekt: `./gradlew detekt`. It checks the Kotlin code in src/main, src/test and src/androidTest of every module, and in src/screenshotTest of the modules with screenshot tests, and the code in build-logic, without type resolution. Any finding fails the build. Reports go to build/reports/detekt/ in each project.
- Run Android Lint: `./gradlew lint`. It checks every module, with Dewfall's own checks from `:lint-rules` on main and test sources of every module of the app, but not on src/screenshotTest, which it can't read, and writes a report to build/reports/ in each of them. Any warning fails the build. In the app's plain Kotlin/JVM modules it also runs Animal Sniffer, which checks the code against the Android API at minSdk 29, because lint's own API check skips those modules. The lint checks' own tests run with `./gradlew :lint-rules:test`, which `./gradlew test` includes.
- Run the pre-push gate: `python3 scripts/pre_push_gate.py`. It needs Python 3.9 or later, with no packages beyond its standard library. macOS has Python 3.9 with Xcode's Command Line Tools. `--no-build` skips the build for a quick look, and the gate then ends INCOMPLETE or FAIL, never PASS.
- Run the screenshot tests: `./gradlew testDebugScreenshotTestDefaultTestSuite`. They render each screen's previews on the JVM and compare them with the reference images. The report goes to build/reports/tests/testDebugScreenshotTestDefaultTestSuite/ in the module. `./gradlew test` doesn't run them.
- Record the screenshot tests' reference images again: `./gradlew updateDebugScreenshotTestDefaultTestSuite`, after a change to a screen that you meant. Look at every changed image before you commit it, as docs/testing.md#screenshot-tests says.
- Check the screenshot test folders: `python3 scripts/scan_screenshot_tests.py`. It checks that they hold only one-call wrappers and reference images with no text in them.
- Run every check that the pull request workflow runs: `./gradlew spotlessCheck detekt lint test testDebugScreenshotTestDefaultTestSuite assembleDebug`, then `python3 scripts/scan_screenshot_tests.py`. The workflow is .github/workflows/pull-request.yml. It adds `--continue`, so one run reports every failing check, and `-Pandroid.builder.sdkDownload=false`, so a missing SDK package fails the build instead of being downloaded. After the build, the workflow fails if the build changed or added files in core/database/schemas/, so commit the schema files it writes.
- Record the saved YouTube responses again: `./gradlew :core:youtube:recordYouTubeResponses`. It sends requests to YouTube, so run it only when docs/testing.md says to. `-PyoutubeSets=SEARCH_NASA,UPLOADS_NASA` records only the sets it names. If none of them names the machine's address and the network's country, the recorder also makes VIDEO_BIG_BUCK_BUNNY's call to learn them, and writes nothing of it.
- Open the YouTube layer's debug screen: install a debug build with `./gradlew :app:installDebug`, then open "Dewfall debug" in the launcher. docs/testing.md says what to check, and how to check that release builds leave the screen out.
- Run the live YouTube check by hand: `./gradlew :core:youtube:liveYouTubeCheck`. It sends requests to YouTube and googlevideo.com, so run it only when docs/testing.md says to. It logs its results and writes them to core/youtube/build/live-youtube-check/results.txt, and files no issue. To run the daily workflow now instead: `gh workflow run daily-youtube-check.yml`.
- Check renovate.json after you change it: `npx --yes --package renovate@44.140.0 -- renovate-config-validator --strict`. It needs Node.js 24. To use a newer renovate release, pick one that is at least 14 days old.

## Writing style

- Write docs in plain English with short sentences. Someone new to the project should be able to follow them without a glossary.
- Use Markdown for every doc and Mermaid for diagrams.
- No emoji in docs, code comments or commit messages.
