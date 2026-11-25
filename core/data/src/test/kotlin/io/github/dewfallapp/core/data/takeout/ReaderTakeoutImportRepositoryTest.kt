package io.github.dewfallapp.core.data.takeout

import io.github.dewfallapp.core.data.subscriptions.SubscriptionsRepository
import io.github.dewfallapp.core.logging.LogLevel
import io.github.dewfallapp.core.logging.Logger
import io.github.dewfallapp.core.model.Channel
import io.github.dewfallapp.core.model.ChannelId
import io.github.dewfallapp.core.model.ChannelOverview
import io.github.dewfallapp.core.model.SubscribedChannel
import io.github.dewfallapp.core.model.TakeoutImportFailure
import io.github.dewfallapp.core.model.TakeoutImportResult
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.FileNotFoundException
import java.io.FilterInputStream
import java.io.InputStream
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.zip.ZipEntry
import java.util.zip.ZipException
import java.util.zip.ZipOutputStream
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runTest

// Made-up channels: no real channel has these IDs or names.
private const val ID_1 = "UCtakeouttest00000000001"
private const val ID_2 = "UCtakeouttest00000000002"
private const val FILE = "content://example.invalid/document/made-up-takeout.zip"
private val CSV =
    """
    Channel Id,Channel Url,Channel Title
    $ID_1,http://www.youtube.com/channel/$ID_1,Made-up Bakery
    $ID_2,http://www.youtube.com/channel/$ID_2,Made-up Observatory
    not a channel,x,y
    """
        .trimIndent()

/** Records subscribeAll's channels, and says which IDs were subscribed already. */
private class FakeSubscriptions(private val existing: Set<String> = emptySet()) :
    SubscriptionsRepository {
    val batches = mutableListOf<List<Channel>>()

    override suspend fun subscribeAll(channels: List<Channel>): Int {
        batches += channels
        return channels.count { it.id.value !in existing }
    }

    override fun subscribedChannels(): Flow<List<SubscribedChannel>> = emptyFlow()

    override fun channelOverviews(): Flow<List<ChannelOverview>> = emptyFlow()

    override fun subscribedChannel(channelId: ChannelId): Flow<SubscribedChannel?> = emptyFlow()

    override fun isSubscribed(channelId: ChannelId): Flow<Boolean> = emptyFlow()

    override suspend fun subscribe(channel: Channel) = error("Not used by the import")

    override suspend fun unsubscribe(channelId: ChannelId) = error("Not used by the import")

    override suspend fun moveSubscription(from: ChannelId, to: Channel) =
        error("Not used by the import")
}

/** Opens every file with [open], and remembers the streams it gave and whether they closed. */
private class FakeOpener(private val open: () -> InputStream?) : DocumentOpener {
    val opened = mutableListOf<String>()
    val streams = mutableListOf<ClosingStream>()

    override fun open(file: String): InputStream? {
        opened += file
        return open()?.let { ClosingStream(it).also(streams::add) }
    }
}

private class ClosingStream(input: InputStream) : FilterInputStream(input) {
    var closed = false

    override fun close() {
        closed = true
        super.close()
    }
}

/** Every line the repository logs. */
private class RecordedLog {
    val lines = mutableListOf<String>()
    val logger = Logger({ _, tag, text -> lines += "$tag $text" }, LogLevel.DEBUG)
}

class ReaderTakeoutImportRepositoryTest {
    private val log = RecordedLog()

    private fun repository(
        opener: DocumentOpener,
        subscriptions: SubscriptionsRepository = FakeSubscriptions(),
        dispatcher: CoroutineDispatcher,
    ) = ReaderTakeoutImportRepository(opener, subscriptions, dispatcher, log.logger)

    @Test
    fun `subscribes to the file's channels by ID and name, and counts them`() = runTest {
        val subscriptions = FakeSubscriptions(existing = setOf(ID_2))
        val opener = FakeOpener { ByteArrayInputStream(CSV.toByteArray()) }
        val repository = repository(opener, subscriptions, StandardTestDispatcher(testScheduler))

        val result = repository.importFrom(FILE)

        val expected =
            listOf(
                Channel(ChannelId(ID_1), "Made-up Bakery", emptyList(), ""),
                Channel(ChannelId(ID_2), "Made-up Observatory", emptyList(), ""),
            )
        assertEquals(TakeoutImportResult.Imported(expected, added = 1, unreadableLines = 1), result)
        assertEquals(1, (result as TakeoutImportResult.Imported).alreadySubscribed)
        assertEquals(listOf(expected), subscriptions.batches)
        assertEquals(listOf(FILE), opener.opened)
        assertTrue(opener.streams.single().closed)
    }

    @Test
    fun `a zip file is read too`() = runTest {
        val zip = ByteArrayOutputStream()
        ZipOutputStream(zip).use {
            it.putNextEntry(ZipEntry("Takeout/subscriptions/subscriptions.csv"))
            it.write(CSV.toByteArray())
        }
        val opener = FakeOpener { ByteArrayInputStream(zip.toByteArray()) }

        val result =
            repository(opener, dispatcher = StandardTestDispatcher(testScheduler)).importFrom(FILE)

        assertEquals(2, (result as TakeoutImportResult.Imported).channels.size)
    }

