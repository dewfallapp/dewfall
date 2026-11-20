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

/** How YouTube ends the text of a link that it shortened: three full stops, or an ellipsis. */
private val SHORTENED_ENDINGS = listOf("...", "\u2026")

/** How the text of a link that YouTube shortened starts. */
private val ADDRESS_STARTS = listOf("http://", "https://")

/**
 * The Unicode categories that no address that opens a page holds: spaces of any kind, line and
 * paragraph separators, control characters, and format characters, which are invisible or reorder
 * the text around them.
 */
private val CATEGORIES_NOT_IN_ADDRESSES =
    setOf(
        Character.SPACE_SEPARATOR,
        Character.LINE_SEPARATOR,
        Character.PARAGRAPH_SEPARATOR,
        Character.CONTROL,
        Character.FORMAT,
    )

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
 * newline, and a link keeps its text, unless YouTube shortened it: then the link shows its whole
 * address, as [wholeAddress] decides.
 *
 * NewPipeExtractor writes some descriptions without escaping their text, so a `<` that doesn't
 * start one of its own tags is read as text. Text that looks exactly like one of those tags, such
 * as `<b>`, can't be told apart from them, and an unescaped entity such as `&amp;` is decoded.
 */
internal fun htmlDescriptionToText(html: String): String {
    val escaped = STRAY_LESS_THAN.replace(html, ESCAPED_LESS_THAN)
    val body = Jsoup.parseBodyFragment(escaped).body()
    for (link in body.select("a[href]")) {
        val address = wholeAddress(link.wholeText(), link.attr("href"))
        if (address != null) link.text(address)
    }
    return body.wholeText().replace(NO_BREAK_SPACE, ' ')
}

/**
 * The whole address of a link whose [text] YouTube shortened, which is the link's [address], or
 * null when the text isn't a shortened form of that address.
 *
 * As docs/architecture.md's "Descriptions" section says, with its sources, YouTube ends the text of
 * a long link with "...", after the start of the address, and NewPipeExtractor v0.27.1 puts the
 * whole address in the link, from YouTube's redirect link. The text counts as shortened only when
 * it starts with `http://` or `https://`, so a title that ends with "..." stays, and when the
 * address is longer and starts with the text before the "...", so the two are the same address. An
 * address that [opensAPage] refuses keeps the shortened text.
 */
private fun wholeAddress(text: String, address: String): String? {
    val ending = SHORTENED_ENDINGS.firstOrNull { text.endsWith(it) } ?: return null
    val shown = text.removeSuffix(ending)
    val isShortenedAddress =
        ADDRESS_STARTS.any { shown.startsWith(it) } &&
            address.length > shown.length &&
            address.startsWith(shown)
    return address.takeIf { isShortenedAddress && opensAPage(address) }
}

/**
 * Whether [address] holds no space, line break, control character or format character. One that
 * does wouldn't open a page, or it could read differently on screen from the one the user copies.
 */
private fun opensAPage(address: String): Boolean =
    address.codePoints().noneMatch { Character.getType(it).toByte() in CATEGORIES_NOT_IN_ADDRESSES }
