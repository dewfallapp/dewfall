package io.github.dewfallapp.core.youtube.newpipe

import io.github.dewfallapp.core.model.SubtitleLine
import kotlin.time.Duration
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds
import org.jsoup.Jsoup
import org.jsoup.nodes.Element
import org.jsoup.nodes.TextNode
import org.jsoup.parser.Parser

/** The namespace of TTML's elements, which the root element of a TTML file names. */
private const val TTML_NAMESPACE = "http://www.w3.org/ns/ttml"

/** The name of a TTML file's root element. */
private const val TTML_ROOT = "tt"

/** The element that breaks a line inside a cue. */
private const val LINE_BREAK = "br"

/**
 * A clock time as YouTube writes it in TTML, such as 00:01:02.345: hours, minutes, seconds, and up
 * to three digits of a second.
 */
private val CLOCK_TIME = Regex("""(\d{2,3}):([0-5]\d):([0-5]\d)(?:\.(\d{1,3}))?""")

/** The digits of milliseconds, to which a shorter fraction of a second is padded. */
private const val MILLISECOND_DIGITS = 3

/** A run of white space in a cue's text, no-break spaces included, which becomes one space. */
private val WHITE_SPACE = Regex("""[\s\u00A0]+""")

/**
 * Thrown when a subtitle answer isn't a TTML file: an empty answer, an HTML page, a file in another
 * format, or one with a document type declaration, which YouTube's TTML never has. subtitleLinesOf
 * also throws it for a status that names no other category. It maps to BROKEN, because only an
 * update can make the layer read another kind of answer.
 */
internal class NotSubtitlesException : Exception("YouTube's answer is not a TTML subtitle file")

/**
 * The lines of [ttml], a subtitle track in TTML as YouTube sends it, sorted by their start. Each
 * `p` in the body is one line, with its `begin` and `end` times kept as YouTube gives them, so the
 * lines of an auto-generated track overlap, as YouTube shows two of them at once.
 *
 * Its text is plain: character references decoded, styling left out, white space collapsed to
 * single spaces, and each `br` a line break. A line with no text is left out. A line whose times
 * are missing or can't be read, or whose end isn't after its start, is left out and counted as
 * dropped in [skipped].
 *
 * Throws [NotSubtitlesException] for an answer that isn't a TTML file. jsoup never reads a document
 * type declaration or an external entity, and an answer with a declaration is refused. See
 * docs/architecture.md#subtitles.
 */
internal fun ttmlLinesOf(ttml: String, skipped: SkippedParts): List<SubtitleLine> {
    if (ttml.isBlank()) throw NotSubtitlesException()
    val document = Jsoup.parse(ttml, "", Parser.xmlParser())
    val root = document.children().firstOrNull()
    val isTtml =
        document.documentType() == null &&
            root != null &&
            root.tagName() == TTML_ROOT &&
            root.attr("xmlns") == TTML_NAMESPACE
    if (!isTtml) throw NotSubtitlesException()
    val lines = mutableListOf<SubtitleLine>()
    for (paragraph in root.select("body p")) {
        val text = textOf(paragraph)
        if (text.isNotEmpty()) {
            skipped.addOrDrop(lines, lineOrNull(paragraph, text))
        }
    }
    return lines.sortedBy { it.start }
}

/** The line that [paragraph] holds, with [text], or null if its times are missing or wrong. */
private fun lineOrNull(paragraph: Element, text: String): SubtitleLine? {
    val start = clockTimeOrNull(paragraph.attr("begin"))
    val end = clockTimeOrNull(paragraph.attr("end"))
    return if (start != null && end != null && end > start) SubtitleLine(start, end, text) else null
}

/** [value] read as a [CLOCK_TIME], or null if it isn't one. */
private fun clockTimeOrNull(value: String): Duration? {
    val match = CLOCK_TIME.matchEntire(value.trim()) ?: return null
    val (hours, minutes, seconds) = match.destructured
    // The fraction of a second, the last group, is empty when the time has none.
    val fraction = match.groupValues.last()
    return hours.toLong().hours +
        minutes.toLong().minutes +
        seconds.toLong().seconds +
        fraction.padEnd(MILLISECOND_DIGITS, '0').toLong().milliseconds
}

/**
 * The text of [paragraph] as plain text: each run of white space becomes one space, each `br` a
 * line break, and every other element gives only its text. Each line is trimmed, and empty lines
 * are left out. jsoup has decoded the character references already, and it walks the elements
 * without recursion, so a deeply nested answer can't overflow the stack.
 */
private fun textOf(paragraph: Element): String {
    val text = StringBuilder()
    paragraph.traverse { node, _ ->
        when {
            node is TextNode -> text.append(WHITE_SPACE.replace(node.wholeText, " "))
            node is Element && node.normalName() == LINE_BREAK -> text.append('\n')
        }
    }
    // A run of white space can span the end of one element and the start of the next, so each row
    // is collapsed again once it is whole.
    return text
        .lines()
        .map { WHITE_SPACE.replace(it, " ").trim() }
        .filter { it.isNotEmpty() }
        .joinToString("\n")
}
