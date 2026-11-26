package io.github.dewfallapp.core.data.download

import io.github.dewfallapp.core.model.DownloadStatus
import io.github.dewfallapp.core.model.VideoId
import io.github.dewfallapp.core.model.VideoSummary
import kotlinx.coroutines.flow.Flow

/**
 * Starts, pauses and follows downloads, for the screens. The downloads themselves run in the
 * background job of `:core:download`, which no screen owns, so leaving a screen never stops one.
 * See openspec/specs/downloads/spec.md and docs/architecture.md#downloads-and-cleanup.
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
     * Queues [video] for download, keeping its summary with the download, and starts a run. A
     * paused or failed download of it goes on from the bytes it has, at the end of the queue. A
     * waiting, running or finished one stays as it is.
     */
    suspend fun download(video: VideoSummary)

    /** Pauses the video's download if it waits or runs, keeping its bytes. */
    suspend fun pause(videoId: VideoId)
}
