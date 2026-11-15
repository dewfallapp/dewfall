package io.github.dewfallapp.core.youtube.newpipe

import java.io.IOException
import java.io.InterruptedIOException
import java.util.concurrent.CompletableFuture
import java.util.concurrent.ExecutionException
import kotlin.time.Duration.Companion.seconds
import okhttp3.Call
import okhttp3.Callback
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.OkHttpClient
import okhttp3.RequestBody.Companion.toRequestBody
import org.schabi.newpipe.extractor.downloader.Downloader
import org.schabi.newpipe.extractor.downloader.Request
import org.schabi.newpipe.extractor.downloader.Response
import org.schabi.newpipe.extractor.exceptions.ReCaptchaException

/**
 * The User-Agent of a request that NewPipeExtractor sends without one of its own. It sets one only
 * for the mobile clients that it pretends to be, so the web client's requests get this one, of
 * Firefox ESR on Windows, the same one that NewPipeExtractor v0.26.5's own tests send.
 */
private const val DEFAULT_USER_AGENT =
    "Mozilla/5.0 (Windows NT 10.0; Win64; x64; rv:140.0) Gecko/20100101 Firefox/140.0"

/** The status code that YouTube answers with when it limits an app's requests. */
private const val TOO_MANY_REQUESTS = 429

/** How long a request waits for the next bytes of an answer, as in NewPipeExtractor's tests. */
private val READ_TIMEOUT = 30.seconds

/** The host, apart from youtube.com and its subdomains, that YouTube's API requests go to. */
private const val YOUTUBE_API_HOST = "youtubei.googleapis.com"

/** The domain of Google's captcha page, which YouTube sends an app to when it blocks it. */
private const val GOOGLE_DOMAIN = "google.com"

/** The start of the captcha page's path on Google's hosts. */
private const val CAPTCHA_PATH_PREFIX = "/sorry/"

/**
 * The OkHttpClient that the YouTube layer sends its requests with. It follows no redirects, so a
 * request never reaches a host that [NewPipeDownloader] didn't check.
 */
internal fun youTubeHttpClient(): OkHttpClient =
    OkHttpClient.Builder().followRedirects(false).readTimeout(READ_TIMEOUT).build()

/**
 * Whether [url] is an HTTPS link to one of YouTube's hosts, the only ones that the YouTube layer
 * may contact: youtube.com, its subdomains, and youtubei.googleapis.com. See
 * docs/architecture.md#network-access.
 */
private fun isYouTubeUrl(url: HttpUrl): Boolean {
    val host = url.host
    return url.isHttps && (isInDomain(host, "youtube.com") || host == YOUTUBE_API_HOST)
}

/**
 * Thrown for a request that isn't an HTTPS request to one of YouTube's hosts, before anything is
 * sent. It is an IOException because a NewPipeExtractor Downloader may throw only IOException and
 * ReCaptchaException. It maps to BROKEN, not NETWORK, because only a NewPipeExtractor update can
 * make the layer ask for another host, and that host has to be checked before the app contacts it.
 */
internal class HostNotAllowedException :
    IOException("The request is not an HTTPS request to one of YouTube's hosts")

/**
 * The HTTP code that NewPipeExtractor sends each of its requests through, built on OkHttp. [calls]
 * is usually [youTubeHttpClient].
 * - It sends only HTTPS requests to YouTube's hosts, and throws [HostNotAllowedException] for any
 *   other link without sending anything.
 * - HTTP 429, and a redirect to Google's captcha page, throw ReCaptchaException. The client follows
 *   no redirects, so any other answer, a redirect included, goes back to NewPipeExtractor as it is.
 * - Interrupting the thread that waits for an answer cancels the request and throws
 *   InterruptedIOException. The thread stays interrupted, so NewPipeExtractor's next request stops
 *   before it is sent. NewPipeCalls interrupts the thread when the calling coroutine is cancelled.
 *
 * Links, headers and bodies are user data, so nothing here puts them in a log line or an exception
 * message. See ADR 0007.
 */
