package io.github.dewfallapp.core.database

import androidx.room3.ColumnInfo
import androidx.room3.Entity
import androidx.room3.Index
import androidx.room3.PrimaryKey
import io.github.dewfallapp.core.model.Thumbnail
import io.github.dewfallapp.core.model.VideoKind
import kotlin.time.Instant

/**
 * A channel the user subscribes to. A row exists only while the user is subscribed. The ID, name,
 * description and avatar links are user data, and so is toString, so never log a row. See ADR 0007.
 */
@Entity(tableName = "channels")
data class ChannelEntity(
    @PrimaryKey val id: String,
    val name: String,
    val description: String,
    val avatars: List<Thumbnail>,
    @ColumnInfo(name = "subscribed_at") val subscribedAt: Instant,
)

/**
 * A video that Dewfall saved, from a channel's uploads. It has no foreign key to [ChannelEntity],
 * because later tasks save videos of channels the user doesn't subscribe to, and unsubscribing
 * keeps the videos. The ID, title, names and thumbnail links are user data, and so is toString, so
 * never log a row. See ADR 0007.
 */
@Entity(
    tableName = "videos",
    indices = [Index(value = ["channel_id", "published_at"])],
)
data class VideoEntity(
    @PrimaryKey val id: String,
    @ColumnInfo(name = "channel_id") val channelId: String,
    @ColumnInfo(name = "channel_name") val channelName: String,
    val title: String,
    /** The length in milliseconds, or null when YouTube gave none. */
    @ColumnInfo(name = "duration_ms") val durationMs: Long?,
    @ColumnInfo(name = "published_at") val publishedAt: Instant?,
    /** Whether [publishedAt] is only an estimate. False when [publishedAt] is null. */
    @ColumnInfo(name = "published_approximate") val publishedApproximate: Boolean,
    val kind: VideoKind,
    val thumbnails: List<Thumbnail>,
    /** When Dewfall first saved the video. A later save never changes it. */
    @ColumnInfo(name = "first_seen_at") val firstSeenAt: Instant,
    /**
     * The number of the latest save of a channel's uploads that held the video. Each save gets a
     * number above every one already stored, so a later save always has a higher number, even when
     * the clock repeats or goes back. Null for a video saved another way.
     */
    @ColumnInfo(name = "listing_number") val listingNumber: Long?,
    /** The video's place in that save's list, counted from 0. */
    @ColumnInfo(name = "list_position") val listPosition: Int?,
)

/**
 * What the user did with one video, kept apart from YouTube's details so a refresh never touches
 * it. It has no foreign key, so a position can be saved for any video ID. The ID and the times are
 * user data, and so is toString, so never log a row. See ADR 0007.
 */
@Entity(tableName = "watch_state")
data class WatchStateEntity(
    @PrimaryKey @ColumnInfo(name = "video_id") val videoId: String,
    /** The position to resume from, in milliseconds. */
    @ColumnInfo(name = "position_ms") val positionMs: Long,
    /** When the video was first marked watched, or null if it isn't marked watched. */
    @ColumnInfo(name = "watched_at") val watchedAt: Instant?,
    /** The time of the last save of the position or the last mark. */
    @ColumnInfo(name = "updated_at") val updatedAt: Instant,
)
