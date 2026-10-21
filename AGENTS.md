# AGENTS.md

Instructions for AI coding agents and human contributors working on Dewfall. Read this file before doing anything in the repository.

## What Dewfall is

Dewfall is an Android app that works like a podcast app for YouTube. Users pick channels, and new videos download overnight while the phone charges on Wi-Fi, so they play instantly the next day, with or without internet. There is no recommendation feed.

The first version has four parts: overnight downloads with automatic cleanup, a calm inbox of new videos from subscribed channels, search inside the subtitles of watched videos, and normal search and streaming.

- Repository: github.com/dewfallapp/dewfall
- Package name: io.github.dewfallapp. Use it when the Android project is created, and never change it after that.
- License: GPLv3

## Current status

The current phase is Phase 3: the YouTube layer. Its goal is that all access to YouTube lives in `:core:youtube`, behind Dewfall's own interfaces, so every future fix stays in one place. Add modules and code only when a Phase 3 task asks for them, and only what that task asks for. Phase 3 adds no features or screens beyond the debug screen its tasks ask for.

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
| core/ | Core modules that every feature can use. docs/architecture.md describes each one |
| core/database/schemas/ | The Room database schemas that the build writes, one JSON file per database version |
| build-logic/ | Convention plugins with the build settings that every module shares |
| gradle/libs.versions.toml | The version of every plugin and library |
| config/detekt/detekt.yml | detekt's settings: the changes to its default rules, and the list of Compose rules |
| .editorconfig | Editor settings, with a Kotlin section that comes close to the formatter |
| .github/workflows/ | GitHub Actions workflows. pull-request.yml runs the checks on every pull request to main and every push to main |
| renovate.json | Renovate's settings for the weekly dependency update pull requests. docs/testing.md explains them |
| .claude/ | Claude Code setup: the reviewer subagent in agents/, plus the OpenSpec commands and skills |

Some of these are still outlines or empty folders. Later phases fill them in.

## Product rules

These come from docs/vision.md. Do not break them, and ask before doing anything that bends them.

- No recommendation feed, accounts, ads, analytics or tracking.
- No dependency that sends data anywhere the user did not choose. Network calls go to YouTube through the YouTube layer, plus optional services the user turns on, such as SponsorBlock after release.
- User data stays on the device. Export and import go to a file the user picks.
- Never put "YouTube" or YouTube logos in the app's name, icon or package name (applicationId).
- Product behavior, such as the deletion rules, comes from the specs and ADRs. If a request conflicts with them, stop and ask.

## How to work

- Spec first. For any new feature or change in behavior, create an OpenSpec change with /opsx:propose and wait for approval before writing code. Docs, small fixes and refactors that do not change behavior do not need one. Once the feature is built and reviewed, archive the OpenSpec change with /opsx:archive as the last commit of the feature's pull request, before merge. Run the reviewer again on that commit, because it writes the living specs.
- Record decisions. A decision that would be hard to reverse gets an ADR in docs/adr/, numbered in order and based on docs/adr/0000-template.md. When the structure of the app changes, update docs/architecture.md in the same pull request.
- Work on a branch and open a pull request. Never push to main. Name branches by type, such as feat/inbox-screen or docs/phase-0.
- Keep each pull request small, with one concern.
- Use conventional commit messages: feat, fix, docs, test, refactor, build, ci or chore. Example: "feat: add storage limit setting".
- Ask before adding a dependency. Anything that ships in the app must be compatible with GPLv3. Apache 2.0, MIT, BSD and LGPL are fine. Bundled fonts may use the SIL Open Font License 1.1 and must ship with their copyright notice and license text. Build tools that never ship, such as Gradle plugins and the tools they download, may use any open source license or be in the public domain. Ask about anything else. Wait 14 days after a release before adopting it, unless the release fixes a security issue. This applies to new dependencies and to updates. Renovate opens the update pull requests, as docs/testing.md describes.
- GitHub Actions are build tools too, so the same license rule applies. Pin each action to a full commit SHA with its version in a comment, such as `# v7.0.1`. Give workflows read-only permissions unless a job needs more.
- Never commit secrets, keystores, signing passwords or local.properties.
- Do not invent facts, URLs, email addresses or names. If something is missing, ask, or leave a clearly marked TODO.
- When a request is unclear, ask one question instead of guessing.

