package io.github.dewfallapp.core.playback

import io.github.dewfallapp.core.data.watch.WatchStateRepository
import io.github.dewfallapp.core.model.VideoId
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.retry
import kotlinx.coroutines.launch
import kotlinx.coroutines.supervisorScope

/** How many times following a video's watched mark reads the database again after a failure. */
private const val FOLLOW_RETRIES = 5L

/** How long following a video's watched mark waits before it reads the database again. */
private val FOLLOW_RETRY_DELAY = 2.seconds

/**
 * Saves where each video was left, and marks it watched near its end, for the playback service. The
 * service tells it which item is current and when to look or save, on the main thread. It keeps the
 * current item's video ID only while that item is the player's, and drops it when another item or
 * none takes its place, after the last save for it was queued.
 *
 * Writes and reads run one at a time, in the order the service asked for them, through one Channel
 * and one consumer in its own scope, so a save that the service asks for in onDestroy still lands
 * after the service's scope is gone. A write or read that fails logs one constant-text line and the
 * next one goes on. The IDs and positions are user data, so it never logs them. See
 * docs/architecture.md#saving-where-a-video-was-left.
 */
@Singleton
class WatchProgress
@Inject
internal constructor(
    private val repository: WatchStateRepository,
    private val log: PlaybackLog,
    dispatchers: PlaybackDispatchers,
) {
    /** One write or read, in the order they were asked for. */
    private sealed interface Operation {
        /** Saves [position] if it isn't null, then marks the video if [marks]. */
        class Write(
            val videoId: VideoId,
            val position: Duration?,
            val marks: Boolean,
            /** The playback that asked for it, which a failed mark goes back to. */
            val origin: Current? = null,
        ) : Operation

        /** Reads the video's saved position into [answer], or null. */
        class Read(val videoId: VideoId, val answer: CompletableDeferred<Duration?>) : Operation
    }

    /** The current item: its playback ID, video, whether it is live, and its two flags. */
    private class Current(val playbackId: String, val videoId: VideoId, val live: Boolean) {
        /** It has seen a position below 90 percent since it last marked the video. */
        var belowSeen = false

        /** It reached the end and hasn't played or jumped since. */
        var atEnd = false

        /** A mark it asked for failed to write, so its next look or save asks again. */
        var markPending = false

        /**
         * Follows whether the video is marked, so a mark, or an unmark after a mark, clears
         * [belowSeen] and [markPending].
         */
        var marks: Job? = null

        /** Whether a failed mark waits to be asked again, which this ask takes. */
        fun takePending(): Boolean {
            val pending = markPending
            markPending = false
            return pending
        }
    }

    private val failures = CoroutineExceptionHandler { _, error -> log.watchStateFailed(error) }
    private val scope = CoroutineScope(SupervisorJob() + dispatchers.main)
    private val operations = Channel<Operation>(Channel.UNLIMITED)

    /** Only the main thread touches it. */
    private var current: Current? = null

    init {
        scope.launch {
            for (operation in operations) {
                // Each one in its own child, so a failure ends only that child, and the next one
                // starts only after it.
                val child = supervisorScope {
                    launch(failures) { run(operation) }.also { it.join() }
                }
                if (child.isCancelled && operation is Operation.Write && operation.marks) {
                    // The mark failed: the playback that asked asks again while it is still
                    // current, and
                    // a later playback of the same video doesn't inherit it.
                    current?.takeIf { it === operation.origin }?.markPending = true
                }
            }
        }
    }

    /**
     * The player's current item is now [playbackId], of [videoId], which is [live], or none. The
     * same playback ID with new links, as after a refresh, keeps its flags.
     */
    internal fun itemChanged(playbackId: String?, videoId: VideoId?, live: Boolean) {
        val previous = current
        if (previous != null && previous.playbackId == playbackId) return
        previous?.marks?.cancel()
        current =
            if (playbackId != null && videoId != null) {
                Current(playbackId, videoId, live).also(::followMarks)
            } else {
                null
            }
    }

    /** The current video's ID, while there is one. For tests of what it keeps. */
    internal fun currentVideo(): VideoId? = current?.videoId

    /**
     * Looks at the position, at [positionMs] of [durationMs], without saving it. It marks the video
     * when the playback crossed 90 percent.
     */
    internal fun look(positionMs: Long, durationMs: Long) {
        val item = current?.takeUnless { it.live } ?: return
        if (marksNow(item, positionMs, durationMs)) {
            enqueue(Operation.Write(item.videoId, null, true, origin = item))
        }
    }

    /** Saves the position, at [positionMs] of [durationMs], and marks the video if it crossed. */
    internal fun save(positionMs: Long, durationMs: Long) {
        val item = current?.takeUnless { it.live } ?: return
        val marks = if (item.atEnd) item.takePending() else marksNow(item, positionMs, durationMs)
        enqueue(Operation.Write(item.videoId, progressSave(positionMs, item.atEnd), marks, item))
    }

    /** The current item played to its end: it marks the video, and saves zero until it plays on. */
    internal fun ended() {
        val item = current?.takeUnless { it.live } ?: return
        item.belowSeen = false
        item.atEnd = true
        item.markPending = false
        enqueue(Operation.Write(item.videoId, progressSave(0, atEnd = true), marks = true, item))
    }

    /** The current item plays again, or its position jumped, so it is no longer at its end. */
    internal fun movedOn() {
        current?.atEnd = false
    }

    /**
     * The saved position of [videoId], or null when it has none or it can't be read. It waits for
     * every write asked for before it.
     */
    internal suspend fun storedPosition(videoId: VideoId): Duration? {
        val answer = CompletableDeferred<Duration?>()
        enqueue(Operation.Read(videoId, answer))
        return answer.await()
    }

    /** Whether this look marks the video: it crossed 90 percent, or an earlier mark failed. */
    private fun marksNow(item: Current, positionMs: Long, durationMs: Long): Boolean {
        val next = progressStep(item.belowSeen, positionMs, durationMs, ended = false)
        item.belowSeen = next.belowSeen
        return item.takePending() || next.marks
    }

    private fun followMarks(item: Current) {
        var markedBefore = false
        item.marks =
            scope.launch(failures) {
                // Each try asks the repository again.
                flow { emitAll(repository.watchState(item.videoId)) }
                    .map { it?.watchedAt != null }
                    // A read that fails tries again, so a hand unmark after it still counts.
                    .retry(FOLLOW_RETRIES) { error ->
                        log.watchStateFailed(error)
                        delay(FOLLOW_RETRY_DELAY)
                        true
                    }
                    .distinctUntilChanged()
                    .collect { watched ->
                        // A mark, or an unmark after one, is newer than what playback saw.
                        if (watched || markedBefore) {
                            item.belowSeen = false
                            item.markPending = false
                        }
                        markedBefore = watched
                    }
            }
    }

    private fun enqueue(operation: Operation) {
        // The Channel has no capacity limit and is never closed, so this always succeeds.
        operations.trySend(operation)
    }

    private suspend fun run(operation: Operation) {
        when (operation) {
            is Operation.Write -> {
                operation.position?.let { repository.savePosition(operation.videoId, it) }
                if (operation.marks) repository.markWatched(operation.videoId)
            }
            is Operation.Read -> {
                try {
                    val position =
                        repository
                            .watchState(operation.videoId)
                            .map { it?.position }
                            .catch { error ->
                                log.watchStateFailed(error)
                                emit(null)
                            }
                            .first()
                    operation.answer.complete(position)
                } finally {
                    // A read that ends another way still answers, so the start never waits.
                    operation.answer.complete(null)
                }
            }
        }
    }
}
