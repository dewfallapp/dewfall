package io.github.dewfallapp.feature.player.ui

import androidx.media3.common.Player
import io.github.dewfallapp.core.data.download.DownloadsRepository
import io.github.dewfallapp.core.data.video.VideoDetailsRepository
import io.github.dewfallapp.core.data.watch.WatchStateRepository
import io.github.dewfallapp.core.model.ChannelId
import io.github.dewfallapp.core.model.Download
import io.github.dewfallapp.core.model.DownloadStatus
import io.github.dewfallapp.core.model.PlaybackFailure
import io.github.dewfallapp.core.model.VideoDetails
import io.github.dewfallapp.core.model.VideoId
import io.github.dewfallapp.core.model.VideoSummary
import io.github.dewfallapp.core.model.WatchState
import io.github.dewfallapp.core.model.YouTubeResult
import io.github.dewfallapp.core.playback.PictureHold
import io.github.dewfallapp.core.playback.PlaybackConnection
import io.github.dewfallapp.core.playback.stopsPlayback
import kotlin.time.Duration
import kotlin.time.Instant
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow

internal val VIDEO = VideoId("aqz-KE-bpKQ")

internal val DETAILS =
    VideoDetails(
        id = VIDEO,
        title = "A title",
        channelId = ChannelId("UCLA_DiR1FfKNvjuUpBHmylQ"),
        channelName = "A channel",
        channelAvatars = emptyList(),
        description = "",
        duration = null,
        published = null,
        thumbnails = emptyList(),
        isLive = false,
    )

/** One call the fake repository got. It waits until the test completes its answer. */
internal class FakeCall(val videoId: VideoId) {
    val answer = CompletableDeferred<YouTubeResult<VideoDetails>>()
}

/** A repository whose calls wait for the test, so a test decides when each one finishes. */
internal class FakeRepository : VideoDetailsRepository {
    val calls = mutableListOf<FakeCall>()

    override suspend fun videoDetails(videoId: VideoId): YouTubeResult<VideoDetails> {
        val call = FakeCall(videoId).also { calls += it }
        return call.answer.await()
    }
}

internal val MARKED = Instant.parse("2026-11-22T08:00:00Z")

/**
 * A repository of watch states that records its marks and answers from [state]. A test can make
 * reads or writes throw.
 */
internal class FakeWatchStates : WatchStateRepository {
    val state = MutableStateFlow<WatchState?>(null)
    val marks = mutableListOf<String>()
    var failReads = false
    var failWrites = false

    /** While set, a new read answers only once the test completes it. */
    var holdReads: CompletableDeferred<Unit>? = null

    override fun watchState(videoId: VideoId): Flow<WatchState?> {
        val hold = holdReads
        return when {
            failReads -> flow { throw IllegalStateException("The database can't be read") }
            hold != null -> {
                flow {
                    hold.await()
                    emitAll(state)
                }
            }
            else -> state
        }
    }

    override suspend fun savePosition(videoId: VideoId, position: Duration) =
        error("The video screen never saves a position")

    override suspend fun markWatched(videoId: VideoId) {
        marks += "mark ${videoId.value}"
        check(!failWrites) { "The disk is full" }
        state.value =
            (state.value ?: WatchState(Duration.ZERO, null, MARKED)).copy(watchedAt = MARKED)
    }

    override suspend fun markUnwatched(videoId: VideoId) {
        marks += "unmark ${videoId.value}"
        check(!failWrites) { "The disk is full" }
        state.value = state.value?.copy(watchedAt = null)
    }
}

/**
 * A connection that records what the ViewModel asks of it. Its player's current item is the latest
 * prepared one, unless a test sets it, and stopping follows the real rule, stopsPlayback. It counts
 * the picture's holds.
 */
internal class FakeConnection : PlaybackConnection {
    val prepared = mutableListOf<Pair<VideoId, Long>>()
    val stopRequests = mutableListOf<String>()
    val releaseRequests = mutableListOf<String>()
    val descriptions = mutableListOf<Triple<String, String, String>>()
    val videos = mutableMapOf<String, VideoId>()
    var currentItemId: String? = null
    var playing = false
    var pictureHolds = 0

    override val player: StateFlow<Player?> = MutableStateFlow(null)
    override val failure: StateFlow<PlaybackFailure?> = MutableStateFlow(null)

    override fun prepare(videoId: VideoId, play: Boolean, startPositionMs: Long): String {
        prepared += videoId to startPositionMs
        val id = "playback ${prepared.size}"
        videos[id] = videoId
        currentItemId = id
        playing = play
        return id
    }

    override fun videoOf(playbackId: String): VideoId? = videos[playbackId]

    override fun stop(playbackId: String) {
        stopRequests += playbackId
        if (stopsPlayback(currentItemId, playbackId)) {
            playing = false
            currentItemId = null
        }
    }

    override fun release(playbackId: String) {
        releaseRequests += playbackId
    }

    override fun describe(playbackId: String, title: String, channelName: String) {
        descriptions += Triple(playbackId, title, channelName)
    }

    override fun holdPicture(): PictureHold {
        pictureHolds++
        var released = false
        return PictureHold {
            if (!released) {
                released = true
                pictureHolds--
            }
        }
    }

    override fun appStarted() = Unit

    override fun appStopped(changingConfigurations: Boolean) = Unit
}

/**
 * A downloads repository that answers from [status] and records its writes. A test can make reads
 * or writes throw.
 */
internal class FakeDownloads : DownloadsRepository {
    val status = MutableStateFlow<DownloadStatus>(DownloadStatus.NotDownloaded)
    val writes = mutableListOf<String>()
    val downloaded = mutableListOf<VideoSummary>()
    var failReads = false
    var failWrites = false

    override fun statuses() = error("The video screen reads one video's status")

    override fun status(videoId: VideoId): Flow<DownloadStatus> =
        if (failReads) {
            flow { throw IllegalStateException("The database can't be read") }
        } else {
            status
        }

    override fun bytesInUse(): Flow<Long> = error("The video screen shows no storage")

    override suspend fun download(video: VideoSummary) {
        writes += "download ${video.id.value}"
        check(!failWrites) { "The disk is full" }
        downloaded += video
    }

    override fun downloads(): Flow<List<Download>> =
        error("The video screen doesn't list downloads")

    override suspend fun remove(videoId: VideoId) =
        error("The video screen doesn't remove downloads")

    override suspend fun pause(videoId: VideoId) {
        writes += "pause ${videoId.value}"
        check(!failWrites) { "The disk is full" }
    }
}
