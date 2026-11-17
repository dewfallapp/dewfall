package io.github.dewfallapp.core.youtube.newpipe.saved

import java.time.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

private const val PLAYER_URL = "https://www.youtube.com/youtubei/v1/player?prettyPrint=false"
private const val SW_JS_URL = "https://www.youtube.com/sw.js"

class SavedExchangesTest {
    private val content =
        SavedSetContent(
            recordedOn = LocalDate.parse("2026-11-17"),
            newPipeExtractorVersion = "v0.26.5",
            exchanges =
                listOf(
                    SavedExchange(
                        SavedRequest(
                            "GET",
                            SW_JS_URL,
                            listOf(Header("Referer", "https://www.youtube.com")),
                            body = null,
                        ),
                        SavedResponse(
                            200,
                            listOf(Header(CONTENT_TYPE, "text/javascript")),
                            "var ytcfg = {};\r\nself.x = 1;\n",
                        ),
                    ),
                    SavedExchange(
                        SavedRequest(
                            "POST",
                            PLAYER_URL,
                            listOf(
                                Header("X-YouTube-Client-Name", "1"),
                                Header("Content-Type", "application/json"),
                            ),
                            body = """{"videoId":"aqz-KE-bpKQ"}""",
                        ),
                        SavedResponse(
                            200,
                            listOf(Header(CONTENT_TYPE, "application/json; charset=UTF-8")),
                            """{"playabilityStatus":{"status":"OK"}}""",
                        ),
                    ),
                ),
        )

    @Test
    fun `a saved set reads back exactly as it was written`() {
        val files = formatSavedSet(SavedSet.SEARCH_NASA, content)

        val read = parseSavedSet(files.getValue(EXCHANGES_FILE), files::getValue)

        assertEquals(content.recordedOn, read.recordedOn)
        assertEquals(content.newPipeExtractorVersion, read.newPipeExtractorVersion)
        assertEquals(content.exchanges.size, read.exchanges.size)
        for ((written, readBack) in content.exchanges.zip(read.exchanges)) {
            assertTrue(written.request.matches(readBack.request))
            assertEquals(written.request.headers, readBack.request.headers)
            assertEquals(written.response.code, readBack.response.code)
            assertEquals(written.response.headers, readBack.response.headers)
            assertEquals(written.response.body, readBack.response.body)
        }
        assertNull(read.exchanges.first().request.body)
    }

    @Test
    fun `each body has a file named by its place and its link`() {
        val files = formatSavedSet(SavedSet.SEARCH_NASA, content)

        assertEquals(setOf(EXCHANGES_FILE, "01-sw.js", "02-player.json"), files.keys)
        assertEquals(content.exchanges[1].response.body, files.getValue("02-player.json"))
    }

    @Test
    fun `headers match in any order, and their names in any case`() {
        val sent =
            SavedRequest(
                "POST",
                PLAYER_URL,
                listOf(
                    Header("content-type", "application/json"),
                    Header("X-YouTube-Client-Name", "1"),
                ),
                body = """{"videoId":"aqz-KE-bpKQ"}""",
            )

        assertTrue(content.exchanges[1].request.matches(sent))
    }

    @Test
    fun `a request with another body, link or header value doesn't match`() {
        val saved = content.exchanges[1].request
        fun copy(
            url: String = saved.url,
            headers: List<Header> = saved.headers,
            body: String? = saved.body,
        ) = SavedRequest(saved.method, url, headers, body)

        assertFalse(saved.matches(copy(body = """{"videoId":"jNQXAC9IVRw"}""")))
        assertFalse(saved.matches(copy(url = "$PLAYER_URL&t=1")))
        assertFalse(saved.matches(copy(headers = listOf(Header("Content-Type", "text/plain")))))
    }

    @Test
    fun `the values of one header must keep their order`() {
        fun request(vararg values: String) =
            SavedRequest("GET", SW_JS_URL, values.map { Header("Accept", it) }, body = null)

        assertFalse(request("a", "b").matches(request("b", "a")))
    }
}
