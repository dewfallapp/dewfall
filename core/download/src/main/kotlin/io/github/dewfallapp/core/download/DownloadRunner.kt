package io.github.dewfallapp.core.download

import io.github.dewfallapp.core.data.download.DownloadQueue
import io.github.dewfallapp.core.data.download.QueueState
import io.github.dewfallapp.core.data.download.QueuedDownload
import io.github.dewfallapp.core.logging.LogField
import io.github.dewfallapp.core.logging.Logger
import io.github.dewfallapp.core.model.DownloadProgress
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/** How a run of the queue ended. */
enum class RunOutcome {
    /** No download waits any more. */
    DONE,

    /** A download waits for a connection or for YouTube, so the job runs again later. */
    RETRY,
}

/** Hears how far the running download is, at most once a second, for the notification. */
fun interface RunReport {
    suspend fun progress(progress: DownloadProgress)
}

/** What a run does after one download. */
internal enum class Step {
    /** Take the next waiting download. */
    NEXT,

    /** End the run, and run again later. */
    RETRY,
}

/**
 * Runs the download queue: one download at a time, in the order asked, until none waits. Both
 * schedulers run it, the user-initiated data transfer job and the WorkManager worker, and Phase 7's
 * overnight job will too, so nothing in it depends on a screen. [VideoDownloader] downloads each
 * video. The runner follows each download's row, so a pause stops it within one buffer. Its log
 * lines hold no ID, title, link or file name. See docs/architecture.md#downloads-and-cleanup.
 */
