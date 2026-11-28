package io.github.dewfallapp.core.download

import io.github.dewfallapp.core.data.download.QueueState
import io.github.dewfallapp.core.logging.Logger
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.test.runTest

/**
 * A history whose last exit was the user's, at 2000, after the update at 1000, from a process that
 * wasn't in the foreground, as a worker without a foreground service on Android 12 or 13 is.
 */
private class BackgroundExitHistory(private val swiped: Boolean) : AppHistory {
    override fun latestExit() =
        LatestExit(stopped = true, atMillis = 2_000, foreground = false, swiped = swiped)

    override fun updatedAtMillis() = 1_000L
}

/** How the stop check treats a swipe of a background worker's process, and Force stop. */
internal class SwipeRunTest : RunnerFixture() {
    private suspend fun runAfter(history: AppHistory, scope: CoroutineScope) =
        run(DownloadsAfterStop(queue, history, scope, Logger.Silent))

    @Test
    fun `a swipe of a background worker goes on with the download`() = runTest {
        queue.add(FakeRow(FIRST, "aaaaaaaaaaa-folder", state = QueueState.DOWNLOADING))

        assertEquals(
            RunOutcome.DONE,
            runAfter(BackgroundExitHistory(swiped = true), backgroundScope),
        )

        assertEquals(QueueState.DOWNLOADED, queue.row(FIRST).state)
    }

    @Test
    fun `Force stop during a background worker pauses the download`() = runTest {
        queue.add(FakeRow(FIRST, "aaaaaaaaaaa-folder", state = QueueState.DOWNLOADING))

        assertEquals(
            RunOutcome.DONE,
            runAfter(BackgroundExitHistory(swiped = false), backgroundScope),
        )

        assertEquals(QueueState.PAUSED, queue.row(FIRST).state)
    }
}
