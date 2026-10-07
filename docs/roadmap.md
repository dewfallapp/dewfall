# Dewfall roadmap

This is the working plan for Dewfall. Work through it from top to bottom, one phase at a time. Each phase has a goal, a task list, a guide for the parts the maintainer handles personally, and a line that says when the phase is finished.

Once the GitHub project board exists, progress is tracked there. Until then, the checkboxes in this file track Phase 0. Otherwise, change this file only when the plan itself changes.

## The app in short

Dewfall is a YouTube client that works like a podcast app. You pick the channels you care about. While your phone charges on Wi-Fi at night, the app downloads their new videos, so in the morning they play instantly, with or without internet. There is no recommendation feed. The app helps people watch what they chose and then put the phone down.

It is for people who feel YouTube takes too much of their time, people who learn from long videos such as lectures, tutorials and podcasts, and people with slow or expensive mobile data.

The first version has four parts:

1. Channels delivered. New uploads from your channels download overnight, with a storage limit and automatic cleanup.
2. A calm home screen. An inbox of new videos from your channels, with no suggestions and no Shorts.
3. Search inside videos. The app saves the subtitles of watched videos, so you can search for something that was said and jump to that moment.
4. Normal search and streaming. Any video can be found and watched right away without downloading.

**Non-goals.** No recommendation feed, no accounts, no ads and no analytics. These stay out unless the vision document is changed on purpose.

## Decisions already made

1. Name: Dewfall, confirmed by the availability checks in Phase 0. Dew forms quietly overnight and is there in the morning, the same way new videos arrive in the app. The code lives at github.com/dewfallapp/dewfall, and the package name is io.github.dewfallapp.
2. License: GPLv3. NewPipeExtractor is licensed under GPL 3.0, so an app that uses it has to be GPLv3 too.
3. YouTube access: NewPipeExtractor, wrapped in its own module behind the app's own interfaces. Nothing else in the app knows which library sits underneath.
4. Architecture: MVI for screen state. Playback state stays outside MVI and comes from the Media3 session as its own stream, so the screen does not redraw every time the playback position changes.
5. UI: Jetpack Compose with Material 3, light and dark themes, with dynamic color supported.
6. Deletion: a video counts as watched at about 90 percent, and the following night's cleanup deletes it. Users can switch this to right away, after a week, or never, and can mark any video as "keep". When the storage limit is reached, the app stops downloading instead of deleting unwatched videos. Clearing unwatched videos after two weeks is an optional setting.
7. Background work: WorkManager jobs that run only on Wi-Fi while charging, with automatic retries. The app does not run constantly.
8. Subtitles: saved for every watched video, so search still works after the video file is deleted. Tapping an old result streams the video from that moment. Offline, the app shows the matching text with a few lines around it.
9. Discovery: no feed. Only on request, through similar channels, "more like this" on a video, and an opt-in weekly list of channel suggestions.
10. Specs: OpenSpec, with one change per feature and living specs grouped by area of the app. OpenSpec records what the app does. The architecture document and ADRs record how it is built and why.

## How every phase works

Every phase follows the same routine:

1. Split the phase into features and start an OpenSpec change for each one with /opsx:propose. Review its proposal, requirement changes and task list before any code is written.
2. Check the designs for every screen the phase touches.
3. Build the phase in small pull requests, each with its own tests.
4. Update docs/architecture.md, and add an ADR for any new decision. Archive each finished OpenSpec change so the living specs match the app.
5. Merge only when every CI check is green.
6. Use the nightly build on your own phone for a few days before starting the next phase.

## Phase 0. Decisions and docs

**Goal.** Settle the decisions that are expensive to change later, and set up the documentation.

### Tasks

