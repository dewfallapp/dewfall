package io.github.dewfallapp.core.youtube.newpipe

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * Every language code that YouTube's answers held in a check on November 18, 2026: the 156 codes of
 * the list of languages that YouTube translates subtitles into, then the codes of the tracks of "Me
 * at the zoo", Tears of Steel and three NASA videos that weren't in that list.
 */
private val CODES_FROM_YOUTUBE: List<String> =
    listOf(
            "ab aa af ak sq am ar hy as ay az bn ba eu be bho bs br bg my ca ceb",
            "zh-Hans zh-Hant co hr cs da dv nl dz en eo et ee fo fj fil fi fr gaa",
            "gl lg ka de el gn gu ht ha haw iw hi hmn hu is ig id iu ga it ja jv kl",
            "kn kk kha km rw ko kri ku ky lo la lv ln lt lua luo lb mk mg ms ml mt",
            "gv mi mr mn mfe ne new nso no ny oc or om os pam ps fa pl pt pt-PT pa",
            "qu ro rn ru sm sg sa gd sr crs sn sd si sk sl so st es su sw ss sv tg",
            "ta tt te th bo ti to ts tn tum tr tk uk ur ug uz ve vi war cy fy wo xh",
            "yi yo zu zh-CN pt-BR es-ES",
        )
        .flatMap { it.split(' ') }

class LanguageTagsTest {
    @Test
    fun `every code that YouTube sent is already a tag, except iw, which becomes he`() {
        for (code in CODES_FROM_YOUTUBE) {
            val expected = if (code == "iw") "he" else code
            assertEquals(expected, languageTagOf(code), code)
        }
    }

    @Test
    fun `the other old ISO 639 codes become their BCP 47 tags`() {
        assertEquals("id", languageTagOf("in"))
        assertEquals("yi", languageTagOf("ji"))
    }

    @Test
    fun `a tag in another case gets BCP 47's case`() {
        assertEquals("en-GB", languageTagOf("EN-gb"))
        assertEquals("zh-Hant", languageTagOf("ZH-HANT"))
        assertEquals("es-419", languageTagOf("es-419"))
    }

    @Test
    fun `und stays und, because it is a tag`() {
        assertEquals(UNDETERMINED_LANGUAGE, languageTagOf("und"))
    }

    @Test
    fun `a code that isn't a tag gives null`() {
        assertNull(languageTagOf("en_US"))
        assertNull(languageTagOf(""))
        assertNull(languageTagOf("not a tag"))
    }
}
