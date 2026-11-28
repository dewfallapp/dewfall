package io.github.dewfallapp.core.download

import io.github.dewfallapp.core.data.download.QueueState
import io.github.dewfallapp.core.data.playback.StreamLinksRepository
import io.github.dewfallapp.core.logging.Logger
import io.github.dewfallapp.core.model.VideoId
import io.github.dewfallapp.core.model.YouTubeError
import io.github.dewfallapp.core.model.YouTubeResult
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.test.runTest

/**
 * A start that comes while a run is running, such as a tap on a paused download while the run's own
 * download waits for the network. Each ask comes from inside a links call, after the claim and
 * outside the run state's lock, which the claim holds and which the scheduler takes too.
 */
internal class AskDuringRunTest : RunnerFixture() {
    private val launcher = CountingLauncher()

    /** Each video's links calls, in order. */
    private val calls = mutableMapOf<VideoId, Int>()

    private fun scheduler(scope: CoroutineScope) =
        QueueScheduler(queue, runState, launcher, FakeBackups(), StopCheck {}, scope, Logger.Silent)

    /** Fetches each video's links as the fixture does, and runs [before] with its call count. */
    private fun counting(before: suspend (VideoId, Int) -> Unit) = StreamLinksRepository { id ->
        val call = (calls[id] ?: 0) + 1
        calls[id] = call
        before(id, call)
        streamLinks.streamLinks(id)
    }

    @Test
    fun `a download asked for while the run's download waits is taken by that run`() = runTest {
        waiting(FIRST)
        val scheduler = scheduler(backgroundScope)
        val links = counting { id, call ->
            if (id == FIRST && call == 1) {
                waiting(SECOND)
                scheduler.startNow()
                linkAnswers += YouTubeResult.Failure(YouTubeError.NETWORK)
            }
        }

        assertEquals(RunOutcome.DONE, runner(links = links).run { reports += it })

        assertEquals(QueueState.DOWNLOADED, queue.row(FIRST).state)
        assertEquals(QueueState.DOWNLOADED, queue.row(SECOND).state)
        assertEquals(0, launcher.launches)
        assertEquals(mapOf(FIRST to 2, SECOND to 1), calls)
        assertEquals(RunPhase.IDLE, runState.phase)
    }

    @Test
    fun `a run whose download waits ends IDLE before its cleanup, so a start then schedules`() =
        runTest {
            waiting(FIRST)
            linkAnswers += YouTubeResult.Failure(YouTubeError.NETWORK)
            var phaseAtEnd: RunPhase? = null
            record.onEnd = { phaseAtEnd = runState.phase }

            assertEquals(RunOutcome.RETRY, run())

            assertEquals(RunPhase.IDLE, phaseAtEnd)
            scheduler(backgroundScope).startNow()
            assertEquals(1, launcher.launches)
            assertEquals(QueueState.WAITING, queue.row(FIRST).state)
        }

    @Test
    fun `an ask before the last claim doesn't make a waiting download try twice`() = runTest {
        waiting(FIRST)
        waiting(SECOND)
        val scheduler = scheduler(backgroundScope)
        val links = counting { id, _ ->
            if (id == FIRST) scheduler.startNow()
            if (id == SECOND) linkAnswers += YouTubeResult.Failure(YouTubeError.NETWORK)
        }

        assertEquals(RunOutcome.RETRY, runner(links = links).run { reports += it })

        assertEquals(QueueState.DOWNLOADED, queue.row(FIRST).state)
        assertEquals(QueueState.WAITING, queue.row(SECOND).state)
        assertEquals(mapOf(FIRST to 1, SECOND to 1), calls)
        assertEquals(0, launcher.launches)
    }
}
