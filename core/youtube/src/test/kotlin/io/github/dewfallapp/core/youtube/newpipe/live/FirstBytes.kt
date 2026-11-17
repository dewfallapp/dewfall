package io.github.dewfallapp.core.youtube.newpipe.live

import io.github.dewfallapp.core.logging.Logger
import java.io.IOException
import kotlin.time.Duration.Companion.seconds
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import org.schabi.newpipe.extractor.services.youtube.YoutubeParsingHelper

/** How many bytes the check asks a link for: the first KiB, as a player's first request might. */
private const val FIRST_BYTES_COUNT = 1024

/** The status code of an answer to a ranged request that the server honored. */
private const val PARTIAL_CONTENT = 206

/** The most redirects that one request for a link's first bytes follows. */
private const val MAX_REDIRECTS = 3

/** The domain of the links that play a video. */
private const val GOOGLEVIDEO_DOMAIN = "googlevideo.com"

/** How long a request for a link's first bytes waits for the next bytes of the answer. */
private val READ_TIMEOUT = 30.seconds

/** The area of the log tag of the requests for a link's first bytes, Dewfall.FirstBytes. */
private const val LOG_AREA = "FirstBytes"

/** The YouTube client that a stream link was made for, which its `c` parameter names. */
internal enum class LinkClient {
    ANDROID,
    VISIONOS,
    IOS,

    /** Any other client, whose User-Agent the check doesn't know. */
    OTHER,
}

/** How a request for a link's first bytes ended. */
internal enum class FirstBytesAnswer {
    /** 206, with the format's length as the total and the bytes asked for. */
    PLAYABLE,

    /** A status code other than 206, or a redirect without a Location header. */
    WRONG_STATUS,

    /** 206, but the total in Content-Range is missing or differs from the format's length. */
    WRONG_LENGTH,

    /** 206, but with fewer bytes than asked for. */
    SHORT_BODY,

    /** The link was made for a client whose User-Agent the check doesn't know. Nothing was sent. */
    UNKNOWN_CLIENT,

    /** The link or a redirect isn't HTTPS to googlevideo.com. Nothing was sent to it. */
    HOST_NOT_ALLOWED,

    /** More redirects than [MAX_REDIRECTS]. */
    TOO_MANY_REDIRECTS,

    /** The request failed with an IOException. */
    NETWORK,
}

/**
 * How a request for a link's first bytes ended: its [answer], the [client] that the link was made
 * for, and the status code of the last answer, if a request got one.
 */
internal class FirstBytesResult(
    val answer: FirstBytesAnswer,
    val client: LinkClient,
    val status: Int?,
)

/** Fetches the first bytes of a stream link. The live check's tests fake it. */
internal fun interface FirstBytesFetcher {
    /**
     * Asks [link], a format whose file is [contentLength] bytes long, for its first bytes, and says
     * how that went.
     */
    fun fetch(link: String, contentLength: Long): FirstBytesResult
}

/**
 * The OkHttpClient that asks links for their first bytes. It follows no redirects itself, so
 * [FirstBytes] checks every host before a request reaches it.
 */
internal fun firstBytesClient(): OkHttpClient =
    OkHttpClient.Builder()
        .followRedirects(false)
        .followSslRedirects(false)
        .readTimeout(READ_TIMEOUT)
        .build()

/**
 * Asks a stream link for its first [FIRST_BYTES_COUNT] bytes with a GET request and a Range header,
 * as a player's HTTP data source does, through [client], usually [firstBytesClient].
 * - It sends only HTTPS requests to googlevideo.com and its subdomains, and follows at most
 *   [MAX_REDIRECTS] redirects, each checked before it is sent.
 * - It sends the User-Agent that NewPipe's player sends for the link's client, which the link's `c`
 *   parameter names: NewPipeExtractor's own User-Agent for its Android, visionOS or iOS client. It
 *   sends nothing for a link of any other client.
 * - It closes every answer, so OkHttp never warns about a leaked one, a warning that names the
 *   host.
 *
 * It logs an IOException through [logger] as class names and stack frames only, because its message
 * can name the host. It never logs a link. See ADR 0007.
 */
internal class FirstBytes(private val client: OkHttpClient, logger: Logger) : FirstBytesFetcher {
    private val log = logger.withTag(LOG_AREA)

    override fun fetch(link: String, contentLength: Long): FirstBytesResult {
        val linkClient = clientOf(link)
        val userAgent = userAgentOf(linkClient)
        val answered =
            if (userAgent == null) {
                Answered(FirstBytesAnswer.UNKNOWN_CLIENT)
            } else {
                ask(link.toHttpUrlOrNull(), userAgent, contentLength, MAX_REDIRECTS)
            }
        return FirstBytesResult(answered.answer, linkClient, answered.status)
    }

