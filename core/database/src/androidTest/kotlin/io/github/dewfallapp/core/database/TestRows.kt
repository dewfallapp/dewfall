package io.github.dewfallapp.core.database

import androidx.room3.Room
import androidx.sqlite.driver.AndroidSQLiteDriver
import androidx.test.platform.app.InstrumentationRegistry
import io.github.dewfallapp.core.model.Thumbnail
import io.github.dewfallapp.core.model.VideoKind
import kotlin.time.Instant

/** Made-up IDs and times. The tests choose every time, so none depends on the clock. */
internal const val CHANNEL_A = "UCaaaaaaaaaaaaaaaaaaaaaa"
internal const val CHANNEL_B = "UCbbbbbbbbbbbbbbbbbbbbbb"
internal val MONDAY: Instant = Instant.parse("2026-11-16T09:00:00Z")
internal val TUESDAY: Instant = Instant.parse("2026-11-17T09:00:00Z")
internal val WEDNESDAY: Instant = Instant.parse("2026-11-18T09:00:00Z")
internal val AVATARS = listOf(Thumbnail("https://yt3.example.invalid/a=s88", 88, 88))
internal val THUMBNAILS = listOf(Thumbnail("https://i.example.invalid/vi/x/hq.jpg", 480, 360))

/** An empty in-memory database, built as the app builds its own, with the phone's SQLite. */
internal fun inMemoryDatabase(): DewfallDatabase =
    Room.inMemoryDatabaseBuilder<DewfallDatabase>(
            InstrumentationRegistry.getInstrumentation().targetContext,
        )
        .setDriver(AndroidSQLiteDriver())
        .build()

/** A made-up 11-character video ID from [n]. */
internal fun videoId(n: Int): String = "v" + n.toString().padStart(10, '0')

internal fun channel(
    id: String = CHANNEL_A,
    name: String = "Kettle and Crumb",
    description: String = "Bread and fermentation.",
    avatars: List<Thumbnail> = AVATARS,
    subscribedAt: Instant = MONDAY,
) = ChannelEntity(id, name, description, avatars, subscribedAt)

internal fun details(
    id: String = CHANNEL_A,
    name: String = "Kettle and Crumb",
    description: String = "Bread and fermentation.",
    avatars: List<Thumbnail> = AVATARS,
) = ChannelDetails(id, name, description, avatars)

internal fun video(
    id: String,
    title: String = "A video",
    channelId: String = CHANNEL_A,
    durationMs: Long? = 600_000,
    publishedAt: Instant? = MONDAY,
    approximate: Boolean = false,
    kind: VideoKind = VideoKind.VIDEO,
    thumbnails: List<Thumbnail> = THUMBNAILS,
    savedAt: Instant = MONDAY,
    listPosition: Int = 0,
) =
    VideoEntity(
        id = id,
        channelId = channelId,
        channelName = "Kettle and Crumb",
        title = title,
        durationMs = durationMs,
        publishedAt = publishedAt,
        publishedApproximate = approximate,
        kind = kind,
        thumbnails = thumbnails,
        firstSeenAt = savedAt,
        listingNumber = null,
        listPosition = listPosition,
    )

/**
 * [videos] as a save at [savedAt] passes them to the DAO: each first seen then, at its place in the
 * list. The DAO gives them the save's listing number.
 */
internal fun listed(savedAt: Instant, vararg videos: VideoEntity): List<VideoEntity> =
    videos.mapIndexed { place, video ->
        video.copy(firstSeenAt = savedAt, listPosition = place)
    }
