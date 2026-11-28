package io.github.dewfallapp.core.data.download

import io.github.dewfallapp.core.model.Download
import io.github.dewfallapp.core.model.DownloadStatus
import io.github.dewfallapp.core.model.VideoId
import io.github.dewfallapp.core.model.VideoSummary
import kotlinx.coroutines.flow.Flow

/**
 * Starts, pauses, removes and follows downloads, for the screens. The downloads themselves run in
 * the background job of `:core:download`, which no screen owns, so leaving a screen never stops
 * one. See openspec/specs/downloads/spec.md and docs/architecture.md#downloads-and-cleanup.
 *
 * Main-safe. The Flows come from the database, so they emit again after every change and never
 * touch the network. Writes finish even when the caller is cancelled. The IDs and titles are user
 * data, so never log them.
 */
interface DownloadsRepository {
    /** Every video's download status. A video that isn't in the map is NotDownloaded. */
    fun statuses(): Flow<Map<VideoId, DownloadStatus>>

    /** The download status of the video with [videoId]. */
    fun status(videoId: VideoId): Flow<DownloadStatus>

    /**
     * The bytes that downloads keep on the phone: the files of finished downloads, and the parts
     * that waiting, running, paused and failed downloads keep. While a download runs, it follows
     * the download's progress, which the download job writes about once a second.
     */
    fun bytesInUse(): Flow<Long>

    /**
     * Queues [video] for download, keeping its summary with the download, and starts a run. A
     * paused or failed download of it goes on from the bytes it has, at the end of the queue. A
     * waiting, running or finished one stays as it is.
     */
    suspend fun download(video: VideoSummary)

    /** Pauses the video's download if it waits or runs, keeping its bytes. */
    suspend fun pause(videoId: VideoId)

    /**
     * The summary saved with the video's download when it was asked for, whatever the download's
     * state, or null while the video has no download. It emits again when the download is asked for
     * or removed.
     */
    fun savedVideo(videoId: VideoId): Flow<VideoSummary?>

    /** Every download, in the queue's order, with the summary saved when it was asked for. */
    fun downloads(): Flow<List<Download>>

    /**
     * Removes the video's download, whatever its state, and deletes its files: a cancel of an
     * unfinished one, or a remove of a finished one. A running download stops within one buffer,
     * and the download job deletes its files once its writer stopped. Removing a video with no
     * download does nothing.
     */
    suspend fun remove(videoId: VideoId)
}
