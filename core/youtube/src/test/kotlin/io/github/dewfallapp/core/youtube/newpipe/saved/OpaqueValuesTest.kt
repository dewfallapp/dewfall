package io.github.dewfallapp.core.youtube.newpipe.saved

import java.util.Base64
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

// Values in the shapes that YouTube's answers carry them: NASA's public channel ID, and made-up
// tokens.
private const val CHANNEL_ID = "UCLA_DiR1FfKNvjuUpBHmylQ"
private const val TOKEN = "Zv65wXyQ1aBcD9eFgH2iJ"
private const val ACTION = "QUFFLUhqbXhNUjZrU2pJdGZ3"
private const val OTHER_TOKEN = "Xk92PqLm4RtzW0vB7nYc"

/**
 * A token whose base64 decodes to bytes with no long printable run, so nothing inside it counts.
 */
private const val ENTITY_KEY = "EgthcXotS0UtYnBLUSA-KAE"

class OpaqueValuesTest {
    @Test
    fun `random base64, hex and long numbers are opaque`() {
        val opaque =
            listOf(
                CHANNEL_ID,
                TOKEN,
                ACTION,
                "3f2a9c41b7e8d0a5",
                "12345678909876543210",
                "8a7c2f1e-0001-4c6b-9d3e-1f2a3b4c5d6e",
                "k3j9x0q2m8z7v1b4w6",
                // Base32 and base36 in one case, which can have long runs of letters.
                "JBSWY3DPEHPK3PXP",
                "nbswy3dpehpk3pxp",
                "MFRGGZDFMZTWQ2LKNNWG23TPOBYXE43U",
                "2n9c4x7hq1z8m3kbzt",
            )
        for (run in opaque) {
            assertTrue(isOpaque(run), run)
        }
    }

    @Test
    fun `words, short runs and placeholders aren't opaque`() {
        val notOpaque =
            listOf(
                "COMPACT_VIDEO_RENDERER",
                "MusicTrackVariant",
                "GetChannelPage_rid",
                "application/json",
                "VISITOR_DATA_12",
                "TOKEN_1234",
                "aqz-KE-bpKQ",
                "s88-c-k-c0x00ffffff-no-rj",
                "abcdefabcdefabcdef",
                "YoutubeChannelV2",
            )
        for (run in notOpaque) {
            assertFalse(isOpaque(run), run)
        }
    }

    @Test
    fun `a string sits under its member's name, or the name of its array, and names count too`() {
        val body =
            """{"a":{"channelId":"$CHANNEL_ID"},"ids":["$TOKEN"],"b":"$ENTITY_KEY",""" +
                """"$OTHER_TOKEN":1}"""

        assertEquals(
            listOf(
                OpaqueValue("channelId", CHANNEL_ID),
                OpaqueValue("ids", TOKEN),
                OpaqueValue("b", ENTITY_KEY),
                OpaqueValue(MEMBER_NAME, OTHER_TOKEN),
            ),
            opaqueValuesInFile("01-browse.json", body),
        )
    }

    @Test
    fun `a link's parts sit under its host, the segment before them, a name or its fragment`() {
        val body =
            """{"url":"https://$OTHER_TOKEN.example.org/$TOKEN=s88-c-k""" +
                """?sqp=$ENTITY_KEY#rs${'$'}$ACTION",""" +
                """"r":"/channel/$CHANNEL_ID"}"""

        val found = opaqueValuesInFile("01-browse.json", body)

        assertEquals(
            listOf(
                OpaqueValue(HOST, OTHER_TOKEN),
                OpaqueValue("$OTHER_TOKEN.example.org", TOKEN),
                OpaqueValue("sqp", ENTITY_KEY),
                OpaqueValue(FRAGMENT, ACTION),
                OpaqueValue("channel", CHANNEL_ID),
            ),
            found.filter { it.value != "AAE-HjmxMR6kSjItfw" },
        )
    }

