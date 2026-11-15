# 0008. The YouTube layer's contract: results, errors, IDs and times

## Status

Accepted

## Context

[ADR 0001](0001-newpipeextractor-behind-youtube-layer.md) puts NewPipeExtractor behind Dewfall's own interfaces for search, video details, a channel's latest uploads, stream links and subtitles. #34 defines those interfaces and the models they return, before any NewPipeExtractor code exists. #35 and #148 implement them.

From Phase 4 on, ViewModels, the player, downloads and background jobs all call these interfaces. So four choices are hard to reverse once code builds on them: how a function reports failure, which error categories exist, how YouTube IDs are typed, and which types hold times.

Every fact below was checked on October 21, 2026. Library versions are the newest stable releases on that day.

### What NewPipeExtractor reports

NewPipeExtractor [v0.26.5](https://github.com/TeamNewPipe/NewPipeExtractor/releases/tag/v0.26.5), released on August 15, 2026, is its newest release. It reports problems as checked exceptions: ExtractionException, its subclass ParsingException, ContentNotAvailableException and its subclasses, ReCaptchaException and SignInConfirmNotBotException. The HTTP code that an app gives NewPipeExtractor throws IOException. Their messages carry URLs and IDs, and Android's crash handler writes the message of every uncaught exception to logcat ([ADR 0007](0007-how-the-app-logs.md)).

The exception types limit which reasons the app can tell apart:

- Members-only videos and paid videos both throw PaidContentException. Only the message differs ([checkPlayabilityStatus](https://github.com/TeamNewPipe/NewPipeExtractor/blob/v0.26.5/extractor/src/main/java/org/schabi/newpipe/extractor/services/youtube/extractors/YoutubeStreamExtractor.java#L873-L926)).
- A removed video, a video that never existed, and a premiere or live stream that hasn't started all reach the same generic ContentNotAvailableException at the end of that function.
- NewPipeExtractor can't tell from a video's own page whether it is a Short. YoutubeStreamExtractor doesn't override [isShortFormContent](https://github.com/TeamNewPipe/NewPipeExtractor/blob/v0.26.5/extractor/src/main/java/org/schabi/newpipe/extractor/stream/StreamExtractor.java#L581-L583), which returns false. Lists, such as search results, do mark Shorts.

### What Android's guidance says

Google's [data layer guide](https://developer.android.com/topic/architecture/data-layer) says data sources must be main-safe, safe to call from the main thread. For errors, its default is exceptions, with custom exception types for errors the app knows about. It also allows a Result class, which "models errors and other signals that can happen as part of processing the result".

### Three ways to return failure

Screens use MVI. The ViewModel runs the work and sends its result back through the reducer as an intent ([ADR 0002](0002-mvi-with-playback-state-outside.md)).

| Option | With MVI | With ADR 0007 |
|---|---|---|
| A sealed result type of Dewfall's own: Success with the value, or Failure with an error category | The ViewModel dispatches the result as an intent. The reducer's `when` must cover every category, and its tests cover the failure states without a ViewModel. Background jobs decide whether to retry with the same `when`. | Nothing is thrown, so no library exception can reach the crash handler through a caller that forgot to catch it. |
| Kotlin's [Result](https://kotlinlang.org/api/core/kotlin-stdlib/kotlin/-result/) | A failure holds any Throwable, so a reducer needs `is` checks and an `else` branch, and screen state ends up holding a Throwable and its message. | Library exceptions still leave the module. [runCatching](https://kotlinlang.org/api/core/kotlin-stdlib/kotlin/run-catching.html) catches any Throwable, including CancellationException, which coroutines need to see. |
| Dewfall's own exceptions | Every call site needs try/catch, and nothing in Kotlin forces it. A missed catch ends viewModelScope's coroutine with a crash. | Safe only if every exception that leaves the module is Dewfall's and every call is wrapped. |

### IDs

A video ID is 11 characters from A to Z, a to z, 0 to 9, `-` and `_`. NewPipeExtractor checks the same pattern ([YoutubeStreamLinkHandlerFactory](https://github.com/TeamNewPipe/NewPipeExtractor/blob/v0.26.5/extractor/src/main/java/org/schabi/newpipe/extractor/services/youtube/linkHandler/YoutubeStreamLinkHandlerFactory.java#L48)). A channel ID is "UC" followed by 22 characters from the same set. NewPipeExtractor only checks the "UC" prefix ([YoutubeChannelHelper](https://github.com/TeamNewPipe/NewPipeExtractor/blob/v0.26.5/extractor/src/main/java/org/schabi/newpipe/extractor/services/youtube/YoutubeChannelHelper.java#L61)). The length comes from the IDs themselves, not from a document. Phase 5's Google Takeout import reads channel IDs from a file the user picks, so they come from outside the app.

Room has stored Kotlin value classes in entities through KSP since [Room 2.6.0](https://developer.android.com/jetpack/androidx/releases/room#2.6.0). Room 3 runs only through KSP.

### Time types

| Option | Status | Notes |
|---|---|---|
| kotlin.time.Instant and kotlin.time.Duration | Stable. [Instant](https://kotlinlang.org/api/core/kotlin-stdlib/kotlin.time/-instant/) since Kotlin 2.3, as [What's new in Kotlin 2.3.0](https://kotlinlang.org/docs/whatsnew23.html) says, and [Duration](https://kotlinlang.org/api/core/kotlin-stdlib/kotlin.time/-duration/) since Kotlin 1.6. Neither needs an opt-in in Kotlin 2.4.20. | In the standard library, so no new dependency. They aren't tied to the JVM, so the models stay ready to share if Dewfall ever adds Kotlin Multiplatform, which [ADR 0006](0006-platform-and-android-versions.md#plain-kotlinjvm-modules) notes as a consequence of its plain Kotlin/JVM modules. `toKotlinInstant()` converts NewPipeExtractor's java.time.Instant. `:core:logging` already uses Duration. |
| java.time.Instant and java.time.Duration | Stable, and on Android from API 26, below the app's minimum of 29 | Tied to the JVM. |
| kotlinx-datetime | [Alpha](https://kotlinlang.org/docs/components-stability.html) | A new dependency. Breaking changes are expected. |

Room has no built-in converter for any of them ([type converters](https://developer.android.com/training/data-storage/room/referencing-data)). It does have one for enums.

YouTube doesn't always give an exact upload time. Lists often give a relative time, such as "3 weeks ago", and the video page sometimes gives only a date.

## Decision

**Results.** Every YouTube layer function is a main-safe suspend function that returns `YouTubeResult<T>`: `Success` with the value, or `Failure` with a `YouTubeError`. It reports every problem with YouTube or the network as a Failure. The only exception it throws is CancellationException, when the calling coroutine is cancelled. No NewPipeExtractor exception leaves `:core:youtube`. The layer catches each one at its edge, logs it with the Logger, whose summary leaves out messages, and returns the category.

**Error categories.** `YouTubeError` is an enum, so it holds no user data, its toString is the constant's name, and LogField.state can log it.

| Category | Meaning |
|---|---|
| NETWORK | YouTube couldn't be reached. The phone may be offline, which only Android code can tell. |
| BLOCKED | YouTube refused to answer, such as with a captcha or a request to prove the user isn't a bot. |
| BROKEN | YouTube answered, but the layer couldn't read the answer. An update is needed. |
| PRIVATE | The video is private. |
| AGE_RESTRICTED | Only a signed-in adult can watch the video. |
| COUNTRY_BLOCKED | The video isn't available in the user's country. |
| MEMBERS_OR_PAID | The video is for paying members, or has to be bought or rented. |
| CHANNEL_CLOSED | YouTube closed the channel. |
| UNAVAILABLE | YouTube gives no reason the layer can tell apart: removed, never existed, or not started yet. |

The first three mean that nothing from YouTube can load right now. The others concern one video or channel. #35 and #148 map NewPipeExtractor's exceptions to these categories. docs/architecture.md lists the mapping.

**IDs.** `VideoId` and `ChannelId` are value classes. Their constructors check the formats above, strictly, and throw IllegalArgumentException with a message that never contains the text. `parseOrNull` returns null instead, for text from outside the app. Their toString leaves out the ID, because an ID is user data. Playlist IDs get no type, because no function takes or returns one.

**Times.** Times are `kotlin.time.Instant`, and lengths are `kotlin.time.Duration`. A publish time is a `PublishedTime`, whose `isApproximate` is true when YouTube gave only a date, or a relative time in days or longer. Then the time is an estimate.

**Where they live.** `YouTubeResult`, `YouTubeError`, the IDs and the models live in `:core:model`. `:core:data` exposes that module as an api dependency, so screens and reducers can see them, while `:core:youtube` stays hidden behind `:core:data`.

## Consequences

- A caller handles success and failure in one `when`. Where it switches on `YouTubeError` with an exhaustive `when`, a new category stops the build until that place decides what the category means.
- A library exception can't reach the crash handler through the YouTube layer. That narrows the gap that [ADR 0007](0007-how-the-app-logs.md#consequences) calls permanent.
- Callers get no stack trace. The layer logs it when the failure happens.
- Each call site unwraps a result, which is more code than calling a function that throws.
- Members-only and paid videos share one category, and a removed video can't be told from one that hasn't started. A list item's `VideoKind.UPCOMING` says that a video hasn't started.
- "Offline" and "YouTube not loading" need Android's connectivity state next to NETWORK. A later phase decides where that check lives.
- If YouTube ever changes its ID formats, the checks reject the new IDs everywhere at once, and the fix is one file.
- Phase 5 adds a Room converter for Instant, and checks that Room 3 stores the ID value classes.
- [ADR 0009](0009-how-the-youtube-layer-reaches-youtube.md) widens BROKEN to cover a request that the layer refuses to send, because only an update can fix that too.
- Changing any of these decisions needs a new ADR.
