package io.github.dewfallapp.core.youtube.newpipe

import io.github.dewfallapp.core.model.MediaRequestRefused
import java.util.concurrent.atomic.AtomicLong
import java.util.concurrent.atomic.AtomicReference
import okhttp3.HttpUrl
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response

/** The domain of the hosts that serve a video's bytes. */
private const val GOOGLEVIDEO_DOMAIN = "googlevideo.com"

/**
 * The language that every media request asks for, British English, as ADR 0009 asks of every
 * request to YouTube. It is plain text, not NewPipeExtractor's Localization, so the media requests
 * call no NewPipeExtractor code.
 */
private const val MEDIA_LANGUAGE = "en-GB"

/** The most redirects that one request follows, the same as NewPipe's player and OkHttp. */
internal const val MAX_MEDIA_REDIRECTS = 20

/** The value of a link's `c` parameter for YouTube's visionOS client. */
private const val VISIONOS_CLIENT = "VISIONOS"

/**
 * The body of every request for a progressive format: an `x` and a zero byte, as NewPipe v0.29.3's
 * YoutubeHttpDataSource sends.
 */
private val PROGRESSIVE_BODY = byteArrayOf(0x78, 0)

/** The status codes of a redirect that keeps the method and the body. */
private val KEEPS_METHOD = setOf(307, 308)

/** The status codes that OkHttp follows as redirects. */
private val REDIRECTS = setOf(300, 301, 302, 303, 307, 308)

/**
 * The User-Agents that the media requests send. The stream links call, which holds NewPipeCalls'
 * Mutex, reads the visionOS one from NewPipeExtractor and keeps it here, so the media requests call
 * no NewPipeExtractor code on the player's threads. See ADR 0009.
 */
internal class MediaUserAgents {
    private val visionOs = AtomicReference<String?>(null)

    /** The visionOS client's User-Agent, or null before the first stream links call. */
    fun visionOs(): String? = visionOs.get()

    /** Keeps [userAgent] as the visionOS client's User-Agent. */
    fun rememberVisionOs(userAgent: String) {
        visionOs.set(userAgent)
    }
}

/** How a media request is sent, which the media source that makes it decides. */
internal enum class MediaRequestShape {
    /** A progressive format: POST with `x\0`, `TE: trailers`, `rn` and the client's User-Agent. */
    PROGRESSIVE,

    /** A live stream's HLS: plain GET with the layer's own User-Agent, as NewPipe sends it. */
    LIVE,
}

/**
 * Sends a request for a video's bytes as [shape] says, only over HTTPS to googlevideo.com and its
 * subdomains, and follows at most [MAX_MEDIA_REDIRECTS] redirects itself, checking each one's host
 * before sending it. It throws only IOException, so nothing else escapes on OkHttp's threads, and
 * MediaRequestRefused for anything it won't send. See docs/architecture.md#the-player.
 */
internal class MediaInterceptor(
    private val shape: MediaRequestShape,
    private val userAgents: MediaUserAgents,
) : Interceptor {
    /** The next `rn` value, counted from 0 for each interceptor, as YoutubeHttpDataSource does. */
    private val requestNumber = AtomicLong(0)

    override fun intercept(chain: Interceptor.Chain): Response {
        val original = chain.request()
        checkAllowed(original.url)
        var request = shaped(original, userAgentFor(original.url))
        var redirects = 0
        while (true) {
            val response = chain.proceed(request)
            val location = response.header("Location")
            if (response.code !in REDIRECTS || location == null) return response
            response.close()
            redirects++
            if (redirects > MAX_MEDIA_REDIRECTS) {
                throw MediaRequestRefused(MediaRequestRefused.Reason.TOO_MANY_REDIRECTS)
            }
            val target =
                request.url.resolve(location)
                    ?: throw MediaRequestRefused(MediaRequestRefused.Reason.HOST_NOT_ALLOWED)
            checkAllowed(target)
            request = redirected(request, target, response.code)
        }
    }

    private fun userAgentFor(url: HttpUrl): String =
        when (shape) {
            MediaRequestShape.PROGRESSIVE -> {
                if (!isVisionOsLink(url)) {
                    throw MediaRequestRefused(MediaRequestRefused.Reason.UNKNOWN_CLIENT)
                }
                userAgents.visionOs()
                    ?: throw MediaRequestRefused(MediaRequestRefused.Reason.UNKNOWN_CLIENT)
            }
            MediaRequestShape.LIVE -> DEFAULT_USER_AGENT
        }

    private fun shaped(request: Request, userAgent: String): Request {
        val builder =
            request
                .newBuilder()
                .header("User-Agent", userAgent)
                .header("Accept-Language", MEDIA_LANGUAGE)
        if (shape == MediaRequestShape.PROGRESSIVE) {
            builder
                .url(withRequestNumber(request.url))
                .header("TE", "trailers")
                .post(PROGRESSIVE_BODY.toRequestBody())
        }
        return builder.build()
    }

    /** [url] with an `rn` parameter, if it is a `/videoplayback` link without one. */
    private fun withRequestNumber(url: HttpUrl): HttpUrl =
        if (url.encodedPath.endsWith("/videoplayback") && url.queryParameter("rn") == null) {
            url.newBuilder()
                .addQueryParameter("rn", requestNumber.getAndIncrement().toString())
                .build()
        } else {
            url
        }
}

/**
 * The next request after [request] got a redirect with [status] to [target], as OkHttp makes it.
 */
private fun redirected(request: Request, target: HttpUrl, status: Int): Request {
    val builder = request.newBuilder().url(target)
    if (status !in KEEPS_METHOD) {
        builder.get().removeHeader("Content-Length").removeHeader("Content-Type")
    }
    return builder.build()
}

/**
 * The OkHttpClient under the media requests. It follows no redirects itself, so MediaInterceptor
 * checks every host before a request reaches it, and, like the layer's own client, keeps no cookies
 * and no cache.
 */
internal fun mediaHttpClient(): OkHttpClient =
    OkHttpClient.Builder().followRedirects(false).followSslRedirects(false).build()

/** Refuses [url] unless it is HTTPS to googlevideo.com or one of its subdomains. */
private fun checkAllowed(url: HttpUrl) {
    if (!url.isHttps) throw MediaRequestRefused(MediaRequestRefused.Reason.NOT_HTTPS)
    if (!isInDomain(url.host, GOOGLEVIDEO_DOMAIN)) {
        throw MediaRequestRefused(MediaRequestRefused.Reason.HOST_NOT_ALLOWED)
    }
}

/**
 * Whether [url] was made for YouTube's visionOS client, by its `c` parameter. It parses the
 * parameter, which is stricter than NewPipeExtractor's search for the text `&c=VISIONOS`.
 */
internal fun isVisionOsLink(url: HttpUrl): Boolean = url.queryParameter("c") == VISIONOS_CLIENT
