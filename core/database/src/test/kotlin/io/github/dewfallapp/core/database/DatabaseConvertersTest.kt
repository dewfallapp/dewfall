package io.github.dewfallapp.core.database

import io.github.dewfallapp.core.model.Thumbnail
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.time.Instant

class DatabaseConvertersTest {
    @Test
    fun `an instant keeps its milliseconds`() {
        val instant = Instant.parse("2026-11-22T08:30:15.123Z")

        val millis = DatabaseConverters.instantToMillis(instant)

        assertEquals(instant.toEpochMilliseconds(), millis)
        assertEquals(instant, DatabaseConverters.millisToInstant(millis))
    }

    @Test
    fun `thumbnails come back with their sizes`() {
        val thumbnails =
            listOf(
                Thumbnail("https://i.ytimg.com/vi/aaaaaaaaaaa/default.jpg", 120, 90),
                Thumbnail("https://i.ytimg.com/vi/aaaaaaaaaaa/hqdefault.jpg", null, null),
                Thumbnail("https://i.ytimg.com/vi/aaaaaaaaaaa/mq.jpg?a=1&b=\"2\"", 320, null),
            )

        val text = DatabaseConverters.thumbnailsToJson(thumbnails)

        assertEquals(thumbnails, DatabaseConverters.jsonToThumbnails(text))
    }

    @Test
    fun `an unknown size is left out of the text`() {
        val text =
            DatabaseConverters.thumbnailsToJson(listOf(Thumbnail("https://a.example", null, 5)))

        assertEquals("""[{"url":"https://a.example","height":5}]""", text)
    }

    @Test
    fun `an empty list comes back empty`() {
        val text = DatabaseConverters.thumbnailsToJson(emptyList())

        assertEquals("[]", text)
        assertEquals(emptyList(), DatabaseConverters.jsonToThumbnails(text))
    }

    @Test
    fun `fields added later are ignored`() {
        val text = """[{"url":"https://a.example","width":1,"height":2,"format":"webp"}]"""

        assertEquals(
            listOf(Thumbnail("https://a.example", 1, 2)),
            DatabaseConverters.jsonToThumbnails(text),
        )
    }

    @Test
    fun `unreadable text fails with a fixed message that quotes nothing`() {
        for (text in
            listOf("not json https://secret.example", """[{"width":1}]""", """{"url":"x"}""")) {
            val failure =
                assertFailsWith<IllegalStateException> { DatabaseConverters.jsonToThumbnails(text) }

            assertEquals(UNREADABLE_THUMBNAILS, failure.message)
            assertNull(failure.cause)
        }
    }
}
