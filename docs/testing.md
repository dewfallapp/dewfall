# Testing

This file explains how Dewfall is tested: which kinds of tests exist, what each one covers, and which CI workflow runs them. Read it before writing tests or changing a workflow.

## Test layers

### Fast tests

Fast tests run on every change. They cover:

- Reducers.
- Use cases.
- Repositories with fake data sources.
- The YouTube layer against saved responses.
- Room queries.
- The theme against the design tokens. [architecture.md](architecture.md#the-theme) explains the check.
- Background jobs.
- Screenshot tests of each screen, in light, dark and large font.

Unit tests never touch the network.

### Slow tests

Slow tests are emulator tests of the main flows, such as search then play, and download then play offline.

### Benchmarks

Benchmarks for startup and scrolling run on a real phone before each release.

## CI workflows

The project uses four GitHub Actions workflows, plus Renovate's weekly update pull requests, which workflow 1 tests.

1. **Every pull request.** Formatting, lint, unit tests, screenshot tests, a database migration check, and a debug build. Branch protection blocks merging when any of them fail.
2. **Every merge to main.** Everything above, plus the emulator tests, and a nightly APK.
3. **Daily schedule.** The YouTube layer runs against real YouTube and opens an issue when something breaks. It never blocks pull requests.
4. **Version tag.** A signed release build, changelog, checksums, and a GitHub release.
5. **Weekly.** Renovate opens dependency update pull requests, and workflow 1 tests them. See [Dependency updates](#dependency-updates).

### The pull request workflow

Workflow 1 is .github/workflows/pull-request.yml. Today it runs one job, Checks, with the all-checks command from [AGENTS.md](../AGENTS.md#commands): formatting, detekt, Android Lint with the Android API check, unit tests, and a debug build. After the build, it checks that the Room schemas are committed. Screenshot tests join it when the task that adds them is done. Database migration tests need an emulator, so the task that adds them decides which workflow runs them.

- It also runs on every push to main. Those runs fill the Gradle cache, which pull request runs only read. Workflow 2, with the emulator tests and the nightly APK, doesn't exist yet.
- It adds `--continue` to the command, so one run reports every failing check.
- It installs the Android SDK packages the build needs, and turns off AGP's automatic SDK downloads, so a missing package fails the build.
- After the Gradle command, a step fails when the build changed or added files in core/database/schemas/. It runs even when the Gradle command failed. See [Database migration tests](#database-migration-tests).
- When the job fails, it uploads the build/reports/ folder of every project as an artifact called reports, kept for 7 days.
- It caches Gradle with setup-gradle's basic caching, which is MIT licensed, because the default, enhanced caching, loads a proprietary component ([DISTRIBUTION.md](https://github.com/gradle/actions/blob/v6.4.0/DISTRIBUTION.md)).
- A repository ruleset named main protects the branch. It requires a pull request, allows only merge commits, and requires the check named Checks to pass on a branch that's up to date with main. It blocks force pushes and deletion, and nobody can bypass it, maintainers included. The ruleset requires the check by its name, so renaming the job blocks merging until the ruleset is updated.
- The repository allows only GitHub's own actions and gradle/actions/setup-gradle, and requires every action to be pinned to a full commit SHA. A new action needs the allow list changed in the repository settings first.

### Dependency updates

[Renovate](https://docs.renovatebot.com/) opens a pull request for each dependency update, and workflow 1 tests it like any other pull request. Renovate runs as the Mend Renovate app on Mend's servers, which clone the repository for each run. It reads its settings from renovate.json. Renovate is licensed under AGPL-3.0 and never ships in the app.

- **What it updates.** The plugins and libraries in gradle/libs.versions.toml, the Gradle wrapper, and the actions and the runner image in .github/workflows/. ktfmt and Gummy Bears have no library entry in the catalog, so two custom managers in renovate.json find their versions. The Gummy Bears manager names the artifact for API 29, so change it when minSdk changes. Renovate would also update the JDK version in the workflow's setup-java step, but the build needs JDK 17, so renovate.json turns that off. Nothing updates the Android SDK packages in the workflow or the Android versions in build-logic. Change these by hand.
- **When.** Renovate creates new branches only on Fridays, in the Asia/Dhaka time zone. It still rebases open pull requests on other days.
- **How many.** At most five of Renovate's pull requests are open at once, with no hourly limit. Updates beyond that wait for the next Friday after a pull request closes. The Dependency Dashboard lists them.
- **The 14-day wait.** AGENTS.md says to wait 14 days after a release before adopting it, unless the release fixes a security issue. Renovate creates no branch for a release that is less than 14 days old, or that has no release date. The Dependency Dashboard, an issue that Renovate keeps open, lists these updates under "Pending Status Checks".
- **Security fixes.** A pull request that fixes a known vulnerability skips the wait and the schedule. Renovate finds these fixes in the repository's Dependabot alerts and in the [OSV](https://osv.dev/) vulnerability database, which it downloads and searches offline. When a release fixes a security issue that neither source lists, such as a Gradle release, tick its box under "Pending Status Checks" to create the pull request now.
- **Groups.** Entries that share a version in the catalog change together, such as Hilt's three entries. Kotlin and KSP share one pull request. So do Spotless and ktfmt, because ktfmt 0.65 and later are published as org.jetbrains.kotlinx:ktfmt, which only Spotless 8.10.4 or later can load. detekt and compose-rules share one too, because each compose-rules release is built for one detekt version. That pull request waits until the maintainer ticks its box on the Dependency Dashboard, after checking compose-rules' supported versions table.
- **Pre-releases.** Renovate offers a pre-release only when the current version is a pre-release with the same major, minor and patch numbers. So detekt moves from one 2.0.0 alpha to the next or to 2.0.0, and every other dependency gets stable releases only ([ignoreUnstable](https://docs.renovatebot.com/configuration-options/#ignoreunstable)).
- **Titles.** Gradle updates start with "build:" and Actions updates with "ci:", as the commit message rule in AGENTS.md asks.
- **Up to date with main.** The ruleset requires a branch that's up to date with main, and Renovate rebases its open pull requests when main moves. To rebase one now, tick the rebase box in its description. Don't press "Update branch". Renovate would then treat the pull request as edited by someone else and stop updating it.
- **Fixing a pull request.** Some updates need a change before Checks passes. A ktfmt update can change the formatting, a Kotlin or library update can deprecate an API the code uses, and an AGP update can need new build tools. Push the fix to Renovate's branch. Renovate then leaves the branch alone, so keep it up to date with main yourself. For ktfmt and AGP updates, the pull request's description names the usual fix.
- **The Gradle wrapper.** Renovate writes the new distributionSha256Sum, then runs the wrapper task to update gradle-wrapper.jar, gradlew and gradlew.bat. If that task fails on Mend's servers, only gradle-wrapper.properties changes. Then run `./gradlew wrapper` on the branch and push any changes it makes.
- **Review.** Nothing merges on its own. Before merging, check that Checks passed and read the release notes, especially detekt's. For a library that ships in the app, check that the update adds no artifact whose license breaks the rule in AGENTS.md.
- **Exceptions.** Renovate's pull requests don't use the pull request template, and their branches start with renovate/.

AGENTS.md has the command that checks renovate.json.

## Running tests locally

Run these from the repository root. AGENTS.md lists the command that runs every check the pull request workflow runs.

| To run | Command |
|---|---|
| Every unit test | `./gradlew test` |
| One plain Kotlin/JVM module's tests | `./gradlew :core:mvi:test` |
| One Android module's tests | `./gradlew :core:data:testDebugUnitTest` |
| One test class | Add `--tests` with the class name, such as `./gradlew :core:mvi:test --tests "io.github.dewfallapp.core.mvi.counter.CounterReducerTest"` |

Each module writes an HTML report to build/reports/tests/test/index.html in a plain Kotlin/JVM module, or to build/reports/tests/testDebugUnitTest/index.html in an Android module. The XML results go to build/test-results/. AGP 9 creates unit tests for the debug build type only, so Android modules have no release unit tests.

### Writing tests

- Use kotlin.test's annotations and assertions, such as kotlin.test.Test, BeforeTest, AfterTest and assertEquals. They run on JUnit 6 in plain Kotlin/JVM modules and on JUnit 4 in Android modules, so a test reads the same in both. [architecture.md](architecture.md#build-settings) explains why there are two.
- Name each local test in src/test/ with backticks, as a sentence about what it checks, such as `` `increment adds one` ``. detekt's naming rule skips test folders. Don't do this in src/androidTest/: Android runs method names with spaces only from API level 30, and the minimum is 29 ([Kotlin coding conventions](https://kotlinlang.org/docs/coding-conventions.html#names-for-test-methods)).
- Put tests in the src/test/ folder of the module whose code they test.

### Reducers

A reducer is a plain function, so its test calls reduce and compares the Next it returns. It needs no coroutines and no ViewModel. [CounterReducerTest.kt](../core/mvi/src/test/kotlin/io/github/dewfallapp/core/mvi/counter/CounterReducerTest.kt) shows the pattern.

### ViewModels and coroutines

ViewModel tests use kotlinx-coroutines-test, as Google's guides describe ([Testing Kotlin coroutines](https://developer.android.com/kotlin/coroutines/test), [Testing Kotlin flows](https://developer.android.com/kotlin/flow/test)). [MviViewModelTest.kt](../core/mvi/src/test/kotlin/io/github/dewfallapp/core/mvi/MviViewModelTest.kt) shows the pattern.

- Replace the Main dispatcher with `Dispatchers.setMain(UnconfinedTestDispatcher())` in a @BeforeTest function, and reset it with `Dispatchers.resetMain()` in an @AfterTest function. viewModelScope runs on Main. Without a Main dispatcher, its coroutines run on Dispatchers.Default's threads, and the test becomes flaky.
- Wrap each test in runTest. It shares the Main dispatcher's scheduler and skips delays.
- Where the code under test takes a dispatcher, pass `StandardTestDispatcher(testScheduler)`, so every TestDispatcher in the test shares one scheduler. Call advanceUntilIdle() to run the work it queued.
- Read a ViewModel's state and pendingEffects through their value. Both are StateFlows that hold their latest value, so the test needs no collector.
- To check every value a Flow emits over time, collect it in backgroundScope, as Google's flow testing guide shows. The project doesn't use Turbine.
- setMain, resetMain, UnconfinedTestDispatcher and advanceUntilIdle are marked experimental, and compiler warnings fail the build. So annotate the test class with `@OptIn(ExperimentalCoroutinesApi::class)`.
- Test which work each intent starts, and that its result comes back through the reducer. The reducer's own tests cover the rest of the screen's logic.

## Saved YouTube responses

TODO

## Screenshot tests

TODO

## Database migration tests

There are no migration tests yet. The first public release is the first GitHub release, including a pre-release, published from a version tag. A tag made only to test the release workflow, with no published release, doesn't count, and nightly APKs don't count. Until then, the database stays at version 1 and a schema change needs no migration, as AGENTS.md says. Phase 5 adds the first tables (#48).

**The schema check.** Room writes the schema of each database version to core/database/schemas/, and the files are committed. When the schema changes, Room rewrites the file, even when the version number stays the same, and the build still passes. So the pull request workflow has a step after the build that fails when the build changed or added a file in that folder. Locally, `git status core/database/schemas` shows the same changes after a build.

**From the first public release on.** A committed schema file must never change after that, because phones with the release have databases in that shape. The workflow then needs a second check, which fails when a schema file that's on main changes or disappears. Phase 9 adds it (#134).

**Migration tests.** Phase 9 sets them up (#53), so they're ready for the first migration after the first public release. Room's MigrationTestHelper, from androidx.room3:room3-testing, creates a database at an old version from its schema file, runs the migrations, and checks the result against the new schema ([Test migrations](https://developer.android.com/training/data-storage/room/migrating-db-versions#test)). On Android it needs an Instrumentation, so the tests run as androidTest tests on an emulator or a phone. The Room Gradle plugin already adds the schema files to the androidTest assets. Google doesn't recommend Robolectric for Room tests ([Test and debug your database](https://developer.android.com/training/data-storage/room/testing-db)), and tests on the computer's JVM would need a Kotlin Multiplatform JVM target, which ADR 0006 rules out. So #53 also decides which workflow runs an emulator for these tests.
