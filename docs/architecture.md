# Architecture

This is the design document for Dewfall. It explains how the app is built: its modules, how data moves through them, and how the main features work underneath. The reasons behind each decision are in the ADRs in [adr/](adr/). Update this file in the same pull request as any change to the structure of the app.

## Modules

Dewfall is one Gradle build with an app module, core modules, and feature modules as features arrive. Today the feature modules hold the Search YouTube screen, the app's first screen, the video screen, which opens from it, and the YouTube layer's debug screen, which only debug builds contain. Some modules are plain Kotlin/JVM and the rest are Android modules. [ADR 0006](adr/0006-platform-and-android-versions.md) explains why.

- A plain Kotlin/JVM module can't see Android classes, so the build itself keeps Android code out of it. Its tests run on the computer's JVM with nothing from Android to fake. It can't hold user-facing text, because string resources are part of Android, so it returns types that the screens turn into text.
- An Android module is the app or an Android library. It holds everything that needs Android, such as the database, screens, ViewModels, resources and background jobs.

### The module graph

Solid arrows are dependencies in the build today. An arrow labeled api is an api dependency, so a module that depends on the module where the arrow starts sees the module where it ends too. For example, `:core:data` sees `:core:logging` through `:core:youtube`. The arrow labeled debug only is a debugImplementation dependency, so release builds leave out the debug screen's modules. See [The YouTube layer's debug screen](#the-youtube-layers-debug-screen). Dashed arrows show modules that come later: the inbox's two modules, which Phase 5 adds, and `:core:ui`, which comes when features share UI that needs the domain models or string resources, such as the video row that the inbox shares with search.

```mermaid
graph TD
    app[":app<br/>Android app"]
    data[":core:data<br/>Android library"]
    database[":core:database<br/>Android library"]
    designsystem[":core:designsystem<br/>Android library"]
    youtube[":core:youtube<br/>Kotlin/JVM"]
    logging[":core:logging<br/>Kotlin/JVM"]
    model[":core:model<br/>Kotlin/JVM"]
    mvi[":core:mvi<br/>Kotlin/JVM"]
    playback[":core:playback<br/>Android library"]
    searchUi[":feature:youtube-search-ui<br/>Android library"]
    searchLogic[":feature:youtube-search-logic<br/>Kotlin/JVM"]
    playerUi[":feature:player-ui<br/>Android library"]
    playerLogic[":feature:player-logic<br/>Kotlin/JVM"]
    debugUi[":feature:youtube-debug-ui<br/>Android library, debug builds only"]
    debugLogic[":feature:youtube-debug-logic<br/>Kotlin/JVM, debug builds only"]
    coreUi[":core:ui<br/>Android library, later"]
    inboxUi[":feature:inbox-ui<br/>Android library, later"]
    inboxLogic[":feature:inbox-logic<br/>Kotlin/JVM, later"]

    app --> data
    app --> designsystem
    app --> logging
    data --> database
    data --> youtube
    data -->|api| model
    database --> model
    youtube -->|api| model
    youtube -->|api| logging
    playback --> data
    playback -->|api| model
    playback --> logging
    app --> searchUi
    searchUi --> searchLogic
    searchUi --> data
    searchUi --> designsystem
    searchUi --> model
    searchUi --> mvi
    searchLogic -->|api| mvi
    searchLogic -->|api| model
    app --> playerUi
    playerUi --> playerLogic
    playerUi --> data
    playerUi --> designsystem
    playerUi --> model
    playerUi --> mvi
    playerUi --> playback
    playerLogic -->|api| mvi
    playerLogic -->|api| model
    app -->|debug only| debugUi
    debugUi --> debugLogic
    debugUi --> designsystem
    debugUi --> youtube
    debugUi --> model
    debugUi --> mvi
    debugLogic -->|api| mvi
    debugLogic -->|api| model

    app -.-> inboxUi
    inboxUi -.-> inboxLogic
    inboxUi -.-> data
    inboxUi -.-> designsystem
    inboxUi -.-> mvi
    inboxUi -.-> model
    inboxLogic -.-> mvi
    inboxLogic -.-> model
    coreUi -.-> designsystem
    coreUi -.-> model
```

### Core modules

Android modules set a namespace. Plain Kotlin/JVM modules have no namespace, so for them the table gives the package their code uses.

| Module | Folder | Type | Namespace or package | What it holds | Depends on |
|---|---|---|---|---|---|
| `:app` | app/ | Android app | io.github.dewfallapp | The entry point, which ties the other modules together. It holds the Hilt Application class, the app's only activity, and the navigation between screens, which opens on Search YouTube. See [Navigation](#navigation). It also holds the logcat writer and the Hilt module that provides the logger. See [Logging](#logging). | `:core:data`, `:core:designsystem`, `:core:logging`, and each feature's `-ui` module, today `:feature:youtube-search-ui` and `:feature:player-ui`. In debug builds only, `:feature:youtube-debug-ui`. Navigation 3 and kotlinx-serialization-core, for the back stack. |
| `:core:model` | core/model/ | Kotlin/JVM | io.github.dewfallapp.core.model | Domain models, such as videos, channels and subtitle lines. Today it holds the YouTube layer's models, its IDs, YouTubeResult and YouTubeError, TimeAgo, which counts how long ago a PublishedTime was, the way YouTube writes it, for screens to turn into text, and the player's FormatChoice, PlaybackFailure and MediaRequestRefused. See [The YouTube layer](#the-youtube-layer) and [The player](#the-player). | Nothing |
| `:core:logging` | core/logging/ | Kotlin/JVM | io.github.dewfallapp.core.logging | The Logger that every module logs through, its levels and fields, and the error summary that leaves out messages. See [Logging](#logging). | Nothing |
| `:core:mvi` | core/mvi/ | Kotlin/JVM | io.github.dewfallapp.core.mvi | The MVI base: MviState, MviIntent, MviEffect, the Reducer, and MviViewModel, the base class of every screen's ViewModel. See [The MVI contract](#the-mvi-contract). | Nothing. It uses Lifecycle's ViewModel and kotlinx.coroutines. |
| `:core:youtube` | core/youtube/ | Kotlin/JVM | io.github.dewfallapp.core.youtube | The YouTube layer: Dewfall's interfaces for search, video details, a channel's latest uploads, stream links and subtitles, and their NewPipeExtractor implementation. NewPipeYouTube, one of its two public classes, implements all five. The other, MediaRequests, makes the player's OkHttp clients for googlevideo.com. It also holds the HTTP code that NewPipeExtractor sends its requests through, and the code that runs each call. It is the only module that may use NewPipeExtractor. See [ADR 0001](adr/0001-newpipeextractor-behind-youtube-layer.md) and [The YouTube layer](#the-youtube-layer). | `:core:model`, `:core:logging`, kotlinx.coroutines and OkHttp, all four as api dependencies, because the interfaces return the models, NewPipeYouTube's constructor takes a Logger and a CoroutineDispatcher, and MediaRequests returns OkHttp's Call.Factory |
| `:core:database` | core/database/ | Android library | io.github.dewfallapp.core.database | The Room database: entities, DAOs, migrations and the exported schemas | `:core:model` |
| `:core:data` | core/data/ | Android library | io.github.dewfallapp.core.data | Repositories that combine the database and the YouTube layer. Today it holds YouTubeSearchRepository, which the Search YouTube screen uses, VideoDetailsRepository, which the video screen uses, StreamLinksRepository, which the player uses, and YouTubeModule, the Hilt module that provides the YouTube layer, and the player's two OkHttp Call.Factory providers, marked `@ProgressiveMediaCalls` and `@LiveMediaCalls`. See [How Hilt provides the layer](#how-hilt-provides-the-layer) and [Reading from YouTube](#reading-from-youtube). | `:core:model`, `:core:database`, `:core:youtube` |
| `:core:playback` | core/playback/ | Android library | io.github.dewfallapp.core.playback | The player: PlaybackService, the media session service with one ExoPlayer, and PlaybackConnection, which hands screens its MediaController, with the format choice's Android parts, the media sources and the error mapping. See [The player](#the-player). | `:core:model` as an api dependency, `:core:data`, `:core:logging`, Media3 with media3-session as an api dependency, because the connection exposes Media3's Player, and OkHttp |
| `:core:designsystem` | core/designsystem/ | Android library | io.github.dewfallapp.core.designsystem | The Compose theme built from the design tokens, with the Manrope font. See [The theme](#the-theme). It also holds the shared icons, today the search, close, error, keyboard_arrow_down, play_arrow and filled pause glyphs from Material Symbols Rounded, under the Apache License 2.0, ReadableWidth, the widest a screen's column gets, and the first shared component, Banner. | Nothing. It exposes Material 3 as an api dependency. |

`:core:mvi` holds the MVI base, `:core:designsystem` holds the theme, and `:core:logging` holds the logger. `:core:youtube` holds the YouTube layer's interfaces and the code that runs NewPipeExtractor, and `:core:model` the models they return. `:core:database` holds no code yet, and `:core:data` holds the search, video details and stream links repositories and the Hilt module that provides the YouTube layer. `:core:playback` holds the player. A module that logs depends on `:core:logging`. Today `:app`, `:core:youtube` and `:core:playback` do, and each other module adds the dependency when it first logs. `-logic` modules never do, because reducers don't log. `:core:database` has Room set up, but no database until Phase 5 adds the first tables (#48). NewPipeYouTube implements all five of the YouTube layer's interfaces. Compose is set up in `:app`, `:core:designsystem` and every feature's `-ui` module. Hilt is set up in `:app`, `:core:data`, `:core:playback` and every feature's `-ui` module.

`:lint-rules` is a build tool, not part of the app. It holds Dewfall's own lint checks, which lint runs on every module of the app. No module's code depends on it, and nothing from it ships. See [Dewfall's lint checks](#dewfalls-lint-checks).

`:core:designsystem` holds the theme and the components that know nothing about Dewfall's data. Its components take plain values, such as text, images and click handlers. Shared UI that needs the domain models or string resources, such as a video row that several features use, or the text of a duration, goes in a `:core:ui` Android library instead. Counting that needs no text, such as TimeAgo, stays in `:core:model`, a plain Kotlin/JVM module, as ADR 0006 asks. `:core:ui` gets created when two features first need such a piece. It may depend on `:core:designsystem` and `:core:model`, and feature `-ui` modules may depend on it.

### Feature modules

Each feature has two modules, because its reducer has to be plain Kotlin/JVM and its screens need Android. For the inbox, they look like this.

| Module | Folder | Type | Namespace or package | What it holds | Depends on |
|---|---|---|---|---|---|
| `:feature:inbox-logic` | feature/inbox-logic/ | Kotlin/JVM | io.github.dewfallapp.feature.inbox.logic | InboxState, InboxIntent, InboxEffect, the reducer, and the reducer's tests | `:core:mvi`, `:core:model` |
| `:feature:inbox-ui` | feature/inbox-ui/ | Android library | io.github.dewfallapp.feature.inbox.ui | The Compose screens, InboxViewModel, string resources and screenshot tests | `:feature:inbox-logic`, `:core:data`, `:core:designsystem`, `:core:mvi`, `:core:model` |

- Feature modules depend on core modules, never on another feature's modules. A feature's `-ui` module also depends on its own `-logic` module.
- The ViewModel goes in the `-ui` module and extends MviViewModel from `:core:mvi`. It runs side effects through `:core:data` and uses Hilt, and both are Android. A plain Kotlin/JVM module can't depend on an Android module.
- `:app` depends on every feature's `-ui` module and handles navigation between features. A feature whose screen sits on `:app`'s back stack exposes one public composable from its `-ui` module, which takes plain values and actions, such as `onOpenVideo`, and never sees Navigation 3. See [Navigation](#navigation). The YouTube layer's debug screen has its own activity instead.

The Search YouTube screen is the first feature. Its pair:

| Module | Folder | Type | Namespace or package | What it holds | Depends on |
|---|---|---|---|---|---|
| `:feature:youtube-search-logic` | feature/youtube-search-logic/ | Kotlin/JVM | io.github.dewfallapp.feature.youtubesearch.logic | YouTubeSearchState, YouTubeSearchIntent, the reducer, the length label, the line-break functions, and their tests | `:core:mvi`, `:core:model`, both as api dependencies |
| `:feature:youtube-search-ui` | feature/youtube-search-ui/ | Android library | io.github.dewfallapp.feature.youtubesearch.ui | The Compose screen and its previews, YouTubeSearchViewModel and its tests, and string resources | `:feature:youtube-search-logic`, `:core:data`, `:core:designsystem`, `:core:model`, `:core:mvi` |

The video screen, which opens from a search result, is the second. Its pair:

| Module | Folder | Type | Namespace or package | What it holds | Depends on |
|---|---|---|---|---|---|
| `:feature:player-logic` | feature/player-logic/ | Kotlin/JVM | io.github.dewfallapp.feature.player.logic | PlayerState, PlayerIntent, the reducer, the channel's initial, which errors offer "Try again", the start decision, the shared failure rule, the playback times' text, and their tests | `:core:mvi`, `:core:model`, both as api dependencies |
| `:feature:player-ui` | feature/player-ui/ | Android library | io.github.dewfallapp.feature.player.ui | PlayerRoute, the Compose screen and its previews, the video area and its playback status, PlayerViewModel and its tests, and string resources | `:feature:player-logic`, `:core:data`, `:core:designsystem`, `:core:model`, `:core:mvi`, `:core:playback`, media3-ui-compose and media3-common-ktx |

The modules are named player, because #42 to #44 add playback to the same screen. The service that plays media doesn't depend on any screen, so it lives in a core module of its own, `:core:playback`. See [The player](#the-player).

The YouTube layer's debug screen has a pair of its own, which `:app` adds to debug builds only. [The YouTube layer's debug screen](#the-youtube-layers-debug-screen) explains it.

| Module | Folder | Type | Namespace or package | What it holds | Depends on |
|---|---|---|---|---|---|
| `:feature:youtube-debug-logic` | feature/youtube-debug-logic/ | Kotlin/JVM | io.github.dewfallapp.feature.youtubedebug.logic | YouTubeDebugState, YouTubeDebugIntent, the reducer, the summaries of each answer, and their tests | `:core:mvi`, `:core:model`, both as api dependencies |
| `:feature:youtube-debug-ui` | feature/youtube-debug-ui/ | Android library | io.github.dewfallapp.feature.youtubedebug.ui | YouTubeDebugActivity, the Compose screen and its previews, YouTubeDebugViewModel and its tests, and string resources | `:feature:youtube-debug-logic`, `:core:designsystem`, `:core:model`, `:core:mvi`, `:core:youtube` |

`:feature:youtube-debug-ui` is the only feature module that depends on `:core:youtube`, because its job is to call the layer's functions themselves. Every real feature reaches YouTube only through repositories in `:core:data`.

### Module names

Every module's last name segment must be unique in the build. Gradle can mix up two projects that have both the same group and the same name, such as `:inbox:logic` and `:search:logic` when both are in group dewfall ([gradle/gradle#847](https://github.com/gradle/gradle/issues/847)). By default, Gradle bases each project's group on its parent's path, so `:core:data` is in group dewfall.core and the clash can't happen yet. It happens as soon as a build setting gives every project the same group. Unique names prevent it, whatever the groups are. So feature modules are named `:feature:inbox-logic` and `:feature:inbox-ui`, not `:feature:inbox:logic` and `:feature:inbox:ui`.

### Build settings

Every module of the app applies one of three convention plugins from build-logic/. They hold the settings that every module of that type shares, and each one adds Dewfall's lint checks to the module's lint. `:lint-rules` applies a fourth, dewfall.lint.rules.

| Plugin | For | What it sets |
|---|---|---|
| dewfall.android.application | `:app` | compileSdk 37, minSdk 29 and targetSdk 37, as ADR 0006 decides, the JDK 17 toolchain, the shared Kotlin compiler, lint and detekt settings, and JUnit 4 unit tests |
| dewfall.android.library | Android libraries | compileSdk 37 and minSdk 29, targetSdk 37 for lint and tests, the JDK 17 toolchain, the shared Kotlin compiler, lint and detekt settings, and JUnit 4 unit tests |
| dewfall.jvm.library | Plain Kotlin/JVM modules | The Kotlin JVM plugin, the JDK 17 toolchain, the shared Kotlin compiler, lint and detekt settings, the Android API check, and JUnit 6 unit tests |
| dewfall.lint.rules | `:lint-rules` | The Kotlin JVM and lint plugins, the JDK 17 toolchain, the shared Kotlin compiler with Kotlin API version 2.2, lint and detekt settings with lint's checks for lint code turned on, JUnit 6 unit tests, and lint-api and lint-tests at AGP's version plus 23. See [Dewfall's lint checks](#dewfalls-lint-checks). |

Another plugin, dewfall.detekt, holds the detekt settings and adds the compose-rules plugin to detekt. The four plugins above apply it, and so does the root project, to check the code in build-logic.

Android modules that use Compose, Hilt or Room also apply the matching plugins below, after dewfall.android.application or dewfall.android.library. Today `:app`, `:core:designsystem` and every feature's `-ui` module apply dewfall.android.compose, `:app`, `:core:data` and every feature's `-ui` module apply dewfall.android.hilt, and only `:core:database` applies dewfall.android.room. `:app` also applies Kotlin's serialization plugin, for its navigation keys. See [Navigation](#navigation).

`:app` also turns on BuildConfig, which AGP 8.0 and later generate only for modules that ask ([AGP 8.0.0](https://developer.android.com/build/releases/agp-8-0-0-release-notes)). The Hilt module that provides the logger reads BuildConfig.DEBUG to pick the lowest log level. See [Logging](#logging).

| Plugin | What it sets |
|---|---|
| dewfall.android.compose | The Compose compiler plugin, Compose turned on in the Android build, the Compose BOM, and the preview annotations, with the preview tooling in debug builds only. The module adds the Compose libraries it uses, such as Material 3. |
| dewfall.android.hilt | KSP, the Hilt Gradle plugin, Hilt and Hilt's compiler, with KSP warnings failing the build |
| dewfall.android.room | KSP, the Room Gradle plugin, Room and Room's compiler, with KSP warnings failing the build, and the module's schemas/ folder as the place where Room writes the database schemas |

The Compose compiler is part of Kotlin, so its plugin always has the Kotlin version, and it runs inside the Kotlin compiler that AGP's built-in Kotlin provides ([Compose compiler](https://developer.android.com/develop/ui/compose/compiler)). Compose 1.12 needs compileSdk 37 and AGP 9 ([compatibility](https://developer.android.com/develop/ui/compose/compiler#agp-compatibility)).

Hilt's compiler runs through KSP. The kotlin-kapt plugin doesn't work with built-in Kotlin. Google recommends KSP instead, and offers the com.android.legacy-kapt plugin only as a fallback for projects that can't move to KSP yet ([Migrate to built-in Kotlin](https://developer.android.com/build/migrate-to-built-in-kotlin#migration-steps-migrate-kotlin-kapt-plugin)). Hilt supports KSP, so the build uses neither. The Hilt Gradle plugin then builds the app's Hilt components in a javac task of its own, such as hiltJavaCompileDebug. In an Android module, the `ksp` configuration covers the main source set only. Tests that use Hilt add its compiler to `kspTest` or `kspAndroidTest` ([KSP configurations](https://github.com/google/ksp/blob/2.3.12/README.md#single-platform-jvm--android)). The Hilt Gradle plugin needs Hilt 2.59 or later on AGP 9 ([Dagger 2.59](https://github.com/google/dagger/releases/tag/dagger-2.59)), and Hilt reads the classes that Kotlin 2.4 compiles from 2.60 on ([google/dagger#5180](https://github.com/google/dagger/issues/5180)). So a new Kotlin minor version may need a Hilt update too.

Room's compiler runs through KSP too. The build uses Room 3, whose classes are in the androidx.room3 package. Google marks Room 2 as deprecated ([Room 2.x guide](https://developer.android.com/training/data-storage/room/v2)), and its Room guide uses Room 3 ([Room](https://developer.android.com/training/data-storage/room)). Room 3 generates only Kotlin, runs only through KSP, and is built around coroutines ([Room 3.0.0](https://developer.android.com/jetpack/androidx/releases/room3#3.0.0)). WorkManager 2.12.0 uses Room 2 for its own database, so the app will ship both once Phase 7 adds WorkManager. The two don't clash, because Room 3 has a package of its own for exactly this case ([Migrate from Room 2.x to Room 3.0](https://developer.android.com/training/data-storage/room/migration-2-to-3)).

The Room Gradle plugin tells Room's compiler where the schemas go. Room writes the schema of each database version to core/database/schemas/, as `<database class>/<version>.json`, and the files are committed. Debug and release builds share the folder, and the plugin checks that both write the same schema. The plugin also adds the folder to the androidTest assets, where migration tests read it ([Room Gradle plugin](https://developer.android.com/training/data-storage/room/migrating-db-versions#export-schemas)). Room writes a file only when the schema changed. If the schema changes but the version number stays the same, Room rewrites that version's file and the build still passes. The pull request workflow catches this, as [testing.md](testing.md#database-migration-tests) describes.

Room 3 adds a service, MultiInstanceInvalidationService, to the app's manifest. It isn't exported, and it runs only for a database built with enableMultiInstanceInvalidation(), which keeps database instances in several processes in sync. The app runs in a single process, so it doesn't need that.

**The same versions in debug and release.** Checks on an emulator run a debug build, so debug and release builds must resolve the same version of every library they share. A debugImplementation dependency can break that, because Gradle resolves each build type's dependencies on its own. Until #40, only the debug screen brought `:core:mvi`, whose Lifecycle 2.11.0 is newer than 2.9.4, the highest version that the app's other AndroidX libraries ask for. Without a constraint, debug builds would have run Lifecycle 2.11.0 and SavedState 1.4.0 while release builds kept 2.9.4 and 1.3.2. A constraint in `:app` on lifecycle-viewmodel gave release the same versions, because Lifecycle publishes rules that keep all its artifacts on one version, and Lifecycle 2.11.0 needs SavedState 1.4.0 ([dependency constraints](https://docs.gradle.org/9.8.1/userguide/dependency_constraints.html)). Now the Search YouTube screen brings `:core:mvi` and Lifecycle 2.11.0 into release builds too, so both builds resolve the same versions with no constraint, and #40 removed it. A new debugImplementation dependency needs the same check. To compare the two builds, run `./gradlew -q :app:dependencies --configuration debugRuntimeClasspath` and the same for releaseRuntimeClasspath. Today the debug build has only these extra parts: Compose's preview tooling and the debug screen's two modules. #41 checked it again for Navigation 3: both builds resolve the same versions, and the only versions that changed are navigationevent and navigationevent-compose, from 1.0.0 to 1.1.1, and kotlinx-serialization-core and its BOM, kotlinx-serialization-bom, from 1.7.3 to 1.11.0. The merged release manifest didn't change.

Every module compiles with JDK 17, and the Java and Kotlin bytecode targets follow the toolchain. So every module produces Java 17 bytecode, and the version is written in one place. Android modules get Kotlin from AGP's built-in Kotlin support.

Kotlin compiler warnings fail the build in every module, so a call to a deprecated API stops the build. KotlinCompiler.kt in build-logic turns on the compiler's [allWarningsAsErrors](https://kotlinlang.org/docs/gradle-compiler-options.html#attributes-common-to-jvm-and-javascript) option in the module's `kotlin.compilerOptions` block, which reaches every Kotlin compilation in the module, tests included ([Target the JVM](https://kotlinlang.org/docs/gradle-compiler-options.html#target-the-jvm)). In Android modules, AGP's built-in Kotlin provides that block ([Migrate to built-in Kotlin](https://developer.android.com/build/migrate-to-built-in-kotlin#migration-steps-migrate-kotlin-options)). In plain Kotlin/JVM modules, the Kotlin JVM plugin does. The code in build-logic is a separate build, so the setting doesn't cover it. KSP has a setting of its own, which dewfall.android.hilt and dewfall.android.room turn on through KotlinSymbolProcessing.kt, so warnings that Hilt or Room report while KSP runs fail the build too. javac compiles the Java code that Hilt generates, and builds the Hilt components in the Hilt Gradle plugin's own task. Its warnings, such as Dagger's warnings about the dependency graph, don't fail the build. If a warning can't be fixed, suppress it with @Suppress on the single statement or declaration, never a whole file, with a comment that says why and names the issue that will remove it, if there is one.

**Unit tests.** Testing.kt in build-logic sets up every module's unit tests, so a new module gets them by applying its plugin. Tests use [kotlin.test](https://kotlinlang.org/api/core/kotlin-test/)'s annotations and assertions everywhere, so they read the same in every module. Underneath, two versions of JUnit run them:

- Plain Kotlin/JVM modules run JUnit 6 through the JUnit Platform. Kotlin's docs use JUnit 6 with kotlin.test ([Test code using JUnit](https://kotlinlang.org/docs/jvm-test-using-junit.html)). Gradle 9 no longer puts the JUnit Platform launcher on the test classpath by itself, so Testing.kt adds it ([Gradle upgrade guide](https://docs.gradle.org/9.8.1/userguide/upgrading_version_8.html)).
- Android modules run JUnit 4, AGP's default and the framework in Android's [local test guide](https://developer.android.com/training/testing/local-tests). Robolectric supports only JUnit 4 ([robolectric#3477](https://github.com/robolectric/robolectric/issues/3477)), Compose's test rules are JUnit 4 rules, and JUnit 6 deprecates the engine that would run JUnit 4 tests on it ([JUnit 6.0.0](https://docs.junit.org/6.0.0/release-notes/)).

Both get kotlinx-coroutines-test. [testing.md](testing.md#running-tests-locally) explains how to write and run the tests.

**Repositories.** Libraries come from Google's Maven repository and Maven Central, and plugins from those two and the Gradle Plugin Portal. NewPipeExtractor is the exception: it publishes its releases only on JitPack, which builds each version from TeamNewPipe's Git tag ([ADR 0009](adr/0009-how-the-youtube-layer-reaches-youtube.md)). settings.gradle.kts adds JitPack through Gradle's `exclusiveContent`, with a filter for the group com.github.TeamNewPipe alone. Gradle then looks for that group only on JitPack, and for nothing else there ([repository content filtering](https://docs.gradle.org/9.8.1/userguide/filtering_repository_content.html)). The filter compares the group's name exactly, and NewPipeExtractor's POM names its nanojson dependency as com.github.TeamNewPipe, so the build spells the group that way everywhere. JitPack answers to any case, and NewPipeExtractor's README spells it com.github.teamnewpipe. On a disk that ignores case, as macOS's does by default, Gradle's cache keeps both spellings in one folder, so a build that once resolved the other spelling can show it in the dependency tree. `./gradlew --refresh-dependencies` fixes that.

The versions of every plugin and library are in gradle/libs.versions.toml. To add a module, include it in settings.gradle.kts, apply one of the three plugins in its build.gradle.kts, add dewfall.android.compose, dewfall.android.hilt or dewfall.android.room if it uses them, and set a namespace if it is an Android module.

### Formatting and lint

Four tools check the code. AGENTS.md lists the commands that run them.

**Formatting.** Spotless runs ktfmt in kotlinlang style, with 4-space indents and lines of up to 100 characters. It covers every Kotlin file and Kotlin build script in the repository. Its settings are in the root build.gradle.kts, because build-logic and the root build scripts belong to no module. Kotlin files use LF line endings, set in .gitattributes and in the Spotless settings. ktfmt doesn't read .editorconfig. The Kotlin section there comes from ktfmt and helps the IDE's own formatter come close to it. The roadmap first named ktlint. Its last stable release, 1.8.0 from November 2025, parses Kotlin 2.2. The ktlint organization took the project over from Pinterest and supports newer Kotlin only in 2.0 alphas, which moved to new Maven coordinates ([ktlint changelog](https://github.com/ktlint/ktlint/blob/master/CHANGELOG.md)). Spotless can't load them yet, because it only knows the old coordinates ([KtLintStep.java](https://github.com/diffplug/spotless/blob/gradle/8.10.3/lib/src/main/java/com/diffplug/spotless/kotlin/KtLintStep.java)).

**detekt.** detekt runs its default rules, with the changes in config/detekt/detekt.yml, on every module and on build-logic. Any finding fails the build. Each module runs the plain `detekt` task, which doesn't need compiled code. So the rules that need type resolution don't run, such as InjectDispatcher, which would back the "inject dispatchers" rule in AGENTS.md. ForbiddenImport runs without it, and rejects imports of GlobalScope. It sees only imports, so the logging rules of [ADR 0007](adr/0007-how-the-app-logs.md) are left to [Dewfall's lint checks](#dewfalls-lint-checks), which resolve each call. Suppressing a detekt finding follows the rule for compiler warnings in [Build settings](#build-settings). An import finding can only be suppressed for a whole file, because an import can't carry an annotation, and no file does that. Two brace rules back the Kotlin style rule in AGENTS.md, and neither needs type resolution. BracesOnIfStatements flags an if or if/else that spans lines without braces. It keeps its default policies, singleLine 'never' and multiLine 'always'. The 'never' policy flags braces on a one-line if, which never happens here, because ktfmt puts every braced branch on lines of its own. MandatoryBracesLoops flags a for, while or do loop without braces whose body starts on a new line. BracesOnWhenStatements stays off. Once one branch of a when spans lines, none of its policies allows braces only on the branches that span lines. Android's Kotlin style guide allows a when to mix one-line branches without braces and longer branches with them ([braces](https://developer.android.com/kotlin/style-guide#braces)). So the build doesn't catch a one-line loop without braces, a one-line if with more than one else, a when branch that needs braces, or a forEach that should be a for loop. AGENTS.md's rule and the reviewer subagent in .claude/agents/ cover those. The detekt task reads only src/main and src/test, so Android test and variant folders aren't checked. Composable functions are named in PascalCase, as the Compose API guidelines say, so FunctionNaming ignores functions annotated with @Composable ([detekt and Compose](https://github.com/detekt/detekt/blob/v2.0.0-alpha.6/website/docs/introduction/compose.mdx)). UnusedPrivateFunction and LongParameterList need type resolution, so they don't run yet. When type resolution is turned on, they need the same page's settings for private previews and for composables with many parameters. The build uses detekt 2.0.0-alpha.6, which is an alpha. The newest stable release, 1.23.8 from February 2025, was built for Kotlin 2.0.21, Gradle 8.12.1 and AGP 8.8.1 ([compatibility table](https://detekt.dev/docs/introduction/compatibility/)). It uses a Gradle method that Gradle 10 removes, and its maintainers plan no more 1.23 releases ([detekt#8452](https://github.com/detekt/detekt/issues/8452)). Support for AGP 9's built-in Kotlin came only in the 2.0 alphas ([detekt#8320](https://github.com/detekt/detekt/issues/8320)). 2.0.0-alpha.6 is built against Kotlin 2.4.10, Gradle 9.6.1 and AGP 9.3.1, close to this build's versions ([release notes](https://github.com/detekt/detekt/releases/tag/v2.0.0-alpha.6)). Rule names and settings may still change before 2.0.0, so read the release notes before each detekt update.

**detekt's Compose rules.** detekt also runs [compose-rules](https://github.com/mrmans0n/compose-rules) 0.6.7, a detekt plugin with rules for Compose code, such as "a public composable that emits UI takes a modifier parameter". dewfall.detekt adds it, so it checks every module and build-logic. The Compose section of config/detekt/detekt.yml lists every rule, as compose-rules' docs recommend ([detekt setup](https://github.com/mrmans0n/compose-rules/blob/v0.6.7/docs/detekt.md#enabling-rules)). The docs leave the four opt-in rules off. Three of them stay off: ComposableNestingDepth, PreviewNaming and UnstableCollections. Material2 is on, because it enforces the Material 3 rule in AGENTS.md. It flags any use of Material 2, the androidx.compose.material package, but not the icons in androidx.compose.material.icons, which the rule always allows. Every other rule is on. [rules.md](https://github.com/mrmans0n/compose-rules/blob/v0.6.7/docs/rules.md) explains each rule. Eight rules need type resolution, so they don't run yet: ConditionHoist, InvalidReadOnlyComposable, MissingNonRestartableComposable, MissingReadOnlyComposable, StaleRememberUpdatedStateInRemember, UnnecessaryComposable, UnnecessaryLaunchedEffect and VarsWithoutStateBacking. compose-rules' ComposableNaming checks the names of composable functions, which detekt's FunctionNaming skips. Each compose-rules release is built for one detekt version, and 0.6.7 is built for detekt 2.0.0-alpha.6 ([supported versions](https://github.com/mrmans0n/compose-rules/blob/v0.6.7/docs/detekt.md#supported-versions-matrix)). So each detekt update has to wait for a compose-rules release built for the new detekt version, and both versions change in the same pull request. Renovate puts both in one pull request, which waits for the maintainer's approval ([testing.md](testing.md#dependency-updates)).

**Android Lint.** Lint checks every module, the plain Kotlin/JVM ones through the com.android.lint plugin. It also runs [Dewfall's lint checks](#dewfalls-lint-checks) on main and test sources. Any warning fails the build. Each module checks only its own code. The checks that look up versions or library data on the network are off, so lint gives the same result on every run. AndroidLint.kt in build-logic lists them. Renovate's pull requests bring new versions instead ([testing.md](testing.md#dependency-updates)). The app's manifest suppresses two checks until the tasks that fix them are done: DataExtractionRules (#68) and MissingApplicationIcon (#120).

**The Android API check.** The plain Kotlin/JVM modules compile and run their tests against the full JDK, so a call to a Java API that Android 10 lacks passes there and fails on a phone ([ADR 0006](adr/0006-platform-and-android-versions.md)). Lint can't catch this in these modules. AGP runs lint one module at a time, and lint's API check skips any module that isn't Android ([ApiDetector](https://android.googlesource.com/platform/tools/base/+/refs/heads/mirror-goog-studio-main/lint/libs/lint-checks/src/main/java/com/android/tools/lint/checks/ApiDetector.kt)). So [Animal Sniffer](https://github.com/xvik/gradle-animalsniffer-plugin) checks each of these modules' compiled classes against a [Gummy Bears](https://github.com/open-toast/gummy-bears) signature of the Android API. The signature's API level comes from the minSdk in build-logic, so the check follows it. `./gradlew lint` runs it too.

The signature includes the methods that D8 backports, but not the APIs that core library desugaring adds, because Gummy Bears publishes no such signature for API 29. So the check also flags an API that only desugaring provides. If code needs such an API, check that Android's [desugaring table](https://developer.android.com/studio/write/java11-nio-support-table) lists it, then allow it in configureAndroidApiCheck with Animal Sniffer's ignore or annotation setting, and say why in a comment.

What it checks:

- The module's own classes in src/main. Test code never ships, so it isn't checked, and a module with no classes has nothing to check.
- Calls to the JDK. It doesn't check the module's dependencies, such as NewPipeExtractor, or calls into them, because Animal Sniffer allows every package it finds on the compile classpath, such as kotlinx.coroutines or androidx.lifecycle.
- For an invokedynamic call, only a lambda's target method and interface ([SignatureChecker, Animal Sniffer 1.24](https://github.com/mojohaus/animal-sniffer/blob/animal-sniffer-1.24/animal-sniffer/src/main/java/org/codehaus/mojo/animal_sniffer/SignatureChecker.java#L358-L371)). It never checks the JDK class that sets the call up.

That last point matters for Kotlin's string templates, such as the toString of a data class. Kotlin compiles them to invokedynamic calls through StringConcatFactory, which Android 10 lacks, and Animal Sniffer passes them. That is right, because D8 rewrites these calls into StringBuilder calls ([StringConcatInstructionDesugaring](https://r8.googlesource.com/r8/+/refs/heads/main/src/main/java/com/android/tools/r8/ir/desugar/stringconcat/StringConcatInstructionDesugaring.java)). Lambdas work on any API level too ([Java 8+ language features](https://developer.android.com/studio/write/java8-support)).

### Dewfall's lint checks

`:lint-rules` holds Dewfall's own lint checks, which enforce the logging rules of [ADR 0007](adr/0007-how-the-app-logs.md). The ADR lists the five issues and the two exceptions. Lint runs them on every module of the app, because the three module plugins add `:lint-rules` to each module's `lintChecks` configuration ([Add build dependencies](https://developer.android.com/build/dependencies)). That configuration only feeds lint, so nothing from `:lint-rules` reaches the app. `./gradlew :app:dependencies --configuration releaseRuntimeClasspath` doesn't list it, and neither does the debug classpath.

- **Why lint and not detekt.** This build runs detekt without type resolution, so detekt sees only names and imports. Lint resolves each call, so it catches a fully qualified call, an import alias and a callable reference such as `Log::println`.
- **Test sources.** Lint skips test sources by default, except for issues that ask for them ([Lint](https://developer.android.com/reference/tools/gradle-api/9.4/com/android/build/api/dsl/Lint)). Each Dewfall issue asks for them, so it runs on src/test, where the recorder of saved YouTube responses and the live YouTube check live. The build leaves `checkTestSources` off, so lint's own checks still skip tests. AGP already analyzes each module's tests in a task of its own, such as lintAnalyzeJvmTest, so no task is added.
- **Nothing can suppress them.** Each issue has an empty list of suppress names, as "Can I make my lint check not suppressible?" in lint's [FAQ](https://github.com/googlesamples/android-custom-lint-rules/blob/5751b4f0b7d6c5af67b990e7e06571179baa1e07/docs/api-guide/faq.md.html) describes. Lint then reports @Suppress, @SuppressLint, `//noinspection` and a `disable` setting for the issue as errors of their own, and ignores the issue in lint.xml, in a severity setting and in `checkOnly`. The two exceptions live in LogWriterClass.kt, and each allows only what its writer needs: Log.println in LogcatWriter, and standard output in ConsoleLogWriter. Changing them updates ADR 0007 in the same pull request.
- **@ConstantText.** `:core:logging` marks Logger's messages, withTag's area and every LogField name with @ConstantText. DewfallLogTextNotConstant finds the arguments through lint's annotation handling, which maps named arguments and reads the annotation from compiled classes, as `:core:youtube` sees `:core:logging`. A function that passes its own String parameter on as log text marks it too, such as ResultField in the live YouTube check, and its callers are checked instead.
- **Versions.** Lint's libraries add 23 to AGP's major version, so AGP 9.4.1 comes with lint 32.4.1 ([custom lint rules](https://github.com/googlesamples/android-custom-lint-rules/blob/5751b4f0b7d6c5af67b990e7e06571179baa1e07/README.md#lint-version)). dewfall.lint.rules derives lint-api's and lint-tests' version from the catalog's AGP version, so they have no catalog entry, and Renovate's AGP update moves them too. The issue registry reports lint's current API level, so lint never calls the checks out of date.
- **Kotlin.** Lint runs the checks on its own Kotlin standard library, 2.2.10 for lint 32.4.1, as lint-api's POM names. The `lintChecks` configuration also brings kotlin-stdlib 2.4.20 from `:lint-rules`, but lint loads its own classes first. So dewfall.lint.rules compiles the checks with Kotlin API version 2.2, which keeps them to the 2.2 standard library. Lint 32.4.1 analyzes code with Kotlin compiler 2.4, and AGP 9.3 and later always run lint's K2 mode.
- **Lint's checks for lint code.** lint-checks has checks for code that implements lint checks, such as LintImplTextFormat, which are off by default. dewfall.lint.rules turns on the twelve that lint-checks registers. MissingVendor isn't one of them, so DewfallIssueRegistryTest checks the vendor.

docs/testing.md describes the checks' tests, and how to check a rule in the real build.

## Network access

AGENTS.md says the app's network calls go only to YouTube, through the YouTube layer, and to services the user turns on. PRIVACY.md tells users that the app connects to YouTube. Some libraries make requests of their own, so the app turns those off.

**YouTube.** NewPipeExtractor sends every request through NewPipeDownloader in `:core:youtube`, which sends it with OkHttp ([ADR 0009](adr/0009-how-the-youtube-layer-reaches-youtube.md)). It sends only HTTPS requests to youtube.com, its subdomains and youtubei.googleapis.com, the host of YouTube's API for its mobile clients. It refuses any other link without sending it, and the layer reports that as BROKEN. Its client follows no redirects, so no request reaches a host that it didn't check, and it stores no cookies. HTTP 429, a redirect to Google's captcha page, and HTTP 403 with an HTML body, which is Google's block page for networks that it suspects of automated requests, become ReCaptchaException, so the layer reports them as BLOCKED. For the block page it reads only the status and the content type, never the page's text. The only cookie in the requests is a fixed one that NewPipeExtractor adds to the web client's requests, `SOCS=CAE=`, which tells YouTube that the user rejected all non-essential cookies, so YouTube doesn't answer with its consent page ([generateConsentCookie](https://github.com/TeamNewPipe/NewPipeExtractor/blob/v0.27.1/extractor/src/main/java/org/schabi/newpipe/extractor/services/youtube/YoutubeParsingHelper.java#L1165-L1175)). In a check of the layer with NewPipeExtractor v0.27.1 against YouTube on November 18, 2026, and in the saved responses recorded that day, the functions that NewPipeYouTube implements sent these requests. [What each function sends](#what-each-function-sends) gives the cases that the checks didn't reach. The layer's tests replay the saved responses and check these counts, as [testing.md](testing.md#saved-youtube-responses) describes.

| Function | Hosts | Requests |
|---|---|---|
| Search | www.youtube.com | One |
| Video details, and stream links | youtubei.googleapis.com, for the player request of YouTube's visionOS client, and www.youtube.com, for both clients' visitor IDs, the web client's player request and the video page's other data | Five each: a visitor ID and a player request for each of the visionOS and web clients, and the page's other data. Two for a video that YouTube says can't be played, such as a private one, or that never existed: NewPipeExtractor stops at the visionOS client's answer. |
| A channel's latest uploads | www.youtube.com | Two for most channels: the channel's page, which holds its Videos tab, then its Live tab. One for a channel with no Live tab. |
| Subtitle tracks | The same as video details | Five, as for video details, because NewPipeExtractor reads the tracks from the same page. Two for a video that YouTube says can't be played. |
| Subtitle lines | www.youtube.com, at /api/timedtext | One: the track's link |

NewPipeExtractor asks for a new visitor ID for each player request and keeps none. The first call in each process also fetches www.youtube.com/sw.js_data, to read the version of YouTube's web client, or YouTube's search page if that fails ([getClientVersion](https://github.com/TeamNewPipe/NewPipeExtractor/blob/v0.27.1/extractor/src/main/java/org/schabi/newpipe/extractor/services/youtube/YoutubeParsingHelper.java#L566-L591)). A stream link that needs decoding makes NewPipeExtractor fetch YouTube's player code from www.youtube.com, once per process. No link of the visionOS client needed it in the check, because none had the `n` parameter or a cipher. The stream links and HLS manifests that the layer returns point to googlevideo.com, which the player and downloads contact. The player's requests are below. The tasks that add downloads and image loading list the hosts they contact. The YouTube layer's debug screen, in debug builds only, calls the same functions, so it sends the same requests and nothing else.

**The daily check.** The live check of the YouTube layer runs in the daily workflow and by hand, never in the app ([testing.md](testing.md#the-daily-youtube-check)). It sends the layer's requests through NewPipeDownloader, so the allow list above applies to them. It also asks two of Big Buck Bunny's stream links, an audio-only and a video-only one, for their first KiB, with GET requests and a Range header. So it is the only code today that fetches bytes from googlevideo.com: two small requests a run, plus any retry or redirect, and never from a phone. They go through an OkHttp client of the check's own, which sends only HTTPS requests to googlevideo.com and its subdomains. The client follows no redirects itself. The check follows at most three, and checks each one's host before it sends anything. The layer's allow list doesn't change.

Each request sends the User-Agent that NewPipe v0.29.3's player, which uses NewPipeExtractor v0.27.1, sends for the client that made the link, which the link's `c` parameter names ([YoutubeHttpDataSource](https://github.com/TeamNewPipe/NewPipe/blob/v0.29.3/app/src/main/java/org/schabi/newpipe/player/datasource/YoutubeHttpDataSource.java#L669-L675)). It comes from NewPipeExtractor's YoutubeParsingHelper, with its default localization, en-GB, so the country in it is GB. NewPipeExtractor v0.27.1 makes every link with its visionOS client.

| Client in the link | User-Agent that the check sends |
|---|---|
| VISIONOS | getVisionOsUserAgent, the one that the visionOS client's player request sends |
| Any other | None. The check fails with LINK_NOT_PLAYABLE without sending anything. |

StreamLinks carries no headers, so this table binds only the check. The player's requests follow the rules below. In a run by hand on November 18, 2026, with NewPipeExtractor v0.27.1, both links were made for the visionOS client, and both answered 206 with the format's length.

From GitHub's runners, YouTube blocks the player requests of video details and stream links. On November 18, 2026, with NewPipeExtractor v0.27.1, it answered the visionOS client's player request on some runners with its "confirm you're not a bot" status, which NewPipeExtractor throws as SignInConfirmNotBotException, and on others with HTTP 403 and Google's block page, an HTML page, which NewPipeDownloader throws as ReCaptchaException. The layer returns BLOCKED for both. Search and channel pages worked. Phones on home or mobile networks don't use data-center addresses, so the block affects CI only. The daily workflow skips blocked calls, and a run by hand, which skips nothing, checks them ([testing.md](testing.md#youtube-blocks-the-runners-video-calls)).

**The player.** While a video plays, the app fetches its bytes from googlevideo.com. Every such request goes through MediaRequests, the YouTube layer's second client, which `:core:data` hands the player as OkHttp's Call.Factory. Its rules follow NewPipe v0.29.3's player, which uses NewPipeExtractor v0.27.1, checked on November 19 and 20, 2026:

- **Hosts.** Only HTTPS requests to googlevideo.com and its subdomains. Its OkHttpClient follows no redirects. MediaRequests follows at most 20, the same as NewPipe's, checks each Location's host before it sends anything, and closes each redirect's response. A 307 or 308 keeps the method and the body, and 300 to 303 become a GET with no body, as OkHttp's own rule does ([RetryAndFollowUpInterceptor at parent-5.5.0](https://github.com/square/okhttp/blob/parent-5.5.0/okhttp/src/commonJvmAndroid/kotlin/okhttp3/internal/http/RetryAndFollowUpInterceptor.kt)). Anything else fails with MediaRequestRefused, an IOException with constant text, before it is sent. The client keeps no cookies and no cache.
- **Progressive formats.** NewPipe's main path plays each format through a DASH manifest that NewPipeExtractor builds. Only when it can't build one does it fall back to a progressive source through [YoutubeHttpDataSource](https://github.com/TeamNewPipe/NewPipe/blob/v0.29.3/app/src/main/java/org/schabi/newpipe/player/datasource/YoutubeHttpDataSource.java). Dewfall follows that fallback, which needs no DASH library: each request is a POST with the body `x\0` and `TE: trailers`, keeps Media3's Range header, gets an `rn` parameter that numbers each source's requests from 0, and sends the visionOS client's User-Agent. Only a link whose `c` parameter is `VISIONOS` gets it, and it stays across redirects. Any other link fails without a request.
- **The User-Agent.** The stream links call, which holds the layer's Mutex, reads it from NewPipeExtractor's `getVisionOsUserAgent` with the layer's Localization, en-GB, and keeps it for MediaRequests, so no NewPipeExtractor code runs on Media3's loader threads. NewPipe passes no Localization, which falls back to the same en-GB, so the string is the same.
- **Live streams.** HLS requests stay plain GET requests with NewPipeDownloader's own User-Agent, as NewPipe sends them for live streams.
- **Language.** Every request sends `Accept-Language: en-GB` and no country, as ADR 0009's note of November 20, 2026 says. The bytes hold no text, and the links already carry the country of the call that made them.

A 1080p run of a 2 h 31 min video on November 20, 2026, with a seek near its end, played through these rules. Opening a video now sends the video details call and the stream links call, five requests each, one after the other behind the layer's Mutex, so the picture starts after both. #181 tracks sharing one call between them.

**Media3's platform diagnostics.** ExoPlayer would send each video's length, codecs, sizes, bitrates and errors to Android's media metrics, which Google may collect. The player turns that off, as [The player](#the-player) says.

**The INTERNET permission and OkHttp's initializer.** `:app`'s manifest declares the INTERNET permission. The Android build of OkHttp merges the same permission into the app's manifest, and adds [PlatformInitializer](https://github.com/square/okhttp/blob/parent-5.5.0/okhttp/src/androidMain/kotlin/okhttp3/internal/platform/PlatformInitializer.kt) to App Startup's InitializationProvider. It runs at startup, makes no network call, and only stores the app's Context, from which OkHttp reads its public suffix list in the APK's assets ([AssetPublicSuffixList](https://github.com/square/okhttp/blob/parent-5.5.0/okhttp/src/androidMain/kotlin/okhttp3/internal/publicsuffix/AssetPublicSuffixList.kt)). So the app keeps it.

**Emoji fonts.** Compose brings in androidx.emoji2. Its EmojiCompatInitializer runs at startup and asks the system's downloadable font provider for a newer emoji font. On most phones that provider is Google Play services, which may download the font from Google. `:app`'s manifest removes the initializer with `tools:node="remove"`, as the [emoji2](https://developer.android.com/develop/ui/views/text-and-emoji/emoji2#use-different-font-provider) and [App Startup](https://developer.android.com/topic/libraries/app-startup#disable-individual) docs describe. The cost is on older phones. Android 11 and lower can't update their emoji font ([Display emoji](https://developer.android.com/develop/ui/compose/text/emoji)), so very new emoji, such as those in video titles, may show as empty boxes on Android 10 and 11.

**Fonts.** The theme bundles its font, Manrope, in the APK. Compose's downloadable fonts, from androidx.compose.ui:ui-text-google-fonts, would ask Google Play services' font provider for it instead, and that provider may download the font from Google. So the app doesn't use downloadable fonts. See [The theme](#the-theme).

## Logging

Every module logs through the Logger in `:core:logging`. [ADR 0007](adr/0007-how-the-app-logs.md) explains why the app has its own logger, and defines the user data that no log line may contain. The logger makes no network calls and has no startup initializer. Its lines go to logcat on the phone and nowhere else.

### The parts

| Type | Module | What it does |
|---|---|---|
| Logger | `:core:logging` | The class that code calls: `debug`, `info`, `warn` and `error`. It drops lines below its minimum level before it builds any text, and writes an error as its summary. It is final, so every line goes through the same formatting. |
| LogLevel | `:core:logging` | DEBUG, INFO, WARN and ERROR. |
| LogField | `:core:logging` | A value logged after the message as `name=value`: a count, a flag, a duration or an enum's name. It has no version that takes a String. |
| ConstantText | `:core:logging` | The annotation that marks Logger's messages, withTag's area and every LogField name as constant text. A lint check flags any argument for them that isn't. See [Dewfall's lint checks](#dewfalls-lint-checks). |
| LogWriter | `:core:logging` | Where finished lines go. It has one function, so a test can pass a lambda. |
| LogcatWriter | `:app` | The LogWriter that writes to logcat. It is the only code that calls android.util.Log. |
| ConsoleLogWriter | `:core:youtube`'s tests | The LogWriter of the recorder of saved YouTube responses and of the live check, which writes to standard output, where Gradle shows it. It is the only code in src/test that writes to standard output. See [testing.md](testing.md#recording-the-sets) and [The daily YouTube check](testing.md#the-daily-youtube-check). |
| LoggingModule | `:app` | The Hilt module that provides one Logger for the whole app, with LogcatWriter and the build's lowest level. |

### Using the logger

A class receives a Logger in its constructor and keeps it with an area tag. Messages, areas and field names are constant text. Values go in fields, and an error goes in the `error` parameter. For example:

```kotlin
class UploadsRefresher(logger: Logger) {
    private val log = logger.withTag("YouTube")

    fun onRefreshed(videoCount: Int) {
        log.info("Refreshed uploads", LogField.count("videos", videoCount))
    }

    fun onRefreshFailed(attempt: Int, error: Exception) {
        log.warn("Refresh failed", LogField.count("attempt", attempt), error = error)
    }
}
```

The warning above is written with the tag Dewfall.YouTube, as text like this:

```
Refresh failed attempt=2
java.io.IOException
  at io.github.dewfallapp.core.youtube.UploadsRefresher.refresh(UploadsRefresher.kt:41)
Caused by: java.net.SocketTimeoutException
  at ...
```

- Messages, areas and field names are a string literal, a `const val`, or `+` of those, never a string template, even of constants. A function that passes its own String parameter on as one of them marks it @ConstantText, so its callers are checked instead. Lint flags anything else, in tests too.
- Every tag is Dewfall, or Dewfall followed by a dot and an area, such as Dewfall.YouTube, so one prefix finds every line the app writes. Android limits tag length only up to Android 7 ([isLoggable](https://developer.android.com/reference/android/util/Log#isLoggable(java.lang.String,%20int))), but tags stay short anyway, because LogcatWriter leaves only 68 bytes of each entry for everything except the text. See [Long text](#long-text).
- The error summary writes the class name of the error and of each cause, each with up to 30 stack frames, and at most 10 causes. It never writes a message or toString(). It stops at a cause it already wrote, so a chain that loops ends.
- There is no global logger. Hilt provides the Logger, and code that Hilt doesn't build gets it from whoever builds that code.
- Reducers never log, because logging is a side effect. ViewModels may.
- Tests pass `Logger.Silent`, or a Logger with a writer that records lines. [testing.md](testing.md#logging) shows how.

### Levels in each build

Debug builds keep every level. Release builds keep WARN and ERROR. LoggingModule picks the level from BuildConfig.DEBUG, which `:app` turns on, as [Build settings](#build-settings) describes.

Google's [Log Info Disclosure](https://developer.android.com/privacy-and-security/risks/log-info-disclosure) page recommends a logcat backend in developer builds only. For apps that can't avoid logs in production, it falls back to keeping only warnings and errors, with R8 removing the other levels, with no values that aren't constant in the log statements, and with flags that can shut logging down during an incident. Dewfall keeps one part of the fallback, warnings and errors in release builds, because it has no crash reporting: the WARN and ERROR lines in logcat are what a user can read with adb and share when reporting a problem. It departs from the fallback in three ways, as [ADR 0007](adr/0007-how-the-app-logs.md#why-release-builds-keep-warnings-and-errors) explains. Its lines keep LogField values and error class names and frames, which aren't constant, but whose types keep user data out. Until Phase 9 turns on R8, a level check drops the other levels, not R8. And it has no flag to shut logging down, because it has no server or remote settings, so only a new release can change logging.

Nothing shrinks the code yet, so release builds still contain the debug and info calls and their constant text. Those calls return after one level check, before building any text. When Phase 9 turns on R8 (#146):

- A `-assumenosideeffects` rule on Logger.debug and Logger.info removes those calls ([rule types](https://developer.android.com/topic/performance/app-optimization/additional-rule-types)). Phase 9 checks R8's output for what stays, such as the code that builds the LogField arguments.
- The same page describes `-maximumremovedandroidloglevel` as removing Android logging calls, such as Log.w and Log.isLoggable, at or below a level. LogcatWriter passes the priority as a variable, so that rule can't remove its calls by level. The `-assumenosideeffects` rule above does that job instead.

### Long text

liblog cuts any log entry whose payload, the priority, the tag and the text, is over LOGGER_ENTRY_MAX_PAYLOAD, 4068 bytes ([log.h](https://android.googlesource.com/platform/system/logging/+/bcac7c30d88a3773a7c0bc9f5617a23a886331fd/liblog/include/log/log.h#71)). An error summary at its limits goes far past that. So LogcatWriter splits text longer than 4000 bytes into several entries, with the same priority and tag:

- It splits at line breaks, so each entry holds whole lines where it can.
- It splits a single line longer than 4000 bytes between characters, never inside one.
- It counts a character outside the Basic Multilingual Plane, such as most emoji, as 6 bytes. Log.println hands the text to liblog through JNI's GetStringUTFChars ([android_util_Log.cpp](https://android.googlesource.com/platform/frameworks/base/+/1cdfff555f4a21f71ccc978290e2e212e2f8b168/core/jni/android_util_Log.cpp#92)). The [JNI spec](https://docs.oracle.com/en/java/javase/17/docs/specs/jni/functions.html#getstringutfchars) says that returns modified UTF-8, which writes such a character as 6 bytes. ART writes it as UTF-8's 4 bytes instead, against the spec ([jni_internal.cc](https://android.googlesource.com/platform/art/+/6484611fd45e69db9f33f98bfd6864014b030ecf/runtime/jni/jni_internal.cc#222)). Counting 6 keeps each entry within 4000 bytes either way.

The splitting is a function that unit tests call directly, because android.util.Log has no working code in local unit tests.

### What the logger can't cover

- **NewPipeExtractor's logger.** NewPipeExtractor has its own hook, ExtractorLogger, which stays silent until an app sets a logger. Dewfall never sets it, because half of its calls log user data, such as URLs and titles. A lint check flags any use of ExtractorLogger and any class that implements NewPipeExtractor's Logger.
- **Uncaught exceptions.** Android's RuntimeInit installs a pre-handler in every app process that writes an uncaught exception's full stack trace, messages included, to logcat's crash buffer. Apps can replace the default handler, but not the pre-handler ([RuntimeInit.java](https://android.googlesource.com/platform/frameworks/base/+/1cdfff555f4a21f71ccc978290e2e212e2f8b168/core/java/com/android/internal/os/RuntimeInit.java#240)). This gap is permanent. Catching exceptions at module edges narrows it. See [Error handling](#error-handling).
- **Other libraries' own logs.** Media3 logs errors by default ([Debug logging](https://developer.android.com/media/media3/exoplayer/debug-logging)), and its lines append each throwable's message, which can name a host or carry a link. So the player turns Media3's logging off before its first Media3 call, as [The player](#the-player) says, and ADR 0007's note records. WorkManager has its own minimum logging level ([custom configuration](https://developer.android.com/develop/background-work/background-tasks/persistent/configuration/custom-configuration)). The task that adds it checks it against ADR 0007.
- **OkHttp's logs.** On Android, OkHttp writes its own lines to logcat, under the tag OkHttp. One of them warns that a response body was never closed, and names the host ([RealConnectionPool](https://github.com/square/okhttp/blob/parent-5.5.0/okhttp/src/commonJvmAndroid/kotlin/okhttp3/internal/connection/RealConnectionPool.kt#L310)). Another reports an IOException that leaves a response callback, with the request's host ([RealCall](https://github.com/square/okhttp/blob/parent-5.5.0/okhttp/src/commonJvmAndroid/kotlin/okhttp3/internal/connection/RealCall.kt#L588)). NewPipeDownloader closes every answer and catches every exception in its callback, so it causes neither.
- **Calls that skip the logger.** Lint flags them: android.util.Log outside LogcatWriter, console output outside ConsoleLogWriter, and other logging APIs, such as java.util.logging. The two writers are where lines leave the logger. See [Dewfall's lint checks](#dewfalls-lint-checks).

## The YouTube layer

`:core:youtube` is the only module that reaches YouTube, and the only one that may use NewPipeExtractor ([ADR 0001](adr/0001-newpipeextractor-behind-youtube-layer.md)). It returns Dewfall's own models from `:core:model`, so nothing outside it knows which library sits underneath. [ADR 0008](adr/0008-youtube-layer-contract.md) explains its results, errors, IDs and time types.

### The interfaces

There is one interface for each thing the app needs from YouTube, so each caller depends only on what it uses. Their names follow Google's convention for data sources, the type of data followed by RemoteDataSource ([data layer](https://developer.android.com/topic/architecture/data-layer)).

| Interface | Function | Returns | Implemented in |
|---|---|---|---|
| SearchRemoteDataSource | `search(query)` | The first page of channels and videos | #35 |
| VideoDetailsRemoteDataSource | `videoDetails(videoId)` | What the video screen shows | #35 |
| ChannelUploadsRemoteDataSource | `latestUploads(channelId)` | The channel, with its latest long-form videos and live streams | #35 |
| StreamLinksRemoteDataSource | `streamLinks(videoId)` | The links that play or download the video | #35 |
| SubtitlesRemoteDataSource | `subtitleTracks(videoId)`, then `subtitleLines(track)` | The video's subtitle tracks, then one track's lines | #148 |

The first four are `fun interface`s, so a test can pass a lambda instead of writing a fake. One class can implement several of them. NewPipeYouTube implements all five, and Hilt provides it, as [How Hilt provides the layer](#how-hilt-provides-the-layer) describes.

Every function keeps these rules. Each interface's KDoc repeats the first two.

- It is a suspend function, and main-safe: it can be called from any thread, including the main thread, and the implementation moves blocking work to a dispatcher it receives, so tests can replace it, as the [data layer guide](https://developer.android.com/topic/architecture/data-layer) asks.
- It returns `YouTubeResult`: `Success` with the value, or `Failure` with a `YouTubeError`. It reports every problem with YouTube or the network as a Failure. The only exception it throws is CancellationException, when the calling coroutine is cancelled.
- It catches every NewPipeExtractor exception at the module's edge, logs it with the Logger, and returns its category. No NewPipeExtractor exception leaves the module. See [Mapping NewPipeExtractor's exceptions](#mapping-newpipeextractors-exceptions).
- It never logs the search text, IDs, URLs or anything else that ADR 0007 calls user data.

### The models

| Model | What it holds |
|---|---|
| VideoId, ChannelId | A video's or a channel's ID. The constructor checks the format and throws IllegalArgumentException with a fixed message. `parseOrNull` returns null instead, for text from outside the app, such as the Takeout import. toString leaves out the ID. |
| VideoSummary | A video in a list: ID, title, channel ID and name, length, publish time, thumbnails and VideoKind |
| VideoKind | VIDEO, SHORT, LIVE or UPCOMING. VIDEO includes finished premieres and recordings of live streams. LIVE includes a premiere that is playing now. |
| Channel | ID, name, avatars and description |
| SearchResults | The first page of a search: channels and videos, in YouTube's order |
| ChannelUploads | A channel, and its latest long-form videos and live streams, never Shorts, in the order that [A channel's latest uploads](#a-channels-latest-uploads) describes. A premiere or live stream that hasn't started is UPCOMING. |
| VideoDetails | ID, title, channel ID, name and avatars, description as plain text, length, publish time, thumbnails, and whether it is live |
| StreamLinks | Audio formats, video formats, an HLS manifest, and when the links stop working |
| SubtitleTrack, SubtitleLine | A track's language as a BCP 47 tag, whether YouTube generated it, and its link. A line's start, end and plain text, with the times YouTube gives it. See [Subtitles](#subtitles). |
| Thumbnail | One size of an image, with its width and height when YouTube gives them |
| PublishedTime | When a video was published or is scheduled to start, and whether that time is only an estimate |
| YouTubeResult, YouTubeError | What a function returns, and why it failed. See [Error handling](#error-handling). |

The ID, title, channel ID and channel name of a video are required, and so are the ID and name of a channel. When the layer can't read one of them, it leaves the item out of a list, or fails a call about one video or channel with BROKEN. The other fields are optional, and the layer fills a missing one with null or an empty list or text. YouTube often leaves them out, such as a channel's description or a live stream's length, so a missing one logs nothing. The layer warns only about items it left out, and about parts that NewPipeExtractor reports it couldn't parse, in one line for each call, as [How a call runs](#how-a-call-runs) describes.

Times are kotlin.time's Instant, and lengths are its Duration. YouTube often gives only a date with no time, or a relative time in days or longer, such as "3 weeks ago". Then PublishedTime's `isApproximate` is true, and its Instant is an estimate: the time of day means nothing, and the date can be as far off as the unit YouTube gave. A relative time in hours, such as "7 hours ago", isn't marked approximate, but is only as exact as the hour.

A model's thumbnails hold every size YouTube offers. The screen picks the smallest one that is at least as wide as the space it fills in pixels, or the largest one if none is that wide. That helper comes with the first screen that shows a thumbnail, which #176 decides. Until then, rows show a plain box.

### A channel's latest uploads

ChannelUploads holds a channel's long-form videos and live streams, and never its Shorts. Whether the inbox shows videos that haven't started is a Phase 5 decision.

On a channel's page, YouTube shows regular videos and premieres in the Videos tab, live streams in the Live tab, whether they are on air, upcoming or recorded, and Shorts in the Shorts tab. So the layer reads two tabs and merges them, which takes two requests for most channels:

- The Videos tab. NewPipeExtractor's channel request already holds it, with the channel's name, avatar and description.
- The Live tab. A channel with no live streams has no Live tab, and then the layer sends only the first request.

NewPipeExtractor can't read an age-restricted channel's tabs. It reads the channel's system playlists of videos and of live streams instead, with two requests each, or one when a playlist doesn't exist ([YoutubePlaylistExtractor](https://github.com/TeamNewPipe/NewPipeExtractor/blob/v0.27.1/extractor/src/main/java/org/schabi/newpipe/extractor/services/youtube/extractors/YoutubePlaylistExtractor.java#L83-L114)). So such a channel takes three to five requests. When YouTube sends a channel's ID on to another channel, each step adds a request. Every item takes the channel's ID and name from the channel's page, and an item that is in both tabs is kept once, in its first place.

Both tabs give the length of each video that can be watched from start to end. NewPipeExtractor's item code marks a stream that is on air by its badge, and a premiere or stream that hasn't started by its scheduled start. The other publish times are relative, such as "Streamed 2 years ago", so most of them are approximate.

The merged list follows this order, which the KDoc of ChannelUploads and ChannelUploadsRemoteDataSource states too:

- The videos are sorted by their publish time, newest first. Exact and approximate times are compared as they are.
- An upcoming video's time is its scheduled start, which is in the future, so upcoming videos with a scheduled start come first.
- Videos with no publish time come last.
- Videos with the same time, and videos with no time, keep the order YouTube gave, with the Videos tab's videos before the Live tab's.

UploadOrder.kt implements this order, and its tests cover each rule. If the inbox wants a different order later, Phase 5 sorts its own rows.

The channel's RSS feed was rejected. It lists Shorts without marking them, so leaving them out would depend on an undocumented link pattern, and it gives no lengths.

### Stream links

YouTube serves most of a video's sound and picture as separate formats. Each format in StreamLinks is a single file that HTTP GET requests with byte ranges can read. The layer keeps the formats that NewPipeExtractor marks as PROGRESSIVE_HTTP with a direct link, and leaves out the formats that need a manifest to play. So a player usually plays one audio format and one video format together, such as with Media3's MergingMediaSource ([media sources](https://developer.android.com/media/media3/exoplayer/media-sources)). A video format whose `includesAudio` is true has the sound too, so it plays on its own. NewPipeExtractor v0.27.1 reads formats only from YouTube's visionOS client, which in the check gave no such format, so a player plays an audio and a video format together, or the HLS manifest. A live stream has only the HLS manifest.

A format's codec, length in bytes, bitrate, width, height or frame rate is null when NewPipeExtractor doesn't know it.

The links stop working after some time. NewPipeExtractor doesn't say when, so the layer reads it from the links themselves: the `expire` query parameter of each format's link, and the `/expire/` path segment of the HLS manifest's link. `expiresAt` is the earliest of them, or null when no link has one. Callers fetch new links just before playing or downloading, and again when a link stops working. The player fetches the bytes through MediaRequests, as [Network access](#network-access) describes. StreamLinks has no DASH manifest, because NewPipeExtractor v0.27.0 stopped giving one.

A video with no format that plays as a single file and no HLS manifest fails with BROKEN. NewPipeExtractor leaves out a format whose link it can't decode, without an error, so an empty answer is the only sign that it couldn't read the links.

### Descriptions

NewPipeExtractor gives a video's description as HTML, with `<br>` for line breaks and `<a>` for links. VideoDetails holds it as plain text, so the layer turns it into text with jsoup's [wholeText](https://jsoup.org/apidocs/org/jsoup/nodes/Element.html#wholeText()). Each `<br>` becomes a newline, and a link keeps only the text it shows. NewPipeExtractor writes a run of spaces as a space followed by no-break spaces, and the layer turns those back into spaces.

For some of YouTube's answers, NewPipeExtractor puts the description's text into the HTML without escaping it, so a `<` in the text would read as the start of a tag. Before jsoup reads the HTML, the layer escapes each `<` that doesn't start one of the tags that NewPipeExtractor writes: links, `<b>`, `<i>`, `<s>`, their closing tags, and `<br>`. Text that looks exactly like one of those tags, such as `<b>`, still reads as a tag, and an entity such as `&amp;` that wasn't escaped is decoded. A channel's description already comes as plain text.

### Subtitles

SubtitlesRemoteDataSource lists a video's tracks first, then reads the lines of the track the caller picks. A track carries its link, so reading its lines is one request. The facts below come from a check against YouTube on November 18, 2026, with NewPipeExtractor v0.27.1, and from the saved sets SUBTITLES_ME_AT_THE_ZOO, SUBTITLES_TEARS_OF_STEEL and SUBTITLES_AUTO_DUBBED, recorded the same day ([testing.md](testing.md#the-saved-sets)).

**Tracks.** NewPipeExtractor lists the tracks that the visionOS client's player answer names ([getSubtitles](https://github.com/TeamNewPipe/NewPipeExtractor/blob/v0.27.1/extractor/src/main/java/org/schabi/newpipe/extractor/services/youtube/extractors/YoutubeStreamExtractor.java#L683-L723)), from the same page as video details, so listing them sends the same five requests. It lists only the tracks that YouTube has, those that a person wrote and those that YouTube generated from speech, and never YouTube's machine translations into other languages: it removes the `tlang` parameter from each link. A video with no tracks, such as Big Buck Bunny, which has no speech, has an empty list.

The layer leaves out one more kind of machine translation. For a video with AI-dubbed audio tracks, YouTube also lists an auto-generated track in each dubbed language, made from the dubbed audio, whose text is a translation. Each of three NASA videos had 20 of them, one for each dubbed audio track, next to a written English track and an auto-generated English one from the original speech. The link of each of the 20 had `variant=timing-optimized`, while the original track's had `variant=gemini` or `variant=unified`, and Tears of Steel's auto-generated track, from a video without dubbing, had none. YouTube doesn't document the parameter, so the saved set SUBTITLES_AUTO_DUBBED and the daily check's kind of the same name watch it: if YouTube renames it, the dubbed tracks come back, and the check fails. The layer drops an auto-generated track whose link has that variant. A written track is never dropped. The other variants vary from one fetch to the next: in one of 15 fetches of the same NASA video on November 19, 2026, YouTube listed its auto-generated English track twice, as en with no variant and as en-US with `variant=unified`, and the layer kept both.

Each track's link goes to www.youtube.com/api/timedtext, which the layer's allow list accepts, and needs no PoToken. It carries a signature and an expiry, which was about 7 hours after the tracks were listed. So callers list the tracks just before reading their lines, as they fetch stream links just before playing. NewPipeExtractor v0.27.0 added a name to each track, YouTube's label, such as "English (auto-generated)". SubtitleTrack has no field for it, so two tracks in the same language and of the same kind look the same, and later phases take the first, in YouTube's order.

**Language tags.** NewPipeExtractor passes YouTube's language code on unchanged. Almost all of YouTube's codes are BCP 47 tags already, such as en, pt-BR, es-ES or zh-Hant, but YouTube writes Hebrew as iw, an old ISO 639 code that BCP 47 replaced with he. The layer writes each code with Locale's BCP 47 functions, `Locale.forLanguageTag(code).toLanguageTag()`, which write iw as he, and in and ji as id and yi, as the documentation of [Locale](https://developer.android.com/reference/java/util/Locale) says under legacy language codes. Of the 159 codes in YouTube's answers, the 156 of its list of translation languages and the other codes of the tracks in the saved sets, only iw changed. A code that isn't a tag at all becomes und, BCP 47's tag for an unknown language, and counts as a part that the layer couldn't read.

**The format.** The layer asks for TTML, the W3C's Timed Text Markup Language, which NewPipeExtractor also uses by default ([getSubtitlesDefault](https://github.com/TeamNewPipe/NewPipeExtractor/blob/v0.27.1/extractor/src/main/java/org/schabi/newpipe/extractor/services/youtube/extractors/YoutubeStreamExtractor.java#L677-L679)), so NewPipe's own users read the same links. The track's link asks for it with `fmt=ttml`, which NewPipeExtractor adds when it lists the tracks, so SubtitleTrack's link is the one that the layer reads, unchanged. YouTube also sends WebVTT, and its own formats srv1, srv3 and json3. In the check, every track came back with its text in each of them, and these were the differences:

| Format | A written track | An auto-generated track | Why it lost |
|---|---|---|---|
| TTML | One `p` per line, with `begin` and `end` times, and `<br />` for a line break | The same, one phrase per `p`, with no repeated text | Chosen |
| WebVTT | One cue per line | Each cue repeats the row before it, with a tag before each word, such as `<00:00:05.000><c> have</c>`, and a cue of 10 milliseconds between them | Clean phrases would need guesses that real repeated speech can fool |
| srv3 | One `p` per line, with times in milliseconds | Each word in its own `s` element, an empty `p` between lines, and elements for windows and pens | It is YouTube's own format, with no documentation, and it would need more rules of YouTube's own. The word times aren't in the model. |
| srv1 | One element per line | The same | It escapes character references twice, such as `&amp;#39;` for an apostrophe |
| json3 | One event per line | Each word in its own segment, and an event with only a line break between lines, as in srv3 | It would need rules of YouTube's own too, and a JSON parser, which `:core:youtube` doesn't depend on itself |

TTML was also the smallest for the auto-generated track of Tears of Steel: 8.3 KB, against 16.1 KB for srv3 and 21 KB for WebVTT.

**Reading the file.** TtmlLines.kt reads the file with jsoup's XML parser, which `:core:youtube` already uses for descriptions, so the same code runs on the computer and on a phone. jsoup never reads a document type declaration or an external entity, and never expands an entity that one declares: `&secret;` stays as that text. YouTube's TTML has no declaration, so the layer also refuses any file that has one. The root element must be `tt` in TTML's namespace, and each `p` in the body is one line:

- Its times are `begin` and `end`, as clock times such as 00:01:02.345, kept as YouTube gives them. A line whose times are missing or can't be read, or that ends at or before its start, is left out and counted, so the call logs "YouTube answer read in part".
- Its text is plain. jsoup decodes character references, such as `&#39;`. Styling, such as a `span`, is left out and its text kept. Each run of white space, line breaks and no-break spaces in the file included, becomes one space, and each `br` becomes a line break. Each row is trimmed, and empty rows are left out. A line with no text is left out without a warning.
- The lines are sorted by their start. Lines that start together keep the file's order.

Lines can overlap. In an auto-generated track they overlap by design: YouTube shows two lines at once, so each line ends when the line after the next one starts. 47 of the 97 lines of Tears of Steel's auto-generated track overlapped the next. In a written track an overlap means two lines on screen at once, such as a short line inside a longer one in Tears of Steel's Hebrew track. The layer keeps YouTube's times either way, because a caller that wants lines one after another can end each line when the next one starts, while times that the layer changed couldn't be got back. How lines show on screen is the player's decision, in Phase 4.

Sound labels, such as [Music] and [Applause], stay, as YouTube shows them. Phase 8 decides what its search index keeps.

**Failures.** Listing the tracks fails as video details do, because it reads the same page: PRIVATE for a private video, and UNAVAILABLE for an age-restricted video and for one that never existed. Reading the lines maps YouTube's answer to the track's link:

| Answer | Category |
|---|---|
| 200 with a TTML file | The lines. A file with no lines gives an empty list. |
| 403 without an HTML body, 404 or 410. An expired link, a link whose signature doesn't match and a link for another video each got 404 with Google's HTML error page. | UNAVAILABLE. Here it means that the link no longer works, so the caller lists the tracks again, which also shows whether the video is still there. SubtitlesRemoteDataSource's KDoc says so. |
| 403 with an HTML body, 429, or a redirect to Google's captcha page | BLOCKED, as for every request, see [Network access](#network-access) |
| 500 to 599 | NETWORK, so a job tries again later. BROKEN would say that only an update helps. |
| Any other status, or 200 with something that isn't a TTML file: an empty body, an HTML page, another format, or a file with a document type declaration | BROKEN. A link without its signature, and one for a language that the video has no track in, each got 200 with an empty body. |

### How a call runs

Every call into NewPipeExtractor goes through NewPipeCalls in `:core:youtube`, which keeps the rules above for each one. [ADR 0009](adr/0009-how-the-youtube-layer-reaches-youtube.md) explains the choices.

- **Setup.** NewPipeExtractor's setup, NewPipe.init, is global. It writes static fields that every request reads, and they aren't volatile. NewPipeCalls runs it once, on the first call, inside a lazy. Every call reads the lazy on the thread that then calls NewPipeExtractor, so that thread sees the setup. Nothing runs at app startup.
- **Language and country.** Every request asks for British English, because NewPipeExtractor reads some of YouTube's answers by their English text. Its country is the device's region, read at each call, or GB when YouTube's country list doesn't have the region. Each extractor that the layer builds gets both from RequestSettings. NewPipeExtractor builds some extractors itself, inside another one, such as the system playlists that an age-restricted channel's tabs read. Those ask for NewPipe's preferred country, so NewPipeCalls sets it to the call's country before each call.
- **Threads and cancellation.** A call runs its blocking work with runInterruptible, on the dispatcher that NewPipeCalls receives. Cancelling the caller interrupts that thread. NewPipeDownloader then cancels the request in progress and throws InterruptedIOException, and the thread stays interrupted, so NewPipeExtractor's next request stops too. NewPipeExtractor passes that exception on, and it would read as NETWORK, so NewPipeCalls checks for cancellation first and throws CancellationException.
- **One call at a time.** NewPipeExtractor keeps caches in static fields that it reads and writes without locks. Every call into NewPipeExtractor touches at least one of them, so each holds one Mutex while NewPipeExtractor runs, and they wait for each other. The fields belong to the process, so the Mutex does too, and every NewPipeCalls shares it. The table below lists the static state of v0.27.1 that the layer's calls reach, apart from ExtractorLogger's logger, which is volatile and which Dewfall never sets ([Logging](#what-the-logger-cant-cover)). It leaves out youtubeMusicClientVersion, which only YouTube Music requests read and write. v0.27.0 removed the static PoToken provider and iOS client flag, and setPoTokenProvider now does nothing.
- **Downloads.** Reading a subtitle track's lines calls no NewPipeExtractor code that reads or writes its static state: it sends one request through NewPipeDownloader, which keeps no state of its own, and parses the answer with jsoup. So NewPipeCalls runs it with `runDownload`, which keeps every rule above except the lock and the setup: a download doesn't wait for a call that holds the lock, such as a search behind an overnight job's slow video page, and a slow download doesn't hold up other calls. The request still goes through the downloader and its host checks, and asks for British English. It names no country, like NewPipeExtractor's request for sw.js_data, because the link comes from the call that listed the tracks, which named the call's country. See [ADR 0009](adr/0009-how-the-youtube-layer-reaches-youtube.md).
- **Logging.** A failure is logged once, with the message "YouTube call failed" and two fields: the call, such as SEARCH, and the category. BROKEN is logged at ERROR, because only an update fixes it, and the other categories at WARN. The error is written as class names and stack frames, as [Logging](#logging) describes. A call that succeeds after leaving out items that lack a required field, or after NewPipeExtractor reported parts it couldn't parse, logs one WARN line, "YouTube answer read in part", with three fields: the call, `dropped`, the number of items left out, and `parseErrors`, the number of parse errors.

| Static state in NewPipeExtractor v0.27.1 | Written by | Read or written by | How the layer keeps it safe |
|---|---|---|---|
| The downloader, localization and country ([NewPipe](https://github.com/TeamNewPipe/NewPipeExtractor/blob/v0.27.1/extractor/src/main/java/org/schabi/newpipe/extractor/NewPipe.java#L39-L41)) | NewPipe.init, and NewPipeCalls, which sets the country at the start of each call | Every request. The country counts only for an extractor that NewPipeExtractor builds inside another one, because the others get theirs from RequestSettings. | The lazy setup for the downloader and localization. The country is set on the thread that runs the call, inside the Mutex. |
| The version of YouTube's web client: clientVersion, clientVersionExtracted and hardcodedClientVersionValid ([YoutubeParsingHelper](https://github.com/TeamNewPipe/NewPipeExtractor/blob/v0.27.1/extractor/src/main/java/org/schabi/newpipe/extractor/services/youtube/YoutubeParsingHelper.java#L162-L168)) | [getClientVersion](https://github.com/TeamNewPipe/NewPipeExtractor/blob/v0.27.1/extractor/src/main/java/org/schabi/newpipe/extractor/services/youtube/YoutubeParsingHelper.java#L566-L591), the first time it runs, from sw.js_data. A thread can see clientVersionExtracted set while clientVersion is still null, and then send no version. | Every request of the web client, whose body [prepareDesktopJsonBuilder](https://github.com/TeamNewPipe/NewPipeExtractor/blob/v0.27.1/extractor/src/main/java/org/schabi/newpipe/extractor/services/youtube/YoutubeParsingHelper.java#L1049-L1076) builds and whose headers [getClientInfoHeaders](https://github.com/TeamNewPipe/NewPipeExtractor/blob/v0.27.1/extractor/src/main/java/org/schabi/newpipe/extractor/services/youtube/YoutubeParsingHelper.java#L1125-L1130) builds: search, a channel's page and tabs, the video page's other data, and the [web player request](https://github.com/TeamNewPipe/NewPipeExtractor/blob/v0.27.1/extractor/src/main/java/org/schabi/newpipe/extractor/services/youtube/YoutubeStreamHelper.java#L41-L71). So every function but reading a subtitle track's lines, which calls no NewPipeExtractor code that touches this state. | The Mutex |
| YouTube's player code and what it decodes: the player code, the signature timestamp, the decoding functions, a HashMap of decoded parameters, and the errors it keeps ([YoutubeJavaScriptPlayerManager](https://github.com/TeamNewPipe/NewPipeExtractor/blob/v0.27.1/extractor/src/main/java/org/schabi/newpipe/extractor/services/youtube/YoutubeJavaScriptPlayerManager.java#L33-L49)) | getSignatureTimestamp, deobfuscateSignature and getUrlWithThrottlingParameterDeobfuscated | Stream links only. [buildAndAddItagInfoToList](https://github.com/TeamNewPipe/NewPipeExtractor/blob/v0.27.1/extractor/src/main/java/org/schabi/newpipe/extractor/services/youtube/extractors/YoutubeStreamExtractor.java#L1240-L1272) calls the last two for each format, from getAudioStreams, getVideoStreams and getVideoOnlyStreams. getSignatureTimestamp has no caller in v0.27.1. The player code is fetched only for a link with a cipher or the `n` parameter, and no link of the visionOS client had either in the check. | The Mutex |
| The cookie consent flag and the random generator for playback nonces ([YoutubeParsingHelper](https://github.com/TeamNewPipe/NewPipeExtractor/blob/v0.27.1/extractor/src/main/java/org/schabi/newpipe/extractor/services/youtube/YoutubeParsingHelper.java#L181-L200)), and the interpreter that runs the player code instead of Rhino when an app sets one ([JavaScript](https://github.com/TeamNewPipe/NewPipeExtractor/blob/v0.27.1/extractor/src/main/java/org/schabi/newpipe/extractor/utils/JavaScript.java#L11)) | Setters that Dewfall never calls | Every video fetch reads the generator, every web request reads the consent flag, and every run of the player code reads the interpreter | Nothing to do: Dewfall never writes them, and the generator is a java.util.Random, which is thread-safe |

The table leaves out static fields that NewPipeExtractor fills when a class loads and only reads afterwards, such as the patterns that read relative times like "3 weeks ago" ([PatternMap](https://github.com/TeamNewPipe/NewPipeExtractor/blob/v0.27.1/timeago-parser/src/main/java/org/schabi/newpipe/extractor/timeago/PatternMap.java#L12-L95)). They need nothing, because the JVM makes what a class's initializer wrote visible to every thread that uses the class ([JLS 12.4.2](https://docs.oracle.com/javase/specs/jls/se17/html/jls-12.html#jls-12.4.2)).

### What each function sends

Each function calls NewPipeExtractor's extractors directly, never StreamInfo.getInfo or the other Info.getInfo helpers. Those read every part of the answer, and StreamInfo.getInfo fails a video whose formats it can't read, even when only its details are needed.

| Function | What it calls in NewPipeExtractor | Requests |
|---|---|---|
| Search | A blank query returns empty lists and sends nothing. Otherwise the search extractor for the query, with no filter, then its first page. Playlists in the results are left out. SearchExtractor.NothingFoundException means a Success with empty lists. | One |
| Video details | The stream extractor for the video, then only its details: the title, the channel's link, name and avatars, the description, the length, the publish time, the thumbnails and the stream type | Five, or two for a video that YouTube says can't be played or that never existed |
| A channel's latest uploads | The channel extractor for the channel's ID, then the tab extractors of its Videos and Live tabs | Two for most channels, one for a channel with no Live tab, three to five for an age-restricted channel, and one more for each step when YouTube sends the channel on to another. See [A channel's latest uploads](#a-channels-latest-uploads). |
| Stream links | The same stream extractor as video details, then only its audio formats, video formats, video-only formats and HLS manifest | Five, as for video details. If a link needs decoding, NewPipeExtractor also fetches YouTube's player code, once per process. |
| Subtitle tracks | The same stream extractor as video details, then only its subtitle tracks in TTML | Five, as for video details, or two for a video that can't be played |
| Subtitle lines | No extractor. NewPipeDownloader sends a GET for the track's link. | One |

Video details, stream links and subtitle tracks each fetch the video's page, about 0.5 to 0.7 MB, and the layer keeps no cache. An overnight job that needs a video's stream links and its subtitles sends eleven requests, five for each page and one for the lines, and sixteen with the video's details too. That is fine on Wi-Fi while charging, for the few videos of a night. Phase 7's plan for downloads decides whether the layer should offer one call that returns what a job needs from a single fetch of the page.

The first call in each process also fetches www.youtube.com/sw.js_data, as [Network access](#network-access) describes.

### How Hilt provides the layer

NewPipeYouTube in `:core:youtube` is one of the layer's two public classes. It builds the HTTP client, NewPipeDownloader and NewPipeCalls, and implements the five interfaces by delegating to one internal class for each. It also builds the other public class, MediaRequests, the player's client for googlevideo.com. YouTubeModule in `:core:data` is the Hilt module that provides them:

- One NewPipeYouTube for the whole app, in SingletonComponent with @Singleton, built with the app's Logger and Dispatchers.IO. Hilt's bindings are unscoped unless they name a scope ([Component scopes](https://developer.android.com/training/dependency-injection/hilt-android#component-scopes)). NewPipeExtractor's setup is global, so one instance keeps one downloader and one HTTP client for the whole app.
- Each of the five interfaces, bound to that instance with @Binds ([Inject interface instances with @Binds](https://developer.android.com/training/dependency-injection/hilt-android#inject-interfaces)).
- The player's two OkHttp Call.Factory providers, from that instance's MediaRequests. `@ProgressiveMediaCalls` isn't scoped, so each `Provider.get()` makes a new factory, whose requests number from 0. `@LiveMediaCalls` is a singleton. They expose only OkHttp's type, so `:core:playback` never sees `:core:youtube`.

Hilt builds NewPipeYouTube the first time something asks for one of the interfaces, not when the app starts. Repositories in `:core:data` take the interfaces they need in their constructors. Feature modules don't depend on `:core:youtube`, so they reach YouTube only through those repositories. The one exception is the debug screen, which calls the layer's functions themselves. See [The YouTube layer's debug screen](#the-youtube-layers-debug-screen).

### Mapping NewPipeExtractor's exceptions

youTubeErrorOf in `:core:youtube` turns every exception into one of the categories in [Error handling](#error-handling), for #35's functions and for #148's. It checks a subclass before its parent. The table names the exceptions of NewPipeExtractor v0.27.1.

NewPipeExtractor wraps a failed request in ParsingException in some places, such as when it fetches YouTube's player code ([YoutubeJavaScriptExtractor](https://github.com/TeamNewPipe/NewPipeExtractor/blob/v0.27.1/extractor/src/main/java/org/schabi/newpipe/extractor/services/youtube/YoutubeJavaScriptExtractor.java#L160-L161)). So for an exception whose own type names no category, the causes count, up to 10 of them.

| Thrown | Category |
|---|---|
| CancellationException, and the InterruptedIOException that NewPipeExtractor passes on when the caller is cancelled | CancellationException, never mapped. NewPipeCalls checks for cancellation before it maps anything. |
| HostNotAllowedException, which the layer's HTTP code throws for a request that isn't HTTPS to one of YouTube's hosts | BROKEN, checked before IOException, which it extends. Only a NewPipeExtractor update can ask for another host, and that host has to be checked first. |
| Any other IOException, from the layer's own HTTP code | NETWORK |
| SignInConfirmNotBotException, and ReCaptchaException, which the layer's HTTP code throws for HTTP 429, a redirect to Google's captcha page, or HTTP 403 with an HTML body, which is Google's block page | BLOCKED |
| PrivateContentException | PRIVATE |
| AgeRestrictedContentException | AGE_RESTRICTED. NewPipeExtractor v0.27.1 throws it only for the Android client's wording. The visionOS client asks the viewer to sign in to confirm their age, which reaches the generic ContentNotAvailableException, so an age-restricted video is UNAVAILABLE for now. The layer doesn't read the wording itself. |
| GeographicRestrictionException, UnsupportedContentInCountryException | COUNTRY_BLOCKED |
| PaidContentException, YoutubeMusicPremiumContentException | MEMBERS_OR_PAID |
| AccountTerminatedException | CHANNEL_CLOSED |
| SubtitleLinkRefusedException, which the layer throws when YouTube answers a subtitle link with 403, 404 or 410 | UNAVAILABLE: the link no longer works, so the caller lists the tracks again |
| YouTubeServerErrorException, which the layer throws when YouTube answers a subtitle link with a server error | NETWORK, as an IOException |
| NotSubtitlesException, which the layer throws for a subtitle answer that isn't a TTML file or has another status | BROKEN |
| Any other ContentNotAvailableException, and ContentNotSupportedException | UNAVAILABLE |
| SearchExtractor.NothingFoundException | Not an error: search returns a Success with empty lists |
| ParsingException, ExtractionException and their other subclasses, such as HttpResponseException, which v0.27.0 added and only its SoundCloud code throws, and any other RuntimeException | The category of its first cause that is a HostNotAllowedException (BROKEN), a ReCaptchaException or SignInConfirmNotBotException (BLOCKED), or another IOException (NETWORK). BROKEN if no cause is. |

Errors, such as OutOfMemoryError, aren't caught.

### What NewPipeExtractor v0.27 changed

NewPipeExtractor [v0.27.0](https://github.com/TeamNewPipe/NewPipeExtractor/releases/tag/v0.27.0), released on November 7, 2026, and [v0.27.1](https://github.com/TeamNewPipe/NewPipeExtractor/releases/tag/v0.27.1), released on November 10, changed what the layer reads. The contract and the models stayed the same.

- Formats, the HLS manifest, subtitle tracks and the live flag come only from the visionOS client, because the Android and iOS clients are gone. In the check on November 18, 2026, the visionOS client gave a video's sound and picture only as separate formats, so StreamLinks had no format that includes audio. [Stream links](#stream-links) describes what a player does instead.
- The DASH manifest is gone, and setPoTokenProvider does nothing until NewPipeExtractor supports SABR, YouTube's newer way of streaming. SABR could replace single-file format links, which would change StreamLinks.
- Description is now a record with a Type enum. Subtitle tracks gain a name, and audio formats can be marked as dubbed automatically. Neither of those is in the models yet.
- NewPipeExtractor reads the web client's version from sw.js_data, a JSON answer, instead of sw.js. Its saved copies need a masking rule of their own, as [testing.md](testing.md#privacy) describes.
- An age-restricted video is UNAVAILABLE, as [Mapping NewPipeExtractor's exceptions](#mapping-newpipeextractors-exceptions) explains, and a video that never existed is UNAVAILABLE instead of BROKEN.

On November 18, 2026, NewPipeExtractor's dev branch was 27 commits past v0.27.1.

### The YouTube layer's debug screen

The debug screen calls each function of the YouTube layer on a phone and shows what came back. It is for checking the layer by hand: after a NewPipeExtractor update, when the daily check or a user reports a breakage, and on Android versions that tests on the computer don't cover. [testing.md](testing.md#checking-the-youtube-layer-on-a-phone) says how to use it. Only debug builds contain it.

**What it shows.** It has one section for each of the five interfaces, with an input field, a Run button that turns into Cancel while the call runs, and the call's outcome. The Subtitles section lists a video's tracks, and then reads the lines of the track that the user picks.

| Section | Input it starts with | What it shows when the call works |
|---|---|---|
| Search | nasa, the saved set SEARCH_NASA | How many channels and videos, how many videos of each kind, each channel's name and avatar count, and each video's title, channel, kind, length, publish time and thumbnail count |
| Video details | aqz-KE-bpKQ, Big Buck Bunny, the saved set VIDEO_BIG_BUCK_BUNNY | The title, the channel's name and ID, the length, the publish time, whether it is live, the description's length and line count, and how many thumbnails and channel avatars |
| Latest uploads | UCLA_DiR1FfKNvjuUpBHmylQ, NASA's channel, the saved set UPLOADS_NASA | The channel's name and ID, whether YouTube sent the channel on to another one, how many videos of each kind, and every video in the order the layer returned |
| Stream links | aqz-KE-bpKQ | How many audio and video formats, how many have sound, how many of each MIME type, whether there is an HLS manifest, when the links expire, and each format's itag, type, codec, size, frame rate, bitrate and length in bytes |
| Subtitles | R6MlUcmOul8, Tears of Steel, the saved set SUBTITLES_TEARS_OF_STEEL | How many tracks are written and how many auto-generated, and each track's number, language tag and kind, in YouTube's order, with a Read lines button. For the track read: how many lines, the first start, the latest end, how many lines have more than one row, how many overlap the next line, and the first 20 lines with their times and text |

- Publish times are ISO 8601 in UTC, marked when they are approximate. Lengths are Kotlin's Duration text, such as 10m 35s. A value that YouTube didn't give shows as unknown.
- A failed call shows its YouTubeError category, such as NETWORK. The layer throws only CancellationException, so there is no exception message to show.
- The screen shows text only, and no URL. The summaries that the reducer builds from each answer have no field for one. A subtitle track shows as its number in the listing, never as its link. Stream links carry the phone's IP address and other details of its network, as [testing.md](testing.md#privacy) lists, and ADR 0007 counts every URL as user data. For the same reason, the screen has no way to copy a link. Whether a link plays is the live check's job, and later the player's.
- A video or channel ID is trimmed, then has to parse as a VideoId or ChannelId. If it doesn't, the field says so and nothing is sent. Search text is sent as typed.

**How it opens.** YouTubeDebugActivity in `:feature:youtube-debug-ui` hosts the screen, so the app needs no navigation for it. The module's manifest gives the activity a launcher entry of its own, labeled "Dewfall debug". Library manifests are merged only into the builds that include the library ([Merge multiple manifest files](https://developer.android.com/build/manage-manifests#merge-manifests)), so only debug builds show the entry. The activity is exported, because the launcher and adb's shell can only start an exported activity, and it reads nothing from the intent that starts it. It has a task affinity of its own, so its entry never just brings the app's main task to the front. It takes its theme from the application, calls enableEdgeToEdge, and draws inside DewfallTheme. The screen's background fills the window behind the system bars, and its content scrolls inside the safe drawing area, so no text or control sits under the system bars, the display cutout or the keyboard.

```bash
adb shell am start -n io.github.dewfallapp/io.github.dewfallapp.feature.youtubedebug.ui.YouTubeDebugActivity
```

**How the calls run.** YouTubeDebugViewModel extends MviViewModel. Hilt builds it with the five interfaces of the layer, which YouTubeModule provides. The activity gets it through `by viewModels()`, which builds a @HiltViewModel in an @AndroidEntryPoint activity ([Hilt and Jetpack](https://developer.android.com/training/dependency-injection/hilt-jetpack#viewmodels)).

- Each section runs at most one call. Run stops the section's call, if one is running, then starts a new one with the input as it is now. Cancel stops it.
- An answer counts only while its section is running the same request. The reducer drops any other answer, such as one that arrives after a cancel.
- The Subtitles section reads one track's lines at a time, after its listing worked. Read lines stops the lines call that is running, if any, then starts one for the picked track. A new Run of the listing clears the lines and stops their call. The ViewModel keeps the listed tracks, links included, in memory only, never in the state, to pass the picked one to `subtitleLines`. It replaces them in the same step as it hands the listing to the reducer, so a number always picks the track on screen.
- Calls in different sections run at the same time, and the layer's Mutex makes them wait for each other ([How a call runs](#how-a-call-runs)). Reading a track's lines is the one call that doesn't wait, because it calls no NewPipeExtractor code that keeps state.
- Leaving the screen clears the ViewModel, which cancels every call. A rotation keeps the ViewModel, so calls keep running. After process death, each input field gets its text back, because it saves its own text, and every section goes back to idle.
- The screen has no effects, so its effect type is Nothing. Everything it shows has to survive a rotation, so it is all state.
- The input fields are state-based text fields. Each one holds its own text and reports every change as an intent, as Google's [migration guide](https://developer.android.com/develop/ui/compose/text/migrate-state-based#conforming-approach) describes.

**How release builds leave it out.** `:app` adds `:feature:youtube-debug-ui` with debugImplementation. So the release build has none of its classes, resources or manifest entries, and none of `:feature:youtube-debug-logic`. The screen lives in feature modules, not in `:app`'s src/debug folder, because detekt reads only src/main and src/test, and because ADR 0006 puts reducers in plain Kotlin/JVM modules. [testing.md](testing.md#checking-that-release-builds-leave-it-out) has the commands that check the release APK. [Build settings](#build-settings) explains how both builds keep the shared libraries at the same versions.

**Why it sees the YouTube layer.** `:feature:youtube-debug-ui` depends on `:core:youtube` directly, to call the layer's own functions. It is the only feature module that may. Every real feature reaches YouTube through repositories in `:core:data`. NewPipeExtractor stays hidden from it too, because `:core:youtube` depends on NewPipeExtractor as an implementation dependency.

## Data flow

### Reading from YouTube

This is how a screen reads from YouTube, through a repository in `:core:data`. The Search YouTube screen with YouTubeSearchRepository, the video screen with VideoDetailsRepository, and the player with StreamLinksRepository, do it. The player isn't a ViewModel, so step 1 runs in PlaybackService's scope instead, and step 3 turns the result into the item to play. Feature modules can't see `:core:youtube`, because `:core:data` depends on it as an implementation dependency. Feature modules see the models, `YouTubeResult` and `YouTubeError`, because those live in `:core:model`. The debug screen is the one feature module that sees `:core:youtube` too.

```mermaid
sequenceDiagram
    participant VM as YouTubeSearchViewModel
    participant Repo as YouTubeSearchRepository in :core:data
    participant YT as YouTube layer
    participant R as YouTubeSearchReducer
    VM->>Repo: search(query)
    Repo->>YT: SearchRemoteDataSource.search(query)
    YT-->>Repo: YouTubeResult
    Repo-->>VM: YouTubeResult
    VM->>R: reduce(state, SearchFinished(request, result))
    R-->>VM: Next(state with results or the error)
```

1. The ViewModel's runSideEffects calls the repository from viewModelScope. The call is main-safe, so the ViewModel doesn't switch threads.
2. The repository calls the YouTube layer, and returns its result, or a result of its own built from it. YouTubeSearchRepository, VideoDetailsRepository and StreamLinksRepository return the layer's result unchanged. Each is an interface, so a ViewModel's tests fake it without seeing `:core:youtube`.
3. The ViewModel dispatches the result as an intent, such as SearchFinished. The reducer turns a Success into content and a Failure into an error state.

The rest of this section, such as how data moves through the database and background jobs, comes with Phase 5.

## The MVI contract

Every screen uses MVI, as [ADR 0002](adr/0002-mvi-with-playback-state-outside.md) decides. `:core:mvi` holds the base types that every screen builds on. A small example screen, a counter, lives in core/mvi/src/test/ and shows the whole pattern with its tests.

### The types

| Type | What it is |
|---|---|
| MviState | A screen's state. One immutable class per screen, usually a data class with val properties and read-only collections. After the initial state, only the screen's reducer makes new ones. |
| MviIntent | Something that happens to the screen: what the user does, or the result of work the ViewModel ran. A screen's intents form a sealed interface. Results are named in the past tense, such as Loaded. Player commands, such as play, pause and seek, aren't intents: they go straight to the player. See [The player](#the-player). |
| MviEffect | A one-time instruction to the UI that starts in the ViewModel, such as a message after a load, or a screen that opens only after work finishes. A screen's effects form a sealed interface. |
| Reducer | A pure function from the old state and an intent to a Next: the new state and any effects, in order. It runs no coroutines, I/O, clock, randomness or shared mutable state. |
| PendingEffect | An effect that the UI hasn't handled yet, with an id that is unique within its ViewModel. |
| MviViewModel | The base class of every screen's ViewModel. It holds the state and the pending effects, and runs every intent through the reducer. |

A feature's State, Intent, Effect and reducer go in its `-logic` module. Its ViewModel extends MviViewModel and goes in its `-ui` module. The base type names start with Mvi, so they don't clash with Compose's State or Android's Intent.

An action that starts in the UI, changes no state and needs no business logic, such as opening or closing a screen, skips intents and effects. The UI calls the action its caller gave it, such as `onOpenVideo`, and `:app`'s navigation handles it, as Android's [UI events guidance](https://developer.android.com/topic/architecture/ui-layer/events) describes. [ADR 0002](adr/0002-mvi-with-playback-state-outside.md) records this.

### How an intent flows

```mermaid
sequenceDiagram
    participant UI as Screen
    participant VM as MviViewModel
    participant R as Reducer
    participant D as Repository
    UI->>VM: dispatch(Load)
    VM->>R: reduce(state, Load)
    R-->>VM: Next(state with loading shown)
    VM-->>UI: state
    VM->>D: runSideEffects(Load) starts the load
    D-->>VM: the loaded data
    VM->>R: reduce(state, Loaded)
    R-->>VM: Next(new state, effects)
    VM-->>UI: state and pending effects
```

1. The screen calls dispatch with an intent, such as Load.
2. dispatch runs the reducer. The new state replaces the old one in `state`, and the reducer's effects join the end of `pendingEffects`.
3. dispatch then calls the ViewModel's runSideEffects. It starts the work that the intent needs in viewModelScope, such as a load through `:core:data`. Intents that need no work do nothing there.
4. When the work finishes, the ViewModel calls dispatch again with the result, such as Loaded. The result goes through the reducer like any other intent.

So every change to the state comes from the reducer, and the reducer's unit tests cover most of the screen's logic. The ViewModel's tests cover which work starts for which intent. dispatch runs on the main thread: the UI calls it there, and viewModelScope runs on Dispatchers.Main.immediate.

### Effects

Android's [UI events guidance](https://developer.android.com/topic/architecture/ui-layer/events) says that events from a ViewModel should become UI state, because a channel or stream doesn't guarantee that the UI receives and handles each event when the ViewModel outlives the UI. So MviViewModel holds effects as state. Each effect waits in `pendingEffects`, a StateFlow next to `state`, until the UI says it handled it.

- The UI handles the first pending effect, then calls effectHandled with its id. That removes only that effect, and the next one becomes first, so effects are handled in order.
- An effect stays pending until then. If the UI stops before it handles the effect, or its handler is cancelled halfway, such as a message that was showing during a rotation, the UI gets the effect again.
- Pending effects survive configuration changes and the UI stopping, because they live in the ViewModel. They don't survive process death. Anything that must survive process death belongs in the screen's state, saved with [SavedStateHandle](https://developer.android.com/topic/libraries/architecture/viewmodel/viewmodel-savedstate).

The UI side comes with the first screen that has effects. The Search YouTube screen has none, so its effect type is Nothing. The effect handling will look like this sketch. It waits until the screen is started, handles the effect, then reports it. It is keyed by the effect's id, so a recomposition doesn't handle the same effect twice.

```kotlin
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.lifecycle.Lifecycle
// Not androidx.compose.ui.platform.LocalLifecycleOwner, which is deprecated.
import androidx.lifecycle.compose.LocalLifecycleOwner
import io.github.dewfallapp.core.mvi.MviEffect
import io.github.dewfallapp.core.mvi.PendingEffect
import kotlinx.coroutines.flow.first

@Composable
fun <E : MviEffect> HandleEffects(
    pendingEffects: List<PendingEffect<E>>,
    onEffectHandled: (id: Long) -> Unit,
    handle: suspend (E) -> Unit,
) {
    val first = pendingEffects.firstOrNull()
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val currentHandle by rememberUpdatedState(handle)
    val currentOnEffectHandled by rememberUpdatedState(onEffectHandled)
    if (first != null) {
        LaunchedEffect(first.id, lifecycle) {
            lifecycle.currentStateFlow.first { it.isAtLeast(Lifecycle.State.STARTED) }
            currentHandle(first.effect)
            currentOnEffectHandled(first.id)
        }
    }
}
```

A screen calls it with `viewModel.pendingEffects.collectAsStateWithLifecycle().value` and `viewModel::effectHandled`. The YouTube layer's debug screen has no effects, so it doesn't call it.

### Side effects and dispatchers

- MviViewModel takes no dispatcher. viewModelScope runs on Dispatchers.Main.immediate, and tests replace Main with Dispatchers.setMain, as Google's [coroutine testing guide](https://developer.android.com/kotlin/coroutines/test#setting-main-dispatcher) does.
- Code that moves work to another thread, such as a repository, receives a CoroutineDispatcher in its constructor, so a test can pass a TestDispatcher. Its suspend functions are safe to call from the main thread, so the ViewModel calls them from viewModelScope without switching threads ([coroutines best practices](https://developer.android.com/kotlin/coroutines/coroutines-best-practices)).
- The reducer never runs side effects. Only runSideEffects starts work.
- Playback state never goes into screen state. A screen that shows playback reads two sources: its own state, and the player, through its MediaController, with media3-ui-compose's state holders or as Flows, at the rate the visible UI needs. Player commands go straight to the player, not through intents. See [ADR 0002](adr/0002-mvi-with-playback-state-outside.md) and its note of November 20, 2026.

### Why the ViewModel base is in a plain Kotlin/JVM module

[ADR 0006](adr/0006-platform-and-android-versions.md) puts the MVI base types in a plain Kotlin/JVM module, and MviViewModel can live there too. Lifecycle publishes a JVM build of lifecycle-viewmodel, lifecycle-viewmodel-desktop, and `:core:mvi` compiles against it. Android modules get the Android build, lifecycle-viewmodel-android, instead. Both builds come from the same source files, and their ViewModel and viewModelScope APIs are the same, so classes compiled against one run with the other. The Android build only adds AndroidViewModel and helpers for Android's View tree, which `:core:mvi` doesn't use.

`:core:mvi` exposes Lifecycle and kotlinx.coroutines as api dependencies, because MviViewModel extends ViewModel and exposes StateFlows. So `-logic` modules can see ViewModel too. They don't use it, because ViewModels go in `-ui` modules.

On the computer's JVM there's no Main dispatcher, and viewModelScope then quietly falls back to EmptyCoroutineContext, so its coroutines run on Dispatchers.Default's threads ([Lifecycle 2.8.0](https://developer.android.com/jetpack/androidx/releases/lifecycle#2.8.0)). That's why every ViewModel test sets Main first, as [testing.md](testing.md#running-tests-locally) shows.

## Navigation

`:app` owns the back stack and the screens on it, with [Navigation 3](https://developer.android.com/guide/navigation/navigation-3). Only `:app` sees it. Each feature whose screen sits on the back stack exposes one public composable from its `-ui` module, which takes plain values and actions: `YouTubeSearch(onOpenVideo)` and `PlayerRoute(videoId, onClose)`. So features never depend on each other or on the navigation library, and replacing it would change only `:app`.

- **The keys.** Each screen has a key: SearchKey, an object, and PlayerKey, which holds the video's VideoId and an entry ID. They are `@Serializable` NavKeys, so `rememberNavBackStack(SearchKey)` saves the back stack across configuration changes and process death ([Save your back stack](https://developer.android.com/guide/navigation/navigation-3/save-state)). VideoId lives in `:core:model`, which has no serialization plugin, so `:app`'s VideoIdSerializer saves its text and restores it through VideoId's constructor, which checks it.
- **The ID in saved state.** The back stack is saved in the state Android keeps for a process it ends in the background, so the video ID of an open video screen is in it, on the device only, as the search field's text is. PlayerKey's toString hides the ID, as VideoId's does ([ADR 0007](adr/0007-how-the-app-logs.md)).
- **The content key.** Navigation 3 tells entries apart by their content key, and NavEntry's toString prints it. Since Navigation 3 1.1.6, its default is made from the key's toString and its class ([release notes](https://developer.android.com/jetpack/androidx/releases/navigation3)), and in 1.2.1 it is the string `"$key:${key::class}"` ([NavEntry.kt at 1.2.1](https://android.googlesource.com/platform/frameworks/support/+/65822252146d26e872acf03dc11bda43f11efa3b/navigation3/navigation3-runtime/src/commonMain/kotlin/androidx/navigation3/runtime/NavEntry.kt)). PlayerKey's toString hides the ID, so that default would be the same for every video. So PlayerKey's entry ID, a random UUID's text made when the key is created, is its content key. It keeps two entries apart, even two for the same video, and holds no ID.
- **The decorators.** NavDisplay gets `rememberSaveableStateHolderNavEntryDecorator()` and `rememberViewModelStoreNavEntryDecorator()`, so each entry keeps its own saved state, such as the search field's text, and its own ViewModels, which are cleared when the entry leaves the back stack. From Lifecycle 2.11.0 the decorator passes the activity's default factory, which is Hilt's, on to each entry, so `viewModel()` works for a @HiltViewModel inside an entry ([Lifecycle 2.11.0](https://developer.android.com/jetpack/androidx/releases/lifecycle)).
- **A ViewModel per video.** PlayerViewModel takes its video through Hilt's [assisted injection](https://dagger.dev/hilt/view-model.html): PlayerRoute adds a creation callback to the entry's default creation extras, which calls the ViewModel's assisted factory. The factory takes PlayerArgs, an ordinary class that holds the VideoId, because Kotlin renames any function that takes a value class ([inline classes](https://kotlinlang.org/docs/inline-classes.html)), and Dagger can't inject value classes ([google/dagger#2930](https://github.com/google/dagger/issues/2930)).
- **Opening and closing.** Three small functions of the back stack, in BackStack.kt, change it. openVideo adds a PlayerKey only while SearchKey is on top, so a second tap while the video screen opens opens nothing. close removes that entry's own key, if it is still there. goBack, which NavDisplay calls for the system's back gesture, removes the top key only while there is more than one. So a double tap on close can't empty the back stack, which would crash NavDisplay: it requires at least one entry, and throws "NavDisplay backstack cannot be empty" otherwise ([NavDisplay.kt at 1.2.1](https://android.googlesource.com/platform/frameworks/support/+/65822252146d26e872acf03dc11bda43f11efa3b/navigation3/navigation3-ui/src/commonMain/kotlin/androidx/navigation3/ui/NavDisplay.kt)).

The three facts about Navigation 3 above, NavEntry's toString, the default content key and the empty back stack, were checked on November 19, 2026, in version 1.2.1, the one the app uses, both in its source at the release commit and in its compiled classes.

## The theme

`:core:designsystem` holds Dewfall's Material 3 theme. It comes from the Dewfall design system in [design/claude-design/design-system/](design/claude-design/design-system/README.md): the colors, type scale, shapes and spacing in its tokens.json, with the code adapted from its compose-theme.md. That export stays an unchanged copy of the design system, so the theme's code copies the values instead of reading them.

- `DewfallTheme` is the entry point. `:app` draws the whole UI inside it. Screens read colors, text styles and shapes from `MaterialTheme`, never fixed values, and take spacing from `DewfallSpacing`.
- On Android 12 and later, the theme takes its colors from the wallpaper, because the [design brief](design/brief.md) turns dynamic color on by default. Older phones get Dewfall's own light and dark palettes. The brief has no setting to turn dynamic color off, so the app has none. The `dynamicColor` parameter is there for previews.
- The `darkTheme` parameter follows the system's dark theme. The fullscreen player will pass true, because it uses the dark scheme in light mode too.
- The palettes, the type scale and the shapes are internal to the module, so screens reach them only through `MaterialTheme`. The palettes include the fixed roles, which Material 3's scheme builders accept.
- Material 3 is an api dependency of the module, because screens use its components and `MaterialTheme`.
- The design system's opacity tokens aren't in the code. Material 3's components already use the same state layer and disabled opacities, and its modal bottom sheet puts `scrim` behind itself at 32%, as scrim-modal asks. Compose dialogs are different: they dim the screen through Android's dialog window, by an amount that comes from the window theme, not from `scrim`. The first dialog needs a check against the design system's 32%. duration-scrim comes with the first component that uses it.

**Manrope.** All text is set in Manrope, the design system's typeface. The module's res/font/manrope.ttf is the variable font Manrope[wght].ttf, version 4.505, from [googlefonts/manrope](https://github.com/googlefonts/manrope) at commit 468c0db. The copy in Google Fonts' own repository, ofl/manrope/ in [google/fonts](https://github.com/google/fonts), is the same file, with the same Git blob hash. One file holds every weight from 200 to 800, at 164,700 bytes. The static files for the three weights the type scale uses, manrope-regular.ttf, manrope-medium.ttf and manrope-semibold.ttf in fonts/ttf/ at the same commit, take 432,912 bytes together. Variable fonts need Android 8.0, below the app's minimum ([ADR 0006](adr/0006-platform-and-android-versions.md)).

- The type scale uses weights 400, 500 and 600. The font family also has 700 and 800, from the same file, for Android's Bold text setting on Android 12 and later. With the setting on, Compose adds the setting's adjustment, 300 on the Android 16 emulator, to each style's weight. Body text then draws at 700, and headlines and titles at 800, the heaviest weight in the family. So all text gets bolder, and body text stays lighter than titles. Without 700 and 800, every style would draw at 600. The family has a Font for each of these weights, so text never gets a synthetic bold.
- The file's own default weight is 200. Compose's `Font(resId, weight)` sets the weight axis of a res/font font only after the font loads, and on the Android 10 and Android 16 emulators that had no effect: every weight drew ExtraLight. So each weight has a font XML in res/font, such as manrope_regular.xml, whose `android:fontVariationSettings` sets the axis as the font loads. The font family points at those files. compose-theme.md's Type.kt passes the weight to `Font(R.font.manrope, weight, variationSettings)`, which goes through the same step after loading, so the theme replaces it with the font XMLs.
- Manrope covers Latin, Cyrillic, Greek and Vietnamese. Video titles, channel names, descriptions and subtitles can be in any language. Characters Manrope lacks, such as Japanese, Arabic, Hindi or Thai, fall back one by one to the phone's own fonts, so a mixed line uses two typefaces.

**The font's license.** Manrope is under the SIL Open Font License 1.1, with no Reserved Font Name. Every copy of the font must ship with its copyright notice and the license. The module's assets/licenses/Manrope-OFL.txt is the license file from the font's repository, and it starts with the copyright notice. It sits in assets/ because res/font/ accepts only font files, and because resource shrinking never removes assets, so every APK contains it. The app has no screen that shows it yet. An open-source licenses section in Settings will (#139).

**Checking the theme against the tokens.** DesignTokensTest, in the module's unit tests, reads tokens.json and compares every color, text style, shape and spacing value with the theme. It also fails when a token group gains or loses a token. Copy a token edit into the theme by hand, and the test passes once both agree. The module's build declares tokens.json as an input of its test tasks, so Gradle runs the tests again when the tokens change, instead of reusing a cached result. kotlinx-serialization-json parses the file in the tests only, and doesn't ship. ManropeTest checks that each of Manrope's fonts loads one of the font XMLs, and that each XML sets the weight it declares. The font folder is a test input too.

**Previews.** ThemePreviews.kt shows every color role except scrim and surfaceTint, which carry no text, with the roles drawn on it, the type scale with a line in several scripts, and the shapes. The color previews come in Dewfall's palette, in light and dark, and in dynamic color. Android Studio shows them. To see one on a phone or an emulator, install the debug build and open the preview through Compose's PreviewActivity, which the preview tooling adds to debug builds only:

```bash
adb shell am start -n io.github.dewfallapp/androidx.compose.ui.tooling.PreviewActivity --es composable io.github.dewfallapp.core.designsystem.theme.ThemePreviewsKt.AccentColorsPreview
```

The other previews are SurfaceColorsPreview, AccentColorsDynamicPreview, SurfaceColorsDynamicPreview, TypeScalePreview and ShapeScalePreview. On a phone, the phone's own dark theme and wallpaper decide what they show. The previews without Dynamic in their name keep Dewfall's palette on Android 12 and later too.

## The player

Videos play in `:core:playback`, an Android library that no screen owns, so #43's background play and #44's mini player reuse it. It depends on `:core:data`, for the stream links and the YouTube layer's media requests, and on `:core:model` and `:core:logging`, never on `:core:youtube`. The facts about Media3 below were checked on November 19 and 20, 2026, in Media3 1.11.1, the version the app uses, in its source at tag 1.11.1 and in its compiled classes.

```mermaid
sequenceDiagram
    participant Route as PlayerRoute
    participant Conn as PlaybackConnection
    participant Svc as PlaybackService
    participant Repo as StreamLinksRepository
    participant MR as MediaRequests
    Route->>Conn: prepare(videoId)
    Conn->>Svc: startService, then a MediaController on the session's token
    Conn->>Svc: setMediaItem(playback ID only)
    Svc->>Repo: streamLinks(videoId)
    Repo-->>Svc: StreamLinks
    Svc->>Svc: chooseFormats, then the item with the chosen links
    Svc->>MR: the video's bytes, through OkHttpDataSource
    Route->>Conn: player, read with media3-ui-compose's state holders
```

### The service and the connection

- **PlaybackService** is a MediaSessionService with one ExoPlayer. It adds its session with `addSession` in `onCreate`, and publishes it to PlaybackSessions, a singleton inside the process. It posts no notification until #43: `onUpdateNotificationAsync` returns an immediate future without calling super, so it never runs in the foreground. Its manifest entry isn't exported and has no intent filter, so no other app can bind to it, and on Android 10 and 11 Media3 gives the platform session no media button PendingIntent that would start it as a foreground service after the process ends ([MediaSessionLegacyStub at 1.11.1](https://github.com/androidx/media/blob/1.11.1/libraries/session/src/main/java/androidx/media3/session/MediaSessionLegacyStub.java)). Media3 registers a runtime media button receiver instead, which dies with the process. The manifest also removes media3-session's BluetoothValidationActivity, because there is no Bluetooth control until #43.
- **PlaybackConnection** is how screens reach the player. Its one implementation starts the service with `startService` while a screen is visible, and builds one MediaController for the application's context from the session's own token, because a SessionToken that names the service needs the intent filter ([SessionToken.java at 1.11.1](https://github.com/androidx/media/blob/1.11.1/libraries/session/src/main/java/androidx/media3/session/SessionToken.java)). It keeps the controller while the session lives, so a configuration change finds it as it was.
- **When the service stops.** Android stops an app's started services a few minutes after the app leaves the screen, unless something binds to them ([background service limits](https://developer.android.com/about/versions/oreo/background#services)). Then `onDestroy` releases the session and the player and clears PlaybackSessions and PlaybackItems. `onStartCommand` returns START_NOT_STICKY, so Android never restarts an empty service. The connection drops its controller when it disconnects, and the next `prepare` starts the service again.
- **Who may connect.** The session's `onConnectAsync` accepts only controllers from the app's own package. Its `onMediaButtonEvent` returns true for every event, which stops Media3 from handling it, so headset and Bluetooth buttons do nothing until #43.

### Opaque playback IDs

Media3 copies an item's `mediaId` into Android's media session, which apps with notification access and Bluetooth devices can read. So the session never names the video:

1. `prepare(videoId)` keeps the VideoId in PlaybackItems under a new playback ID, a random UUID's text, and sets an item with only that ID.
2. The session's `onSetMediaItems` looks the VideoId up, fetches the stream links through StreamLinksRepository, chooses the formats, and returns an item with the same ID, the placeholder URI `dewfall-playback:<playback ID>`, and the chosen links in its tag. A video that can't play leaves the player empty, and its failure goes to the connection.
3. The session's player is SessionPlayer, a ForwardingSimpleBasePlayer around the ExoPlayer. It shows the session each item reduced to its playback ID, with no link, no metadata and no manifest, and every error with the same code, a constant message and no cause. ExoPlayer's renderer errors append the format's codec, size, bitrate and language to their message ([ExoPlaybackException at 1.11.1](https://github.com/androidx/media/blob/1.11.1/libraries/exoplayer/src/main/java/androidx/media3/exoplayer/ExoPlaybackException.java)), and Media3 copies the message into the platform session.

So other apps see that a video plays, its length and its position, never its ID, title or link. PlaybackItems drops an entry when another item takes its place, when the items are cleared, when its playback can't start, and when its screen stops it, even if its item never became the player's. So in memory the process holds the ID and the links of one video at a time, and none after the screen closes.

### Choosing the formats

FormatChoice, in `:core:model`, is a pure function, so its tests run on the JVM:

- **The limit.** The shorter side of the picture is at most 480 pixels on a metered network and 1080 on an unmetered one, so a portrait video gets the same quality. NetworkCheck reads NET_CAPABILITY_NOT_METERED of the active network when playback starts. A network that is only temporarily not metered, or none, counts as metered. A refresh of expired links chooses again from the new links with the same limit, so the quality doesn't change within one playback.
- **Video.** Among the formats within the limit that the phone decodes, those decoded in hardware first, then the largest shorter side, then the lowest bitrate. With none within the limit, the decodable one with the smallest shorter side. A format with no codec counts as not decodable.
- **Audio.** The original track, or every format when none has a track, or else the first track. The lowest bitrate on a metered network, the highest otherwise.
- **The phone's decoders.** Decoders makes the same check as ExoPlayer's renderers, so the choice and the renderer agree: the codec's sample MIME type, `MediaCodecUtil.getDecoderInfos`, and `MediaCodecInfo.isFormatSupported` with the format's codec, size and frame rate. `hardwareAccelerated` says whether a supporting decoder works in hardware.
- **Live.** Links with no formats and an HLS manifest count as live. When ExoPlayer knows the manifest's variants, the service limits the video group with a TrackSelectionOverride to the variants within the limit, or the smallest one, and ExoPlayer adapts among them.

### Media sources

PlaybackSources is the ExoPlayer's MediaSource.Factory. It reads the item's tag:

- A video format and an audio format become a `MergingMediaSource(adjustPeriodTimeOffsets = true, clipDurations = true, ...)` of two ProgressiveMediaSources, as Media3's [media sources guide](https://developer.android.com/media/media3/exoplayer/media-sources) advises.
- A format with sound and picture becomes one ProgressiveMediaSource, and a live stream an HlsMediaSource.
- Each progressive source gets a new `@ProgressiveMediaCalls` Call.Factory from `:core:data`, so each numbers its requests from 0, and HLS gets the `@LiveMediaCalls` one. Both come from the YouTube layer's MediaRequests, as [Network access](#network-access) describes. OkHttpDataSource takes the Call.Factory.
- An item without the tag fails with MediaRequestRefused, without a request, and never falls back to a default HTTP data source.

### Errors

- **Expired links.** On HTTP 403 or 410, the service fetches new links once, puts the new item in place of the old one, goes back to the old position, or a live stream's live edge, and plays if it was playing. The refresh steps run over RefreshTarget, a small interface, so a JVM test checks them with a fake. The limit resets once the item is ready again, so a second refusal in a row stands.
- **Retries.** PlaybackLoadErrorPolicy retries neither 403, 410 nor a MediaRequestRefused, so the refresh starts at once and a refused request is never sent again. Other errors keep DefaultLoadErrorHandlingPolicy's retries.
- **Categories.** PlaybackFailure, in `:core:model`, names why a video can't play. The service maps the real PlaybackException, which only it sees, and a failed resolution maps the YouTubeError. MediaRequestRefused counts as YOUTUBE wherever it is in the cause chain, because OkHttpDataSource wraps it as an IOException around an ExecutionException ([OkHttpDataSource at 1.11.1](https://github.com/androidx/media/blob/1.11.1/libraries/datasource_okhttp/src/main/java/androidx/media3/datasource/okhttp/OkHttpDataSource.java)), and Media3 reports any IOException from a data source as a network error. A network that failed or timed out is NETWORK, a decoder that can't decode the format or fails while decoding is NOT_DECODABLE, and anything else is OTHER, including a decoder that fails to start, such as while another app holds it, or that the system took back. Each decision is a pure function of plain inputs, with its own tests.
- **The screen.** The video area shows the failure in the details' words, plus "This video can't play on this phone." for NOT_DECODABLE and "This video stopped playing." for OTHER. "Try again" shows for NETWORK, YOUTUBE and OTHER, and prepares the video again from the last position. When the details failed with the same category, the video area shows no second message, and the details' "Try again" retries both.
- **Logs.** Media3's own logging is off, set with `Log.setLogLevel(Log.LOG_LEVEL_OFF)` in PlaybackService's `onCreate` and PlaybackConnection's constructor, before either makes another Media3 call, because Media3's error lines append each throwable's message ([Log.java at 1.11.1](https://github.com/androidx/media/blob/1.11.1/libraries/common/src/main/java/androidx/media3/common/util/Log.java)). PlaybackLog writes one WARN line for each failure, with the category as a state field and Media3's error code as a count field.

### Sound and the screen

- The player follows Android's [audio focus guidance](https://developer.android.com/media/optimize/audio-focus): it plays with USAGE_MEDIA and AUDIO_CONTENT_TYPE_MOVIE and handles audio focus, so a call or another app's sound pauses it or lowers it, and unplugging headphones pauses it.
- ExoPlayer is built with `setUsePlatformDiagnostics(false)`, so it sends nothing to Android's media metrics, which Google may collect ([ExoPlayer.Builder.setUsePlatformDiagnostics](https://developer.android.com/reference/androidx/media3/exoplayer/ExoPlayer.Builder#setUsePlatformDiagnostics(boolean))).

### The video area

The video screen's VideoArea, in `:feature:player-ui`, sits above the details, as the "Player" frames show:

- The picture is media3-ui-compose's ContentFrame on a SurfaceView, in a 16:9 box that takes at most 45% of the window's height. Compose's clip doesn't round a SurfaceView, so the box draws its rounded corners over it in the screen's background role, `surface`. The shutter before the first frame is `surfaceContainerHighest`, not ContentFrame's black.
- `Modifier.keepScreenOn()` applies while the player plays, so the screen doesn't time out and stop the activity, which would pause the video.
- The seek bar ticks as many times as it has pixels, with `rememberProgressStateWithTickCount`, and the times tick once a second, with `rememberProgressStateWithTickInterval`. The play and pause button uses `rememberPlayPauseButtonState`. At a video's end it offers to play it again from the start. A live stream says "Live" in place of the seek bar and the times.
- `rememberPlaybackStatus`, in the same module, listens with media3-common-ktx's `Player.listen` for loading, live and the end. It stays there until #44 needs to share it, so `:core:playback` has no Compose.

**Starting, pausing and stopping.** PlayerRoute keeps three things in its saved state: whether playback started, the playback ID, and the position. Each start saves the position it starts from, and ON_STOP saves the player's position, unless the player is idle after a failure, when its position may not be where the video was. The pure function `startAction` in `:feature:player-logic` decides from them:

- The first opening plays from the start, or a live stream from its live edge.
- After a configuration change, the playback goes on.
- After the system ended the process, or stopped the service, the playback ID is unknown, so the screen waits at the saved position with the play button and no picture, and fetches nothing until the user presses play.

ON_STOP pauses through the connection, unless the activity is only changing configurations, and a start that still waits for the connection then starts paused. "Try again", and play after the session was gone, go on from the player's position, or the saved one when the player has no item. PlayerViewModel keeps the latest playback ID, from the first start, a play after the session was gone, or "Try again", and its `onCleared` asks the connection to stop. Navigation 3 clears it only after the screen's exit animation, so the connection stops only if the player's item is still that ID.

**Player commands skip intents.** Play, pause, seek and the playback starts go from the UI straight to the player, not through PlayerIntent, and the screen reads the playback state through the controller's state holders, never in PlayerState. A tap would only pass through the reducer unchanged. [ADR 0002](adr/0002-mvi-with-playback-state-outside.md)'s note of November 20, 2026 records it.

### How the playback tests run

`:core:playback`'s unit tests run on the JVM against AGP's stub android.jar, with no Robolectric. Media3's PlaybackException, HttpDataSourceException, MediaItem with a URI and LoadEventInfo call Android code that the stub throws on. So each decision is a pure function of plain inputs, and a thin adapter turns Media3's objects into them: the failure category, the cause-chain walk, the retry decision, the session's error, the source plan, the stop rule and the metered rule. The refresh steps run over a fake RefreshTarget. The adapters only read fields and build Media3 objects, and the hand checks in [testing.md](testing.md#checking-the-player-by-hand) run them.

## Downloads and cleanup

TODO

## Subtitle search

TODO

## Database tables

There are no tables yet. Phase 5 adds the first ones (#48), and Phase 8 adds the full-text index for subtitle search (#71). The database lives in `:core:database`, and Room writes its schemas to core/database/schemas/, as [Build settings](#build-settings) describes.

**The SQLite driver.** Room 3 reaches SQLite through a driver. Dewfall uses AndroidSQLiteDriver, which uses the SQLite that comes with the phone's Android version. Its library is already one of Room's dependencies, so it adds nothing to the APK. The database builder sets it with setDriver, as Google's [Room guide](https://developer.android.com/training/data-storage/room) does. The other driver, BundledSQLiteDriver, ships its own SQLite 3.50.1, with FTS5, compiled into a native library for each processor type (ABI). In androidx.sqlite:sqlite-bundled-android 2.7.1, each library is 1.1 to 1.3 MB, and the four ABIs add 4.9 MB in all. SQLite is public domain, which isn't on the license list in AGENTS.md, so that driver needs the maintainer's approval. Phase 8 looks at the choice again, once the full-text index's needs are known.

**Schema changes.** The first public release is the first GitHub release, including a pre-release, published from a version tag. A tag made only to test the release workflow, with no published release, doesn't count, and nightly APKs don't count. Until then, the database stays at version 1, and a schema change needs no migration. Room rewrites the version 1 schema file, and the pull request commits it. A phone that ran an older build can't open the database after that, because Room finds a different schema under the same version number and throws an exception. Clear the app's data after installing the new build. Destructive migrations stay banned. From the first public release on, every schema change raises the version and comes with a migration and a migration test.

## Error handling

When code logs an error, the logger writes only the class names and stack frames of the error and its causes, never their messages, because libraries put URLs and IDs in them. Code that catches a library's exceptions at its module's edge, such as the YouTube layer turning NewPipeExtractor's exceptions into Dewfall's own errors, also keeps them away from Android's crash handler, which logs every message. See [Logging](#logging).

### Errors from YouTube

The YouTube layer reports every problem as a `YouTubeResult.Failure` that holds a `YouTubeError`, and never throws one. [ADR 0008](adr/0008-youtube-layer-contract.md) explains why. The categories are the ones a screen or a background job can act on. The last column is a plan. The OpenSpec changes for the screens in Phases 4 and 5 and the background jobs in Phase 7 decide what the app does.

| Category | What it means | What the app plans to do |
|---|---|---|
| NETWORK | YouTube couldn't be reached | Search YouTube says "YouTube can't be reached. Check your connection." with "Try again". A screen whose message depends on the connection, such as the inbox's "Downloaded videos still play", shows "offline" when the phone has no connection, and "YouTube not loading" otherwise. A job tries again later. |
| BLOCKED | YouTube refused to answer, such as with a captcha or Google's block page | "YouTube not loading". A job waits longer before it tries again. |
| BROKEN | The layer couldn't read YouTube's answer, or it refused to send a request for a host that it doesn't allow. Either way, only an update fixes it. | "YouTube not loading". An update fixes it, as [breakage.md](breakage.md) describes. |
| PRIVATE, AGE_RESTRICTED, COUNTRY_BLOCKED, MEMBERS_OR_PAID, CHANNEL_CLOSED, UNAVAILABLE | One video or channel can't be shown | A message on that video or channel. The rest of the screen works. |

When reading a subtitle track's lines fails with UNAVAILABLE, the track's link no longer works, such as after it expired, and the caller lists the tracks again for a link that works, as [Subtitles](#subtitles) describes.

A video that never existed is UNAVAILABLE, as [ADR 0008](adr/0008-youtube-layer-contract.md) promises, and the saved set VIDEO_NEVER_EXISTED tests it (#155). With NewPipeExtractor v0.26.5 it was BROKEN, because YouTube answered the Android client's player request with an empty object. An age-restricted video is UNAVAILABLE too, not AGE_RESTRICTED, until NewPipeExtractor recognises the visionOS client's wording, as [Mapping NewPipeExtractor's exceptions](#mapping-newpipeextractors-exceptions) says.

The design's two error states for the inbox are "Inbox, offline" and "Inbox, YouTube not loading" ([screens.md](design/screens.md#empty-error-and-offline-states)). Only Android code can tell whether the phone has a connection, so the code that shows an error checks the connection when it gets NETWORK. Search YouTube doesn't check: both cases ask the same of the user, to check the connection and try again, and there are no downloads to point to. The first screen whose message depends on the connection adds the check and decides where it lives. The ACCESS_NETWORK_STATE permission it needs is already in the manifest: Media3 and the player's metered check brought it with #42.

An error reaches a screen this way, as [Reading from YouTube](#reading-from-youtube) shows:

1. The YouTube layer catches the library's exception at its edge, logs it with the Logger, and returns a Failure with the category.
2. The repository in `:core:data` passes the result on. `YouTubeResult` and `YouTubeError` live in `:core:model`, which `:core:data` exposes, so feature modules can see them without seeing `:core:youtube`.
3. The ViewModel dispatches the result as an intent, and the reducer turns the category into state.
4. The screen picks the text for the category from string resources, because plain Kotlin/JVM modules can't hold text.

`YouTubeError` is an enum, and Failure holds nothing but the enum, so neither one's toString contains user data. Neither is a Throwable, so neither can reach Android's crash handler. VideoId's and ChannelId's toString and error messages leave out the ID.

TODO: errors from the database, downloads and background jobs, which come with the phases that add them.
