package io.github.dewfallapp.core.youtube.newpipe.live

import io.github.dewfallapp.core.youtube.newpipe.recordingLogger
import java.io.IOException
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.schabi.newpipe.extractor.services.youtube.YoutubeParsingHelper

/** A made-up link of YouTube's visionOS client, the one that every link comes from. */
private const val VISIONOS_LINK =
    "https://rr1---sn-test.googlevideo.com/videoplayback?itag=140&c=VISIONOS"

/** The length of the made-up format, in bytes. */
private const val LENGTH = 5000L

class FirstBytesTest {
    private val sent = mutableListOf<Request>()
    private val lines = mutableListOf<String>()

    @Test
    fun `a 206 with the format's length as the total is playable`() {
        val result = firstBytes { partial(it) }.fetch(VISIONOS_LINK, LENGTH)

        assertEquals(FirstBytesAnswer.PLAYABLE, result.answer)
        assertEquals(LinkClient.VISIONOS, result.client)
        assertEquals(206, result.status)
        val request = sent.single()
        assertEquals("GET", request.method)
        assertEquals(VISIONOS_LINK, request.url.toString())
        assertEquals("bytes=0-1023", request.header("Range"))
        assertEquals(YoutubeParsingHelper.getVisionOsUserAgent(null), request.header("User-Agent"))
        assertEquals(emptyList(), lines)
    }

    @Test
    fun `a link of another client is refused, and nothing is sent`() {
        // The Android and iOS clients made links until NewPipeExtractor v0.27.0 removed them.
        for (client in listOf("WEB", "ANDROID", "IOS")) {
            val result = firstBytes { partial(it) }.fetch(link(client = client), LENGTH)

            assertEquals(FirstBytesAnswer.UNKNOWN_CLIENT, result.answer, client)
            assertEquals(LinkClient.OTHER, result.client, client)
        }
        assertEquals(emptyList(), sent)
    }

    @Test
    fun `any status code but 206 is not playable, and names the code`() {
        for (code in listOf(200, 403, 404)) {
            val result = firstBytes { answer(it, code) }.fetch(VISIONOS_LINK, LENGTH)

            assertEquals(FirstBytesAnswer.WRONG_STATUS, result.answer)
            assertEquals(code, result.status)
        }
    }

    @Test
    fun `a total length other than the format's is WRONG_LENGTH`() {
        val wrongTotal = firstBytes { partial(it, total = "4999") }.fetch(VISIONOS_LINK, LENGTH)
        val unknownTotal = firstBytes { partial(it, total = "*") }.fetch(VISIONOS_LINK, LENGTH)
        val noRange = firstBytes { answer(it, 206, ByteArray(1024)) }.fetch(VISIONOS_LINK, LENGTH)

        assertEquals(FirstBytesAnswer.WRONG_LENGTH, wrongTotal.answer)
        assertEquals(FirstBytesAnswer.WRONG_LENGTH, unknownTotal.answer)
        assertEquals(FirstBytesAnswer.WRONG_LENGTH, noRange.answer)
    }

    @Test
    fun `fewer bytes than asked for is SHORT_BODY`() {
        val result = firstBytes { partial(it, bytes = 100) }.fetch(VISIONOS_LINK, LENGTH)

        assertEquals(FirstBytesAnswer.SHORT_BODY, result.answer)
    }

    @Test
    fun `a file shorter than a KiB is playable when all of it comes back`() {
        val result = firstBytes {
            partial(it, bytes = 100, total = "100")
        }
            .fetch(VISIONOS_LINK, 100)

        assertEquals(FirstBytesAnswer.PLAYABLE, result.answer)
    }

    @Test
    fun `a redirect to googlevideo com is followed with the same headers`() {
        val other = link(host = "rr2---sn-other.googlevideo.com")
        val result = firstBytes {
            if (it.url.toString() == VISIONOS_LINK) redirect(it, other) else partial(it)
        }
            .fetch(VISIONOS_LINK, LENGTH)

        assertEquals(FirstBytesAnswer.PLAYABLE, result.answer)
        assertEquals(listOf(VISIONOS_LINK, other), sent.map { it.url.toString() })
        assertEquals(1, sent.map { it.header("User-Agent") }.toSet().size)
        assertEquals(listOf("bytes=0-1023", "bytes=0-1023"), sent.map { it.header("Range") })
    }

