# Design

## Context

The download engine from #54 keeps each download's files in its own folder under `noBackupFilesDir/downloads/`, through DownloadFiles in `:core:data`. Each row of the `downloads` table has `downloaded_bytes`, the bytes of the download's files on disk:

- While a download runs, VideoDownloader writes it at the end of each 10 MiB range and at most once a second between them (`PROGRESS_EVERY`), through DownloadQueue and DownloadQueueDao's `setProgress`, which only changes a DOWNLOADING row.
- `finish` sets it to the bytes of the complete files, read from the disk.
- `fail` keeps it when the download keeps its parts, and sets it to 0 when the files are deleted.
- `clearFormats` sets it to 0 when the formats are chosen again, after the folder was emptied.
- #55 adds removing a download, which deletes its row and its folder.

So `downloaded_bytes` already counts what the answers ask for: complete files and the parts that waiting, downloading, paused and failed downloads keep, and 0 for a download whose files are gone.

The Downloads screen is #55's. It is built in parallel with this change. This change builds on top of it once #55 merges, and changes only what it needs in #55's state, reducer, ViewModel and screen. #55's session named its modules `:feature:downloads-logic` and `:feature:downloads-ui`, and leaves the slot under the top app bar empty for this card. The class and function names of #55's screen below are the ones #55 is expected to use. If #55 names them differently, the build uses #55's names, and nothing else in this design changes.

The design system fixes the card's look: `surface-container-low`, `shape-medium` (12dp), `space-md` (16dp) padding, the heading in `label-medium` on `on-surface-variant`, and the amount in `label-large` on `on-surface`, in one row with the heading at the start and the amount at the end, which wraps when the text is large (the "Downloads" frames, and tokens.json's notes on `surface-container-low`, `label-medium`, `label-large`, `space-md` and `shape-medium`, and the design system's StorageMeter component README). The bar of the frames is left out, as answer 1 says.

## Goals / Non-Goals

**Goals:**
- One number, read from the database, that follows the downloads while the screen shows, with no work when no screen shows it.
- The card in light, dark, dynamic color and large font, with screenshot tests, and read as one item by a screen reader.

**Non-Goals:**
- The storage limit, the bar, and the "paused at the storage limit" state of the meter (Phase 7).
- The phone's free space. The engine already says "Not enough space on this phone" on a download that can't fit.
- Finding files that no row knows about, such as a folder whose delete failed. Phase 7's cleanup owns the folder's housekeeping.

## Decisions

### Count from the database, not from the folder

DownloadDao gets `bytesInUse(): Flow<Long>`, `SELECT COALESCE(SUM(downloaded_bytes), 0) FROM downloads`. A Room query that returns a Flow runs again after any write to its table, and Room's guide suggests `distinctUntilChanged` for that reason ([Write asynchronous DAO queries](https://developer.android.com/training/data-storage/room/async-queries)). So the Flow follows progress writes, finishes, failures and removals, and it stops when no one collects it. DownloadsRepository gets `bytesInUse(): Flow<Long>`, the DAO's Flow with `distinctUntilChanged`, because writes that don't change the sum, such as a pause, would otherwise wake the screen. DownloadFiles doesn't change.

Alternative: walk the downloads folder and add up the files' lengths after every change to the table. It would also count folders that no row knows about. It was rejected because the engine writes the row before it deletes a failed download's folder (`DownloadOutcomes.failed`), so the walk that the row's write starts would still see the folder and show the old size until the next write to the table, which may never come. Counting from the rows has no such order to get wrong, needs no dispatcher and no file system access on each change, and is one query.

### The screen formats the number

The state holds the bytes as a Long, and the screen turns them into text, because the text depends on the locale, which can change while the screen lives. #55's session adds a pure `fileSize(bytes)` and a composable `fileSizeText(bytes)` to `:core:ui` for its rows' sizes: units of 1000 (kB, MB, GB), one decimal under 10 of the unit, and the locale's number format, so 3.6 billion bytes is "3.6 GB", as in the frames. The card calls `fileSizeText`, so the rows and the card agree. #55 chose its own function over Android's `Formatter.formatShortFileSize` because its rows need two sizes in one unit, "344 of 820 MB", and a pure function gets JVM unit tests. If #55 merges without such a helper, this change adds `fileSizeText` to `:core:ui` itself, with those rules and unit tests, so the rows can share it later.

