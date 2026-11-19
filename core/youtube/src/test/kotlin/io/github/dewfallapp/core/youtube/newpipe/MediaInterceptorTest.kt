package io.github.dewfallapp.core.youtube.newpipe

import io.github.dewfallapp.core.model.MediaRequestRefused
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.Response
import okhttp3.ResponseBody.Companion.asResponseBody
import okio.Buffer
import okio.ForwardingSource
import okio.buffer

// Made-up links in the shape of the saved sets' masked links, so no test names a real host or
// address.
private const val VISIONOS_LINK =
    "https://rr2---sn-masked.googlevideo.com/videoplayback?expire=1795048415" +
        "&ip=2001%3Adb8%3A%3A1&itag=MASKED&c=VISIONOS"
private const val OTHER_HOST_LINK = "https://rr2---sn-masked.googlevideo.com.example/videoplayback"
private const val LIVE_MANIFEST_LINK =
    "https://manifest.googlevideo.com/api/manifest/hls_variant/expire/1795048415" +
        "/ip/2001%3Adb8%3A%3A1/id/MASKED/file/index.m3u8"
private const val USER_AGENT = "a made-up visionOS User-Agent"

/** One request that the fake server got, with its body read. */
private class Sent(val request: Request, val body: ByteArray?)

/**
 * A fake googlevideo.com in place of the network: an interceptor after MediaInterceptor that
 * records each request and answers with the next response from [answers].
 */
private class FakeServer(private val answers: MutableList<(Request) -> Response>) {
    val sent = mutableListOf<Sent>()
    val closed = mutableListOf<Boolean>()

    fun client(shape: MediaRequestShape, userAgents: MediaUserAgents): OkHttpClient =
        mediaHttpClient()
            .newBuilder()
            .addInterceptor(MediaInterceptor(shape, userAgents))
            .addInterceptor { chain ->
                val request = chain.request()
                val body = request.body?.let { Buffer().also(it::writeTo).readByteArray() }
                sent += Sent(request, body)
                answers.removeAt(0)(request)
            }
            .build()

    /** An answer with [code], an optional [location], and a body whose closing is recorded. */
    fun answer(code: Int, location: String? = null): (Request) -> Response = { request ->
        val index = closed.size
        closed += false
        val source =
            object : ForwardingSource(Buffer()) {
                override fun close() {
                    closed[index] = true
                    super.close()
                }
            }
        Response.Builder()
            .request(request)
            .protocol(Protocol.HTTP_1_1)
            .code(code)
            .message("")
            .apply { if (location != null) header("Location", location) }
            .body(source.buffer().asResponseBody(null, 0))
            .build()
    }
}

private fun knownUserAgents() = MediaUserAgents().apply { rememberVisionOs(USER_AGENT) }

private fun get(link: String) = Request.Builder().url(link).header("Range", "bytes=0-1023").build()

class MediaInterceptorTest {
    @Test
    fun `a progressive request is a POST with NewPipe's shape and the visionOS User-Agent`() {
        val answers = mutableListOf<(Request) -> Response>()
        val fake = FakeServer(answers)
        answers += fake.answer(206)

        fake
            .client(MediaRequestShape.PROGRESSIVE, knownUserAgents())
            .newCall(get(VISIONOS_LINK))
            .execute()
            .close()

        val sent = fake.sent.single()
        assertEquals("POST", sent.request.method)
        assertContentEquals(byteArrayOf(0x78, 0), sent.body)
        assertEquals("bytes=0-1023", sent.request.header("Range"))
        assertEquals("trailers", sent.request.header("TE"))
        assertEquals("en-GB", sent.request.header("Accept-Language"))
        assertEquals(USER_AGENT, sent.request.header("User-Agent"))
        assertEquals("0", sent.request.url.queryParameter("rn"))
    }

    @Test
    fun `rn counts from 0 for each interceptor, and a link's own rn stays`() {
        val answers = mutableListOf<(Request) -> Response>()
        val fake = FakeServer(answers)
        repeat(3) { answers += fake.answer(206) }
        val client = fake.client(MediaRequestShape.PROGRESSIVE, knownUserAgents())

        client.newCall(get(VISIONOS_LINK)).execute().close()
        client.newCall(get(VISIONOS_LINK)).execute().close()
        client.newCall(get("$VISIONOS_LINK&rn=7")).execute().close()

        assertEquals(
            listOf("0", "1", "7"),
            fake.sent.map { it.request.url.queryParameter("rn") },
        )
    }

    @Test
    fun `http and other hosts are refused before anything is sent`() {
        val fake = FakeServer(mutableListOf())
        val client = fake.client(MediaRequestShape.PROGRESSIVE, knownUserAgents())

        val http =
            assertFailsWith<MediaRequestRefused> {
                client.newCall(get(VISIONOS_LINK.replace("https:", "http:"))).execute()
            }
        val otherHost =
            assertFailsWith<MediaRequestRefused> {
                client.newCall(get("$OTHER_HOST_LINK?c=VISIONOS")).execute()
            }

        assertEquals(MediaRequestRefused.Reason.NOT_HTTPS, http.reason)
        assertEquals(MediaRequestRefused.Reason.HOST_NOT_ALLOWED, otherHost.reason)
        assertTrue(fake.sent.isEmpty())
        assertFalse(http.message.orEmpty().contains("googlevideo"))
    }

