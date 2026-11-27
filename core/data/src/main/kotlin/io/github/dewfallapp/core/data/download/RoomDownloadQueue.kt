package io.github.dewfallapp.core.data.download

import io.github.dewfallapp.core.data.database.ApplicationScope
import io.github.dewfallapp.core.database.DownloadDao
import io.github.dewfallapp.core.database.DownloadEntity
import io.github.dewfallapp.core.database.DownloadQueueDao
import io.github.dewfallapp.core.database.DownloadState
import io.github.dewfallapp.core.model.DownloadFailure
import io.github.dewfallapp.core.model.VideoId
import javax.inject.Inject
import kotlin.time.Clock
import kotlin.time.Instant
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map

/**
 * The download queue over the `downloads` table, for the download job. Its writes run in the
 * app-wide scope, so a write that started finishes when the job is cancelled.
 */
internal class RoomDownloadQueue
@Inject
constructor(
    private val queueDao: DownloadQueueDao,
    private val downloadDao: DownloadDao,
    private val clock: Clock,
    @ApplicationScope private val externalScope: CoroutineScope,
) : DownloadQueue {
    override suspend fun queuedStates(): List<QueueState> =
        queueDao.queuedStates().map { it.toQueueState() }

    override suspend fun resetDownloading() {
        externalScope.write(clock) { queueDao.resetDownloading(it) }
    }

    override suspend fun pauseQueued() {
        externalScope.write(clock) { downloadDao.pauseQueued(it) }
    }

    override suspend fun claimNext(): QueuedDownload? =
        externalScope.async { queueDao.claimNext(clock.now()) }.await()?.toQueued()

    override fun state(videoId: VideoId): Flow<QueueState?> =
        downloadDao.download(videoId.value).map { it?.state?.toQueueState() }.distinctUntilChanged()

    override fun folderOf(videoId: VideoId): Flow<String?> =
        queueDao.folderOf(videoId.value).distinctUntilChanged()

    override suspend fun folders(): Set<String> = queueDao.folders().toSet()

    override suspend fun isFolderOwned(folder: String): Boolean = queueDao.isFolderOwned(folder)

    override suspend fun setFormats(videoId: VideoId, chosen: ChosenFormats): Boolean =
        externalScope.write(clock) {
            queueDao.setFormats(
                videoId = videoId.value,
                videoItag = chosen.video.itag,
                videoMime = chosen.video.mimeType,
                videoBytes = chosen.video.bytes,
                audioItag = chosen.audio?.itag,
                audioMime = chosen.audio?.mimeType,
                audioBytes = chosen.audio?.bytes,
                audioTrack = chosen.audio?.track,
                now = it,
            )
        }

    override suspend fun clearFormats(videoId: VideoId): Boolean =
        externalScope.write(clock) { queueDao.clearFormats(videoId.value, it) }

    override suspend fun setProgress(videoId: VideoId, downloadedBytes: Long): Boolean =
        externalScope.write(clock) { queueDao.setProgress(videoId.value, downloadedBytes, it) }

    override suspend fun backToWaiting(videoId: VideoId, youtubeTries: Int): Boolean =
        externalScope.write(clock) { queueDao.backToWaiting(videoId.value, youtubeTries, it) }

    override suspend fun fail(
        videoId: VideoId,
        reason: DownloadFailure,
        keepBytes: Boolean,
    ): Boolean = externalScope.write(clock) { queueDao.fail(videoId.value, reason, keepBytes, it) }

    override suspend fun finish(videoId: VideoId, bytes: Long): Boolean =
        externalScope.write(clock) { queueDao.finish(videoId.value, bytes, it) }
}

/**
 * Runs [update] with [clock]'s time in this scope, the app-wide one, so it finishes when its caller
 * is cancelled, and says whether it changed a row.
 */
private suspend fun CoroutineScope.write(
    clock: Clock,
    update: suspend (Instant) -> Int,
): Boolean {
    val now = clock.now()
    return async { update(now) }.await() > 0
}

private fun DownloadEntity.toQueued(): QueuedDownload {
    val videoFormat = chosenFormat(videoItag, videoMime, videoBytes)
    val audioFormat = chosenFormat(audioItag, audioMime, audioBytes)?.copy(track = audioTrack)
    return QueuedDownload(
        videoId = VideoId(videoId),
        folder = folder,
        chosen = videoFormat?.let { ChosenFormats(it, audioFormat) },
        downloadedBytes = downloadedBytes,
        youtubeTries = youtubeTries,
    )
}

/** A chosen format, or null when its columns are empty. */
private fun chosenFormat(itag: Int?, mimeType: String?, bytes: Long?): ChosenFormat? =
    if (itag != null && mimeType != null) ChosenFormat(itag, mimeType, bytes) else null

private fun DownloadState.toQueueState(): QueueState =
    when (this) {
        DownloadState.WAITING -> QueueState.WAITING
        DownloadState.DOWNLOADING -> QueueState.DOWNLOADING
        DownloadState.PAUSED -> QueueState.PAUSED
        DownloadState.FAILED -> QueueState.FAILED
        DownloadState.DOWNLOADED -> QueueState.DOWNLOADED
    }
