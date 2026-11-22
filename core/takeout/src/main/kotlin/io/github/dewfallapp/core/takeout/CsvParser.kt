package io.github.dewfallapp.core.takeout

/**
 * One line of a table: its first fields, at most the parser's keptFields, how many fields it has in
 * all, and whether it holds only spaces and tabs, outside quotes.
 */
internal class CsvRow(val fields: List<String>, val fieldCount: Int, val isBlank: Boolean)

/**
 * Splits a text into the rows of a table of comma-separated values, as RFC 4180's section 2 says,
 * and a little more:
 * - A field that starts with a double quote ends at the next double quote that isn't doubled. It
 *   may hold commas, line breaks and doubled double quotes, which become one. A quoted field that
 *   the text ends inside ends with the text.
 * - A double quote inside a field that didn't start with one stays as it is, and so does text after
 *   a quoted field's closing quote.
 * - A line ends at a line feed, at a carriage return and a line feed, or at a lone carriage return.
 *
 * A text that ends with a line break has no empty row after it. Each row keeps only its first
 * [keptFields] fields, at least 1, and counts the rest, so a line with millions of commas doesn't
 * fill memory with fields that nobody reads.
 */
internal class CsvParser(
    private val text: String,
    private val keptFields: Int,
    private val onRow: (CsvRow) -> Unit,
) {
    init {
        require(keptFields >= 1) { "keptFields must be at least 1" }
    }

    private val fields = mutableListOf<String>()
    private var fieldCount = 0
    private val field = StringBuilder()
    private var quoted = false
    private var lineHasQuote = false
    private var index = 0

    /** Gives each row to onRow, in order, without keeping them. */
    fun parse() {
        while (index < text.length) {
            if (quoted) readQuoted(text[index]) else readPlain(text[index])
            index++
        }
        if (field.isNotEmpty() || fieldCount > 0 || lineHasQuote) endRow()
    }

    private fun readQuoted(char: Char) {
        when {
            char == QUOTE && text.getOrNull(index + 1) == QUOTE -> {
                field.append(QUOTE)
                index++
            }
            char == QUOTE -> quoted = false
            else -> field.append(char)
        }
    }

    private fun readPlain(char: Char) {
        when {
            char == QUOTE && field.isEmpty() -> {
                quoted = true
                lineHasQuote = true
            }
            char == COMMA -> endField()
            char == LINE_FEED -> endRow()
            char == CARRIAGE_RETURN -> {
                if (text.getOrNull(index + 1) == LINE_FEED) index++
                endRow()
            }
            else -> field.append(char)
        }
    }

    private fun endField() {
        if (fieldCount < keptFields) fields += field.toString()
        fieldCount++
        field.setLength(0)
    }

    private fun endRow() {
        endField()
        val blank = !lineHasQuote && fieldCount == 1 && fields[0].all { it == ' ' || it == '\t' }
        onRow(CsvRow(fields.toList(), fieldCount, blank))
        fields.clear()
        fieldCount = 0
        lineHasQuote = false
    }
}

private const val QUOTE = '"'
private const val COMMA = ','
private const val LINE_FEED = '\n'
private const val CARRIAGE_RETURN = '\r'
