# Design

## Context

See proposal.md for why this change is needed. The pieces it builds on:

- `:app`'s `AndroidManifest.xml` applies `@style/Theme.Dewfall` to `<application>`. Both `MainActivity` and the debug build's `YouTubeDebugActivity` (in `:feature:youtube-debug-ui`) inherit that theme.
- `app/src/main/res/values/themes.xml` defines `Theme.Dewfall` with parent `android:Theme.Material.Light.NoActionBar`, and `values-night/themes.xml` with parent `android:Theme.Material.NoActionBar`. Checked in the Android 37 SDK's `themes_material.xml` and `colors_material.xml` on November 21, 2026:
  - Light `Theme.Material.Light.NoActionBar` sets `windowBackground` to `#FAFAFA` (`material_grey_50`), `statusBarColor` to `?attr/colorPrimaryDark` (`#757575`, `material_grey_600`), `navigationBarColor` to `@color/navigation_bar_default`, and leaves `windowLightStatusBar` and `windowLightNavigationBar` false.
  - Dark `Theme.Material.NoActionBar` sets `windowBackground` to `#303030` (`material_grey_850`) and `statusBarColor` to `#000000`.
- `DewfallTheme` in `:core:designsystem` sets the Compose color scheme to:
  - `DewfallLightColors` (`surface = #F5FBF2`) and `DewfallDarkColors` (`surface = #101411`) below Android 12 (API 29–30). Both values come from `docs/design/claude-design/design-system/tokens.json` and are checked by `DesignTokensTest`.
  - `dynamicLightColorScheme(context)` and `dynamicDarkColorScheme(context)` on Android 12 and later (API 31–37), always on in the app (no dynamic-color toggle exists).
- `MainActivity` calls `enableEdgeToEdge()` before `setContent { DewfallApp() }`. Every screen draws a full-size `Surface` using `MaterialTheme.colorScheme.surface`.
- ADR 0006 sets `minSdk = 29`, `targetSdk = 37`, and `compileSdk = 37`.
- Dewfall has no app icon until [#120](https://github.com/dewfallapp/dewfall/issues/120) (Phase 9). `AndroidManifest.xml` suppresses `MissingApplicationIcon` until then.

## Goals / Non-Goals

**Goals:**

- Eliminate the grey/dark-grey flash before Compose draws the first frame across Android 10 through Android 17, in light, dark, and dynamic color.
- Match the Android 12+ system splash screen background to the same dynamic surface color.
- Guard the static light and dark launch colors against drifting from `tokens.json`, and verify every qualifier folder's theme attributes with fast JVM unit tests.

**Non-Goals:**

- Custom splash screen icon, icon background color, animated vector drawable, or branding image (waits for `#120` in Phase 9).
- Suspending the first frame with `setKeepOnScreenCondition` or custom exit animations (nothing loads at startup in Phase 4).
- Backporting an icon splash screen to Android 10–11 (see proposal Question 2).
- Changes to `:core:designsystem`'s Compose theme, `:core:playback`, `:feature:player-logic`, or `:feature:player-ui`.

## Decisions

### A spec area for app launch (`app-launch`)

ADR 0004 lists nine living-spec areas (`player`, `subscriptions`, `inbox`, `downloads`, `background-sync`, `cleanup`, `transcript-search`, `data-export`, and `youtube-search`). None covers the app's launch window or splash screen.

- **Choice:** Add `app-launch` as a living-spec area once the maintainer approves proposal Question 1, with a dated note in ADR 0004 and matching updates to `openspec/config.yaml` and `docs/roadmap.md`'s Phase 0 guide (following `#40`'s precedent for `youtube-search`).
- **Alternative:** Add a `theme` area. Rejected because `#26` (the Compose theme) has no living spec and `#138` specifically governs app launch behavior, which `#120` (app icon) can extend later.

### Platform XML attributes instead of `androidx.core:core-splashscreen`

