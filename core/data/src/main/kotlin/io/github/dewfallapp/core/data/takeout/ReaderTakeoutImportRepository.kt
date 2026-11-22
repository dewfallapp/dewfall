package io.github.dewfallapp.core.data.takeout

import io.github.dewfallapp.core.data.subscriptions.SubscriptionsRepository
import io.github.dewfallapp.core.logging.LogField
import io.github.dewfallapp.core.logging.Logger
import io.github.dewfallapp.core.model.Channel
import io.github.dewfallapp.core.model.TakeoutImportFailure
import io.github.dewfallapp.core.model.TakeoutImportResult
import io.github.dewfallapp.core.takeout.TakeoutFile
import io.github.dewfallapp.core.takeout.TakeoutReader
import java.io.FilterInputStream
import java.io.IOException
import java.io.InputStream
import javax.inject.Inject
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Job
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.job
import kotlinx.coroutines.withContext

/**
 * Reads the picked file with [TakeoutReader] on [dispatcher], then subscribes through
 * [SubscriptionsRepository.subscribeAll], which holds the clock and the application scope.
 */
internal class ReaderTakeoutImportRepository
@Inject
constructor(
    private val opener: DocumentOpener,
    private val subscriptions: SubscriptionsRepository,
    @TakeoutReadDispatcher private val dispatcher: CoroutineDispatcher,
    logger: Logger,
) : TakeoutImportRepository {
    private val log = logger.withTag("Takeout")

    override suspend fun importFrom(file: String): TakeoutImportResult =
        when (val reading = read(file)) {
            // A file that couldn't be opened or read was logged as a warning already.
            null -> TakeoutImportResult.Failed(TakeoutImportFailure.NOT_READABLE)
            is TakeoutFile.Subscriptions -> subscribe(reading)
            TakeoutFile.NoSubscriptions -> failed(TakeoutImportFailure.NO_CHANNELS)
            TakeoutFile.TooLarge -> failed(TakeoutImportFailure.TOO_LARGE)
        }

    private suspend fun subscribe(reading: TakeoutFile.Subscriptions): TakeoutImportResult {
        val channels = reading.channels.map { Channel(it.id, it.name, emptyList(), "") }
        val added = subscriptions.subscribeAll(channels)
        log.info(
            "Import subscribed",
            LogField.count("channels", channels.size),
            LogField.count("added", added),
            LogField.count("unreadableLines", reading.unreadableLines),
        )
        return TakeoutImportResult.Imported(channels, added, reading.unreadableLines)
    }

    private fun failed(reason: TakeoutImportFailure): TakeoutImportResult {
        log.info("Import subscribed to nothing", LogField.state("reason", reason))
        return TakeoutImportResult.Failed(reason)
    }

    /** The file's reading, or null when it couldn't be opened or read. */
    private suspend fun read(file: String): TakeoutFile? =
        try {
            withContext(dispatcher) {
                val job = coroutineContext.job
                openOrNull(file)?.use { TakeoutReader.read(CancellableStream(it, job)) }
            }
        } catch (error: IOException) {
            log.warn("Import couldn't read the file", error = error)
            null
        }

    private fun openOrNull(file: String): InputStream? =
        try {
            opener.open(file)
        } catch (error: SecurityException) {
            log.warn("Import couldn't open the file", error = error)
            null
        } catch (error: IllegalArgumentException) {
            log.warn("Import couldn't open the file", error = error)
            null
        }
}

/**
 * Checks [job] before each read, and throws its CancellationException once it is cancelled, so a
 * blocking read of a large file stops at the next chunk when the caller leaves.
 */
private class CancellableStream(input: InputStream, private val job: Job) :
    FilterInputStream(input) {
    override fun read(): Int {
        job.ensureActive()
        return super.read()
    }

    override fun read(b: ByteArray, off: Int, len: Int): Int {
        job.ensureActive()
        return super.read(b, off, len)
    }

    override fun skip(n: Long): Long {
        job.ensureActive()
        return super.skip(n)
    }
}
