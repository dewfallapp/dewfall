# Tasks

## 1. Decisions the maintainer approves first

- [ ] 1.1 Add a dated note to ADR 0004 that adds the living-spec area `youtube-search`, and the same area to openspec/config.yaml's list and to the list in docs/roadmap.md's Phase 0 guide, once the maintainer approves it. Verify that `openspec validate youtube-search --strict` passes.
- [ ] 1.2 Record the rule for large screens and resized windows in docs/design/brief.md's Rules, from design.md, with the conflict with Android's guidance named. If the maintainer approves the single-line search field, note it in the design system's SearchField README. Verify that the brief no longer says only "portrait except the fullscreen player".
- [ ] 1.3 Open an issue for thumbnail images, with the image library and image hosts as its open question, in the milestone that the maintainer's answer gives it, and add it to docs/roadmap.md if it becomes a Phase 4 task. Verify that it is on the project board.

## 2. The search repository

- [ ] 2.1 Add the `YouTubeSearchRepository` interface to `:core:data`, with an implementation that calls `SearchRemoteDataSource` and returns its result unchanged, and its Hilt binding. Verify with a unit test against a fake data source, and `./gradlew :core:data:testDebugUnitTest`.
- [ ] 2.2 Update docs/architecture.md: "Reading from YouTube" names the repository and drops "once Phase 4 adds the first repository", and `:core:data`'s row no longer says it holds only YouTubeModule. Verify that the sequence diagram's names match the code.

## 3. The logic module

- [ ] 3.1 Add `:feature:youtube-search-logic`, a plain Kotlin/JVM module, with the screen's state, intents, request and row types from design.md. Verify that `./gradlew :feature:youtube-search-logic:build` passes.
- [ ] 3.2 Write the reducer: Submit with trimmed, non-blank text starts Loading with a request whose number grows, blank text changes nothing, SearchFinished counts only for the running request, Clear and TryAgain behave as the spec says, Shorts are left out, and failures keep their YouTubeError. Verify with reducer tests for every scenario the reducer decides, including "x", Clear, "x", then the first answer arriving and being dropped.
- [ ] 3.3 Write the length and time-ago functions from design.md, with java.time. Verify with tests that use fixed values of now and the time zone, never the real clock:
  - Each boundary: 59 and 60 seconds, 59 and 60 minutes, 23 and 24 hours, 1 and 2 calendar days, 13 and 14 days, the last day before a calendar month and the month itself, the same for a year, plus live, upcoming, unknown and future times.
  - Approximate times built the way NewPipeExtractor v0.27.1's TimeAgoParser builds them, for each of YouTube's units and the counts in SEARCH_NASA's saved answer, written in the test as constants, so that recording the set again can't break it. Each reads back as YouTube wrote it.
  - An approximate "1 day ago" on the day the clocks go forward in Europe/London, which reads "Yesterday", not hours. The tests run with now in March of a year that isn't a leap year, on February 29 of a leap year, and on the last day of a 31-day month.
  - "4 weeks ago" in February, which reads back as "1 month ago", as design.md accepts.

## 4. The UI module and the app

- [ ] 4.1 Add `:feature:youtube-search-ui`, an Android library, with `YouTubeSearchViewModel`. It runs one search at a time, cancels on a new search and on Clear, and dispatches SearchFinished. Verify with ViewModel tests against a fake YouTubeSearchRepository, with Main replaced by Dispatchers.setMain: one call per search, a replaced search is cancelled and its answer never shows, Clear cancels, and a blank Submit or a TryAgain with no failure sends nothing and keeps the running search.
- [ ] 4.2 Write the stateless `YouTubeSearchScreen`, which takes `now`, and its route, which reads the clock, following the "Search YouTube" frames: title and line, the search field with its clear button, the InputTransformation that turns line breaks into spaces, the keyboard's search action and hardware Enter, focus once and a hidden keyboard after a search, the message before the first search, loading, rows, no results and the two failure messages with "Try again". Keep the content in one centered column at most 640dp wide. All strings in resources, plurals for time ago and for the spoken length, and a content description on every icon. Verify with previews in light, dark, a font scale of 2 and a tablet-sized window.
- [ ] 4.3 Make DewfallApp show the route instead of the placeholder, remove the placeholder and its strings, and make MainActivity ask for portrait. Get the ViewModel with `lifecycle-viewmodel-compose` if the maintainer approves it, or with `by viewModels()` in MainActivity if not, and check the release dependency tree for anything else the artifact adds. Verify that `./gradlew assembleDebug lint` passes, and that the app opens on Search YouTube.
- [ ] 4.4 Update the docs this screen makes stale, and verify that each line below now matches the code:
  - AGENTS.md: the feature/ row, which says only the debug screen exists, and the Modules rule's list of modules.
  - docs/architecture.md: the module table and graph with the two new modules, `:app`'s row about the placeholder, the sentences that say only the debug screen uses `:core:mvi`, the sentences on where Compose and Hilt are set up, the thumbnail helper sentence that names Phase 4, and the MVI section's "The UI side comes with the first screen in Phase 4", which now waits for the first screen with effects.
  - docs/architecture.md's "Errors from YouTube": the NETWORK row and the paragraph on the connection check, which say what this screen shows and that the check comes with the first screen that needs it.
  - docs/architecture.md's "The same versions in debug and release" and the constraint in app/build.gradle.kts, whose reason changes once release ships `:core:mvi` too. Keep or remove the constraint, as the release and debug dependency trees show.

## 5. Checks

- [ ] 5.1 Run every check that the pull request workflow runs: `./gradlew spotlessCheck detekt lint test assembleDebug`. Verify that it passes.
- [ ] 5.2 Check by hand on an emulator with Android 10 and with the newest Android: search "nasa" and see rows with no Shorts, search for nonsense and see no results, turn the network off and see the NETWORK message, then "Try again" with the network on. Search with Gboard's search key on both emulators and with a hardware keyboard's Enter, and paste text with a line break. Check that the keyboard opens once and closes after a search. Check light, dark and a large font, that a phone stays in portrait, a tablet or foldable emulator in landscape, split screen, and that nothing sits under the system bars or the keyboard. Verify that `adb logcat` shows no search text, title or ID.

## Workflow follow-up

- Archive the change with /opsx:archive as the last commit of the feature's pull request, then run the reviewer again on that commit.
