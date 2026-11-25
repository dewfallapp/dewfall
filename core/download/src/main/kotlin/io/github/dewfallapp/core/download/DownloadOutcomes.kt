package io.github.dewfallapp.core.download

import io.github.dewfallapp.core.data.download.DownloadFiles
import io.github.dewfallapp.core.data.download.DownloadQueue
import io.github.dewfallapp.core.data.download.QueuedDownload
import io.github.dewfallapp.core.logging.LogField
import io.github.dewfallapp.core.logging.Logger
import io.github.dewfallapp.core.model.DownloadFailure
import io.github.dewfallapp.core.model.YouTubeError
import java.io.IOException
import javax.inject.Inject

/** The tries in a row that YouTube may block or break a download before it fails. */
internal const val YOUTUBE_TRIES = 3

/**
 * What happens to a download that can't go on now, as the architecture's failure table says: it
 * waits for the network, counts one more try that YouTube blocked or broke, or fails, deleting its
 * files only when nothing can be kept. Each returns what the run does next.
 */
internal class DownloadOutcomes
@Inject
constructor(
    private val queue: DownloadQueue,
    private val files: DownloadFiles,
    logger: Logger,
) {
    private val log = logger.withTag("Downloads")

    /** A failure of the links call. */
    suspend fun youTubeFailure(item: QueuedDownload, error: YouTubeError): Step =
        when (error) {
            YouTubeError.NETWORK -> waitForNetwork(item, error = null)
            YouTubeError.BLOCKED,
            YouTubeError.BROKEN -> youTubeTrouble(item)
            else -> failed(item, DownloadFailure.UNAVAILABLE)
        }

    /** A lost connection: the download waits, and the job runs again once the network is back. */
    suspend fun waitForNetwork(item: QueuedDownload, error: IOException?): Step {
        log.warn("Download waits for the network", error = error)
        queue.backToWaiting(item.videoId, item.youtubeTries)
        return Step.RETRY
    }

    /** One more try that YouTube blocked or broke: the job runs again, or after three, fails. */
    suspend fun youTubeTrouble(item: QueuedDownload): Step {
        val tries = item.youtubeTries + 1
        return if (tries >= YOUTUBE_TRIES) {
            failed(item, DownloadFailure.YOUTUBE, keepBytes = true)
        } else {
            queue.backToWaiting(item.videoId, tries)
            Step.RETRY
        }
    }

    /**
     * Fails the download for [reason], deleting its files unless [keepBytes]. The row is written
     * first, and the files go only when it changed, so a pause that came in between keeps its row
     * and its files together.
     */
    suspend fun failed(
        item: QueuedDownload,
        reason: DownloadFailure,
        keepBytes: Boolean = false,
    ): Step {
        if (queue.fail(item.videoId, reason, keepBytes) && !keepBytes) files.delete(item.folder)
        log.warn("Download failed", LogField.state("reason", reason))
        return Step.NEXT
    }
}
