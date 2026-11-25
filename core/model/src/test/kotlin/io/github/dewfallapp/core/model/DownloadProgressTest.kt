package io.github.dewfallapp.core.model

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class DownloadProgressTest {
    @Test
    fun `nothing yet is 0 percent`() {
        assertEquals(0, DownloadProgress(downloadedBytes = 0, totalBytes = 1_000).percent)
    }

    @Test
    fun `part way rounds down`() {
        assertEquals(42, DownloadProgress(downloadedBytes = 429, totalBytes = 1_000).percent)
        assertEquals(99, DownloadProgress(downloadedBytes = 999, totalBytes = 1_000).percent)
    }

    @Test
    fun `every byte is 100 percent`() {
        assertEquals(100, DownloadProgress(downloadedBytes = 1_000, totalBytes = 1_000).percent)
    }

    @Test
    fun `bytes past the total stay at 100 percent`() {
        assertEquals(100, DownloadProgress(downloadedBytes = 1_200, totalBytes = 1_000).percent)
    }

    @Test
    fun `an unknown or empty total has no percentage`() {
        assertNull(DownloadProgress(downloadedBytes = 10, totalBytes = null).percent)
        assertNull(DownloadProgress(downloadedBytes = 0, totalBytes = 0).percent)
    }

    @Test
    fun `a large file doesn't overflow`() {
        val total = 8L * 1024 * 1024 * 1024
        assertEquals(50, DownloadProgress(downloadedBytes = total / 2, totalBytes = total).percent)
    }
}
