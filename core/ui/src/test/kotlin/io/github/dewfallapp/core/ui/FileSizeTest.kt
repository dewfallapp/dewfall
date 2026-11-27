package io.github.dewfallapp.core.ui

import java.util.Locale
import kotlin.test.Test
import kotlin.test.assertEquals

class FileSizeTest {
    private fun shown(bytes: Long, unit: SizeUnit? = null): String {
        val size = fileSize(bytes, unit)
        return size.digits(Locale.UK) + " " + size.unit.name
    }

    @Test
    fun `sizes count 1,000 bytes to a kilobyte`() {
        assertEquals("0 KB", shown(0))
        assertEquals("1 KB", shown(1_000))
        assertEquals("1 MB", shown(1_000_000))
        assertEquals("1 GB", shown(1_000_000_000))
    }

    @Test
    fun `one decimal under 10 of the unit, none from 10`() {
        assertEquals("9.4 MB", shown(9_400_000))
        assertEquals("1.2 GB", shown(1_234_567_890))
        assertEquals("940 MB", shown(940_000_000))
        assertEquals("3.6 GB", shown(3_600_000_000))
        assertEquals("12 KB", shown(12_340))
    }

    @Test
    fun `a size that rounds up to the next step shows there`() {
        assertEquals("10 MB", shown(9_950_000))
        assertEquals("1 MB", shown(999_600))
        assertEquals("999 KB", shown(999_400))
        assertEquals("1 GB", shown(999_700_000))
    }

    @Test
    fun `the done part of a total shares the total's unit`() {
        val unit = sizeUnitOf(820_000_000)
        assertEquals("344 MB", shown(344_000_000, unit))
        assertEquals("0.3 MB", shown(300_000, unit))
        assertEquals("1,200 MB", shown(1_200_000_000, unit))
    }

    @Test
    fun `the digits follow the phone's language`() {
        assertEquals("3,6", fileSize(3_600_000_000).digits(Locale.GERMANY))
        assertEquals("3.6", fileSize(3_600_000_000).digits(Locale.UK))
    }
}
