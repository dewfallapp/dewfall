package io.github.dewfallapp.core.youtube.newpipe.saved

/** The characters that JSON escapes with a letter, by that letter. */
private val ESCAPES = mapOf('n' to '\n', 't' to '\t', 'r' to '\r', 'b' to '\b', 'f' to '\u000c')

/** The number of hex digits in a \u escape. */
private const val UNICODE_DIGITS = 4

/** The length of a whole \u escape. */
private const val UNICODE_ESCAPE = 6

/** The characters other than letters and digits that a number is written with. */
private const val NUMBER_SIGNS = ".+-"

/**
 * A JSON value in a saved file's text, with the index of its first character and the index after
 * its last, so masking can replace a value in place and leave the rest of the text as it was.
 */
internal sealed class JsonValue(val start: Int, val end: Int)

/** An object, with its members in order. */
internal class JsonObject(start: Int, end: Int, val members: List<Pair<String, JsonValue>>) :
    JsonValue(start, end)

/** An array, with its items in order. */
internal class JsonArray(start: Int, end: Int, val items: List<JsonValue>) : JsonValue(start, end)

/** A string, with its escapes read. */
internal class JsonString(start: Int, end: Int, val value: String) : JsonValue(start, end)

/** A number, true, false or null, as it is written. */
internal class JsonLiteral(start: Int, end: Int, val text: String) : JsonValue(start, end)

/** Thrown when the text isn't JSON where a value should start. */
private class MalformedJsonException : Exception("Not JSON")

/**
 * The JSON value that starts at [start] in [text], after any white space, or null if the text isn't
 * JSON there. The text may go on after the value, as a script does after an object in it.
 */
internal fun parseJsonAt(text: String, start: Int = 0): JsonValue? =
    try {
        JsonParser(text).valueAt(start)
    } catch (_: MalformedJsonException) {
        null
    }

/**
 * [text] as one JSON value, or null if it isn't one: a body that starts with { or [ and holds
 * nothing but white space after the value.
 */
internal fun parseJsonBody(text: String): JsonValue? {
    val value = if (looksLikeJsonBody(text)) parseJsonAt(text) else null
    return value?.takeIf { text.substring(it.end).isBlank() }
}

/** Whether [text] starts like a JSON body, with { or [, so a check must be able to read it. */
internal fun looksLikeJsonBody(text: String): Boolean {
    val trimmed = text.trimStart()
    return trimmed.startsWith("{") || trimmed.startsWith("[")
}

/** [text] with each of [replacements], from a value's start to its end, replaced by new text. */
internal fun replaceValues(text: String, replacements: List<Pair<JsonValue, String>>): String {
    val result = StringBuilder()
    var index = 0
    for ((value, replacement) in replacements.sortedBy { it.first.start }) {
        result.append(text, index, value.start).append(replacement)
        index = value.end
    }
    return result.append(text, index, text.length).toString()
}

/** Reads the JSON values of [text]. */
private class JsonParser(private val text: String) {
    fun valueAt(from: Int): JsonValue {
        val start = skipSpace(from)
        return when (text.getOrNull(start)) {
            '{' -> objectAt(start)
            '[' -> arrayAt(start)
            '"' -> stringAt(start)
            else -> literalAt(start)
        }
    }

    private fun objectAt(start: Int): JsonObject {
        val members = mutableListOf<Pair<String, JsonValue>>()
        var index = skipSpace(start + 1)
        var open = text.getOrNull(index) != '}'
        while (open) {
            val name = stringAt(skipSpace(index))
            index = skipSpace(name.end)
            if (text.getOrNull(index) != ':') throw MalformedJsonException()
            val value = valueAt(index + 1)
            members += name.value to value
            index = skipSpace(value.end)
            when (text.getOrNull(index)) {
                ',' -> index++
                '}' -> open = false
                else -> throw MalformedJsonException()
            }
        }
        return JsonObject(start, index + 1, members)
    }

    private fun arrayAt(start: Int): JsonArray {
        val items = mutableListOf<JsonValue>()
        var index = skipSpace(start + 1)
        var open = text.getOrNull(index) != ']'
        while (open) {
            val item = valueAt(index)
            items += item
            index = skipSpace(item.end)
            when (text.getOrNull(index)) {
                ',' -> index++
                ']' -> open = false
                else -> throw MalformedJsonException()
            }
        }
        return JsonArray(start, index + 1, items)
    }

    private fun stringAt(start: Int): JsonString {
        if (text.getOrNull(start) != '"') throw MalformedJsonException()
        val (value, end) = stringAt(text, start)
        if (end > text.length) throw MalformedJsonException()
        return JsonString(start, end, value)
    }

    private fun literalAt(start: Int): JsonLiteral {
        var end = start
        while (end < text.length && (text[end].isLetterOrDigit() || text[end] in NUMBER_SIGNS)) {
            end++
        }
        if (end == start) throw MalformedJsonException()
        return JsonLiteral(start, end, text.substring(start, end))
    }

    private fun skipSpace(from: Int): Int {
        var index = from
        while (index < text.length && text[index].isWhitespace()) {
            index++
        }
        return index
    }
}

/**
 * The string that starts with the quote at [start] in [text], with its escapes read, and the index
 * after its closing quote. Without a closing quote, the index is past the end of the text.
 */
internal fun stringAt(text: String, start: Int): Pair<String, Int> {
    val string = StringBuilder()
    var index = start + 1
    while (index < text.length && text[index] != '"') {
        val (char, length) = characterAt(text, index)
        string.append(char)
        index += length
    }
    return string.toString() to index + 1
}

/**
 * The character that the text at [index] in a JSON string stands for, and how many characters of
 * the text it takes: one for a plain character, and more for an escape.
 */
private fun characterAt(text: String, index: Int): Pair<Char, Int> {
    val char = text[index]
    val escaped = text.getOrNull(index + 1)
    val digitsStart = minOf(index + 2, text.length)
    val unicode =
        text
            .substring(digitsStart, minOf(digitsStart + UNICODE_DIGITS, text.length))
            .toIntOrNull(HEX_RADIX)
    return when {
        char != '\\' || escaped == null -> char to 1
        escaped == 'u' && unicode != null -> unicode.toChar() to UNICODE_ESCAPE
        else -> (ESCAPES[escaped] ?: escaped) to 2
    }
}

/** [text] with every JSON escape in it read, inside strings or not. */
internal fun jsonUnescaped(text: String): String {
    val result = StringBuilder()
    var index = 0
    while (index < text.length) {
        val (char, length) = characterAt(text, index)
        result.append(char)
        index += length
    }
    return result.toString()
}