- **Choice:** Configure the launch window and Android 12+ splash screen using Android platform XML attributes in `:app`'s `res/` folders, adding no new dependency.
  - On Android 12 and later, the platform [SplashScreen API](https://developer.android.com/develop/ui/views/launch/splash-screen) reads `android:windowSplashScreenBackground` directly from the activity's theme before the app process runs any Kotlin code.
  - `Theme.Dewfall` remains a single theme on `<application>`, so `MainActivity`, `YouTubeDebugActivity`, and Compose previews all inherit it with no theme-switching code.
- **Alternative:** Add `androidx.core:core-splashscreen:1.2.0` (Apache License 2.0, released November 5, 2025, checked November 21, 2026 on [Google Maven](https://maven.google.com/web/index.html#androidx.core:core-splashscreen:1.2.0)). Android's [splash screen migration guide](https://developer.android.com/develop/ui/views/launch/splash-screen/migrate) recommends it when an app wants a uniform icon splash screen on Android 11 and lower or needs `setKeepOnScreenCondition`. Rejected for now (and raised in proposal Question 2) because:
  - Dewfall has no app icon until `#120`, so a backported splash screen on Android 10–11 would display the generic Android robot icon instead of a clean surface-colored window.
  - Nothing runs at startup that needs `setKeepOnScreenCondition`.
  - It introduces a shipping dependency plus `Theme.SplashScreen` / `postSplashScreenTheme` / `installSplashScreen()` plumbing on `MainActivity` (which `YouTubeDebugActivity` and `PreviewActivity` would not call).
  - Adopting `core-splashscreen` later in Phase 5 or `#120` is additive and reuses the same `@color/launch_surface` resources.

### Matching `DewfallTheme`'s `surface` color across API levels

Checked on November 21, 2026 in `androidx.compose.material3:material3-android:1.4.0` (`DynamicTonalPalette.android.kt`, the version in Compose BOM 2026.09.00) and in the Android 37 SDK's `public-final.xml` and `colors_dynamic.xml`:

- **API 29–30 (Android 10–11):** `DewfallTheme` uses `DewfallLightColors.surface` (`#F5FBF2`) and `DewfallDarkColors.surface` (`#101411`).
- **API 31–33 (Android 12–13):** `dynamicLightColorScheme(context)` calls `dynamicLightColorScheme31`, which sets `surface = neutralVariant98`, computed in Kotlin as `system_neutral2_600.setLuminance(98f)`. `dynamicDarkColorScheme(context)` calls `dynamicDarkColorScheme31`, which sets `surface = neutralVariant6`, computed as `system_neutral2_600.setLuminance(6f)`. Android 12 and 13 expose only 13 discrete tones (`0`, `10`, `50`, `100`..`900`, `1000`) per palette in `android.R.color`; tones 98 and 6 do not exist as XML resources before API 34. Because SystemUI renders the starting window and splash screen from XML before any app code runs, XML must pick the closest platform resource in the same `system_neutral2` palette:
  - Light: `@android:color/system_neutral2_10` (tone 99, 1 luminance step from tone 98).
  - Dark: `@android:color/system_neutral2_900` (tone 10, 4 luminance steps lighter than tone 6, keeping the wallpaper's hue and chroma).
- **API 34–37 (Android 14–17):** `dynamicLightColorScheme(context)` and `dynamicDarkColorScheme(context)` call `dynamicLightColorScheme34` / `dynamicDarkColorScheme34`, which read `android.R.color.system_surface_light` and `android.R.color.system_surface_dark` directly. Referencing those two resources in XML gives an exact match.

One color resource name, `launch_surface`, resolves across all six configurations:

| Folder | Android versions | Mode | Value | Match with Compose `surface` |
|---|---|---|---|---|
| `values/colors.xml` | API 29–30 | Light | `#F5FBF2` | Exact (`DewfallLightColors.surface`) |
| `values-night/colors.xml` | API 29–30 | Dark | `#101411` | Exact (`DewfallDarkColors.surface`) |
| `values-v31/colors.xml` | API 31–33 | Light | `@android:color/system_neutral2_10` | Closest XML tone (99 vs 98, same `neutral2` palette) |
| `values-night-v31/colors.xml` | API 31–33 | Dark | `@android:color/system_neutral2_900` | Closest XML tone (10 vs 6, same `neutral2` palette) |
| `values-v34/colors.xml` | API 34+ | Light | `@android:color/system_surface_light` | Exact (`dynamicLightColorScheme34`) |
| `values-night-v34/colors.xml` | API 34+ | Dark | `@android:color/system_surface_dark` | Exact (`dynamicDarkColorScheme34`) |

Per Android's [Providing Resources](https://developer.android.com/guide/topics/resources/providing-resources) qualifier precedence (Table 2), `night` has higher precedence than `v<API>`, so `values-night-v31` and `values-night-v34` are the required directory names and override `values-v31` and `values-v34` in dark mode.

- **Alternatives considered for API 31–33:**
  - `system_neutral1_10` / `system_neutral1_900`: chroma 4 instead of chroma 8, so greyer than Compose Material 3 1.4.0's `neutralVariant` (`system_neutral2`) dynamic surfaces.
  - `system_neutral2_1000` for dark: pure `#000000` black (6 luminance steps darker than tone 6, dropping the wallpaper tint).
  - Dewfall's static `#F5FBF2` / `#101411` on API 31–33: would flash green before switching to a non-green wallpaper's dynamic surface.

### Keeping launch colors and themes in `:app`

- **Choice:** Place `colors.xml` and `themes.xml` in `app/src/main/res/`.
- **Alternative:** Put `colors.xml` in `:core:designsystem` so `DesignTokensTest` checks it directly. Rejected because `:core:designsystem` intentionally keeps its palettes internal so screens only read `MaterialTheme.colorScheme` (`docs/architecture.md#the-theme`), and Android library resources are visible to every feature module that depends on `:core:designsystem`. Only `:app` owns window themes.

### Theme structure and system bar attributes

Split `Theme.Dewfall` into a light/dark base style plus an API 31+ style so dark mode and API 31+ do not duplicate attributes:

- `app/src/main/res/values/themes.xml`:
  - `Base.Theme.Dewfall` (parent `android:Theme.Material.Light.NoActionBar`):
    - `android:colorBackground` = `@color/launch_surface`
    - `android:windowBackground` = `@color/launch_surface`
    - `android:colorPrimaryDark` = `@color/launch_surface`
    - `android:windowLightStatusBar` = `true`
    - `android:windowLightNavigationBar` = `true`
  - `Theme.Dewfall` (parent `Base.Theme.Dewfall`)
- `app/src/main/res/values-night/themes.xml`:
  - `Base.Theme.Dewfall` (parent `android:Theme.Material.NoActionBar`):
    - `android:colorBackground` = `@color/launch_surface`
    - `android:windowBackground` = `@color/launch_surface`
    - `android:colorPrimaryDark` = `@color/launch_surface`
    - `android:windowLightStatusBar` = `false`
    - `android:windowLightNavigationBar` = `false`
- `app/src/main/res/values-v31/themes.xml`:
  - `Theme.Dewfall` (parent `Base.Theme.Dewfall`):
    - `android:windowSplashScreenBackground` = `@color/launch_surface`

Why each attribute is included or omitted (verified in Android 37 SDK `attrs.xml`, `themes.xml`, and `themes_material.xml` on November 21, 2026):

- `android:windowBackground` sets the starting window background before Compose draws the first frame.
- `android:colorBackground` is read by `windowBackgroundFallback` (shown in unfilled areas during multi-window resize) and by `ActivityTaskDescription` (Recents' task background).
- `android:windowSplashScreenBackground` sets the Android 12+ system splash screen background explicitly, matching [`android.R.attr#windowSplashScreenBackground`](https://developer.android.com/reference/android/R.attr#windowSplashScreenBackground).
- `android:colorPrimaryDark` (not deprecated) overrides the `#757575` grey (light) and `#000000` black (dark) that `Theme.Material` passes to `statusBarColor` on Android 10–11 preview windows, without referencing deprecated `android:statusBarColor`.
- `android:windowLightStatusBar` and `android:windowLightNavigationBar` (added in API 23 and API 27, both below `minSdk = 29`, neither deprecated) give dark icons on light surfaces and light icons on dark surfaces before `enableEdgeToEdge()` runs.
- `android:statusBarColor` and `android:navigationBarColor` are omitted because Android 15 (API 35) deprecated both and ignores them under mandatory [edge-to-edge](https://developer.android.com/about/versions/15/behavior-changes-15#edge-to-edge), and `AGENTS.md` forbids deprecated APIs in new code. On Android 12–14 the system splash screen fills behind both bars using `windowSplashScreenBackground`, and `MainActivity` calls `enableEdgeToEdge()` in `onCreate()`.

### Verification and tests

- **JVM unit test (`LaunchThemeTest` in `app/src/test/kotlin/io/github/dewfallapp/`):**
  - Follows the same pattern as `DesignTokensTest` and `ManropeTest` in `:core:designsystem`: `app/build.gradle.kts` declares `docs/design/claude-design/design-system/tokens.json`, `app/src/main/res`, and `app/src/main/AndroidManifest.xml` as inputs to `:app`'s `Test` tasks and passes their relative paths via system properties. `:app` already has `testImplementation(libs.kotlinx.serialization.json)`.
  - Verifies:
    - `values/colors.xml` and `values-night/colors.xml` `launch_surface` match `tokens.json`'s `surface.light` and `surface.dark` exactly.
    - `values-v31/colors.xml`, `values-night-v31/colors.xml`, `values-v34/colors.xml`, and `values-night-v34/colors.xml` point to the exact expected `@android:color/system_*` resources.
    - `values/themes.xml`, `values-night/themes.xml`, and `values-v31/themes.xml` declare the expected parents and items, and do not use deprecated `android:statusBarColor` or `android:navigationBarColor`.
    - `app/src/main/AndroidManifest.xml` keeps `android:theme="@style/Theme.Dewfall"` on `<application>` and does not override it on `MainActivity`.
- **Android Lint & build checks:** `./gradlew spotlessCheck detekt lint test assembleDebug` checks API availability (`NewApi` / `UnusedAttribute`) across `values/`, `values-v31/`, and `values-v34/`, plus Dewfall's formatting and lint rules.
- **Hand checks on emulators** (only after asking the user before starting an emulator, since `#43` uses them too):
  - Android 10 (API 29) in light and dark mode: launch window background (`#F5FBF2` / `#101411`), status bar and icons, transition to Search YouTube, and `YouTubeDebugActivity`.
  - Android 14+ (e.g., Android 16 API 36, and Android 12/13 if an image is installed) in light and dark mode with two contrasting wallpapers: splash screen background, seamless transition into Compose's dynamic `surface`, and split-screen resize.

## Risks / Trade-offs

- [API 31–33 dark dynamic surface is tone 10 (`system_neutral2_900`) while Compose draws tone 6 (`neutralVariant6`)] → Closest platform XML resource in the same `neutral2` palette; exact match is impossible in XML before API 34, and the subtle 4-step shift replaces `Theme.Material`'s harsh `#303030` grey flash.
- [Android 10–11 light mode briefly shows `Theme.Material`'s black navigation bar strip on the preview window before `enableEdgeToEdge()` runs] → Lasts only until `onCreate()` runs `enableEdgeToEdge()`; avoids deprecated `android:navigationBarColor` per `AGENTS.md`, and proposal Question 4 asks the maintainer whether to accept it or override `android:navigationBarColor` below API 35.
- [Static hex values `#F5FBF2` and `#101411` in `app/src/main/res/` are copied from `tokens.json`] → `LaunchThemeTest` reads `tokens.json` directly with Gradle test task inputs, so any token edit fails `:app:testDebugUnitTest` until updated.
- [Devices on Android 12+ whose OEM disables wallpaper Monet overlays] → Android's framework still resolves `@android:color/system_neutral2_*` and `@android:color/system_surface_*` to the system's default neutral palette, which is the exact same resource `dynamicLightColorScheme` / `dynamicDarkColorScheme` read, so launch and first frame still match.
- [No backported icon splash screen on Android 10–11 and no `setKeepOnScreenCondition` yet] → Intentional until `#120` adds Dewfall's icon or Phase 5 needs startup state; migrating to `androidx.core:core-splashscreen` later is additive and keeps the same `launch_surface` color resources.