    @Test
    fun `values inside URL-encoded text and base64 sit under their own names`() {
        val encodedLink =
            "https%3A%2F%2Faccounts.google.com%2Fx%3Fa%3D1%26continue_action%3D$ACTION"
        val linkInBase64 =
            Base64.getUrlEncoder()
                .withoutPadding()
                .encodeToString("https://a.org/p?t=$TOKEN".toByteArray())
        val body = """{"next":"$encodedLink","blob":"$linkInBase64"}"""

        val found = opaqueValuesInFile("01-browse.json", body)

        assertTrue(OpaqueValue("continue_action", ACTION) in found, found.toString())
        // ACTION is base64 of text, and that text is opaque too.
        assertTrue(OpaqueValue("continue_action", "AAE-HjmxMR6kSjItfw") in found, found.toString())
        assertTrue(OpaqueValue("t", TOKEN) in found, found.toString())
    }

    @Test
    fun `numbers count outside strings, but not the digits after a decimal point`() {
        val body = """{"score":0.12345678901234567,"a":1,"b":2,"id":12345678901234567890}"""

        assertEquals(
            listOf(OpaqueValue(OUTSIDE_STRINGS, "12345678901234567890")),
            opaqueValuesInFile("01-next.json", body),
        )
    }

    @Test
    fun `exchanges_txt is read line by line, with the lines of a request marked`() {
        val index =
            """
            # recorded: 2026-11-16
            > POST https://www.youtube.com/youtubei/v1/next?ctoken=$ENTITY_KEY
            > X-Token: $TOKEN
            >
            > {"cpn":"HixlqCKwYnI4sczA"}
            < 200
            <
            < @01-next.json
            """
                .trimIndent()

        assertEquals(
            listOf(
                OpaqueValue("ctoken", ENTITY_KEY, inRequest = true),
                OpaqueValue("X-Token", TOKEN, inRequest = true),
                OpaqueValue("cpn", "HixlqCKwYnI4sczA", inRequest = true),
            ),
            opaqueValuesInFile(EXCHANGES_FILE, index),
        )
    }

    @Test
    fun `a value under a kept key, or part of a kept value, may stay`() {
        val kept = keptValuesOf(listOf(exchange(answer = """{"browseId":"VL$CHANNEL_ID"}""")))

        assertEquals(setOf("VL$CHANNEL_ID"), kept)
        assertTrue(isKept(OpaqueValue("targetId", CHANNEL_ID), kept))
        assertFalse(isKept(OpaqueValue("targetId", TOKEN), kept))
    }

    @Test
    fun `a request keeps a value that no earlier answer holds, but not one that came from one`() {
        val set =
            listOf(
                exchange(request = """{"params":"$TOKEN"}""", answer = """{"p":"$OTHER_TOKEN"}"""),
                exchange(request = """{"params":"$OTHER_TOKEN"}"""),
                exchange(answer = """{"params":"$ACTION"}"""),
            )

        val kept = keptValuesOf(set)

        // NewPipeExtractor made the first, and YouTube gave the second; the third is in an answer.
        assertEquals(setOf(TOKEN), kept)
    }

    @Test
    fun `a request keeps NewPipeExtractor's tab constants that an earlier answer holds`() {
        val videosTab = NEWPIPE_EXTRACTOR_VALUES.keys.first()
        val set =
            listOf(
                exchange(answer = """{"tabs":["$videosTab","$TOKEN"]}"""),
                exchange(request = """{"params":"$videosTab"}"""),
                exchange(request = """{"params":"$TOKEN"}"""),
            )

        assertEquals(setOf(videosTab), keptValuesOf(set))
    }

    @Test
    fun `a value counts as given by an earlier answer in another encoding too`() {
        val escaped = TOKEN.substring(0, 4) + "\\u002f" + TOKEN.substring(4)
        val sent = TOKEN.substring(0, 4) + "/" + TOKEN.substring(4)
        val set =
            listOf(
                exchange(answer = """{"p":"$escaped"}"""),
                exchange(request = """{"continuation":"$sent"}"""),
            )

        assertEquals(emptySet(), keptValuesOf(set))
    }