- [x] Confirm that no app is named Dewfall on the Play Store, F-Droid or IzzyOnDroid.
- [x] Create the GitHub organization dewfallapp. The username dewfall already belongs to someone else.
- [x] Search the WIPO Global Brand Database and the USPTO for Dewfall in software, Nice class 9.
- [ ] Set the package name to io.github.dewfallapp. Android treats a different package name as a different app, so it cannot change after the first release.
- [x] Create a public repository named dewfall in the dewfallapp organization, with the GPLv3 license.
- [ ] Write docs/vision.md with what the app is, who it is for, the first version's features, and the non-goals.
- [ ] Add the root files: README, CONTRIBUTING, CODE_OF_CONDUCT, SECURITY, PRIVACY and CHANGELOG.
- [ ] Create the docs folders and empty files: architecture.md, testing.md, release.md, breakage.md, adr/ and design/.
- [ ] Install OpenSpec with npm and run openspec init in the repository. It creates the openspec/ folder and sets up its commands for your AI coding tool.
- [ ] Write the first ADRs: NewPipeExtractor, MVI, GPLv3, OpenSpec, and the deletion rules.
- [ ] Create a GitHub Project board with one milestone per phase, and turn the tasks in this document into issues.
- [x] Write AGENTS.md with rules for AI coding tools: module boundaries, naming, test requirements, and no hardcoded strings.

### Your guide

Keep vision.md to one page. If the app does not fit on one page, the first version is too big.

Keep "YouTube" and the YouTube logo out of the app name and icon. That is general trademark caution, not legal advice.

The name checks were done in October 2026. The Play Store, F-Droid and IzzyOnDroid have no app named Dewfall, and the USPTO has no results. GitHub has 7 repositories with dewfall in the name, none of them Android or video apps. The github.com/dewfall username belongs to another user, so the project uses the dewfallapp organization. The WIPO Global Brand Database has four results. Only one is named Dewfall: a Korean registration owned by PNF International that covers only Nice class 3 (cosmetics). The other three only contain the word somewhere, such as the UK mark OpenRyse, owned by a person named Dewfall. None of them overlaps with software. The PATENTSCOPE results describe devices that measure or simulate dew, which do not affect the name.

An ADR needs five short parts: title, status, context, decision, and consequences. A few paragraphs is enough. The point is that a year from now you can see why you chose something and what you gave up.

AGENTS.md matters if you use AI coding tools. They read it before working in the repo, so every rule you write there saves you from repeating it in every prompt.

OpenSpec works in small changes, not whole phases. Start each feature with /opsx:propose, review the proposal, tasks and requirement changes it creates, then build it with /opsx:apply. Once the feature is merged, archive the change so its requirements move into the living specs. Group the living specs by area of the app: player, subscriptions, inbox, downloads, background-sync, cleanup, transcript-search and data-export. Phase 7, for example, becomes changes such as add-overnight-sync, add-storage-limit, add-cleanup-rules and add-battery-setup. Write every requirement with WHEN and THEN scenarios, so each one maps onto a test.

OpenSpec has two weak spots. Nothing forces you to update a spec when the code changes, so specs can drift away from the app. The pull request template line in Phase 2 is there to catch that. The workflow was also rebuilt recently, so older tutorials use different command names. Trust the current README over blog posts. OpenSpec installs through npm, so you need Node.js alongside your Android tools.

**Done when.** Someone reads vision.md and can explain the app back to you correctly.

## Phase 1. Design

**Goal.** Decide what every first-version screen looks like before writing any UI code.

### Tasks

- [ ] Write a half-page design brief in docs/design/brief.md.
- [ ] Explore three or four visual directions in Stitch.
- [ ] Pick one direction, then export its DESIGN.md and screenshots into docs/design/.
- [ ] Design the full first-version screen set in Claude Design, in light and dark.
- [ ] Build clickable prototypes of the three main flows: onboarding, inbox to player, and search inside videos.
- [ ] Export the result as HTML or .zip into docs/design/.
- [ ] Write a screen inventory in docs/design/screens.md, one line per screen with its file.
- [ ] Screen: onboarding, including channel import, storage limit, and battery setup.
- [ ] Screen: inbox home.
- [ ] Screen: search and search results.
- [ ] Screen: player in portrait, fullscreen and mini sizes.
- [ ] Screen: channel page.
- [ ] Screen: downloads and storage.
- [ ] Screen: search inside videos, including the offline text view.
- [ ] Screen: settings.
- [ ] Screen: download notification.
- [ ] Screen: empty, error and offline states.

### Your guide

