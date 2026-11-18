package io.github.dewfallapp.core.youtube.newpipe

import org.jsoup.Jsoup
import org.schabi.newpipe.extractor.stream.Description

/**
 * A `<` that doesn't start one of the tags that NewPipeExtractor writes into a description: a link,
 * bold, italic and struck-through text, and a line break.
 */
private val STRAY_LESS_THAN = Regex("""<(?!(?:a href="[^"<>]*"|/a|/?[bis]|br)>)""")

/** The escaped form of `<`, which HTML reads as text. */
private const val ESCAPED_LESS_THAN = "&lt;"

/** NewPipeExtractor keeps a run of spaces as a space followed by no-break spaces. */
private const val NO_BREAK_SPACE = '\u00A0'

/** This description as plain text. */
internal fun Description.toPlainText(): String =
    when (type) {
        Description.Type.HTML -> htmlDescriptionToText(content)
        // Plain text, and Markdown, which YouTube doesn't send, are already text.
        Description.Type.PLAIN_TEXT,
        Description.Type.MARKDOWN -> content
    }

/**
 * The text of [html], a description that NewPipeExtractor wrote as HTML. A line break becomes a
 * newline, and a link keeps only its text.
 *
 * NewPipeExtractor writes some descriptions without escaping their text, so a `<` that doesn't
 * start one of its own tags is read as text. Text that looks exactly like one of those tags, such
 * as `<b>`, can't be told apart from them, and an unescaped entity such as `&amp;` is decoded.
 */
internal fun htmlDescriptionToText(html: String): String {
    val escaped = STRAY_LESS_THAN.replace(html, ESCAPED_LESS_THAN)
    return Jsoup.parseBodyFragment(escaped).body().wholeText().replace(NO_BREAK_SPACE, ' ')
}
