# Design

## Context

See proposal.md for why this change is needed and for the four decisions the maintainer approved on November 21, 2026. The pieces it builds on:

- `:app`'s `AndroidManifest.xml` applies `@style/Theme.Dewfall` to `<application>`. `MainActivity` (and in debug builds, `YouTubeDebugActivity`) inherits that theme.
- `app/src/main/res/values/themes.xml` defines `Theme.Dewfall` with parent `android:Theme.Material.Light.NoActionBar`, and `values-night/themes.xml` with parent `android:Theme.Material.NoActionBar`. Checked on November 21, 2026 in AOSP's [`android10-release/themes_material.xml`](https://android.googlesource.com/platform/frameworks/base/+/refs/heads/android10-release/core/res/res/values/themes_material.xml), [`android11-release/themes_material.xml`](https://android.googlesource.com/platform/frameworks/base/+/refs/heads/android11-release/core/res/res/values/themes_material.xml), [`main/themes_material.xml`](https://android.googlesource.com/platform/frameworks/base/+/refs/heads/main/core/res/res/values/themes_material.xml), and [`android10-release/colors_material.xml`](https://android.googlesource.com/platform/frameworks/base/+/refs/heads/android10-release/core/res/res/values/colors_material.xml):
  - Both `Theme.Material` and `Theme.Material.Light` set `windowBackground` to `?attr/colorBackground` (lines 160 and 535 in `android10-release`, lines 160 and 533 in `android11-release`, lines 160 and 538 in `main`), and `Theme` sets `windowBackgroundFallback` to `?attr/colorBackground` ([`android10-release/themes.xml`](https://android.googlesource.com/platform/frameworks/base/+/refs/heads/android10-release/core/res/res/values/themes.xml#168)).
  - In light mode, `Theme.Material.Light` sets `colorBackground` to `#FAFAFA` (`material_grey_50`), `statusBarColor` to `?attr/colorPrimaryDark` (`#757575`, `material_grey_600`), `navigationBarColor` to `@color/black` on Android 10 and 11 (lines 554 and 551), and sets neither `windowLightStatusBar` nor `windowLightNavigationBar`.
  - In dark mode, `Theme.Material` sets `colorBackground` to `#303030` (`material_grey_850`), `statusBarColor` to `?attr/colorPrimaryDark` (`#000000`), and `navigationBarColor` to `@color/black` on Android 10 and 11 (lines 179 and 178).
- `DewfallTheme` in `:core:designsystem` sets the Compose color scheme to:
  - `DewfallLightColors` (`surface = #F5FBF2`) and `DewfallDarkColors` (`surface = #101411`) on Android 10 and 11 (API 29–30). Both values come from `docs/design/claude-design/design-system/tokens.json` and are checked by `DesignTokensTest`.
  - `dynamicLightColorScheme(context)` and `dynamicDarkColorScheme(context)` on Android 12 and later (API 31–37), always on in the app (no dynamic-color toggle exists).
- `MainActivity` calls `enableEdgeToEdge()` before `setContent { DewfallApp() }`. Every screen draws a full-size `Surface` using `MaterialTheme.colorScheme.surface`.
- ADR 0006 sets `minSdk = 29`, `targetSdk = 37`, and `compileSdk = 37`.
- Dewfall has no app icon until [#120](https://github.com/dewfallapp/dewfall/issues/120) (Phase 9). `AndroidManifest.xml` suppresses `MissingApplicationIcon` until then.

## Goals / Non-Goals

**Goals:**

- Eliminate the grey/dark-grey flash before Compose draws the first frame across Android 10 through Android 17, in light, dark, and dynamic color.
- Match the Android 12+ system splash screen background to the same dynamic surface color.
- Guard the static light and dark launch colors against drifting from `tokens.json`, keep the manifest wired to `Theme.Dewfall`, and forbid deprecated system bar XML attributes via fast JVM unit tests.

**Non-Goals:**

- Custom splash screen icon, icon background color, animated vector drawable, or branding image (waits for `#120` in Phase 9).
- Suspending the first frame with `setKeepOnScreenCondition` or custom exit animations (nothing loads at startup in Phase 4).
- Backporting an icon splash screen to Android 10–11.
- Changes to `:core:designsystem`'s Compose theme, `:core:playback`, `:feature:player-logic`, or `:feature:player-ui`.

## Decisions

### A spec area for app launch (`app-launch`)

ADR 0004 lists nine living-spec areas (`player`, `subscriptions`, `inbox`, `downloads`, `background-sync`, `cleanup`, `transcript-search`, `data-export`, and `youtube-search`). None covers the app's launch window or splash screen.

- **Choice:** Add `app-launch` to ADR 0004 with a dated note, and add it to `openspec/config.yaml` and `docs/roadmap.md`'s Phase 0 guide, as approved by the maintainer on November 21, 2026 (following `#40`'s precedent for `youtube-search`).
- **Alternative:** Add a `theme` area. Rejected because `#26` has no living spec and `#138` specifically governs app launch behavior, which `#120` (app icon) can extend later.

### Platform XML attributes instead of `androidx.core:core-splashscreen`

- **Choice:** Configure the launch window and Android 12+ splash screen using Android platform XML attributes in `:app`'s `res/` folders, adding no new dependency (approved by the maintainer on November 21, 2026).
  - On Android 12 and later, the platform [SplashScreen API](https://developer.android.com/develop/ui/views/launch/splash-screen) reads [`android:windowSplashScreenBackground`](https://developer.android.com/reference/android/R.attr#windowSplashScreenBackground) directly from the activity's theme before the app process runs any Kotlin code.
  - `Theme.Dewfall` remains a single theme on `<application>`, so `MainActivity` and other activities inherit it with no theme-switching code.
- **Alternative:** Add `androidx.core:core-splashscreen:1.2.0` (Apache License 2.0, on [Google Maven](https://maven.google.com/web/index.html#androidx.core:core-splashscreen:1.2.0), checked November 21, 2026). Android's [splash screen migration guide](https://developer.android.com/develop/ui/views/launch/splash-screen/migrate) recommends it when an app wants a uniform icon splash screen on Android 11 and lower or needs `setKeepOnScreenCondition`. Deferred because Dewfall has no app icon until `#120` and runs no startup work before Compose draws; adopting it later in Phase 5 or `#120` is additive and reuses the same `@color/launch_surface` resources.

### Matching `DewfallTheme`'s `surface` color across API levels

Checked on November 21, 2026 in Material 3 `1.4.0` ([release notes](https://developer.android.com/jetpack/androidx/releases/compose-material3#1.4.0), [Google Maven](https://maven.google.com/web/index.html#androidx.compose.material3:material3-android:1.4.0), the version in Compose BOM `2026.09.00`), in public `androidx-main`'s [`DynamicTonalPalette.android.kt`](https://android.googlesource.com/platform/frameworks/support/+/refs/heads/androidx-main/compose/material3/material3/src/androidMain/kotlin/androidx/compose/material3/DynamicTonalPalette.android.kt), and in [`android.R.color`](https://developer.android.com/reference/android/R.color) / AOSP [`android14-release/public-final.xml`](https://android.googlesource.com/platform/frameworks/base/+/refs/heads/android14-release/core/res/res/values/public-final.xml):

- **API 29–30 (Android 10–11):** `DewfallTheme` uses `DewfallLightColors.surface` (`#F5FBF2`) and `DewfallDarkColors.surface` (`#101411`). Nothing can read Compose's `MaterialTheme` before the first frame draws, so `values/colors.xml` and `values-night/colors.xml` hold those two hex values, and `LaunchThemeTest` ties them to `tokens.json`.
- **API 31–33 (Android 12–13):** Material 3 `1.4.0`'s `dynamicLightColorScheme` and `dynamicDarkColorScheme` compute `surface` in code as tone 98 (`neutralVariant98`) and tone 6 (`neutralVariant6`) from `android.R.color.system_neutral2_600`. Android 12 and 13 expose only 13 discrete tones (`0`, `10`, `50`, `100`..`900`, `1000`) per palette in [`android.R.color`](https://developer.android.com/reference/android/R.color); tones 98 and 6 do not exist as XML resources before API 34. Because SystemUI renders the starting window and splash screen from XML before any app code runs, XML picks the closest platform resources in the same `system_neutral2` palette (approved by the maintainer on November 21, 2026):
  - Light: [`@android:color/system_neutral2_10`](https://developer.android.com/reference/android/R.color#system_neutral2_10) (tone 99, 1 luminance step from tone 98).
  - Dark: [`@android:color/system_neutral2_900`](https://developer.android.com/reference/android/R.color#system_neutral2_900) (tone 10, 4 luminance steps lighter than tone 6, keeping the wallpaper tint).
- **API 34–37 (Android 14–17):** Material 3 `1.4.0`'s `dynamicLightColorScheme` and `dynamicDarkColorScheme` read [`android.R.color.system_surface_light`](https://developer.android.com/reference/android/R.color#system_surface_light) and [`android.R.color.system_surface_dark`](https://developer.android.com/reference/android/R.color#system_surface_dark) directly (public since API 34, lines 3641 and 3684 of `android14-release/public-final.xml`). Referencing those two resources in XML gives an exact match.
- **Future Compose BOM updates:** `androidx-main` changed [`DynamicTonalPalette.android.kt`](https://android.googlesource.com/platform/frameworks/support/+/refs/heads/androidx-main/compose/material3/material3/src/androidMain/kotlin/androidx/compose/material3/DynamicTonalPalette.android.kt) on November 9, 2026 (commit `b58c8e9ec7af`, "Only resolve the tones used by dynamic color schemes on SDKs 31-33"). That commit kept every role's resolved tone unchanged and only refactored how tones are computed, but it shows this code still moves upstream. `docs/architecture.md` will note to check the API 31–33 mapping again whenever a Compose BOM update moves Material 3.

One color resource name, `launch_surface`, resolves across all six configurations:

| Folder | Android versions | Mode | Value | Match with Compose `surface` |
|---|---|---|---|---|
| `values/colors.xml` | API 29–30 | Light | `#F5FBF2` | Exact (`DewfallLightColors.surface`) |
| `values-night/colors.xml` | API 29–30 | Dark | `#101411` | Exact (`DewfallDarkColors.surface`) |
| `values-v31/colors.xml` | API 31–33 | Light | `@android:color/system_neutral2_10` | Closest XML tone (99 vs 98, same `neutral2` palette) |
| `values-night-v31/colors.xml` | API 31–33 | Dark | `@android:color/system_neutral2_900` | Closest XML tone (10 vs 6, same `neutral2` palette) |
| `values-v34/colors.xml` | API 34+ | Light | `@android:color/system_surface_light` | Exact (`dynamicLightColorScheme34`) |
| `values-night-v34/colors.xml` | API 34+ | Dark | `@android:color/system_surface_dark` | Exact (`dynamicDarkColorScheme34`) |

Per Android's [Providing Resources](https://developer.android.com/guide/topics/resources/providing-resources) qualifier precedence (Table 2, checked November 21, 2026), `night` has higher precedence than `v<API>`, so `values-night-v31` and `values-night-v34` are the required directory names and override `values-v31` and `values-v34` in dark mode.

- **Alternatives considered for API 31–33:**
  - `system_neutral1_10` / `system_neutral1_900`: chroma 4 instead of chroma 8, so greyer than Material 3 1.4.0's `neutralVariant` (`system_neutral2`) dynamic surfaces.
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
    - `android:colorPrimaryDark` = `@color/launch_surface`
    - `android:windowLightStatusBar` = `true`
  - `Theme.Dewfall` (parent `Base.Theme.Dewfall`)
- `app/src/main/res/values-night/themes.xml`:
  - `Base.Theme.Dewfall` (parent `android:Theme.Material.NoActionBar`):
    - `android:colorBackground` = `@color/launch_surface`
    - `android:colorPrimaryDark` = `@color/launch_surface`
    - `android:windowLightStatusBar` = `false`
- `app/src/main/res/values-v31/themes.xml`:
  - `Theme.Dewfall` (parent `Base.Theme.Dewfall`):
    - `android:windowSplashScreenBackground` = `@color/launch_surface`

Why each attribute is included or omitted (checked November 21, 2026 in public AOSP sources and [`android.R.attr`](https://developer.android.com/reference/android/R.attr)):

- **`android:colorBackground` alone (no duplicate `android:windowBackground`):** `Theme.Material` and `Theme.Material.Light` already set `windowBackground` to `?attr/colorBackground` ([`android10-release/themes_material.xml`](https://android.googlesource.com/platform/frameworks/base/+/refs/heads/android10-release/core/res/res/values/themes_material.xml#160), [`main/themes_material.xml`](https://android.googlesource.com/platform/frameworks/base/+/refs/heads/main/core/res/res/values/themes_material.xml#160)), and `Theme` sets `windowBackgroundFallback` to `?attr/colorBackground` ([`android10-release/themes.xml`](https://android.googlesource.com/platform/frameworks/base/+/refs/heads/android10-release/core/res/res/values/themes.xml#168)). `Activity` also reads `colorBackground` into [`ActivityManager.TaskDescription`](https://developer.android.com/reference/android/app/ActivityManager.TaskDescription) for Recents ([`android10-release/Activity.java`](https://android.googlesource.com/platform/frameworks/base/+/refs/heads/android10-release/core/java/android/app/Activity.java#4977), [`main/Activity.java`](https://android.googlesource.com/platform/frameworks/base/+/refs/heads/main/core/java/android/app/Activity.java#5589)). Setting `android:colorBackground` drives all three from one item.
- **`android:windowSplashScreenBackground`:** Sets the Android 12+ system splash screen background explicitly ([`android.R.attr#windowSplashScreenBackground`](https://developer.android.com/reference/android/R.attr#windowSplashScreenBackground)).
- **`android:colorPrimaryDark`:** Not deprecated ([`main/attrs.xml`](https://android.googlesource.com/platform/frameworks/base/+/refs/heads/main/core/res/res/values/attrs.xml#1126)). Because `Theme.Material` maps `statusBarColor` to `?attr/colorPrimaryDark`, setting it replaces the `#757575` grey (light) and `#000000` black (dark) on Android 10–11 preview windows without using deprecated `android:statusBarColor`.
- **`android:windowLightStatusBar`:** Added in API 23 (below `minSdk = 29`) and not deprecated ([`main/attrs.xml`](https://android.googlesource.com/platform/frameworks/base/+/refs/heads/main/core/res/res/values/attrs.xml#2787)). Gives dark status bar icons on `#F5FBF2` in light mode and light icons on `#101411` in dark mode before `enableEdgeToEdge()` runs.
- **No `android:statusBarColor` or `android:navigationBarColor`:** Both are deprecated in Android 15 (API 35) ([`statusBarColor` at `main/attrs.xml#2687-2689`](https://android.googlesource.com/platform/frameworks/base/+/refs/heads/main/core/res/res/values/attrs.xml#2687), [`navigationBarColor` at `main/attrs.xml#2707-2710`](https://android.googlesource.com/platform/frameworks/base/+/refs/heads/main/core/res/res/values/attrs.xml#2707)). Per Android's [Android 15 edge-to-edge behavior changes](https://developer.android.com/about/versions/15/behavior-changes-15#edge-to-edge), `statusBarColor` has no effect on Android 15+, and `navigationBarColor` has no effect on gesture navigation while still affecting 3-button navigation (where Android 15 sets it to match a color-drawable window background by default; AOSP's `attrs.xml` javadoc calls it ignored, which differs from the behavior-changes guide's 3-button note). Because both are deprecated, `AGENTS.md` forbids using them in new code.
- **No `android:windowLightNavigationBar` through API 33 (and unset on API 34+ unless needed by hand check):** AOSP's `Theme.Material.Light` sets `navigationBarColor` to `@color/black` through Android 13 ([`android10-release`](https://android.googlesource.com/platform/frameworks/base/+/refs/heads/android10-release/core/res/res/values/themes_material.xml#554), [`android11-release`](https://android.googlesource.com/platform/frameworks/base/+/refs/heads/android11-release/core/res/res/values/themes_material.xml#551), [`android12-release`](https://android.googlesource.com/platform/frameworks/base/+/refs/heads/android12-release/core/res/res/values/themes_material.xml#551), [`android13-release`](https://android.googlesource.com/platform/frameworks/base/+/refs/heads/android13-release/core/res/res/values/themes_material.xml#551)), switching to `@color/navigation_bar_default` only in [`android14-release`](https://android.googlesource.com/platform/frameworks/base/+/refs/heads/android14-release/core/res/res/values/themes_material.xml#551). Leaving `android:windowLightNavigationBar` unset through API 33 keeps navigation icons light and readable on that black bar until `enableEdgeToEdge()` runs. On Android 13+, the system splash screen computes both light status and light navigation bar appearance from `ContrastColorUtil.isColorLight` of its background ([`android13-release/StartingSurfaceDrawer.java#377-381`](https://android.googlesource.com/platform/frameworks/base/+/refs/heads/android13-release/libs/WindowManager/Shell/src/com/android/wm/shell/startingsurface/StartingSurfaceDrawer.java#377), [`main/SplashscreenWindowCreator.java#214-220`](https://android.googlesource.com/platform/frameworks/base/+/refs/heads/main/libs/WindowManager/Shell/src/com/android/wm/shell/startingsurface/SplashscreenWindowCreator.java#214)). Android 12 and 12L add the splash screen window without any bar appearance ([`android12L-release/StartingSurfaceDrawer.java#239-291`](https://android.googlesource.com/platform/frameworks/base/+/refs/heads/android12L-release/libs/WindowManager/Shell/src/com/android/wm/shell/startingsurface/StartingSurfaceDrawer.java#239)), so its icons stay white whatever the theme sets. `SplashScreenView.applySystemBarsContrastColor` ([`android12-release/SplashScreenView.java#598-602`](https://android.googlesource.com/platform/frameworks/base/+/refs/heads/android12-release/core/java/android/window/SplashScreenView.java#598)) runs there only when an app takes over the splash screen view for an exit animation, which Dewfall doesn't. The hand check on Android 12L confirmed white status bar icons and navigation buttons on the light splash screen. That was already true of `Theme.Material`'s grey splash screen, and no theme attribute changes it. (Corrected on November 21, 2026, after the hand check.) If a 3-button navigation hand check on API 34+ still shows a light navigation bar with unreadable light icons, `android:windowLightNavigationBar="true"` would be added only for light mode on API 34+ (`values-v34/themes.xml`), with dark mode kept `false` (`values-night-v34/themes.xml`).
- **Comments in `colors.xml` and `themes.xml`:** Explain in `values/colors.xml` (and `values-night/colors.xml`) why those two static hex values live in XML (nothing can read Compose's `MaterialTheme` before the first frame draws) and that `LaunchThemeTest` ties them to `tokens.json`. Explain in `values/themes.xml` why `android:colorBackground` covers `windowBackground`, `windowBackgroundFallback`, and `ActivityManager.TaskDescription`, and point to `@color/launch_surface` in `colors.xml`.

### Verification and tests

- **JVM unit test (`LaunchThemeTest` in `app/src/test/kotlin/io/github/dewfallapp/`):**
  - Follows the same pattern as `DesignTokensTest` and `ManropeTest` in `:core:designsystem`: `app/build.gradle.kts` declares `docs/design/claude-design/design-system/tokens.json`, `app/src/main/res`, and `app/src/main/AndroidManifest.xml` as inputs to `:app`'s `Test` tasks and passes their relative paths via system properties. `:app` already has `testImplementation(libs.kotlinx.serialization.json)`.
  - Verifies:
    - `values/colors.xml` and `values-night/colors.xml` `launch_surface` match `tokens.json`'s `surface.light` and `surface.dark` exactly.
    - `app/src/main/AndroidManifest.xml` applies `android:theme="@style/Theme.Dewfall"` on `<application>` and does not override it on `MainActivity`.
    - `values-night/themes.xml` does not define `Theme.Dewfall` (so Android's `night` qualifier never shadows `values-v31/themes.xml` in dark mode).
    - No theme XML under `app/src/main/res/` sets deprecated `android:statusBarColor` or `android:navigationBarColor`, and no theme XML for API 29–33 (`values/`, `values-night/`, `values-v31/`) sets `android:windowLightNavigationBar` (guarding against dark icons on Android 10–13's black navigation bar).
    - All six folders define `launch_surface`, so no light or dark variant falls back to another folder's color.
    - `Base.Theme.Dewfall` in `values/` and `values-night/` sets `android:colorBackground` and `android:colorPrimaryDark` to `@color/launch_surface`, and `android:windowLightStatusBar` to `true` and `false`. `Theme.Dewfall` in `values/` only inherits it, and `Theme.Dewfall` in `values-v31/` sets `android:windowSplashScreenBackground` to `@color/launch_surface`.
  - Does not duplicate the `@android:color/system_*` names from `values-v31/` and `values-v34/` as tautological string assertions; Android Lint and AAPT verify that those resource references resolve and respect API levels (`NewApi` / `UnusedAttribute`).
- **Android Lint and build checks:** `./gradlew spotlessCheck detekt lint test assembleDebug --continue -Pandroid.builder.sdkDownload=false` checks resource resolution, API qualifiers, formatting, and all unit tests.
- **Hand checks on emulators:**
  - **Android 10 (API 29)** in light and dark mode, with both gesture navigation and **3-button navigation**: cold launch window background (`#F5FBF2` / `#101411`), status bar color and icons, navigation bar icon readability on the black bar before `enableEdgeToEdge()`, Recents background, and transition to Search YouTube.
  - **Android 12 and later** (Android 14+, e.g. Android 16 API 36, plus Android 12/13 if an image is installed) in light and dark mode, with both gesture navigation and **3-button navigation**, across two contrasting wallpapers: splash screen background, seamless transition into Compose's dynamic `surface`, navigation bar appearance and icon contrast during launch (adding `android:windowLightNavigationBar="true"` only for light mode on API 34+, with dark mode kept `false`, if an API 34+ 3-button check shows a light bar with unreadable light icons), Recents, and multi-window resizing in split screen.

## Risks / Trade-offs

- [API 31–33 dark dynamic surface is tone 10 (`system_neutral2_900`) while Compose draws tone 6 (`neutralVariant6`)] → Closest platform XML resource in the same `neutral2` palette; exact match is impossible in XML before API 34, and the subtle 4-step shift replaces `Theme.Material`'s harsh `#303030` grey flash.
- [A future Compose BOM update may change Material 3's API 31–33 dynamic `surface` mapping (`androidx-main` refactored `DynamicTonalPalette.android.kt` on November 9, 2026 while keeping resolved tones unchanged)] → `docs/architecture.md` records the mapping and notes to re-check API 31–33 when Material 3 updates.
- [Android 10–13 light mode briefly shows `Theme.Material.Light`'s black navigation bar before `enableEdgeToEdge()` runs] → Leaving `android:windowLightNavigationBar` unset through API 33 keeps the navigation icons light and readable on that black bar until `onCreate()` runs `enableEdgeToEdge()`, while keeping zero deprecated XML attributes per `AGENTS.md`.
- [Android 12 and 12L draw white status bar icons and navigation buttons on the splash screen, which are hard to see on a light one (found in the hand check on November 21, 2026)] → A platform limit: those versions ignore the theme's bar attributes for the splash screen window, and `Theme.Material`'s grey splash screen had the same icons. It lasts only until the first frame, and Android 13 and later pick the icon colors from the background.
- [Static hex values `#F5FBF2` and `#101411` in `app/src/main/res/` are copied from `tokens.json`] → `LaunchThemeTest` reads `tokens.json` directly with Gradle test task inputs, so any token edit fails `:app:testDebugUnitTest` until updated.
- [Devices on Android 12+ whose OEM disables wallpaper Monet overlays] → Android's framework still resolves `@android:color/system_neutral2_*` and `@android:color/system_surface_*` to the system's default neutral palette, which is the same resource `dynamicLightColorScheme` / `dynamicDarkColorScheme` read, so launch and first frame still match.
- [No backported icon splash screen on Android 10–11 and no `setKeepOnScreenCondition` yet] → Intentional until `#120` adds Dewfall's icon or Phase 5 needs startup state; migrating to `androidx.core:core-splashscreen` later is additive and keeps the same `launch_surface` color resources.
