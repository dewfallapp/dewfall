package io.github.dewfallapp.core.database

import androidx.room3.Dao
import androidx.room3.Insert
import androidx.room3.OnConflictStrategy
import androidx.room3.Query
import kotlinx.coroutines.flow.Flow

/**
 * The subscribed channels. Every function is main-safe: Room runs the queries on its own context.
 */
@Dao
interface ChannelDao {
    /**
     * Every subscribed channel, by name, ignoring case for ASCII letters, then by ID. The channels
     * screen (#115) decides the order it shows.
     */
    @Query("SELECT * FROM channels ORDER BY name COLLATE NOCASE, id")
    fun subscribedChannels(): Flow<List<ChannelEntity>>

    /** The subscribed channel with [id], or null while it isn't subscribed. */
    @Query("SELECT * FROM channels WHERE id = :id")
    fun subscribedChannel(id: String): Flow<ChannelEntity?>

    /** Subscribes to [channel], or changes nothing if it is already subscribed. */
    @Insert(onConflict = OnConflictStrategy.IGNORE) suspend fun subscribe(channel: ChannelEntity)

    /** Unsubscribes from the channel with [id]. Its videos and their watch state stay. */
    @Query("DELETE FROM channels WHERE id = :id") suspend fun unsubscribe(id: String)
}
