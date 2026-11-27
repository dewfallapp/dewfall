package io.github.dewfallapp.core.download

import io.github.dewfallapp.core.data.download.ChosenFormats
import io.github.dewfallapp.core.data.download.DownloadFolders
import io.github.dewfallapp.core.data.download.DownloadQueue
import io.github.dewfallapp.core.data.download.QueueState
import io.github.dewfallapp.core.data.download.QueuedDownload
import io.github.dewfallapp.core.model.DownloadFailure
import io.github.dewfallapp.core.model.VideoId
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map

/** One row of the fake queue. */
internal data class FakeRow(
    val videoId: VideoId,
    val folder: String,
    val state: QueueState = QueueState.WAITING,
    val chosen: ChosenFormats? = null,
    val downloadedBytes: Long = 0,
    val youtubeTries: Int = 0,
    val failure: DownloadFailure? = null,
)

/**
 * The download queue in memory, with the table's rules: the queue's order is the order of [add],
 * and every write but the reset and the pause changes only a DOWNLOADING row. It counts the
 * progress writes.
 */
internal class FakeQueue : DownloadQueue, DownloadFolders {
    private val rows = MutableStateFlow<List<FakeRow>>(emptyList())
    var progressWrites = 0

    /** How many times every queued download was paused. */
    var pauses = 0

    /** Runs at each pause of every queued download. */
    var onPause: (() -> Unit)? = null

    /** Makes the next formats' write throw, as a database that throws does. */
    var failNextFormats = false

    /** Makes the pause of every queued download throw, as a database that throws does. */
    var failPauseQueued = false

    /**
     * Runs after a claim's write, before the claim returns, as the moment when the Room queue's
     * write is done and its caller still awaits it. A caller that a stop cancelled then gets the
     * cancellation, as the Room queue's await throws it.
     */
    var afterClaim: (suspend () -> Unit)? = null

    /** Runs at the start of [finish], before its write, as a remove that comes just then. */
    var beforeFinish: (() -> Unit)? = null

    /** Like [afterClaim], after the reset of the rows left DOWNLOADING. */
    var afterReset: (suspend () -> Unit)? = null

    fun add(row: FakeRow) {
        rows.value += row
    }

    fun row(videoId: VideoId): FakeRow = rows.value.single { it.videoId == videoId }

    /** Removes the video's download, as the user's cancel or remove does. */
    fun remove(videoId: VideoId) {
        rows.value = rows.value.filterNot { it.videoId == videoId }
    }

    /**
     * Removes the video's download and asks for it again with [folder], in one change, as Room may
     * report the two.
     */
    fun replace(videoId: VideoId, folder: String) {
        rows.value = rows.value.filterNot { it.videoId == videoId } + FakeRow(videoId, folder)
    }

    /** Pauses the video's download, as the user's tap does. */
    fun pause(videoId: VideoId) {
        update(videoId, QueueState.WAITING, QueueState.DOWNLOADING) {
            it.copy(state = QueueState.PAUSED)
        }
    }

    private fun update(videoId: VideoId, vararg from: QueueState, change: (FakeRow) -> FakeRow) =
        rows.value
            .any { it.videoId == videoId && it.state in from }
            .also { changed ->
                if (changed) {
                    rows.value =
                        rows.value.map {
                            if (it.videoId == videoId && it.state in from) change(it) else it
                        }
                }
            }

    override suspend fun queuedStates() =
        rows.value
            .map { it.state }
            .filter { it == QueueState.WAITING || it == QueueState.DOWNLOADING }

    override suspend fun resetDownloading() {
        rows.value =
            rows.value.map {
                if (it.state == QueueState.DOWNLOADING) it.copy(state = QueueState.WAITING) else it
            }
        afterReset?.let {
            it()
            currentCoroutineContext().ensureActive()
        }
    }

    override suspend fun pauseQueued() {
        if (failPauseQueued) error("The database failed")
        pauses++
        onPause?.invoke()
        rows.value =
            rows.value.map {
                if (it.state == QueueState.WAITING || it.state == QueueState.DOWNLOADING) {
                    it.copy(state = QueueState.PAUSED)
                } else {
                    it
                }
            }
    }

    override suspend fun claimNext(): QueuedDownload? {
        val next = rows.value.firstOrNull { it.state == QueueState.WAITING } ?: return null
        update(next.videoId, QueueState.WAITING) { it.copy(state = QueueState.DOWNLOADING) }
        afterClaim?.let {
            it()
            currentCoroutineContext().ensureActive()
        }
        return QueuedDownload(
            next.videoId,
            next.folder,
            next.chosen,
            next.downloadedBytes,
            next.youtubeTries,
        )
    }

    override fun state(videoId: VideoId): Flow<QueueState?> = rows.map { all ->
        all.firstOrNull { it.videoId == videoId }?.state
    }

    override fun folderOf(videoId: VideoId): Flow<String?> = rows.map { all ->
        all.firstOrNull { it.videoId == videoId }?.folder
    }

    override suspend fun folders(): Set<String> = rows.value.map { it.folder }.toSet()

    override suspend fun isFolderOwned(folder: String): Boolean =
        rows.value.any { it.folder == folder }

    override suspend fun setFormats(videoId: VideoId, chosen: ChosenFormats): Boolean {
        if (failNextFormats) {
            failNextFormats = false
            error("The database failed")
        }
        return update(videoId, QueueState.DOWNLOADING) { it.copy(chosen = chosen) }
    }

    override suspend fun clearFormats(videoId: VideoId) =
        update(videoId, QueueState.DOWNLOADING) { it.copy(chosen = null, downloadedBytes = 0) }

    override suspend fun setProgress(videoId: VideoId, downloadedBytes: Long): Boolean {
        progressWrites++
        return update(videoId, QueueState.DOWNLOADING) {
            it.copy(downloadedBytes = downloadedBytes)
        }
    }

    override suspend fun backToWaiting(videoId: VideoId, youtubeTries: Int) =
        update(videoId, QueueState.DOWNLOADING) {
            it.copy(state = QueueState.WAITING, youtubeTries = youtubeTries)
        }

    override suspend fun fail(videoId: VideoId, reason: DownloadFailure, keepBytes: Boolean) =
        update(videoId, QueueState.DOWNLOADING) {
            val failed = it.copy(state = QueueState.FAILED, failure = reason)
            if (keepBytes) failed else failed.copy(chosen = null, downloadedBytes = 0)
        }

    override suspend fun finish(videoId: VideoId, bytes: Long): Boolean {
        beforeFinish?.invoke()
        return update(videoId, QueueState.DOWNLOADING) {
            it.copy(state = QueueState.DOWNLOADED, downloadedBytes = bytes, youtubeTries = 0)
        }
    }
}