internal class NewPipeDownloader(private val calls: Call.Factory) : Downloader() {
    override fun execute(request: Request): Response {
        val url = request.url().toHttpUrlOrNull()
        if (url == null || !isYouTubeUrl(url)) throw HostNotAllowedException()
        if (Thread.currentThread().isInterrupted) throw cancelledException(cause = null)
        return send(calls.newCall(request.toOkHttpRequest(url)))
    }

    /**
     * Sends [call] and waits for its answer. OkHttp sends the request and reads the answer on its
     * own thread, while this thread waits. A blocking OkHttp call notices an interrupt only between
     * reads and writes, while cancel() closes the connection at once.
     */
    private fun send(call: Call): Response {
        val answer = CompletableFuture<Response>()
        call.enqueue(AnswerReader(answer))
        try {
            return answer.get()
        } catch (e: InterruptedException) {
            call.cancel()
            Thread.currentThread().interrupt()
            throw cancelledException(cause = e)
        } catch (e: ExecutionException) {
            // The IOException or ReCaptchaException that stopped the answer, which NewPipeExtractor
            // expects as they are, or any other exception from reading it.
            throw e.cause ?: e
        }
    }
}

/** The exception for a request that stopped because its thread was interrupted. */
private fun cancelledException(cause: InterruptedException?): InterruptedIOException =
    InterruptedIOException("The request was cancelled").apply { initCause(cause) }

/** Whether [host] is [domain] or one of its subdomains. */
private fun isInDomain(host: String, domain: String): Boolean =
    host == domain || host.endsWith(".$domain")

/**
 * This request for OkHttp, sent to [url], the parsed form of its link. NewPipeExtractor's headers
 * replace the defaults, such as the User-Agent of the mobile clients that it pretends to be.
 */
private fun Request.toOkHttpRequest(url: HttpUrl): okhttp3.Request {
    val builder = okhttp3.Request.Builder().url(url).header("User-Agent", DEFAULT_USER_AGENT)
    for ((name, values) in headers()) {
        builder.removeHeader(name)
        for (value in values) {
            builder.addHeader(name, value)
        }
    }
    return builder.method(httpMethod(), dataToSend()?.toRequestBody()).build()
}

/**
 * Reads an answer on OkHttp's thread, and completes [answer] with it, or with the exception that
 * stopped it. Reading the body here means that cancelling the call also stops a slow body.
 */
private class AnswerReader(private val answer: CompletableFuture<Response>) : Callback {
    override fun onFailure(call: Call, e: IOException) {
        answer.completeExceptionally(e)
    }

    override fun onResponse(call: Call, response: okhttp3.Response) {
        // OkHttp logs an IOException that leaves this function, with the request's host, and an
        // exception of another type would end the app on OkHttp's thread. So every exception goes
        // back to the waiting thread instead.
        @Suppress("TooGenericExceptionCaught")
        try {
            answer.complete(response.use { it.toNewPipeResponse() })
        } catch (e: Exception) {
            answer.completeExceptionally(e)
        }
    }
}

/** This answer for NewPipeExtractor, or ReCaptchaException if YouTube refused to answer. */
private fun okhttp3.Response.toNewPipeResponse(): Response {
    if (code == TOO_MANY_REQUESTS || isRedirectToCaptcha()) {
        // The layer never shows the captcha, so the exception doesn't carry its link.
        throw ReCaptchaException("YouTube asked for a captcha", "")
    }
    return Response(code, message, headers.toMultimap(), body.string(), request.url.toString())
}

/** Whether this answer sends the app on to Google's captcha page. */
private fun okhttp3.Response.isRedirectToCaptcha(): Boolean {
    val target = if (isRedirect) header("Location")?.let { request.url.resolve(it) } else null
    return target != null &&
        isInDomain(target.host, GOOGLE_DOMAIN) &&
        target.encodedPath.startsWith(CAPTCHA_PATH_PREFIX)
}
