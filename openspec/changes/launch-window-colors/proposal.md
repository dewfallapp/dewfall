# Proposal

## Why

Today `:app`'s window theme in `app/src/main/res/values/themes.xml` and `values-night/themes.xml` inherits the platform's `Theme.Material` without overriding its background colors. Before Compose draws the first frame, the launch window and the Android 12+ splash screen show `Theme.Material`'s grey (`#FAFAFA` in light, `#303030` in dark) instead of Dewfall's `surface` (`#F5FBF2` in light, `#101411` in dark), and they ignore dynamic color on Android 12 and later. This came up while checking the theme for [#26](https://github.com/dewfallapp/dewfall/issues/26) and is tracked in Phase 4 issue [#138](https://github.com/dewfallapp/dewfall/issues/138).

## What Changes

- **Match the launch window background to Dewfall's `surface` color before the first frame:**
  - On **Android 10 and 11 (API 29–30)**, where dynamic color does not exist, the launch window uses Dewfall's own `surface` token: `#F5FBF2` in light mode and `#101411` in dark mode.
  - On **Android 12 and 13 (API 31–33)**, where dynamic color is on, the launch window uses the closest platform dynamic neutral-variant resources: `@android:color/system_neutral2_10` in light mode and `@android:color/system_neutral2_900` in dark mode.
  - On **Android 14 and later (API 34–37)**, the launch window uses `@android:color/system_surface_light` in light mode and `@android:color/system_surface_dark` in dark mode, matching `MaterialTheme.colorScheme.surface` from Compose Material 3's dynamic color schemes exactly.
- **Match the Android 12+ system splash screen background:**
  - On Android 12 and later (API 31–37), `Theme.Dewfall` sets `android:windowSplashScreenBackground` to the same dynamic surface color as the launch window background, in light and dark mode.
- **Keep related window surfaces and bar icons consistent:**
  - `Theme.Dewfall` also sets `android:colorBackground` to the same surface color, so multi-window resize fallback (`windowBackgroundFallback`) and Recents' task background match.
  - `Theme.Dewfall` sets `android:colorPrimaryDark` to the surface color (so the Android 10–11 preview window's status bar matches instead of flashing `Theme.Material`'s `#757575` grey in light mode or `#000000` in dark mode) and sets `android:windowLightStatusBar` and `android:windowLightNavigationBar` to `true` in light mode and `false` in dark mode.
  - `Theme.Dewfall` stays on `<application>`, so `MainActivity`, the debug build's `YouTubeDebugActivity`, and Compose previews all inherit the same launch window colors.
- **Out of scope for this change:**
  - Custom splash screen icon, icon background color, or branding image. Dewfall has no app icon until [#120](https://github.com/dewfallapp/dewfall/issues/120) in Phase 9, so Android 12+ keeps the default launcher icon on the splash screen.
  - Holding the splash screen open while loading data (`setKeepOnScreenCondition`) or custom exit animations. Nothing runs at app startup today.
  - Backporting an icon splash screen to Android 10 and 11 (see Question 2 below).
- **Automated token and theme checks:**
  - Unit tests in `:app` check the light and dark launch surface XML colors against `docs/design/claude-design/design-system/tokens.json` (just as `DesignTokensTest` checks `Color.kt`) and verify the qualifier folders' color and theme attributes.

## Capabilities

### New Capabilities

- `app-launch`: how the app's launch window before the first frame and the Android 12+ splash screen match Dewfall's light, dark, and dynamic colors. Adding a new living-spec area changes ADR 0004's list (see Question 1 below).

### Modified Capabilities

None.

## Impact

- `:app` resources: adds `colors.xml` across `values/`, `values-night/`, `values-v31/`, `values-night-v31/`, `values-v34/`, and `values-night-v34/`, updates `values/themes.xml` and `values-night/themes.xml`, and adds `values-v31/themes.xml`.
- `:app` build and tests: declares `tokens.json` and `app/src/main/res` as inputs to `:app`'s unit test tasks in `app/build.gradle.kts`, and adds JVM unit tests in `app/src/test/`.
- No Kotlin changes in `MainActivity`, `DewfallApp`, or `:core:designsystem` (unless Question 2 adopts `androidx.core:core-splashscreen`).
- No changes to `:core:playback`, `:feature:player-logic`, `:feature:player-ui`, `:core:youtube`, or any feature module.
- Dependencies: none under the recommended approach (Question 2 notes `androidx.core:core-splashscreen:1.2.0` as an alternative).
- Network, logging, and privacy: no network calls, no new permissions, no new logging, and no user data stored or exposed (ADR 0007).
- Docs: `docs/architecture.md` ("The theme" section), ADR 0004 (dated note adding `app-launch`), `openspec/config.yaml`, and `docs/roadmap.md` (Phase 0 living-spec area list).

## Questions for the maintainer

1. **New living-spec area (`app-launch`):** ADR 0004 lists nine living-spec areas (`player`, `subscriptions`, `inbox`, `downloads`, `background-sync`, `cleanup`, `transcript-search`, `data-export`, `youtube-search`), and none covers the launch window or splash screen. Should we add `app-launch` to ADR 0004 (with a dated note, plus updating `openspec/config.yaml` and `docs/roadmap.md`), as `#40` did for `youtube-search`?
   - **Recommendation:** Yes, add `app-launch`. It gives a clear home for the launch window, the splash screen, and later launch/icon behavior such as [#120](https://github.com/dewfallapp/dewfall/issues/120). (Alternative: `theme`, though `#26` has no living spec and `#138` is about app launch.)

2. **Platform XML attributes vs `androidx.core:core-splashscreen`:** Android's [splash screen migration guide](https://developer.android.com/develop/ui/views/launch/splash-screen/migrate) recommends `androidx.core:core-splashscreen` (latest stable `1.2.0`, released November 5, 2025, Apache License 2.0, checked November 21, 2026 on [Google Maven](https://maven.google.com/web/index.html#androidx.core:core-splashscreen:1.2.0)) to show a consistent icon splash screen on Android 11 and lower and to support `setKeepOnScreenCondition`. Should we use platform XML attributes only (zero new dependencies), or add `androidx.core:core-splashscreen:1.2.0` now?
   - **Recommendation:** Use platform XML attributes only for now. Dewfall has no app icon until [#120](https://github.com/dewfallapp/dewfall/issues/120) (Phase 9) and runs no blocking startup work before Compose draws. Adding `core-splashscreen` today would bring a new shipping dependency and `installSplashScreen()` / `postSplashScreenTheme` plumbing in `MainActivity` (which `YouTubeDebugActivity` and Compose previews don't call) only to display the generic Android robot icon on Android 10–11. Platform XML attributes match `#138`'s wording (*"the window background before the first frame and the Android 12+ splash screen"*) cleanly, and `core-splashscreen` can be added later if Phase 5's onboarding check needs `setKeepOnScreenCondition` or when `#120` adds Dewfall's icon.

3. **Android 12–13 (API 31–33) dynamic color approximation:** Checked in Compose Material 3 1.4.0's `DynamicTonalPalette.android.kt` on November 21, 2026: on API 34+, `dynamicLightColorScheme` and `dynamicDarkColorScheme` read `android.R.color.system_surface_light` and `android.R.color.system_surface_dark`, so XML matches Compose exactly. On API 31–33, Material 3 computes `surface` in code as tone 98 (`neutralVariant98`) and tone 6 (`neutralVariant6`) from `android.R.color.system_neutral2_600`, and neither tone exists as an XML color resource before API 34. Because SystemUI renders the starting window and splash screen from XML before any app code runs, should API 31–33 use `@android:color/system_neutral2_10` (tone 99 in light) and `@android:color/system_neutral2_900` (tone 10 in dark)?
   - **Recommendation:** Yes. Both come from the same `system_neutral2` palette that Compose Material 3 1.4.0 uses on API 31–33. Tone 99 is one luminance step from tone 98, and tone 10 is four steps lighter than tone 6 while keeping the wallpaper tint (the next darker resource, `system_neutral2_1000`, is pure `#000000` black with no tint).

4. **Android 10–11 (API 29–30) navigation bar before `enableEdgeToEdge()`:** On Android 10–11, `Theme.Material` sets `android:navigationBarColor` to black (`@color/navigation_bar_default`), and Android 15 deprecated `android:navigationBarColor` and `android:statusBarColor` (checked in Android 37 SDK `attrs.xml` on November 21, 2026). Should we avoid deprecated XML attributes completely and leave `android:navigationBarColor` unset, or set `android:navigationBarColor` to `@color/launch_surface` in `values/themes.xml` (and omit it in `values-v35/themes.xml`)?
   - **Recommendation:** Avoid deprecated XML attributes completely, following `AGENTS.md`'s rule against deprecated APIs. Setting non-deprecated `android:colorPrimaryDark`, `android:windowLightStatusBar`, and `android:windowLightNavigationBar` fixes the status bar and system bar icons on Android 10–11; in light mode on Android 10–11, the preview window will briefly show `Theme.Material`'s black navigation bar until `enableEdgeToEdge()` runs in `MainActivity.onCreate()`. On Android 12–14 the system splash screen fills behind both bars, and on Android 15+ edge-to-edge is enforced automatically.