    @Test
    fun `an opaque parameter name counts, in a link and in name=value pairs`() {
        val body = """{"url":"https://a.org/p?$TOKEN=1","pairs":"a=1&$OTHER_TOKEN=2"}"""

        val values = opaqueValuesInFile("01-next.json", body).map { it.value }

        assertTrue(TOKEN in values, values.toString())
        assertTrue(OTHER_TOKEN in values, values.toString())
    }

    @Test
    fun `a failure message prints a key only when it is made of words`() {
        assertEquals("playerMicroformatRenderer", keyForMessage("playerMicroformatRenderer"))
        assertEquals("GetPlayer_rid", keyForMessage("GetPlayer_rid"))
        assertEquals("www.youtube.com", keyForMessage("www.youtube.com"))
        assertEquals(MEMBER_NAME, keyForMessage(MEMBER_NAME))
        for (key in
            listOf(
                TOKEN,
                "rr3---sn-ab5l6nr6.googlevideo.com",
                "203.0.113.5",
                "2001:db8::1",
                "@someone",
                "JBSWY3DPEHPK3PXP",
                "a".repeat(65),
            )) {
            assertEquals(OPAQUE_KEY, keyForMessage(key), key)
        }
    }

    @Test
    fun `the decoded texts hold what URL encoding and base64 hide, and the bytes`() {
        val hidden = "secret-198.51.100.7"
        val inner = Base64.getUrlEncoder().encodeToString(byteArrayOf(1, 2) + hidden.toByteArray())
        val twiceEncoded = "x%253D" + inner.replace("=", "%253D")
        val outer =
            Base64.getEncoder()
                .encodeToString("prefix https://a.org/p?v=$twiceEncoded".toByteArray())

        val decoded = decodedTextsOf(exchange(answer = """{"blob":"$outer"}"""))

        assertTrue(decoded.texts.any { (_, text) -> hidden in text }, "text not found")
        assertTrue(
            decoded.bytes.any { String(it, Charsets.ISO_8859_1).contains(hidden) },
            "bytes not found",
        )
    }

    @Test
    fun `a value that isn't opaque on its own counts under a key that holds one that is`() {
        // Not random enough on its own, but no word either.
        val lookalike = OpaqueValue("k", "Abcdefghij1Klmnopqrs", isCertain = false)
        val found = listOf(OpaqueValue("k", TOKEN), lookalike, lookalike.copy(key = "other"))

        val certainKeys = certainKeysOf(found)

        assertTrue(countsAsOpaque(lookalike, certainKeys))
        assertFalse(countsAsOpaque(lookalike.copy(key = "other"), certainKeys))
        assertEquals(
            listOf(OpaqueValue("x", "Abcdefghij1Klmnopqrs", isCertain = false)),
            opaqueValuesInFile("01-next.json", """{"x":"Abcdefghij1Klmnopqrs"}"""),
        )
    }

    @Test
    fun `past the deepest nesting that the check reads, every run counts`() {
        // A lower-case word, URL-encoded ten times over, so that it sits deeper than the check
        // reads.
        val body = """{"x":"plainlowercasewords%${"25".repeat(9)}41"}"""

        val found = opaqueValuesInFile("01-next.json", body)

        assertTrue(OpaqueValue("x", "plainlowercasewords") in found, found.toString())
        assertFalse(isOpaque("plainlowercasewords"))
    }

    private fun exchange(request: String? = null, answer: String = "{}") =
        SavedExchange(
            SavedRequest(
                if (request == null) "GET" else "POST",
                "https://www.youtube.com/youtubei/v1/browse",
                emptyList(),
                request,
            ),
            SavedResponse(200, emptyList(), answer),
        )
}
