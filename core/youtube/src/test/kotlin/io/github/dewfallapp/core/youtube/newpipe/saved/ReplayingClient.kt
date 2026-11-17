package io.github.dewfallapp.core.youtube.newpipe.saved

import io.github.dewfallapp.core.youtube.newpipe.youTubeHttpClient
import java.io.IOException
import java.util.Collections
import kotlin.test.fail
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import okio.Buffer

/**
 * The test system property that names the NewPipeExtractor version that the build uses.
 * core/youtube/build.gradle.kts sets it.
 */
private const val NEWPIPE_EXTRACTOR_VERSION_PROPERTY = "dewfall.newpipeExtractorVersion"

/** How many characters on each side of a difference a report quotes. */
private const val QUOTED_CHARACTERS = 40

/**
 * HTTP/2 answers have no reason phrase, and NewPipeDownloader passes it on without reading it, so a
 * replayed answer has none either.
 */
private const val NO_REASON_PHRASE = ""

/**
 * Thrown for a request that the saved set has no answer for. NewPipeDownloader passes it on as the
 * IOException that a failed request throws, so the layer reports NETWORK, and the test reports the
 * request through [ReplayingClient.assertNoMisses] instead.
 */
internal class MissingSavedResponseException :
    IOException("No saved YouTube response matches the request")

/**
 * The HTTP client that replays the saved set [set], whose files hold [content]. Its interceptor
 * answers each request with the saved answer to the same request before OkHttp opens a connection,
 * so a test never reaches YouTube. It goes under NewPipeDownloader, as the app's client does, so
 * the layer's own HTTP code runs too, with the host checks that it makes before a request is sent.
 *
 * A request matches a saved one when its method, link, headers and body are the same, with the
 * headers in any order. A request without a match throws [MissingSavedResponseException].
 */
internal class ReplayingClient(private val set: SavedSet, private val content: SavedSetContent) {
    private val sent = Collections.synchronizedList(mutableListOf<Endpoint>())
    private val misses = Collections.synchronizedList(mutableListOf<String>())

    /**
     * The client to give NewPipeDownloader, with the app's settings and the replaying interceptor.
     */
    val client: OkHttpClient =
        youTubeHttpClient().newBuilder().addInterceptor { chain -> answer(chain.request()) }.build()

    /** The requests answered so far, in order. */
    val sentRequests: List<Endpoint>
        get() = synchronized(sent) { sent.toList() }

    /** Fails the test with a report of the first request that the set had no answer for, if any. */
    fun assertNoMisses() {
        val firstMiss = synchronized(misses) { misses.firstOrNull() }
        if (firstMiss != null) fail(firstMiss)
    }

    private fun answer(request: Request): Response {
        val saved = request.toSavedRequest()
        val exchange = content.exchanges.firstOrNull { it.request.matches(saved) }
        if (exchange == null) {
            misses += missReport(saved, number = sent.size + misses.size + 1)
            throw MissingSavedResponseException()
        }
        sent += Endpoint.of(saved)
        return exchange.response.toOkHttpResponse(request)
    }

    /**
     * Says which request had no saved answer, how it differs from the closest saved request, and
     * how to record the set again. The set's files are public, so quoting them is safe.
     */
    private fun missReport(request: SavedRequest, number: Int): String {
        val buildVersion = System.getProperty(NEWPIPE_EXTRACTOR_VERSION_PROPERTY) ?: "unknown"
        // The saved request to the same endpoint at the same place, else the first one with the
        // same link, else the first one to the same endpoint.
        val sameEndpoint =
            content.exchanges.withIndex().filter {
                hostAndPathOf(it.value.request) == hostAndPathOf(request)
            }
        val closest =
            sameEndpoint.firstOrNull { it.index == number - 1 }
                ?: sameEndpoint.firstOrNull { it.value.request.url == request.url }
                ?: sameEndpoint.firstOrNull()
        return buildString {
            appendLine(
                "No saved YouTube response matches request $number of the saved set ${set.name}, " +
                    "recorded with NewPipeExtractor ${content.newPipeExtractorVersion} on " +
                    "${content.recordedOn}. This build uses NewPipeExtractor $buildVersion."
            )
            appendLine("  ${request.method} ${request.url}")
            if (closest == null) {
                appendLine("The set has no request to the same endpoint.")
            } else {
                appendLine("Closest saved request: number ${closest.index + 1}.")
                append(differences(closest.value.request, request))
            }
            appendLine(
                "If NewPipeExtractor changed its requests, record the set again with " +
                    "./gradlew :core:youtube:recordYouTubeResponses. " +
                    "See docs/testing.md#saved-youtube-responses."
            )
        }
    }
}

/** A request's method, host and path, such as POST www.youtube.com/youtubei/v1/next. */
private fun hostAndPathOf(request: SavedRequest): String {
    val url = request.url.toHttpUrl()
    return "${request.method} ${url.host}${url.encodedPath}"
}

/** This OkHttp request as a saved set keeps one. */
internal fun Request.toSavedRequest(): SavedRequest =
    SavedRequest(
        method = method,
        url = url.toString(),
        headers = headers.map { (name, value) -> Header(name, value) },
        body = body?.let { Buffer().also(it::writeTo).readUtf8() },
    )

/** This saved answer as OkHttp's answer to [request]. */
private fun SavedResponse.toOkHttpResponse(request: Request): Response {
    val builder =
        Response.Builder()
            .request(request)
            .protocol(Protocol.HTTP_1_1)
            .code(code)
            .message(NO_REASON_PHRASE)
    for (header in headers) {
        builder.addHeader(header.name, header.value)
    }
    val contentType = headers.firstOrNull { it.name.equals(CONTENT_TYPE, ignoreCase = true) }?.value
    return builder.body(body.toResponseBody(contentType?.toMediaTypeOrNull())).build()
}

/** How [sent] differs from [saved]: its link, its headers, or where its body first differs. */
private fun differences(saved: SavedRequest, sent: SavedRequest): String = buildString {
    if (saved.url != sent.url) {
        appendLine("  Its link differs. Saved: ${saved.url}")
    }
    val savedHeaders = saved.headers.toSet()
    val sentHeaders = sent.headers.toSet()
    for (header in (savedHeaders - sentHeaders).sortedBy { it.name }) {
        appendLine("  Saved header not sent: ${header.name}: ${header.value}")
    }
    for (header in (sentHeaders - savedHeaders).sortedBy { it.name }) {
        appendLine("  Sent header not saved: ${header.name}: ${header.value}")
    }
    if (saved.body != sent.body) {
        val savedBody = saved.body.orEmpty()
        val sentBody = sent.body.orEmpty()
        val at =
            savedBody
                .zip(sentBody)
                .indexOfFirst { (savedChar, sentChar) -> savedChar != sentChar }
                .let {
                    if (it < 0) minOf(savedBody.length, sentBody.length) else it
                }
        appendLine("  Its body differs at character $at:")
        appendLine("    saved: ${quoteAround(savedBody, at)}")
        appendLine("    sent:  ${quoteAround(sentBody, at)}")
    }
}

/** The text of [body] around the character at [at]. */
private fun quoteAround(body: String, at: Int): String =
    body.substring(
        (at - QUOTED_CHARACTERS).coerceAtLeast(0),
        (at + QUOTED_CHARACTERS).coerceAtMost(body.length),
    )
