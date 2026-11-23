package io.github.dewfallapp

import androidx.test.ext.junit.runners.AndroidJUnit4
import io.github.dewfallapp.core.model.initialOf
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import org.junit.runner.RunWith

/**
 * Checks the channel emblem's initial for names that start with an emoji of several code points, as
 * openspec/specs/player/spec.md's "A name that starts with an emoji" scenario asks (#213). The
 * video screen and the channel page both take the emblem's text from [initialOf], and the emblem
 * hides its text from the semantics tree, so the test checks [initialOf] itself.
 *
 * It runs on the emulator because [initialOf] uses java.text.BreakIterator, which Android backs
 * with its own ICU. JDK 17's BreakIterator splits flags and emoji joined by U+200D into code
 * points, so core/model's unit tests leave these cases out. The variation selector case may pass on
 * JDK 17 too, because U+FE0F is a nonspacing mark. It starts no activity and needs no Hilt.
 * docs/testing.md#what-channel-initial-checks explains it.
 */
@RunWith(AndroidJUnit4::class)
class ChannelInitialTest {
    @Test
    fun flagStaysWhole() {
        // U+1F1FA U+1F1E6, the regional indicators U and A: the flag of Ukraine.
        assertInitial(expected = "\uD83C\uDDFA\uD83C\uDDE6", name = "\uD83C\uDDFA\uD83C\uDDE6 Name")
    }

    @Test
    fun familyEmojiStaysWhole() {
        // U+1F468 man, U+200D zero width joiner, U+1F469 woman, U+200D, U+1F467 girl.
        val family = "\uD83D\uDC68\u200D\uD83D\uDC69\u200D\uD83D\uDC67"
        assertInitial(expected = family, name = "$family Name")
    }

    @Test
    fun skinToneStaysWhole() {
        // U+1F44D thumbs up, U+1F3FD medium skin tone.
        assertInitial(expected = "\uD83D\uDC4D\uD83C\uDFFD", name = "\uD83D\uDC4D\uD83C\uDFFD Name")
    }

    @Test
    fun variationSelectorStaysWhole() {
        // U+2764 heavy black heart, U+FE0F variation selector 16, which asks for the emoji style.
        assertInitial(expected = "\u2764\uFE0F", name = "\u2764\uFE0F Name")
    }

    /**
     * Checks that [name]'s initial is [expected], and that [expected] has more than one code point,
     * so a cut to the first code point fails every case.
     */
    private fun assertInitial(expected: String, name: String) {
        assertTrue(expected.codePointCount(0, expected.length) > 1)
        assertEquals(expected, initialOf(name))
    }
}
