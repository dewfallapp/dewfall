# 0007. How the app logs

## Status

Accepted

## Context

The roadmap's Phase 3 asks how the app logs: one logger that the plain Kotlin/JVM modules can use too, no user data in any log, and less detail in release builds (#131). AGENTS.md adds that nothing may send data anywhere the user didn't choose. So the logger makes no network calls, has no analytics, and adds no startup initializer.

Every fact below was checked on October 21, 2026. Library versions are the newest stable releases on that day.

### What Android's guidance says

Google's [Log Info Disclosure](https://developer.android.com/privacy-and-security/risks/log-info-disclosure) page treats logcat as a place where data can leak. Since Android 4.1, only privileged system apps can read it, but some preloaded apps hold that privilege. The page recommends configuring the logcat backend for developer builds only, and logging only compile-time constants whenever possible. Its fallback is for apps that can't avoid logs in production: keep only warnings and errors, have R8 remove the other levels, remove values that aren't constant from the log statements, and prepare flags that can shut logging down during an incident.

### What limits the options

- The plain Kotlin/JVM modules can't use android.util.Log ([ADR 0006](0006-platform-and-android-versions.md)). The YouTube layer is one of them, and most errors will start there.
- Exception messages carry user data. NewPipeExtractor builds many of them from URLs and IDs, such as `"URL not accepted: " + url` ([LinkHandlerFactory](https://github.com/TeamNewPipe/NewPipeExtractor/blob/v0.26.5/extractor/src/main/java/org/schabi/newpipe/extractor/linkhandler/LinkHandlerFactory.java#L80)) and `"Failed to resolve channelId for " + idOrPath` ([YoutubeChannelHelper](https://github.com/TeamNewPipe/NewPipeExtractor/blob/v0.26.5/extractor/src/main/java/org/schabi/newpipe/extractor/services/youtube/YoutubeChannelHelper.java#L121)). android.util.Log prints a Throwable's stack trace, which starts with its message.
- Nothing shrinks the code yet. build-logic doesn't turn on R8's minify, so a release build keeps every log call that the code makes.
- liblog cuts any log entry whose payload is over LOGGER_ENTRY_MAX_PAYLOAD, 4068 bytes ([log.h](https://android.googlesource.com/platform/system/logging/+/bcac7c30d88a3773a7c0bc9f5617a23a886331fd/liblog/include/log/log.h#71)). An error with ten causes and their stack frames goes far past that.

### NewPipeExtractor's own logger

NewPipeExtractor [v0.26.5](https://github.com/TeamNewPipe/NewPipeExtractor/releases/tag/v0.26.5), its newest release, released on August 15, 2026, has its own logging hook, ExtractorLogger.

- An app turns it on with the static [setLogger](https://github.com/TeamNewPipe/NewPipeExtractor/blob/v0.26.5/extractor/src/main/java/org/schabi/newpipe/extractor/utils/ExtractorLogger.java#L29-L31). Until then it stays silent. A call with values returns before it formats them ([logFormat](https://github.com/TeamNewPipe/NewPipeExtractor/blob/v0.26.5/extractor/src/main/java/org/schabi/newpipe/extractor/utils/ExtractorLogger.java#L75)), and every call returns before it reaches a logger ([log](https://github.com/TeamNewPipe/NewPipeExtractor/blob/v0.26.5/extractor/src/main/java/org/schabi/newpipe/extractor/utils/ExtractorLogger.java#L40)).
- It has 10 calls, all at debug level. Five of them log user data. [StreamInfo.getInfo](https://github.com/TeamNewPipe/NewPipeExtractor/blob/v0.26.5/extractor/src/main/java/org/schabi/newpipe/extractor/stream/StreamInfo.java#L81-L89) logs the video URL, in two calls. Every Info [logs itself](https://github.com/TeamNewPipe/NewPipeExtractor/blob/v0.26.5/extractor/src/main/java/org/schabi/newpipe/extractor/Info.java#L57), and its [toString](https://github.com/TeamNewPipe/NewPipeExtractor/blob/v0.26.5/extractor/src/main/java/org/schabi/newpipe/extractor/Info.java#L69-L74) holds the URL and the name, which is a title, a channel name or the search text. StreamInfo [logs itself](https://github.com/TeamNewPipe/NewPipeExtractor/blob/v0.26.5/extractor/src/main/java/org/schabi/newpipe/extractor/stream/StreamInfo.java#L65) a second time, with the video ID too. [NewPipe.init](https://github.com/TeamNewPipe/NewPipeExtractor/blob/v0.26.5/extractor/src/main/java/org/schabi/newpipe/extractor/NewPipe.java#L58-L59) logs the content language and country.
- The app's logger receives only finished text ([Logger](https://github.com/TeamNewPipe/NewPipeExtractor/blob/v0.26.5/extractor/src/main/java/org/schabi/newpipe/extractor/utils/Logger.java)), so it can't tell the constant part of a line from the values put into it.

### Uncaught exceptions

Android's RuntimeInit installs two handlers in every app process ([RuntimeInit.java](https://android.googlesource.com/platform/frameworks/base/+/1cdfff555f4a21f71ccc978290e2e212e2f8b168/core/java/com/android/internal/os/RuntimeInit.java#240)). LoggingHandler is the uncaught exception pre-handler. It writes the full stack trace, messages included, to logcat's crash buffer. KillApplicationHandler is the default handler, which ends the process. The code's comment says that apps can replace the default handler, but not the pre-handler.

### The options

| Option | License | Plain JVM artifact | Why it lost |
|---|---|---|---|
| Dewfall's own small logger | Dewfall's code, GPLv3 | Yes | Chosen |
| [Kermit 2.2.0](https://github.com/touchlab/Kermit/releases/tag/2.2.0), September 8, 2026 | Apache 2.0 | Yes, kermit-jvm | Its messages are lambdas, which make it easy to build text from values, and it has a global logger. Its [logcat writer](https://github.com/touchlab/Kermit/blob/2.2.0/kermit-core/src/androidMain/kotlin/co/touchlab/kermit/LogcatWriter.kt#L36-L40) hands each Throwable to android.util.Log, which prints its message. Dewfall would still write its own writer and error formatting. |
| [SLF4J 2.0.20](https://github.com/qos-ch/slf4j/tree/v_2.0.20), September 22, 2026, with [uk.uuid.slf4j:slf4j-android 2.0.20-0](https://github.com/nomis/slf4j-android/tree/slf4j-android-2.0.20-0), September 28, 2026 | MIT, and MIT | Yes, slf4j-api | Its messages take `{}` placeholders filled with any object, and its LoggerFactory is global. The Android backend [hands each Throwable](https://github.com/nomis/slf4j-android/blob/slf4j-android-2.0.20-0/src/main/java/uk/uuid/slf4j/android/LogAdapter.java#L203-L208) to android.util.Log. NewPipeExtractor doesn't use SLF4J, so it brings no bridge either. The older org.slf4j:slf4j-android stopped at 1.7.36, which SLF4J 2 no longer finds ([SLF4J FAQ](https://www.slf4j.org/faq.html#changesInVersion200)). |
| [Timber 5.0.1](https://github.com/JakeWharton/timber/releases/tag/5.0.1), August 13, 2021 | Apache 2.0 | No. It ships only as an Android library ([POM](https://repo1.maven.org/maven2/com/jakewharton/timber/timber/5.0.1/timber-5.0.1.pom)). | The plain Kotlin/JVM modules can't use it, so they would need a second logging API. |

None of the three libraries makes network calls or adds a startup initializer. Each would add a dependency, with the 14-day wait on every update, and still need Dewfall's own code to keep user data out.

## Decision

Dewfall has its own small logger, in the plain Kotlin/JVM module `:core:logging`. It depends on nothing.

- **Logger.** `debug`, `info`, `warn` and `error` take a message and any number of LogFields. `warn` and `error` also take the error that caused the problem. Logger is a final class, so every line, in the app and in tests, goes through the same formatting.
- **LogField.** A value logged after the message as `name=value`. It holds only counts, flags, durations and enum names, and has no version that takes a String.
- **Errors.** An error is written as the class name of the error and each cause, each with up to 30 stack frames, and at most 10 causes. Its messages, localized messages and toString() are never written, in any build.
- **Tags.** Every tag is Dewfall, or Dewfall.<area> through `withTag`, such as Dewfall.YouTube. The area is constant text.
- **Getting the logger.** Code receives a Logger in its constructor. Hilt provides one in `:app`. There is no global logger. Tests pass `Logger.Silent`, or a Logger with a writer that records lines.
- **Reducers never log.** Logging is a side effect, so `-logic` modules don't depend on `:core:logging`. ViewModels may log.
- **The writer.** In `:app`, LogcatWriter is the only code that calls android.util.Log. It splits text longer than 4000 bytes at line breaks into several entries, with the same priority and tag. It splits a single longer line between characters. Log.println hands the text to liblog through JNI's GetStringUTFChars ([android_util_Log.cpp](https://android.googlesource.com/platform/frameworks/base/+/1cdfff555f4a21f71ccc978290e2e212e2f8b168/core/jni/android_util_Log.cpp#92)). The [JNI spec](https://docs.oracle.com/en/java/javase/17/docs/specs/jni/functions.html#getstringutfchars) says that returns modified UTF-8, which writes a character outside the Basic Multilingual Plane, such as most emoji, as 6 bytes. ART writes such a character as UTF-8's 4 bytes instead, against the spec ([jni_internal.cc](https://android.googlesource.com/platform/art/+/6484611fd45e69db9f33f98bfd6864014b030ecf/runtime/jni/jni_internal.cc#222)). LogcatWriter counts it as 6, so an entry stays within 4000 bytes either way.
- **Levels.** Debug builds keep every level. Release builds keep WARN and ERROR. `:app` picks the level from BuildConfig.DEBUG.
- **NewPipeExtractor's logger.** Dewfall never sets ExtractorLogger, in any build. Half of its calls log user data, and a bridge couldn't separate the constant text from the values. Left unset, it costs nothing.
- **Checks.** detekt's ForbiddenImport rejects imports of android.util.Log, ExtractorLogger and NewPipeExtractor's Logger. It runs without type resolution, so it sees only imports. LogcatWriter.kt and its test import android.util.Log, and suppress the rule for their whole file, because an import can't carry an annotation.

### Why release builds keep warnings and errors

Google's guidance prefers no logcat output at all in release builds. Dewfall keeps one part of its fallback instead: release builds keep warnings and errors. The app has no crash reporting. When a user reports a problem, the WARN and ERROR lines in logcat are what they can read with adb and share.

Dewfall departs from three parts of that fallback:

- The fallback asks for log statements without values that aren't constant. Dewfall's lines keep LogField values and the class names and stack frames of errors, which aren't constant. They are what makes a line useful in a report, and their types keep user data out: a field holds only a count, a flag, a duration or an enum's name, and a summary holds no messages.
- The fallback has R8 remove the other levels. Nothing shrinks the code until Phase 9, so until then the Logger drops DEBUG and INFO lines in release builds with a level check, and the calls stay in the app.
- The fallback asks for flags that can shut logging down during an incident. Dewfall has none. It has no server and no remote settings, by design, so a flag could change only with a new release, and a new release can just as well fix the log call. No line carries user data, so an incident would come from a log call that breaks these rules.

### User data

User data is anything the user typed, picked or watched, and anything YouTube returned for them. No log line may contain it. It includes:

- Search queries, both YouTube searches and searches in subtitles, and any other text the user typed.
- Video, channel and playlist IDs, and any other YouTube ID.
- URLs of every kind: page, stream, thumbnail and subtitle URLs, and any URL inside another value.
- Titles, descriptions, channel names and chapter names.
- Subtitle text.
- The list of subscriptions, the watch history, the inbox, the videos marked keep, and the resume position of a video.
- File names, file paths and content URIs that contain any of the above, or that the user picked, such as the Takeout file or the export file.
- The bodies and headers of network requests and responses, cookies, and tokens such as YouTube's visitor data.
- The content language and country.
- The message and the toString() of any exception, because libraries put URLs and IDs in them.

These are safe to log: constant text, Dewfall's enum names, counts, sizes in bytes, durations, true or false, HTTP status codes, and the class names and stack frames of errors.

### Phase 9's report

The "Copy debug info" button in settings (#82) comes in Phase 9. It includes the last error the app recorded. "Report a problem" (#132) was closed as a duplicate of it. Any error text it shows uses the same summary as the logger: class names and frames, never messages. Whether logs also stay in memory for it is decided then. A writer that keeps recent lines can join LogcatWriter without changing any code that logs.

## Consequences

- No new dependency, and one logging API in every module.
- Logs say less than they could. Debugging relies on the debugger and on the YouTube layer's debug screen (#37) more than on log lines.
- Some things stay unchecked until the lint check in #144: messages, tags and field names that aren't constant, and calls to println, System.out, System.err or printStackTrace. detekt's PrintStackTrace rule catches only `printStackTrace()` on a catch block's own exception. detekt's ForbiddenMethodCall could catch println, but it needs type resolution, which this build doesn't run.
- **Uncaught exceptions are a permanent gap.** No app can replace RuntimeInit's pre-handler, so the full trace of an uncaught exception, messages included, always reaches logcat's crash buffer. Catching exceptions at module edges narrows the gap, such as the YouTube layer turning NewPipeExtractor's exceptions into Dewfall's own errors.
- Release builds still contain the debug and info calls and their constant text, because nothing shrinks the code. They return after one level check, before building any text.
- When Phase 9 turns on R8 (#146), a `-assumenosideeffects` rule on Logger.debug and Logger.info removes those calls ([rule types](https://developer.android.com/topic/performance/app-optimization/additional-rule-types)). Phase 9 checks R8's output for what stays, such as the code that builds the LogField arguments. That page describes `-maximumremovedandroidloglevel` as removing Android logging calls, such as Log.w and Log.isLoggable, at or below a level. LogcatWriter passes the priority as a variable, so that rule can't remove its calls by level. Dewfall's own `-assumenosideeffects` rule does that job instead.
- Other libraries log on their own. Media3 logs errors by default ([Debug logging](https://developer.android.com/media/media3/exoplayer/debug-logging)), and WorkManager has a minimum logging level setting ([custom configuration](https://developer.android.com/develop/background-work/background-tasks/persistent/configuration/custom-configuration)). The tasks that add them check them against this ADR.
- Letting LogField take a new kind of value, or setting NewPipeExtractor's logger, needs a new ADR.
