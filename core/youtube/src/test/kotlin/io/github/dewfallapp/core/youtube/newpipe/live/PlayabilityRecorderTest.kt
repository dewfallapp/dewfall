package io.github.dewfallapp.core.youtube.newpipe.live

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody

/** The visionOS client's player request goes to this host and path. */
private const val VISIONOS_PLAYER = "https://youtubei.googleapis.com/youtubei/v1/player"

/** The web client's player request, which NewPipeExtractor doesn't check for playability. */
private const val WEB_PLAYER = "https://www.youtube.com/youtubei/v1/player"

class PlayabilityRecorderTest {
    private val recorder = PlayabilityRecorder()

    @Test
    fun `each status that NewPipeExtractor compares keeps its name, in any case`() {
        val statuses =
            listOf("OK", "LOGIN_REQUIRED", "UNPLAYABLE", "ERROR", "unplayable").map {
                answerTo(VISIONOS_PLAYER, """{"playabilityStatus":{"status":"$it"}}""")
                recorder.take()?.status
            }

        assertEquals(
            listOf(
                PlayabilityStatus.OK,
                PlayabilityStatus.LOGIN_REQUIRED,
                PlayabilityStatus.UNPLAYABLE,
                PlayabilityStatus.ERROR,
                PlayabilityStatus.UNPLAYABLE,
            ),
            statuses,
        )
    }

    @Test
    fun `any other status is OTHER`() {
        answerTo(VISIONOS_PLAYER, """{"playabilityStatus":{"status":"SOMETHING_NEW"}}""")

        assertEquals(PlayabilityStatus.OTHER, recorder.take()?.status)
    }

    @Test
    fun `JSON with no status string is STATUS_MISSING`() {
        val bodies =
            listOf(
                """{"responseContext":{}}""",
                """{"playabilityStatus":{"reason":"r"}}""",
                """{"playabilityStatus":{"status":7}}""",
                """[]""",
            )

        for (body in bodies) {
            answerTo(VISIONOS_PLAYER, body)
            assertEquals(PlayabilityStatus.STATUS_MISSING, recorder.take()?.status, body)
        }
    }

    @Test
    fun `an answer that isn't JSON is NOT_JSON, with no reasonGiven`() {
        answerTo(VISIONOS_PLAYER, "<html><title>Sorry...</title></html>", code = 403)

        assertEquals(PlayabilityAnswer(PlayabilityStatus.NOT_JSON, null), recorder.take())
    }

    @Test
    fun `a reason string is GIVEN, and no reason or another value is MISSING`() {
        val reasons =
            listOf(
                    """{"playabilityStatus":{"status":"UNPLAYABLE","reason":"r"}}""",
                    """{"playabilityStatus":{"status":"UNPLAYABLE"}}""",
                    """{"playabilityStatus":{"status":"UNPLAYABLE","reason":{"runs":[]}}}""",
                )
                .map {
                    answerTo(VISIONOS_PLAYER, it)
                    recorder.take()?.reasonGiven
                }

        assertEquals(listOf(ReasonGiven.GIVEN, ReasonGiven.MISSING, ReasonGiven.MISSING), reasons)
    }

    @Test
    fun `other requests are read by no one but the next interceptor`() {
        answerTo(WEB_PLAYER, """{"playabilityStatus":{"status":"ERROR"}}""")
        answerTo("https://www.youtube.com/youtubei/v1/browse", "{}")

        assertNull(recorder.take())
    }

    @Test
    fun `only the first visionOS answer since take counts, and take forgets it`() {
        answerTo(VISIONOS_PLAYER, """{"playabilityStatus":{"status":"ERROR"}}""")
        answerTo(VISIONOS_PLAYER, """{"playabilityStatus":{"status":"OK"}}""")

        assertEquals(PlayabilityStatus.ERROR, recorder.take()?.status)
        assertNull(recorder.take())
    }

    @Test
    fun `the whole body still reaches whoever made the request`() {
        val body = """{"playabilityStatus":{"status":"OK"},"streamingData":{}}"""

        assertEquals(body, answerTo(VISIONOS_PLAYER, body))
    }

    /**
     * Sends a POST to [url] through the recorder, answered with [body], and returns the body read.
     */
    private fun answerTo(url: String, body: String, code: Int = 200): String {
        val client =
            OkHttpClient.Builder()
                .addInterceptor(recorder)
                .addInterceptor { chain -> answer(chain.request(), code, body) }
                .build()
        val request = Request.Builder().url(url).post("{}".toRequestBody()).build()
        return client.newCall(request).execute().use { it.body.string() }
    }
}

/** An answer to [request] with [code] and [body]. */
private fun answer(request: Request, code: Int, body: String): Response =
    Response.Builder()
        .request(request)
        .protocol(Protocol.HTTP_1_1)
        .code(code)
        .message("")
        .body(body.toResponseBody("application/json".toMediaType()))
        .build()
