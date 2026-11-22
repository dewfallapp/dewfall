# Proposal

## Why

Phase 5 makes the inbox the home screen: opening the app shows new videos from your channels, and a half-watched video resumes where you stopped. Every Phase 5 task needs somewhere on the phone to keep the channels you subscribe to, their videos, and what you watched. Today `:core:database` has Room set up but no database, and `:core:data` only reads from YouTube. This change adds the database and the repositories that the rest of Phase 5 builds on ([#48](https://github.com/dewfallapp/dewfall/issues/48)).

## What Changes

- **The first database.** `:core:database` gets DewfallDatabase, at version 1, in a file named `dewfall.db` in the app's own storage, with three tables:
  - `channels`: the channels the user subscribes to. A row exists only while the user is subscribed.
  - `videos`: videos Dewfall keeps on the phone. In this change they come from a channel's latest uploads. Later tasks also save videos the user plays or downloads from any channel.
  - `watch_state`: how far the user watched each video, and whether and when they marked it watched. It is kept apart from YouTube's data, so a refresh never changes it.
- **DAOs** for each table, with the queries and transactions that the repositories need.
- **Three repositories in `:core:data`**, which return Dewfall's own models:
  - SubscriptionsRepository: the subscribed channels as a Flow, one subscribed channel as a Flow, whether one channel is subscribed as a Flow, subscribe and unsubscribe.
  - StoredVideosRepository: save a channel's latest uploads. A save merges each video with what is saved, so a later, vaguer publish time or a missing thumbnail never replaces a better one, and it keeps YouTube's order for videos with the same time.
  - WatchStateRepository: one video's watch state as a Flow, save the position, mark watched and mark unwatched.
- **New models in `:core:model`**: SubscribedChannel and WatchState.
- **Emulator tests for the database.** The DAO tests run as androidTest tests in `:core:database`, so they run against the phone's own SQLite, which is 3.22 on Android 10, the oldest Android the app supports. The emulator tests workflow runs them on Android 10 and 17. As the comment on #48 asks, the build tries once whether they could run on the computer's JVM with the bundled driver instead, and docs/testing.md and the roadmap record the answer. design.md explains why the emulator stays the proof either way.
- **Backups and phone transfers leave the data out.** The manifest already turns Android's backup off. On some phones with Android 12 or later that doesn't stop a direct transfer to a new phone ([Auto Backup](https://developer.android.com/identity/data/autobackup), [Android 12 behavior changes](https://developer.android.com/about/versions/12/behavior-changes-12#backup-restore)). So before the first subscription is stored, the app gets data extraction rules that leave every file out of both, and the manifest's lint suppression for them goes. The manifest gave this to #68 (export and import); it moves here because this change stores the first user data.
- **Nothing the user sees changes yet.** No screen uses the repositories in this change. #47 adds the Subscribe button, #49 the inbox, #115 the channels screen, #50 resume and mark as watched, #51 the Takeout import and #52 manual refresh.

## Capabilities

### New Capabilities

- `subscriptions`: which channels the user subscribes to, what Dewfall keeps about them and their videos on the phone, and what subscribing, unsubscribing and saving a channel's uploads change. It is one of the living-spec areas that ADR 0004 already lists.

### Modified Capabilities

- `player`: adds requirements for what Dewfall remembers about each video the user watches: the position to resume from, and whether and when it was marked watched. #50 connects the player to them.

## Impact

- `:core:database`: the database class, three entities, three DAOs, column type converters, a function that builds the database, the version 1 schema file in core/database/schemas/, androidTest tests, and the test runner and test dependencies in its build file. It applies Kotlin's serialization plugin, for the thumbnail lists.
- `:core:data`: the three repositories, their Hilt bindings, a Hilt module that provides the database, its DAOs and the clock, and unit tests with fake DAOs.
- `:core:model`: two new models, SubscribedChannel and WatchState.
- Dependencies: kotlinx-serialization-json 1.11.0, already in gradle/libs.versions.toml for tests, now ships in the app through `:core:database`. Apache 2.0, released on April 9, 2026, and its only other dependency, kotlinx-serialization-core, already ships. No other new artifact ships. The JVM try in design.md uses androidx.sqlite:sqlite-bundled-jvm as a test-only dependency, and it isn't committed.
- CI: .github/workflows/emulator-tests.yml also runs `:core:database:connectedDebugAndroidTest`, and its pull request filter adds core/database/ and core/model/.
- Docs: docs/architecture.md (modules, build settings, data flow, database tables, error handling), docs/testing.md (the fast-test list, where the database tests live and how to run them), docs/roadmap.md (its testing section lists "Room queries" among the fast tests) and AGENTS.md (where things are, and the command that runs the database tests).
- Privacy: everything stays in the app's own storage. Android's backup stays off, and the new data extraction rules leave every file out of Google Drive backups and phone-to-phone transfers on Android 12 and later. Nothing in this change logs, and nothing is sent anywhere.
- `:app`: app/src/main/res/xml/data_extraction_rules.xml, `android:dataExtractionRules` in the manifest, the DataExtractionRules lint suppression removed and the manifest's comment updated, and BackupOffTest. docs/architecture.md's lint paragraph names only the remaining suppression, and PRIVACY.md says backups and transfers leave the app's data out.
- Before the first public release the database stays at version 1, as AGENTS.md says. A later change to these tables before then means clearing the app's data after installing that build, as docs/architecture.md#database-tables says.

## The maintainer's answers

On November 22, 2026, in this change's session, the maintainer answered three questions:

1. "Plan for #48: Dewfall starts keeping, on the phone only, the channels you subscribe to, their videos, and how far you watched each video. Google Drive backups and moving to a new phone leave all of it out. Nothing on screen changes yet; later Phase 5 tasks use it. Approve this plan?" Answer: "Approve (Recommended)". This is the approval that AGENTS.md's "Spec first" asks for, and it covers the data extraction rules, which #47's session had first asked about.
2. "When you unsubscribe from a channel, should Dewfall keep what you watched of its videos (resume positions and watched marks)?" Answer: "Keep them (Recommended)". The subscriptions spec's "Unsubscribing removes only the subscription" follows it.
3. "May the app ship kotlinx-serialization-json 1.11.0, a small JetBrains library for storing thumbnail lists (Apache 2.0, released April 9, 2026, no known vulnerabilities)?" Answer: "Yes, ship it (Recommended)".

Earlier, on November 21, 2026, the maintainer delegated dependency decisions: "if you need dependencies, then don't ask for my approval. Check yourself and do whatever you feel is correct." Question 3 asked anyway, because AGENTS.md on main then still said to ask before adding a dependency. PR #227 later wrote the delegation into AGENTS.md.
