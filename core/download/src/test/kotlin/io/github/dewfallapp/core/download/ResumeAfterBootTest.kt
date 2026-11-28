package io.github.dewfallapp.core.download

import io.github.dewfallapp.core.data.download.QueueState
import io.github.dewfallapp.core.logging.LogLevel
import io.github.dewfallapp.core.logging.LogWriter
import io.github.dewfallapp.core.logging.Logger
import io.github.dewfallapp.core.model.VideoId
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.test.runTest

private val VIDEO = VideoId("aaaaaaaaaaa")

/** Counts its launches, and answers each with [accepts], or throws [throws] when it is set. */
private class BootLauncher(var accepts: Boolean = false) : RunLauncher {
    var launches = 0
    var throws: Exception? = null

    override suspend fun launch(): Boolean {
        launches++
        throws?.let { throw it }
        return accepts
    }
}

/** QueueScheduler.resumeAfterBoot, which BootReceiver runs after every boot. See #280. */
class ResumeAfterBootTest {
    private val queue = FakeQueue()
    private val runState = DownloadRunState()
    private val launcher = BootLauncher()
    private val backups = FakeBackups()
    private val lines = mutableListOf<String>()
    private val logger = Logger(LogWriter { _, _, text -> lines += text }, LogLevel.DEBUG)

    private fun scheduler(scope: CoroutineScope, stopCheck: StopCheck = StopCheck {}) =
        QueueScheduler(queue, runState, launcher, backups, stopCheck, scope, logger)

    @Test
    fun `a waiting download whose job Android dropped and won't schedule arms the first backup`() =
        runTest {
            queue.add(FakeRow(VIDEO, "f"))

            scheduler(backgroundScope).resumeAfterBoot()

            assertEquals(1, launcher.launches)
            assertEquals(listOf("arm $FIRST_BACKUP_JOB_ID"), backups.calls)
            assertEquals(RunPhase.IDLE, runState.phase)
            assertTrue(lines.any { "Download backup armed after boot" in it }, lines.toString())
        }

    @Test
    fun `a running row that the reboot left waits again before the backup is armed`() = runTest {
        queue.add(FakeRow(VIDEO, "f", state = QueueState.DOWNLOADING))
        var stateAtArm: QueueState? = null
        backups.onCall = { stateAtArm = queue.row(VIDEO).state }

        scheduler(backgroundScope).resumeAfterBoot()

        assertEquals(QueueState.WAITING, stateAtArm)
        assertEquals(listOf("arm $FIRST_BACKUP_JOB_ID"), backups.calls)
    }

    @Test
    fun `a job that Android restored is left to run, with its rows as they are`() = runTest {
        queue.add(FakeRow(VIDEO, "f", state = QueueState.DOWNLOADING))
        backups.queueJob = true

        scheduler(backgroundScope).resumeAfterBoot()

        assertEquals(0, launcher.launches)
        assertEquals(emptyList(), backups.calls)
        assertEquals(QueueState.DOWNLOADING, queue.row(VIDEO).state)
        assertEquals(RunPhase.IDLE, runState.phase)
    }

    @Test
    fun `a job that Android schedules from the boot needs no backup`() = runTest {
        queue.add(FakeRow(VIDEO, "f"))
        launcher.accepts = true

        scheduler(backgroundScope).resumeAfterBoot()

        assertEquals(1, launcher.launches)
        assertEquals(emptyList(), backups.calls)
        assertEquals(RunPhase.SCHEDULED, runState.phase)
    }

    @Test
    fun `a launch that throws counts as refused, so the backup is still armed`() = runTest {
        queue.add(FakeRow(VIDEO, "f"))
        launcher.throws = SecurityException("RUN_USER_INITIATED_JOBS was denied")

        scheduler(backgroundScope).resumeAfterBoot()

        assertEquals(listOf("arm $FIRST_BACKUP_JOB_ID"), backups.calls)
        assertTrue(lines.any { "Download run after boot failed" in it }, lines.toString())
        assertTrue(lines.none { "was denied" in it }, lines.toString())
    }

    @Test
    fun `a backup that JobScheduler refuses is logged as not armed, and the row waits`() = runTest {
        queue.add(FakeRow(VIDEO, "f"))
        backups.armAccepts = false

        scheduler(backgroundScope).resumeAfterBoot()

        assertEquals(listOf("arm $FIRST_BACKUP_JOB_ID"), backups.calls)
        assertEquals(QueueState.WAITING, queue.row(VIDEO).state)
        assertEquals(RunPhase.IDLE, runState.phase)
        assertTrue(lines.any { "Download backup not armed after boot" in it }, lines.toString())
        assertTrue(lines.none { "Download backup armed after boot" in it }, lines.toString())
    }

    @Test
    fun `nothing waiting launches and arms nothing`() = runTest {
        queue.add(FakeRow(VIDEO, "f", state = QueueState.PAUSED))

        scheduler(backgroundScope).resumeAfterBoot()

        assertEquals(0, launcher.launches)
        assertEquals(emptyList(), backups.calls)
    }

    @Test
    fun `a queue that the stop check paused launches and arms nothing`() = runTest {
        queue.add(FakeRow(VIDEO, "f", state = QueueState.DOWNLOADING))

        scheduler(backgroundScope, StopCheck { queue.pauseQueued() }).resumeAfterBoot()

        assertEquals(QueueState.PAUSED, queue.row(VIDEO).state)
        assertEquals(0, launcher.launches)
        assertEquals(emptyList(), backups.calls)
    }

    @Test
    fun `nothing happens while a run is scheduled or running`() = runTest {
        queue.add(FakeRow(VIDEO, "f"))
        for (phase in listOf(RunPhase.SCHEDULED, RunPhase.RUNNING)) {
            runState.phase = phase

            scheduler(backgroundScope).resumeAfterBoot()

            assertEquals(0, launcher.launches)
            assertEquals(emptyList(), backups.calls)
            assertEquals(phase, runState.phase)
        }
    }

    @Test
    fun `below Android 14 a refused start arms nothing and says the run wasn't scheduled`() =
        runTest {
            queue.add(FakeRow(VIDEO, "f"))
            backups.available = false

            scheduler(backgroundScope).resumeAfterBoot()

            assertEquals(1, launcher.launches)
            assertEquals(emptyList(), backups.calls)
            assertTrue(lines.any { "Download run not scheduled" in it }, lines.toString())
            assertTrue(lines.none { "Download backup armed after boot" in it }, lines.toString())
        }

    @Test
    fun `the app's return before the backup starts schedules the user-initiated job`() = runTest {
        queue.add(FakeRow(VIDEO, "f"))
        val scheduler = scheduler(backgroundScope)
        scheduler.resumeAfterBoot()
        launcher.accepts = true

        scheduler.startNow()

        assertEquals(2, launcher.launches)
        assertEquals(RunPhase.SCHEDULED, runState.phase)
    }
}
