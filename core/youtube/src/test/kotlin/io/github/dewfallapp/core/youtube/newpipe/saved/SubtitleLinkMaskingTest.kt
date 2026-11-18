package io.github.dewfallapp.core.youtube.newpipe.saved

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** A made-up address of the machine, in a googlevideo link, as YouTube writes the real one. */
private const val MACHINE_ADDRESS = "93.184.216.34"

/** A made-up event ID, as the ei parameter of YouTube's links holds one. */
private const val EVENT_ID_VALUE = "Zv65wXyQ1aBcD9eFgH2iJ"

/** The link of the visionOS client's player request, whose answer lists the subtitle tracks. */
private const val PLAYER_URL = "https://youtubei.googleapis.com/youtubei/v1/player"

/**
 * Tests the masking of subtitle links on www.youtube.com/api/timedtext: they keep only the
 * parameters that docs/testing.md#links lists, and their ip parameter, which names no address,
 * isn't taken for the machine's.
 */
class SubtitleLinkMaskingTest {
    @Test
    fun `a subtitle link keeps only its format, variant, language and kind`() {
        val signature =
            "0123456789ABCDEF0123456789ABCDEF01234567.89ABCDEF0123456789ABCDEF0123456789ABCD"
        val link =
            "https://www.youtube.com/api/timedtext?v=R6MlUcmOul8&ei=$EVENT_ID_VALUE&caps=asr" +
                "&opi=112496729&xoaf=4&xowf=1&xospf=1&hl=en-GB&ip=0.0.0.0&ipbits=0" +
                "&expire=1763500000&sparams=ip,ipbits,expire,v,ei,caps,opi,xoaf" +
                "&signature=$signature&key=yt8&kind=asr&lang=en&variant=gemini"
        val answer =
            exchange(
                requestBody = "{}",
                responseBody = """{"baseUrl":"${link.replace("&", "\\u0026")}"}""",
            )
        val download = exchange(url = "$link&fmt=ttml", responseBody = "<tt/>")

        val masked = mask(listOf(answer, download))

        // ipbits stays, because 0 is what a masked number becomes.
        val maskedLink =
            "https://www.youtube.com/api/timedtext?v=MASKED&ei=EVENT_ID&caps=MASKED" +
                "&opi=MASKED&xoaf=MASKED&xowf=MASKED&xospf=MASKED&hl=MASKED&ip=MASKED" +
                "&ipbits=0&expire=MASKED&sparams=MASKED&signature=MASKED&key=MASKED" +
                "&kind=asr&lang=en&variant=gemini"
        assertEquals(
            """{"baseUrl":"${maskedLink.replace("&", "\\u0026")}"}""",
            masked.exchanges[0].response.body,
        )
        assertEquals("$maskedLink&fmt=ttml", masked.exchanges[1].request.url)
        assertTrue(MaskedValue.LINK_PARAMETER in unmaskedKindsIn(link))
        assertEquals(emptySet(), unmaskedKindsIn(masked.exchanges[0].response.body))
        assertEquals(emptySet(), unmaskedKindsIn(masked.exchanges[1].request.url))
    }

    @Test
    fun `the ip parameter of a subtitle link, 0_0_0_0, isn't taken for the machine's address`() {
        val body =
            """{"a":"https://www.youtube.com/api/timedtext?v=R6MlUcmOul8\\u0026ip=0.0.0.0",""" +
                """"b":"https://www.youtube.com/api/timedtext?v=R6MlUcmOul8\\u0026ip=%3A%3A",""" +
                """"c":"https://x.googlevideo.com/videoplayback?expire=1&ip=$MACHINE_ADDRESS"}"""

        val values = realValuesOf(listOf(exchange(responseBody = body)))

        assertEquals(setOf(MACHINE_ADDRESS), values.addresses)
    }

    private fun exchange(
        url: String = PLAYER_URL,
        requestBody: String? = null,
        responseBody: String,
    ) =
        SavedExchange(
            SavedRequest(if (requestBody == null) "GET" else "POST", url, emptyList(), requestBody),
            SavedResponse(200, listOf(Header(CONTENT_TYPE, "application/json")), responseBody),
        )
}
