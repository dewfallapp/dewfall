package io.github.dewfallapp.core.youtube.newpipe

import java.io.IOException
import java.io.InterruptedIOException
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference
import kotlin.concurrent.thread
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue
import okhttp3.Call
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.ResponseBody.Companion.toResponseBody
import okio.Buffer
import org.schabi.newpipe.extractor.downloader.Request
import org.schabi.newpipe.extractor.exceptions.ReCaptchaException

private const val SEARCH_URL = "https://www.youtube.com/youtubei/v1/search?prettyPrint=false"

/** How long a test waits for another thread before it fails. */
private const val WAIT_SECONDS = 5L

class NewPipeDownloaderTest {
    /** Every request that reached the fake server, in order. */
    private val sent = mutableListOf<okhttp3.Request>()

    @AfterTest
    fun clearInterrupt() {
        // A test that interrupts this thread must not leave it interrupted for the next test.
        Thread.interrupted()
    }

    @Test
    fun `sends the method, link, headers and body`() {
        val downloader = NewPipeDownloader(clientAnswering { answer(it) })
        val request =
            Request.newBuilder()
                .post(SEARCH_URL, """{"query":"q"}""".toByteArray())
                .setHeader("Content-Type", "application/json")
                .setHeaders("X-Test", listOf("first", "second"))
                .build()

        downloader.execute(request)

        val sentRequest = sent.single()
        assertEquals("POST", sentRequest.method)
        assertEquals(SEARCH_URL, sentRequest.url.toString())
        assertEquals("application/json", sentRequest.header("Content-Type"))
        assertEquals(listOf("first", "second"), sentRequest.headers("X-Test"))
        val body = Buffer().also { sentRequest.body!!.writeTo(it) }.readUtf8()
        assertEquals("""{"query":"q"}""", body)
    }

    @Test
    fun `sends a desktop browser's User-Agent when NewPipeExtractor sets none`() {
        val downloader = NewPipeDownloader(clientAnswering { answer(it) })

        downloader.execute(Request.newBuilder().get(SEARCH_URL).build())

        assertTrue(sent.single().header("User-Agent")!!.startsWith("Mozilla/5.0"))
    }

    @Test
    fun `sends NewPipeExtractor's own User-Agent instead of the default`() {
        val downloader = NewPipeDownloader(clientAnswering { answer(it) })
        val request =
            Request.newBuilder().get(SEARCH_URL).setHeader("User-Agent", "Mobile client").build()

        downloader.execute(request)

        assertEquals(listOf("Mobile client"), sent.single().headers("User-Agent"))
    }

    @Test
    fun `returns the answer's status, headers, body and link`() {
        val downloader =
            NewPipeDownloader(
                clientAnswering {
                    answer(it, code = 404, body = "Not here")
                        .newBuilder()
                        .header("X-Answer", "yes")
                        .build()
                }
            )

        val response = downloader.execute(Request.newBuilder().get(SEARCH_URL).build())

        assertEquals(404, response.responseCode())
        assertEquals("yes", response.getHeader("X-Answer"))
        assertEquals("Not here", response.responseBody())
        assertEquals(SEARCH_URL, response.latestUrl())
    }

    @Test
    fun `HTTP 429 throws ReCaptchaException`() {
        val downloader = NewPipeDownloader(clientAnswering { answer(it, code = 429) })

        assertFailsWith<ReCaptchaException> {
            downloader.execute(Request.newBuilder().get(SEARCH_URL).build())
        }
    }

    @Test
    fun `a redirect to Google's captcha page throws ReCaptchaException`() {
        val downloader =
            NewPipeDownloader(
                clientAnswering {
                    answer(it, code = 302)
                        .newBuilder()
                        .header("Location", "https://www.google.com/sorry/index?continue=x")
                        .build()
                }
            )

        assertFailsWith<ReCaptchaException> {
            downloader.execute(Request.newBuilder().get(SEARCH_URL).build())
        }
    }

    @Test
    fun `another redirect comes back as it is`() {
        val downloader =
            NewPipeDownloader(
                clientAnswering {
                    answer(it, code = 302)
                        .newBuilder()
                        .header("Location", "https://www.youtube.com/sorry/")
                        .build()
                }
            )

        val response = downloader.execute(Request.newBuilder().get(SEARCH_URL).build())

        assertEquals(302, response.responseCode())
        assertEquals(1, sent.size)
    }

