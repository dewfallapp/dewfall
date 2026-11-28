package io.github.dewfallapp.core.download

import android.app.job.JobParameters
import io.github.dewfallapp.core.model.DownloadProgress
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest

/** Records every call, in order, as "arm 55", "disarm 56" and "cancel 54". */
internal class FakeBackups : BackupJobs {
    val calls = mutableListOf<String>()
    var scheduled = false

    /** Called before each arm, disarm and cancel is recorded. */
    var onCall: () -> Unit = {}

    override fun arm(id: Int) {
        onCall()
        calls += "arm $id"
    }

    override fun disarm(id: Int) {
        onCall()
        calls += "disarm $id"
    }

    override fun cancelQueueJob() {
        onCall()
        calls += "cancel $QUEUE_JOB_ID"
    }

    override fun anyScheduled() = scheduled
}

@OptIn(ExperimentalCoroutinesApi::class)
class KeepingArmedTest {
    private val backups = FakeBackups()

    @Test
    fun `a run keeps its backup armed every 10 seconds, then disarms it after the last arm`() =
        runTest {
            val io = StandardTestDispatcher(testScheduler)
            val done = CompletableDeferred<RunOutcome>()
            val run = async { keepingArmed(backups, FIRST_BACKUP_JOB_ID, io) { done.await() } }

            runCurrent()
            assertEquals(listOf("arm 55"), backups.calls)
            advanceTimeBy(25.seconds)
            assertEquals(listOf("arm 55", "arm 55", "arm 55"), backups.calls)

            done.complete(RunOutcome.RETRY)
            assertEquals(RunOutcome.RETRY, run.await())
            assertEquals(listOf("arm 55", "arm 55", "arm 55", "disarm 55"), backups.calls)
            advanceTimeBy(60.seconds)
            assertEquals("disarm 55", backups.calls.last())
        }

    @Test
    fun `a run that throws disarms its backup too`() = runTest {
        val io = StandardTestDispatcher(testScheduler)

        assertFailsWith<IllegalStateException> {
            keepingArmed(backups, SECOND_BACKUP_JOB_ID, io) { error("Broken") }
        }

        assertEquals("disarm 56", backups.calls.last())
        assertEquals(1, backups.calls.count { it == "disarm 56" })
    }

    @Test
    fun `a run that returns leaves its backup armed while another run keeps it`() = runTest {
        val io = StandardTestDispatcher(testScheduler)

        keepingArmed(backups, FIRST_BACKUP_JOB_ID, io, keptElsewhere = { true }) {
            delay(1.seconds)
            RunOutcome.DONE
        }
        advanceTimeBy(60.seconds)

        assertEquals(listOf("arm 55"), backups.calls)
    }

    @Test
    fun `a run that returns checks for another run only after its last arm`() = runTest {
        val io = StandardTestDispatcher(testScheduler)
        val checkedAfter = mutableListOf<List<String>>()

        keepingArmed(
            backups,
            FIRST_BACKUP_JOB_ID,
            io,
            keptElsewhere = {
                checkedAfter += backups.calls.toList()
                false
            },
        ) {
            advanceTimeBy(15.seconds)
            RunOutcome.DONE
        }

        assertEquals(listOf(listOf("arm 55", "arm 55")), checkedAfter)
        assertEquals(listOf("arm 55", "arm 55", "disarm 55"), backups.calls)
    }

    @Test
    fun `a cancelled run leaves its backup armed`() = runTest {
        val io = StandardTestDispatcher(testScheduler)
        val run = async {
            keepingArmed(backups, FIRST_BACKUP_JOB_ID, io) { CompletableDeferred<Unit>().await() }
        }
        runCurrent()

        run.cancel()
        runCurrent()
        advanceTimeBy(60.seconds)

        assertEquals(listOf("arm 55"), backups.calls)
    }

    @Test
    fun `a cancelled run disarms its backup when its stop says so`() = runTest {
        val io = StandardTestDispatcher(testScheduler)
        val run = async {
            keepingArmed(backups, FIRST_BACKUP_JOB_ID, io, disarmOnCancel = { true }) {
                CompletableDeferred<Unit>().await()
            }
        }
        runCurrent()

        run.cancel()
        runCurrent()
        advanceTimeBy(60.seconds)

        assertEquals(listOf("arm 55", "disarm 55"), backups.calls)
    }
}

@OptIn(ExperimentalCoroutinesApi::class)
class JobRunsTest {
    private val backups = FakeBackups()
    private val runState = DownloadRunState()
    private val marker = FakeMarker()
    private val decided = CompletableDeferred<Unit>()
    private val runs = JobRuns(backups, runState, marker) { decided.await() }

