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
- Background jobs.
- Screenshot tests of each screen, in light, dark and large font.

Unit tests never touch the network.

### Slow tests

Slow tests are emulator tests of the main flows, such as search then play, and download then play offline.

### Benchmarks

Benchmarks for startup and scrolling run on a real phone before each release.

## CI workflows

The project uses five GitHub Actions workflows.

1. **Every pull request.** Formatting, lint, unit tests, screenshot tests, a database migration check, and a debug build. Branch protection blocks merging when any of them fail.
2. **Every merge to main.** Everything above, plus the emulator tests, and a nightly APK.
3. **Daily schedule.** The YouTube layer runs against real YouTube and opens an issue when something breaks. It never blocks pull requests.
4. **Version tag.** A signed release build, changelog, checksums, and a GitHub release.
5. **Weekly.** Dependency update pull requests from Renovate or Dependabot, tested by workflow 1.

### The pull request workflow

Workflow 1 is .github/workflows/pull-request.yml. Today it runs one job, Checks, with the all-checks command from [AGENTS.md](../AGENTS.md#commands): formatting, detekt, Android Lint with the Android API check, unit tests, and a debug build. Screenshot tests and the database migration check join it when the tasks that add them are done.

- It also runs on every push to main. Those runs fill the Gradle cache, which pull request runs only read. Workflow 2, with the emulator tests and the nightly APK, doesn't exist yet.
- It adds `--continue` to the command, so one run reports every failing check.
- It installs the Android SDK packages the build needs, and turns off AGP's automatic SDK downloads, so a missing package fails the build.
- When the job fails, it uploads the build/reports/ folder of every project as an artifact called reports, kept for 7 days.
- It caches Gradle with setup-gradle's basic caching, which is MIT licensed, because the default, enhanced caching, loads a proprietary component ([DISTRIBUTION.md](https://github.com/gradle/actions/blob/v6.4.0/DISTRIBUTION.md)).
- A repository ruleset named main protects the branch. It requires a pull request, allows only merge commits, and requires the check named Checks to pass on a branch that's up to date with main. It blocks force pushes and deletion, and nobody can bypass it, maintainers included. The ruleset requires the check by its name, so renaming the job blocks merging until the ruleset is updated.
- The repository allows only GitHub's own actions and gradle/actions/setup-gradle, and requires every action to be pinned to a full commit SHA. A new action needs the allow list changed in the repository settings first.

## Running tests locally

TODO

## Saved YouTube responses

TODO

## Screenshot tests

TODO

## Database migration tests

TODO
