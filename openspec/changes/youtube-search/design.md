# Design

## Context

See proposal.md for why. The pieces this change builds on:

- The YouTube layer's `SearchRemoteDataSource.search(query)` returns `YouTubeResult<SearchResults>`, the first page of channels and videos, and throws only CancellationException. Its KDoc says a blank query returns empty results without a request.
- `:core:mvi` holds MviViewModel and the reducer contract. The debug screen in `:feature:youtube-debug-*` is the only screen built on them so far, and it is in debug builds only.
- docs/architecture.md's "Reading from YouTube" sketches this flow: the ViewModel calls a repository in `:core:data`, which calls the layer, and dispatches the result as an intent. Its "Errors from YouTube" section plans what screens show for each YouTubeError, and leaves where the connection check lives to a later phase.
- `:core:data` binds the layer's interfaces with Hilt in YouTubeModule, but holds no repository yet.
- DewfallApp shows a placeholder screen, which says that Phase 4 replaces it.
- ADR 0006: the app targets API 37, so on screens at least 600dp wide Android ignores orientation and resizability limits, with no opt-out. The brief says portrait, except the fullscreen player. The maintainer's comment on #40 asks this first screen to decide how screens look on large screens and in resized windows, and to record the rule in docs/design/brief.md.

## Goals / Non-Goals

**Goals:**

- The first real screen, as the pattern the other Phase 4 screens follow: two feature modules, a pure reducer with its tests, a ViewModel that runs the calls, and a stateless screen.
- The first repository in `:core:data`.
- The rule for large screens and resized windows that every later screen follows.

**Non-Goals:**

