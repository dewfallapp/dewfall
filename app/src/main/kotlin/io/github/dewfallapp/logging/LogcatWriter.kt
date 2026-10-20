package io.github.dewfallapp.logging

import io.github.dewfallapp.core.logging.LogLevel
import io.github.dewfallapp.core.logging.LogWriter

/**
 * The most bytes of text in one logcat entry. liblog cuts any entry whose payload, the priority,
 * the tag and the text together, is longer than LOGGER_ENTRY_MAX_PAYLOAD in its log.h, 4068 bytes.
 * 4000 leaves room for the priority and a short tag. See docs/architecture.md#logging.
 */
internal const val MAX_ENTRY_BYTES = 4000

/** Chars below this code take 1 byte in modified UTF-8, apart from U+0000, which takes 2. */
private const val ONE_BYTE_BELOW = 0x80

/**
 * Chars from [ONE_BYTE_BELOW] up to this code, not including it, take 2 bytes in modified UTF-8.
 */
private const val TWO_BYTES_BELOW = 0x800

/** Every other char takes 3 bytes in modified UTF-8, including each half of a surrogate pair. */
private const val MAX_CHAR_BYTES = 3

/** A surrogate pair, the longest character in modified UTF-8. */
private const val MAX_CHARACTER_BYTES = 2 * MAX_CHAR_BYTES

/**
 * Writes log lines to logcat. It is the only code that calls android.util.Log. detekt forbids
 * importing that class, so this file names it in full. See ADR 0007.
 *
 * Text longer than one entry holds goes out as several entries, with the same priority and tag.
 */
class LogcatWriter
internal constructor(
    // Log.println in the app. Tests pass their own, because android.util.Log has no working code
    // in local unit tests.
    private val printLine: (priority: Int, tag: String, text: String) -> Unit
) : LogWriter {
    constructor() : this({ priority, tag, text -> android.util.Log.println(priority, tag, text) })

    override fun write(level: LogLevel, tag: String, text: String) {
        val priority = level.toLogcatPriority()
        splitForLogcat(text).forEach { printLine(priority, tag, it) }
    }
}

internal fun LogLevel.toLogcatPriority(): Int =
    when (this) {
        LogLevel.DEBUG -> android.util.Log.DEBUG
        LogLevel.INFO -> android.util.Log.INFO
        LogLevel.WARN -> android.util.Log.WARN
        LogLevel.ERROR -> android.util.Log.ERROR
    }

/**
 * Splits [text] into logcat entries of at most [maxBytes] bytes each. It splits at line breaks, so
 * each entry holds whole lines where it can. A line longer than [maxBytes] is split between
 * characters, never inside one.
 *
 * Log.println hands the text to liblog through JNI's GetStringUTFChars. The JNI spec says that
 * returns modified UTF-8, which writes a character outside the Basic Multilingual Plane, such as
 * most emoji, as two 3-byte halves. ART writes it as UTF-8's 4 bytes instead, against the spec.
 * This counts it as 6, so an entry stays within [maxBytes] either way. See
 * docs/architecture.md#long-text.
 */
internal fun splitForLogcat(text: String, maxBytes: Int = MAX_ENTRY_BYTES): List<String> {
    require(maxBytes >= MAX_CHARACTER_BYTES) { "An entry must fit the longest character" }
    val entries = mutableListOf<String>()
    val pending = StringBuilder()
    // -1 while pending holds no line yet, so that an empty line still counts as one.
    var pendingBytes = -1

    fun flush() {
        if (pendingBytes >= 0) entries += pending.toString()
        pending.clear()
        pendingBytes = -1
    }

    for (line in text.split('\n')) {
        val lineBytes = line.sumOf { it.modifiedUtf8Bytes() }
        when {
            lineBytes > maxBytes -> {
                flush()
                entries += splitLine(line, maxBytes)
            }
            pendingBytes >= 0 && pendingBytes + 1 + lineBytes <= maxBytes -> {
                pending.append('\n').append(line)
                pendingBytes += 1 + lineBytes
            }
            else -> {
                flush()
                pending.append(line)
                pendingBytes = lineBytes
            }
        }
    }
    flush()
    return entries
}

/** Splits a line that is longer than [maxBytes] into pieces, keeping surrogate pairs whole. */
private fun splitLine(line: String, maxBytes: Int): List<String> {
    val pieces = mutableListOf<String>()
    var start = 0
    var pieceBytes = 0
    var index = 0
    while (index < line.length) {
        val end = index + Character.charCount(line.codePointAt(index))
        val characterBytes = (index until end).sumOf { line[it].modifiedUtf8Bytes() }
        if (pieceBytes + characterBytes > maxBytes) {
            pieces += line.substring(start, index)
            start = index
            pieceBytes = 0
        }
        pieceBytes += characterBytes
        index = end
    }
    pieces += line.substring(start)
    return pieces
}

/**
 * The bytes this char takes in modified UTF-8. Each half of a surrogate pair takes 3, and U+0000
 * takes 2, so the result is never less than the char's share of standard UTF-8.
 */
private fun Char.modifiedUtf8Bytes(): Int =
    when {
        code == 0 -> 2
        code < ONE_BYTE_BELOW -> 1
        code < TWO_BYTES_BELOW -> 2
        else -> MAX_CHAR_BYTES
    }