    @Test
    fun `the user-initiated job keeps the first backup, and each backup keeps the other`() {
        assertEquals(FIRST_BACKUP_JOB_ID, backupFor(QUEUE_JOB_ID))
        assertEquals(SECOND_BACKUP_JOB_ID, backupFor(FIRST_BACKUP_JOB_ID))
        assertEquals(FIRST_BACKUP_JOB_ID, backupFor(SECOND_BACKUP_JOB_ID))
    }

    @Test
    fun `a backup runs only when no run is registered`() {
        assertTrue(runs.startBackup(FIRST_BACKUP_JOB_ID))
        assertTrue(runState.backupRun)

        assertFalse(runs.startBackup(SECOND_BACKUP_JOB_ID))
        assertTrue(FIRST_BACKUP_JOB_ID in runs)
        assertFalse(SECOND_BACKUP_JOB_ID in runs)
    }

    @Test
    fun `two backups that start in the other order still run one`() {
        assertTrue(runs.startBackup(SECOND_BACKUP_JOB_ID))

        assertFalse(runs.startBackup(FIRST_BACKUP_JOB_ID))
        assertTrue(SECOND_BACKUP_JOB_ID in runs)
    }

    @Test
    fun `a backup does nothing while the user-initiated job runs`() {
        runs.startQueueJob()

        assertFalse(runs.startBackup(FIRST_BACKUP_JOB_ID))
        assertFalse(runs.startBackup(SECOND_BACKUP_JOB_ID))
    }

    @Test
    fun `the user-initiated job's start ends the hand-back of a backup run`() {
        runs.startBackup(FIRST_BACKUP_JOB_ID)

        runs.startQueueJob()

        assertFalse(runState.backupRun)
        assertTrue(QUEUE_JOB_ID in runs)
    }

    @Test
    fun `a stop by the user disarms the backup, any other stop leaves it`() {
        runs.startQueueJob()

        assertTrue(runs.disarmsAfterStop(QUEUE_JOB_ID, JobParameters.STOP_REASON_USER))
        for (reason in
            listOf(
                JobParameters.STOP_REASON_CONSTRAINT_CONNECTIVITY,
                JobParameters.STOP_REASON_TIMEOUT,
                JobParameters.STOP_REASON_CANCELLED_BY_APP,
                JobParameters.STOP_REASON_UNDEFINED,
            )) {
            assertFalse(runs.disarmsAfterStop(QUEUE_JOB_ID, reason))
        }
    }

    @Test
    fun `a first backup that the user-initiated job stopped disarms the second`() {
        runs.startBackup(FIRST_BACKUP_JOB_ID)
        runs.startQueueJob()

        // The first backup kept the second, which no other run keeps.
        assertTrue(
            runs.disarmsAfterStop(FIRST_BACKUP_JOB_ID, JobParameters.STOP_REASON_CANCELLED_BY_APP),
        )
    }

    @Test
    fun `a second backup that the user-initiated job stopped leaves the first armed`() {
        runs.startBackup(SECOND_BACKUP_JOB_ID)
        runs.startQueueJob()

        // The second backup kept the first, which the user-initiated job keeps now.
        assertFalse(
            runs.disarmsAfterStop(SECOND_BACKUP_JOB_ID, JobParameters.STOP_REASON_CANCELLED_BY_APP),
        )
    }

    @Test
    fun `a second backup's backup is kept by a user-initiated run that started`() {
        runs.startBackup(SECOND_BACKUP_JOB_ID)
        assertFalse(runs.keptByAnother(SECOND_BACKUP_JOB_ID))

        runs.startQueueJob()

        // Both keep the first backup, so the second backup's end must not disarm it.
        assertTrue(runs.keptByAnother(SECOND_BACKUP_JOB_ID))
        assertTrue(runs.keptByAnother(QUEUE_JOB_ID))
    }

    @Test
    fun `a first backup's backup is kept by no user-initiated run`() {
        runs.startBackup(FIRST_BACKUP_JOB_ID)
        runs.startQueueJob()

        assertFalse(runs.keptByAnother(FIRST_BACKUP_JOB_ID))
    }

    @Test
    fun `a backup stopped for another reason leaves its backup armed`() {
        runs.startBackup(FIRST_BACKUP_JOB_ID)

        assertFalse(runs.disarmsAfterStop(FIRST_BACKUP_JOB_ID, JobParameters.STOP_REASON_TIMEOUT))
    }

    @Test
    fun `a backup run's end clears its registration and its hand-back`() {
        runs.startBackup(FIRST_BACKUP_JOB_ID)

        runs.end(FIRST_BACKUP_JOB_ID)

        assertFalse(FIRST_BACKUP_JOB_ID in runs)
        assertFalse(runState.backupRun)
        assertTrue(runs.startBackup(SECOND_BACKUP_JOB_ID))
    }