@Singleton
class DownloadRunner
@Inject
internal constructor(
    private val queue: DownloadQueue,
    private val downloader: VideoDownloader,
    private val runState: DownloadRunState,
    private val stopCheck: StopCheck,
    logger: Logger,
    @DownloadDispatcher private val dispatcher: CoroutineDispatcher,
) {
    private val log = logger.withTag("Downloads")

    /**
     * Runs the queue until no download waits, and says whether to run again later. It waits for the
     * decision about a stop by the user first, and turns any download that an ended process left as
     * running back to waiting. Cancelling it, as a job's stop does, puts the download it ran back
     * to waiting, with its bytes, for the next run; when [stoppedByUser] says the stop was the
     * user's, such as the Task Manager's Stop, it pauses every waiting and running download
     * instead, in one write, as the maintainer chose. It runs on the injected IO dispatcher,
     * because its file checks, StorageManager's free space and the writes block, and the UIDT job's
     * service calls it from the main thread. [report] is called on that dispatcher too.
     */
    suspend fun run(stoppedByUser: () -> Boolean = { false }, report: RunReport): RunOutcome =
        withContext(dispatcher) {
            val marks = RunMarks()
            try {
                // One run at a time in the process: Android may start the job again while a
                // stopped run still puts its download back, and the new run's reset would take
                // that row.
                runState.runLock.withLock {
                    marks.locked = true
                    try {
                        runOnDispatcher(report, stoppedByUser, marks)
                    } catch (e: CancellationException) {
                        pauseAfterUserStop(stoppedByUser, marks)
                        throw e
                    }
                }
            } catch (e: CancellationException) {
                // Stopped while it waited for the run lock, which only a stopped run that puts
                // its download back holds, because Android runs one job or work at a time: the
                // pause waits for it, so it never pauses a row that a later run claimed. Two
                // limits: work that WorkManager kept across an update from Android 13 to 14 can
                // run beside the job, and Phase 7's overnight job will call run() too. Either can
                // make this wait last a whole run, so Phase 7 revisits it.
                if (!marks.locked) {
                    withContext(NonCancellable) {
                        runState.runLock.withLock { pauseAfterUserStop(stoppedByUser, marks) }
                    }
                }
                throw e
            } finally {
                // A run stopped before it began, while it waited for the run lock, the stop check
                // or the phase, ends any SCHEDULED phase, so the next start schedules again. That
                // may be a later start's, which a run that hasn't begun can't tell apart: at worst
                // the next start schedules once more, which replaces a job that hasn't begun.
                if (!marks.began) {
                    withContext(NonCancellable) {
                        runState.mutex.withLock {
                            if (runState.phase == RunPhase.SCHEDULED) runState.phase = RunPhase.IDLE
                        }
                    }
                }
            }
        }

    /**
     * Pauses the queue after a stop by the user that came before the run reached a download, such
     * as during its reset, while the run holds the run lock, so no other run claims a row in
     * between. During a download, downloadOne has paused it already, and a second pause could undo
     * a download that the user went on with in between.
     */
    private suspend fun pauseAfterUserStop(stoppedByUser: () -> Boolean, marks: RunMarks) {
        if (stoppedByUser() && !marks.paused) {
            withContext(NonCancellable) { queue.pauseQueued() }
            marks.paused = true
        }
    }

    private suspend fun runOnDispatcher(
        report: RunReport,
        stoppedByUser: () -> Boolean,
        marks: RunMarks,
    ): RunOutcome {
        stopCheck.await()
        runState.mutex.withLock {
            runState.phase = RunPhase.RUNNING
            marks.began = true
        }
        var outcome: RunOutcome? = null
        try {
            queue.resetDownloading()
            log.info("Downloads run started")
            outcome = runQueue(report, stoppedByUser, marks)
        } finally {
            // Its job asks to run again when the run didn't finish the queue, but a tap or the
            // app's return may start a run sooner: scheduling a job that isn't running only
            // replaces it, so a backoff never holds a new download for hours.
            // Even when the run was cancelled while another holds the lock, so every run ends
            // IDLE. A start that scheduled the next run in the meantime keeps it SCHEDULED.
            withContext(NonCancellable) {
                runState.mutex.withLock {
                    if (runState.phase == RunPhase.RUNNING) runState.phase = RunPhase.IDLE
                }
            }
            log.info("Downloads run ended", LogField.flag("done", outcome == RunOutcome.DONE))
        }
        return outcome
    }

    private suspend fun runQueue(
        report: RunReport,
        stoppedByUser: () -> Boolean,
        marks: RunMarks,
    ): RunOutcome {
        var step = Step.NEXT
        while (step == Step.NEXT) {
            // Not cancellable: a stop that came during the claim would leave the claimed row
            // DOWNLOADING in an idle process. So the claim always finishes, and downloadOne,
            // already cancelled, puts the row back, or pauses the queue on a stop by the user.
            val next =
                withContext(NonCancellable) {
                    runState.mutex.withLock {
                        queue.claimNext().also { if (it == null) runState.phase = RunPhase.IDLE }
                    }
                } ?: break
            step = downloadOne(next, report, stoppedByUser, marks)
        }
        return if (step == Step.RETRY) RunOutcome.RETRY else RunOutcome.DONE
    }

    /**
     * Downloads [item] while its row says it runs. When the user pauses it, or a later task removes
     * it, the download stops within one buffer and the run goes on with the next.
     */
    private suspend fun downloadOne(
        item: QueuedDownload,
        report: RunReport,
        stoppedByUser: () -> Boolean,
        marks: RunMarks,
    ): Step = coroutineScope {
        val bytes = NewBytes()
        val work = async { downloader.attempt(item, report, bytes) }
        val watch = launch {
            queue.state(item.videoId).first { it == QueueState.PAUSED || it == null }
            work.cancel()
        }
        try {
            work.await()
        } catch (e: CancellationException) {
            if (!isActive) {
                // The run itself was cancelled, as a job's stop does. A stop by the user
                // pauses the queue. Any other stop leaves the download waiting for the next
                // run, so no row stays marked as running in an idle process, which a swipe
                // from Recents could then end and the next start would take for a stop by
                // the user. New bytes in this attempt start its tries from 0, as they do in
                // the downloader, so the attempt ends first: a buffer it was writing may still
                // land. One write either way, so a process that ends right after can't leave
                // half of it.
                withContext(NonCancellable) {
                    work.join()
                    if (stoppedByUser()) {
                        queue.pauseQueued()
                        marks.paused = true
                    } else {
                        queue.backToWaiting(item.videoId, bytes.of(item).youtubeTries)
                    }
                }
                throw e
            }
            // Otherwise the user paused it.
            Step.NEXT
        } finally {
            watch.cancel()
        }
    }
}

/** What a run did, which its cleanup reads. */
private class RunMarks {
    /** Whether the run set its phase to RUNNING, after which its own cleanup ends the phase. */
    var began = false

    /** Whether the run holds the run lock, or held it. */
    var locked = false

    /** Whether a stop by the user already paused the queue. */
    var paused = false
}
