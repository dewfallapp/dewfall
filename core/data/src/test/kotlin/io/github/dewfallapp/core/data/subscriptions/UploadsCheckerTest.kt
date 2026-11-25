package io.github.dewfallapp.core.data.subscriptions

import io.github.dewfallapp.core.data.channel.ChannelUploadsRepository
import io.github.dewfallapp.core.data.video.StoredVideosRepository
import io.github.dewfallapp.core.logging.LogLevel
import io.github.dewfallapp.core.logging.LogWriter
import io.github.dewfallapp.core.logging.Logger
import io.github.dewfallapp.core.model.Channel
import io.github.dewfallapp.core.model.ChannelId
import io.github.dewfallapp.core.model.ChannelOverview
import io.github.dewfallapp.core.model.ChannelUploads
import io.github.dewfallapp.core.model.SubscribedChannel
import io.github.dewfallapp.core.model.UploadsCheck
import io.github.dewfallapp.core.model.UploadsCheckFailure
import io.github.dewfallapp.core.model.VideoSummary
import io.github.dewfallapp.core.model.YouTubeError
import io.github.dewfallapp.core.model.YouTubeResult
import java.io.IOException
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.time.Instant
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest

private val SUBSCRIBED_AT = Instant.parse("2026-11-01T12:00:00Z")

/** A made-up channel ID that sorts with [n]. */
private fun id(n: Int) = ChannelId("UC" + n.toString().padStart(22, '0'))

private fun channel(n: Int) = Channel(id(n), "Channel $n", emptyList(), "")

private fun subscribed(vararg n: Int) = n.map { SubscribedChannel(channel(it), SUBSCRIBED_AT) }

private fun uploads(n: Int) = ChannelUploads(channel(n), videos = emptyList())

/** Records the subscriptions' writes, and returns the snapshot each test gives it. */
private class CheckedSubscriptions(
    private val channels: Flow<List<SubscribedChannel>>,
    private val writes: MutableList<String>,
    private val moveFails: Boolean = false,
) : SubscriptionsRepository {
    override fun subscribedChannels(): Flow<List<SubscribedChannel>> = channels

    override fun channelOverviews(): Flow<List<ChannelOverview>> = emptyFlow()

    override fun subscribedChannel(channelId: ChannelId): Flow<SubscribedChannel?> = emptyFlow()

    override fun isSubscribed(channelId: ChannelId): Flow<Boolean> = emptyFlow()

    override suspend fun subscribe(channel: Channel) = error("Not used")

    override suspend fun subscribeAll(channels: List<Channel>): Int = error("Not used")

    override suspend fun unsubscribe(channelId: ChannelId) = error("Not used")

    override suspend fun moveSubscription(from: ChannelId, to: Channel) {
        if (moveFails) throw IOException("disk full")
        writes += "move ${from.value} to ${to.id.value}"
    }
}

/** Records saves. */
private class CheckedStoredVideos(
    private val writes: MutableList<String>,
    private val saveFails: Boolean = false,
) : StoredVideosRepository {
    override suspend fun saveUploads(uploads: ChannelUploads) {
        if (saveFails) throw IOException("disk full")
        writes += "save ${uploads.channel.id.value}"
    }

    override fun inboxVideos(): Flow<List<VideoSummary>> = emptyFlow()
}

/** Answers each channel ID from [answers], records the IDs asked for, and can hold every call. */
private class CheckedUploads(
    private val answers: Map<ChannelId, YouTubeResult<ChannelUploads>>,
    private val gate: CompletableDeferred<Unit>? = null,
) : ChannelUploadsRepository {
    val asked = mutableListOf<ChannelId>()

    override suspend fun latestUploads(channelId: ChannelId): YouTubeResult<ChannelUploads> {
        asked += channelId
        gate?.await()
        return answers.getValue(channelId)
    }
}

/**
 * One checker with its fakes, the writes in the order they ran, and the log's lines. The checker
 * runs in a SupervisorJob child of [testScope], as DatabaseModule's app-wide scope has a
 * SupervisorJob, so a check that throws reaches its own exception handler.
 */
private class Setup(
    testScope: CoroutineScope,
    channels: Flow<List<SubscribedChannel>>,
    answers: Map<ChannelId, YouTubeResult<ChannelUploads>>,
    gate: CompletableDeferred<Unit>? = null,
    moveFails: Boolean = false,
    saveFails: Boolean = false,
) {
    val writes = mutableListOf<String>()
    val lines = mutableListOf<String>()
    val uploads = CheckedUploads(answers, gate)
    val checker =
        RepositoriesUploadsChecker(
            subscriptions = CheckedSubscriptions(channels, writes, moveFails),
            uploads = uploads,
            storedVideos = CheckedStoredVideos(writes, saveFails),
            externalScope =
                CoroutineScope(
                    testScope.coroutineContext + SupervisorJob(testScope.coroutineContext[Job]),
                ),
            logger = Logger(LogWriter { _, _, text -> lines += text }, LogLevel.DEBUG),
        )
}

