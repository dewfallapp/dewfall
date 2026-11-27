package io.github.dewfallapp.core.database

import androidx.room3.Dao
import androidx.room3.Insert
import androidx.room3.OnConflictStrategy
import androidx.room3.Query
import androidx.room3.Transaction
import io.github.dewfallapp.core.model.DownloadFailure
import kotlin.time.Instant
import kotlinx.coroutines.flow.Flow

/**
 * The `downloads` table as the screens and the repository use it: the downloads, a request, a pause
 * and a remove. Every function is main-safe: Room runs the queries on its own context. The download
 * job uses [DownloadQueueDao]. See docs/architecture.md#downloads-and-cleanup.
 *
 * No write may change a DOWNLOADED row, because its `updated_at` is when it finished, which the
 * Downloads screen orders by: [pause] and [requeue] name the states they change. A later write that
 * must change a DOWNLOADED row adds a `finished_at` column first.
 */
@Dao
abstract class DownloadDao {
    /** The download of the video with [videoId], or null if it has none. */
    @Query("SELECT * FROM downloads WHERE video_id = :videoId")
    abstract fun download(videoId: String): Flow<DownloadEntity?>

    /** Every download, in the queue's order. */
    @Query("SELECT * FROM downloads ORDER BY requested_at, rowid")
    abstract fun downloads(): Flow<List<DownloadEntity>>

    /**
     * The bytes that every download keeps on the phone, 0 with none: the complete files of finished
     * downloads, and the parts that the others keep, as `downloaded_bytes` records them.
     */
    @Query("SELECT COALESCE(SUM(downloaded_bytes), 0) FROM downloads")
    abstract fun bytesInUse(): Flow<Long>

    @Query("SELECT * FROM downloads WHERE video_id = :videoId")
    abstract suspend fun row(videoId: String): DownloadEntity?

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    abstract suspend fun insertIfAbsent(download: DownloadEntity): Long

    @Query(
        "UPDATE downloads SET state = 'WAITING', failure = NULL, youtube_tries = 0, " +
            "requested_at = :now, updated_at = :now " +
            "WHERE video_id = :videoId AND state IN ('PAUSED', 'FAILED')",
    )
    abstract suspend fun requeue(videoId: String, now: Instant): Int

    /**
     * Queues [download], whose state is WAITING, unless the video has a download already. A paused
     * or failed one goes back to the end of the queue, with its files and formats. A waiting,
     * running or finished one stays as it is. Returns whether the video now waits because of this
     * call. The repository starts a run either way, so a queue that Android refused to schedule
     * starts too.
     */
    @Transaction
    open suspend fun request(download: DownloadEntity): Boolean =
        insertIfAbsent(download) != -1L || requeue(download.videoId, download.requestedAt) > 0

    /** Pauses the video's download if it waits or runs. */
    @Query(
        "UPDATE downloads SET state = 'PAUSED', updated_at = :now " +
            "WHERE video_id = :videoId AND state IN ('WAITING', 'DOWNLOADING')",
    )
    abstract suspend fun pause(videoId: String, now: Instant): Int

    /**
     * Deletes the video's row. Only [remove] calls it, which returns the row, so its caller can
     * delete the folder; a row deleted any other way leaves its folder until the next run's sweep.
     */
    @Query("DELETE FROM downloads WHERE video_id = :videoId")
    abstract suspend fun delete(videoId: String): Int

    /**
     * Removes the video's download, whatever its state, and returns its row as it was, or null when
     * it had none. One transaction, so the job can't claim the row between the read and the delete.
     * The caller deletes the files, or leaves them to the job when the row was DOWNLOADING.
     */
    @Transaction
    open suspend fun remove(videoId: String): DownloadEntity? =
        row(videoId)?.also { delete(it.videoId) }

    /** Pauses every download that waits or runs, after the user stopped the app. */
    @Query(
        "UPDATE downloads SET state = 'PAUSED', updated_at = :now " +
            "WHERE state IN ('WAITING', 'DOWNLOADING')",
    )
    abstract suspend fun pauseQueued(now: Instant): Int
}

/**
 * The `downloads` table as the download job runs it. Every function is main-safe. Every write names
 * the state it expects: the reset and every write after the claim `WHERE state = 'DOWNLOADING'`,
 * and the claim `WHERE state = 'WAITING'`, so a late write never undoes a pause or a stop, and none
 * changes a DOWNLOADED row, whose `updated_at` is when it finished. Each returns the number of rows
 * it changed. See docs/architecture.md#downloads-and-cleanup.
 */
@Dao
abstract class DownloadQueueDao {
    /** The states of the downloads that wait or run. */
    @Query("SELECT state FROM downloads WHERE state IN ('WAITING', 'DOWNLOADING')")
    abstract suspend fun queuedStates(): List<DownloadState>