    @Test
    fun `a redirect to an allowed host keeps the User-Agent and closes the redirect`() {
        val answers = mutableListOf<(Request) -> Response>()
        val fake = FakeServer(answers)
        answers += fake.answer(302, "https://rr5---sn-masked.googlevideo.com/videoplayback?x=1")
        answers += fake.answer(206)

        fake
            .client(MediaRequestShape.PROGRESSIVE, knownUserAgents())
            .newCall(get(VISIONOS_LINK))
            .execute()
            .close()

        val (first, second) = fake.sent
        assertEquals("POST", first.request.method)
        assertEquals("GET", second.request.method)
        assertNull(second.body)
        assertEquals(USER_AGENT, second.request.header("User-Agent"))
        assertEquals("rr5---sn-masked.googlevideo.com", second.request.url.host)
        assertEquals(listOf(true, true), fake.closed)
    }

    @Test
    fun `a 307 keeps the POST and its body, and a 300 becomes a GET`() {
        val answers = mutableListOf<(Request) -> Response>()
        val fake = FakeServer(answers)
        answers += fake.answer(307, VISIONOS_LINK)
        answers += fake.answer(300, VISIONOS_LINK)
        answers += fake.answer(206)

        fake
            .client(MediaRequestShape.PROGRESSIVE, knownUserAgents())
            .newCall(get(VISIONOS_LINK))
            .execute()
            .close()

        assertEquals(listOf("POST", "POST", "GET"), fake.sent.map { it.request.method })
        assertContentEquals(byteArrayOf(0x78, 0), fake.sent[1].body)
    }

    @Test
    fun `a redirect to another host is refused, and its response is closed`() {
        val answers = mutableListOf<(Request) -> Response>()
        val fake = FakeServer(answers)
        answers += fake.answer(302, OTHER_HOST_LINK)

        val refused =
            assertFailsWith<MediaRequestRefused> {
                fake
                    .client(MediaRequestShape.PROGRESSIVE, knownUserAgents())
                    .newCall(get(VISIONOS_LINK))
                    .execute()
            }

        assertEquals(MediaRequestRefused.Reason.HOST_NOT_ALLOWED, refused.reason)
        assertEquals(1, fake.sent.size)
        assertEquals(listOf(true), fake.closed)
    }

    @Test
    fun `20 redirects are followed, and the 21st is refused`() {
        val answers = mutableListOf<(Request) -> Response>()
        val fake = FakeServer(answers)
        repeat(MAX_MEDIA_REDIRECTS) { answers += fake.answer(302, VISIONOS_LINK) }
        answers += fake.answer(206)
        val client = fake.client(MediaRequestShape.PROGRESSIVE, knownUserAgents())

        client.newCall(get(VISIONOS_LINK)).execute().close()
        repeat(MAX_MEDIA_REDIRECTS + 1) { answers += fake.answer(302, VISIONOS_LINK) }
        val refused =
            assertFailsWith<MediaRequestRefused> { client.newCall(get(VISIONOS_LINK)).execute() }

        assertEquals(MediaRequestRefused.Reason.TOO_MANY_REDIRECTS, refused.reason)
    }

    @Test
    fun `a redirect without a Location comes back as it is`() {
        val answers = mutableListOf<(Request) -> Response>()
        val fake = FakeServer(answers)
        answers += fake.answer(302)

        val response =
            fake
                .client(MediaRequestShape.PROGRESSIVE, knownUserAgents())
                .newCall(get(VISIONOS_LINK))
                .execute()

        assertEquals(302, response.code)
        response.close()
    }

    @Test
    fun `a progressive link of another client, or before any stream links call, is refused`() {
        val fake = FakeServer(mutableListOf())

        val otherClient =
            assertFailsWith<MediaRequestRefused> {
                fake
                    .client(MediaRequestShape.PROGRESSIVE, knownUserAgents())
                    .newCall(get(VISIONOS_LINK.replace("c=VISIONOS", "c=WEB")))
                    .execute()
            }
        val noUserAgent =
            assertFailsWith<MediaRequestRefused> {
                fake
                    .client(MediaRequestShape.PROGRESSIVE, MediaUserAgents())
                    .newCall(get(VISIONOS_LINK))
                    .execute()
            }

        assertEquals(MediaRequestRefused.Reason.UNKNOWN_CLIENT, otherClient.reason)
        assertEquals(MediaRequestRefused.Reason.UNKNOWN_CLIENT, noUserAgent.reason)
        assertTrue(fake.sent.isEmpty())
    }

    @Test
    fun `a live manifest link is a plain GET with the layer's own User-Agent`() {
        val answers = mutableListOf<(Request) -> Response>()
        val fake = FakeServer(answers)
        answers += fake.answer(200)

        fake
            .client(MediaRequestShape.LIVE, MediaUserAgents())
            .newCall(Request.Builder().url(LIVE_MANIFEST_LINK).build())
            .execute()
            .close()

        val sent = fake.sent.single()
        assertEquals("GET", sent.request.method)
        assertEquals(DEFAULT_USER_AGENT, sent.request.header("User-Agent"))
        assertEquals("en-GB", sent.request.header("Accept-Language"))
        assertNull(sent.request.header("TE"))
        assertNull(sent.request.url.queryParameter("rn"))
    }

    @Test
    fun `the visionOS check parses the c parameter`() {
        val base = "https://rr2---sn-masked.googlevideo.com/videoplayback"

        assertTrue(isVisionOsLink("$base?c=VISIONOS&itag=MASKED".toHttpUrl()))
        assertTrue(isVisionOsLink("$base?itag=MASKED&c=VISIONOS".toHttpUrl()))
        assertFalse(isVisionOsLink("$base?itag=MASKED&c=VISIONOSX".toHttpUrl()))
        assertFalse(isVisionOsLink("$base?itag=MASKED".toHttpUrl()))
    }
}
