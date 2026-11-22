package io.github.dewfallapp.fake

import android.content.res.AssetManager
import java.io.IOException
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger
import okhttp3.Call
import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody

/** HTTP's status for the part of a file that a Range header asked for. */
private const val PARTIAL_CONTENT = 206

/** HTTP's status for a whole file. */
private const val OK = 200

/** HTTP's status for a Range header that the file can't satisfy. */
private const val RANGE_NOT_SATISFIABLE = 416

/** A Range header with one range of bytes, from a first byte to an optional last one. */
private val singleRange = Regex("""bytes=(\d+)-(\d*)""")

/**
 * Answers the player's requests for [TestVideo]'s two files from the test APK's [assets], as a real
 * server does: 200 with the whole file for a request with no Range header, which OkHttpDataSource
 * sends for a read from the start with no set length; 206 with the bytes that a Range header asks
 * for; and 416 for a range that starts at or past the end, which OkHttpDataSource reads as the end
 * of the input when it starts at the end.
 *
 * Its [calls] never reach the network: the interceptor answers every request itself and never calls
 * `chain.proceed`, and any other link fails with an IOException, as does every request while
 * [network] has the media off. It counts the requests for each file that it answers, so a test can
 * check that the player read both.
 */
class AssetMedia(private val assets: AssetManager, private val network: FakeNetwork) {
    private val files =
        mapOf(
            TestVideo.VIDEO_URL to TestVideo.VIDEO_ASSET,
            TestVideo.AUDIO_URL to TestVideo.AUDIO_ASSET,
        )
    private val contents = ConcurrentHashMap<String, ByteArray>()
    private val requests = ConcurrentHashMap<String, AtomicInteger>()

    /** The client that the player's media sources use in place of the YouTube layer's. */
    val calls: Call.Factory = OkHttpClient.Builder().addInterceptor(::answer).build()

    /**
     * The size in bytes of the file at [url], one of [TestVideo]'s links. It reads only the asset's
     * descriptor, not the file, so the fake stream links stay main-safe, as ADR 0008 asks of the
     * YouTube layer. The build stores mp4 and m4a assets uncompressed, which openFd needs.
     */
    fun length(url: String): Long = assets.openFd(asset(url)).use { it.length }

    /** How many requests [calls] answered for the file at [url]. */
    fun requestsFor(url: String): Int = requests[url]?.get() ?: 0

    /** Reads the whole file, on OkHttp's thread, the first time the player asks for it. */
    private fun bytes(url: String): ByteArray =
        contents.getOrPut(url) { assets.open(asset(url)).use { it.readBytes() } }

    private fun asset(url: String): String =
        files[url] ?: throw IOException("Not one of the test video's links")

    private fun answer(chain: Interceptor.Chain): Response {
        val request = chain.request()
        if (!network.mediaReachable) throw IOException("The fake network is off")
        val url = request.url.toString()
        val bytes = bytes(url)
        requests.getOrPut(url) { AtomicInteger() }.incrementAndGet()
        val builder =
            Response.Builder()
                .request(request)
                .protocol(Protocol.HTTP_1_1)
                .header("Accept-Ranges", "bytes")
        val type = (if (url == TestVideo.VIDEO_URL) "video/mp4" else "audio/mp4").toMediaType()
        val header = request.header("Range")
        val requested = header?.let { range(it, bytes.size) }
        return when {
            header == null -> builder.code(OK).message("OK").body(bytes.toResponseBody(type))
            requested == null -> {
                builder
                    .code(RANGE_NOT_SATISFIABLE)
                    .message("Range Not Satisfiable")
                    .header("Content-Range", "bytes */${bytes.size}")
                    .body(ByteArray(0).toResponseBody(null))
            }
            else -> {
                val first = requested.first
                val last = requested.last
                builder
                    .code(PARTIAL_CONTENT)
                    .message("Partial Content")
                    .header("Content-Range", "bytes $first-$last/${bytes.size}")
                    .body(bytes.copyOfRange(first.toInt(), last.toInt() + 1).toResponseBody(type))
            }
        }.build()
    }

    /**
     * The bytes that a Range [header] asks for in a file of [size] bytes, with the last one cut to
     * the file's end, or null when the file can't give them.
     */
    private fun range(header: String, size: Int): LongRange? {
        val match = singleRange.matchEntire(header)
        val first = match?.groupValues?.get(1)?.toLongOrNull()
        val end = size - 1L
        val last = match?.groupValues?.get(2)?.toLongOrNull()?.coerceAtMost(end) ?: end
        return if (first != null && first < size && first <= last) first..last else null
    }
}