    @Test
    fun `a file with no channels subscribes to nothing`() = runTest {
        val subscriptions = FakeSubscriptions()
        val opener = FakeOpener { ByteArrayInputStream("a,b\nc,d\n".toByteArray()) }

        val result =
            repository(opener, subscriptions, StandardTestDispatcher(testScheduler))
                .importFrom(FILE)

        assertEquals(TakeoutImportResult.Failed(TakeoutImportFailure.NO_CHANNELS), result)
        assertEquals(emptyList(), subscriptions.batches)
        assertTrue(opener.streams.single().closed)
    }

    @Test
    fun `a file that is too large subscribes to nothing`() = runTest {
        val subscriptions = FakeSubscriptions()
        val opener = FakeOpener {
            ByteArrayInputStream(ByteArray(9 * 1024 * 1024) { 'x'.code.toByte() })
        }

        val result =
            repository(opener, subscriptions, StandardTestDispatcher(testScheduler))
                .importFrom(FILE)

        assertEquals(TakeoutImportResult.Failed(TakeoutImportFailure.TOO_LARGE), result)
        assertEquals(emptyList(), subscriptions.batches)
    }

    @Test
    fun `a file that can't be opened or read is not readable`() = runTest {
        val failures =
            listOf<() -> InputStream?>(
                { null },
                { throw FileNotFoundException("made-up-takeout.zip not found") },
                { throw SecurityException("no access to made-up-takeout.zip") },
                { throw IllegalArgumentException("bad URI made-up-takeout.zip") },
                { damagedZip() },
            )
        for (failure in failures) {
            val subscriptions = FakeSubscriptions()
            val result =
                repository(
                        FakeOpener(failure),
                        subscriptions,
                        StandardTestDispatcher(testScheduler),
                    )
                    .importFrom(FILE)

            assertEquals(TakeoutImportResult.Failed(TakeoutImportFailure.NOT_READABLE), result)
            assertEquals(emptyList(), subscriptions.batches)
        }
    }

    @Test
    fun `a ZipException is not readable, and its message isn't logged`() = runTest {
        val opener = FakeOpener {
            object : InputStream() {
                override fun read(): Int = throw ZipException("entry Made-up Bakery/$ID_1.csv")
            }
        }

        val result =
            repository(opener, dispatcher = StandardTestDispatcher(testScheduler)).importFrom(FILE)

        assertEquals(TakeoutImportResult.Failed(TakeoutImportFailure.NOT_READABLE), result)
        assertTrue(log.lines.any { "ZipException" in it }, "the error's class is logged")
        assertNothingPrivateLogged()
    }

    @Test
    fun `nothing logged holds a channel, a name or the file`() = runTest {
        val opener = FakeOpener { ByteArrayInputStream(CSV.toByteArray()) }
        repository(opener, dispatcher = StandardTestDispatcher(testScheduler)).importFrom(FILE)
        repository(
                FakeOpener { throw SecurityException(FILE) },
                dispatcher = StandardTestDispatcher(testScheduler),
            )
            .importFrom(FILE)

        // One line for the import, and one warning for the failed open.
        assertEquals(2, log.lines.size)
        assertNothingPrivateLogged()
    }

    @Test
    fun `leaving while the file is read stops the read and subscribes to nothing`() = runTest {
        val subscriptions = FakeSubscriptions()
        val stream = EndlessCsv()
        val repository = repository(FakeOpener { stream }, subscriptions, Dispatchers.IO)

        val caller = launch(Dispatchers.Default) { repository.importFrom(FILE) }
        assertTrue(stream.started.await(10, TimeUnit.SECONDS), "the read started")
        caller.cancelAndJoin()

        assertEquals(emptyList(), subscriptions.batches)
        val readsAtCancel = stream.reads
        Thread.sleep(100)
        assertEquals(readsAtCancel, stream.reads, "no read after the cancellation")
    }

    private fun assertNothingPrivateLogged() {
        for (line in log.lines) {
            for (secret in listOf(ID_1, ID_2, "Made-up", "made-up-takeout", "example.invalid")) {
                assertFalse(secret in line, "a log line holds user data")
            }
        }
    }
}

/** A .zip file cut short inside its only entry. */
private fun damagedZip(): InputStream {
    val zip = ByteArrayOutputStream()
    ZipOutputStream(zip).use {
        it.putNextEntry(ZipEntry("subscriptions.csv"))
        it.write(CSV.repeat(50).toByteArray())
    }
    val bytes = zip.toByteArray()
    return ByteArrayInputStream(bytes.copyOf(bytes.size / 2))
}

/**
 * A .csv file that never ends: its header, then blank lines, one small chunk per read, slowly, so a
 * test can cancel while it is read.
 */
private class EndlessCsv : InputStream() {
    val started = CountDownLatch(1)

    @Volatile var reads = 0

    private val header = "Channel Id,Channel Url,Channel Title\n".toByteArray()

    override fun read(): Int = throw UnsupportedOperationException()

    override fun read(b: ByteArray, off: Int, len: Int): Int {
        reads++
        started.countDown()
        Thread.sleep(5)
        if (reads == 1) {
            header.copyInto(b, off)
            return header.size
        }
        b[off] = '\n'.code.toByte()
        return 1
    }
}
