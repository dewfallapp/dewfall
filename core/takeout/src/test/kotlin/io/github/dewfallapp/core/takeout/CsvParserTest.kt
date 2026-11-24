package io.github.dewfallapp.core.takeout

import kotlin.test.Test
import kotlin.test.assertEquals

/** Tests what CsvParser keeps of each row. TakeoutReaderTest covers the rest through the reader. */
class CsvParserTest {
    private fun rows(text: String, keptFields: Int): List<CsvRow> {
        val rows = mutableListOf<CsvRow>()
        CsvParser(text, keptFields) { rows += it }.parse()
        return rows
    }

    @Test
    fun `a row keeps only its first fields and counts them all`() {
        val row = rows("a,b,c,d,e,f,g,h,i,j\n", keptFields = 3).single()

        assertEquals(listOf("a", "b", "c"), row.fields)
        assertEquals(10, row.fieldCount)
    }

    @Test
    fun `a row with fewer fields than kept keeps them all`() {
        val row = rows("a,b", keptFields = 3).single()

        assertEquals(listOf("a", "b"), row.fields)
        assertEquals(2, row.fieldCount)
    }

    @Test
    fun `the count starts again on each row, and a blank row is one field`() {
        val parsed = rows("a,b,c,d\n \t\nx,y\n", keptFields = 3)

        assertEquals(listOf(4, 1, 2), parsed.map { it.fieldCount })
        assertEquals(listOf(false, true, false), parsed.map { it.isBlank })
    }
}