### State, intent and reducer

#55's DownloadsState gets `bytesInUse: BytesInUse`, a sealed interface like #55's DownloadsList: `Reading` until the first read, `Read(bytes)`, and `Unreadable` after a read that failed. Two intents and their reducer cases: `BytesInUseRead(bytes)` stores the bytes, and `BytesInUseUnreadable` marks them unreadable. #55's own reducer cases replace only the list, so neither read hides the other. A plain `Long?` would leave the ViewModel unable to tell a failed read from the first one, which the return after a failure below needs. While the value is `Reading` or `Unreadable` the card shows its heading with no amount, so the list doesn't move when the amount arrives, and a failed read leaves the rest of the screen as it is.

The ViewModel reads `bytesInUse()` only while the screen collects its state, as InboxViewModel's `readWhileShown()` does: `SharingStarted.WhileSubscribed` over MviViewModel's `stateSubscriptionCount` turns the read on and off, so nothing runs while Downloads sits on the back stack or the app is in the background. The read joins #55's own read-while-shown when #55 has one, and gets the same shape of its own when not. A read that throws sends `BytesInUseUnreadable` instead of reaching `viewModelScope`, where it would crash the app, as the inbox's DownloadsUnreadable does (docs/architecture.md, "The inbox"). WhileSubscribed keeps a read that failed stopped while a screen that comes back within its stop timeout collects again, so, as #55's `returnAfterFailure` does for an Unreadable list, the state gaining a collector while the bytes are `Unreadable` starts the reads again at once.

### The card

A `StorageCard` composable in #55's `-ui` module, as the first item of the screen's list, directly under the top app bar, as the design system's Downloads order says. It scrolls with the list. When #55 shows an empty state instead of a list, the card stays above it, so "No downloads yet" still shows a space of zero. It isn't shared, so it doesn't go in `:core:designsystem` or `:core:ui`. Its row is a FlowRow with `SpaceBetween`, so at a large font the amount goes under the heading instead of being cut. The card has `semantics(mergeDescendants = true)`, so TalkBack reads "Offline storage, 3.6 GB" as one item. The string "Offline storage" is a resource.

### Tests

- DAO test (androidTest of `:core:database`): the sum is 0 with no rows, adds every state's bytes, and drops when a row is deleted.
- Repository test (`:core:data`, with FakeDownloadDao): the Flow gives the DAO's sum and skips a repeated value.
- Reducer tests: `BytesInUseRead` stores the bytes and changes nothing else; `BytesInUseUnreadable` marks them unreadable and changes nothing else; #55's list reads keep the bytes.
- ViewModel tests: the state follows the repository's Flow while the state has a collector; the read stops when the state has none, and starts again when it has one; a Flow that throws gives unreadable bytes, leaves the list, and doesn't crash; after such a failure, a collector that comes back within the stop timeout starts the read again.
- Screenshot tests of the card's previews: light, dark, large font and dynamic color, with an amount, with zero, and with no amount yet, plus a screen preview with no downloads that shows the card above the empty state.
- An emulator test check in #55's Downloads screen test, if it has one: with a downloaded row and a paused row in the in-memory database, the card says their sum as one node.

## Risks / Trade-offs

- [The amount lags the files by up to one second while a download runs, and a pause keeps the last write, which can be up to one second of bytes short] → The engine writes progress at that rate already. The next run's first write puts it right. A difference of a few megabytes doesn't change a number shown to one decimal of a gigabyte.
- [A folder whose delete failed isn't counted] → Rare: it needs the file system to refuse to delete a file in the app's own storage. Phase 7's cleanup can delete what no row knows about.
- [#55 changes shape while this change waits] → This change is built only after #55 merges, from #55's final code, and the plan check and reviewer see the real names then.
- [ADR 0010 says "#57 adds up their sizes"] → Left as it is: the rows' `downloaded_bytes` are those files' sizes, recorded by the engine, so the line stays true, and an accepted ADR isn't rewritten for a detail of how.

## Migration Plan

No migration: one new query, and no schema change, so the database stays at version 1 and core/database/schemas/ doesn't change.
