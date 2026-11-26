package io.github.dewfallapp.core.data.download

import io.github.dewfallapp.core.database.DownloadDao
import io.github.dewfallapp.core.database.DownloadEntity
import io.github.dewfallapp.core.database.DownloadState
import io.github.dewfallapp.core.model.DownloadFailure
import io.github.dewfallapp.core.model.VideoKind
import kotlin.time.Instant
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf

/** A made-up row of the video [videoId], in [state]. */
internal fun row(
    videoId: String = "aaaaaaaaaaa",
    state: DownloadState = DownloadState.WAITING,
    failure: DownloadFailure? = null,
    videoItag: Int? = null,
    videoBytes: Long? = null,
    audioItag: Int? = null,
    audioBytes: Long? = null,
    downloadedBytes: Long = 0,
) =
    DownloadEntity(
        videoId = videoId,
        folder = "folder-1",
        title = "A video",
        channelId = "UCaaaaaaaaaaaaaaaaaaaaaa",
        channelName = "Kettle and Crumb",
        durationMs = 600_000,
        publishedAt = null,
        publishedApproximate = false,
        kind = VideoKind.VIDEO,
        thumbnails = emptyList(),
        state = state,
        failure = failure,
        requestedAt = Instant.parse("2026-11-26T09:00:00Z"),
        videoItag = videoItag,
        videoMime = videoItag?.let { "video/mp4" },
        videoBytes = videoBytes,
        audioItag = audioItag,
        audioMime = audioItag?.let { "audio/webm" },
        audioBytes = audioBytes,
        audioTrack = null,
        downloadedBytes = downloadedBytes,
        youtubeTries = 0,
        updatedAt = Instant.parse("2026-11-26T09:00:00Z"),
    )

/**
 * Records the repository's calls as text, answers its reads with [rows], and can hold a write until
 * the test lets it go. The engine's calls aren't used here.
 */
internal class FakeDownloadDao(
    private val rows: List<DownloadEntity> = emptyList(),
    private val requested: Boolean = true,
    private val gate: CompletableDeferred<Unit>? = null,
) : DownloadDao() {
    val calls = mutableListOf<String>()
    var requestedRow: DownloadEntity? = null

    override fun download(videoId: String): Flow<DownloadEntity?> =
        flowOf(rows.firstOrNull { it.videoId == videoId })

    override fun downloads(): Flow<List<DownloadEntity>> = flowOf(rows)

    override suspend fun request(download: DownloadEntity): Boolean {
        gate?.await()
        requestedRow = download
        calls += "request ${download.videoId}"
        return requested
    }

    override suspend fun pause(videoId: String, now: Instant): Int {
        gate?.await()
        calls += "pause $videoId $now"
        return 1
    }

    override suspend fun row(videoId: String) = error("Not used")

    override suspend fun insertIfAbsent(download: DownloadEntity) = error("Not used")

    override suspend fun requeue(videoId: String, now: Instant) = error("Not used")

    override suspend fun pauseQueued(now: Instant) = error("Not used")
}
