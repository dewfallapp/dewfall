# Design

## Context

See proposal.md for why. What this change builds on:

- `:core:database` applies dewfall.android.room: KSP, the Room 3 Gradle plugin, room3-runtime 3.0.3 and room3-compiler, with KSP warnings failing the build, and core/database/schemas/ as the schema folder. It has no code yet. The pull request workflow fails when the build changes or adds a file in core/database/schemas/.
- `:core:data` applies dewfall.android.hilt and depends on `:core:database` as an implementation dependency, so feature modules can't see the database. It holds the YouTube repositories, each an interface with one implementation bound in a Hilt module.
- `:core:model` holds ChannelId and VideoId, which check their format and leave the ID out of toString, Channel, VideoSummary, VideoKind, PublishedTime and Thumbnail. Times are kotlin.time's Instant and lengths its Duration (ADR 0008).
- ChannelUploads holds a channel and its latest long-form videos and live streams, never Shorts. Its channel's ID can differ from the one asked for, because YouTube sends some channels on to another one.
- The app's manifest sets `android:allowBackup="false"`.
- The app supports Android 10 (API 29) and later (ADR 0006). Android 10 has SQLite 3.22: the [android.database.sqlite reference](https://developer.android.com/reference/android/database/sqlite/package-summary), checked on November 22, 2026, lists 3.22 for API 28 and 3.28 for API 30, and an API level that isn't listed uses the next lower one. SQLite's `INSERT ... ON CONFLICT DO UPDATE` needs 3.24.0 ([UPSERT](https://www.sqlite.org/lang_upsert.html)), so no query may use it.
- Room 3.0.3's sources, read on November 22, 2026 from [room3-runtime-android-3.0.3-sources.jar](https://dl.google.com/android/maven2/androidx/room3/room3-runtime-android/3.0.3/room3-runtime-android-3.0.3-sources.jar) and [room3-common-jvm-3.0.3-sources.jar](https://dl.google.com/android/maven2/androidx/room3/room3-common-jvm/3.0.3/room3-common-jvm-3.0.3-sources.jar): converters are `@ColumnTypeConverter` functions registered with `@ColumnTypeConverters` (Room 2's `@TypeConverter` was renamed), enums have a built-in converter by name, `@Insert` and `@Update` take an `entity` for partial entities, `@Transaction` works on non-abstract functions of an abstract `@Dao` class, DAO functions may return Flow, and `Room.databaseBuilder<T>(context, name)` uses AndroidSQLiteDriver when no driver is set.
- docs/testing.md#database-migration-tests says Google doesn't recommend Robolectric for Room, and that Room tests on the computer's JVM would need a Kotlin Multiplatform JVM target, which ADR 0006 rules out. The comment on #48 asks to check that again, since Room 3 runs on the SQLiteDriver APIs. [Where the DAO tests run](#where-the-dao-tests-run) answers it.
- ChannelUploads' times come mostly from relative times, such as "Streamed 2 years ago", so most are approximate, and a later fetch gives a vaguer estimate from a later "now" ([A channel's latest uploads](../../../docs/architecture.md#a-channels-latest-uploads)).
- SQLite before 3.32.0 allows at most 999 bound values in one statement ([Limits in SQLite](https://www.sqlite.org/limits.html#max_variable_number), checked on November 22, 2026), so Android 11 (API 30, SQLite 3.28) and older have that limit, and Android 12 (API 31, SQLite 3.32) is the first without it. SQLite added `NULLS LAST` in 3.30.0 ([SQLite 3.30.0 release log](https://www.sqlite.org/releaselog/3_30_0.html), checked on November 22, 2026), so no query may use it either.

## Goals / Non-Goals

**Goals:**

- Tables that #47, #49, #115, #50, #51 and #52 can use as they are, and that Phases 6 to 8 can add tables beside without reshaping these.
- Repositories that hide Room from feature modules and speak Dewfall's models.
- Every requirement in the delta specs tested against a real SQLite database.

**Non-Goals:**

- The inbox query and the rule for which videos count as new. That is #49's. The columns it needs are here: `subscribed_at`, `first_seen_at`, `published_at`, `kind` and `watch_state`. The one index starts with `channel_id`, so an inbox query that orders all subscribed channels' videos by `published_at` may want its own index, which #49 adds.
- Reading a channel's saved videos as a list. No task through #52 needs it: the channel page (#47) shows YouTube's own list, the channels screen (#115) needs only the latest time, and the inbox (#49) writes its own query. So the repositories have no read of videos yet. VideoDao's `channelVideos` keeps the order for #49 to build on, as [YouTube's order for equal times](#youtubes-order-for-equal-times) says.
- The order of the channels screen and its "Last upload" line (#115). `subscribedChannels` returns channels by name, ignoring case for ASCII letters, then by ID, for now, and `videos`' index serves a latest-upload query.
- Calling YouTube. Fetching a channel's uploads is for the task that needs it first, #47 or #52. This change only saves what they fetched.
- A batch subscribe for the Takeout import (#51), saving a single video that the user plays from search (#50), downloads and the "keep" mark (Phases 6 and 7), and subtitles (Phase 8). Each adds its own DAO functions or tables keyed by video ID.
- Migrations. The database stays at version 1 until the first public release.

## Decisions

### Three tables

| Table | Primary key | Columns | Holds |
|---|---|---|---|
| `channels` | `id` TEXT | `name` TEXT, `description` TEXT, `avatars` TEXT (JSON), `subscribed_at` INTEGER | One row for each subscribed channel |
| `videos` | `id` TEXT | `channel_id` TEXT, `channel_name` TEXT, `title` TEXT, `duration_ms` INTEGER NULL, `published_at` INTEGER NULL, `published_approximate` INTEGER (0 or 1), `kind` TEXT, `thumbnails` TEXT (JSON), `first_seen_at` INTEGER, `listed_at` INTEGER NULL, `list_position` INTEGER NULL | One row for each video Dewfall saved. Index on (`channel_id`, `published_at`). |
| `watch_state` | `video_id` TEXT | `position_ms` INTEGER, `watched_at` INTEGER NULL, `updated_at` INTEGER | One row for each video the user played or marked |

Times are milliseconds since the Unix epoch, in UTC. Every column is NOT NULL unless marked NULL.

How later tasks use them:

| Task | Uses |
|---|---|
| #47 Subscribe on a channel page | `subscribedChannel`, `isSubscribed`, `subscribe`, `unsubscribe`. Whether it also saves the page's uploads is #47's choice. |
| #49 Inbox | `videos` joined with `channels` (subscribed only) and `watch_state` (unwatched), filtered by `kind`, in the order of VideoDao's `channelVideos` (newest first, YouTube's order for equal times), with its own "new" rule on `first_seen_at` and `subscribed_at` |
| #115 Channels screen | `subscribedChannels`, and MAX(`published_at`) per channel from `videos` for "Last upload" |
| #50 Resume and mark as watched | `savePosition`, `markWatched`, `markUnwatched`, `watchState` |
| #51 Takeout import | `subscribe` with the ID and name from the file, and empty avatars and description, which the next `saveUploads` fills |
| #52 Manual refresh, Phase 7 sync | `subscribedChannels` to know what to fetch, then `saveUploads` per channel |
| Phase 6 downloads, Phase 7 cleanup and "keep" | A `downloads` table keyed by video ID. Cleanup reads `watch_state.watched_at` for "next night", "after a week" and "right away". |
| Phase 7 export | Every table |
| Phase 8 subtitles | Subtitle tables and the full-text index keyed by video ID, with the title from `videos` |

### `channels` holds subscribed channels only

- **Choice:** a channel row exists only while the user is subscribed. Unsubscribing deletes it. `subscribed_at` is NOT NULL.
- **Why:** no task through Phase 8 needs details of a channel the user doesn't subscribe to. A video already carries its channel's ID and name. One table answers "which channels" and "since when" with no join.
- **Alternative:** a `channels` table for every channel seen, with a `subscriptions` table or a nullable `subscribed_at`. Rejected: saving a video from search would then have to merge a channel's name into a row without wiping its avatars, and every query of the subscribed channels would need the extra condition.

### `videos` has no foreign key to `channels`

- **Choice:** `channel_id` and `channel_name` are plain columns, copied from VideoSummary.
- **Why:** later tasks save videos from channels the user never subscribed to, such as a video played from search (#50) or downloaded (Phase 6). Unsubscribing must not delete videos, their watch state, or later their downloads. A foreign key would either block the delete or cascade it.
- **Alternative:** a foreign key with ON DELETE SET NULL. Rejected: a video always has a channel, and the ID is needed to show it again.

### `watch_state` is its own table, with no foreign key

- **Choice:** the user's own data about a video lives apart from the details YouTube gives, keyed by video ID only.
- **Why:** a refresh rewrites YouTube's details, and must never touch what the user did. The position can be saved for any video ID, before or without its details being saved, so #50 can save it from the player, which knows only the video's ID. The channel page (#47) can read the watch states of videos it got from YouTube. An orphaned row is a few bytes, and Phase 7's export keeps it.
- **Alternative:** watch columns on `videos`. Rejected: every save of YouTube's details would have to leave them alone, and a video would need saving before its position could be.

### Saving videos merges them with what is saved

- **Choice:** VideoDao's `saveChannelUploads` runs in one `@Transaction`: it reads the saved rows for the uploads' video IDs, in chunks of at most 500 IDs, merges each new video with its saved row in Kotlin with `mergeVideo(saved, new)`, writes the videos that weren't saved with `@Insert` and the saved ones with `@Update`, and runs an UPDATE of the channel's row with `mergeChannelDetails`. The UPDATE matches no row when the channel isn't subscribed, so it never subscribes one.
- **The merge rule,** a pure function in `:core:database`, as the subscriptions spec states it:
  - The title, channel ID, channel name and kind come from the new save.
  - The length and the thumbnails come from the new save, unless it has none, and then the saved ones stay.
  - The publish time comes from the new save only when it is exact, when none was saved, or when the saved video was UPCOMING and the new one isn't. Otherwise the saved time and its `published_approximate` stay. A new save with no time keeps the saved one.
  - A video ID that appears twice in one save is kept once, at its first place, as the YouTube layer already does (UploadOrder.kt's `distinctBy`). The DAO does it again, so a caller's list can't break the transaction with a primary key error, and VideoDaoTest covers it.
  - `first_seen_at` stays. A video with no saved row gets the time of the save.
  - `listed_at` becomes the time of the save, and `list_position` the video's place in the uploads' list, counted from 0. See [YouTube's order for equal times](#youtubes-order-for-equal-times).
  - For the channel: the name comes from the uploads, and the avatars and the description too, unless they are empty, and then the saved ones stay. `subscribed_at` never changes.
- **Why:** a relative time such as "3 days ago" becomes "3 weeks ago" as the video ages, and each fetch estimates it from a later "now". Taking every new time would move `published_at` on each refresh and lose precision, and #49 orders and filters on it. YouTube also often leaves out a channel's description or a video's thumbnails, which shouldn't wipe what was known. Merging in Kotlin keeps the rule in one place that JVM tests can cover case by case. The transaction already read which videos are saved, so it inserts the new ones and updates the rest, with no failed insert and no exception on the way. Room 3.0.3's `@Upsert` would try an insert first and update when it fails on the primary key, so every refresh would make SQLite report a failed insert. Neither path uses SQLite's own UPSERT syntax, which Android 10 lacks, and neither deletes a row. The chunks keep each IN list under SQLite's limit of 999 bound values on Android 11 and older. A channel's uploads hold a few dozen videos, so one chunk is the usual case.
- **Alternative:** the merge in SQL, with CASE expressions in an UPDATE. Rejected: harder to read and to test, for a rule that may grow.
- **Alternative:** `@Insert` with REPLACE. Rejected: it deletes the old row first.
- **A note for #50:** PublishedTime marks "7 hours ago" as not approximate, though it is only as exact as the hour. So under this rule a later refresh's "5 hours ago" replaces it, and it would also replace an exact time that #50 might save from a video's details. #50 decides whether a time from the details should win, which may need one more column.

### YouTube's order for equal times

- **Choice:** `listed_at` and `list_position` keep each video's place in the latest save that held it. VideoDao's `channelVideos(channelId)` returns a channel's saved videos in this order: newest `published_at` first, videos with no time last (`published_at DESC`: SQLite's [SELECT](https://www.sqlite.org/lang_select.html#orderby) page, checked on November 22, 2026, says "SQLite considers NULL values to be smaller than any other values for sorting purposes", so they come last in a DESC order, and Android 10 has no `NULLS LAST`), then the latest `listed_at` first, then `list_position`, then the ID.
- **Why:** ChannelUploads keeps YouTube's order for videos with the same time and for videos with no time, with the Videos tab before the Live tab. Ties are common: one save turns every "3 days ago" into the same instant, and the first save after subscribing holds weeks and months of uploads. The inbox (#49) shows videos per channel, and the brief's open question of "up to two recent videos per channel" needs the right two. Positions from one save compare with each other, so the order compares `listed_at` first: videos that a later save held come before those it no longer held, among videos with the same time.
- **Who reads it:** `channelVideos` is a DAO query only, for #49 to build on and for the tests. No repository exposes it until a task needs it.
- **Alternative:** no rank, with ties broken by ID. Rejected: it scrambles YouTube's order, and no caller could get it back.
- **Alternative:** SQLite's rowid. Rejected: VACUUM may renumber the rowids of a table without an INTEGER PRIMARY KEY.

### Saving positions and marks

- **Choice:** WatchStateDao's `savePosition`, `markWatched` and `markUnwatched` each run in one transaction: insert-ignore of a row with position 0, no `watched_at` and `updated_at` now, then one UPDATE. `markWatched` sets `watched_at = COALESCE(watched_at, :now)`. `markUnwatched` sets it to NULL and does nothing to a video with no row. Each sets `updated_at` to now: it is the time of the last save or mark, even one that changed nothing else. Positions below zero are stored as zero.
- **Why:** insert-ignore then UPDATE works on SQLite 3.22 and never deletes a row, and `COALESCE` keeps the first time of marking, which the spec asks for.

### Column types and converters

- **Choice:** `@ColumnTypeConverters` on the database with an object, DatabaseConverters, for Instant to and from epoch milliseconds, and List<Thumbnail> to and from JSON text. Lengths and positions are Long milliseconds in the entities, `duration_ms` and `position_ms`, because Duration is a Kotlin value class, and a plain Long column leaves no doubt about how Room stores it. (Changed during the build from a Duration converter.) VideoKind uses Room's built-in enum converter, which stores the name. PublishedTime is two columns, `published_at` and `published_approximate`.
- **Why:** the entities keep Dewfall's own types where Room can convert them, so the mapping in `:core:data` deals with IDs and lengths only. Milliseconds are as exact as anything YouTube gives.

### Thumbnail and avatar lists as JSON

- **Choice:** each list is one TEXT column, a JSON array of objects with `url`, and `width` and `height` when known, written with kotlinx-serialization-json and a private `@Serializable` class in `:core:database`. `:core:database` applies Kotlin's serialization plugin, as `:app` does. Unknown keys are ignored, so a later field doesn't break reading. A list that can't be read throws IllegalStateException with a fixed message and no cause, because kotlinx.serialization's messages quote the input, which holds URLs, and URLs are user data (ADR 0007).
- **Why:** nothing queries inside the lists. They are always read and written whole with their row.
- **Alternative:** child tables `video_thumbnails` and `channel_avatars`. Rejected: two more tables, `@Relation` reads and a delete-and-insert on every save, for data nothing filters on.
- **The dependency:** kotlinx-serialization-json 1.11.0 is already in gradle/libs.versions.toml, used by `:app`'s and `:core:designsystem`'s tests. It now ships. Its license is Apache 2.0, and its only other dependency, kotlinx-serialization-core 1.11.0, already ships through `:app`. The version is older than 14 days: 1.11.0 is the version already in the catalog. Newer releases, 1.12.0 and 1.13.0, are Renovate's to bring, under the same rules. The artifacts that ship are kotlinx-serialization-json-jvm and kotlinx-serialization-core-jvm 1.11.0, both Apache 2.0 by the 1.11.0 POM, and the PR lists them by those names. The maintainer delegated dependency decisions on November 21, 2026, as proposal.md quotes, so this design checks the license and the 14-day rule instead of asking.

### Entities, DAOs and their visibility

- **Choice:** entities hold IDs as String. The mapping in `:core:data` turns them into ChannelId and VideoId, whose constructors check the format, so a row can only hold an ID that passed that check when it was written. ChannelEntity, VideoEntity and WatchStateEntity, with ChannelDao, VideoDao and WatchStateDao, are public in `:core:database`, so `:core:data` can use them, and only `:core:data` depends on `:core:database`. VideoDao and WatchStateDao are abstract classes, so their `@Transaction` functions have bodies. ChannelDao, which needs no transaction, is an interface. The entities are data classes, so their toString prints IDs, titles and URLs. Their KDoc says they are user data and never to log them, as the models' KDoc does.
- **Why:** feature modules stay unable to see Room, through `:core:data`'s implementation dependency.

### Building the database

- **Choice:** `:core:database` has `buildDewfallDatabase(context)`, which calls `Room.databaseBuilder<DewfallDatabase>(context.applicationContext, "dewfall.db")` and `setDriver(AndroidSQLiteDriver())`, as docs/architecture.md#database-tables says. No multi-instance invalidation and no destructive migration. In `:core:data`, DatabaseModule provides it as a `@Singleton`, with its three DAOs, and a `kotlin.time.Clock` that is `Clock.System`.
- **Why:** `:core:database` doesn't apply Hilt, and one Hilt module next to YouTubeModule keeps Hilt where it already is. The driver choice stays with the database.

### Repositories

- **Choice:** three interfaces in `:core:data`, each with a Room implementation bound in a Hilt module, like the YouTube repositories:
  - SubscriptionsRepository: `subscribedChannels(): Flow<List<SubscribedChannel>>`, `subscribedChannel(channelId): Flow<SubscribedChannel?>`, which the channel page (#47) reads to show a subscribed channel when YouTube can't be reached, `isSubscribed(channelId): Flow<Boolean>`, derived from it, `suspend subscribe(channel: Channel)`, `suspend unsubscribe(channelId)`.
  - StoredVideosRepository: `suspend saveUploads(uploads: ChannelUploads)`. Reads come with the tasks that need them, as the Non-Goals say.
  - WatchStateRepository: `watchState(videoId): Flow<WatchState?>`, `suspend savePosition(videoId, position: Duration)`, `suspend markWatched(videoId)`, `suspend markUnwatched(videoId)`.
- Each takes the Clock for "now". The Flows use `distinctUntilChanged`, because Room emits again whenever the table changes.
- Every write runs in an app-wide CoroutineScope that the repository receives, as `externalScope.async { ... }.await()`, so a write that started finishes even when the caller's coroutine is cancelled, such as when the user leaves the channel page right after tapping Subscribe, or the player saves a position as it stops. Google's [coroutines best practices](https://developer.android.com/kotlin/coroutines/coroutines-best-practices#create-coroutines-data-layer) ("Creating coroutines in the business and data layer", checked on November 22, 2026) shows this pattern with `launch { ... }.join()`. `async` and `await` do the same, and also hand an exception from the write back to a caller that is still waiting, where `join` would drop it. The scope is `@ApplicationScope`, a qualifier in `:core:data`, provided by DatabaseModule as `CoroutineScope(SupervisorJob() + Dispatchers.Default)`. Room moves the query to its own context, so the dispatcher only runs the coroutine's start. Tests pass their own scope.
- New models in `:core:model`: SubscribedChannel(channel, subscribedAt) and WatchState(position, watchedAt: Instant?, updatedAt). Their KDoc says they are user data, as the other models do.
- **Why:** each caller depends only on what it uses, as with the YouTube layer's interfaces, and a ViewModel's test fakes an interface without seeing Room.

### Threading and errors

- **Choice:** the repositories add no dispatcher of their own beyond the app-wide scope above. A write that fails while its caller still waits rethrows to the caller. A write that fails after its caller was cancelled has no one to rethrow to: the SupervisorJob scope keeps the app running, and the error is lost, with no log line. That only happens for a bug or a full disk, and the next write tries again. Room's suspend functions and Flows run their queries on Room's own query context, so they are main-safe. Database exceptions, such as a full disk, are not caught. They point to a bug or to storage, which Phase 7's storage work handles, and docs/architecture.md's error section says so.
- **Why:** AGENTS.md asks for injected dispatchers so tests can replace them. Room's tests use a real in-memory database, which needs no replacement.

### Where the DAO tests run

The comment on #48 asks whether the DAO tests can run on the computer's JVM with the bundled driver instead of on an emulator.

- **What the sources say.** Room 3.0.3's Android artifact has an `inMemoryDatabaseBuilder<T>()` that takes no Context, and its builder accepts a null Context for an in-memory database (Room.android.kt and RoomDatabase.android.kt in room3-runtime-android-3.0.3-sources.jar). The bundled driver for a computer is a separate artifact, androidx.sqlite:sqlite-bundled-jvm, with native libraries for Linux, macOS and Windows. Google's [Test and debug your database](https://developer.android.com/training/data-storage/room/testing-db) page, checked on November 22, 2026, describes host tests through a Kotlin Multiplatform JVM target, and doesn't recommend Robolectric. Adding the JVM artifact to an Android module's unit tests isn't a setup that page describes.
- **What the build does.** Task 2.5 tries it once: one DAO test as a JVM unit test of `:core:database`, with sqlite-bundled-jvm as a test-only dependency, at the androidx.sqlite version that Room 3.0.3 already resolves, if that release is at least 14 days old, run with `./gradlew :core:database:testDebugUnitTest`. The result, whether it runs and what fails if not, goes into docs/testing.md with the versions and the date of the try, because the answer can change with later releases. The try isn't committed.
- **Choice either way:** the DAO tests run on an emulator, as androidTest tests. They then run against the phone's own SQLite: 3.22 on the Android 10 emulator in CI, the oldest Android the app supports, and 3.50 on Android 17. That is what proves the no-UPSERT rule and the bound-value limit. A bundled SQLite on the JVM is 3.50, so it would pass a query that Android 10 can't run. docs/testing.md's fast-test list and the roadmap's testing section move "Room queries" to the emulator tests, with this reason. If the try works, docs/testing.md says it could add fast JVM runs later.

### Tests

- `:core:database` androidTest, on an emulator or a phone, against an in-memory database built like the real one: ChannelDaoTest, VideoDaoTest and WatchStateDaoTest cover every scenario in the two delta specs that the DAOs decide, including one save of more than 500 videos, so the chunks are covered, and the order of `channelVideos` for equal times, no times, and a video a later save no longer holds. DatabaseFileTest writes to a database file in the test's own folder, closes it, opens it again and reads the subscription, the video and the watch state back, for the "after the app closes" scenarios. Each test uses times it chooses, so none depends on the clock.
- `:core:database` unit tests on the JVM: DatabaseConvertersTest, for the round trips of Instant and thumbnail lists, with null widths and heights, an empty list, unknown keys, and the fixed message for a list that can't be read. VideoMergeTest, with one test for each part of the merge rule, for videos and for channels.
- `:core:data` unit tests on the JVM: one test class for each repository, with fake DAOs that record what they were asked and return what the test gives them, and a fixed Clock. They check the mapping to and from the models, that "now" comes from the Clock, the Flows' distinct values, and, for one write in each repository, that the write finishes when the caller's coroutine is cancelled while it runs, with a fake DAO that suspends until the test lets it go. They don't reimplement SQL: the emulator tests prove the queries.
- The privacy scenarios: BackupOffTest checks the backup and transfer rules, as [Data extraction rules](#data-extraction-rules) says. A transfer between two real phones can't run in CI, so the test checks the rules that Android applies. The "nothing logged" scenario holds because neither `:core:database` nor the new code in `:core:data` uses the Logger, and `:core:database` doesn't depend on `:core:logging`. Nothing in this change opens a network connection.
- `:core:database`'s build sets `testInstrumentationRunner` to AndroidX Test's AndroidJUnitRunner with `disableAnalytics=true`, as `:app` does, and adds the runner, androidx.test.ext:junit, kotlin-test-junit and kotlinx-coroutines-test for androidTest. All are already in the version catalog.
- .github/workflows/emulator-tests.yml runs `:app:connectedDebugAndroidTest :core:database:connectedDebugAndroidTest` on both API levels, its pull request filter adds `core/database/**` and `core/model/**`, because the entities use the models' types, and its "Upload the reports" step adds core/database/build/reports/androidTests/ and core/database/build/outputs/androidTest-results/. The Checks job doesn't compile androidTest, so this is where a broken database test shows.
- Run once locally on one emulator before the PR, as docs/testing.md#running-them says.

### Data extraction rules

- **Choice:** app/src/main/res/xml/data_extraction_rules.xml, named by `android:dataExtractionRules` on `<application>`, with a `<cloud-backup>` and a `<device-transfer>` section. Each excludes the domains `root`, `device_root` and `external` at path `.`, and includes nothing. `android:allowBackup="false"` stays, for Android 11 and older, where it turns off both backup and transfer: the manufacturer exception in [Android 12 behavior changes](https://developer.android.com/about/versions/12/behavior-changes-12#backup-restore) covers only apps running on and targeting Android 12 and higher. app/src/main/res/xml/full_backup_content.xml, named by `android:fullBackupContent`, excludes the same three domains for Android 10 and 11, because lint's DataExtractionRules check asks for it while minSdk is below 31, and it keeps the files out even if backup were turned on there. (Added during the build, when lint flagged it.)
- **Why:** on Android 12 and later, `allowBackup="false"` stops Google Drive backup, but on some manufacturers' phones not a direct transfer to a new phone ([Android 12 behavior changes](https://developer.android.com/about/versions/12/behavior-changes-12#backup-restore), checked on November 22, 2026). [Auto Backup](https://developer.android.com/identity/data/autobackup), checked the same day, says an `<exclude>` wins over an include, `root` is the folder that holds all of the app's private files, `device_root` the same in device-protected storage, and `external` the app's external files folder. Excluding the three at `.` leaves nothing. The database lives under `root`, in its `databases` folder.
- **Cross-platform transfer to iOS:** Android 16 QPR2 adds a `<cross-platform-transfer platform="ios">` section. [Auto Backup](https://developer.android.com/identity/data/autobackup) says a mode with no rules is fully enabled, but it also says the section needs `<platform-specific-params>` with the bundle ID and team ID of the matching iOS app, and the [Android 16 QPR2 Beta 1 post](https://android-developers.googleblog.com/2025/08/android-16-qpr2-beta-1-is-here.html) says an app opts in to cross-platform transfer with that tag and its own BackupAgent code (both checked on November 22, 2026). So an app without the section doesn't take part. Dewfall has no iOS app and no BackupAgent, and the rules leave the section out.
- **The lint suppression:** the manifest's `tools:ignore` drops DataExtractionRules, so lint checks the rules from now on. Its comment no longer points to #68 for them.
- **Test:** BackupOffTest in `:app`'s unit tests reads the manifest and the rules file, as LaunchThemeTest reads the theme files, and checks `allowBackup="false"`, that `dataExtractionRules` and `fullBackupContent` name their files, that both sections exclude the three domains at `.` and include nothing, and that the file has no `<cross-platform-transfer>` section and the manifest no `android:backupAgent`.

### No new ADR

- **Choice:** the tables are recorded in docs/architecture.md#database-tables and in this design, without an ADR.
- **Why:** until the first public release the schema can change freely at version 1, so none of these choices is hard to reverse yet. After the release a change needs a migration, which Room supports. If a later task wants to change a choice that a migration can't express cleanly, that task writes the ADR.

## Risks / Trade-offs

- [A schema change before the first public release breaks the database on a phone that ran an older build] → Room throws on open. docs/architecture.md already says to clear the app's data. The maintainer's own phone runs nightly builds, so the PR says when the schema changed.
- [Orphaned `watch_state` rows after videos are never saved] → A few bytes each. Phase 7's cleanup can delete them if it wants to, without a schema change.
- [`channel_name` on a video goes stale after a channel renames] → The next `saveUploads` rewrites it. Screens that group by channel can read the name from `channels`.
- [A redirected channel: `saveUploads` for a subscribed ID returns a channel with another ID] → The channel update matches the uploads' channel ID, so the old subscription keeps its details, and its videos are saved under the new ID. Then the inbox's join on `channel_id` would hide them. #52 and Phase 7 decide how to follow a redirect, and a comment on #52 says so.
- [Nothing deletes rows from `videos`, including those of channels the user unsubscribed from] → A few hundred bytes a video. Phase 7's cleanup decides what to prune, and must keep videos that have a watch state, a download or saved subtitles.
- [#52 may need the ID it asked for] → `saveUploads(uploads)` doesn't receive the subscribed ID that a redirect replaced. #52 may add a form that takes it, when it decides how refresh follows a redirect, as the comment on #52 asks.
- [JSON in a column can't be queried] → Nothing needs to. A later need moves it to a table in a migration.
- [Emulator tests are slower and need a device] → They run in CI on both API levels. Locally, one emulator at a time.

## Migration Plan

None. The database is new, at version 1. Rollback is reverting the PR. A phone that ran this build keeps an unused `dewfall.db` until the app's data is cleared.