    @Test
    fun `the user-initiated run registers before it touches a backup`() = runTest {
        val io = StandardTestDispatcher(testScheduler)
        val registeredAtCall = mutableListOf<Boolean>()
        backups.onCall = { registeredAtCall += QUEUE_JOB_ID in runs }
        val done = CompletableDeferred<RunOutcome>()

        val run = async { runs.queueRun(io, disarmOnCancel = { false }) { done.await() } }
        decided.complete(Unit)
        runCurrent()

        assertTrue("disarm 56" in backups.calls)
        assertTrue("arm 55" in backups.calls)
        assertTrue(registeredAtCall.all { it })
        done.complete(RunOutcome.DONE)
        assertEquals(RunOutcome.DONE, run.await())
        assertEquals("disarm 55", backups.calls.last())
    }

    @Test
    fun `a stopped user-initiated run leaves the first backup armed`() = runTest {
        val io = StandardTestDispatcher(testScheduler)
        val run = async {
            runs.queueRun(io, disarmOnCancel = { false }) { CompletableDeferred<Unit>().await() }
        }
        decided.complete(Unit)
        runCurrent()

        run.cancel()
        runCurrent()

        assertFalse("disarm 55" in backups.calls)
    }

    @Test
    fun `the user-initiated run deletes the marker once the stop check decided, before its end`() =
        runTest {
            val io = StandardTestDispatcher(testScheduler)
            marker.present = true
            val done = CompletableDeferred<RunOutcome>()

            val run = async { runs.queueRun(io, disarmOnCancel = { false }) { done.await() } }
            runCurrent()
            assertTrue(marker.present)

            decided.complete(Unit)
            runCurrent()
            assertFalse(marker.present)
            done.complete(RunOutcome.DONE)
            run.await()
        }

    @Test
    fun `a backup run writes the marker only while no user-initiated run is registered`() {
        runs.startBackup(SECOND_BACKUP_JOB_ID)
        runs.backupBegins()
        assertTrue(marker.present)

        marker.present = false
        runs.startQueueJob()
        runs.backupBegins()

        assertFalse(marker.present)
    }

    @Test
    fun `a finished backup run leaves a user-initiated job that already started here`() = runTest {
        runs.startQueueJob()

        runs.afterBackupDone()

        assertEquals(emptyList(), backups.calls)
    }

    @Test
    fun `a finished backup run waits for the run state's lock before it cancels`() = runTest {
        runState.mutex.lock()
        val done = async { runs.afterBackupDone() }
        runCurrent()
        assertEquals(emptyList(), backups.calls)

        // A tap scheduled the job while the lock was held.
        runState.phase = RunPhase.SCHEDULED
        runState.mutex.unlock()
        done.await()

        assertEquals(emptyList(), backups.calls)
    }

    @Test
    fun `a finished backup run cancels the idle user-initiated job only while idle`() = runTest {
        runs.afterBackupDone()
        assertEquals(listOf("cancel 54"), backups.calls)

        runState.phase = RunPhase.SCHEDULED
        runs.afterBackupDone()
        assertEquals(listOf("cancel 54"), backups.calls)
    }
}

/** Records the backup run's notifications, and shows them while [shown] says so. */
private class FakeShade : NoticeShade {
    val set = mutableListOf<Int?>()
    var shown = false

    override fun set(progress: DownloadProgress) {
        set += progress.percent
    }

    override fun isShown() = shown
}

class BackupNoticeTest {
    private val shade = FakeShade()
    private val notice = BackupNotice(shade)

    private fun at(percent: Int?) =
        DownloadProgress(
            downloadedBytes = (percent ?: 0).toLong(),
            totalBytes = percent?.let { 100L },
        )

    @Test
    fun `it is set at the first report, then on each percent change while it shows`() {
        notice.report(at(null))
        shade.shown = true
        notice.report(at(1))
        notice.report(at(1))
        notice.report(at(2))

        assertEquals(listOf(null, 1, 2), shade.set)
    }

    @Test
    fun `one that never shows is set only once`() {
        // Swiped away before any report looked, or no notification permission.
        notice.report(at(1))
        notice.report(at(2))
        notice.report(at(3))

        assertEquals(listOf<Int?>(1), shade.set)
    }

    @Test
    fun `a first one that shows late is set again at the next percent change`() {
        notice.report(at(1))
        notice.report(at(2))
        shade.shown = true
        notice.report(at(3))

        assertEquals(listOf<Int?>(1, 3), shade.set)
    }

    @Test
    fun `once seen and gone, it isn't set again during the run`() {
        notice.report(at(1))
        shade.shown = true
        notice.report(at(2))
        shade.shown = false
        notice.report(at(3))
        notice.report(at(4))

        assertEquals(listOf<Int?>(1, 2), shade.set)
    }
}
