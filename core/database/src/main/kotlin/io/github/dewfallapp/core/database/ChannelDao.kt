package io.github.dewfallapp.core.database

import androidx.room3.ColumnInfo
import androidx.room3.Dao
import androidx.room3.Embedded
import androidx.room3.Insert
import androidx.room3.OnConflictStrategy
import androidx.room3.Query
import kotlin.time.Instant
import kotlinx.coroutines.flow.Flow

/**
 * The query of [ChannelDao.channelOverviews], in one constant so that its plan test checks the
 * query the app runs.
 *
 * One correlated subquery picks the rowid of the channel's last upload: the newest row with a
 * publish time that isn't an upcoming premiere or live stream. Room stores VideoKind by name, so
 * 'UPCOMING' matches it. The join then reads that one row's time and approximate flag, so both come
 * from the same video. `ORDER BY published_at DESC, rowid DESC` matches the index on `(channel_id,
 * published_at)` read backwards, because an index's key ends with the table row's rowid
 * (https://www.sqlite.org/fileformat2.html, section 2.5), so SQLite stops at the first row that
 * counts and sorts nothing. The rows come in no particular order: the repository sorts them by
 * name.
 */
internal const val CHANNEL_OVERVIEWS_QUERY =
    "SELECT c.*, v.published_at AS last_published_at, " +
        "v.published_approximate AS last_published_approximate " +
        "FROM channels c " +
        "LEFT JOIN videos v ON v.rowid = (" +
        "SELECT v2.rowid FROM videos v2 " +
        "WHERE v2.channel_id = c.id AND v2.kind != 'UPCOMING' AND v2.published_at IS NOT NULL " +
        "ORDER BY v2.published_at DESC, v2.rowid DESC " +
        "LIMIT 1)"

/**
 * A subscribed channel with the publish time of its last upload, or nulls when no saved video of it
 * counts. The channel and the time are user data, so never log a row. See ADR 0007.
 */
data class ChannelOverviewRow(
    @Embedded val channel: ChannelEntity,
    @ColumnInfo(name = "last_published_at") val lastPublishedAt: Instant?,
    /** Whether [lastPublishedAt] is only an estimate. Null when [lastPublishedAt] is. */
    @ColumnInfo(name = "last_published_approximate") val lastPublishedApproximate: Boolean?,
)

/**
 * The subscribed channels. Every function is main-safe: Room runs the queries on its own context.
 */
@Dao
interface ChannelDao {
    /**
     * Every subscribed channel, by name, ignoring case for ASCII letters, then by ID. The Channels
     * screen reads [channelOverviews] instead, which the repository sorts by the phone's language.
     */
    @Query("SELECT * FROM channels ORDER BY name COLLATE NOCASE, id")
    fun subscribedChannels(): Flow<List<ChannelEntity>>

    /**
     * Every subscribed channel with its last upload, in no particular order. It emits again when a
     * subscription or a saved video changes. See [CHANNEL_OVERVIEWS_QUERY].
     */
    @Query(CHANNEL_OVERVIEWS_QUERY) fun channelOverviews(): Flow<List<ChannelOverviewRow>>

    /** The subscribed channel with [id], or null while it isn't subscribed. */
    @Query("SELECT * FROM channels WHERE id = :id")
    fun subscribedChannel(id: String): Flow<ChannelEntity?>

    /** Subscribes to [channel], or changes nothing if it is already subscribed. */
    @Insert(onConflict = OnConflictStrategy.IGNORE) suspend fun subscribe(channel: ChannelEntity)

    /** Unsubscribes from the channel with [id]. Its videos and their watch state stay. */
    @Query("DELETE FROM channels WHERE id = :id") suspend fun unsubscribe(id: String)
}
