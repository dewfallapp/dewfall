# Design

## Context

#54 built the download engine, in `:core:download`, and the download controls on the video screen and in the inbox ([archived change](../archive/2026-11-26-download-videos/design.md), [ADR 0010](../../../docs/adr/0010-download-engine.md)). Its design left #55 "`downloads()` and `remove(videoId)` on DownloadsRepository, reading the rows' summaries, and pause and cancel for every row, a waiting one included" ([architecture](../../../docs/architecture.md#downloads-and-cleanup)).

What exists today, read from main at a41d3059:

- The `downloads` table keeps each download's whole VideoSummary, its state (WAITING, DOWNLOADING, PAUSED, FAILED, DOWNLOADED), its formats and sizes, its bytes so far, its folder's random name, `requested_at` and `updated_at`. `DownloadDao.downloads()` already lists every row in the queue's order, `ORDER BY requested_at, rowid`.
- `DownloadsRepository` has `statuses()`, `status(videoId)`, `download(video)` and `pause(videoId)`. `DownloadDao.pause` already pauses a WAITING row as well as a DOWNLOADING one, so pausing a waiting download needs no new write. `download(video)` already requeues a PAUSED or FAILED row at the end of the queue, which is what "Resume" and "Try again" need.
- DownloadRunner follows the row it downloads with `queue.state(videoId).first { it == PAUSED || it == null }`, and cancels the attempt within one buffer when the row turns PAUSED or goes. Its KDoc already says "or a later task removes it". So a deleted row stops a running download with no change to the watch.
- FormatWriter calls `part.parentFile?.mkdirs()` before it writes a part, so a writer that is still finishing its last buffer could make a deleted folder again.
- VideoRowItem in `:core:ui` draws a video row: thumbnail with its length, title, and a meta line with the channel name, how long ago it came out, the status words and the status control, as one merged screen-reader node.
- The navigation bar has two destinations, each with its own back stack, in AppNavigation, following Navigation 3's multiple back stacks recipe ([recipe](https://developer.android.com/guide/navigation/navigation-3/recipes/multiple-backstacks)).

## Goals / Non-Goals

**Goals**

- The Downloads screen as the design's "Downloads" frame shows it, minus the parts that belong to later tasks, with pause and cancel for every unfinished row and remove for every finished one.
- Cancel and remove leave no file behind, even when the process ends in between, and never race a writer that is still running.
- A third navigation destination, with the same history rules as the other two.

**Non-Goals**