**The brief.** Describe the app idea, who it is for, the mood, and the screen list. State "Android app, Material 3, light and dark mode". Ask both tools to name colors by Material 3 roles such as primary, surface and on-surface, so they map straight onto MaterialTheme. Dynamic color can replace your palette with colors from the user's wallpaper, so each layout has to work with any palette.

**Stitch.** Stitch is a free Google Labs tool at stitch.withgoogle.com that turns prompts, sketches or voice into multi-screen designs. Use it for quick exploration, not polish. A starting prompt:

*Android app, Material 3, light and dark mode. A calm YouTube client that works like a podcast app. The home screen is an inbox of new videos from subscribed channels, each with a downloaded badge, and a storage indicator at the top. No recommendations, no Shorts. Quiet and uncluttered.*

Generate several variations and compare them side by side. When you pick one, export its DESIGN.md, a Markdown file that records the design system's colors, fonts and component styles for other tools. Stitch is a Labs product and changes often, so check its export menu each time you use it.

**Claude Design.** Claude Design is available on the Pro, Max, Team and Enterprise plans. Start a project and upload your DESIGN.md and the chosen Stitch screenshots before asking for anything. Anthropic warns that without a design system the output comes out generic. A starting prompt:

*Use the attached DESIGN.md and screenshots as the design system. Design every screen in the attached brief for an Android app built with Material 3, in light and dark versions. Name colors by Material 3 roles such as primary, surface and on-surface. Then build clickable prototypes of three flows: first-launch onboarding, opening a video from the inbox, and searching inside videos and jumping to a moment.*

Claude Design exports as .zip, PDF, PPTX, standalone HTML, to Canva, or as a handoff to Claude Code. It has no Figma or image export, so take README screenshots from the real app later.

**Important.** Neither tool produces Jetpack Compose code. Treat the designs as the reference for how the app should look, and write the Compose code yourself or with Claude Code. If you use the Claude Code handoff, tell it the target is Jetpack Compose with Material 3.

**Done when.** Every first-version screen exists in light and dark, and the three main flows are clickable.

## Phase 2. Project setup and CI

**Goal.** An almost empty app with a solid structure and a pipeline that blocks bad changes.

### Tasks

- [ ] Create the Android project with these modules: app, core data, core database, the YouTube layer, the design system, and one module per feature as features arrive.
- [ ] Set up Hilt, Jetpack Compose and Material 3.
- [ ] Set up Room with schema export into the repository.
- [ ] Write the MVI base classes: state, intent, one-time effect, and reducer.
- [ ] Build the theme from the design tokens, and check it with dynamic color on and off.
- [ ] Set up formatting and lint with Spotless and ktlint, detekt, and Android Lint.
- [ ] Add the pull request workflow in GitHub Actions: formatting, lint, unit tests, and a debug build.
- [ ] Turn on branch protection for main so failing checks block merging.
- [ ] Add a pull request template with the line "OpenSpec change archived, or not needed".
- [ ] Add Renovate or Dependabot for weekly dependency update pull requests.
- [ ] Write the modules section of docs/architecture.md with a Mermaid diagram.
- [ ] Optional: import the repository into Claude Design as a design system, so new designs use your real theme.

### Your guide

The tool names in this plan come from memory. Check that each one is still maintained before you adopt it.

A reducer is a plain function that takes the old state and an intent and returns a new state. Most screen logic can then be tested in milliseconds without a phone. Write a test for the MVI base right away so the pattern is clear for later screens.

Once the theme exists in code, Claude Design can import a design system from a GitHub repository. In Claude Code, the /design-sync command pulls the design system into Claude Design, and /design creates and syncs design projects from the terminal.

**Done when.** A pull request with a failing test cannot be merged.

## Phase 3. The YouTube layer

**Goal.** All access to YouTube lives in one isolated module, so every future fix stays in one place.

### Tasks

- [ ] Wrap NewPipeExtractor behind your own interfaces for search, video details, a channel's latest uploads, stream links, and subtitles.
- [ ] Map every NewPipeExtractor type to your own models inside the module.
- [ ] Save real responses as test fixtures and write tests against them.
- [ ] Add a small debug screen that calls each function.
- [ ] Add the daily scheduled workflow that runs the layer against real YouTube and opens a GitHub issue when something fails.
- [ ] Write docs/breakage.md.