    /**
     * Asks [url] for its first bytes with [userAgent], following up to [redirectsLeft] redirects.
     */
    private tailrec fun ask(
        url: HttpUrl?,
        userAgent: String,
        contentLength: Long,
        redirectsLeft: Int,
    ): Answered {
        if (url == null || !isGoogleVideoUrl(url)) {
            return Answered(FirstBytesAnswer.HOST_NOT_ALLOWED)
        }
        val request =
            Request.Builder()
                .url(url)
                .header("Range", "bytes=0-${FIRST_BYTES_COUNT - 1}")
                .header("User-Agent", userAgent)
                .build()
        val step =
            try {
                client.newCall(request).execute().use { stepOf(it, contentLength) }
            } catch (e: IOException) {
                log.warn("A request for a link's first bytes failed", error = e)
                Step.Done(Answered(FirstBytesAnswer.NETWORK))
            }
        return when (step) {
            is Step.Done -> step.answered
            is Step.Redirect -> {
                if (redirectsLeft == 0) {
                    Answered(FirstBytesAnswer.TOO_MANY_REDIRECTS, step.status)
                } else {
                    ask(step.location, userAgent, contentLength, redirectsLeft - 1)
                }
            }
        }
    }

    /** What to do after [response]: follow its redirect, or stop with an answer. */
    private fun stepOf(response: Response, contentLength: Long): Step {
        val location = response.header("Location")
        return when {
            response.isRedirect && location != null -> {
                Step.Redirect(response.request.url.resolve(location), response.code)
            }
            response.code != PARTIAL_CONTENT -> {
                Step.Done(Answered(FirstBytesAnswer.WRONG_STATUS, response.code))
            }
            totalLengthOf(response) != contentLength -> {
                Step.Done(Answered(FirstBytesAnswer.WRONG_LENGTH, response.code))
            }
            !response.body.source().request(minOf(FIRST_BYTES_COUNT.toLong(), contentLength)) -> {
                Step.Done(Answered(FirstBytesAnswer.SHORT_BODY, response.code))
            }
            else -> Step.Done(Answered(FirstBytesAnswer.PLAYABLE, response.code))
        }
    }

    /** An answer, and the status code of the response that gave it, if there was one. */
    private class Answered(val answer: FirstBytesAnswer, val status: Int? = null)

    /** What a response leads to. */
    private sealed interface Step {
        class Done(val answered: Answered) : Step

        /** A redirect with [status] to [location], or to null when it isn't a valid link. */
        class Redirect(val location: HttpUrl?, val status: Int) : Step
    }
}

/** The total length of the file that a 206 answer is part of, from its Content-Range header. */
private fun totalLengthOf(response: Response): Long? =
    response.header("Content-Range")?.substringAfterLast('/')?.toLongOrNull()

/** Whether [url] is an HTTPS link to googlevideo.com or one of its subdomains. */
internal fun isGoogleVideoUrl(url: HttpUrl): Boolean =
    url.isHttps && (url.host == GOOGLEVIDEO_DOMAIN || url.host.endsWith(".$GOOGLEVIDEO_DOMAIN"))

/** The client that [link] was made for, which its `c` parameter names. */
internal fun clientOf(link: String): LinkClient =
    when {
        YoutubeParsingHelper.isAndroidStreamingUrl(link) -> LinkClient.ANDROID
        YoutubeParsingHelper.isVisionOsStreamingUrl(link) -> LinkClient.VISIONOS
        YoutubeParsingHelper.isIosStreamingUrl(link) -> LinkClient.IOS
        else -> LinkClient.OTHER
    }

/**
 * The User-Agent that NewPipe v0.29.1's player sends for a link of [client], or null for a client
 * that the check doesn't know. NewPipe's player passes no localization, so NewPipeExtractor uses
 * its default, en-GB, the same one that the layer's requests use. See YoutubeHttpDataSource in
 * [NewPipe v0.29.1](https://github.com/TeamNewPipe/NewPipe/blob/v0.29.1/app/src/main/java/org/schabi/newpipe/player/datasource/YoutubeHttpDataSource.java#L675-L689).
 */
private fun userAgentOf(client: LinkClient): String? =
    when (client) {
        LinkClient.ANDROID -> YoutubeParsingHelper.getAndroidUserAgent(null)
        LinkClient.VISIONOS -> YoutubeParsingHelper.getVisionOsUserAgent(null)
        LinkClient.IOS -> YoutubeParsingHelper.getIosUserAgent(null)
        LinkClient.OTHER -> null
    }
