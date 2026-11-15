# 0009. How the YouTube layer reaches YouTube

## Status

Accepted

## Context

[ADR 0001](0001-newpipeextractor-behind-youtube-layer.md) puts NewPipeExtractor behind the YouTube layer, and [ADR 0008](0008-youtube-layer-contract.md) defines the layer's contract. #35 adds NewPipeExtractor and implements the contract, in two pull requests. Before any function can work, five choices have to be made: where NewPipeExtractor comes from, which HTTP client sends its requests, how the layer keeps its requests to YouTube's hosts, which language and country the requests name, and how calls share NewPipeExtractor's global state. The language decides what users read, and once Phase 5 stores titles, changing it changes the stored titles only from then on.

Every fact below was checked on October 22, 2026, against NewPipeExtractor [v0.26.5](https://github.com/TeamNewPipe/NewPipeExtractor/releases/tag/v0.26.5), its newest release, released on August 15, 2026.

### Where NewPipeExtractor comes from

NewPipeExtractor's [README](https://github.com/TeamNewPipe/NewPipeExtractor/blob/v0.26.5/README.md?plain=1#L9) says that it is available on JitPack. Its build's [publishing settings](https://github.com/TeamNewPipe/NewPipeExtractor/blob/v0.26.5/extractor/build.gradle.kts#L123-L197) send releases nowhere else: they publish to Maven Central's snapshot repository and to a local folder, and sign only the snapshots. Maven Central has no release. JitPack builds each version from TeamNewPipe's Git tag on its own servers, and its [build log](https://jitpack.io/com/github/TeamNewPipe/NewPipeExtractor/v0.26.5/build.log) shows the command.

JitPack serves any group in any case, and writes the requested case into the POM. NewPipeExtractor's POM names its dependency nanojson, a fork that TeamNewPipe also builds on JitPack, as com.github.TeamNewPipe. Gradle's repository filters compare a group's name exactly, so a filter for com.github.teamnewpipe resolves NewPipeExtractor and fails on nanojson.

### The HTTP client

NewPipeExtractor sends every request through a Downloader that the app provides, and blocks until it returns. The layer's contract says that cancelling the caller cancels the request.

| | OkHttp 5.5.0 | HttpURLConnection |
|---|---|---|
| License | Apache 2.0, with Okio, also Apache 2.0 | No new artifact. On a phone it is the Android version's own, which AOSP builds from a fork of OkHttp 2 ([README.android](https://android.googlesource.com/platform/external/okhttp/+/refs/heads/main/README.android)). On the computer it is the JDK's own, so unit tests and phones would run different code. |
| Newest stable | 5.5.0, released on August 16, 2026 ([changelog](https://github.com/square/okhttp/blob/parent-5.5.0/CHANGELOG.md)) | No release of its own |
| Stopping a request in progress | `Call.cancel()`, which "immediately closes the socket connection" and may be called from any thread ([RealCall](https://github.com/square/okhttp/blob/parent-5.5.0/okhttp/src/commonJvmAndroid/kotlin/okhttp3/internal/connection/RealCall.kt#L159-L177)) | No cancel method. Android documents `disconnect()` only as a way to release resources after the body has been read ([reference](https://developer.android.com/reference/java/net/HttpURLConnection)). |

A blocking OkHttp call notices an interrupt only between reads and writes. NewPipeExtractor catches the resulting InterruptedIOException in some of its requests and carries on. In a check against YouTube, a cancelled video fetch kept going for 1.15 seconds and sent five more requests. With OkHttp's asynchronous call, a wait that an interrupt stops, and `cancel()`, the same cancel took effect within 2 milliseconds and sent nothing more.

The Android build of OkHttp merges the INTERNET permission into the app's manifest, and adds an App Startup initializer, [PlatformInitializer](https://github.com/square/okhttp/blob/parent-5.5.0/okhttp/src/androidMain/kotlin/okhttp3/internal/platform/PlatformInitializer.kt), which only stores the app's Context. Animal Sniffer rejects java.net.http's HttpClient, which Android lacks.

### The language

NewPipeExtractor reads some of YouTube's answers by their English text:

- Upcoming premieres and streams in channel tabs are marked by the text "Premieres " or "Scheduled for ", and their start is parsed in en-GB's date format ([YoutubeStreamInfoItemLockupExtractor](https://github.com/TeamNewPipe/NewPipeExtractor/blob/v0.26.5/extractor/src/main/java/org/schabi/newpipe/extractor/services/youtube/extractors/YoutubeStreamInfoItemLockupExtractor.java#L44-L49)).
- A closed channel is recognised by the text "This account has been terminated" ([defaultAlertsCheck](https://github.com/TeamNewPipe/NewPipeExtractor/blob/v0.26.5/extractor/src/main/java/org/schabi/newpipe/extractor/services/youtube/YoutubeParsingHelper.java#L1250-L1293)).
- Private, age-restricted, paid, members-only and country-blocked videos are recognised by English reasons ([checkPlayabilityStatus](https://github.com/TeamNewPipe/NewPipeExtractor/blob/v0.26.5/extractor/src/main/java/org/schabi/newpipe/extractor/services/youtube/extractors/YoutubeStreamExtractor.java#L873-L926)).

In a check against NASA's channel, the Live tab asked for in Bengali, German and Japanese lost the upcoming mark of both of its upcoming streams, which came back as plain videos with no time. The same check showed YouTube's machine translations: 26 to 28 of the Videos tab's 30 English titles came back translated. The check didn't test the other direction, but asking for en-GB can likewise bring titles in other languages back in English. The title that a video's own page gives, which the layer's video details read, is the original one.

### NewPipeExtractor's global state

NewPipeExtractor's setup, NewPipe.init, writes static fields that every request reads, and they aren't volatile. NewPipeExtractor also keeps the version of YouTube's web client, and YouTube's player code with what it decodes, in static fields that it reads and writes without locks. Every call touches the client version, and stream links also touch the player code. [How a call runs](../architecture.md#how-a-call-runs) lists each field, with the code that reads and writes it.

## Decision

**NewPipeExtractor from JitPack, for TeamNewPipe's group alone.** settings.gradle.kts adds JitPack through `exclusiveContent` with `includeGroup("com.github.TeamNewPipe")`, spelled as the POMs spell it. Gradle looks for that group only on JitPack, and for nothing else there.

**OkHttp, behind the layer's own Downloader.** NewPipeDownloader, in `:core:youtube`, sends each of NewPipeExtractor's requests with OkHttp:

- It sends only HTTPS requests to youtube.com, its subdomains and youtubei.googleapis.com. It refuses any other link without sending it, with an exception that the layer maps to BROKEN, because only a NewPipeExtractor update can make the layer ask for another host.
- Its client follows no redirects, so no request reaches a host that it didn't check. HTTP 429 and a redirect to Google's captcha page become ReCaptchaException, which maps to BLOCKED.
- It waits for OkHttp's asynchronous call in a way that an interrupt stops. On an interrupt it cancels the call, keeps the thread interrupted, and throws InterruptedIOException. NewPipeCalls runs each call with runInterruptible, so cancelling the caller interrupts the thread, and the call then throws CancellationException.

**British English, and the device's country.** Every request asks for en-GB. It names the device's region as its country, read at each call, or GB when YouTube's country list doesn't have the region.

**One NewPipeExtractor setup, and one call at a time.** NewPipeCalls sets NewPipeExtractor up on its first call, and runs one call at a time behind a Mutex, because every call reads or writes NewPipeExtractor's static caches, which have no locks. The caches belong to the process, so the Mutex does too. docs/architecture.md lists them.

## Consequences

- Lists, such as search results and a channel's uploads, may show YouTube's English machine translations of titles, while video details show the original title. A later phase can store the original title from video details.
- Upcoming streams, closed channels and the reasons a video can't play are recognised as NewPipeExtractor's code expects.
- The app trusts JitPack's build of NewPipeExtractor, which isn't signed. The filter keeps JitPack to TeamNewPipe's group, so no other library can come from it, and TeamNewPipe's group can't come from anywhere else.
- Renovate takes a JitPack version's release date from JitPack's build, so the 14-day wait counts from then. [testing.md](../testing.md#dependency-updates) explains this.
- OkHttp and Okio ship in the app, with OkHttp's startup initializer. Neither makes a network call at startup.
- A NewPipeExtractor update that needs a new host fails with BROKEN until the allow list and [Network access](../architecture.md#network-access) name the host.
- A slow call makes the next one wait, such as a search that starts while an overnight job reads a channel.
- Changing the language or the HTTP client needs a new ADR.
