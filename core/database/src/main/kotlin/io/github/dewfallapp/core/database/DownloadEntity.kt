package io.github.dewfallapp.core.database

import androidx.room3.ColumnInfo
import androidx.room3.Entity
import androidx.room3.Index
import androidx.room3.PrimaryKey
import io.github.dewfallapp.core.model.DownloadFailure
import io.github.dewfallapp.core.model.Thumbnail
import io.github.dewfallapp.core.model.VideoKind
import kotlin.time.Instant

/** Where a download stands in the queue. Room stores its name. */
enum class DownloadState {
    /** Waits for its turn or for a connection. */
    WAITING,

    /** A run downloads it now, or did when the app's process ended. */
    DOWNLOADING,

    /** The user paused it, or stopped the app while it ran. */
    PAUSED,

    /** It stopped for the row's failure, and waits for the user to try again. */
    FAILED,

    /** Every file is on the phone. */
    DOWNLOADED,
}

/**
 * One video's download: the queue's entry and its record. It keeps the whole VideoSummary that the
 * download was asked for with, so later screens can show it without asking YouTube, and it never
 * touches `videos`. The chosen formats stay once chosen, so new links after an expiry continue the
 * same files. The ID, title, names, thumbnail links and folder are user data, and so is toString,
 * so never log a row. See ADR 0007 and docs/architecture.md#downloads-and-cleanup.
 */
@Entity(tableName = "downloads", indices = [Index(value = ["state", "requested_at"])])
data class DownloadEntity(
    @PrimaryKey @ColumnInfo(name = "video_id") val videoId: String,
    /** The name of the download's folder: a random UUID, so no path holds the video's ID. */
    val folder: String,
    val title: String,
    @ColumnInfo(name = "channel_id") val channelId: String,
    @ColumnInfo(name = "channel_name") val channelName: String,
    /** The length in milliseconds, or null when YouTube gave none. */
    @ColumnInfo(name = "duration_ms") val durationMs: Long?,
    @ColumnInfo(name = "published_at") val publishedAt: Instant?,
    /** Whether [publishedAt] is only an estimate. False when [publishedAt] is null. */
    @ColumnInfo(name = "published_approximate") val publishedApproximate: Boolean,
    val kind: VideoKind,
    val thumbnails: List<Thumbnail>,
    val state: DownloadState,
    /** Why it failed, while [state] is FAILED, or else null. */
    val failure: DownloadFailure?,
    /** When the user asked for it, or last asked it to go on. The queue's order. */
    @ColumnInfo(name = "requested_at") val requestedAt: Instant,
    /** YouTube's number for the chosen video format, or null until the formats are chosen. */
    @ColumnInfo(name = "video_itag") val videoItag: Int?,
    @ColumnInfo(name = "video_mime") val videoMime: String?,
    /** The video file's size in bytes, or null if YouTube didn't say. */
    @ColumnInfo(name = "video_bytes") val videoBytes: Long?,
    /** The chosen audio format, or null until chosen, or for a format with both. */
    @ColumnInfo(name = "audio_itag") val audioItag: Int?,
    @ColumnInfo(name = "audio_mime") val audioMime: String?,
    /**
     * YouTube's ID of the chosen audio format's sound track, or null when the video has one track.
     * Formats of different tracks share an itag, so the itag alone can't find it again.
     */
    @ColumnInfo(name = "audio_track") val audioTrack: String?,
    @ColumnInfo(name = "audio_bytes") val audioBytes: Long?,
    /** The bytes on the phone so far, across its files. */
    @ColumnInfo(name = "downloaded_bytes") val downloadedBytes: Long,
    /** Tries that failed with YouTube blocking or breaking, in a row. */
    @ColumnInfo(name = "youtube_tries") val youtubeTries: Int,
    /** The time of the last change. */
    @ColumnInfo(name = "updated_at") val updatedAt: Instant,
)
