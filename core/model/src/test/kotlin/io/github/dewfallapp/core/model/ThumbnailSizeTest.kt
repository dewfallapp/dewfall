package io.github.dewfallapp.core.model

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class ThumbnailSizeTest {
    private fun size(name: String, width: Int?) =
        Thumbnail(url = "https://images.example.invalid/$name.jpg", width = width, height = null)

    private val small = size("small", 120)
    private val medium = size("medium", 320)
    private val large = size("large", 480)

    @Test
    fun `the smallest size at least as wide as the space is picked`() {
        assertEquals(medium, listOf(small, medium, large).forWidth(189))
        assertEquals(medium, listOf(large, small, medium).forWidth(189))
    }

    @Test
    fun `an exact width is picked`() {
        assertEquals(medium, listOf(small, medium, large).forWidth(320))
    }

    @Test
    fun `the widest size is picked when none is wide enough`() {
        assertEquals(large, listOf(small, medium, large).forWidth(600))
    }

    @Test
    fun `of two sizes with the same width, the first is picked`() {
        val other = size("other", 320)
        assertEquals(medium, listOf(small, medium, other).forWidth(189))
        assertEquals(other, listOf(other, medium).forWidth(600))
    }

    @Test
    fun `sizes with no width count only when no size has one`() {
        val unknown = size("unknown", null)
        val unknownToo = size("unknownToo", null)
        assertEquals(small, listOf(unknown, small).forWidth(600))
        assertEquals(unknown, listOf(unknown, unknownToo).forWidth(600))
    }

    @Test
    fun `an empty list has no size`() {
        assertNull(emptyList<Thumbnail>().forWidth(189))
    }
}
