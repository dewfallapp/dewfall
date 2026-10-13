# AGENTS.md

Instructions for AI coding agents and human contributors working on Dewfall. Read this file before doing anything in the repository.

## What Dewfall is

Dewfall is an Android app that works like a podcast app for YouTube. Users pick channels, and new videos download overnight while the phone charges on Wi-Fi, so they play instantly the next day, with or without internet. There is no recommendation feed.

The first version has four parts: overnight downloads with automatic cleanup, a calm inbox of new videos from subscribed channels, search inside the subtitles of watched videos, and normal search and streaming.

- Repository: github.com/dewfallapp/dewfall
- Package name: io.github.dewfallapp. Use it when the Android project is created, and never change it after that.
- License: GPLv3

## Current status

The current phase is Phase 2: project setup and CI. Its goal is an almost empty app with a solid structure, and a pull request check that blocks bad changes. Add Gradle files, modules and source code only when a Phase 2 task asks for them, and only what that task asks for. Phase 2 adds no features.

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
| build-logic/ | Convention plugins with the build settings that every module shares |
| gradle/libs.versions.toml | The version of every plugin and library |
| .claude/ | Claude Code setup: the reviewer subagent in agents/, plus the OpenSpec commands and skills |

Some of these are still outlines or empty folders. Later phases fill them in.

## Product rules

These come from docs/vision.md. Do not break them, and ask before doing anything that bends them.

- No recommendation feed, accounts, ads, analytics or tracking.
- No dependency that sends data anywhere the user did not choose. Network calls go to YouTube through the YouTube layer, plus optional services the user turns on, such as SponsorBlock after release.
- User data stays on the device. Export and import go to a file the user picks.
- Never put "YouTube" or YouTube logos in the app name, icon or the app's package name (applicationId).
- Product behavior, such as the deletion rules, comes from the specs and ADRs. If a request conflicts with them, stop and ask.

## How to work

- Spec first. For any new feature or change in behavior, create an OpenSpec change with /opsx:propose and wait for approval before writing code. Docs, small fixes and refactors that do not change behavior do not need one.
- Record decisions. A decision that would be hard to reverse gets an ADR in docs/adr/, numbered in order and based on docs/adr/0000-template.md. When the structure of the app changes, update docs/architecture.md in the same pull request.
- Work on a branch and open a pull request. Never push to main. Name branches by type, such as feat/inbox-screen or docs/phase-0.
- Keep each pull request small, with one concern.
- Use conventional commit messages: feat, fix, docs, test, refactor, build, ci or chore. Example: "feat: add storage limit setting".
- Ask before adding a dependency. Every dependency must be compatible with GPLv3. Apache 2.0, MIT, BSD and LGPL are fine. Ask about anything else.
- Never commit secrets, keystores, signing passwords or local.properties.
- Do not invent facts, URLs, email addresses or names. If something is missing, ask, or leave a clearly marked TODO.
- When a request is unclear, ask one question instead of guessing.

## Review before handing off

- Before you stop for the maintainer's review, and before you open a pull request, run the reviewer subagent on your changes. Tell it what the task asked for, and which issue on the project board the work closes, if any.
- Fix everything under "Must fix", then run the reviewer again. If the same problem is still there after two rounds, stop and tell the maintainer instead of trying again.
- When you report back, include the reviewer's final verdict, what it found, what you changed, and its questions for the maintainer.
- Put the final verdict in the pull request description.
- If you're not using Claude Code, go through the checklist in .claude/agents/reviewer.md yourself before handing off.

## Architecture rules

These apply from Phase 2 on. If Phase 2 refines them, update this section in the same pull request.

- Kotlin, Jetpack Compose, Material 3, Hilt, Room, WorkManager and Media3.
- Modules: `:app`, the plain Kotlin/JVM modules `:core:model`, `:core:mvi` and `:core:youtube` (the YouTube layer), and the Android libraries `:core:data`, `:core:database` and `:core:designsystem`. docs/architecture.md describes each one.
- Each feature has two modules: `:feature:<name>-logic`, plain Kotlin/JVM, with the State, Intent, Effect and reducer, and `:feature:<name>-ui`, an Android library with the screens and the ViewModel. Feature modules depend on core modules, never on another feature's modules. A feature's `-ui` module also depends on its own `-logic` module.
- Code that needs nothing from Android lives in plain Kotlin/JVM modules with no Android imports: the YouTube layer, the domain models, the MVI base types and the reducers. See ADR 0006.
- Only the YouTube layer module may use NewPipeExtractor. It maps every NewPipeExtractor type to Dewfall's own models, so nothing else in the app knows which library sits underneath.
- Every screen uses MVI: an immutable State, an Intent sealed interface, one-time Effects, and a reducer that is a pure function. The ViewModel runs side effects. Every reducer has unit tests.
- Playback state never goes into screen state. The player lives in a MediaSessionService and exposes playback state as its own Flow. Position updates run only as often as the visible UI needs them.
- Download, sync and cleanup logic must not depend on any screen being open, because background WorkManager jobs reuse it. Background jobs run only on unmetered networks while charging.
- No hardcoded user-facing text. Every string goes in resources, and every icon has a content description.
- Colors come from MaterialTheme color roles. Never hardcode colors. Every screen must work in light, dark and dynamic color.
- Use coroutines and Flow. Never use GlobalScope. Inject dispatchers so tests can replace them.
- Room schemas are exported into the repository. Every schema change needs a migration and a migration test. Never use destructive migrations.

## Testing rules

- New logic ships with tests in the same pull request.
- Unit tests never touch the network. The YouTube layer is tested against saved responses. Live YouTube checks run only in the scheduled CI workflow.
- Screens get screenshot tests in light, dark and large font.
- Never delete, skip or weaken a test to make it pass. If a test looks wrong, explain why and ask.

## Commands

Run these from the repository root. The build needs JDK 17 installed, because every module compiles with a JDK 17 toolchain and Gradle never downloads one. It also needs the Android SDK with the Android 17 (API 37) platform and build tools 36.0.0. Gradle finds the SDK through the ANDROID_HOME environment variable, or through sdk.dir in local.properties.

- Build the debug APK: `./gradlew assembleDebug`. The APK goes to app/build/outputs/apk/debug/app-debug.apk.
- Run the unit tests: `./gradlew test`. It runs the tests of the plain Kotlin/JVM modules and the debug unit tests of the Android modules.
- Run Android Lint: `./gradlew lint`. It checks the app and the Android library modules, and writes a report to build/reports/ in each of them. It doesn't check the plain Kotlin/JVM modules yet. #27 sets that up.

The formatting commands come with #27.

## Writing style

- Write docs in plain English with short sentences. Someone new to the project should be able to follow them without a glossary.
- Use Markdown for every doc and Mermaid for diagrams.
- No emoji in docs, code comments or commit messages.