- More than the first page of results. The layer returns one page, and loading more is a later change if search needs it.
- Navigation between screens. #41's change adds the video screen and the navigation that opens it.
- Channel results, thumbnail images, and the downloaded and subtitle marks, as proposal.md explains.
- Telling "offline" from "YouTube not loading". See [Failure messages](#failure-messages).
- Screenshot tests. Phase 4's own task, #45, adds them for every screen.

## Decisions

### Two modules named youtube-search

`:feature:youtube-search-logic` and `:feature:youtube-search-ui`, as AGENTS.md's two-module rule asks. The name says which search it is, because Phase 8 adds the other search, of saved subtitles, and the design brief says users must always know which one they are in.

### A spec area for searching YouTube

The capability is `youtube-search`. ADR 0004 lists the living-spec areas, player, subscriptions, inbox, downloads, background-sync, cleanup, transcript-search and data-export, and none of them is searching YouTube. Search YouTube grows in Phase 5 with channel results, so it gets its own area. That changes ADR 0004's list, so it waits for the maintainer: a dated note in ADR 0004, and the same list in openspec/config.yaml.

### A thin repository in :core:data

`YouTubeSearchRepository` is an interface in `:core:data` with one suspend function, `search(query): YouTubeResult<SearchResults>`. Its implementation calls the layer and returns its result unchanged, and Hilt binds it.

- **Why a repository that adds nothing yet.** Features may reach YouTube only through `:core:data` (AGENTS.md), so the screen can't call the layer itself. Phase 5 gives the repository work of its own, such as marking the channels a user follows.
- **Why an interface.** The `-ui` module's ViewModel tests fake it without seeing `:core:youtube`.
- **Alternative:** expose `SearchRemoteDataSource` through `:core:data` with an api dependency. Rejected, because it would let every feature see the layer's interfaces.

### The screen's state

The reducer in `-logic` owns a state with two parts: the number of the latest request, and one sealed value for the results area.

| Results area | When |
|---|---|
| Idle | Before the first search, and after clearing |
| Loading(request) | While a search runs |
| Results(request, rows) | A search returned videos to show |
| NoResults(request) | A search returned none to show |
| Failed(request, error) | A search failed, with its YouTubeError |

A request holds the trimmed search text and its number. The number lives outside the sealed value, so Clear doesn't reset it, and every request gets a new one. Intents are Submit(text), Clear, TryAgain and SearchFinished(request, result). SearchFinished counts only while the results area is Loading with the same request. So the answer to a replaced search, or one that arrives after Clear, is dropped, even when the text is the same. Submit with blank text changes nothing.

- **The field's text isn't in the state.** The field is a state-based text field that holds its own text. `rememberTextFieldState` saves and restores it as `rememberSaveable` would, so it survives process death ([migration guide](https://developer.android.com/develop/ui/compose/text/migrate-state-based)). The reducer gets the text only when the user sends it. The debug screen differs: it reports every change as an intent, because its Run button reads the state.
- **No effects.** Nothing on this screen happens once, so its effect type is Nothing. The MVI section's effect handling stays unused until a screen needs it, and its sentence about Phase 4 changes to say so.

### Rows

The reducer turns each VideoSummary into a row with the video's ID, title, channel name, length, kind and publish time, and leaves out Shorts, if the maintainer agrees. The row keeps the ID, so #41 can open the video. It drops the thumbnails, which no image shows yet, and the channel's ID, which nothing uses yet. VideoKind's KDoc already says a screen leaves out the kinds it doesn't show, and the design system's VideoRow says "no Shorts".

### Length and time text

Two pure functions in `-logic` decide what a row says, so plain JVM tests cover them:

- **Length.** At least an hour: "1h 06m". Under an hour: "54m". Under a minute: "45s". A live stream: "Live". A video that hasn't started: "Upcoming". Unknown: nothing. Seconds are dropped above a minute, as in the design. A screen reader gets the length in words, such as "1 hour 6 minutes", from plural resources.
- **Time ago.** From the publish time, now and the phone's time zone, a unit and a count:

| Since the publish time | Row says |
|---|---|
| In the future, or no time | Nothing |
| Under an hour | Minutes, at least 1, such as "15m ago" |
| Under 24 hours | Hours, such as "7h ago" |
| 1 calendar day | "Yesterday" |
| 2 to 13 calendar days | Days, such as "10 days ago" |
| 14 days or more, less than a calendar month | Weeks, days divided by 7, such as "3 weeks ago" |
| A calendar month or more, less than a calendar year | Calendar months, such as "2 months ago" |
| A calendar year or more | Calendar years, such as "2 years ago" |

- **Why calendar units.** YouTube's lists give times like "10 days ago", "2 weeks ago" or "1 month ago". NewPipeExtractor v0.27.1's TimeAgoParser turns them into an approximate instant (PublishedTime.isApproximate) by going back that many calendar units from now: `now.minus(amount, unit)`. It takes a year as `minusYears(n).minusDays(1)`, and moves date-based units back to the start of the day ([TimeAgoParser.getResultFor](https://github.com/TeamNewPipe/NewPipeExtractor/blob/v0.27.1/extractor/src/main/java/org/schabi/newpipe/extractor/localization/TimeAgoParser.java)). YouTube shows days up to 13, as the saved set SEARCH_NASA, recorded on November 18, 2026, shows with "8 days ago" to "Streamed 13 days ago", then "Streamed 2 weeks ago". So counting the same calendar units, in the same order, reads each of YouTube's times back as YouTube wrote it.
- **Approximate times start at days.** A time that YouTube gave in days or longer is approximate, and never shows minutes or hours, even when it is less than 24 real hours old, as on the day the clocks go forward. It reads from "Yesterday" up.
- **One case reads differently.** In February and March, 28 days can be a whole calendar month. So YouTube's "4 weeks ago" can then read back as "1 month ago". Both are what YouTube itself could have said, so the rows accept it.
- **Where they go later.** #41's video screen and the inbox will need the same text. Then the pure functions move to a shared module, as docs/architecture.md plans for "the code that formats a duration", so no feature copies them.
- **The functions use java.time.** Calendar months need a calendar, and java.time is on Android from API 26, under the minSdk of 29, and in plain Kotlin/JVM. No new dependency.

The `-ui` module turns them into text with plural string resources, so "1 week ago" and "3 weeks ago" both read right. The route reads the clock and the time zone once per answer and passes them to the stateless screen, so previews and #45's screenshot tests don't change with the date. The reducer never reads the clock, so it stays pure.

### The search field

The field is single-line, if the maintainer agrees, which departs from the design system's SearchField. SearchField says not to set single-line, so that a long text wraps. But a field that allows several lines makes the on-screen keyboard show a return key instead of search ([KeyboardOptions](https://developer.android.com/reference/kotlin/androidx/compose/foundation/text/KeyboardOptions)), and then nothing reliably sends the search, because the screen has no other button for it. A single-line field shows the keyboard's search action, and Enter on a hardware keyboard triggers the same action. A long text scrolls sideways in the field instead of wrapping. So:

- The field is single-line, with the keyboard's action set to search, and its keyboard action handler sends the search. Enter on a hardware keyboard runs the same handler.
- An InputTransformation turns every line break in pasted text into a space, so no line break ever gets into the text.
- The field asks for focus once. A saved flag records that it did, so a configuration change or a return to the screen doesn't open the keyboard again. Sending a search hides the on-screen keyboard and clears the field's focus.

### Failure messages

| YouTubeError | Message | Try again |
|---|---|---|
| NETWORK | YouTube can't be reached. Check your connection. | Yes |
| Any other | YouTube isn't loading right now. | Yes |

- This follows docs/architecture.md's plan, "YouTube not loading" for BLOCKED and BROKEN, and the design's "Inbox, YouTube not loading" frame, which also offers "Try again". A search isn't expected to give the categories that are about one video or channel, so they share the same message.
- **No connection check yet.** architecture.md plans "offline" for NETWORK when the phone has no connection. On this screen both cases need the same thing from the user, to check the connection and try again, and there are no downloads to point to, as the inbox's "Downloaded videos still play" does. So this screen shows one message for NETWORK, and the check, with the ACCESS_NETWORK_STATE permission it needs, comes with the first screen whose message depends on it. The PR updates architecture.md's table and its paragraph on the check to say so.
- BROKEN doesn't say that Dewfall needs an update. No update exists before the first release, and trying again does no harm.

### How the ViewModel runs a search

`YouTubeSearchViewModel` extends MviViewModel. After Submit or TryAgain, it starts a search only when the state's request number changed, so a blank Submit, or a TryAgain with no failure to try again, sends nothing and keeps the running search. When the number changed, it cancels the running search's job and starts one that calls the repository with the new request, then dispatches SearchFinished. For Clear it cancels the job. It takes no dispatcher, because the repository's call is main-safe. Leaving the screen clears the ViewModel, which cancels the search through viewModelScope.

### Getting the ViewModel inside Compose

DewfallApp calls the screen's route, which gets its ViewModel with `viewModel()` from `androidx.lifecycle:lifecycle-viewmodel-compose`. In an @AndroidEntryPoint activity that function uses the activity's default factory, which Hilt provides, so @HiltViewModel works with no Hilt-specific artifact ([Hilt in Compose](https://developer.android.com/develop/ui/compose/libraries#hilt)).

- The artifact is new to the build, so it waits for the maintainer's answer. It comes from Lifecycle 2.11.0, the catalog's version, released on June 17, 2026, under the Apache License 2.0. Its POM brings lifecycle-viewmodel-savedstate, lifecycle-runtime-compose and kotlinx-serialization-core 1.7.3, all under the Apache License 2.0. activity-compose 1.13.0 already brings lifecycle-runtime-compose into release builds. The PR shows which of them the release dependency tree gains. Lifecycle 2.11.1 came out on November 18, 2026, so the 14-day rule keeps the build on 2.11.0 until December 2.
- **If the maintainer says no:** MainActivity gets the ViewModel with `by viewModels()`, which activity-compose already brings, and passes it to the route. The ViewModel then has to be public instead of internal.
- **Alternative:** `hiltViewModel()` from `androidx.hilt:hilt-lifecycle-viewmodel-compose`. Rejected for now, because `viewModel()` does the same in a single activity, and #41's navigation decides what scoping it needs.

### Large screens and resized windows

This is the rule that later screens follow too:

- **Phones stay in portrait.** MainActivity asks for portrait in the manifest, as the brief says. Android honors it on screens narrower than 600dp, and ignores it on larger ones (ADR 0006).
- **Any window works.** On a tablet, an unfolded foldable, landscape on a large screen or in split screen, the screen's content sits in one column, at most 640dp wide, centered, and the background fills the window. 640dp keeps lines readable, and a phone in portrait is narrower, so phones look the same.
- **No new dependency.** A width limit on the content needs no window size classes. A later screen that needs two panes, such as a list and its detail, decides that in its own change.
- docs/design/brief.md's Rules line changes to say this, as the maintainer's comment asks.
- **A conflict with Android's guidance.** Android's Android 16 behavior changes call restricting orientation and resizability too restrictive. The brief asks for portrait on phones, and AGENTS.md says to follow the project's own rules and point out the conflict, so the plan keeps portrait on phones and the brief's new rule names the conflict.

### The screen

`YouTubeSearchScreen` is stateless: it takes the state, the field's text state, `now` and an onIntent function. Its layout follows the "Search YouTube" frames: the title and its line, a rounded search field with a search icon and a clear button, then the message or the rows.

- The field follows the design system's SearchField: the keyboard opens as the screen opens, and the keyboard's action is search.
- Colors come from MaterialTheme's roles and text from its type scale, so light, dark and dynamic color all work.
- The screen draws edge to edge, and its content sits inside the safe drawing area, so nothing is under the system bars, the display cutout or the keyboard.
- Every icon has a content description, and every string is a resource.
- The thumbnail box is a plain surface-colored box with the length in a small label, as in the design frames.

### Logging

The screen adds no log lines. The YouTube layer already logs each call's name, outcome category and counts, never its text (ADR 0007). The reducer never logs.

## Risks / Trade-offs

- [The app's first screen has no way back to anything] → Nothing comes before it in Phase 4. Phase 5 opens it from the inbox and gives it a back arrow.
- [Rows that do nothing when tapped until #41 lands] → The two changes are next to each other in the roadmap, and no release comes before both land.
- [Plain boxes instead of thumbnails make results harder to tell apart] → Titles and channel names carry the meaning for now. Thumbnails need a new dependency and requests to YouTube's image hosts outside the YouTube layer, so they are a question for the maintainer, tracked in an issue of their own.
- [The portrait request is on the app's only activity, and the fullscreen player, #44, rotates] → #44's change sets the activity's requested orientation while the fullscreen player shows, and puts it back after.
- [Lint may flag a portrait-locked activity] → Any lint warning fails the build. If lint flags it, the PR suppresses that one check for MainActivity with a comment that cites ADR 0006 and the brief.
- [Time ago depends on the phone's clock] → A wrong clock gives a wrong "time ago", which harms nothing else. The text is only shown, never stored.
