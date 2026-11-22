# Design

## Context

See proposal.md for why. What this change builds on:

- #48 made the `channels` table and SubscriptionsRepository in `:core:data`. `subscribe(channel)` inserts one row with `OnConflictStrategy.IGNORE`, so a second subscribe keeps the first row, and every write runs in the `@ApplicationScope` CoroutineScope, so it finishes when its caller is cancelled. #48's design names #51 as the user of a batch subscribe, and its subscriptions spec already has the scenario "A subscription from a file": a channel subscribed with only its ID and name gets its avatars and description from the next save of its uploads.
- ChannelId in `:core:model` checks the format, "UC" and 22 characters from A to Z, a to z, 0 to 9, `-` and `_`, and `parseOrNull` is there for text from outside the app, such as this file. Its toString hides the ID.
- ADR 0006 lists "Opening the Takeout file" under the Storage Access Framework, from Android 4.4 with no permission. ADR 0007 counts the picked file's name, path and content URI as user data.
- The ViewModel takes no dispatcher (AGENTS.md), so blocking reads run in a repository, on an injected dispatcher.
- The design's frames: "Onboarding 2, import channels" (the steps for getting the file, "Choose Takeout file"), "Flow 1: channels imported" ("4 channels found", the list, Continue), and the "No channels yet" states of the inbox and the channels screen, each with "Import from Google Takeout" under "Search YouTube". docs/design/screens.md lists them. The onboarding flow itself is not a Phase 5 task.
- The maintainer exported a Takeout file, and it was read on the computer for a check that compares counts only. Nothing from it, not its paths, rows, IDs, names, counts or statistics, is in this repository. The format below comes from public sources and the made-up test files.
- Takeout's names depend on the account's language. Invidious's guide says the steps and the file's name differ with Google's language settings ([Export YouTube subscriptions](https://github.com/iv-org/documentation/blob/master/docs/export-youtube-subscriptions.md), checked on November 22, 2026). NewPipeExtractor's subscription reader notes that the file always has three columns, that the header's names depend on the locale, and that the data keeps its order in every locale ([YoutubeSubscriptionExtractor at v0.27.1](https://github.com/TeamNewPipe/NewPipeExtractor/blob/v0.27.1/extractor/src/main/java/org/schabi/newpipe/extractor/services/youtube/extractors/YoutubeSubscriptionExtractor.java), checked on November 22, 2026). In a .zip, it takes the first .csv entry that gives at least one channel.
- Android 14 and later throw ZipException from `ZipInputStream.getNextEntry()` for entry names that contain `..` or start with `/`, in apps that target API 34 or higher ([Android 14 behavior changes, Zip path traversal](https://developer.android.com/about/versions/14/behavior-changes-14#zip-path-traversal), checked on November 22, 2026). Takeout's names don't, and such a file ends as NOT_READABLE. The JVM tests can't show it, so a test covers the mapping of ZipException instead.

## Goals / Non-Goals

**Goals:**

- A reader that needs nothing from Android, tested on the JVM with made-up files for every rule in the delta spec.
- An import that leaves the subscriptions either as they were or with every channel in the file.
- A screen that the onboarding flow (#83) and the channels screen (#115) can open as it is.

**Non-Goals:**

- Fetching avatars, descriptions or videos after the import. #52's manual refresh and Phase 7's overnight sync fetch every subscribed channel, and fill them through `saveUploads`. How far back the first fetch after an import reaches, the brief's open question, is #52's to decide for the inbox and Phase 7's for downloads; this change saves no videos. The inbox shows the imported channels' videos after that refresh.
- What the inbox shows with channels but no saved videos yet. That is #49's.
- The onboarding flow, its step counter and its "Skip for now" (#83), and the channels screen's own button (#115).
- Dewfall's own export and import of all its data (#68, Settings' "Your data").
- Unsubscribing from channels that are missing from a new file. An import only adds.
- Other formats, such as the YouTube Data API's JSON or NewPipe's export. Takeout exports subscriptions as a .csv file, as Invidious's guide and NewPipeExtractor's notes say.

## Decisions

### Modules

| Module | Kind | Holds |
|---|---|---|
| `:core:takeout` (new) | Plain Kotlin/JVM | TakeoutReader: bytes in, channels and counts out. Depends on `:core:model` for ChannelId. |
| `:core:model` | Plain Kotlin/JVM | TakeoutImportResult, the result that the repository gives the screen |
| `:core:database` | Android | ChannelDao's `subscribeAll` |
| `:core:data` | Android | TakeoutImportRepository, DocumentOpener, `SubscriptionsRepository.subscribeAll` |
| `:feature:takeout-import-logic` (new) | Plain Kotlin/JVM | State, intents and reducer |
| `:feature:takeout-import-ui` (new) | Android | The screen, its ViewModel, previews and screenshot tests |
| `:app` | Android | TakeoutImportKey and the navigation to it |

Why a core module for the reader, and not one of these:

- `:feature:takeout-import-logic`: the repository in `:core:data` runs the reader, because the ViewModel takes no dispatcher, and core modules never depend on feature modules.
- `:core:data`: it is an Android library, and AGENTS.md puts code that needs nothing from Android in plain Kotlin/JVM modules.
- `:core:model`: it holds Dewfall's models and small rules about them. A CSV and zip reader is a different job, and other features don't need it.
- `:core:youtube`, with NewPipeExtractor's YoutubeSubscriptionExtractor: the YouTube layer is Dewfall's client for YouTube's servers, and reading a local file isn't that. The extractor also splits lines at every comma, so it breaks a name with a comma, which RFC 4180 allows in quotes, and it drops the lines it can't read without counting them, which the report needs.

The module named "takeout" keeps the door open for the later formats in Non-Goals without promising them.

### Telling a .zip file from a .csv file

The reader looks at the file's first four bytes. `PK` followed by bytes 3 and 4 starts a .zip file's first entry, and `PK` followed by 5 and 6 starts an empty .zip file ([APPNOTE.TXT](https://pkwaredownloads.blob.core.windows.net/pkware-general/Documentation/APPNOTE-6.3.9.TXT), sections 4.3.7 and 4.3.16). Anything else is read as a .csv file. It doesn't use the file's name or the type that the picker reports. The name is user data the app needn't read, and the type is whatever the app that holds the file says.

The picker asks for every type, `*/*`. Each app that holds files, such as the phone's downloads, a file manager or a cloud drive, reports a file's type itself, and no source lists what each of them reports for a .zip or a .csv file. A filter on a list of types could hide the user's own export, and the user couldn't pick it. Showing every file costs a longer list, and a wrong file only means a clear "no channels" message, because the reader checks the content. Alternative: a list such as `application/zip` and `text/csv`, rejected for that reason.

If the user picks another kind of archive, such as a .tgz file, it is read as a .csv file and ends as "no channels". The import screen's steps say to download the .zip file, as the design's frame does, and the "no channels" message names the .zip file.

### Reading a .zip file as a stream

The repository gets an InputStream from ContentResolver, and the reader wraps it in java.util.zip's ZipInputStream, which reads entries in the order they are stored and handles a data descriptor after an entry's data (APPNOTE.TXT section 4.3.9), which ZipOutputStream also writes. Java's ZipFile, which reads the table at the end of the file, needs a file on disk, and the picker gives a stream. Copying the user's file into the app's cache first would make a second copy of user data, of a file whose size the app doesn't control. A library that reads the table at the end, such as Apache Commons Compress, would be a new dependency that ships, for a file that Dewfall's own steps tell the user to keep small.

The reader skips entries whose names don't end in `.csv`, ignoring case, without keeping their bytes. It reads each .csv entry as a table, up to 8 MiB, and keeps the one with the most channels, the first of them on a tie. A .csv entry over 8 MiB is passed over. Keeping the largest, rather than NewPipeExtractor's first, means a small three-field table with channel IDs, if a full export ever holds one, can't hide the subscriptions behind it. Entry names are read only for their last four characters, and never logged.

### Reading the table

A small reader written for this, about a hundred lines, follows RFC 4180 ([RFC 4180](https://www.rfc-editor.org/rfc/rfc4180), section 2) and accepts a little more:

- Fields separate at commas. A field that starts with a double quote ends at the next double quote that isn't doubled, and may hold commas, line breaks and doubled double quotes, which become one. A double quote inside a field that didn't start with one stays as it is. A quoted field that the file ends inside ends with the file.
- Lines end at a line feed, or at a carriage return and a line feed. A lone carriage return outside quotes also ends a line.
- A UTF-8 byte order mark at the start is dropped. The text is read as UTF-8, and bytes that aren't UTF-8 become U+FFFD, so they can only spoil a name, never an ID, which is ASCII.
- A blank line is a line with no characters, or only spaces and tabs.

Then, per line:

| Line | Counts as |
|---|---|
| Blank | Nothing |
| The first line that isn't blank, if its first field isn't a channel ID | The header: nothing |
| Exactly three fields, the first a channel ID by `ChannelId.parseOrNull` after trimming, the third not empty after trimming | A channel, with the trimmed third field as its name |
| A channel ID that an earlier line of the same table had | Nothing: the first line wins |
| Anything else | A line it couldn't read |

The link in the second field isn't checked. The ID comes from the first field, as NewPipeExtractor's notes say, and checking the link would break the import if YouTube changed its link's form. The field count is checked, so a table that also starts with a channel ID but has other fields, such as one about the user's own channel if an export holds one, isn't imported.

Alternative: Apache Commons CSV, Apache 2.0. It would be a new dependency that ships, and the rules above that matter here, the counts, the header and the field check, would still be Dewfall's code. The reader's own tests cover each rule.

### The limits

A line with a 24-character ID, a link of about 56 characters and a name is about 100 bytes, so 8 MiB holds about 80,000 channels, far more than any list of subscriptions. A .csv file over it is very likely not a list of subscriptions, and the limit keeps the whole table, and the list of channels in the screen's state, small enough for any phone. The reader counts the bytes it reads from an entry or a .csv file, and stops at the limit, so a .zip file whose entry claims a small size but holds more can't get past it.

A .zip file has its own limit: the reader stops after 256 MiB of the .zip file itself and gives TooLarge. Reading every entry, to find the table with the most channels, means reading the whole file, and a file picked from a cloud drive is downloaded as it is read, maybe over mobile data, which docs/vision.md's readers may pay for. An export of only subscriptions, which the screen's steps ask for, is tiny, and 256 MiB leaves room for one with more of YouTube's options but no videos. The "too large" message then says to export only the subscriptions.

### The result

`:core:takeout` returns a TakeoutFile:

- Subscriptions(channels: list of TakeoutChannel(id: ChannelId, name), unreadableLines)
- NoSubscriptions
- TooLarge

It throws IOException for a stream that fails and for a damaged .zip file (ZipException is an IOException), and lets the caller's CancellationException through, and nothing else. ZipInputStream's `getNextEntry()` throws IllegalArgumentException, not ZipException, when an entry's name isn't valid UTF-8: in OpenJDK 17, `ZipInputStream.readLOC` decodes UTF-8 names through `ZipCoder.toStringUTF8`, which ends in `String.newStringUTF8NoRepl`, and `String.throwMalformed` throws IllegalArgumentException there; `readLOC` doesn't catch it ([ZipCoder.java](https://github.com/openjdk/jdk/blob/jdk-17%2B35/src/java.base/share/classes/java/util/zip/ZipCoder.java) and [String.java](https://github.com/openjdk/jdk/blob/jdk-17%2B35/src/java.base/share/classes/java/lang/String.java) at jdk-17+35, checked on November 22 and 23, 2026). Android's own copy may differ, so the catch covers both, and a JVM test shows the mapping. A .zip file re-made by a tool that writes non-ASCII names in a local code page, as a translated Takeout's folder names would be, hits it. So the reader catches IllegalArgumentException from ZipInputStream and throws a ZipException instead, which ends as NOT_READABLE. The repository turns that into Dewfall's model in `:core:model`:

- TakeoutImportResult.Imported(channels: list of Channel, added, alreadySubscribed, unreadableLines)
- TakeoutImportResult.Failed(reason), with reason one of NO_CHANNELS, NOT_READABLE and TOO_LARGE

`channels` keeps the file's order, with no avatars and an empty description. `alreadySubscribed` is the number of channels minus `added`.

### Subscribing in one step

ChannelDao gets `@Insert(onConflict = OnConflictStrategy.IGNORE) suspend fun subscribeAll(channels: List<ChannelEntity>): List<Long>`. It runs in one transaction, gives each row's row ID, or -1 for a row that the conflict ignored, so the number of row IDs that aren't -1 is the number of new subscriptions. Sources:

- Room 3's [Insert](https://developer.android.com/reference/kotlin/androidx/room3/Insert) page, checked on November 22, 2026, allows `List<Long>` as the return type of an insert of a list.
- Room 3.0.3's runtime: `EntityInsertAdapter.insertAndReturnIdsList` runs the one prepared statement for each row, and `getLastInsertedRowId` returns -1 when SQLite's `changes()` is 0, as it is for an ignored row ([room3-runtime-android-3.0.3-sources.jar](https://dl.google.com/android/maven2/androidx/room3/room3-runtime-android/3.0.3/room3-runtime-android-3.0.3-sources.jar), EntityInsertAdapter.kt and util/ConnectionUtil.kt, read on November 22, 2026).
- Room 3.0.3's compiler: `CoroutineInsertOrUpsertFunctionBinder` makes a suspend insert call `performSuspending(db, isReadOnly = false, inTransaction = true)` ([room3-compiler-3.0.3-sources.jar](https://dl.google.com/android/maven2/androidx/room3/room3-compiler/3.0.3/room3-compiler-3.0.3-sources.jar), androidx/room3/solver/shortcut/binder/CoroutineInsertOrUpsertFunctionBinder.kt, line 53, read on November 22, 2026). Task 2.1 also checks that the generated ChannelDao_Impl in core/database/build/generated/ksp/ calls it that way.

Each row is its own run of one prepared statement, so SQLite's limit of 999 bound values per statement before 3.32.0 ([Limits in SQLite](https://www.sqlite.org/limits.html#max_variable_number), checked on November 22, 2026), which Android 11 (SQLite 3.28) and older have ([android.database.sqlite](https://developer.android.com/reference/android/database/sqlite/package-summary), checked on November 22, 2026), doesn't apply. The DAO test inserts more than 999 rows.

SubscriptionsRepository gets `subscribeAll(channels: List<Channel>): Int`, which gives every row the same time of subscribing, `clock.now()`, runs in the `@ApplicationScope` scope like `subscribe`, and returns the number added. An empty list returns 0 without touching the database.

Alternative: calling `subscribe` once per channel. Each would be its own transaction, the channels list's Flow could emit in between, and a failure halfway would leave half a file.

### The repository

TakeoutImportRepository in `:core:data`:

1. `importFrom(file: String)`, where `file` is the picked content URI as text, so the ViewModel and its tests need no android.net.Uri.
2. On an injected dispatcher, Dispatchers.IO in the app, it opens the file through DocumentOpener, a small interface whose binding in `:core:data` calls `ContentResolver.openInputStream(Uri.parse(file))`, and reads it with TakeoutReader. It closes the stream in every case.
3. It maps IOException, SecurityException, a null stream and IllegalArgumentException from the resolver to NOT_READABLE, and the reader's NoSubscriptions and TooLarge to their reasons. Any other exception is a bug and isn't caught.
4. Then it calls `SubscriptionsRepository.subscribeAll`, which holds the clock and the application scope, and returns Imported. It doesn't reach ChannelDao itself.

The read runs in the caller's coroutine, so leaving the screen while reading cancels it before anything is written. A blocking read can't be interrupted, so the repository wraps the opened stream in one that checks the caller's Job before each read and throws its CancellationException once it is cancelled. The reader lets that through, as it lets any exception but its own results through, so a long read of a large .zip file stops at the next chunk, and `withContext` rethrows the cancellation before the write. The write runs in the application scope, so once started it finishes.

It logs, through the Logger with the tag Dewfall.Takeout, one line per failure with the reason as an enum name, and one line per import with the three counts as numbers. Never the file, its name, its link, or any ID or name.

### The screen

The logic, in `:feature:takeout-import-logic`:

- State: `Idle`, `Reading`, `Imported(channels, added, alreadySubscribed, unreadableLines)` or `Failed(reason)`. The channel rows hold the ID, for list keys, and the name.
- Intents: `FilePicked(file)` and `ImportFinished(result)`. No effects.
- `FilePicked` moves any state but Reading to Reading. While Reading, it changes nothing. `ImportFinished` counts only while Reading.
- The ViewModel runs the import for a FilePicked that moved the state to Reading, in viewModelScope.

Opening the picker is a UI action that changes no state, so it goes straight to `rememberLauncherForActivityResult(OpenDocument())`. A null result, the closed picker, dispatches nothing. Back and Done are navigation actions to `:app`.

The layout follows the design's frames, in one centered column at most 640dp wide, edge to edge with the window insets:

- A back arrow at the top, as in the frames, with the content description "Back". The arrow is design system's ic_arrow_back, the same file as #47's channel page adds.
- Idle: "Bring your channels", the frame's two sentences, "Getting the file" with its three numbered steps and "Google's labels can differ with your language.", and the "Choose Takeout file" button. It leaves out the frame's "Step 2 of 4" and "Skip for now", which belong to the onboarding.
- Reading: the same text, with a progress indicator and "Reading the file" where the button was.
- Imported: "N channels found", "From your Takeout file. You can add or remove channels at any time in Channels.", one line with the counts that aren't zero ("35 new. 5 were already in Dewfall. 2 lines couldn't be read."), the channels as rows with ChannelEmblem's initial and the name, and a Done button that stays at the bottom.
- Failed: the Idle text, with a banner above the steps that gives the reason's message, and "Choose another file" in place of the button.

The channel rows show no description line, because the file has none. Counts use plurals in strings.xml.

### Where it opens from

`:app` gets TakeoutImportKey and an entry for it, with back and Done closing it. The inbox from #49 exposes the empty state's "Import from Google Takeout" as a plain action, which `:app` turns into opening the key, and #115 does the same for the channels screen. #49's session agreed on November 22, 2026 that its empty state ships with "Search YouTube" only, and that the pull request that lands second adds the button and its wiring, so no button does nothing. #49 is likely first, so this change adds `onImportFromTakeout` to `Inbox(...)` in `:feature:inbox-ui`, the "Import from Google Takeout" text button under "Search YouTube" in its "No channels yet" state, the button's string, that state's screenshot images, and the line in `:app`. If #49's change is archived by then, the requirement on the empty inbox goes into this change as a MODIFIED delta of the inbox spec's "The inbox says when it is empty", in place of this change's ADDED requirement. Until #49 merges, the screen has no way in, so this change isn't merged before #49.

### Tests

- `:core:takeout`: one JVM test or more per scenario of the delta spec's reading rules, with files built in the test from made-up channels whose IDs are "UC" and 22 placeholder characters, such as `UCtakeouttest00000000001`. Zip files are built with ZipOutputStream, which writes a data descriptor after each deflated entry. A damaged .zip file is a built one cut short. The 8 MiB limit is tested on a plain .csv file, on a .zip entry over it followed by a real table, and on a deflated entry whose local header claims no size, because its sizes are in the data descriptor after it.
- `:core:database`: ChannelDaoTest gets tests for `subscribeAll`: the count of new rows, a row already there keeps its name and time, a list of 1,200 channels, and an empty list. That the batch is one transaction comes from the compiler's generated code, which task 2.1 checks, because a Flow test of it could pass without one: Room can merge invalidations.
- `:core:data`: TakeoutImportRepositoryTest with a fake DocumentOpener and a recording fake SubscriptionsRepository: each failure reason, a ZipException mapped to NOT_READABLE, the counts, that cancelling during a read of a stream that never ends stops the read and writes nothing, and that nothing it logs, through a recording log writer, holds a made-up ID, name or file link, including for a ZipException whose message names a made-up entry, since the Logger writes an error's class names only. RoomSubscriptionsRepository's test gets `subscribeAll`: the count, one time for the batch, an empty list, and a write that finishes when the caller is cancelled.
- `:feature:takeout-import-logic`: the reducer's tests, one per transition.
- `:feature:takeout-import-ui`: ViewModel tests with a fake repository, and screenshot tests of Idle and Imported in light, dark, large font and dynamic color, Imported on a tablet, Reading and each Failed reason in light and dark, the no-channels failure at large font, and Imported with only new channels in light.
- `:app`: an emulator test that opens the import screen from the empty inbox, answers the picker with a made-up .csv file that the test writes to the app's cache folder, and checks the report and the subscriptions. The existing tests start MainActivity with `createAndroidComposeRule<MainActivity>`, whose ActivityResultRegistry a test can't replace without a hook in shipped code. So the test uses espresso-intents: `Intents.init()`, then `intending(hasAction(Intent.ACTION_OPEN_DOCUMENT)).respondWith(...)` with a result whose data is the file's `file://` URI, which ContentResolver opens too. It is a new androidTest dependency, `androidx.test.espresso:espresso-intents` 3.7.0, the version of espresso-core already in gradle/libs.versions.toml: Apache 2.0, released on July 30, 2025, never ships. Its POM also names androidx.test:rules, runner and core 1.7.0, espresso-core 3.7.0, androidx.annotation 1.7.0, error_prone_annotations 2.30.0 (Apache 2.0), junit 4.13.2 (EPL 1.0), hamcrest-core and hamcrest-library 1.3 (BSD) and kotlin-stdlib 1.9.21. The pull request lists each one that the androidTest classpath doesn't already have, with its license and date, under "New dependencies". None ships. It uses an in-memory database through Hilt if #47 or #49 adds that replacement first, or adds it.
- By hand: on one emulator, pick a made-up .zip file and a made-up .csv file from Downloads, and check the report, back, Done, and the screen in dark mode and at the largest font. On the computer, a JVM test that isn't committed reads the maintainer's real file and compares only counts. Its result goes in the hand-off, never into the repository.

## Risks / Trade-offs

- [Google changes the subscriptions file's shape, such as a fourth column] → The import says the file holds no channels, and no wrong channel is subscribed. docs/breakage.md gets a short section on what to check, and the made-up test files make the fix quick.
- [A Takeout export split into parts, with the subscriptions in a part the user didn't pick] → The "no channels" message names the .zip file from Takeout and the subscriptions file. The import screen's steps tell the user to tick only subscriptions, which keeps the export to one small part.
- [Another three-field table with channel IDs in a full export] → The reader keeps the table with the most channels, so only a table larger than the subscriptions could win. No such table is known, and the steps tell the user to export only subscriptions.
- [A very large .zip file is read to its end] → Reading a .zip file goes through every entry, to find the table with the most channels, which can take a while for a large export, and downloads it when it is in a cloud drive. The 256 MiB limit bounds both. The screen shows that it is reading, and leaving it stops the read at the next chunk and writes nothing. Takeout's steps keep the file small.
- [Disk full or a database error during `subscribeAll`] → It fails as #48's other writes do, as a crash with no partial import, because the insert is one transaction. Showing it as a message is left to a general error task, as for the other writes.
- [Process death while reading] → The screen comes back in Idle, and nothing was written. If it happened after the write started, the channels are there, and a second import of the same file adds nothing.
- [The report lists all channels in the file, which can be hundreds] → The list is lazy, keyed by ID.

## Migration Plan

No migration. The `channels` table doesn't change, and the database stays at version 1.
