# Proposal

## Why

Today `:app`'s window theme in `app/src/main/res/values/themes.xml` and `values-night/themes.xml` inherits the platform's `Theme.Material` without overriding its background colors. Before Compose draws the first frame, the launch window and the Android 12+ splash screen show `Theme.Material`'s grey (`#FAFAFA` in light, `#303030` in dark) instead of Dewfall's `surface` (`#F5FBF2` in light, `#101411` in dark), and they ignore dynamic color on Android 12 and later. This came up while checking the theme for [#26](https://github.com/dewfallapp/dewfall/issues/26) and is tracked in Phase 4 issue [#138](https://github.com/dewfallapp/dewfall/issues/138).

## What Changes

- **Match the launch window background to Dewfall's `surface` color before the first frame:**
  - On **Android 10 and 11 (API 29–30)**, where dynamic color does not exist, the launch window uses Dewfall's own `surface` token: `#F5FBF2` in light mode and `#101411` in dark mode. Nothing can read Compose's `MaterialTheme` before the first frame draws, so the XML theme holds those two hex values, and unit tests tie them to `tokens.json`.
  - On **Android 12 and 13 (API 31–33)**, where dynamic color is on, the launch window uses the closest platform dynamic neutral-variant resources: `@android:color/system_neutral2_10` in light mode and `@android:color/system_neutral2_900` in dark mode.
  - On **Android 14 and later (API 34–37)**, the launch window uses `@android:color/system_surface_light` in light mode and `@android:color/system_surface_dark` in dark mode, matching `MaterialTheme.colorScheme.surface` from Compose Material 3's dynamic color schemes exactly.
- **Match the Android 12+ system splash screen background:**
  - On Android 12 and later (API 31–37), `Theme.Dewfall` sets `android:windowSplashScreenBackground` to the same dynamic surface color as the launch window background, in light and dark mode.
- **Keep related window surfaces and status bar icons consistent:**
  - `Theme.Dewfall` sets `android:colorBackground` to the launch surface color. `Theme.Material` already maps `windowBackground` and `windowBackgroundFallback` (multi-window resize fallback) to `?attr/colorBackground`, and `Activity` copies `colorBackground` into [`ActivityManager.TaskDescription`](https://developer.android.com/reference/android/app/ActivityManager.TaskDescription) for Recents.
  - `Theme.Dewfall` sets `android:colorPrimaryDark` to the launch surface color (so the Android 10–11 preview window's status bar matches instead of flashing `Theme.Material`'s `#757575` grey in light mode or `#000000` in dark mode) and sets `android:windowLightStatusBar` to `true` in light mode and `false` in dark mode.
  - `android:windowLightNavigationBar` stays unset on Android 10 and 11 because `Theme.Material.Light` sets `navigationBarColor` to `@color/black` there; setting it would put dark navigation icons on a black bar. Hand checks with 3-button navigation on Android 10 and on Android 12+ will confirm how the navigation bar draws; `android:windowLightNavigationBar` will be set on API 31+ only if that check shows a light navigation bar there.
  - `Theme.Dewfall` stays on `<application>`, so `MainActivity` and any activity inheriting the application theme share the same launch window colors.
- **Out of scope for this change:**
  - Custom splash screen icon, icon background color, or branding image. Dewfall has no app icon until [#120](https://github.com/dewfallapp/dewfall/issues/120) in Phase 9, so Android 12+ keeps the default launcher icon on the splash screen.
  - Holding the splash screen open while loading data (`setKeepOnScreenCondition`) or custom exit animations. Nothing runs at app startup today.
  - Backporting an icon splash screen to Android 10 and 11.
- **Automated token and theme checks:**
  - Unit tests in `:app` (`LaunchThemeTest`) check the static light and dark launch surface XML colors against `docs/design/claude-design/design-system/tokens.json`, verify `AndroidManifest.xml` applies `@style/Theme.Dewfall`, and verify no theme XML sets deprecated system bar attributes or `android:windowLightNavigationBar` on API 29–30.

## Capabilities

### New Capabilities

- `app-launch`: how the app's launch window before the first frame, Recents and multi-window fallback background, and the Android 12+ splash screen match Dewfall's light, dark, and dynamic colors. Adds `app-launch` to ADR 0004's list of living-spec areas, which the maintainer approved on November 21, 2026.

### Modified Capabilities

None.

## Impact

- `:app` resources: adds `colors.xml` across `values/`, `values-night/`, `values-v31/`, `values-night-v31/`, `values-v34/`, and `values-night-v34/`, updates `values/themes.xml` and `values-night/themes.xml`, and adds `values-v31/themes.xml`.
- `:app` build and tests: declares `tokens.json`, `app/src/main/res`, and `app/src/main/AndroidManifest.xml` as inputs to `:app`'s unit test tasks in `app/build.gradle.kts`, and adds `LaunchThemeTest` in `app/src/test/kotlin/io/github/dewfallapp/`.
- No Kotlin changes in `MainActivity`, `DewfallApp`, or `:core:designsystem`.
- No changes to `:core:playback`, `:feature:player-logic`, `:feature:player-ui`, `:core:youtube`, or any feature module.
- Dependencies: none.
- Network, logging, and privacy: no network calls, no new permissions, no new logging, and no user data stored or exposed (ADR 0007).
- Docs: `docs/architecture.md` ("The theme" section), ADR 0004 (dated note adding `app-launch`), `openspec/config.yaml`, and `docs/roadmap.md` (Phase 0 living-spec area list).

## Decisions approved by the maintainer

On November 21, 2026, the maintainer answered all four questions for this change:

1. **New living-spec area (`app-launch`):** Approved. Add `app-launch` to ADR 0004 with a dated note, and add it to `openspec/config.yaml` and `docs/roadmap.md`'s Phase 0 guide, as `#40` did for `youtube-search`.
2. **Platform XML attributes vs `androidx.core:core-splashscreen`:** Approved platform XML attributes only, with no `androidx.core:core-splashscreen` dependency. Android's [splash screen migration guide](https://developer.android.com/develop/ui/views/launch/splash-screen/migrate) recommends `androidx.core:core-splashscreen` (`1.2.0`, Apache License 2.0, on [Google Maven](https://maven.google.com/web/index.html#androidx.core:core-splashscreen:1.2.0), checked November 21, 2026) for a uniform icon splash screen on Android 11 and lower and for `setKeepOnScreenCondition`. Dewfall has no app icon until [#120](https://github.com/dewfallapp/dewfall/issues/120) and runs no startup work before Compose draws, so platform XML attributes fit cleanly without a new dependency.
3. **Android 12–13 (API 31–33) dynamic color approximation:** Approved `@android:color/system_neutral2_10` (light, tone 99) and `@android:color/system_neutral2_900` (dark, tone 10) on API 31–33 as the closest XML resources in the same `system_neutral2` palette that Compose Material 3 1.4.0 uses to compute `neutralVariant98` and `neutralVariant6` in code. API 34+ uses `@android:color/system_surface_light` and `@android:color/system_surface_dark` for an exact match.
4. **No deprecated system bar attributes and navigation bar icons:** Approved keeping zero deprecated XML attributes (`android:statusBarColor` and `android:navigationBarColor`, deprecated in Android 15), and leaving `android:windowLightNavigationBar` unset wherever the navigation bar stays black (at least on API 29–30, where AOSP's [`android10-release`](https://android.googlesource.com/platform/frameworks/base/+/refs/heads/android10-release/core/res/res/values/themes_material.xml#554) and [`android11-release`](https://android.googlesource.com/platform/frameworks/base/+/refs/heads/android11-release/core/res/res/values/themes_material.xml#551) `themes_material.xml` set `navigationBarColor` to `@color/black` in `Theme.Material.Light`). Hand checks with 3-button navigation on Android 10 and Android 12+ will verify bar contrast, and `android:windowLightNavigationBar` will be added on API 31+ only if that check shows a light bar there.
