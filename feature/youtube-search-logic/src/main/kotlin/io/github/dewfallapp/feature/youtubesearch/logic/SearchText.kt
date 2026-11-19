package io.github.dewfallapp.feature.youtubesearch.logic

/** A line break of any kind: Windows' CR LF, an old Mac's CR, or LF. */
private val LINE_BREAK = Regex("\\r\\n|\\r|\\n")

/**
 * Where [text] holds line breaks, last first, each a CR LF pair or a single CR or LF. Replacing
 * them from the last one keeps the earlier ranges right, and replacing each on its own keeps a
 * cursor outside them where it was.
 */
fun lineBreaksFromLast(text: CharSequence): List<IntRange> =
    LINE_BREAK.findAll(text).map { it.range }.toList().asReversed()