    /** Puts back in the queue every download that a process which ended left as running. */
    @Query(
        "UPDATE downloads SET state = 'WAITING', updated_at = :now WHERE state = 'DOWNLOADING'",
    )
    abstract suspend fun resetDownloading(now: Instant): Int

    @Query(
        "SELECT * FROM downloads WHERE state = 'WAITING' ORDER BY requested_at, rowid LIMIT 1",
    )
    abstract suspend fun nextWaiting(): DownloadEntity?

    @Query(
        "UPDATE downloads SET state = 'DOWNLOADING', updated_at = :now " +
            "WHERE video_id = :videoId AND state = 'WAITING'",
    )
    abstract suspend fun markDownloading(videoId: String, now: Instant): Int

    /** The first waiting download, now marked as running, or null when none waits. */
    @Transaction
    open suspend fun claimNext(now: Instant): DownloadEntity? {
        val next = nextWaiting() ?: return null
        markDownloading(next.videoId, now)
        return next.copy(state = DownloadState.DOWNLOADING, updatedAt = now)
    }

    @Query(
        "UPDATE downloads SET video_itag = :videoItag, video_mime = :videoMime, " +
            "video_bytes = :videoBytes, audio_itag = :audioItag, audio_mime = :audioMime, " +
            "audio_bytes = :audioBytes, audio_track = :audioTrack, updated_at = :now " +
            "WHERE video_id = :videoId AND state = 'DOWNLOADING'",
    )
    abstract suspend fun setFormats(
        videoId: String,
        videoItag: Int,
        videoMime: String,
        videoBytes: Long?,
        audioItag: Int?,
        audioMime: String?,
        audioBytes: Long?,
        audioTrack: String?,
        now: Instant,
    ): Int

    /** Forgets the chosen formats and the bytes, before the files start again. */
    @Query(
        "UPDATE downloads SET video_itag = NULL, video_mime = NULL, video_bytes = NULL, " +
            "audio_itag = NULL, audio_mime = NULL, audio_bytes = NULL, audio_track = NULL, " +
            "downloaded_bytes = 0, " +
            "updated_at = :now WHERE video_id = :videoId AND state = 'DOWNLOADING'",
    )
    abstract suspend fun clearFormats(videoId: String, now: Instant): Int

    @Query(
        "UPDATE downloads SET downloaded_bytes = :bytes, updated_at = :now " +
            "WHERE video_id = :videoId AND state = 'DOWNLOADING'",
    )
    abstract suspend fun setProgress(videoId: String, bytes: Long, now: Instant): Int

    /** Puts a running download back in the queue, with [youtubeTries] failed tries in a row. */
    @Query(
        "UPDATE downloads SET state = 'WAITING', youtube_tries = :youtubeTries, " +
            "updated_at = :now WHERE video_id = :videoId AND state = 'DOWNLOADING'",
    )
    abstract suspend fun backToWaiting(videoId: String, youtubeTries: Int, now: Instant): Int

    /**
     * Fails a running download for [failure]. Unless [keepBytes], it forgets the formats and the
     * bytes, because the files are deleted.
     */
    @Query(
        "UPDATE downloads SET state = 'FAILED', failure = :failure, updated_at = :now, " +
            "video_itag = CASE WHEN :keepBytes THEN video_itag END, " +
            "video_mime = CASE WHEN :keepBytes THEN video_mime END, " +
            "video_bytes = CASE WHEN :keepBytes THEN video_bytes END, " +
            "audio_itag = CASE WHEN :keepBytes THEN audio_itag END, " +
            "audio_mime = CASE WHEN :keepBytes THEN audio_mime END, " +
            "audio_bytes = CASE WHEN :keepBytes THEN audio_bytes END, " +
            "audio_track = CASE WHEN :keepBytes THEN audio_track END, " +
            "downloaded_bytes = CASE WHEN :keepBytes THEN downloaded_bytes ELSE 0 END " +
            "WHERE video_id = :videoId AND state = 'DOWNLOADING'",
    )
    abstract suspend fun fail(
        videoId: String,
        failure: DownloadFailure,
        keepBytes: Boolean,
        now: Instant,
    ): Int

    /** Marks a running download done, with [bytes] on the phone. */
    @Query(
        "UPDATE downloads SET state = 'DOWNLOADED', failure = NULL, youtube_tries = 0, " +
            "downloaded_bytes = :bytes, updated_at = :now " +
            "WHERE video_id = :videoId AND state = 'DOWNLOADING'",
    )
    abstract suspend fun finish(videoId: String, bytes: Long, now: Instant): Int
}
