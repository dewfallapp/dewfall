package io.github.dewfallapp.core.youtube.newpipe.saved

import io.github.dewfallapp.core.youtube.newpipe.NewPipeDownloader
import java.net.InetSocketAddress
import java.net.Proxy
import java.time.LocalDate
import java.util.concurrent.atomic.AtomicInteger
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue
import okhttp3.Call
import okhttp3.EventListener
import org.schabi.newpipe.extractor.downloader.Request

private const val SEARCH_URL = "https://www.youtube.com/youtubei/v1/search?prettyPrint=false"
private const val SEARCH_BODY = """{"query":"nasa"}"""
private const val ANSWER = """{"contents":{}}"""
private const val YOUTUBE = "https://www.youtube.com"
private const val USER_AGENT = "Test client"

class ReplayingClientTest {
    private val content =
        SavedSetContent(
            recordedOn = LocalDate.parse("2026-11-17"),
            newPipeExtractorVersion = "v0.26.5",
            exchanges =
                listOf(
                    SavedExchange(
                        SavedRequest(
                            "POST",
                            SEARCH_URL,
                            listOf(
                                Header("Origin", YOUTUBE),
                                Header("Referer", YOUTUBE),
                                Header("User-Agent", USER_AGENT),
                            ),
                            SEARCH_BODY,
                        ),
                        SavedResponse(
                            200,
                            listOf(Header(CONTENT_TYPE, "application/json")),
                            ANSWER,
                        ),
                    )
                ),
        )

    private val replay = ReplayingClient(SavedSet.SEARCH_NASA, content)

    @Test
    fun `answers a request through NewPipeDownloader with its saved answer`() {
        val response = NewPipeDownloader(replay.client).execute(search(SEARCH_BODY))

        assertEquals(200, response.responseCode())
        assertEquals(ANSWER, response.responseBody())
        assertEquals("application/json", response.getHeader(CONTENT_TYPE))
        assertEquals(SEARCH_URL, response.latestUrl())
        assertEquals(listOf(Endpoint.SEARCH), replay.sentRequests)
        replay.assertNoMisses()
    }

    @Test
    fun `matches a request whose headers come in another order`() {
        val request =
            Request.newBuilder()
                .post(SEARCH_URL, SEARCH_BODY.toByteArray())
                .setHeader("User-Agent", USER_AGENT)
                .setHeader("Referer", YOUTUBE)
                .setHeader("Origin", YOUTUBE)
                .build()

        assertEquals(ANSWER, NewPipeDownloader(replay.client).execute(request).responseBody())
    }

    @Test
    fun `never looks up a host or opens a connection`() {
        val lookups = AtomicInteger()
        val connections = AtomicInteger()
        val listener =
            object : EventListener() {
                override fun dnsStart(call: Call, domainName: String) {
                    lookups.incrementAndGet()
                }

                override fun connectStart(
                    call: Call,
                    inetSocketAddress: InetSocketAddress,
                    proxy: Proxy,
                ) {
                    connections.incrementAndGet()
                }
            }
        val client = replay.client.newBuilder().eventListener(listener).build()

        NewPipeDownloader(client).execute(search(SEARCH_BODY))
        assertFailsWith<MissingSavedResponseException> {
            NewPipeDownloader(client).execute(search("""{"query":"other"}"""))
        }

        assertEquals(0, lookups.get())
        assertEquals(0, connections.get())
    }

    @Test
    fun `a request without a saved answer fails, and the report says how it differs`() {
        assertFailsWith<MissingSavedResponseException> {
            NewPipeDownloader(replay.client).execute(search("""{"query":"other"}"""))
        }

        val report = assertFailsWith<AssertionError> { replay.assertNoMisses() }.message.orEmpty()
        assertTrue(report.contains("request 1 of the saved set SEARCH_NASA"), report)
        assertTrue(report.contains("NewPipeExtractor v0.26.5 on 2026-11-17"), report)
        assertTrue(report.contains("Closest saved request: number 1."), report)
        assertTrue(report.contains("Its body differs at character 10:"), report)
        assertTrue(report.contains("./gradlew :core:youtube:recordYouTubeResponses"), report)
        assertEquals(emptyList(), replay.sentRequests)
    }

    private fun search(body: String): Request =
        Request.newBuilder()
            .post(SEARCH_URL, body.toByteArray())
            .setHeader("Origin", YOUTUBE)
            .setHeader("Referer", YOUTUBE)
            .setHeader("User-Agent", USER_AGENT)
            .build()
}
