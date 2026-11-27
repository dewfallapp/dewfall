package io.github.dewfallapp.core.data.download

import io.github.dewfallapp.core.model.DownloadFailure
import io.github.dewfallapp.core.model.VideoId
import kotlinx.coroutines.flow.Flow

/** Where a download stands in the queue, as the download job reads it. */
enum class QueueState {
    WAITING,
    DOWNLOADING,
    PAUSED,
    FAILED,
    DOWNLOADED,
}

/**
 * One chosen format of a download: YouTube's [itag], its [mimeType], its size in [bytes], or null
 * if YouTube didn't say, and for audio, its sound [track]'s ID, or null when the video has one
 * track. Audio formats of different tracks share an itag, so the itag and the track find a format
 * again. The track's ID is a YouTube ID, which is user data, so never log it.
 */
data class ChosenFormat(
    val itag: Int,
    val mimeType: String,
    val bytes: Long?,
    val track: String? = null,
)

/** The formats a download keeps: a [video] format, and an [audio] one unless the video has both. */
data class ChosenFormats(val video: ChosenFormat, val audio: ChosenFormat?)

/**
 * A download that the job took from the queue. [folder] is its folder's name, [chosen] its formats
 * once chosen, [downloadedBytes] the bytes on the phone, and [youtubeTries] the tries in a row that
 * YouTube blocked or broke. The ID and folder are user data, so never log them.
 */
data class QueuedDownload(
    val videoId: VideoId,
    val folder: String,
    val chosen: ChosenFormats?,
    val downloadedBytes: Long,
    val youtubeTries: Int,
)

/**
 * The download queue as the download job sees it, over the `downloads` table. Only `:core:download`
 * uses it. Every write names the states it may change, so a write that comes late changes nothing
 * after a pause or a stop, and the one-row writes return whether they changed the row. The reset
 * and the pause change every row in their states.
 * Writes finish even when the caller is cancelled. See docs/architecture.md#downloads-and-cleanup.
 */
interface DownloadQueue {
    /** The states of the downloads that wait or run, WAITING and DOWNLOADING. */
    suspend fun queuedStates(): List<QueueState>

    /** Turns every download marked as running back to waiting. */
    suspend fun resetDownloading()

    /** Pauses every download that waits or runs, after the user stopped the app. */
    suspend fun pauseQueued()

    /** The first waiting download, now marked as running, or null when none waits. */
    suspend fun claimNext(): QueuedDownload?

    /** The state of the video's download, or null when it has none. */
    fun state(videoId: VideoId): Flow<QueueState?>

    /** The folder of the video's download, or null when it has none. */
    fun folderOf(videoId: VideoId): Flow<String?>

    /** The folder of every download. */
    suspend fun folders(): Set<String>

    /** Whether a download names [folder]. */
    suspend fun isFolderOwned(folder: String): Boolean

    suspend fun setFormats(videoId: VideoId, chosen: ChosenFormats): Boolean

    /** Forgets the chosen formats and bytes, before the files start again. */
    suspend fun clearFormats(videoId: VideoId): Boolean

    suspend fun setProgress(videoId: VideoId, downloadedBytes: Long): Boolean

    /** Puts a running download back in the queue, with [youtubeTries] failed tries in a row. */
    suspend fun backToWaiting(videoId: VideoId, youtubeTries: Int): Boolean

    /**
     * Fails a running download for [reason]. Without [keepBytes], its formats and bytes are
     * forgotten, because its files are deleted.
     */
    suspend fun fail(videoId: VideoId, reason: DownloadFailure, keepBytes: Boolean): Boolean

    /** Marks a running download done, with [bytes] on the phone. */
    suspend fun finish(videoId: VideoId, bytes: Long): Boolean
}

/** Whether any download waits or runs. */
suspend fun DownloadQueue.hasQueued(): Boolean = queuedStates().isNotEmpty()

/** Whether any download is marked as running, which only an ended process can leave. */
suspend fun DownloadQueue.hasDownloading(): Boolean = QueueState.DOWNLOADING in queuedStates()
