package io.github.dewfallapp.core.download

import io.github.dewfallapp.core.data.download.QueueState
import io.github.dewfallapp.core.logging.Logger
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.test.runTest

/** A marker in memory. */
internal class FakeMarker(var present: Boolean = false) : BackupMarker {
    override fun write() {
        present = true
    }

    override fun delete() {
        present = false
    }

    override fun exists() = present
}

/**
 * A history whose last exit was a stop by the user, with the marker and the backups' state, and
 * whether the process was in the [foreground] at its end.
 */
private class StoppedHistory(
    private val marker: BackupMarker,
    private val backups: FakeBackups,
    private val foreground: Boolean = true,
) : AppHistory {
    override fun latestExit() = LatestExit(stopped = true, atMillis = 2_000, foreground)

    override fun updatedAtMillis() = 1_000L

    override fun backupRanLast() = marker.exists()

    override fun backupScheduled() = backups.anyScheduled()
}

/** How a run's `begin`, the backup marker and the stop check fit together. */
internal class BackupRunTest : RunnerFixture() {
    private val marker = FakeMarker()
    private val backups = FakeBackups()

    @Test
    fun `a run that finds nothing reports no progress, so a backup run shows no notification`() =
        runTest {
            assertEquals(RunOutcome.DONE, run())

            assertEquals(emptyList(), reports)
        }

    @Test
    fun `begin waits for the stop check, and runs before the first claim`() = runTest {
        waiting(FIRST)
        val checking = CompletableDeferred<Unit>()
        val decided = CompletableDeferred<Unit>()
        var stateAtBegin: QueueState? = null
        val stopCheck = StopCheck {
            checking.complete(Unit)
            decided.await()
        }

        val result = async {
            runner(stopCheck).run(
                begin = {
                    stateAtBegin = queue.row(FIRST).state
                    marker.write()
                },
            ) {}
        }
        checking.await()

        assertFalse(marker.present)
        decided.complete(Unit)
        assertEquals(RunOutcome.DONE, result.await())
        assertTrue(marker.present)
        assertEquals(QueueState.WAITING, stateAtBegin)
    }

    @Test
    fun `a begin that deletes the marker waits for the stop check`() = runTest {
        waiting(FIRST)
        marker.present = true
        val checking = CompletableDeferred<Unit>()
        val decided = CompletableDeferred<Unit>()
        val stopCheck = StopCheck {
            checking.complete(Unit)
            decided.await()
        }

        val result = async { runner(stopCheck).run(begin = { marker.delete() }) {} }
        checking.await()

        assertTrue(marker.present)
        decided.complete(Unit)
        assertEquals(RunOutcome.DONE, result.await())
        assertFalse(marker.present)
    }

    @Test
    fun `a backup run after a Stop during a user-initiated run still pauses`() = runTest {
        // The user-initiated run deleted the marker, and Android ended its process on the Stop.
        // Backup 55, still armed, starts a new process and writes the marker as it begins.
        queue.add(FakeRow(FIRST, "aaaaaaaaaaa-folder", state = QueueState.DOWNLOADING))
        backups.scheduled = true
        val afterStop =
            DownloadsAfterStop(
                queue,
                StoppedHistory(marker, backups),
                backgroundScope,
                Logger.Silent,
            )

        val outcome = runner(afterStop).run(begin = { marker.write() }) {}

        assertEquals(RunOutcome.DONE, outcome)
        assertEquals(QueueState.PAUSED, queue.row(FIRST).state)
        assertTrue(marker.present)
    }

    @Test
    fun `a swipe during a backup run doesn't pause`() = runTest {
        // A backup run wrote the marker, the swipe ended its process, and the other backup,
        // still scheduled, starts the new process.
        queue.add(FakeRow(FIRST, "aaaaaaaaaaa-folder", state = QueueState.DOWNLOADING))
        marker.present = true
        backups.scheduled = true
        val afterStop =
            DownloadsAfterStop(
                queue,
                StoppedHistory(marker, backups, foreground = false),
                backgroundScope,
                Logger.Silent,
            )

        assertEquals(RunOutcome.DONE, run(afterStop))

        assertEquals(QueueState.DOWNLOADED, queue.row(FIRST).state)
    }

    @Test
    fun `a Stop during a backup run while the player's service ran pauses`() = runTest {
        // The player's foreground service made the Task Manager list the app during the run.
        queue.add(FakeRow(FIRST, "aaaaaaaaaaa-folder", state = QueueState.DOWNLOADING))
        marker.present = true
        backups.scheduled = true
        val afterStop =
            DownloadsAfterStop(
                queue,
                StoppedHistory(marker, backups, foreground = true),
                backgroundScope,
                Logger.Silent,
            )

        assertEquals(RunOutcome.DONE, run(afterStop))

        assertEquals(QueueState.PAUSED, queue.row(FIRST).state)
    }

    @Test
    fun `Force stop during a backup run pauses, because it cancelled the backups`() = runTest {
        queue.add(FakeRow(FIRST, "aaaaaaaaaaa-folder", state = QueueState.DOWNLOADING))
        marker.present = true
        backups.scheduled = false
        val afterStop =
            DownloadsAfterStop(
                queue,
                StoppedHistory(marker, backups),
                backgroundScope,
                Logger.Silent,
            )

        assertEquals(RunOutcome.DONE, run(afterStop))

        assertEquals(QueueState.PAUSED, queue.row(FIRST).state)
    }
}