    @Test
    fun `a redirect to another host is refused without sending anything there`() {
        val result = firstBytes {
            redirect(it, "https://example.com/videoplayback?c=VISIONOS")
        }
            .fetch(VISIONOS_LINK, LENGTH)

        assertEquals(FirstBytesAnswer.HOST_NOT_ALLOWED, result.answer)
        assertEquals(listOf(VISIONOS_LINK), sent.map { it.url.toString() })
    }

    @Test
    fun `a redirect without a Location header is WRONG_STATUS`() {
        val result = firstBytes { answer(it, 302) }.fetch(VISIONOS_LINK, LENGTH)

        assertEquals(FirstBytesAnswer.WRONG_STATUS, result.answer)
        assertEquals(302, result.status)
    }

    @Test
    fun `a link that isn't HTTPS to googlevideo com is refused, and nothing is sent`() {
        val links =
            listOf(
                VISIONOS_LINK.replace("https://", "http://"),
                link(host = "googlevideo.com.example.com"),
                link(host = "notgooglevideo.com"),
                "not a link&c=VISIONOS",
            )

        for (link in links) {
            val result = firstBytes { partial(it) }.fetch(link, LENGTH)

            assertEquals(FirstBytesAnswer.HOST_NOT_ALLOWED, result.answer, link)
            assertNull(result.status)
        }
        assertEquals(emptyList(), sent)
    }

    @Test
    fun `more than three redirects is TOO_MANY_REDIRECTS`() {
        var hop = 0
        val result = firstBytes {
            redirect(it, link(host = "rr${++hop}---sn-hop.googlevideo.com"))
        }
            .fetch(VISIONOS_LINK, LENGTH)

        assertEquals(FirstBytesAnswer.TOO_MANY_REDIRECTS, result.answer)
        assertEquals(302, result.status)
        // The link, then three redirects. The fourth redirect isn't followed.
        assertEquals(4, sent.size)
    }

    @Test
    fun `a request that fails is NETWORK, and the log line holds no message`() {
        val message = "Failed to connect to rr1---sn-test.googlevideo.com/203.0.113.7"
        val result = firstBytes { throw IOException(message) }.fetch(VISIONOS_LINK, LENGTH)

        assertEquals(FirstBytesAnswer.NETWORK, result.answer)
        assertEquals(
            listOf("WARN Dewfall.FirstBytes A request for a link's first bytes failed"),
            lines,
        )
        assertTrue(lines.none { "googlevideo" in it || "203.0.113" in it })
    }

    private fun firstBytes(answer: (Request) -> Response): FirstBytes =
        FirstBytes(
            firstBytesClient()
                .newBuilder()
                .addInterceptor { chain ->
                    sent += chain.request()
                    answer(chain.request())
                }
                .build(),
            recordingLogger(lines),
        )
}

/** A made-up link on [host] of the YouTube client [client]. */
private fun link(host: String = "rr1---sn-test.googlevideo.com", client: String = "VISIONOS") =
    "https://$host/videoplayback?itag=140&c=$client"

/** A 206 answer to [request] with [bytes] bytes of a file whose total length is [total]. */
private fun partial(request: Request, bytes: Int = 1024, total: String = "$LENGTH"): Response =
    answer(request, 206, ByteArray(bytes), "Content-Range" to "bytes 0-${bytes - 1}/$total")

/** A redirect from [request] to [location]. */
private fun redirect(request: Request, location: String): Response =
    answer(request, 302, ByteArray(0), "Location" to location)

/** An answer to [request] with [code], [body] and [headers]. */
private fun answer(
    request: Request,
    code: Int,
    body: ByteArray = ByteArray(0),
    vararg headers: Pair<String, String>,
): Response =
    Response.Builder()
        .request(request)
        .protocol(Protocol.HTTP_1_1)
        .code(code)
        .message("")
        .apply {
            for ((name, value) in headers) {
                header(name, value)
            }
        }
        .body(body.toResponseBody())
        .build()