private fun TestScope.setup(
    channels: List<SubscribedChannel>,
    answers: Map<ChannelId, YouTubeResult<ChannelUploads>>,
    gate: CompletableDeferred<Unit>? = null,
    moveFails: Boolean = false,
    saveFails: Boolean = false,
) = Setup(backgroundScope, flowOf(channels), answers, gate, moveFails, saveFails)

private fun ok(n: Int) = YouTubeResult.Success(uploads(n))

private fun failed(error: YouTubeError) = YouTubeResult.Failure(error)

// runCurrent is still marked experimental.
@OptIn(ExperimentalCoroutinesApi::class)
class UploadsCheckerTest {
    @Test
    fun `every channel is asked for in turn and saved`() = runTest {
        val s = setup(subscribed(1, 2, 3), mapOf(id(1) to ok(1), id(2) to ok(2), id(3) to ok(3)))

        s.checker.start()
        runCurrent()

        assertEquals(listOf(id(1), id(2), id(3)), s.uploads.asked)
        assertEquals(
            listOf("save ${id(1).value}", "save ${id(2).value}", "save ${id(3).value}"),
            s.writes,
        )
        assertEquals(UploadsCheck(), s.checker.state.value)
    }

    @Test
    fun `no connection stops the check at once`() = runTest {
        val s =
            setup(subscribed(1, 2, 3), mapOf(id(1) to ok(1), id(2) to failed(YouTubeError.NETWORK)))

        s.checker.start()
        runCurrent()

        assertEquals(listOf(id(1), id(2)), s.uploads.asked)
        assertEquals(listOf("save ${id(1).value}"), s.writes)
        assertEquals(
            UploadsCheck(failure = UploadsCheckFailure.CANT_REACH_YOUTUBE),
            s.checker.state.value,
        )
    }

    @Test
    fun `a refusal stops the check at once`() = runTest {
        val s = setup(subscribed(1, 2), mapOf(id(1) to failed(YouTubeError.BLOCKED)))

        s.checker.start()
        runCurrent()

        assertEquals(listOf(id(1)), s.uploads.asked)
        assertEquals(
            UploadsCheck(failure = UploadsCheckFailure.YOUTUBE_NOT_LOADING),
            s.checker.state.value,
        )
    }

    @Test
    fun `a channel that fails on its own is skipped quietly`() = runTest {
        for (error in YouTubeError.entries - YouTubeError.NETWORK - YouTubeError.BLOCKED) {
            val s = setup(subscribed(1, 2), mapOf(id(1) to failed(error), id(2) to ok(2)))

            s.checker.start()
            runCurrent()

            assertEquals(listOf(id(1), id(2)), s.uploads.asked, error.name)
            assertEquals(listOf("save ${id(2).value}"), s.writes, error.name)
            assertEquals(UploadsCheck(), s.checker.state.value, error.name)
        }
    }

    @Test
    fun `three unreadable answers in a row stop the check`() = runTest {
        val broken = failed(YouTubeError.BROKEN)
        val s =
            setup(
                subscribed(1, 2, 3, 4, 5),
                mapOf(id(1) to ok(1), id(2) to broken, id(3) to broken, id(4) to broken),
            )

        s.checker.start()
        runCurrent()

        assertEquals(listOf(id(1), id(2), id(3), id(4)), s.uploads.asked)
        assertEquals(
            UploadsCheck(failure = UploadsCheckFailure.YOUTUBE_NOT_LOADING),
            s.checker.state.value,
        )
    }

    @Test
    fun `unreadable answers apart from each other don't stop the check`() = runTest {
        val broken = failed(YouTubeError.BROKEN)
        val closed = failed(YouTubeError.CHANNEL_CLOSED)
        val s =
            setup(
                subscribed(1, 2, 3, 4, 5, 6),
                mapOf(
                    id(1) to broken,
                    id(2) to broken,
                    id(3) to closed,
                    id(4) to broken,
                    id(5) to broken,
                    id(6) to ok(6),
                ),
            )

        s.checker.start()
        runCurrent()

        assertEquals(6, s.uploads.asked.size)
        assertEquals(UploadsCheck(), s.checker.state.value)
    }

    @Test
    fun `one unreadable answer and nothing read means YouTube isn't loading`() = runTest {
        val s = setup(subscribed(1), mapOf(id(1) to failed(YouTubeError.BROKEN)))

        s.checker.start()
        runCurrent()

        assertEquals(
            UploadsCheck(failure = UploadsCheckFailure.YOUTUBE_NOT_LOADING),
            s.checker.state.value,
        )
    }

    @Test
    fun `a closed channel counts as an answer read`() = runTest {
        val s =
            setup(
                subscribed(1, 2),
                mapOf(
                    id(1) to failed(YouTubeError.CHANNEL_CLOSED),
                    id(2) to failed(YouTubeError.BROKEN),
                ),
            )

        s.checker.start()
        runCurrent()

        assertEquals(UploadsCheck(), s.checker.state.value)
    }