### Your guide

No code outside this module may import NewPipeExtractor classes. If it does, a YouTube change can break the whole app instead of one module.

Use a handful of stable test targets for the daily check: a few long-lived videos with subtitles and a few active channels. The daily check never blocks pull requests, because live results are unreliable by nature.

The breakage playbook in docs/breakage.md lists the steps for a failure: confirm it on a real phone, check whether NewPipeExtractor already has a fix, update the dependency, run the tests, ship a hotfix release, and pin a GitHub issue so users know a fix is coming.

**Done when.** Tests can fetch a channel's latest videos and a playable stream link, and the daily check runs green.

## Phase 4. Search and play

**Goal.** Search for any video and watch it. From here on, the app is usable every day.

### Tasks

- [ ] Search screen with results.
- [ ] Video screen with title, channel and description.
- [ ] Media3 player inside a media session service.
- [ ] Background audio with a media notification.
- [ ] Mini player and fullscreen player.
- [ ] Screenshot tests for each screen in light, dark and large font.
- [ ] First emulator test: search, then play.

### Your guide

Expose the playback position as its own stream from the Media3 session, not as part of a screen's MVI state, and update it only as often as the screen needs. A position update several times a second inside one big state object would redraw large parts of the screen and drain the battery.

Start using the app on your own phone now. You will find more bugs that way than with any test.

**Done when.** You can search for a video and watch it, and audio keeps playing with the screen off.

## Phase 5. Subscriptions and the inbox

**Goal.** Opening the app shows new videos from your channels.

### Tasks

- [ ] Subscribe and unsubscribe from a channel page.
- [ ] Room tables for channels, videos, watch status and resume position.
- [ ] Inbox home screen with new videos from subscribed channels.
- [ ] Mark as watched, with resume from the last position.
- [ ] Import subscriptions from a Google Takeout export.
- [ ] Manual refresh.
- [ ] Start writing a migration test for every database schema change.

### Your guide

Export your own YouTube data from takeout.google.com to get a real file for testing the import. Check the subscriptions file in your export to see its exact format before writing the parser.

**Done when.** Opening the app shows new videos from your channels, and resuming a half-watched video starts where you stopped.

## Phase 6. Manual downloads

**Goal.** Download a video and watch it without internet.

### Tasks

- [ ] Download button on the video screen and in the inbox.
- [ ] Downloads screen with progress, pause and cancel.
- [ ] Play from the downloaded file when it exists.
- [ ] Storage indicator showing how much space downloads use.
- [ ] Emulator test: download, switch off the network, then play.

### Your guide

Keep the download logic separate from the screens. Phase 7 reuses the same code from a background job, so nothing in it may depend on a screen being open.

**Done when.** You can download a video, switch on airplane mode, and watch it.

## Phase 7. Overnight downloads and cleanup

**Goal.** Plug the phone in at night and find new videos waiting in the morning.

### Tasks

- [ ] WorkManager job that checks subscribed channels for new uploads.
- [ ] Downloads that run only on Wi-Fi while charging.
- [ ] Automatic retries with a growing wait between attempts.
- [ ] Notification with progress while downloads run.
- [ ] Storage limit setting.
- [ ] Deletion rules and settings, as described under Decisions already made.
- [ ] "Keep" option on any video.
- [ ] Battery optimization setup screen in onboarding.
- [ ] Fallback when the app opens: offer to download missing videos now or to stream them.
- [ ] Export and import of all user data to a single file.
- [ ] Tests for the background jobs using the WorkManager test helpers.

### Your guide

Android decides the exact time a background job runs, so "overnight" means "whenever the conditions are met". Some phone brands also stop background apps aggressively to save battery. Test on as many brands as you can borrow, and note in docs/breakage.md which settings each one needs.

The data export matters because there is no account. Without it, a lost phone means lost subscriptions, notes and history.

**Done when.** You plug in at night, new videos are ready in the morning, and yesterday's watched videos are gone.

## Phase 8. Subtitles and search inside videos

**Goal.** Find the exact moment in any watched video by searching for what was said.

### Tasks

