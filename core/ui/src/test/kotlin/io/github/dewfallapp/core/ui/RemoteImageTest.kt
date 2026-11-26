package io.github.dewfallapp.core.ui

import io.github.dewfallapp.core.model.Thumbnail
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class RemoteImageTest {
    @Test
    fun `the width grows with the font size and the density, rounded up`() {
        assertEquals(72, imageWidthPx(widthDp = 72f, fontScale = 1f, density = 1f))
        assertEquals(189, imageWidthPx(widthDp = 72f, fontScale = 1f, density = 2.625f))
        assertEquals(432, imageWidthPx(widthDp = 72f, fontScale = 2f, density = 3f))
        assertEquals(109, imageWidthPx(widthDp = 72f, fontScale = 1.5f, density = 1.005f))
    }

    @Test
    fun `a font scale below 1 counts as 1, as the boxes never shrink`() {
        assertEquals(144, imageWidthPx(widthDp = 72f, fontScale = 0.85f, density = 2f))
    }

    @Test
    fun `the link is the size that fits, and an empty list has none`() {
        val small = Thumbnail("https://images.example.invalid/small.jpg", width = 120, height = 68)
        val large = Thumbnail("https://images.example.invalid/large.jpg", width = 336, height = 188)

        assertEquals(large.url, imageLink(listOf(small, large), widthPx = 189))
        assertEquals(small.url, imageLink(listOf(small, large), widthPx = 72))
        assertNull(imageLink(emptyList(), widthPx = 189))
    }
}
