package io.github.dewfallapp.core.model

import java.text.BreakIterator
import java.util.Locale

/**
 * The first user-perceived character of [name], after any leading whitespace, as it is written, or
 * null when [name] has nothing else. A channel's emblem shows it. On a phone, BreakIterator follows
 * Unicode's grapheme rules, so a flag or an emoji with a skin tone stays whole. Locale.ROOT keeps
 * the result from depending on the phone's language.
 */
fun initialOf(name: String): String? {
    val text = name.trimStart()
    if (text.isEmpty()) return null
    val characters = BreakIterator.getCharacterInstance(Locale.ROOT)
    characters.setText(text)
    return text.substring(0, characters.next())
}
