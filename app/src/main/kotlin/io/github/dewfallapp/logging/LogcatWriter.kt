// This is the one file allowed to call android.util.Log, as ADR 0007 says, so it imports the
// class that detekt's ForbiddenImport rejects everywhere else. detekt can suppress an import
// finding only for a whole file, because an import can't carry an annotation.
@file:Suppress("ForbiddenImport")

package io.github.dewfallapp.logging

import android.util.Log
import io.github.dewfallapp.core.logging.LogLevel
import io.github.dewfallapp.core.logging.LogWriter

/**
 * The most bytes of text in one logcat entry. liblog cuts any entry whose payload, the priority,
 * the tag and the text together, is longer than LOGGER_ENTRY_MAX_PAYLOAD in its log.h, 4068 bytes.
 * 4000 leaves room for the priority and a short tag. See docs/architecture.md#logging.
 */
private const val MAX_ENTRY_BYTES = 4000

/** The first char code that takes 2 bytes in modified UTF-8. */
private const val FIRST_TWO_BYTE_CODE = 0x80

/** The first char code that takes 3 bytes in modified UTF-8. */
private const val FIRST_THREE_BYTE_CODE = 0x800

/**
 * The most bytes that one UTF-16 char takes in modified UTF-8: any char from
 * [FIRST_THREE_BYTE_CODE] up, including each half of a surrogate pair.
 */
private const val MAX_UTF16_CHAR_BYTES = 3

/**
 * The most bytes that one code point takes in modified UTF-8: a surrogate pair, two chars of
 * [MAX_UTF16_CHAR_BYTES] each.
 */
private const val MAX_CODE_POINT_BYTES = 2 * MAX_UTF16_CHAR_BYTES

/** The bytes of the line break between two lines in one entry. */
private const val LINE_BREAK_BYTES = 1

/**
 * Writes log lines to logcat. It is the only code that calls android.util.Log. See ADR 0007.
 *
 * Text longer than one entry holds goes out as several entries, with the same priority and tag.
 */
internal class LogcatWriter(
    // Tests pass their own, because android.util.Log has no working code in local unit tests.
    private val printLine: (priority: Int, tag: String, text: String) -> Unit = Log::println
) : LogWriter {
    override fun write(level: LogLevel, tag: String, text: String) {
        val priority = level.toLogcatPriority()
        for (entry in splitForLogcat(text)) printLine(priority, tag, entry)
    }
}

private fun LogLevel.toLogcatPriority(): Int =
    when (this) {
        LogLevel.DEBUG -> Log.DEBUG
        LogLevel.INFO -> Log.INFO
        LogLevel.WARN -> Log.WARN
        LogLevel.ERROR -> Log.ERROR
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
    require(maxBytes >= MAX_CODE_POINT_BYTES) { "An entry must fit the longest character" }
    val entries = mutableListOf<String>()
    // Whole lines waiting to go out together as the next entry, and their bytes with the line
    // breaks between them. An empty line counts as a line too.
    val pending = mutableListOf<String>()
    var pendingBytes = 0

    for (line in text.split('\n')) {
        val lineBytes = line.sumOf { it.modifiedUtf8Bytes() }
        if (pending.isNotEmpty() && pendingBytes + LINE_BREAK_BYTES + lineBytes > maxBytes) {
            entries += pending.joinToString("\n")
            pending.clear()
        }
        if (lineBytes > maxBytes) {
            entries += splitLine(line, maxBytes)
        } else {
            pendingBytes =
                if (pending.isEmpty()) lineBytes else pendingBytes + LINE_BREAK_BYTES + lineBytes
            pending += line
        }
    }
    if (pending.isNotEmpty()) entries += pending.joinToString("\n")
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
        val codePointBytes = (index..<end).sumOf { line[it].modifiedUtf8Bytes() }
        if (pieceBytes + codePointBytes > maxBytes) {
            pieces += line.substring(start, index)
            start = index
            pieceBytes = 0
        }
        pieceBytes += codePointBytes
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
    when (code) {
        // U+0000 is left out here, because modified UTF-8 writes it as 2 bytes, so that the text
        // holds no zero byte. The next branch takes it.
        in 1..<FIRST_TWO_BYTE_CODE -> 1
        in 0..<FIRST_THREE_BYTE_CODE -> 2
        else -> MAX_UTF16_CHAR_BYTES
    }
