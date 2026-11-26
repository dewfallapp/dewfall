package io.github.dewfallapp.core.download

import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.coroutines.resumeWithException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import okhttp3.Call
import okhttp3.Callback
import okhttp3.Request
import okhttp3.Response

/**
 * The size of each range a download asks for, 10 MiB, as yt-dlp asks for YouTube's HTTPS formats
 * (`CHUNK_SIZE = 10 << 20` in yt_dlp/extractor/youtube/_video.py at 2026.11.23).
 */
internal const val RANGE_BYTES = 10L * 1024 * 1024

/** The size of the buffer the answer is copied through, so no range is held in memory. */
private const val BUFFER_BYTES = 64 * 1024

private const val PARTIAL_CONTENT = 206
private const val OK = 200
private const val FORBIDDEN = 403
private const val GONE = 410
private const val RANGE_NOT_SATISFIABLE = 416

/**
 * A Content-Range header's start and total, such as `bytes 0-1023/4096`, with an asterisk for an
 * unknown total.
 */
private val contentRange = Regex("""bytes (\d+)-(\d+)/(\d+|\*)""")

/**
 * YouTube refused a format's link with 403 or 410, which means it expired, so the caller fetches
 * new links. Its message is constant text, because a link is user data.
 */
internal class LinkRefused : IOException("The media link was refused")

/**
 * An answer that doesn't fit the range asked for, or a status that isn't a link's refusal. Its
 * message is constant text: [status] is the HTTP status, never a link.
 */
internal class UnexpectedAnswer(val status: Int) : IOException("Unexpected media answer")

/** How far one file's write got: its [bytes] so far, and whether a range just [ended]. */
internal fun interface WriteProgress {
    suspend fun onBytes(bytes: Long, rangeEnded: Boolean)
}

/**
 * Downloads one format into a `.part` file, in ranges of [rangeBytes], through a client that the
 * YouTube layer's MediaRequests made, so every request keeps playback's rules. It goes on from the
 * file's current length, so a stop loses at most the buffer in flight. It reads each answer in 64
 * KiB buffers and checks for cancellation between them, so a pause stops it within one buffer and
 * the bytes written stay. Blocking work runs on [dispatcher]. See
 * docs/architecture.md#downloads-and-cleanup.
 */
internal class FormatWriter(
    private val dispatcher: CoroutineDispatcher,
    private val rangeBytes: Long = RANGE_BYTES,
    /**
     * Makes the file's bytes durable before the caller renames it complete. A file renamed without
     * it can be empty after a sudden power loss while the row says DOWNLOADED, as a hand check on
     * Android 12L showed after an emulator's hard stop. Tests record it.
     */
    private val sync: (FileOutputStream) -> Unit = { it.fd.sync() },
) {
    /**
     * Writes the format at [url] into [part] until it has [knownLength] bytes, or, when YouTube
     * gave no length, as many as an answer's Content-Range says, or until a 416 after the last
     * byte, and returns the file's length once its bytes are synced to the disk. Throws
     * [LinkRefused] for 403 or 410, [UnexpectedAnswer] for an answer that doesn't fit, any
     * IOException that [calls] or the file throws, and CancellationException.
     */
    suspend fun write(
        url: String,
        part: File,
        knownLength: Long?,
        calls: Call.Factory,
        progress: WriteProgress,
    ): Long =
        withContext(dispatcher) {
            part.parentFile?.mkdirs()
            var total = knownLength
            var offset = if (part.exists()) part.length() else 0L
            if (total != null && offset > total) {
                // More bytes than the format has: not this format's file, so start it again.
                part.delete()
                offset = 0
            }
            FileOutputStream(part, true).use { out ->
                while (total == null || offset < total) {
                    val range = nextRange(url, offset, total, calls, out, progress)
                    offset = range.reached
                    total = range.total
                    progress.onBytes(offset, rangeEnded = true)
                    if (range.written == 0L && total != offset) {
                        throw UnexpectedAnswer(PARTIAL_CONTENT)
                    }
                }
                sync(out)
            }
            offset
        }

    /** Asks for the range from [start] and appends its answer to [out]. */
    private suspend fun nextRange(
        url: String,
        start: Long,
        total: Long?,
        calls: Call.Factory,
        out: FileOutputStream,
        progress: WriteProgress,
    ): RangeWritten {
        val end = rangeEnd(start, total)
        val request = Request.Builder().url(url).header("Range", "bytes=$start-$end").build()
        val call = calls.newCall(request)
        val (answer, written) =
            call.cancelledWithCaller {
                call.await().use { response ->
                    val answer = checkAnswer(response, start, total)
                    // Never more than the range asked, so a server that sends more can't make
                    // the file longer than the format.
                    val limit = if (answer.whole) total else end - start + 1
                    val written =
                        if (answer.pastTheEnd) 0L else copy(response, out, start, limit, progress)
                    answer to written
                }
            }
        val reached = start + written
        val newTotal =
            when {
                // A 416 past the end means there is no more.
                answer.pastTheEnd -> reached
                // A known length stays, so a short answer only ends this range.
                total != null -> total
                // A 200 from byte 0 is the whole file.
                answer.whole -> reached
                // Otherwise the end is the total that Content-Range gives, or a 416: a range
                // that ends early with an asterisk for its total says nothing about the end.
                else -> answer.total
            }
        return RangeWritten(written, reached, newTotal)
    }

    private fun rangeEnd(offset: Long, total: Long?): Long {
        val end = offset + rangeBytes - 1
        return if (total == null) end else minOf(end, total - 1)
    }

    /**
     * Copies at most [limit] bytes of the answer, or all of it when [limit] is null, into [out],
     * one buffer at a time.
     */
    private suspend fun copy(
        response: Response,
        out: FileOutputStream,
        start: Long,
        limit: Long?,
        progress: WriteProgress,
    ): Long {
        val buffer = ByteArray(BUFFER_BYTES)
        var written = 0L
        response.body.byteStream().use { input ->
            while (limit == null || written < limit) {
                currentCoroutineContext().ensureActive()
                val wanted =
                    if (limit == null) {
                        buffer.size
                    } else {
                        minOf(buffer.size.toLong(), limit - written).toInt()
                    }
                val read = input.read(buffer, 0, wanted)
                if (read < 0) break
                out.write(buffer, 0, read)
                written += read
                progress.onBytes(start + written, rangeEnded = false)
            }
        }
        return written
    }
}