- The storage meter above the list (#57). The screen leaves its place under the top bar, and whichever of #55 and #57 lands second puts it there.
- Playing the downloaded file (#56). A row opens the video screen, which streams until #56 lands.
- The design's "Watched, removed tonight" section, and the "Kept" and "Watched" badges (Phase 7's cleanup), and the CC badge (subtitle search, Phase 8).
- Settings, the bar's fourth destination.
- The search sheet with two choices that the design system describes. Search subtitles doesn't exist yet, so the search button opens Search YouTube directly, as on the inbox and the Channels screen.
- The kind that the video screen saves for a download it starts. PlayerViewModel's `summaryOf` saves VIDEO or LIVE, because VideoDetails doesn't say whether a video is a Short. The Downloads screen shows the length tag that VideoRowItem draws for the saved kind; a Short downloaded from Search YouTube's video screen shows its length like any video. The merge desk noted this for #55; it is left as it is, because the inbox shows no Shorts and the row shows nothing wrong.

## Decisions

### 1. The model and the repository

`:core:model` gets:

```kotlin
/** A download as the Downloads screen lists it. */
data class Download(
    val video: VideoSummary,
    val status: DownloadStatus,
    /** When the user asked for it, or last resumed it: the queue's order. */
    val requestedAt: Instant,
    /** When it finished, for a Downloaded one, else null. */
    val finishedAt: Instant?,
)
```

`finishedAt` is the row's `updated_at` when its state is DOWNLOADED: `finish` is the last write a DOWNLOADED row gets, because every queue write after it names `WHERE state = 'DOWNLOADING'`.

`DownloadsRepository` gets:

```kotlin
/** Every download, in the queue's order, with the summary saved when it was asked for. */
fun downloads(): Flow<List<Download>>

/**
 * Removes the video's download, whatever its state, and deletes its files. A running download
 * stops within one buffer, and the download job deletes its files once its writer stopped.
 */
suspend fun remove(videoId: VideoId)
```

Cancel and Remove are the same call: the screen names them differently because one acts on an unfinished download and the other on a finished one.

### 2. Removing without racing the writer

- `DownloadDao.remove(videoId): DownloadEntity?` is a `@Transaction` that reads the row and deletes it, and returns the row as it was, or null when there was none. Room runs a `@Transaction` function in one database transaction, so a claim, which is a transaction too, can't come between the read and the delete.
- `RoomDownloadsRepository.remove` runs it in the app-wide scope, as every write does, so a remove that started finishes after the user leaves the screen. When the row was not DOWNLOADING, it deletes the folder right after, on an injected `@DownloadFilesDispatcher`, which Hilt provides as Dispatchers.IO, because no writer can be writing to it. When it was DOWNLOADING, it leaves the folder to the runner.
- DownloadRunner, after each download's attempt ends, whether it was cancelled by the watch or ended by itself, reads the row once more; when the row is gone, it deletes the folder. The attempt has ended by then, so no writer can make the folder again. An attempt that finished just as the row went, whose `finish` changed nothing, is covered the same way.
- A row removed right after a pause: the watch already ended the attempt on PAUSED, but its last buffer may still land, and a writer that started a new part may have called `mkdirs`. The repository deletes the folder, and at worst a part made in that moment stays, which the sweep below deletes.
- **The sweep.** At the start of every run, after `resetDownloading` and before the first claim, while the run holds `runLock`, the runner deletes every folder under the downloads root that no row names. No writer runs then: writers run only inside a run, and the run lock allows one. A row inserted meanwhile has no folder yet, because only its writer makes it. This deletes what a process that ended between a row's delete and its folder's delete left, and what the pause case above left. It logs only how many folders it deleted. `DownloadQueue` gets `folders(): Set<String>` from `SELECT folder FROM downloads`, and DownloadFiles gets `folders(): List<String>`, the names of the directories under its root.
- A folder that the sweep can't delete fully is logged as a count and left for the next run.
- When #56 plays a downloaded file and the user removes it, the file is unlinked while the player holds it open; on Linux an open file stays readable until it is closed. #56's spec decides what the player does then.

### 3. The screen's MVI contract

`:feature:downloads-logic`:

```kotlin
sealed interface DownloadsList {
    data object Loading : DownloadsList
    data object NoDownloads : DownloadsList
    /** [downloading] in queue order, [onPhone] newest finished first. Not both empty. */
    data class Shown(val downloading: List<Download>, val onPhone: List<Download>) : DownloadsList
    data object Unreadable : DownloadsList
}

data class DownloadsState(val list: DownloadsList = DownloadsList.Loading) : MviState

sealed interface DownloadsIntent : MviIntent {
    data class DownloadsRead(val downloads: List<Download>) : DownloadsIntent
    data object ReadFailed : DownloadsIntent
    data class Pause(val videoId: VideoId) : DownloadsIntent
    /** Resume and Try again: queues the video again from the bytes it has. */
    data class Resume(val video: VideoSummary) : DownloadsIntent
    /** Cancel and Remove download. [finished] says which, for the message. */
    data class Remove(val videoId: VideoId, val finished: Boolean) : DownloadsIntent
    /** The ViewModel removed a finished download. */
    data object Removed : DownloadsIntent
}

sealed interface DownloadsEffect : MviEffect {
    /** Show "Download removed". */
    data object ShowRemoved : DownloadsEffect
}
```

- The reducer splits the list: every Downloaded one goes to `onPhone`, sorted by `finishedAt` descending, ties by queue order; every other one stays in `downloading`, in the repository's order. An empty list is NoDownloads. Pause, Resume and Remove change no state: the screen shows what the phone holds, which the next read brings. Removed gives ShowRemoved.
- DownloadsViewModel extends MviViewModel. It reads `downloads()` only while the screen collects the state, with `stateSubscriptionCount`, as ChannelsViewModel does, so the screen reads again when it shows again after a failed read. A read that throws gives ReadFailed. Pause, Resume and Remove run in viewModelScope; a write that throws is caught, logged as its class name, and leaves the row as it was. Remove dispatches Removed only after a finished download's `remove` returns.
- Rows are keyed by video ID in the LazyColumn, so a row that moves from "Downloading" to "On this phone" keeps no stale state.
- The screen shows "Download removed" in a SnackbarHost above the mini player and the navigation bar, and reports the effect handled when the snackbar ends, as other screens handle their effects.

### 4. The rows

- **Unfinished.** VideoRowItem with the channel name and no publish time, as the design's rows show, plus a new optional `supporting` slot in VideoRowItem, under the meta line inside the merged node: the progress bar and the status line. So a screen reader reads the title, the channel and the status as one item. The buttons sit under the row's text column, outside the merged node, each a separate focus target: "Pause" with the pause glyph and "Cancel" for a running or waiting download; "Resume" and "Cancel" for a paused one; "Try again" and "Cancel" for a failed one. Text buttons, as the design's.
- **The progress bar.** A 4dp LinearProgressIndicator in `primary` on `surfaceContainerHighest`, with round caps and no gap or stop dot, as the design draws it, shown once the total is known. It is hidden from screen readers, because the status line says the percentage.
- **The status line.** Running: "42%, 344 of 820 MB", or "Downloading" while the total is unknown. Waiting: "Waiting to download". Paused: "Paused, 42%", or "Paused". Failed: the error glyph in `error` and "Download failed" or "Not enough space on this phone". The existing strings of `:core:ui` are reused, so the screen and the inbox use the same words.
- **Finished.** VideoRowItem with the channel name and the size in the meta line, and a "Remove download" text button under it, at the end, outside the merged node.
- **Sizes.** A pure function in `:core:ui`, `fileSize(bytes)`, picks the unit, kB, MB or GB, with 1,000 bytes to a kilobyte, as Android's own storage formatting has since Android 8.0 ([Formatter](https://developer.android.com/reference/android/text/format/Formatter#formatShortFileSize(android.content.Context,%20long))), and one decimal under 10 of the unit. "344 of 820 MB" puts both numbers in the total's unit. The numbers use the phone's locale, through `NumberFormat`. Unit tests cover the edges: 999 kB, 1 MB, 9.95 MB, 10 MB, 1 GB. #57 needs the same formatting, so it lives in `:core:ui`, where both screens can reach it; if #57 lands first with its own, this change uses that one instead.

### 5. The screen

- Top bar as the Channels screen's: "Dewfall", "Downloads", and a search button, "Search YouTube", from `:core:designsystem`'s top app bar.
- Section headers "Downloading" and "On this phone" in `titleSmall`, `onSurfaceVariant`, as the design's, each section's rows in one `surfaceContainerLow` card, the rows separated as the inbox's groups are.
- Empty state: the download glyph in a round disc, "No downloads yet", "Download a video from its screen or your inbox to keep it on this phone.", centered as the Channels screen's empty state is.
- Unreadable: the error glyph, "Your downloads can't be shown", "Dewfall couldn't read what it keeps on this phone. Restarting the app may help.", as the Channels screen's.
- The public composable `Downloads(onOpenVideo, onOpenSearch, modifier)` takes plain actions, as AGENTS.md asks; `:app` turns them into navigation.
- Edge to edge, one centered column of readable width in wide windows, the bottom room for the mini player and the bar that `:app` already keeps, as the Channels screen does.
- A double tap on a row opens one video screen, through the same guard BackStack.kt's `openVideo` already has.

### 6. Navigation

- `Destination` gets DOWNLOADS after CHANNELS. AppNavigation holds a back stack per destination in a map, instead of two fields, and `shown` returns the inbox's entries, then the selected destination's when it isn't the inbox. `goBack` on the first screen of Channels or Downloads selects the inbox. `close(key)` looks in every stack. `DownloadsKey` is a data object, as ChannelsKey is, and `BackStack.kt`'s first-key rule and `showsNavigationBar` include it.
- The bar's glyphs: `download_for_offline` outlined and filled, from Material Symbols Rounded, under the Apache License 2.0, as the design system's NavigationBar names, added to `:core:designsystem` beside `ic_subscriptions` and `ic_subscriptions_filled`, with the same source notice. The empty state's disc uses the outlined one.
- No other screen changes: Search YouTube, the video screen and the channel page already open from whichever stack is selected.

### 7. Testing

- `:core:database` DAO tests: `remove` returns the row and deletes it, returns null for a missing one, and a claim after a remove finds nothing; `folders()` lists every row's folder.
- `:core:data`: RoomDownloadsRepository's `downloads()` maps each state, `finishedAt` only for DOWNLOADED; `remove` deletes the folder for a WAITING, PAUSED, FAILED and DOWNLOADED row and not for a DOWNLOADING one; the write finishes after its caller is cancelled, as the other writes' tests check.
- `:core:download` DownloadRunner tests, with the existing fakes: a row removed during its attempt stops it and its folder is deleted after the attempt ends, and the next row starts; a row removed as its attempt finishes has its folder deleted; the sweep deletes folders that no row names, and keeps every folder a row names, before the first claim. Each test fails when the line it covers is removed, which the build checks by hand for each.
- `:feature:downloads-logic` reducer tests: the split, both orders, ties, empty, Unreadable, Removed gives ShowRemoved, the other intents change nothing.
- `:feature:downloads-ui` ViewModel tests with Dispatchers.setMain: reads only while collected, ReadFailed, a failing write leaves the state, Removed only after a finished remove succeeds, not for a cancel.
- Screenshot tests of the previews in light, dark, large font and dynamic color: every row state, empty, unreadable.
- `:core:ui`: unit tests of `fileSize`, and the VideoRowItem previews' references stay unchanged, since the new slot is empty by default.
- `:app` unit tests of AppNavigation with three destinations, and emulator tests with the in-memory database: the bar opens Downloads, back returns to the inbox; a finished row's "Remove download" deletes the row and the folder and shows "Download removed"; "Cancel" on a paused row deletes its folder; a semantics check that each row's text is one node and each button its own.
- Hand checks on an emulator: a real download cancelled mid-way, with `ls` of the downloads folder before and after; TalkBack can't be driven from adb here (answer 12 of #54), so the semantics test covers it. Real-video steps wait if YouTube blocks this network again, as #258 records.

## Risks / Trade-offs

- **A removed download's last part.** Between a pause and a remove, a writer's last buffer can leave a part file until the next run's sweep. The bytes are few, inside the app's own storage, and the next download deletes them. Sweeping on every start of the app would catch it sooner, but `start()` runs without the run lock, while a writer may run.
- **Removing what plays** is #56's question, as Decision 2 says.
- **The kind** of a download from the video screen stays VIDEO or LIVE, as the Non-Goals say.
