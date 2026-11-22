package io.github.dewfallapp.core.database

import androidx.room3.ColumnTypeConverter
import io.github.dewfallapp.core.model.Thumbnail
import kotlin.time.Instant
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json

/**
 * How the entities' own types are stored: times as milliseconds since the Unix epoch, and thumbnail
 * and avatar lists as JSON text. Room uses its built-in converter for VideoKind, which stores the
 * name. Lengths and positions are already Long milliseconds in the entities, because Duration is a
 * Kotlin value class, and a plain Long column leaves no doubt about how Room stores it. See
 * docs/architecture.md#database-tables.
 */
internal object DatabaseConverters {
    private val json = Json {
        // A field added to the stored objects later doesn't break reading older text.
        ignoreUnknownKeys = true
        // An unknown width or height is left out of the text, and reads back as null.
        explicitNulls = false
    }

    @ColumnTypeConverter fun instantToMillis(instant: Instant): Long = instant.toEpochMilliseconds()

    @ColumnTypeConverter
    fun millisToInstant(millis: Long): Instant = Instant.fromEpochMilliseconds(millis)

    @ColumnTypeConverter
    fun thumbnailsToJson(thumbnails: List<Thumbnail>): String =
        json.encodeToString(thumbnails.map { StoredThumbnail(it.url, it.width, it.height) })

    /**
     * The list in [text]. Text that isn't such a list throws IllegalStateException with a fixed
     * message and no cause, because kotlinx.serialization's messages quote the text, and its URLs
     * are user data. See ADR 0007.
     */
    // The cause's message quotes the stored URLs, so it must not travel with the new exception.
    @Suppress("SwallowedException")
    @ColumnTypeConverter
    fun jsonToThumbnails(text: String): List<Thumbnail> {
        val stored =
            try {
                json.decodeFromString<List<StoredThumbnail>>(text)
            } catch (e: SerializationException) {
                throw IllegalStateException(UNREADABLE_THUMBNAILS)
            } catch (e: IllegalArgumentException) {
                throw IllegalStateException(UNREADABLE_THUMBNAILS)
            }
        return stored.map { Thumbnail(it.url, it.width, it.height) }
    }
}

internal const val UNREADABLE_THUMBNAILS = "A stored thumbnail list can't be read"

/** One thumbnail as the JSON text holds it. */
@Serializable
private data class StoredThumbnail(val url: String, val width: Int? = null, val height: Int? = null)
