package io.github.dewfallapp.core.database

import androidx.room3.Dao
import androidx.room3.Insert
import androidx.room3.OnConflictStrategy
import androidx.room3.Query
import androidx.room3.Transaction
import kotlin.time.Instant
import kotlinx.coroutines.flow.Flow

/**
 * The watch state of each video. Every function is main-safe: Room runs the queries on its own
 * context. Saving a position and marking watched insert a row if the video has none, then update
 * it, which works on Android 10's SQLite 3.22 and never deletes a row. Marking unwatched only
 * updates.
 */
@Dao
abstract class WatchStateDao {
    /** The watch state of the video with [videoId], or null if it has none. */
    @Query("SELECT * FROM watch_state WHERE video_id = :videoId")
    abstract fun watchState(videoId: String): Flow<WatchStateEntity?>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    abstract suspend fun insertIfMissing(state: WatchStateEntity)

    @Query(
        "UPDATE watch_state SET position_ms = :positionMs, updated_at = :now " +
            "WHERE video_id = :videoId",
    )
    abstract suspend fun updatePosition(videoId: String, positionMs: Long, now: Instant)

    @Query(
        "UPDATE watch_state SET watched_at = COALESCE(watched_at, :now), updated_at = :now " +
            "WHERE video_id = :videoId",
    )
    abstract suspend fun updateWatched(videoId: String, now: Instant)

    /**
     * Marks the video with [videoId] unwatched and keeps its position. A video with no watch state
     * stays without one.
     */
    @Query("UPDATE watch_state SET watched_at = NULL, updated_at = :now WHERE video_id = :videoId")
    abstract suspend fun markUnwatched(videoId: String, now: Instant)

    /**
     * Saves [positionMs] as the position of the video with [videoId], at [now]. A position below
     * zero is saved as zero. Whether the video is marked watched stays as it was.
     */
    @Transaction
    open suspend fun savePosition(videoId: String, positionMs: Long, now: Instant) {
        insertIfMissing(unplayed(videoId, now))
        updatePosition(videoId, positionMs.coerceAtLeast(0), now)
    }

    /**
     * Marks the video with [videoId] watched at [now], or keeps the first time if it is already
     * marked. Its position stays as it was, or zero for a video with no watch state.
     */
    @Transaction
    open suspend fun markWatched(videoId: String, now: Instant) {
        insertIfMissing(unplayed(videoId, now))
        updateWatched(videoId, now)
    }

    private fun unplayed(videoId: String, now: Instant) =
        WatchStateEntity(videoId = videoId, positionMs = 0, watchedAt = null, updatedAt = now)
}
