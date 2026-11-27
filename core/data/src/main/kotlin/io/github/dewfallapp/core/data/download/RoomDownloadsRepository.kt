package io.github.dewfallapp.core.data.download

import io.github.dewfallapp.core.data.database.ApplicationScope
import io.github.dewfallapp.core.database.DownloadDao
import io.github.dewfallapp.core.database.DownloadEntity
import io.github.dewfallapp.core.database.DownloadState
import io.github.dewfallapp.core.model.ChannelId
import io.github.dewfallapp.core.model.Download
import io.github.dewfallapp.core.model.DownloadFailure
import io.github.dewfallapp.core.model.DownloadProgress
import io.github.dewfallapp.core.model.DownloadStatus
import io.github.dewfallapp.core.model.PublishedTime
import io.github.dewfallapp.core.model.VideoId
import io.github.dewfallapp.core.model.VideoSummary
import java.util.UUID
import javax.inject.Inject
import kotlin.time.Clock
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Instant
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext

/** A new download folder's name: a random UUID, so no path holds the video's ID. */
internal fun interface FolderNames {
    fun next(): String
}

/**
 * Keeps downloads in the database's `downloads` table, starts runs of the queue, and deletes the
 * files of a removed download that no writer uses.
 */
internal class RoomDownloadsRepository
@Inject
constructor(
    private val downloadDao: DownloadDao,
    private val scheduler: DownloadScheduler,
    private val clock: Clock,
    private val folderNames: FolderNames,
    private val files: DownloadFiles,
    @ApplicationScope private val externalScope: CoroutineScope,
    @DownloadFilesDispatcher private val filesDispatcher: CoroutineDispatcher,
) : DownloadsRepository {
    override fun statuses(): Flow<Map<VideoId, DownloadStatus>> =
        downloadDao
            .downloads()
            .map { rows -> rows.associate { VideoId(it.videoId) to it.toStatus() } }
            .distinctUntilChanged()

    override fun status(videoId: VideoId): Flow<DownloadStatus> =
        downloadDao
            .download(videoId.value)
            .map { it?.toStatus() ?: DownloadStatus.NotDownloaded }
            .distinctUntilChanged()

    // Writes that leave the sum as it was, such as a pause, would wake the screen for nothing.
    override fun bytesInUse(): Flow<Long> = downloadDao.bytesInUse().distinctUntilChanged()

    override suspend fun download(video: VideoSummary) {
        val row = waitingRow(video, folderNames.next(), clock.now())
        externalScope
            .async {
                downloadDao.request(row)
                // Also when the video already waited: a run that Android refused to schedule
                // starts now, and a run that is active ignores the call.
                scheduler.start()
            }
            .await()
    }

    override suspend fun pause(videoId: VideoId) {
        val now = clock.now()
        externalScope.async { downloadDao.pause(videoId.value, now) }.await()
    }

    override fun downloads(): Flow<List<Download>> =
        downloadDao.downloads().map { rows -> rows.map { it.toDownload() } }.distinctUntilChanged()

    override suspend fun remove(videoId: VideoId) {
        externalScope
            .async {
                val removed = downloadDao.remove(videoId.value) ?: return@async
                // A running download's writer may still write a buffer, and would make the folder
                // again, so the job deletes it once the writer stopped. Any other has no writer.
                if (removed.state != DownloadState.DOWNLOADING) {
                    withContext(filesDispatcher) { files.delete(removed.folder) }
                }
            }
            .await()
    }
}

/** A new waiting row for [video], with the download's [folder], asked for [now]. */
private fun waitingRow(video: VideoSummary, folder: String, now: Instant) =
    DownloadEntity(
        videoId = video.id.value,
        folder = folder,
        title = video.title,
        channelId = video.channelId.value,
        channelName = video.channelName,
        durationMs = video.duration?.inWholeMilliseconds,
        publishedAt = video.published?.instant,
        publishedApproximate = video.published?.isApproximate ?: false,
        kind = video.kind,
        thumbnails = video.thumbnails,
        state = DownloadState.WAITING,
        failure = null,
        requestedAt = now,
        videoItag = null,
        videoMime = null,
        videoBytes = null,
        audioItag = null,
        audioMime = null,
        audioBytes = null,
        audioTrack = null,
        downloadedBytes = 0,
        youtubeTries = 0,
        updatedAt = now,
    )

/** What the screens show for a row. */
internal fun DownloadEntity.toStatus(): DownloadStatus {
    val progress = DownloadProgress(downloadedBytes, totalBytes())
    return when (state) {
        DownloadState.WAITING -> DownloadStatus.Waiting(progress)
        DownloadState.DOWNLOADING -> DownloadStatus.Downloading(progress)
        DownloadState.PAUSED -> DownloadStatus.Paused(progress)
        // A FAILED row always has its failure. YOUTUBE is the reason that changes the least.
        DownloadState.FAILED -> DownloadStatus.Failed(failure ?: DownloadFailure.YOUTUBE)
        DownloadState.DOWNLOADED -> DownloadStatus.Downloaded(downloadedBytes)
    }
}

/** The download as the Downloads screen lists it. */
internal fun DownloadEntity.toDownload(): Download {
    val status = toStatus()
    return Download(
        video =
            VideoSummary(
                id = VideoId(videoId),
                title = title,
                channelId = ChannelId(channelId),
                channelName = channelName,
                duration = durationMs?.milliseconds,
                published =
                    publishedAt?.let { PublishedTime(it, isApproximate = publishedApproximate) },
                thumbnails = thumbnails,
                kind = kind,
            ),
        status = status,
        requestedAt = requestedAt,
        // No write changes a DOWNLOADED row after finish, so its updated_at is when it finished.
        // See DownloadDao's KDoc.
        finishedAt = if (status is DownloadStatus.Downloaded) updatedAt else null,
    )
}

/**
 * The size of the download's files, or null while the formats aren't chosen or a chosen file's size
 * is unknown.
 */
private fun DownloadEntity.totalBytes(): Long? {
    val video = videoBytes
    val audio = audioBytes
    return when {
        videoItag == null || video == null -> null
        audioItag == null -> video
        audio == null -> null
        else -> video + audio
    }
}

/** Random UUIDs, as [FolderNames] asks. */
internal class RandomFolderNames @Inject constructor() : FolderNames {
    override fun next(): String = UUID.randomUUID().toString()
}