/** What one range added: its [written] bytes, the file's length it [reached], and its total. */
private data class RangeWritten(val written: Long, val reached: Long, val total: Long?)

/** What an answer to a range request holds. */
private data class RangeAnswer(
    /** A 200 with the whole file, for a request from byte 0. */
    val whole: Boolean = false,
    /** The file's total size, from Content-Range, or null if the server didn't say. */
    val total: Long? = null,
    /** A 416 for a request at the end of a file of unknown size: there is nothing more. */
    val pastTheEnd: Boolean = false,
)

/** Checks that [response] answers a request from [offset], and says what it holds. */
private fun checkAnswer(response: Response, offset: Long, total: Long?): RangeAnswer =
    when (response.code) {
        FORBIDDEN,
        GONE -> throw LinkRefused()
        OK -> if (offset == 0L) RangeAnswer(whole = true) else throw UnexpectedAnswer(OK)
        PARTIAL_CONTENT -> {
            val match =
                response.header("Content-Range")?.let { contentRange.matchEntire(it.trim()) }
            val start = match?.groupValues?.get(1)?.toLongOrNull()
            if (start != offset) throw UnexpectedAnswer(PARTIAL_CONTENT)
            RangeAnswer(total = match.groupValues[3].toLongOrNull())
        }
        RANGE_NOT_SATISFIABLE -> {
            if (total == null && offset > 0) {
                RangeAnswer(pastTheEnd = true)
            } else {
                throw UnexpectedAnswer(RANGE_NOT_SATISFIABLE)
            }
        }
        else -> throw UnexpectedAnswer(response.code)
    }

/**
 * Runs [block], which sends this call and reads its answer, and cancels the call when the caller is
 * cancelled meanwhile, also while a read of the answer blocks, so a pause never waits for a server
 * that stalls. A read that the cancellation broke ends as the cancellation.
 */
private suspend fun <T> Call.cancelledWithCaller(block: suspend () -> T): T = coroutineScope {
    val call = this@cancelledWithCaller
    val done = AtomicBoolean(false)
    val watch =
        launch(start = CoroutineStart.UNDISPATCHED) {
            try {
                awaitCancellation()
            } finally {
                if (!done.get()) call.cancel()
            }
        }
    try {
        block()
    } catch (e: IOException) {
        currentCoroutineContext().ensureActive()
        throw e
    } finally {
        done.set(true)
        watch.cancel()
    }
}

/**
 * Sends the call on OkHttp's own threads and waits for its answer. Cancelling the caller before the
 * answer comes cancels the call.
 */
private suspend fun Call.await(): Response = suspendCancellableCoroutine { continuation ->
    continuation.invokeOnCancellation { cancel() }
    enqueue(
        object : Callback {
            override fun onResponse(call: Call, response: Response) {
                continuation.resume(response) { _, value, _ -> value.close() }
            }

            override fun onFailure(call: Call, e: IOException) {
                continuation.resumeWithException(e)
            }
        },
    )
}
