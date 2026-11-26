package io.github.dewfallapp.core.download

import java.io.File
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import okhttp3.Call

/** Ranges of 100 KiB, so a test file of a few hundred KiB takes several. */
private const val RANGE = 100L * 1024

class FormatWriterTest {
    private val folder = Files.createTempDirectory("writer").toFile()
    private val part = File(folder, "video.part")
    // The writer's blocking work runs on a real thread, as on the phone.
    private val writer = FormatWriter(Dispatchers.IO, rangeBytes = RANGE)

    private suspend fun write(
        media: FakeMedia,
        knownLength: Long?,
        progress: WriteProgress = WriteProgress { _, _ -> },
    ) = writer.write(LINK, part, knownLength, media.calls, progress)

    @Test
    fun `a new file comes in ranges`() = runTest {
        val bytes = fileOf(250 * 1024)
        val media = FakeMedia(bytes)

        assertEquals(bytes.size.toLong(), write(media, knownLength = bytes.size.toLong()))

        assertContentEquals(bytes, part.readBytes())
        assertEquals(
            listOf<String?>("bytes=0-102399", "bytes=102400-204799", "bytes=204800-255999"),
            media.ranges,
        )
    }

    @Test
    fun `a partial file goes on from its length`() = runTest {
        val bytes = fileOf(250 * 1024)
        part.writeBytes(bytes.copyOfRange(0, 150_000))
        val media = FakeMedia(bytes)

        write(media, knownLength = bytes.size.toLong())

        assertContentEquals(bytes, part.readBytes())
        assertEquals("bytes=150000-252399", media.ranges.first())
    }

    @Test
    fun `a complete file asks for nothing`() = runTest {
        val bytes = fileOf(1_000)
        part.writeBytes(bytes)
        val media = FakeMedia(bytes)

        assertEquals(1_000, write(media, knownLength = 1_000))

        assertTrue(media.ranges.isEmpty())
    }

    @Test
    fun `an unknown length comes from the Content-Range`() = runTest {
        val bytes = fileOf(250 * 1024)
        val media = FakeMedia(bytes)

        assertEquals(bytes.size.toLong(), write(media, knownLength = null))

        assertContentEquals(bytes, part.readBytes())
        assertEquals(3, media.ranges.size)
    }

    @Test
    fun `a hidden total goes on past a short answer until 416`() = runTest {
        val bytes = fileOf(250 * 1024)
        val media = FakeMedia(bytes, hideTotal = true)

        assertEquals(bytes.size.toLong(), write(media, knownLength = null))

        assertContentEquals(bytes, part.readBytes())
        // The third range ended early, which says nothing about the file's end.
        assertEquals("bytes=256000-358399", media.ranges.last())
    }

    @Test
    fun `a short partial answer with a hidden total is not the end`() = runTest {
        val bytes = fileOf(1_000)
        val media =
            FakeMedia(bytes, hideTotal = true) { n, chain ->
                if (n == 0) {
                    status(chain, 206, bytes.copyOfRange(0, 500), contentRange = "bytes 0-499/*")
                } else {
                    null
                }
            }

        assertEquals(1_000, write(media, knownLength = null))

        assertContentEquals(bytes, part.readBytes())
        assertEquals("bytes=500-102899", media.ranges[1])
    }

    @Test
    fun `a short 200 doesn't replace a known length`() = runTest {
        val bytes = fileOf(5_000)
        val media =
            FakeMedia(bytes) { n, chain ->
                if (n == 0) status(chain, 200, bytes.copyOfRange(0, 2_000)) else null
            }

        assertEquals(5_000, write(media, knownLength = 5_000))

        assertContentEquals(bytes, part.readBytes())
        assertEquals("bytes=2000-4999", media.ranges[1])
    }

    @Test
    fun `a hidden total that ends on a range's edge ends at 416`() = runTest {
        val bytes = fileOf(2 * RANGE.toInt())
        val media = FakeMedia(bytes, hideTotal = true)

        assertEquals(bytes.size.toLong(), write(media, knownLength = null))

        assertContentEquals(bytes, part.readBytes())
        assertEquals("bytes=204800-307199", media.ranges.last())
    }

    @Test
    fun `403 and 410 mean the link expired, and the bytes stay`() = runTest {
        for (code in listOf(403, 410)) {
            part.delete()
            val bytes = fileOf(250 * 1024)
            val media =
                FakeMedia(bytes) { number, chain -> if (number == 1) status(chain, code) else null }

            assertFailsWith<LinkRefused> { write(media, knownLength = bytes.size.toLong()) }

            assertEquals(RANGE, part.length())
        }
    }

    @Test
    fun `an answer that starts at another byte is refused`() = runTest {
        val media =
            FakeMedia(fileOf(1_000)) { _, chain ->
                status(chain, 206, fileOf(10), contentRange = "bytes 5-14/1000")
            }

        val error = assertFailsWith<UnexpectedAnswer> { write(media, knownLength = 1_000) }

        assertEquals(206, error.status)
        assertEquals(0, part.length())
    }

