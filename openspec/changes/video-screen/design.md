# Design

## Context

See proposal.md for why. The pieces this change builds on:

- The YouTube layer's `VideoDetailsRemoteDataSource.videoDetails(videoId)` returns `YouTubeResult<VideoDetails>`: the title, the channel's ID, name and avatars, the description as plain text, the length, the publish time, the thumbnails and whether it is live. It throws only CancellationException. Its KDoc says it can't read a premiere or live stream that hasn't started, and fails those with UNAVAILABLE. The video's page gives an exact publish time, which the saved set VIDEO_BIG_BUCK_BUNNY's replay test checks.
- Search YouTube is the app's only screen. DewfallApp draws it inside DewfallTheme, and its ViewModel comes from `viewModel()` through the activity's Hilt factory. Its rows have no action. Its design.md planned that #41 adds the navigation, and that the length and time-ago functions move to a shared place when a second screen needs them.
- `timeAgo` in `:feature:youtube-search-logic` maps zero calendar days to Yesterday. The maintainer subagent's check of PR #177 asked that #41 settle a same-day label.
- AGENTS.md: each feature has a `-logic` and a `-ui` module, `:app` handles navigation between features, and features never depend on each other.
- ADR 0004's living-spec areas include `player`. The brief calls this screen "Player: portrait with title, channel and description".

## Goals / Non-Goals

**Goals:**

- The app's navigation: a back stack that survives configuration changes and process death, with one ViewModel per screen on it.
- The video screen as the next feature built on the MVI pattern, with its own repository.

**Non-Goals:**

- Playback, the video area and its controls. #42 adds the Media3 player, #43 background audio and #44 the mini and fullscreen players.
- Opening a video from anywhere but Search YouTube. The inbox and channel pages come with Phase 5.
- Images, which #176 decides.
- Tappable links in the description. The text is selectable, so a link can be copied. Opening links is a later change if users miss it.
- Screenshot tests. Phase 4's own task, #45, adds them for every screen.

## Decisions

### Modules named player, and the player spec area

`:feature:player-logic` and `:feature:player-ui`. The screen is the "Player" in the design and the brief, and #42 to #44 add playback to it, so the name fits what it becomes. The capability is `player`, one of ADR 0004's areas, so no ADR changes. The service that plays media in #42 doesn't depend on any screen, so it goes in a core module of its own, not in these.

### Navigation 3 in :app

`:app` owns the back stack and the screens on it, with Navigation 3:

- The keys are `@Serializable` objects and classes that implement NavKey: a `SearchKey` object and a `PlayerKey` that holds the VideoId. `rememberNavBackStack(SearchKey)` saves the back stack across configuration changes and process death, which needs the keys to be serializable ([Save your back stack](https://developer.android.com/guide/navigation/navigation-3/save-state)). That needs the Kotlin serialization plugin in `:app`.
- NavDisplay shows the top of the back stack, and pops it on the system's back gesture. Its entry decorators are `rememberSaveableStateHolderNavEntryDecorator()` and `rememberViewModelStoreNavEntryDecorator()`, so each entry keeps its own saved state and its own ViewModels, which are cleared when it leaves the back stack ([Scoping ViewModel instances to NavEntry objects](https://developer.android.com/guide/navigation/navigation-3/save-state)).
- Each feature's `-ui` module exposes one public composable that takes what it needs as plain values and actions: `YouTubeSearch(onOpenVideo: (VideoId) -> Unit)` and `PlayerRoute(videoId: VideoId, onClose: () -> Unit)`, a name that won't clash with Media3's `Player` in #42. Only `:app` sees Navigation 3, so the features don't depend on it or on each other.

Alternatives:

- **Navigation Compose 2.x.** Rejected, because Navigation 3 was made for Compose: the back stack is a plain list that `:app` owns and changes directly, instead of state inside a NavController.
- **A back stack written by hand in `:app`.** Rejected, because saving it, handling back and predictive back, and giving each screen its own ViewModels would all be Dewfall's own code.
- **The back stack in a ViewModel.** It survives configuration changes but not process death, unless Dewfall saves and restores it by hand, as the same page says.

**Why no ADR.** Only `:app` knows the library, and the features take plain actions, so replacing it later changes one module.

**The video ID in the key.** `PlayerKey` holds a VideoId. VideoId lives in `:core:model`, a plain Kotlin/JVM module without the serialization plugin, so `:app` has a small `KSerializer<VideoId>` that saves the ID's text and restores it through VideoId's constructor, which checks it. The key's toString hides the ID, as VideoId's does (ADR 0007).

**The content key holds no ID.** The content key keeps each entry's saved state and ViewModels apart, and Navigation 3 prints it: NavEntry's toString includes it, and SaveableStateHolder's "Key ... was used multiple times" exception names it, which would put it in logcat's crash buffer, a gap that ADR 0007 names. Since Navigation 3 1.1.6 the default content key is made from the key's toString and its class ([release notes](https://developer.android.com/jetpack/androidx/releases/navigation3)), so with the hidden toString every PlayerKey would get the same one. So PlayerKey also holds an entry ID: a random UUID's text, made when `:app` creates the key and saved with it, and the entry's content key is that entry ID. It tells two entries apart, even two for the same video, and says nothing about the video.

### A ViewModel per video, with the ID through Hilt

PlayerViewModel is a `@HiltViewModel(assistedFactory = ...)` whose constructor takes the VideoId as an `@Assisted` parameter, as Hilt's [assisted injection](https://dagger.dev/hilt/view-model.html) describes. The route gets it with `viewModel(extras = owner.defaultViewModelCreationExtras.withCreationCallback<Factory> { it.create(videoId) })`. `withCreationCallback` comes with Hilt itself.

This works because Lifecycle 2.11.0's entry decorator passes the activity's default factory and creation extras on to each entry's ViewModelStoreOwner ([Lifecycle 2.11.0 release notes](https://developer.android.com/jetpack/androidx/releases/lifecycle)), and the activity's default factory is Hilt's. Search YouTube's `viewModel()` keeps working inside its entry for the same reason.

Alternatives:

- **`hiltViewModel(creationCallback = ...)` from `androidx.hilt:hilt-lifecycle-viewmodel-compose`,** as Navigation 3's recipe for Hilt does. Rejected for now, because it is one more artifact for the same call. If the decorator turns out not to pass Hilt's factory on, this is the fallback, and it needs the maintainer's approval.
- **An Open(videoId) intent from the screen.** Rejected, because the ViewModel would exist for a while without knowing its video, and every intent would have to check for that.

### Opening a video is a plain action, not an effect

A tap on a row calls `onOpenVideo` from the UI directly. It changes no state and needs no business logic, so Android's [UI events guidance](https://developer.android.com/topic/architecture/ui-layer/events) lets the UI pass it to its caller without the ViewModel. The close button calls `onClose` the same way.

That narrows the MVI contract. ADR 0002 says intents are "what the user does", and docs/architecture.md gives "opening another screen" as its example of an effect. So this change adds a dated "Changed in #41" note to ADR 0002, which the maintainer approved with the change on November 19, 2026: an action that starts in the UI, changes no state and needs no business logic, such as opening or closing a screen, goes straight to `:app`'s navigation, with no intent. Effects stay for instructions that start in the ViewModel, such as a message after a load, or a screen that opens only after work finishes. docs/architecture.md's MviEffect row gets that example instead.

`:app` adds a PlayerKey only while SearchKey is on top. A second tap that lands while the video screen opens finds PlayerKey on top, so it opens nothing. The check is a small function of the back stack, so a unit test covers it.

- **Alternative:** `dropUnlessResumed`, which Android's UI events guidance shows for navigation clicks. Rejected, because it depends on each entry's lifecycle state during NavDisplay's transition, which a unit test can't show.

### A thin repository in :core:data

`VideoDetailsRepository` is an interface in `:core:data` with one suspend function, `videoDetails(videoId): YouTubeResult<VideoDetails>`. Its implementation calls the layer and returns the result unchanged, and Hilt binds it, as for search, for the same reasons.

### The screen's state

The reducer in `-logic` owns a state with the number of the latest request and one sealed value:

| Details area | When |
|---|---|
| Loading(number) | Before the first answer, and after "Try again" |
| Shown(page) | The details arrived |
| Failed(number, error) | The call failed, with its YouTubeError |

Intents are Load, which the ViewModel dispatches once when it is created, TryAgain, which counts only while the area is Failed with an error that offers it, and DetailsFinished(number, result), which counts only while the area is Loading with the same number. The ViewModel runs one call at a time, as Search YouTube's does.

The page holds what the screen shows: the title, the channel's name and initial, the description, the publish time and whether it is live. "Live" is VideoDetails.isLive, which is also true for a stream that has just ended while YouTube still serves it as live, so "Live now" shows for that too, as the spec says. The reducer drops the thumbnails and avatars, which no image shows yet, the channel's ID, which nothing uses yet, and the length, which the player shows in #42.

**The channel's initial** is the name's first user-perceived character. The reducer finds it with `java.text.BreakIterator.getCharacterInstance(Locale.ROOT)`, which has no Android imports, and Locale.ROOT keeps the reducer from reading the default locale. The emblem keeps the letter's case, as the design shows it. The reducer skips leading whitespace, and a name with nothing else gets no initial and shows the emblem empty. The layer refuses a blank name, so that is only a guard.

On a phone, `java.text.BreakIterator` is backed by ICU, so a flag or an emoji with a skin tone stays whole. The unit tests run on JDK 17, whose BreakIterator follows Unicode's grapheme rules only from JDK 20 ([JDK-8291660](https://github.com/openjdk/jdk/pull/9991)), and splits most emoji sequences into code points. So the unit tests cover only the cases both agree on: a letter, a lowercase letter, a letter with a combining accent, and an emoji of one code point outside the Basic Multilingual Plane. Task 7.2 checks a flag and a family emoji on the emulators by hand.

- **Alternative:** find the initial in `-ui` with `android.icu.text.BreakIterator`. Rejected, because it moves logic out of the reducer, and the `-ui` module's unit tests would then depend on how Robolectric provides ICU.

### The time ago moves to :core:model

`TimeAgo` and `timeAgo` move from `:feature:youtube-search-logic` to `:core:model`, with their tests, next to PublishedTime. They read a PublishedTime back the way YouTube writes it, which is knowledge about that model, and they have no text and no Android imports, so ADR 0006 puts them in a plain Kotlin/JVM module. Both screens' `-ui` modules call them, and each has its own strings, because the phrasing differs: "7h ago" in a row, "Published 7 hours ago" on the video screen. LengthLabel stays in search, because the video screen shows no length yet.

`TimeAgo` gains `Today`, for a date that is zero calendar days before today. That happens for an estimated time on today's date, and for an exact time a day or more ago on the day the clocks go back, when a day has 25 hours. Rows say "Today", and the video screen says "Published today".

Alternatives:

- **`:core:ui`.** Rejected for now, because it is an Android library, and ADR 0006 puts code that needs nothing from Android in a plain Kotlin/JVM module. `:core:ui` still comes when the inbox shares the video row in Phase 5. docs/architecture.md's sentence that "the code that formats a duration" goes in `:core:ui` then says that the text does, and the counting stays in `:core:model`.
- **A new plain Kotlin/JVM module for display units.** Rejected, because a module for two functions costs more than it gives.

### The layout

The screen follows the "Player" frames, top to bottom, in one scrolling column inside the safe drawing area:

- A top row with the close button: the design's down arrow, with the content description "Close video". #44 turns it into "Minimize player" when the mini player exists. The glyph is Material Symbols Rounded's `keyboard_arrow_down`, from the same commit as the three that #40 added, in `:core:designsystem`.
- The title in `title-large`, marked as a heading.
- The publish line in `body-medium`, `on-surface-variant`, as the frames set it at 14/20.
- The channel in a `surface-container-low` box with `shape-large` corners: a 36dp emblem, as in the frames, that grows with the font scale, as Search YouTube's thumbnail box does, so the initial fits at a large font, with the initial in `title-small`, `on-tertiary-container`, on `tertiary-container`, and a 1dp `outline-variant` ring, as the design system's ChannelHeader describes, then the name in `title-small`.
- The description in `body-large`, inside a SelectionContainer.

The column is at most 640dp wide and centered, and its background fills the window, as the brief's rule for large screens says. `ReadableWidth` moves from `:feature:youtube-search-ui` to `:core:designsystem`, so both screens use the one value. Loading shows a progress indicator with "Loading video" under the close button. A failure shows the search screen's banner style: an error glyph, the message, and "Try again" when it applies.

### Failure messages

| YouTubeError | Message | Try again |
|---|---|---|
| NETWORK | YouTube can't be reached. Check your connection. | Yes |
| BLOCKED, BROKEN | YouTube isn't loading right now. | Yes |
| PRIVATE | This video is private. | No |
| AGE_RESTRICTED | This video is age-restricted. YouTube shows it only to signed-in adults, and Dewfall doesn't use accounts. | No |
| COUNTRY_BLOCKED | This video isn't available in your country. | No |
| MEMBERS_OR_PAID | This video is only for the channel's members, or has to be bought. | No |
| CHANNEL_CLOSED | This video's channel was closed. | No |
| UNAVAILABLE | This video isn't available. It may have been removed, or it may not have started yet. | No |

The first three are the categories in which nothing from YouTube can load right now, as YouTubeError's KDoc groups them. Trying again can help for NETWORK and BLOCKED. Only an app update fixes BROKEN, but a user can't tell it from the other two, it costs nothing to offer, and Search YouTube offers it for every failure. The others are about this one video, and asking again gives the same answer. The video screen has its own strings, because its messages are about one video.

## Risks / Trade-offs

- [The entry decorator may not pass Hilt's factory on as the release notes say] → Tasks 6.1 and 7.2 check it early, by opening two different videos one after the other and by opening Search YouTube's entry. If it fails, the fallback is androidx.hilt's `hiltViewModel`, which goes to the maintainer first.
- [Navigation 3 1.2.1 was built against Compose 1.10, and the app's BOM gives Compose 1.12.1] → AndroidX keeps binary compatibility within a major version, and the build and emulator checks run with the versions the app ships.
- [An upcoming premiere opens a failure] → Its message says it may not have started yet. Telling premieres apart needs the row's kind, which is a later change if users meet it often.
- [Rows and the video screen read the clock once per answer] → A screen left open for hours shows an old time ago, as Search YouTube already accepts.
- [The back stack is saved in the state Android keeps for a process it ends] → It holds the video ID, on the device only, like the search text that #40 saves. The spec says so.

## Migration Plan

None. Nothing is stored, and the app has no release yet.
