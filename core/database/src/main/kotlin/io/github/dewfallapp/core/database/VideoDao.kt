package io.github.dewfallapp.core.database

import androidx.room3.Dao
import androidx.room3.Insert
import androidx.room3.Query
import androidx.room3.Transaction
import androidx.room3.Update
import io.github.dewfallapp.core.model.Thumbnail
import io.github.dewfallapp.core.model.VideoKind
import kotlinx.coroutines.flow.Flow

/**
 * The most video IDs in one `IN` list. SQLite before 3.32.0, which Android 11 and older ship,
 * allows at most 999 bound values in a statement.
 */
private const val MAX_IDS_PER_QUERY = 500

/** A channel's details from a save of its uploads. They are user data, so never log them. */
data class ChannelDetails(
    val id: String,
    val name: String,
    val description: String,
    val avatars: List<Thumbnail>,
)

/** The saved videos. Every function is main-safe: Room runs the queries on its own context. */
@Dao
abstract class VideoDao {
    /** The saved video with [id], or null. */
    @Query("SELECT * FROM videos WHERE id = :id")
    abstract suspend fun video(id: String): VideoEntity?

    /**
     * A channel's saved videos: newest publish time first, videos with no time last, then the
     * latest save that held them first, then their place in it, then by ID. SQLite sorts NULL below
     * every other value, so `DESC` puts videos with no time last, and Android 10's SQLite has no
     * `NULLS LAST`. This keeps YouTube's order for videos with the same time. [inboxVideos] uses
     * the same order.
     */
    @Query(
        "SELECT * FROM videos WHERE channel_id = :channelId " +
            "ORDER BY published_at DESC, listing_number DESC, list_position ASC, id ASC",
    )
    abstract fun channelVideos(channelId: String): Flow<List<VideoEntity>>

    /**
     * The inbox's videos, as the inbox spec defines them, in the order of [channelVideos] across
     * every channel: the [video] kind videos of subscribed channels that aren't marked watched, and
     * that either were published at or after the time of subscribing, or were first saved by a save
     * of the channel's uploads later than its first such save since subscribing, or are one of the
     * channel's 2 latest of that kind, watched or not, or were started, resumed or marked unwatched
     * since subscribing, with a position past 0.
     *
     * The subquery `s` works out each channel's facts once. Its `LIMIT -1` sets no limit, but it
     * stops SQLite from flattening `s` into the outer query, which would run its scalar subqueries
     * again for every video. See "The inbox's videos" in docs/architecture.md#database-tables. Room
     * runs it again whenever `videos`, `channels` or `watch_state` changes.
     */
    @Query(
        "SELECT v.* FROM videos AS v " +
            "JOIN (" +
            "SELECT c.id AS channel_id, c.subscribed_at AS subscribed_at, " +
            "(SELECT l.id FROM videos AS l WHERE l.channel_id = c.id AND l.kind = :video " +
            "ORDER BY l.published_at DESC, l.listing_number DESC, l.list_position ASC, l.id ASC " +
            "LIMIT 1) AS latest, " +
            "(SELECT l.id FROM videos AS l WHERE l.channel_id = c.id AND l.kind = :video " +
            "ORDER BY l.published_at DESC, l.listing_number DESC, l.list_position ASC, l.id ASC " +
            "LIMIT 1 OFFSET 1) AS second, " +
            "(SELECT MIN(f.first_seen_at) FROM videos AS f WHERE f.channel_id = c.id " +
            "AND f.listing_number IS NOT NULL AND f.first_seen_at >= c.subscribed_at) " +
            "AS first_save " +
            "FROM channels AS c LIMIT -1" +
            ") AS s ON s.channel_id = v.channel_id " +
            "LEFT JOIN watch_state AS w ON w.video_id = v.id " +
            "WHERE v.kind = :video AND w.watched_at IS NULL AND (" +
            "v.published_at >= s.subscribed_at " +
            "OR (v.listing_number IS NOT NULL AND v.first_seen_at > s.first_save) " +
            "OR v.id = s.latest OR v.id = s.second " +
            "OR (w.position_ms > 0 AND w.updated_at >= s.subscribed_at)" +
            ") " +
            "ORDER BY v.published_at DESC, v.listing_number DESC, v.list_position ASC, v.id ASC",
    )
    abstract fun inboxVideos(video: VideoKind): Flow<List<VideoEntity>>

    /** The highest listing number stored, or null when no save has run. */
    @Query("SELECT MAX(listing_number) FROM videos")
    abstract suspend fun highestListingNumber(): Long?

    @Query("SELECT * FROM videos WHERE id IN (:ids)")
    abstract suspend fun videosWithIds(ids: List<String>): List<VideoEntity>

    @Insert abstract suspend fun insertVideos(videos: List<VideoEntity>)

    @Update abstract suspend fun updateVideos(videos: List<VideoEntity>)

    @Query("SELECT * FROM channels WHERE id = :id")
    abstract suspend fun channelWithId(id: String): ChannelEntity?

    @Update abstract suspend fun updateChannel(channel: ChannelEntity)

    /**
     * Saves a channel's uploads in one transaction. Each video in [videos] is merged with its saved
     * row by [mergeVideo], and a video ID that appears twice is kept once, at its first place.
     * Every video gets this save's listing number, one above the highest stored, so the order of
     * [channelVideos] never depends on the clock. The channel's row, if it is subscribed, is merged
     * with [channel] by [mergeChannelDetails]. A channel that isn't subscribed stays that way. No
     * row is deleted.
     *
     * It reads which videos are saved first, so it inserts the new ones and updates the rest, with
     * no failed insert. SQLite's own UPSERT syntax needs 3.24.0, newer than Android 10's 3.22.
     */
    @Transaction
    open suspend fun saveChannelUploads(channel: ChannelDetails, videos: List<VideoEntity>) {
        val listing = (highestListingNumber() ?: 0) + 1
        val unique = videos.distinctBy { it.id }.map { it.copy(listingNumber = listing) }
        val saved = HashMap<String, VideoEntity>()
        for (chunk in unique.map { it.id }.chunked(MAX_IDS_PER_QUERY)) {
            for (video in videosWithIds(chunk)) {
                saved[video.id] = video
            }
        }
        val toInsert = ArrayList<VideoEntity>()
        val toUpdate = ArrayList<VideoEntity>()
        for (video in unique) {
            val old = saved[video.id]
            if (old == null) toInsert += video else toUpdate += mergeVideo(old, video)
        }
        if (toInsert.isNotEmpty()) insertVideos(toInsert)
        if (toUpdate.isNotEmpty()) updateVideos(toUpdate)
        val subscribed = channelWithId(channel.id)
        if (subscribed != null) updateChannel(mergeChannelDetails(subscribed, channel))
    }
}
