# Proposal

## Why

Most people who try Dewfall already follow channels on YouTube. Finding each one again with Search YouTube is slow, so Phase 5 lets them bring their whole list from a Google Takeout export, a file Google makes for them and that they pick on the phone ([#51](https://github.com/dewfallapp/dewfall/issues/51)). Dewfall has no accounts, so a file is the only way in. #48 gave the app a place to keep subscriptions, and #47 lets the user add one at a time. This change adds them all at once.

## What Changes

- **An import screen.** It tells the user how to get the file from takeout.google.com, as the design's "Onboarding 2, import channels" frame does, and has a "Choose Takeout file" button that opens Android's own file picker. The user may pick the .zip that Takeout makes, or the subscriptions .csv file inside it. Dewfall reads only that file, only once, and only after the user picked it. It needs no permission.
- **Reading the file.** Dewfall finds the subscriptions table in the file and reads each channel's ID and name from it. It doesn't rely on the names of files, folders or columns, because Takeout translates them into the account's language. A line it can't read is skipped and counted, and the rest are still imported.
- **Subscribing.** Every channel found is subscribed, in one step, with its ID and the name from the file. A channel that was already subscribed keeps its details and its time of subscribing. Importing the same file again adds nothing. Avatars and descriptions come later, with the first refresh of the channel's uploads (#52), as #48's subscriptions spec already says.
- **The report.** After the import, the screen shows how many channels it found and lists them, how many were new and how many were already subscribed, and how many lines it couldn't read. If the file holds no channels, can't be opened, or is too large to be a subscriptions file, the screen says so and offers to choose another file.
- **Where it opens from.** The design puts an "Import from Google Takeout" button on the inbox's "No channels yet" state, and on the channels screen's. The inbox is #49's, which runs in parallel, and the channels screen is #115's. This change's pull request rebases on #49 once it merges and connects that button to the import screen, so the import is reachable when it merges. #115 connects its own button the same way. The onboarding flow, which also has this step, comes later (#83) and can reuse the screen. Once the user has channels, neither button shows, so nothing opens the import again; that follows the design, and #115 or Settings can add a way in later.

## Capabilities

### New Capabilities

None.

### Modified Capabilities

- `subscriptions`: adds what importing a Takeout file subscribes to, which lines it reads and skips, what it reports, and that the file stays on the phone and is read only when the user picks it.
- `inbox`: the "No channels yet" state gets the "Import from Google Takeout" button. #49 makes this spec, and archives it before this change merges, so this delta becomes a MODIFIED delta of its requirement "The inbox says when it is empty" when this change rebases on #49. Until then it is written as an ADDED requirement, so the change validates.

## Impact

- New plain Kotlin/JVM module `:core:takeout`: the reader of Takeout files (zip and CSV), with no Android imports, and its JVM tests with made-up files.
- `:core:model`: the import's result type.
- `:core:database`: ChannelDao gets a batch subscribe in one transaction, with an emulator test in its androidTest.
- `:core:data`: an import repository, which opens the picked file through Android's ContentResolver, reads it with `:core:takeout` and subscribes through the new batch subscribe; SubscriptionsRepository gets `subscribeAll`. JVM tests with fakes.
- New feature modules `:feature:takeout-import-logic` (state, intents, reducer and its tests) and `:feature:takeout-import-ui` (screen, ViewModel, previews, screenshot tests, ViewModel tests).
- `:app`: a navigation key for the import screen, and the inbox button's action after #49 merges.
- Docs: docs/architecture.md (modules, data flow, the import), AGENTS.md (modules and where things are), docs/testing.md (the hand check with a made-up file), and PRIVACY.md (the import reads a file you pick and sends it nowhere).
- Dependencies: `:core:data` declares androidx.core:core-ktx 1.18.0 (Apache 2.0, released on March 11, 2026, by the Last-Modified date of its POM on Google's Maven, checked on November 23, 2026), which the app already shipped through other AndroidX libraries, for `String.toUri`, which lint asks for. It sends nothing anywhere. The emulator test adds androidx.test.espresso:espresso-intents 3.7.0, which never ships. Zip reading uses the JDK's java.util.zip, which Android also has.
- Privacy: the file and everything in it are user data. Dewfall keeps only the channel IDs and names, never logs them or the file's name or link, and sends nothing anywhere. Test files are made up, with placeholder IDs.