## Review before handing off

- Before you stop for the maintainer's review, and before you open a pull request, run the reviewer subagent on your changes. Tell it what the task asked for, and which issue on the project board the work closes, if any.
- Fix everything under "Must fix", then run the reviewer again. If the same problem is still there after two rounds, stop and tell the maintainer instead of trying again.
- When you report back, include the reviewer's final verdict, what it found, what you changed, and its questions for the maintainer.
- Put the final verdict in the pull request description.
- Pull request descriptions follow the sections of .github/pull_request_template.md, because `gh pr create --body` doesn't apply the template.
- If you're not using Claude Code, go through the checklist in .claude/agents/reviewer.md yourself before handing off.

## Architecture rules

These apply from Phase 2 on. A pull request that refines them updates this section too.

- Kotlin, Jetpack Compose, Material 3, Hilt, Room, WorkManager and Media3.
- Follow Android's current official guidance, such as the app architecture recommendations and the Compose docs on developer.android.com. If the android CLI is installed, `android docs search` and `android docs fetch` read the same guidance from the Android Knowledge Base. For any API, library or Gradle setting that may have changed, check the current docs instead of relying on memory. Don't use deprecated APIs in new code. If the guidance conflicts with a rule in this file, follow this file and point out the conflict.
- Modules: `:app`, the plain Kotlin/JVM modules `:core:logging`, `:core:model`, `:core:mvi` and `:core:youtube` (the YouTube layer), and the Android libraries `:core:data`, `:core:database` and `:core:designsystem`. docs/architecture.md describes each one. Shared UI goes in `:core:designsystem`, or in `:core:ui` once it needs domain models, as docs/architecture.md describes.
- Each feature has two modules: `:feature:<name>-logic`, plain Kotlin/JVM, with the State, Intent, Effect and reducer, and `:feature:<name>-ui`, an Android library with the screens and the ViewModel. Feature modules depend on core modules, never on another feature's modules. A feature's `-ui` module also depends on its own `-logic` module.
- Code that needs nothing from Android lives in plain Kotlin/JVM modules with no Android imports: the YouTube layer, the domain models, the MVI base types, the reducers and the logger. See ADR 0006.
- Only the YouTube layer module may use NewPipeExtractor. It maps every NewPipeExtractor type to Dewfall's own models, so nothing else in the app knows which library sits underneath.
- Every screen uses MVI: an immutable State, an Intent sealed interface, one-time Effects, and a reducer that is a pure function. The ViewModel runs side effects. Every reducer has unit tests.
- Every screen's ViewModel extends MviViewModel from `:core:mvi`. Intents include the results of the ViewModel's work, and effects stay pending as state until the UI reports them handled. docs/architecture.md describes the MVI contract.
- Playback state never goes into screen state. The player lives in a MediaSessionService and exposes playback state as its own Flow. Position updates run only as often as the visible UI needs them.
- Download, sync and cleanup logic must not depend on any screen being open, because background WorkManager jobs reuse it. Background jobs run only on unmetered networks while charging.
- No hardcoded user-facing text. Every string goes in resources, and every icon has a content description.
- Colors come from MaterialTheme color roles. Never hardcode colors. Every screen must work in light, dark and dynamic color.
- Every screen draws edge to edge and handles window insets. Backgrounds may sit under the system bars, but no text or controls sit under the system bars or the display cutout. Android 16 and later give apps that target them no way to opt out. See ADR 0006.
- Annotation processors for Kotlin code, such as Hilt's and Room's compilers, run through KSP. The kotlin-kapt plugin doesn't work with AGP's built-in Kotlin, and Google recommends KSP over its com.android.legacy-kapt fallback. Ask first before adding kotlin-kapt or com.android.legacy-kapt.
- Log only through the Logger from `:core:logging`, which a class receives in its constructor. Messages, tags and field names are constant text. Never log user data, as [ADR 0007](docs/adr/0007-how-the-app-logs.md#user-data) defines it. Never call android.util.Log outside LogcatWriter in `:app`, never call println, System.out, System.err or printStackTrace, and never set NewPipeExtractor's ExtractorLogger. Reducers never log.
- Use coroutines and Flow. Never use GlobalScope. Inject dispatchers so tests can replace them. The one exception is viewModelScope's Main dispatcher: ViewModels take no dispatcher, and their tests replace Main with Dispatchers.setMain.
- The database uses Room 3, in the androidx.room3 package. Room 2 is deprecated.
- Room schemas are exported into core/database/schemas/. Commit the schema files that the build writes, and never edit them by hand. The first public release is the first GitHub release, including a pre-release, published from a version tag. A tag made only to test the release workflow, with no published release, doesn't count, and nightly APKs don't count. Until then, the database stays at version 1, and a schema change needs no migration. From the first public release on, every schema change raises the version and needs a migration and a migration test. Never use destructive migrations.

## Testing rules

- New logic ships with tests in the same pull request.
- Write tests with kotlin.test's annotations and assertions. They run on JUnit 6 in plain Kotlin/JVM modules and on JUnit 4 in Android modules. docs/testing.md explains how to test reducers and ViewModels.
- ViewModel tests replace the Main dispatcher with Dispatchers.setMain before each test and reset it after.
- Unit tests never touch the network. The YouTube layer is tested against saved responses. Live YouTube checks run only in the scheduled CI workflow.
- Screens get screenshot tests in light, dark and large font.
- Never delete, skip or weaken a test to make it pass. If a test looks wrong, explain why and ask.

## Commands

Run these from the repository root. The build needs JDK 17 installed, because every module compiles with a JDK 17 toolchain and Gradle never downloads one. It also needs the Android SDK with the Android 17 (API 37) platform and build tools 36.0.0. Gradle finds the SDK through the ANDROID_HOME environment variable, or through sdk.dir in local.properties. The pull request workflow installs the same platform and build tools, so update .github/workflows/pull-request.yml when either changes.

- Build the debug APK: `./gradlew assembleDebug`. The APK goes to app/build/outputs/apk/debug/app-debug.apk.
- Run the unit tests: `./gradlew test`. It runs the tests of the plain Kotlin/JVM modules and the debug unit tests of the Android modules.
- Run one module's unit tests: `./gradlew :core:mvi:test` for a plain Kotlin/JVM module, or `./gradlew :core:data:testDebugUnitTest` for an Android module. Add `--tests` with a class name to run one test class. The HTML report goes to build/reports/tests/ in the module.
- Check formatting: `./gradlew spotlessCheck`. It checks every Kotlin file and Kotlin build script in the repository, build-logic included, against ktfmt in kotlinlang style.
- Fix formatting: `./gradlew spotlessApply`. It rewrites the same files in place. Run it before you commit.
- Run detekt: `./gradlew detekt`. It checks the Kotlin code in src/main and src/test of every module, and the code in build-logic, without type resolution. Any finding fails the build. Reports go to build/reports/detekt/ in each project.
- Run Android Lint: `./gradlew lint`. It checks every module and writes a report to build/reports/ in each of them. Any warning fails the build. In the plain Kotlin/JVM modules it also runs Animal Sniffer, which checks the code against the Android API at minSdk 29, because lint's own API check skips those modules.
- Run every check that the pull request workflow runs: `./gradlew spotlessCheck detekt lint test assembleDebug`. The workflow is .github/workflows/pull-request.yml. It adds `--continue`, so one run reports every failing check, and `-Pandroid.builder.sdkDownload=false`, so a missing SDK package fails the build instead of being downloaded. After the build, the workflow fails if the build changed or added files in core/database/schemas/, so commit the schema files it writes.
- Check renovate.json after you change it: `npx --yes --package renovate@44.140.0 -- renovate-config-validator --strict`. It needs Node.js 24. To use a newer renovate release, pick one that is at least 14 days old.

## Writing style

- Write docs in plain English with short sentences. Someone new to the project should be able to follow them without a glossary.
- Use Markdown for every doc and Mermaid for diagrams.
- No emoji in docs, code comments or commit messages.