- [ ] Save subtitles when a video is watched or downloaded.
- [ ] Full-text search index with Room.
- [ ] Search screen for subtitles, with the video, the matching line, and its time.
- [ ] Tapping a result opens the video at that moment, from the file if it exists, otherwise by streaming.
- [ ] Offline text view with a few lines before and after the match.
- [ ] Setting to keep subtitles only for the last few months.
- [ ] Tests for indexing and search.

### Your guide

Not every video has subtitles. Show a small mark on videos that are searchable, so users are not surprised when one is missing from results.

**Done when.** You search for a word from a video you watched last week and land on the exact moment.

## Phase 9. Release

**Goal.** A stranger can install the app and understand it without asking you anything.

### Tasks

- [ ] Create the release signing key and back it up in two separate offline places.
- [ ] Release workflow on a version tag: build, sign with the key stored in GitHub secrets, generate the changelog, add checksums, and publish a GitHub release.
- [ ] Two build flavors: a GitHub build with an update checker the user agrees to, and an F-Droid build without one.
- [ ] Final privacy policy in PRIVACY.md.
- [ ] Issue templates asking for app version, Android version, phone model and steps to reproduce.
- [ ] "Copy debug info" button in settings.
- [ ] Final pass on onboarding and on empty, error and offline states.
- [ ] Check that every string is in resources, and test with TalkBack and large fonts.
- [ ] Startup benchmark on a real phone.
- [ ] README with screenshots taken from the real app.
- [ ] Submit to IzzyOnDroid or F-Droid after reading their inclusion requirements.

### Your guide

Android only installs an update if it is signed with the same key as the installed app. If you lose the key, every user has to uninstall and lose their data to get updates. Treat the key backup as the most important task in this phase.

Use conventional commit messages from Phase 2 onward, such as "feat: add storage limit" or "fix: crash on empty inbox". The release workflow can then build the changelog from them.

**Done when.** A stranger installs the app and understands it without asking you anything.

## After release

Add these one at a time, in the order your first users ask for them. Their feedback after Phase 9 is worth more than this plan's guess.

- [ ] Per-channel settings: speed, silence skipping, audio-only default, and a sleep timer.
- [ ] Calm features: hide Shorts, block words and channels, and a daily time limit.
- [ ] Notes at a moment in a video, exportable as Markdown.
- [ ] Community add-ons: SponsorBlock, DeArrow and Return YouTube Dislike.
- [ ] Discovery on request: similar channels, "more like this", and the opt-in weekly suggestions.

## Reference: testing and CI/CD

Fast tests run on every change. They cover reducers, use cases, repositories with fake data sources, the YouTube layer against saved responses, Room queries, background jobs, and screenshot tests of each screen. Slow tests are emulator tests of the main flows, such as search then play, and download then play offline. Benchmarks for startup and scrolling run on a real phone before each release.

The project uses five GitHub Actions workflows:

1. Every pull request: formatting, lint, unit tests, screenshot tests, a database migration check, and a debug build. Branch protection blocks merging when any of them fail.
2. Every merge to main: everything above, plus the emulator tests, and a nightly APK.
3. Daily schedule: the YouTube layer runs against real YouTube and opens an issue when something breaks. It never blocks pull requests.
4. Version tag: a signed release build, changelog, checksums, and a GitHub release.
5. Weekly: dependency update pull requests from Renovate or Dependabot, tested by workflow 1.

## Reference: documentation map

1. docs/vision.md: one page on what the app is, who it is for, and the non-goals.
2. docs/architecture.md: the design document. Modules, data flow, the MVI contract, the player, downloads and cleanup, subtitle search, database tables, and error handling.
3. docs/adr/: one short file per architecture decision.
4. openspec/: specs/ holds the living spec for each area of the app, and changes/ holds one folder per feature with its proposal, tasks and requirement changes. Finished changes move to the archive.
5. docs/design/: the brief, DESIGN.md, exported designs, and the screen inventory.
6. docs/testing.md, docs/release.md and docs/breakage.md: how to test, how to release, and what to do when YouTube breaks something.
7. Root files: README, CONTRIBUTING, CODE_OF_CONDUCT, SECURITY, PRIVACY, CHANGELOG, and AGENTS.md.

Every doc lives in the repository as Markdown and changes in the same pull request as the code it describes.