    @Test
    fun `refuses a link that isn't HTTPS to one of YouTube's hosts, without sending it`() {
        val downloader = NewPipeDownloader(clientAnswering { answer(it) })
        val refused =
            listOf(
                "https://www.example.com/",
                "https://www.youtube.com.example.com/",
                "https://notyoutube.com/",
                "https://googleapis.com/",
                "http://www.youtube.com/",
                "not a link",
            )

        for (link in refused) {
            assertFailsWith<HostNotAllowedException>(link) {
                downloader.execute(Request.newBuilder().get(link).build())
            }
        }
        assertEquals(emptyList(), sent)
    }

    @Test
    fun `sends HTTPS requests to YouTube's hosts`() {
        val downloader = NewPipeDownloader(clientAnswering { answer(it) })
        val accepted =
            listOf(
                "https://youtube.com/",
                "https://www.youtube.com/sw.js",
                "https://music.youtube.com/",
                "https://youtubei.googleapis.com/youtubei/v1/player",
            )

        for (link in accepted) {
            downloader.execute(Request.newBuilder().get(link).build())
        }

        assertEquals(accepted, sent.map { it.url.toString() })
    }

    @Test
    fun `a thread that is already interrupted sends nothing`() {
        val downloader = NewPipeDownloader(clientAnswering { answer(it) })
        Thread.currentThread().interrupt()

        assertFailsWith<InterruptedIOException> {
            downloader.execute(Request.newBuilder().get(SEARCH_URL).build())
        }
        assertEquals(emptyList(), sent)
        assertTrue(Thread.currentThread().isInterrupted)
    }

    @Test
    fun `interrupting the waiting thread cancels the request and keeps the thread interrupted`() {
        val started = CountDownLatch(1)
        val call = AtomicReference<Call>()
        val polls = AtomicInteger()
        val client =
            OkHttpClient.Builder()
                .addInterceptor { chain ->
                    call.set(chain.call())
                    started.countDown()
                    // Waits as a slow server would, until the request is cancelled.
                    while (!chain.call().isCanceled() && polls.incrementAndGet() < MAX_POLLS) {
                        Thread.sleep(POLL_MILLIS)
                    }
                    throw IOException("Cancelled")
                }
                .build()
        val downloader = NewPipeDownloader(client)
        val thrown = AtomicReference<Throwable>()
        val stillInterrupted = AtomicReference<Boolean>()
        val waiting = thread {
            try {
                downloader.execute(Request.newBuilder().get(SEARCH_URL).build())
            } catch (e: IOException) {
                thrown.set(e)
                stillInterrupted.set(Thread.currentThread().isInterrupted)
            }
        }

        assertTrue(started.await(WAIT_SECONDS, TimeUnit.SECONDS))
        waiting.interrupt()
        waiting.join(TimeUnit.SECONDS.toMillis(WAIT_SECONDS))

        assertIs<InterruptedIOException>(thrown.get())
        assertEquals(true, stillInterrupted.get())
        assertTrue(call.get().isCanceled())
    }

    @Test
    fun `a request that fails throws its IOException`() {
        val failure = IOException("No connection")
        val client = OkHttpClient.Builder().addInterceptor { throw failure }.build()
        val downloader = NewPipeDownloader(client)

        val thrown =
            assertFailsWith<IOException> {
                downloader.execute(Request.newBuilder().get(SEARCH_URL).build())
            }

        assertEquals(failure.javaClass, thrown.javaClass)
    }

    @Test
    fun `the layer's client follows no redirects`() {
        val client = youTubeHttpClient()

        assertFalse(client.followRedirects)
        assertEquals(TimeUnit.SECONDS.toMillis(30).toInt(), client.readTimeoutMillis)
    }

    /**
     * A client whose answers come from [answer]. Its interceptor answers before OkHttp opens any
     * connection, so no test touches the network.
     */
    private fun clientAnswering(answer: (okhttp3.Request) -> okhttp3.Response): OkHttpClient =
        OkHttpClient.Builder()
            .addInterceptor { chain ->
                synchronized(sent) { sent += chain.request() }
                answer(chain.request())
            }
            .build()

    private fun answer(
        request: okhttp3.Request,
        code: Int = 200,
        body: String = "{}",
    ): okhttp3.Response =
        okhttp3.Response.Builder()
            .request(request)
            .protocol(Protocol.HTTP_1_1)
            .code(code)
            .message("Message")
            .body(body.toResponseBody())
            .build()

    private companion object {
        /** How often the slow fake server checks whether its request was cancelled. */
        const val POLL_MILLIS = 10L

        /** The most checks the slow fake server makes, so a broken test can't hang. */
        const val MAX_POLLS = 500
    }
}
