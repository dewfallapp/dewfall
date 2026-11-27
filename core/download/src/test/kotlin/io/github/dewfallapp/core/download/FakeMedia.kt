package io.github.dewfallapp.core.download

import java.io.IOException
import kotlinx.coroutines.CompletableDeferred
import okhttp3.Call
import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Response
import okhttp3.ResponseBody.Companion.asResponseBody
import okhttp3.ResponseBody.Companion.toResponseBody
import okio.Buffer
import okio.Source
import okio.Timeout
import okio.buffer

/** A made-up link. Every test link is a placeholder, never a real one. */
internal const val LINK = "https://rr1---sn-masked.googlevideo.com/videoplayback?itag=136"

/** A made-up file of [size] bytes, each byte its position modulo 251. */
internal fun fileOf(size: Int): ByteArray = ByteArray(size) { (it % 251).toByte() }

/**
 * Answers range requests for [bytes] as googlevideo.com does, never reaching the network: 206 with
 * the bytes a Range header asks for and a Content-Range with the total, or an asterisk for it when
 * [hideTotal], and 416 for a range that starts at or past the end. [answer] can replace the answer
 * to request number n, counted from 0. It records every Range header.
 */
internal class FakeMedia(
    private val bytes: ByteArray,
    private val hideTotal: Boolean = false,
    private val answer: (Int, Interceptor.Chain) -> Response? = { _, _ -> null },
) {
    val ranges = mutableListOf<String?>()

    val calls: Call.Factory = OkHttpClient.Builder().addInterceptor(::respond).build()

    fun respond(chain: Interceptor.Chain): Response {
        val request = chain.request()
        val number = ranges.size
        ranges += request.header("Range")
        return answer(number, chain) ?: rangeOf(chain)
    }

    private fun rangeOf(chain: Interceptor.Chain): Response {
        val request = chain.request()
        val range =
            Regex("""bytes=(\d+)-(\d+)""").matchEntire(request.header("Range") ?: "")
                ?: throw IOException("No range")
        val start = range.groupValues[1].toInt()
        val end = minOf(range.groupValues[2].toInt(), bytes.size - 1)
        val total = if (hideTotal) "*" else bytes.size.toString()
        return if (start >= bytes.size) {
            status(chain, 416)
        } else {
            Response.Builder()
                .request(request)
                .protocol(Protocol.HTTP_1_1)
                .code(206)
                .message("Partial Content")
                .header("Content-Range", "bytes $start-$end/$total")
                .body(bytes.copyOfRange(start, end + 1).toResponseBody("video/mp4".toMediaType()))
                .build()
        }
    }
}

/** An answer to [chain]'s request with [code] and [body]. */
internal fun status(
    chain: Interceptor.Chain,
    code: Int,
    body: ByteArray = ByteArray(0),
    contentRange: String? = null,
): Response =
    Response.Builder()
        .request(chain.request())
        .protocol(Protocol.HTTP_1_1)
        .code(code)
        .message("Status")
        .apply { contentRange?.let { header("Content-Range", it) } }
        .body(body.toResponseBody("video/mp4".toMediaType()))
        .build()

/**
 * Answers each link from its own [FakeMedia], by the whole link, and fails any other link, or every
 * request while [online] is false, with an IOException, as a lost connection does.
 */
internal class RoutedMedia(private val routes: Map<String, FakeMedia>) {
    var online = true

    val calls: Call.Factory =
        OkHttpClient.Builder()
            .addInterceptor { chain ->
                if (!online) throw IOException("Offline")
                val media =
                    routes[chain.request().url.toString()] ?: throw IOException("Unknown link")
                media.respond(chain)
            }
            .build()
}

/**
 * A 206 for the whole of a 1,000 byte file, or for [contentRange] of [length] bytes, whose body
 * never sends a byte, as a stalled server's doesn't, until the call is cancelled, when its read
 * fails as a socket's would, or for at most 10 seconds. It completes [reading] when the read
 * starts.
 */
internal fun stalled(
    chain: Interceptor.Chain,
    reading: CompletableDeferred<Unit>,
    contentRange: String = "bytes 0-999/1000",
    length: Long = 1_000,
): Response {
    val call = chain.call()
    val source =
        object : Source {
            override fun read(sink: Buffer, byteCount: Long): Long {
                reading.complete(Unit)
                // A deadline, so a test whose call is never cancelled fails instead of hanging.
                val deadline = System.nanoTime() + STALL_LIMIT_NANOS
                while (!call.isCanceled() && System.nanoTime() < deadline) {
                    Thread.sleep(STALL_POLL_MILLIS)
                }
                throw IOException(if (call.isCanceled()) "Canceled" else "Read timed out")
            }

            override fun timeout(): Timeout = Timeout.NONE

            override fun close() = Unit
        }
    return Response.Builder()
        .request(chain.request())
        .protocol(Protocol.HTTP_1_1)
        .code(206)
        .message("Partial Content")
        .header("Content-Range", contentRange)
        .body(source.buffer().asResponseBody("video/mp4".toMediaType(), length))
        .build()
}

/**
 * A 206 answer of [bytes] from the file's start, of [total] bytes in all, whose read first calls
 * [onRead] and then sends the bytes only after a pause, as a buffer that was on its way when a stop
 * came.
 */
internal fun lateBuffer(
    chain: Interceptor.Chain,
    bytes: ByteArray,
    total: Long,
    onRead: () -> Unit,
): Response {
    var sent = false
    val source =
        object : Source {
            override fun read(sink: Buffer, byteCount: Long): Long {
                if (sent) return -1
                sent = true
                onRead()
                Thread.sleep(LATE_BUFFER_MILLIS)
                sink.write(bytes)
                return bytes.size.toLong()
            }

            override fun timeout(): Timeout = Timeout.NONE

            override fun close() = Unit
        }
    return Response.Builder()
        .request(chain.request())
        .protocol(Protocol.HTTP_1_1)
        .code(206)
        .message("Partial Content")
        .header("Content-Range", "bytes 0-${bytes.size - 1}/$total")
        .body(source.buffer().asResponseBody("video/mp4".toMediaType(), bytes.size.toLong()))
        .build()
}

private const val LATE_BUFFER_MILLIS = 300L

private const val STALL_POLL_MILLIS = 10L

private const val STALL_LIMIT_NANOS = 10_000_000_000L