    @Test
    fun `a moved channel's subscription moves before the save`() = runTest {
        val s = setup(subscribed(1), mapOf(id(1) to ok(9)))

        s.checker.start()
        runCurrent()

        assertEquals(
            listOf("move ${id(1).value} to ${id(9).value}", "save ${id(9).value}"),
            s.writes,
        )
    }

    @Test
    fun `a channel already answered under its new ID isn't asked for again`() = runTest {
        // Subscribed under the old ID 1 and the new ID 2, and 1 comes first by name.
        val s = setup(subscribed(1, 2), mapOf(id(1) to ok(2), id(2) to ok(2)))

        s.checker.start()
        runCurrent()

        assertEquals(listOf(id(1)), s.uploads.asked)
    }

    @Test
    fun `a second start while a check runs starts nothing`() = runTest {
        val gate = CompletableDeferred<Unit>()
        val s = setup(subscribed(1), mapOf(id(1) to ok(1)), gate = gate)

        s.checker.start()
        runCurrent()
        assertEquals(UploadsCheck(running = true), s.checker.state.value)
        s.checker.start()
        s.checker.start()
        runCurrent()
        gate.complete(Unit)
        runCurrent()

        assertEquals(listOf(id(1)), s.uploads.asked)
        assertEquals(UploadsCheck(), s.checker.state.value)
    }

    @Test
    fun `a check started again after a failure runs with no failure while it runs`() = runTest {
        val answers = mutableMapOf<ChannelId, YouTubeResult<ChannelUploads>>()
        answers[id(1)] = failed(YouTubeError.NETWORK)
        val s = Setup(backgroundScope, flowOf(subscribed(1)), answers)
        s.checker.start()
        runCurrent()
        assertEquals(UploadsCheckFailure.CANT_REACH_YOUTUBE, s.checker.state.value.failure)

        answers[id(1)] = ok(1)
        val seen = mutableListOf<UploadsCheck>()
        s.checker.start()
        seen += s.checker.state.value
        runCurrent()

        assertEquals(listOf(UploadsCheck(running = true)), seen)
        assertEquals(UploadsCheck(), s.checker.state.value)
    }

    @Test
    fun `a check goes on after the caller is cancelled`() = runTest {
        val gate = CompletableDeferred<Unit>()
        val s = setup(subscribed(1), mapOf(id(1) to ok(1)), gate = gate)
        val caller = launch {
            s.checker.start()
            awaitCancellation()
        }
        runCurrent()

        caller.cancel()
        gate.complete(Unit)
        runCurrent()

        assertEquals(listOf("save ${id(1).value}"), s.writes)
    }

    @Test
    fun `a subscriptions read that throws ends the check quietly`() = runTest {
        val throwing = flow<List<SubscribedChannel>> { throw IOException("damaged") }
        val s = Setup(backgroundScope, throwing, emptyMap())

        s.checker.start()
        runCurrent()

        assertEquals(UploadsCheck(), s.checker.state.value)
        assertTrue(s.lines.any { it.startsWith("Uploads check failed on the phone") })
    }

    @Test
    fun `a move that throws ends the check quietly with no more requests`() = runTest {
        val s = setup(subscribed(1, 2), mapOf(id(1) to ok(9), id(2) to ok(2)), moveFails = true)

        s.checker.start()
        runCurrent()

        assertEquals(listOf(id(1)), s.uploads.asked)
        assertEquals(emptyList(), s.writes)
        assertEquals(UploadsCheck(), s.checker.state.value)
    }

    @Test
    fun `a save that throws ends the check quietly with no more requests`() = runTest {
        val s = setup(subscribed(1, 2), mapOf(id(1) to ok(1), id(2) to ok(2)), saveFails = true)

        s.checker.start()
        runCurrent()

        assertEquals(listOf(id(1)), s.uploads.asked)
        assertEquals(UploadsCheck(), s.checker.state.value)
        s.checker.start()
        runCurrent()
        assertEquals(listOf(id(1), id(1)), s.uploads.asked)
    }

    @Test
    fun `the log holds counts and the failure, and no channel`() = runTest {
        val s =
            setup(
                subscribed(1, 2, 3),
                mapOf(
                    id(1) to ok(9),
                    id(2) to failed(YouTubeError.CHANNEL_CLOSED),
                    id(3) to failed(YouTubeError.NETWORK),
                ),
            )

        s.checker.start()
        runCurrent()

        assertEquals(
            listOf(
                "Uploads check started channels=3",
                "Uploads check stopped saved=1 moved=1 skipped=1 failure=CANT_REACH_YOUTUBE",
            ),
            s.lines,
        )
        for (n in listOf(1, 2, 3, 9)) {
            assertFalse(s.lines.any { id(n).value in it || "Channel $n" in it })
        }
    }

    @Test
    fun `a check that reaches every channel logs that it ended`() = runTest {
        val s = setup(subscribed(1), mapOf(id(1) to ok(1)))

        s.checker.start()
        runCurrent()

        assertEquals("Uploads check ended saved=1 moved=0 skipped=0", s.lines.last())
    }
}
