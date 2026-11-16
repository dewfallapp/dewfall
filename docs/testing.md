# Testing

This file explains how Dewfall is tested: which kinds of tests exist, what each one covers, and which CI workflow runs them. Read it before writing tests or changing a workflow.

## Test layers

### Fast tests

Fast tests run on every change. They cover:

- Reducers.
- Use cases.
- Repositories with fake data sources.
- The YouTube layer against saved responses.
- Room queries.
- The theme against the design tokens. [architecture.md](architecture.md#the-theme) explains the check.
- Background jobs.
- Screenshot tests of each screen, in light, dark and large font.

Unit tests never touch the network.

### Slow tests

Slow tests are emulator tests of the main flows, such as search then play, and download then play offline.

### Benchmarks

Benchmarks for startup and scrolling run on a real phone before each release.

## CI workflows

The project uses four GitHub Actions workflows, plus Renovate's weekly update pull requests, which workflow 1 tests.

1. **Every pull request.** Formatting, lint, unit tests, screenshot tests, a database migration check, and a debug build. Branch protection blocks merging when any of them fail.
2. **Every merge to main.** Everything above, plus the emulator tests, and a nightly APK.
3. **Daily schedule.** The YouTube layer runs against real YouTube and opens an issue when something breaks. It never blocks pull requests.
4. **Version tag.** A signed release build, changelog, checksums, and a GitHub release.
5. **Weekly.** Renovate opens dependency update pull requests, and workflow 1 tests them. See [Dependency updates](#dependency-updates).

### The pull request workflow

Workflow 1 is .github/workflows/pull-request.yml. Today it runs one job, Checks, with the all-checks command from [AGENTS.md](../AGENTS.md#commands): formatting, detekt, Android Lint with the Android API check, unit tests, and a debug build. After the build, it checks that the Room schemas are committed. Screenshot tests join it when the task that adds them is done. Database migration tests need an emulator, so the task that adds them decides which workflow runs them.

- It also runs on every push to main. Those runs fill the Gradle cache, which pull request runs only read. Workflow 2, with the emulator tests and the nightly APK, doesn't exist yet.
- It adds `--continue` to the command, so one run reports every failing check.
- It installs the Android SDK packages the build needs, and turns off AGP's automatic SDK downloads, so a missing package fails the build.
- After the Gradle command, a step fails when the build changed or added files in core/database/schemas/. It runs even when the Gradle command failed. See [Database migration tests](#database-migration-tests).
- When the job fails, it uploads the build/reports/ folder of every project as an artifact called reports, kept for 7 days.
- It caches Gradle with setup-gradle's basic caching, which is MIT licensed, because the default, enhanced caching, loads a proprietary component ([DISTRIBUTION.md](https://github.com/gradle/actions/blob/v6.4.0/DISTRIBUTION.md)).
- A repository ruleset named main protects the branch. It requires a pull request, allows only merge commits, and requires the check named Checks to pass on a branch that's up to date with main. It blocks force pushes and deletion, and nobody can bypass it, maintainers included. The ruleset requires the check by its name, so renaming the job blocks merging until the ruleset is updated.
- The repository allows only GitHub's own actions and gradle/actions/setup-gradle, and requires every action to be pinned to a full commit SHA. A new action needs the allow list changed in the repository settings first.

### Dependency updates

[Renovate](https://docs.renovatebot.com/) opens a pull request for each dependency update, and workflow 1 tests it like any other pull request. Renovate runs as the Mend Renovate app on Mend's servers, which clone the repository for each run. It reads its settings from renovate.json. Renovate is licensed under AGPL-3.0 and never ships in the app.

- **What it updates.** The plugins and libraries in gradle/libs.versions.toml, the Gradle wrapper, and the actions and the runner image in .github/workflows/. ktfmt and Gummy Bears have no library entry in the catalog, so two custom managers in renovate.json find their versions. The Gummy Bears manager names the artifact for API 29, so change it when minSdk changes. Renovate would also update the JDK version in the workflow's setup-java step, but the build needs JDK 17, so renovate.json turns that off. Nothing updates the Android SDK packages in the workflow or the Android versions in build-logic. Change these by hand.
- **When.** Renovate creates new branches only on Fridays, in the Asia/Dhaka time zone. It still rebases open pull requests on other days.
- **How many.** At most five of Renovate's pull requests are open at once, with no hourly limit. Updates beyond that wait for the next Friday after a pull request closes. The Dependency Dashboard lists them.
- **The 14-day wait.** AGENTS.md says to wait 14 days after a release before adopting it, unless the release fixes a security issue. Renovate creates no branch for a release that is less than 14 days old, or that has no release date. The Dependency Dashboard, an issue that Renovate keeps open, lists these updates under "Pending Status Checks".
- **Security fixes.** A pull request that fixes a known vulnerability skips the wait and the schedule. Renovate finds these fixes in the repository's Dependabot alerts and in the [OSV](https://osv.dev/) vulnerability database, which it downloads and searches offline. When a release fixes a security issue that neither source lists, such as a Gradle release, tick its box under "Pending Status Checks" to create the pull request now.
- **NewPipeExtractor.** Its releases are only on JitPack, which settings.gradle.kts adds for TeamNewPipe's group alone ([architecture.md](architecture.md#build-settings)). Renovate reads that repository from the `exclusiveContent` block ([registry-urls.ts](https://github.com/renovatebot/renovate/blob/44.140.0/lib/modules/manager/gradle/parser/registry-urls.ts#L183-L197)). Its Maven datasource takes a release's date from the `Last-Modified` header of the release's POM ([maven/index.ts](https://github.com/renovatebot/renovate/blob/44.140.0/lib/modules/datasource/maven/index.ts#L80-L82)). JitPack sets that header when it builds the version, the first time someone asks for it ([JitPack's README](https://github.com/jitpack/jitpack.io/blob/master/README.md)), so the 14 days count from JitPack's build, not from the Git tag. JitPack built v0.26.5 six minutes after its GitHub release. JitPack lists each version with and without its `v`, such as v0.26.5 and 0.26.5, and serves the same jar for both. Renovate treats the two as equal, so its pull request may use either form. Renovate updates only the dependencies that the catalog names, so nanojson, which comes only through NewPipeExtractor's POM, pinned to a commit, changes only with a NewPipeExtractor release. A NewPipeExtractor release that fixes a YouTube breakage waits 14 days like any other, because the rule's only exception is security fixes. An update that changes NewPipeExtractor's requests to YouTube fails the YouTube layer's tests until the saved responses are recorded again, as [Saved YouTube responses](#recording-the-sets) describes.
- **Groups.** Entries that share a version in the catalog change together, such as Hilt's three entries. Kotlin and KSP share one pull request. So do Spotless and ktfmt, because ktfmt 0.65 and later are published as org.jetbrains.kotlinx:ktfmt, which only Spotless 8.10.4 or later can load. Renovate leaves out a group member that is still inside its 14 days, so a ktfmt release can arrive before the Spotless release that loads it. That pull request fails spotlessCheck until a later run adds Spotless. detekt and compose-rules share one too, because each compose-rules release is built for one detekt version. That pull request waits until the maintainer ticks its box on the Dependency Dashboard, after checking compose-rules' supported versions table.
- **Pre-releases.** Renovate offers a pre-release only when the current version is a pre-release with the same major, minor and patch numbers. So detekt moves from one 2.0.0 alpha to the next or to 2.0.0, and every other dependency gets stable releases only ([ignoreUnstable](https://docs.renovatebot.com/configuration-options/#ignoreunstable)).
- **Titles.** Gradle updates start with "build:" and Actions updates with "ci:", as the commit message rule in AGENTS.md asks.
- **Up to date with main.** The ruleset requires a branch that's up to date with main, and Renovate rebases its open pull requests when main moves. To rebase one now, tick the rebase box in its description. Don't press "Update branch". Renovate would then treat the pull request as edited by someone else and stop updating it.
- **Fixing a pull request.** Some updates need a change before Checks passes. A ktfmt update can change the formatting, a Kotlin or library update can deprecate an API the code uses, an AGP update can need new build tools, and a NewPipeExtractor update can need the saved YouTube responses recorded again. Push the fix to Renovate's branch. Renovate then leaves the branch alone, so keep it up to date with main yourself. For ktfmt and AGP updates, the pull request's description names the usual fix.
- **The Gradle wrapper.** Renovate writes the new distributionSha256Sum, then runs the wrapper task to update gradle-wrapper.jar, gradlew and gradlew.bat. If that task fails on Mend's servers, only gradle-wrapper.properties changes. Then run `./gradlew wrapper` on the branch and push any changes it makes.
- **Review.** Nothing merges on its own. Before merging, check that Checks passed and read the release notes, especially detekt's. For a library that ships in the app, check that the update adds no artifact whose license breaks the rule in AGENTS.md.
- **Exceptions.** Renovate's pull requests don't use the pull request template, and their branches start with renovate/.

AGENTS.md has the command that checks renovate.json.

## Running tests locally

Run these from the repository root. AGENTS.md lists the command that runs every check the pull request workflow runs.

| To run | Command |
|---|---|
| Every unit test | `./gradlew test` |
| One plain Kotlin/JVM module's tests | `./gradlew :core:mvi:test` |
| One Android module's tests | `./gradlew :core:data:testDebugUnitTest` |
| One test class | Add `--tests` with the class name, such as `./gradlew :core:mvi:test --tests "io.github.dewfallapp.core.mvi.counter.CounterReducerTest"` |

Each module writes an HTML report to build/reports/tests/test/index.html in a plain Kotlin/JVM module, or to build/reports/tests/testDebugUnitTest/index.html in an Android module. The XML results go to build/test-results/. AGP 9 creates unit tests for the debug build type only, so Android modules have no release unit tests.

### Writing tests

- Use kotlin.test's annotations and assertions, such as kotlin.test.Test, BeforeTest, AfterTest and assertEquals. They run on JUnit 6 in plain Kotlin/JVM modules and on JUnit 4 in Android modules, so a test reads the same in both. [architecture.md](architecture.md#build-settings) explains why there are two.
- Name each local test in src/test/ with backticks, as a sentence about what it checks, such as `` `increment adds one` ``. detekt's naming rule skips test folders. Don't do this in src/androidTest/: Android runs method names with spaces only from API level 30, and the minimum is 29 ([Kotlin coding conventions](https://kotlinlang.org/docs/coding-conventions.html#names-for-test-methods)).
- Put tests in the src/test/ folder of the module whose code they test.

### Reducers

A reducer is a plain function, so its test calls reduce and compares the Next it returns. It needs no coroutines and no ViewModel. [CounterReducerTest.kt](../core/mvi/src/test/kotlin/io/github/dewfallapp/core/mvi/counter/CounterReducerTest.kt) shows the pattern.

### ViewModels and coroutines

ViewModel tests use kotlinx-coroutines-test, as Google's guides describe ([Testing Kotlin coroutines](https://developer.android.com/kotlin/coroutines/test), [Testing Kotlin flows](https://developer.android.com/kotlin/flow/test)). [MviViewModelTest.kt](../core/mvi/src/test/kotlin/io/github/dewfallapp/core/mvi/MviViewModelTest.kt) shows the pattern.

- Replace the Main dispatcher with `Dispatchers.setMain(UnconfinedTestDispatcher())` in a @BeforeTest function, and reset it with `Dispatchers.resetMain()` in an @AfterTest function. viewModelScope runs on Main. Without a Main dispatcher, its coroutines run on Dispatchers.Default's threads, and the test becomes flaky.
- Wrap each test in runTest. It shares the Main dispatcher's scheduler and skips delays.
- Where the code under test takes a dispatcher, pass `StandardTestDispatcher(testScheduler)`, so every TestDispatcher in the test shares one scheduler. Call advanceUntilIdle() to run the work it queued.
- Read a ViewModel's state and pendingEffects through their value. Both are StateFlows that hold their latest value, so the test needs no collector.
- To check every value a Flow emits over time, collect it in backgroundScope, as Google's flow testing guide shows. The project doesn't use Turbine.
- setMain, resetMain, UnconfinedTestDispatcher and advanceUntilIdle are marked experimental, and compiler warnings fail the build. So annotate the test class with `@OptIn(ExperimentalCoroutinesApi::class)`.
- Test which work each intent starts, and that its result comes back through the reducer. The reducer's own tests cover the rest of the screen's logic.

### Logging

A class that logs receives a Logger in its constructor, as [architecture.md](architecture.md#logging) describes. So a test chooses what happens to the lines.

- Pass `Logger.Silent` when the test doesn't check logging. It writes nothing and builds no text.
- To check what a class logs, pass a Logger whose writer records each line. LogWriter has a single function, so a lambda is enough:

  ```kotlin
  val lines = mutableListOf<String>()
  val logger = Logger({ level, tag, text -> lines += "$level $tag $text" }, LogLevel.DEBUG)
  ```

- Check log lines only where logging is the behavior under test, such as a warning that a class must write when a retry fails. Compare the message and the fields, not stack frames, which change whenever the code moves.
- Tests never read logcat. LogcatWriter's own tests pass a function in place of Log.println, because android.util.Log has no working code in local unit tests.

## Saved YouTube responses

The YouTube layer's tests replay YouTube's real answers, which are saved in core/youtube/src/test/resources/youtube/. A test never reaches YouTube. Recording the answers does, so a developer runs it by hand, and it never runs in `./gradlew test` or in CI. The code is in the `newpipe.saved` package of `:core:youtube`'s tests.

### The saved sets

A saved set is one call through the layer, with every request it sent and YouTube's answer to each. SavedSet.kt lists the sets, with each set's call, the category it fails with, if it fails, and the requests it sends, named by the Endpoint enum there. The recorder and the tests both read that list.

| Set | Call | Requests | Size | What its tests check |
|---|---|---|---|---|
| SEARCH_NASA | Search for "nasa" | 1 | 408 KiB | Channels named after NASA, Shorts are marked, nothing is dropped |
| VIDEO_BIG_BUCK_BUNNY | Video details of aqz-KE-bpKQ, the Blender Foundation's Big Buck Bunny | 7 | 438 KiB | Every detail, the description as plain text, and the stream links |
| UPLOADS_NASA | Latest uploads of NASA's channel, which has a Live tab | 2 | 804 KiB | The two tabs merged in the order that ChannelUploads states, with no Shorts |
| UPLOADS_JAWED | Latest uploads of jawed's channel, which has no Live tab | 1 | 48 KiB | Its one video, "Me at the zoo", and its relative publish time |
| UPLOADS_AGE_RESTRICTED | Latest uploads of an age-restricted channel | 5 | 1,049 KiB | Its system playlists, read in place of its tabs |
| VIDEO_PRIVATE | Video details of a private video | 2 | 4 KiB | PRIVATE |
| VIDEO_AGE_RESTRICTED | Video details of an age-restricted video | 2 | 5 KiB | AGE_RESTRICTED |
| CHANNEL_TERMINATED | Latest uploads of a channel that YouTube closed | 1 | 22 KiB | CHANNEL_CLOSED |
| CHANNEL_DELETED | Latest uploads of a deleted channel | 1 | 21 KiB | UNAVAILABLE |

- The requests don't count sw.js, which every set that reaches the web client also fetches. See [What the tests check](#what-the-tests-check).
- Together the sets take about 2.7 MiB, or about 230 KiB compressed. Each new recording can add up to about that much to the repository's history.
- Video details and stream links send the same requests, so their tests share one set.
- The content should last: the Blender Foundation's film, NASA's channel, and "Me at the zoo", the first video on YouTube. The failures use the IDs that NewPipeExtractor's own tests use.
- Whether YouTube treats a channel as age-restricted can depend on the country of the network that asks, as a note in NewPipeExtractor's own tests says ([YoutubeChannelExtractorTest](https://github.com/TeamNewPipe/NewPipeExtractor/blob/v0.26.5/extractor/src/test/java/org/schabi/newpipe/extractor/services/youtube/YoutubeChannelExtractorTest.java#L714-L723)). The recorder checks the set's requests, so it fails if the channel's page came back without the restriction.
- A video that never existed has no set yet. #155 adds one when it maps that case to UNAVAILABLE.

### What the tests check

- Each function's result, read from a real answer, as the table says. Each test makes one call, through a SavedYouTube of its own.
- The requests that each call sends, in order. They match the counts in [What each function sends](architecture.md#what-each-function-sends). The first call in a process that uses the web client also fetches www.youtube.com/sw.js, and every replayed call is a first call, so each one that reaches the web client fetches it too.
- A playable stream link. A test can't play anything, so it checks what a player needs: a sound format and a picture format that play together, and a format that has both, each with a known length, and the HLS manifest, all HTTPS links to googlevideo.com. The layer keeps a format only when it is a single file with a direct link, and no link needed YouTube's player code to decode, which would have added a request. Fetching real bytes from a link is left to the daily check (#38).
- Relative publish times. See [Relative dates](#relative-dates).

### How a set is stored

Each set has a folder named after it, such as video-big-buck-bunny. It holds exchanges.txt, which lists each request and its answer in order, and one file for each answer's body, named by its place and the last segment of its link, such as 07-player.json. A body stays exactly as YouTube sent it, apart from masking.

```
# Saved YouTube responses for the set VIDEO_BIG_BUCK_BUNNY.
# Written by ./gradlew :core:youtube:recordYouTubeResponses. Don't edit it by hand.
# Signed out, asking for the country GB and the language en-GB.
# recorded: 2026-11-16
# newpipe-extractor: v0.26.5

> POST https://youtubei.googleapis.com/youtubei/v1/visitor_id?prettyPrint=false
> Accept-Language: en-GB, en;q=0.9
> Content-Type: application/json
> User-Agent: com.google.android.youtube/21.03.36 (Linux; U; Android 15; GB) gzip
> X-Goog-Api-Format-Version: 2
>
> {"context":{"client":{"clientName":"ANDROID","clientVersion":"21.03.36", ...}}}
< 200
< content-type: application/json; charset=UTF-8
<
< @01-visitor_id.json
```

Lines that start with `>` are the request: the method and link, the headers sorted by name, then an empty line and the body, on one line. Lines that start with `<` are the answer: the status code, the headers, then an empty line and the name of the body's file. So a changed request shows as a changed line in a diff.

- .gitattributes marks the folder `-text`, so Git never changes the files' line endings, and `linguist-generated`, so GitHub collapses their diffs in a pull request.
- .editorconfig turns off the final newline, the trimming of trailing whitespace and the line-ending setting in that folder, so an editor doesn't change a body when it saves it.
- Spotless and detekt read only Kotlin files, and lint leaves the folder alone. Only the tests read it.

### How the replay works

SavedYouTube builds the layer over one saved set for a test. Its HTTP client, ReplayingClient, answers each request from the set in an interceptor, before OkHttp opens a connection. It goes under NewPipeDownloader, as the app's client does, so the layer's own HTTP code runs too, with its host checks.

A request matches a saved one when its method, link, headers and body are the same. Only the order of the headers may differ, because NewPipeExtractor builds the Origin and Referer headers with Map.of, whose order changes from one JVM to the next ([getOriginReferrerHeaders](https://github.com/TeamNewPipe/NewPipeExtractor/blob/v0.26.5/extractor/src/main/java/org/schabi/newpipe/extractor/services/youtube/YoutubeParsingHelper.java#L1184-L1187)). Matching exactly means that any change in NewPipeExtractor's requests fails a test.

These parts of a request change from one run to the next. Each is made the same:

| Part | Why it changes | How it stays the same |
|---|---|---|
| Visitor data, in three player requests | Each comes from the answer to the visitor ID request before it | The recorder gives each value the same placeholder in the answer and in the request |
| The playback nonce in the body, and the `t` parameter in the link | NewPipeExtractor makes them with a random generator | resetNewPipeExtractorState gives the generator a fixed seed before each call |
| The web client's version, in a header and in the body | NewPipeExtractor reads it from sw.js once per process, and keeps it in a static field | resetNewPipeExtractorState makes it read sw.js again, so each call takes its own set's version |
| The order of the Origin and Referer headers | Map.of | Headers match in any order |
| The country | It is the device's region | The recorder and the tests give NewPipeCalls a UK locale, so every request asks for GB |

Before each call, resetNewPipeExtractorState resets the static state that NewPipeExtractor keeps between calls, as NewPipeExtractor's own tests do in [ensureStateless](https://github.com/TeamNewPipe/NewPipeExtractor/blob/v0.26.5/extractor/src/test/java/org/schabi/newpipe/extractor/services/youtube/YoutubeTestsUtils.java#L27-L32): the client version, the random generator, the cookie consent flag, and YouTube's player code with what NewPipeExtractor decoded with it. SavedYouTube then builds a NewPipeCalls of its own, whose first call sets NewPipeExtractor up with the replaying client. NewPipeCalls' Mutex doesn't cover the reset, so the replay relies on this module's tests running one at a time, as JUnit and Gradle run them by default. If parallel test runs are ever turned on, these tests must still run alone.

A request without a saved answer fails the test with a report. This one came from a change to the random generator's seed:

```
No saved YouTube response matches request 2 of the saved set VIDEO_BIG_BUCK_BUNNY, recorded with NewPipeExtractor v0.26.5 on 2026-11-16. This build uses NewPipeExtractor v0.26.5.
  POST https://youtubei.googleapis.com/youtubei/v1/reel/reel_item_watch?prettyPrint=false&t=vP8jBHsy2YDR&id=aqz-KE-bpKQ&$fields=playerResponse
Closest saved request: number 2.
  Its link differs. Saved: https://youtubei.googleapis.com/youtubei/v1/reel/reel_item_watch?prettyPrint=false&t=867fZ6WKSXg3&id=aqz-KE-bpKQ&$fields=playerResponse
  Its body differs at character 389:
    saved: quest":{"videoId":"aqz-KE-bpKQ","cpn":"uGaaNCVq9tAJ9K8j","contentCheckOk":true,"
    sent:  quest":{"videoId":"aqz-KE-bpKQ","cpn":"uS5Af2_C27_cOhE1","contentCheckOk":true,"
If NewPipeExtractor changed its requests, record the set again with ./gradlew :core:youtube:recordYouTubeResponses. See docs/testing.md#saved-youtube-responses.
```

The layer itself reports such a request as NETWORK, so SavedYouTube fails the test with the report before the test checks the result.

### Relative dates

YouTube gives most publish times in lists as relative text, such as "21 years ago". NewPipeExtractor turns the text into an instant by counting back from the clock at the moment it reads the answer, in the JVM's time zone. It truncates a time in days or longer to midnight there, and takes one more day off for years ([TimeAgoParser](https://github.com/TeamNewPipe/NewPipeExtractor/blob/v0.26.5/extractor/src/main/java/org/schabi/newpipe/extractor/localization/TimeAgoParser.java#L106-L115), [TimeAgoPatternsManager](https://github.com/TeamNewPipe/NewPipeExtractor/blob/v0.26.5/extractor/src/main/java/org/schabi/newpipe/extractor/localization/TimeAgoPatternsManager.java#L22-L24)). It takes no clock from the caller, so a saved answer gives a different instant on each day that a test runs.

assertAgo checks such a time against the test's own clock: the time is an estimate, and lies between the count back from just before the call, less two days, and the count back from just after it. That holds on any day and in any time zone. The test reads the amount, such as 21 years, from the saved answer, so it still passes after the set is recorded again. Exact times, such as the publish time on a video's page, are checked exactly.

### Privacy

The repository is public, so no saved file may say which machine, session or network recorded it, and no file may keep data about a viewer. A recording showed each of these values, and the recorder masks them all:

| Value | Where YouTube puts it | What the recorder does |
|---|---|---|
| The machine's IP address | The `ip` parameter of googlevideo links, URL-encoded for IPv6, and `/ip/` in the link of an HLS manifest | Replaces every form of it with a documentation address, 192.0.2.1 or 2001:db8::1 |
| Visitor data | The answer to each visitor ID request and the context of other answers, and the player request that follows. It holds a visitor ID, a time and the network's country. | Replaces each value with VISITOR_DATA_1, VISITOR_DATA_2 and so on, the same in answers and requests |
| The visitor ID in another encoding | The `visitor_data` tracking parameter | Replaces it with VISITOR_DATA |
| Cookies | Set-Cookie headers on the web client's answers: YSC, VISITOR_INFO1_LIVE, VISITOR_PRIVACY_METADATA, __Secure-YNID, __Secure-YEC and __Secure-ROLLOUT_TOKEN | Keeps only the Content-Type and Location headers of an answer, and masks a cookie's value where a body repeats it |
| The rollout token | The context of some answers | Replaces it with ROLLOUT_TOKEN, wherever it appears |
| Event IDs | The `ei` parameter of links, the `responseId` of each answer, the tracking parameters that carry the same ID (`trackingParams`, `clickTrackingParams` and `trackingParam`), and the request IDs in the CSI tracking parameters, such as `GetPlayer_rid` | Replaces them with EVENT_ID |
| Tokens that YouTube encrypts or hashes, so no check can see what they hold | `plid`, `vm` and `of` in playback statistics links, `redir_token` in the links of a description, `id`, `bui`, `spc` and the signatures `sig` and `lsig` in googlevideo links, which cover the `ip` parameter, the attestation challenge, `videoPlaybackUstreamerConfig`, `continue_action` and `continueAction` in sign-in links, and the number in the comment at the start of sw.js, which changes on every fetch | Replaces them with TOKEN |
| The network's CDN node | The host and the `mn` parameter of googlevideo links | Replaces it with sn-masked |
| The connection's bandwidth | The `initcwndbps` parameter | Replaces it with 0 |
| The size of the machine's network | The `pl` parameter of googlevideo links: the length of the address prefix that a link was made for | Replaces it with 0 |
| The experiments that YouTube put the visitor in, some of which change from one visitor to the next | The `e` tracking parameter, and `fexp` in links | Replaces them with EXPERIMENTS |
| The network's country | sw.js, as the web client's `gl`, even though the request asks for GB, and visitor data | Replaces it with GB in `gl` and in any other country field, such as `countryCode` or `regionCode`. Lists of available countries keep it. |
| YouTube's public API key for its web client | sw.js | Replaces it with API_KEY, because GitHub's secret scanning would flag it |
| The parameters of stream, ad and tracking links that nothing reads, such as `cps`, `mh`, `mm`, `ms`, `mv`, `rms` and `pcm2cms` in googlevideo links, and `ord` and `dc_rdid` in an ad link | googlevideo links, in their query and, for an HLS manifest, their path, links to ad.doubleclick.net and other ad hosts, in their query and after semicolons in their path, the playback statistics links on s.youtube.com, `/ptracking` links, and links through YouTube's `/redirect` | Replaces each value with MASKED, but those that [Links](#links) lists, and any placeholder that a rule above wrote first |
| Data about viewers: comments, who wrote them, their handles, avatars and profile links | The comments in a video's microformat, as schema.org Comment objects, and any member whose name starts with `comment`, `teaser` or `liveChat` | Replaces each string with VIEWER_DATA and each number with 0. Only the `@type` of a schema.org object stays. Wherever else they appear, the recorder also masks every string of at least 8 characters that it found there, and every handle and channel ID, whatever its length. |
| The experiments that YouTube put the visitor in, as flags | The EXPERIMENT_FLAGS object in sw.js, and the `experiments` and `experimentFlags` objects in the Android client's player answer | Replaces each flag's value with false, 0, an empty string, or an empty list or object. The flags' names stay. |
| Any other opaque value | Anywhere, such as entity keys, share entities, thumbnail parameters and the IDs in image links | Replaces each value that isn't kept with TOKEN_1, TOKEN_2 and so on, the same in answers and requests, or with the number alone for a value that is a number |

#### Opaque values

The rules above mask the values that a recording showed. A value of a kind that no rule knows yet would slip through them, so the masking also works the other way round: every opaque value is masked, unless it sits under a key on a short list of kept keys. SavedResponsesPrivacyTest fails on any opaque value that is neither masked nor kept, so a kind of value that YouTube adds later fails a test instead of reaching the repository. The check is in OpaqueValues.kt, and the recorder and the test share it.

A value is opaque on its own when it is at least 16 characters long, and it is one of these:

- A number.
- Hex, with both digits and letters, which may have hyphens, as a UUID has.
- A mix of upper and lower case that changes from one kind of character to another as often as base64 does: on average every 2.2 characters, or every 3 when it has a digit.
- A single-case mix of letters and digits, such as base32 or base36, with a part of at least 12 characters between hyphens and underscores. How often it changes between letters and digits doesn't count, because base32 can have long runs of letters.

Words don't count, such as COMPACT_VIDEO_RENDERER, YoutubeChannelV2 or GetPlayer_rid, and neither do the placeholders, which are upper-case words. A value that isn't opaque on its own, but isn't a word and has a digit or both cases, counts too when another value under its key is opaque on its own. A random value can look like a word by chance, and this catches it where its key gives it away.

16 characters of base64 hold 12 bytes, enough for an ID that could tie a file to a machine or a session. The shorter random values that the recordings showed, ei, plid, of and vm, have rules of their own in the table above. Video IDs have 11 characters, and the `t` parameter of a player request has 12 and comes from the fixed seed.

The check looks for opaque values in every saved file, exchanges.txt included:

- In a JSON body or a script, each string sits under its member's name, or under the name of the array it is in. Members' names count too, and so does text outside strings, such as a comment at the start of sw.js.
- In a link, the host counts, each path segment sits under the segment before it, or the host, each query parameter under its name, and the fragment after `#` counts too. Parameters' names count. Links inside other values count, and so do the request links and the headers in exchanges.txt.
- URL-encoded text is decoded, and so is base64, from each run of at least 6 characters that base64 can be made of, and from each of the run's first four characters, so a value that follows other text still decodes. A last character that can't make a byte is dropped, so a value that runs on into other characters decodes too. Decoded text that has its own names, such as a link, is checked by those names. Other decoded text stays under the outer key. In decoded bytes that are mostly not text, each printable run of at least 6 characters is read further.
- Each link, parameter, decoding and the like is one step deeper. Past 8 steps, every run counts as opaque, so a value can't hide by being nested deeper.
- The digits after the point of a decimal number don't count.

A value may stay when it sits under a key that keeps it, or when it is a value, or part of one, that sits under such a key elsewhere in the set, such as a channel ID in a playlist's ID. A key belongs on the list only if NewPipeExtractor reads its value or a test checks it, and its value can't identify the machine or the session. These keys keep their values everywhere:

| Kept key | Why |
|---|---|
| browseId | Channel and playlist IDs. NewPipeExtractor reads them and sends them, and the tests check channel IDs. |
| channelId | Channel IDs. NewPipeExtractor reads them, and the tests check them. |

These keep their values in a request, when no earlier answer in the set holds the value, because the replay compares every request, and NewPipeExtractor makes these values itself. A value that an earlier answer holds came from YouTube, so it is masked with the same placeholder in that answer and in the request, and the replay still matches. An answer keeps one of these values only when it is part of a value that a request kept.

| Kept key | Why |
|---|---|
| params | The parameters that pick a channel's tab, a search's filter or a playlist's view, which NewPipeExtractor sends as constants. The replay compares them. |
| continuation | The request that NewPipeExtractor builds from a playlist's ID to read its first videos ([YoutubePlaylistExtractor](https://github.com/TeamNewPipe/NewPipeExtractor/blob/v0.26.5/extractor/src/main/java/org/schabi/newpipe/extractor/services/youtube/extractors/YoutubePlaylistExtractor.java#L103-L118)). It holds that ID and a constant, and the replay compares it. |
| cpn | The playback nonce, which NewPipeExtractor makes from the fixed seed, so it is the same on every run. The replay compares it. |

YouTube also names the tabs of a channel in its answer, with the same params that NewPipeExtractor sends for them, so these constants in NewPipeExtractor's code stay in a request even when an earlier answer holds them:

| Value | Why |
|---|---|
| EgZ2aWRlb3PyBgQKAjoA | The params of a channel's Videos tab, which NewPipeExtractor sends for a channel's uploads ([YoutubeChannelTabExtractor](https://github.com/TeamNewPipe/NewPipeExtractor/blob/v0.26.5/extractor/src/main/java/org/schabi/newpipe/extractor/services/youtube/extractors/YoutubeChannelTabExtractor.java#L61-L62)). |
| EgdzdHJlYW1z8gYECgJ6AA | The params of a channel's Live tab, without its URL-encoded padding, which NewPipeExtractor sends for a channel's past live streams ([YoutubeChannelTabExtractor](https://github.com/TeamNewPipe/NewPipeExtractor/blob/v0.26.5/extractor/src/main/java/org/schabi/newpipe/extractor/services/youtube/extractors/YoutubeChannelTabExtractor.java#L65-L66)). |

KEPT_KEYS, KEPT_REQUEST_KEYS and NEWPIPE_EXTRACTOR_VALUES in OpaqueValues.kt are the lists, with the same reasons. When the test fails, its message names each file and key, never the value, and writes (opaque key) in place of a key that may itself be opaque or real, such as a path segment or a host. Mask the value in Masking.kt. Add its key to a list only when it meets the rule above, with the reason.

#### Links

Stream links on googlevideo.com, links to ads and ad trackers, the links that YouTube's player reports playback to, and links through YouTube's redirect say what happened in the session, and which machine and network they were for. Their parameters are masked by default: each value becomes MASKED, unless Dewfall or NewPipeExtractor v0.26.5 reads it for the layer's calls. Links.kt holds the kept parameters, with the same reasons:

| Link | Kept parameter | Why |
|---|---|---|
| googlevideo.com | `expire` | When the link stops working. Dewfall's expiryOf in FormatMapping.kt reads it from a format's link, and from the path of an HLS manifest's link, and StreamLinksReplayTest checks it. |
| www.youtube.com/redirect | `q` | The page that a link in a description goes to. NewPipeExtractor reads it to write the description ([getUrlFromNavigationEndpoint](https://github.com/TeamNewPipe/NewPipeExtractor/blob/v0.26.5/extractor/src/main/java/org/schabi/newpipe/extractor/services/youtube/YoutubeParsingHelper.java#L695-L721)), and VideoDetailsReplayTest checks the description. |

NewPipeExtractor reads a format's length, type and quality from the answer's fields, not from its link. Its code reads two more parameters of a googlevideo link, and both are masked, because nothing that the layer does reads them:

- `c`, the client that asked for the link, only to fetch the start of a format for a DASH manifest, which Dewfall never asks for ([the patterns](https://github.com/TeamNewPipe/NewPipeExtractor/blob/v0.26.5/extractor/src/main/java/org/schabi/newpipe/extractor/services/youtube/YoutubeParsingHelper.java#L184-L189), [getInitializationResponse](https://github.com/TeamNewPipe/NewPipeExtractor/blob/v0.26.5/extractor/src/main/java/org/schabi/newpipe/extractor/services/youtube/dashmanifestcreators/YoutubeDashManifestCreatorsUtils.java#L603-L652)).
- `n`, only to decode it with YouTube's player code ([getThrottlingParameterFromStreamingUrl](https://github.com/TeamNewPipe/NewPipeExtractor/blob/v0.26.5/extractor/src/main/java/org/schabi/newpipe/extractor/services/youtube/YoutubeThrottlingParameterUtils.java#L207-L221)). No saved link has one, and a link that did would need the player code, which no set fetches.

The ad link that a recording showed, on ad.doubleclick.net, carries its parameters after semicolons in its path, among them `ord` and `dc_rdid`. Nothing reads any of them. NewPipeExtractor only recognises a link on googleads.g.doubleclick.net as an ad, when it is asked to open one ([YoutubeStreamLinkHandlerFactory](https://github.com/TeamNewPipe/NewPipeExtractor/blob/v0.26.5/extractor/src/main/java/org/schabi/newpipe/extractor/services/youtube/linkHandler/YoutubeStreamLinkHandlerFactory.java#L127-L131)).

#### Known limits

The recorder's check is the guarantee: it looks for every real value that it knows. SavedResponsesPrivacyTest knows no real value, so it relies on patterns, and these shapes get past its patterns. None of the saved values has one of them.

- A single-case run made only of letters, such as base32 that happens to have no digit, looks like a word, so it isn't opaque on its own. It still counts under a key that holds an opaque value.
- A value shorter than 16 characters isn't opaque on its own. The short random values that the recordings showed have rules of their own.
- Data about viewers is found by the shapes that YouTube uses today: schema.org Comment objects, and members whose names start with `comment`, `teaser` or `liveChat`. Data in a new shape gets past the test. The recorder still looks anywhere for the strings that it masked in a known shape, as the next list says.
- A tracked link counts when it is written with its scheme or `//`, also with `\/` for a slash and `\u0026` for an ampersand. A tracked link that is URL-encoded inside another value is checked only for opaque values.
- A country field counts when its name is `gl`, ends with `_gl`, or is `country`, `countryCode`, `regionCode` or `contentRegion`, written in any case, with or without an underscore.

The recorder's check has limits of its own:

- It knows the machine's address only from the `ip` parameter of stream links. If the machine reaches YouTube over both IPv4 and IPv6 in one run, an address that no link names is found only as text, by the check for any public address.
- It knows the network's country from sw.js's `gl` and from visitor data, not from the `gl` that other answers repeat from the request.
- From data about a viewer, it looks elsewhere only for strings of at least 8 characters, and for handles and channel IDs of any length: a string that is a handle, and the handle or channel ID in a link to a viewer's page, also decoded from its URL encoding. It finds a handle only where the handle ends, so a viewer's @al doesn't match a channel's @alpha. A shorter name or text is masked where the shape is known, but not looked for anywhere else, as it could match other text by chance.
- A comment by the channel itself counts as data about a viewer too, so its name and channel ID are masked wherever they appear. If that breaks a replay test, record another video.

NewPipeExtractor's own recorder masks only IPv4 addresses, and only in the bodies of answers ([RecordingDownloader](https://github.com/TeamNewPipe/NewPipeExtractor/blob/v0.26.5/extractor/src/test/java/org/schabi/newpipe/downloader/RecordingDownloader.java#L39-L84)), so its saved answers keep IPv6 addresses, cookies and visitor data.

The recorder records signed out: its client keeps no cookies, and the only cookie in its requests is the fixed `SOCS=CAE=` that NewPipeExtractor adds. It asks for GB and en-GB, as the tests do.

The recorder keeps YouTube's answers in memory only. First it collects the real values from every set that it records: the machine's address, the session's values, the network's country and the strings in data about viewers. If no set names the address or the country, it also makes VIDEO_BIG_BUCK_BUNNY's call, whose stream links name the address and whose sw.js names the country, keeps only those values, and writes nothing of it. If they are still unknown, it writes nothing at all, because its check couldn't look for them.

Then it masks each set and checks it for every real value of the run. The check decodes exactly as the check of opaque values does, with the same code: every string of a body, every link and its parameters, URL-encoded text, and base64, nested in each other, up to 8 steps deep. In every text, at every layer, it looks for:

- The address: as text, with its colons URL-encoded once or twice, written out in full, written with other zeros, as one number in decimal or hex, and an IPv4 address as an IPv4-mapped IPv6 address. In every array of bytes that it decodes from base64, it looks for the address's 4 or 16 raw bytes and for its text.
- Each visitor data value and visitor ID, each cookie value and each rollout token, as text and as bytes.
- From data about a viewer: each string of at least 8 characters, and each handle and channel ID, whatever its length, as text and as bytes. A handle counts on its own, or in a link to a viewer's page, also decoded from its URL encoding.
- The network's country, when it isn't GB, in any country field. A list of available countries isn't a country field.

It also runs the test's checks below, and fails on any opaque value that isn't kept. Only when every set passes does it write any file. Nothing unmasked is ever written to disk, not even to a temporary folder. If a set fails a check, the recorder writes no set at all, so a run never writes only some of the sets it was asked for. RecordYouTubeResponsesTest checks this with made-up recordings, and MaskingTest checks that values hidden in base64 inside URL encoding inside base64 are found.

SavedResponsesPrivacyTest runs on every pull request, and knows no real value. It fails when a saved file holds a public IP address, when a place that held a real value holds anything but its placeholder, when an opaque value is neither masked nor kept, when a parameter of a stream, ad or tracking link holds anything but MASKED or another placeholder and isn't kept, when data about a viewer isn't masked, when an experiment flag isn't masked, when an answer keeps a header other than Content-Type or Location, or when a file in the folder belongs to no set.

### Recording the sets

Record every set with:

```
./gradlew :core:youtube:recordYouTubeResponses
```

`-PyoutubeSets=SEARCH_NASA,UPLOADS_NASA` records only the sets it names. If none of them names the machine's address and the network's country, the recorder also makes VIDEO_BIG_BUCK_BUNNY's call to learn them, and writes nothing of it. The task sends real requests to YouTube, through the layer's own HTTP client and NewPipeDownloader, so the saved requests are the ones that the app sends, and the host allow list applies. It logs through the Logger, with ConsoleLogWriter: a line for each set that it records, with the number of requests, sw.js included, and the size, and a line for each kind of value that it masked, with how many. It never logs a value. For example:

```
INFO Dewfall.Recorder Recorded a saved set set=UPLOADS_NASA requests=3 kibibytes=804
INFO Dewfall.Recorder Masked values in a saved set set=UPLOADS_NASA value=ADDRESS count=182
```

The recorder writes no set, keeps every old file, says why, and fails when a set's call ends differently than SavedSet expects, when it sends other requests, when a real value is still in the masked set, or when no answer names the machine's address or the network's country. When a call sends other requests, it logs each one, in order, by its name in the Endpoint enum, or OTHER for a request that the enum doesn't name, so SavedSet can be changed to match. Each line looks like this:

```
WARN Dewfall.Recorder A saved set's call sent a request set=UPLOADS_JAWED position=2 endpoint=BROWSE
```

`-PyoutubeSets` leaves out a set that can't be recorded from your network.

Record the sets again:

- **When a NewPipeExtractor update changes its requests.** Renovate's pull request then fails Checks with a report like the one above. Check out Renovate's branch and record every set. If the requests changed, the recorder writes nothing and logs the requests that each call sent. Change them in SavedSet, add any new endpoint to the Endpoint enum, change the counts in the tests and in architecture.md, and record again. Then run `./gradlew :core:youtube:test`, review `git diff --stat`, and commit with a message such as "test: record saved YouTube responses again for NewPipeExtractor vX.Y.Z". Push it to Renovate's branch. Renovate then leaves the branch alone, as [Dependency updates](#dependency-updates) explains. NewPipeExtractor v0.27.0 and v0.27.1 no longer send the Android client's requests for a video's page, so their update will need this: compare onFetchPage in [v0.26.5](https://github.com/TeamNewPipe/NewPipeExtractor/blob/v0.26.5/extractor/src/main/java/org/schabi/newpipe/extractor/services/youtube/extractors/YoutubeStreamExtractor.java#L836-L871) and in [v0.27.0](https://github.com/TeamNewPipe/NewPipeExtractor/blob/v0.27.0/extractor/src/main/java/org/schabi/newpipe/extractor/services/youtube/extractors/YoutubeStreamExtractor.java#L827-L850).
- **After any other NewPipeExtractor update,** so that the version the app ships reads the saved answers.
- **When the daily check (#38), once it exists, shows that YouTube changed its answers.**

## Screenshot tests

TODO

## Database migration tests

There are no migration tests yet. The first public release is the first GitHub release, including a pre-release, published from a version tag. A tag made only to test the release workflow, with no published release, doesn't count, and nightly APKs don't count. Until then, the database stays at version 1 and a schema change needs no migration, as AGENTS.md says. Phase 5 adds the first tables (#48).

**The schema check.** Room writes the schema of each database version to core/database/schemas/, and the files are committed. When the schema changes, Room rewrites the file, even when the version number stays the same, and the build still passes. So the pull request workflow has a step after the build that fails when the build changed or added a file in that folder. Locally, `git status core/database/schemas` shows the same changes after a build.

**From the first public release on.** A committed schema file must never change after that, because phones with the release have databases in that shape. The workflow then needs a second check, which fails when a schema file that's on main changes or disappears. Phase 9 adds it (#134).

**Migration tests.** Phase 9 sets them up (#53), so they're ready for the first migration after the first public release. Room's MigrationTestHelper, from androidx.room3:room3-testing, creates a database at an old version from its schema file, runs the migrations, and checks the result against the new schema ([Test migrations](https://developer.android.com/training/data-storage/room/migrating-db-versions#test)). On Android it needs an Instrumentation, so the tests run as androidTest tests on an emulator or a phone. The Room Gradle plugin already adds the schema files to the androidTest assets. Google doesn't recommend Robolectric for Room tests ([Test and debug your database](https://developer.android.com/training/data-storage/room/testing-db)), and tests on the computer's JVM would need a Kotlin Multiplatform JVM target, which ADR 0006 rules out. So #53 also decides which workflow runs an emulator for these tests.
