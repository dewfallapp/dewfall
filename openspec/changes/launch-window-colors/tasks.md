# Tasks

## 1. Decisions the maintainer approves first

- [ ] 1.1 Once the maintainer approves `app-launch` (proposal Question 1), add a dated note to `docs/adr/0004-openspec-for-specs.md` adding `app-launch` to the living-spec area list, and update the same list in `openspec/config.yaml` and `docs/roadmap.md`'s Phase 0 guide. Verify that `openspec validate launch-window-colors --strict` passes.
- [ ] 1.2 Record the maintainer's answers to proposal Questions 2–4 in `proposal.md` and `design.md` (adjusting tasks below if the maintainer picks `androidx.core:core-splashscreen` or an API 29–30 `android:navigationBarColor` override). Verify that `proposal.md`, `specs/app-launch/spec.md`, `design.md`, and `tasks.md` agree.

## 2. Launch window colors, themes, tests, and docs

- [ ] 2.1 Add `launch_surface` color resources in `:app` across all six qualifier folders (`values/colors.xml` `#F5FBF2`, `values-night/colors.xml` `#101411`, `values-v31/colors.xml` `@android:color/system_neutral2_10`, `values-night-v31/colors.xml` `@android:color/system_neutral2_900`, `values-v34/colors.xml` `@android:color/system_surface_light`, `values-night-v34/colors.xml` `@android:color/system_surface_dark`). Update `app/src/main/res/values/themes.xml` and `values-night/themes.xml` with `Base.Theme.Dewfall` and `Theme.Dewfall` (`android:colorBackground`, `android:windowBackground`, `android:colorPrimaryDark`, `android:windowLightStatusBar`, `android:windowLightNavigationBar`), and add `app/src/main/res/values-v31/themes.xml` setting `android:windowSplashScreenBackground`. Verify that `./gradlew :app:assembleDebug :app:lintDebug` passes with zero warnings.
- [ ] 2.2 Configure `app/build.gradle.kts` to pass `docs/design/claude-design/design-system/tokens.json`, `app/src/main/res`, and `app/src/main/AndroidManifest.xml` as inputs and system properties to `:app`'s unit test tasks, and add `LaunchThemeTest` in `app/src/test/kotlin/io/github/dewfallapp/` checking the static light/dark colors against `tokens.json`, the API 31 and API 34 dynamic color references, the theme attributes (including no deprecated `statusBarColor` or `navigationBarColor`), and the manifest's `@style/Theme.Dewfall` wiring. Verify that `./gradlew :app:testDebugUnitTest --tests "io.github.dewfallapp.LaunchThemeTest"` passes.
- [ ] 2.3 Document the launch window and Android 12+ splash screen theme in `docs/architecture.md` under "The theme", including the API 29–30, API 31–33, and API 34+ color mapping and `LaunchThemeTest`. Verify that every file path, resource name, and SDK fact matches the code and passes review against `.claude/agents/reviewer.md`.

## 3. Checks

- [ ] 3.1 Run every check that the pull request workflow runs: `./gradlew spotlessCheck detekt lint test assembleDebug --continue -Pandroid.builder.sdkDownload=false`. Verify that all tasks pass and no files in `core/database/schemas/` change.
- [ ] 3.2 Ask the user before starting any emulator (since `#43` uses emulators too), then check by hand on an Android 10 (API 29) emulator and an Android 14+ emulator (and Android 12/13 if an image is installed):
  - Cold-start Dewfall in light and dark mode on Android 10 and verify the launch window background (`#F5FBF2` / `#101411`), status bar and bar icons, and seamless transition to Search YouTube, plus `YouTubeDebugActivity`.
  - Cold-start Dewfall in light and dark mode on Android 14+ with two contrasting wallpapers and verify the splash screen background and transition match Compose's dynamic `surface`, plus multi-window resizing in split screen.
- [ ] 3.3 Check that `:core:playback`, `:feature:player-logic`, and `:feature:player-ui` have zero changes (`git diff origin/main --stat`).

## Workflow follow-up

- Run the reviewer checklist in `.claude/agents/reviewer.md` on the implementation before opening or updating the PR for review.
- Archive the change with `openspec archive launch-window-colors` as the last commit of the pull request before merge, then run the reviewer checklist again on that commit.
