package io.github.dewfallapp.feature.player.logic

import io.github.dewfallapp.core.model.YouTubeError
import java.text.BreakIterator
import java.util.Locale

/**
 * Whether a failure with [error] offers "Try again". NETWORK, BLOCKED and BROKEN mean that nothing
 * from YouTube can load right now, so they do. Only an app update fixes BROKEN, but a user can't
 * tell it from the other two. The other errors are about this one video, and asking again gives the
 * same answer.
 */
fun offersTryAgain(error: YouTubeError): Boolean =
    when (error) {
        YouTubeError.NETWORK,
        YouTubeError.BLOCKED,
        YouTubeError.BROKEN -> true
        YouTubeError.PRIVATE,
        YouTubeError.AGE_RESTRICTED,
        YouTubeError.COUNTRY_BLOCKED,
        YouTubeError.MEMBERS_OR_PAID,
        YouTubeError.CHANNEL_CLOSED,
        YouTubeError.UNAVAILABLE -> false
    }

/**
 * The first user-perceived character of [name], after any leading whitespace, as it is written, or
 * null when [name] has nothing else. On a phone, BreakIterator follows Unicode's grapheme rules, so
 * a flag or an emoji with a skin tone stays whole. Locale.ROOT keeps the result from depending on
 * the phone's language.
 */
fun initialOf(name: String): String? {
    val text = name.trimStart()
    if (text.isEmpty()) return null
    val characters = BreakIterator.getCharacterInstance(Locale.ROOT)
    characters.setText(text)
    return text.substring(0, characters.next())
}