    @Test
    fun `a 200 from the start is the whole file`() = runTest {
        val bytes = fileOf(5_000)
        val media = FakeMedia(bytes) { _, chain -> status(chain, 200, bytes) }

        assertEquals(5_000, write(media, knownLength = null))

        assertContentEquals(bytes, part.readBytes())
        assertEquals(1, media.ranges.size)
    }

    @Test
    fun `a 200 part way is refused`() = runTest {
        val bytes = fileOf(5_000)
        part.writeBytes(bytes.copyOfRange(0, 100))
        val media = FakeMedia(bytes) { _, chain -> status(chain, 200, bytes) }

        assertFailsWith<UnexpectedAnswer> { write(media, knownLength = 5_000) }

        assertEquals(100, part.length())
    }

    @Test
    fun `another status is refused with its code and no link`() = runTest {
        val media = FakeMedia(fileOf(1_000)) { _, chain -> status(chain, 500) }

        val error = assertFailsWith<UnexpectedAnswer> { write(media, knownLength = 1_000) }

        assertEquals(500, error.status)
        assertEquals("Unexpected media answer", error.message)
    }

    @Test
    fun `a server that sends more than the range can't grow the file`() = runTest {
        val bytes = fileOf(1_000)
        val media =
            FakeMedia(bytes) { _, chain ->
                // Claims the asked range but sends the whole file.
                status(chain, 206, bytes, contentRange = "bytes 0-499/500")
            }

        assertEquals(500, write(media, knownLength = 500))

        assertContentEquals(bytes.copyOfRange(0, 500), part.readBytes())
    }

    @Test
    fun `cancelling stops between buffers and keeps what was written`() = runTest {
        val bytes = fileOf(250 * 1024)
        val media = FakeMedia(bytes)

        assertFailsWith<CancellationException> {
            write(media, knownLength = bytes.size.toLong()) { written, _ ->
                if (written >= 150_000) throw CancellationException("Paused")
            }
        }

        assertTrue(part.length() in 150_000L..(150_000L + 64 * 1024))
        assertContentEquals(bytes.copyOfRange(0, part.length().toInt()), part.readBytes())
    }

    @Test
    fun `cancelling while the server stalls cancels the call`() = runTest {
        val reading = CompletableDeferred<Unit>()
        var stalledCall: Call? = null
        val media =
            FakeMedia(fileOf(1_000)) { _, chain ->
                stalledCall = chain.call()
                stalled(chain, reading)
            }

        withContext(Dispatchers.Default) {
            val writing = launch { write(media, knownLength = 1_000) }
            reading.await()
            writing.cancel()
            // Without the call's cancellation the read would block until its timeout.
            withTimeout(5.seconds) { writing.join() }
        }

        assertTrue(checkNotNull(stalledCall).isCanceled())
    }

    @Test
    fun `a finished file is synced once, with every byte, before write returns`() = runTest {
        val bytes = fileOf(250 * 1024)
        val synced = mutableListOf<Long>()
        val syncing = FormatWriter(Dispatchers.IO, rangeBytes = RANGE) { synced += part.length() }

        syncing.write(LINK, part, bytes.size.toLong(), FakeMedia(bytes).calls) { _, _ -> }

        assertEquals(listOf(bytes.size.toLong()), synced)
    }

    @Test
    fun `a stopped write isn't synced, because the file isn't complete`() = runTest {
        val bytes = fileOf(250 * 1024)
        val synced = mutableListOf<Long>()
        val syncing = FormatWriter(Dispatchers.IO, rangeBytes = RANGE) { synced += part.length() }

        assertFailsWith<CancellationException> {
            syncing.write(LINK, part, bytes.size.toLong(), FakeMedia(bytes).calls) { written, _ ->
                if (written >= 150_000) throw CancellationException("Paused")
            }
        }

        assertEquals(emptyList(), synced)
    }

    @Test
    fun `progress comes at most one buffer at a time`() = runTest {
        val bytes = fileOf(250 * 1024)
        val seen = mutableListOf<Long>()

        write(FakeMedia(bytes), knownLength = bytes.size.toLong()) { written, _ -> seen += written }

        val steps = seen.zipWithNext { a, b -> b - a }
        assertTrue(steps.all { it <= 64 * 1024 }, "A step was larger than one buffer: $steps")
        assertEquals(bytes.size.toLong(), seen.last())
    }

    @Test
    fun `a file longer than the format starts again`() = runTest {
        val bytes = fileOf(1_000)
        part.writeBytes(fileOf(2_000))

        write(FakeMedia(bytes), knownLength = 1_000)

        assertContentEquals(bytes, part.readBytes())
    }
}
