# 0006. Platform and Android versions

## Status

Accepted

## Context

Phase 2 creates the Android project. Two decisions have to come first, because both are hard to reverse.

The first is the platform. Dewfall could stay an Android app, or become a Kotlin Multiplatform app that shares code with an iPhone version. Switching later means moving code between modules and rewriting parts of it.

The second is the set of Android versions the project declares: the minimum, the target and the compile version. Android refuses to install an app on a phone whose version is below the app's minimum ([uses-sdk](https://developer.android.com/guide/topics/manifest/uses-sdk-element)). So if Dewfall raises its minimum later, people on older phones stop getting updates. The target version sets how Android runs the app. The compile version sets which Android APIs the code can use. The two don't have to match ([Configure your build](https://developer.android.com/build#android_sdk_settings)).

Every fact below was checked on October 13, 2026. Library versions are the newest stable releases on that day. Android versions are written with their API level, as listed in [Android's API level table](https://developer.android.com/guide/topics/manifest/uses-sdk-element#ApiLevels).

### Android only

Three things stand in the way of an iPhone version.

**Apple's App Store rules.** [Guideline 5.2.3](https://developer.apple.com/app-store/review/guidelines/#5.2.3) of Apple's App Review Guidelines says apps should not let people save or download media from third-party sources without explicit authorization from those sources. It names YouTube as an example. Overnight downloads are Dewfall's core feature ([vision.md](../vision.md)), and Dewfall has no such authorization. So the App Store would reject it.

Distribution outside the App Store changes this only in part. Apple lets developers distribute iPhone apps through alternative app marketplaces in the EU, Japan and Brazil. In the EU, apps can also be distributed from the developer's own website ([Apple: alternative distribution for specific regions](https://developer.apple.com/documentation/marketplacekit/participating-in-alternative-distribution-for-specific-regions)). Apple notarizes apps distributed this way ([App Review Guidelines, introduction](https://developer.apple.com/app-store/review/guidelines/)). For an app meant only for these routes, Apple can check it against the Notarization Review Guidelines, a subset of the App Review Guidelines ([Apple: submit for notarization](https://developer.apple.com/help/app-store-connect/managing-alternative-distribution/submit-for-notarization)). The guidelines page marks every guideline in that subset, and 5.2.3 is not marked. So guideline 5.2.3 alone would not stop an iPhone version on an alternative marketplace in those regions. It still rules out the App Store, and the other routes exist only in those regions. This makes the first reason weaker than it looks, and the next two carry more weight.

**NewPipeExtractor is a Java library.** [ADR 0001](0001-newpipeextractor-behind-youtube-layer.md) chose NewPipeExtractor. Its code is Java, and its build uses Gradle's java-library plugin with a Java 17 toolchain ([build.gradle.kts](https://github.com/TeamNewPipe/NewPipeExtractor/blob/3313901108f0f52df68c28a04d88c2891f9c400b/build.gradle.kts#L11-L21)). Kotlin Multiplatform compiles shared code to native code for iOS, where JDK classes don't exist. Only the JVM part of a multiplatform project can use Java-only libraries ([Kotlin: the basics of project structure](https://kotlinlang.org/docs/multiplatform/multiplatform-discover-project.html)). So an iPhone version could not use NewPipeExtractor. It would need a second way to reach YouTube, and ADR 0001's one library would no longer cover the whole app.

**Playback, background work and notifications are Android code.** Playback runs in a Media3 MediaSessionService ([ADR 0002](0002-mvi-with-playback-state-outside.md)). Overnight downloads run as WorkManager jobs ([roadmap](../roadmap.md#decisions-already-made)). Google's list of Jetpack libraries that support Kotlin Multiplatform includes Room, DataStore, Lifecycle and the Compose runtime, but not WorkManager or Media3 ([Kotlin Multiplatform on Android](https://developer.android.com/kotlin/multiplatform)). The download notification, the [foreground services](https://developer.android.com/develop/background-work/services/fgs/service-types) and the [battery setting](https://developer.android.com/training/monitoring-device-state/doze-standby) in onboarding are Android system features too. An iPhone version would rewrite all of these with Apple's APIs, however much code it shared.

Kotlin Multiplatform and Compose Multiplatform would add build setup and module rules from Phase 2 on. They would serve a second platform that can't run the app's YouTube library, and whose main store forbids its core feature.

**Keeping the option open cheaply.** Some of Dewfall's code needs nothing from Android: the YouTube layer, the MVI base types and reducers, and the domain models. These could live in plain Kotlin/JVM modules with no Android imports.

- A later move to Kotlin Multiplatform would start from models and reducers that already have no Android dependencies. The YouTube layer would stay JVM only, because of NewPipeExtractor, but it would have no Android dependencies to untangle either.
- ADR 0002 keeps Android's ViewModel to run side effects. ViewModel is part of Lifecycle, which is on the same Kotlin Multiplatform list, and it has a JVM build on Google Maven ([lifecycle-viewmodel-desktop 2.11.0](https://maven.google.com/web/index.html#androidx.lifecycle:lifecycle-viewmodel-desktop:2.11.0)). So the whole MVI base may fit in a plain Kotlin/JVM module, not only the State, Intent and Effect types and the reducer.
- Tests in these modules get simpler. Android modules also run local tests on the computer's JVM, but against a copy of Android's classes with the code removed. A local test that reaches an Android method throws an exception unless the test mocks or fakes it, or uses Robolectric ([Build local unit tests](https://developer.android.com/training/testing/local-tests)). A plain Kotlin/JVM module can't see Android classes, so its tests never need any of that. This helps keep tests as fast as the roadmap asks for on every change ([roadmap](../roadmap.md#reference-testing-and-cicd)), and as ADR 0002 expects for reducers.

### The minimum version

**What the libraries allow.** These are the newest stable releases of the libraries in AGENTS.md's stack.

| Library | Newest stable release | Minimum Android version | Source |
|---|---|---|---|
| Jetpack Compose | 1.12.1, in Compose BOM 2026.09.00 | API 23 (Android 6.0) | Raised from 21 to 23 in [Compose UI 1.10.0-alpha01](https://developer.android.com/jetpack/androidx/releases/compose-ui#1.10.0-alpha01). Versions in the BOM: [BOM mapping](https://developer.android.com/develop/ui/compose/bom/bom-mapping). |
| Material 3 for Compose | 1.4.0, in the same BOM | API 21 in its own manifest. With the Compose version in the same BOM, API 23. | [Release notes](https://developer.android.com/jetpack/androidx/releases/compose-material3#1.4.0), [Google Maven](https://maven.google.com/web/index.html#androidx.compose.material3:material3-android:1.4.0) |
| Hilt | 2.60.1 | API 14 in its own manifest, so it doesn't limit Dewfall | [Release](https://github.com/google/dagger/releases/tag/dagger-2.60.1), [AndroidManifest.xml](https://github.com/google/dagger/blob/dagger-2.60.1/hilt-android/AndroidManifest.xml#L18) |
| Room | 2.8.5, and 3.0.3 in the new androidx.room3 package | API 23 | Raised from 21 to 23 in [Room 2.8.0-rc02](https://developer.android.com/jetpack/androidx/releases/room#2.8.0-rc02). Room 3: [release notes](https://developer.android.com/jetpack/androidx/releases/room3#3.0.3), [Google Maven](https://maven.google.com/web/index.html#androidx.room3:room3-runtime-android:3.0.3). |
| WorkManager | 2.12.0, released September 23, 2026 | API 24 (Android 7.0) | Raised from 23 to 24 in [WorkManager 2.12.0](https://developer.android.com/jetpack/androidx/releases/work#2.12.0) |
| Media3 | 1.11.1 | API 23 | Raised to 23 in [Media3 1.9.0](https://developer.android.com/jetpack/androidx/releases/media3#1.9.0). Newest release: [1.11.1](https://developer.android.com/jetpack/androidx/releases/media3#1.11.1). |

So the lowest minimum the current libraries allow is API 24, set by WorkManager. [WorkManager 2.11.0](https://developer.android.com/jetpack/androidx/releases/work#2.11.0) still allowed API 23, so API 23 would mean staying on an older WorkManager.

**What NewPipeExtractor needs.** On its dev branch, NewPipeExtractor's README says Android projects with a minSdk below 33 need core library desugaring with the desugar_jdk_libs_nio artifact ([README](https://github.com/TeamNewPipe/NewPipeExtractor/blob/3313901108f0f52df68c28a04d88c2891f9c400b/README.md?plain=1#L24)). Desugaring itself works with any minimum version ([Java 8+ API desugaring](https://developer.android.com/studio/write/java8-support#library-desugaring)).

Its version catalog keeps Rhino, the JavaScript engine it uses, at 1.8.1. The comment says Rhino 1.9.0 needs a minSdk of 26 or higher ([libs.versions.toml](https://github.com/TeamNewPipe/NewPipeExtractor/blob/3313901108f0f52df68c28a04d88c2891f9c400b/gradle/libs.versions.toml#L15)). An upgrade to 1.9.0 was reverted in [pull request 1423](https://github.com/TeamNewPipe/NewPipeExtractor/pull/1423), because builds with a minSdk below 26 failed. A Dewfall minimum below 26 depends on NewPipeExtractor keeping that pin. The NewPipeExtractor links in this ADR point to the dev branch at commit 3313901, from October 11, 2026.

**What Dewfall needs from Android.** This list comes from the roadmap, the [design brief](../design/brief.md) and the [Compose theme](../design/claude-design/design-system/compose-theme.md).

| Need | Android version it needs | Below that version | Source |
|---|---|---|---|
| Dynamic color | Android 12 (API 31) | Dewfall's own light and dark palette, which the design system already has | [Material 3 in Compose](https://developer.android.com/develop/ui/compose/designsystems/material3#dynamic_color_schemes) |
| Dark theme that follows the system setting | Android 10 (API 29) | There is no system setting to follow. The choice is between staying light on those versions and adding a theme setting in the app, which the brief doesn't have. | [Dark theme](https://developer.android.com/develop/ui/views/theming/darktheme) |
| Notification permission | Android 13 (API 33) | The runtime permission doesn't exist, so the app has nothing to ask for | [Notification runtime permission](https://developer.android.com/develop/ui/views/notifications/notification-permission) |
| Notification channels | Android 8.0 (API 26) | Each notification sets its own priority instead | [Notification channels](https://developer.android.com/develop/ui/views/notifications/channels) |
| Manrope weights from one variable font file | Android 8.0 (API 26) | Static font files, one per weight | [Fonts in Compose](https://developer.android.com/develop/ui/compose/text/fonts#variable-fonts) |
| No desugaring for NewPipeExtractor | API 33 | Turn on core library desugaring | [NewPipeExtractor README](https://github.com/TeamNewPipe/NewPipeExtractor/blob/3313901108f0f52df68c28a04d88c2891f9c400b/README.md?plain=1#L24) |
| Overnight downloads with WorkManager, including its foreground service | API 24 | Nothing. WorkManager 2.12.0 covers every candidate and runs the foreground service itself ([long-running workers](https://developer.android.com/develop/background-work/background-tasks/persistent/how-to/long-running)). | [WorkManager 2.12.0](https://developer.android.com/jetpack/androidx/releases/work#2.12.0) |
| Media3 playback in a MediaSessionService | API 23 | Nothing. Media3 1.11.1 covers every candidate. | [Media3 1.9.0](https://developer.android.com/jetpack/androidx/releases/media3#1.9.0) |
| Battery setup in onboarding | Android 6.0 (API 23). Doze and its exemption list apply from that version. | Nothing | [Doze and App Standby](https://developer.android.com/training/monitoring-device-state/doze-standby) |
| Opening the Takeout file and writing the export file | Android 4.4 (API 19), with no permission needed | Nothing | [Storage Access Framework](https://developer.android.com/training/data-storage/shared/documents-files) |
| Optional: downloads the user starts by hand, as user-initiated data transfer jobs | Android 14 (API 34) | Google suggests WorkManager's foreground service as the fallback | [User-initiated data transfer](https://developer.android.com/develop/background-work/background-tasks/uidt#uidt-back-compat) |

**The candidates.** The two share columns are explained below the table.

| Candidate | Google's file: devices on this version or newer ([distributions.json](https://dl.google.com/android/studio/metadata/distributions.json), last modified December 10, 2025) | apilevels.com: web traffic from this version or newer ([apilevels.com](https://apilevels.com/), April 2026 data, updated May 28, 2026) | Needs a fallback or extra setup |
|---|---|---|---|
| API 24, Android 7.0. The lowest the libraries allow. | 99.1% | 96.6% | Dynamic color, dark theme (stay light or add a theme setting), a version check around the notification permission, notification priorities, static font files, and desugaring. Depends on NewPipeExtractor keeping Rhino at 1.8.1 ([libs.versions.toml](https://github.com/TeamNewPipe/NewPipeExtractor/blob/3313901108f0f52df68c28a04d88c2891f9c400b/gradle/libs.versions.toml#L15)). |
| API 26, Android 8.0. Rhino 1.9.0's minimum ([libs.versions.toml](https://github.com/TeamNewPipe/NewPipeExtractor/blob/3313901108f0f52df68c28a04d88c2891f9c400b/gradle/libs.versions.toml#L15)). | 98.3% | 96.1% | Dynamic color, dark theme (stay light or add a theme setting), a version check around the notification permission, and desugaring |
| API 29, Android 10. System dark theme. | 90.7% | 91.1% | Dynamic color, a version check around the notification permission, and desugaring |
| API 31, Android 12. Dynamic color. | 69.2% | 78.8% | A version check around the notification permission, and desugaring |
| API 33, Android 13. No desugaring. | 57.8% | 68.9% | None, apart from the optional row noted below |

At every candidate, user-initiated data transfer jobs would still need the WorkManager fallback below API 34.

**Where the shares come from.** No current primary source gives the share of devices on each Android version. Google's [distribution dashboard](https://developer.android.com/about/dashboards) no longer shows Android versions. It points to Reach and devices in the Google Play Console instead, and Dewfall is not on Google Play.

The first column comes from an API level distribution file that Google publishes with its Android Studio metadata ([distributions.json](https://dl.google.com/android/studio/metadata/distributions.json)). Google's server says the file was last modified on December 10, 2025. The file has no collection date, says nothing about how the data was gathered, and has no entry for API 32 or Android 17. Each share is the sum of the file's entries for that API level and newer. Treat them as a rough guide that is ten months old.

The second column comes from [apilevels.com](https://apilevels.com/), which publishes cumulative usage computed from StatCounter GlobalStats data with [a script](https://github.com/ebelinski/apilevels/blob/main/meta/android-usage-generator.swift). Its footnote says the figures were last updated on May 28, 2026, using April 2026 data from [StatCounter's worldwide mobile and tablet figures](https://gs.statcounter.com/android-version-market-share/mobile-tablet/worldwide), and may have changed since. StatCounter counts page views on websites that use its tracking code ([StatCounter FAQ](https://gs.statcounter.com/faq#methodology)). So this column measures web traffic from each Android version, not devices. apilevels.com is not a primary source either.

For API 29, the chosen minimum, the two sources differ by 0.4 percentage points: 90.7% in Google's file and 91.1% on apilevels.com.

[vision.md](../vision.md#who-it-is-for) names people with slow or expensive mobile data as an audience. Each source gives one figure for each version, with no breakdown by phone price, so neither says anything about the phones this group uses. If they tend to keep phones longer, or to buy phones that stop getting Android updates sooner, a higher minimum would leave out more of them than these shares suggest. There is no data here to say how many.

### The target and compile versions

**The newest Android versions.** Android 17, API level 37, is the newest stable major version. Google released it on June 16, 2026 ([Android Developers Blog](https://android-developers.googleblog.com/2026/06/Android-17.html)). Google's page for Android 17 QPR1, API level 37.1, still says beta. But that page was last updated in April 2026, so it's unclear whether QPR1 has shipped ([Android 17 QPR1](https://developer.android.com/about/versions/17/qpr1)). Android 17 QPR2 is in beta, with API level 37.2 ([Android 17 QPR2](https://developer.android.com/about/versions/17/qpr2)). So the newest stable API level is 37, or 37.1 if QPR1 has shipped. The pages checked show no beta of a next major version.

**The compile version.** Compose 1.12 needs a compileSdk of 37 ([Compose UI 1.12.0-alpha01](https://developer.android.com/jetpack/androidx/releases/compose-ui#1.12.0-alpha01)). So with the newest stable libraries, the compile version is at least 37.

**Rules on the target version.**

- Android: since Android 15, apps that target an API level below 24 can't be installed ([Android 15 behavior changes](https://developer.android.com/about/versions/15/behavior-changes-all#minimum-target-api-level)). The behavior change pages for [Android 16](https://developer.android.com/about/versions/16/behavior-changes-all) and [Android 17](https://developer.android.com/about/versions/17/behavior-changes-all) don't raise it.
- GitHub releases: no store sits between Dewfall's APK and the user, so only Android's rule applies.
- F-Droid: its [Inclusion Policy](https://f-droid.org/docs/Inclusion_Policy/) has no rule on the target version. Neither do its [Inclusion How-To](https://f-droid.org/docs/Inclusion_How-To/) or its [FAQ for app developers](https://f-droid.org/docs/FAQ_-_App_Developers/).
- IzzyOnDroid: its [App Inclusion Policy](https://izzyondroid.org/docs/general/AppInclusionPolicy/) has no rule on the target version either.

So the only fixed floor is Android's own, API 24. Any target above that is Dewfall's choice.

**What targeting the newest version changes.** A new app has no older target to keep. Targeting API 37 turns on every change that earlier Android versions tied to the target. These are the ones that touch Dewfall's features.

| Applies to apps targeting | Change | What it means for Dewfall |
|---|---|---|
| Android 12 (API 31) | Apps can't start a foreground service from the background, apart from listed exemptions. One exemption is that the user has turned off battery optimization for the app ([source](https://developer.android.com/develop/background-work/services/fgs/restrictions-bg-start#background-start-restriction-exemptions)). | The battery setup in onboarding matters for overnight downloads beyond Doze. |
| Android 13 (API 33) | Notifications need the POST_NOTIFICATIONS runtime permission. Media session notifications are exempt. If the user refuses, foreground service notices show only in the Task Manager, not in the notification drawer ([source](https://developer.android.com/develop/ui/views/notifications/notification-permission#exemptions)). | The overnight download notification and the "paused at the limit" notification need the permission. The player's notification doesn't. |
| Android 14 (API 34) | Every foreground service needs a declared type ([source](https://developer.android.com/develop/background-work/services/fgs/service-types)), and so does every long-running WorkManager worker ([source](https://developer.android.com/develop/background-work/background-tasks/persistent/how-to/long-running#foreground-service-type)). | Downloads use the dataSync type, and the player uses mediaPlayback. |
| Android 15 (API 35) | dataSync foreground services may run for 6 hours in total in any 24 hours. The timer resets when the user brings the app to the foreground ([source](https://developer.android.com/about/versions/15/behavior-changes-15#datasync-timeout)). | A long overnight run that keeps a dataSync foreground service going can hit this limit. |
| Android 15 (API 35) | BOOT_COMPLETED receivers can't start dataSync or mediaPlayback foreground services ([source](https://developer.android.com/about/versions/15/behavior-changes-15#fgs-boot-completed)). | Downloads and playback can't start from a boot receiver. |
| Android 15 (API 35) | Apps are edge-to-edge by default ([source](https://developer.android.com/about/versions/15/behavior-changes-15#edge-to-edge)). | Every screen handles the system bar and cutout insets. |
| Android 15 (API 35) | Only the top app, or an app running a foreground service, can request audio focus ([source](https://developer.android.com/about/versions/15/behavior-changes-15#audio-focus)). | The player requests audio focus only while it is visible or its service runs. |
| Android 16 (API 36) | The edge-to-edge opt-out is disabled ([source](https://developer.android.com/about/versions/16/behavior-changes-16#edge-to-edge)). | Same as above, with no opt-out. |
| Android 16 (API 36) | Predictive back animations are on by default, and onBackPressed is no longer called ([source](https://developer.android.com/about/versions/16/behavior-changes-16#predictive-back)). | Back handling can't rely on onBackPressed. |
| Android 16 (API 36) | On displays at least 600dp wide, Android ignores orientation, resizability and aspect ratio limits ([source](https://developer.android.com/about/versions/16/behavior-changes-16#ignore-orientation)). | The brief says portrait only, except the fullscreen player. On tablets and large foldables, every screen must also work in landscape and in resized windows. |
| Android 17 (API 37) | The temporary opt-out from that change is gone ([source](https://developer.android.com/about/versions/17/behavior-changes-17#large-screen-ignore-constraints)). | Same as above, with no way to delay it. |
| Android 17 (API 37) | An app that uses audio while in the background needs a foreground service with while-in-use capabilities ([source](https://developer.android.com/about/versions/17/behavior-changes-17#bg-audio)). | Background audio, a Phase 4 task, depends on the player's foreground service. |
| Android 17 (API 37) | Certificate transparency is on by default ([source](https://developer.android.com/about/versions/17/behavior-changes-17#ct-default)). Encrypted Client Hello is used where the network library and the server support it ([source](https://developer.android.com/about/versions/17/behavior-changes-17#ech-by-default)). | Network calls to YouTube need a check on Android 17. |

Some changes apply to every app on newer Android versions, whatever its target:

- On Android 15, a network request started outside a valid process lifecycle gets an exception. Google recommends WorkManager or a foreground service for requests that must keep going ([source](https://developer.android.com/about/versions/15/behavior-changes-all#background-network-access)).
- On Android 16, WorkManager jobs count against a runtime quota. That includes jobs that keep running after the app leaves the screen, and jobs that run alongside a foreground service. For transfers the user starts, Google points to user-initiated data transfer jobs ([source](https://developer.android.com/about/versions/16/behavior-changes-all#job-quota-opt)).

## Decision

Dewfall is an Android-only app. It doesn't use Kotlin Multiplatform or Compose Multiplatform.

Code that needs nothing from Android lives in plain Kotlin/JVM modules with no Android imports: the YouTube layer, the domain models, the MVI base types and the reducers. The task that creates the Android project (#21) names these modules.

The minimum Android version is API 29 (Android 10). Core library desugaring is on, as NewPipeExtractor requires below API 33.

The target version is API 37 (Android 17). The compile version is API 37, or higher when a library requires it. Raising the target later is its own pull request, after a review of that version's behavior changes.

## Consequences

### Android only

- Dewfall has one platform to build, test and support.
- People with iPhones can't use Dewfall.
- The app uses Android-only libraries such as Media3 and WorkManager directly, without wrapping them for a second platform.
- The build stays a standard Android Gradle build, with no Kotlin Multiplatform setup.
- An iPhone version would need a new ADR. It would also need its own way to reach YouTube, and its own playback, background work and notifications. The App Store stays closed to it under guideline 5.2.3 unless YouTube authorizes downloads.

### Plain Kotlin/JVM modules

- Code in these modules can't import Android classes, so the build enforces the boundary, not code review.
- Their tests are plain JVM tests, with no Android methods to mock or fake.
- If Dewfall ever adds Kotlin Multiplatform, the MVI types, reducers and models are ready to share. The YouTube layer would stay JVM only, because NewPipeExtractor is a Java library.
- These modules can't hold user-facing text, because string resources are part of Android. They return types that the screens turn into text.
- They can't use Android's Context, so anything they need from the platform, such as file locations, is passed in.
- They compile and run their tests against the full JDK. Android has only part of the JDK, and desugaring adds only some of the missing APIs ([Java 8+ API desugaring](https://developer.android.com/studio/write/java8-support#library-desugaring)). Code that uses a missing API passes its tests, then fails on a phone. Phase 2 needs a check, such as Android Lint, that covers these modules. This matters most for the YouTube layer, because of NewPipeExtractor's desugaring note.
- #21 names the modules, and #21 and #32 update the planned modules in AGENTS.md, the roadmap's Phase 2 task and docs/architecture.md.

### The versions

- Phones below Android 10 can't install Dewfall. That leaves out about 9 percent by either source in the candidates table.
- Every supported version has a system dark theme, so the app needs no theme setting.
- The API 29 row of the candidates table lists the fallbacks and version checks that remain. The optional row below that table applies too.
- NewPipeExtractor's Rhino pin doesn't affect Dewfall, because API 29 is above Rhino 1.9.0's minimum of 26 ([libs.versions.toml](https://github.com/TeamNewPipe/NewPipeExtractor/blob/3313901108f0f52df68c28a04d88c2891f9c400b/gradle/libs.versions.toml#L15)).
- Raising the minimum later stops updates for phones below the new minimum.
- Every change in the "What targeting the newest version changes" table applies from the start. On tablets and large foldables, every screen must work in landscape and in resized windows. The brief says portrait, except the fullscreen player.
- Raising the target to a newer Android version takes a review of that version's behavior changes and its own pull request.
- Library updates can raise the compile version. Compose 1.12 already needs 37 ([Compose UI 1.12.0-alpha01](https://developer.android.com/jetpack/androidx/releases/compose-ui#1.12.0-alpha01)).
