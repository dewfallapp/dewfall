package io.github.dewfallapp.core.youtube.newpipe.saved

import io.github.dewfallapp.core.logging.LogField
import io.github.dewfallapp.core.logging.LogLevel
import io.github.dewfallapp.core.logging.Logger
import io.github.dewfallapp.core.model.YouTubeError
import io.github.dewfallapp.core.model.YouTubeResult
import io.github.dewfallapp.core.youtube.newpipe.NewPipeCalls
import io.github.dewfallapp.core.youtube.newpipe.NewPipeDownloader
import io.github.dewfallapp.core.youtube.newpipe.youTubeHttpClient
import java.io.File
import java.time.LocalDate
import java.time.ZoneOffset
import java.util.Collections
import kotlin.system.exitProcess
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import okhttp3.Interceptor
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody

/** The area of the recorder's log tag, Dewfall.Recorder. */
private const val LOG_AREA = "Recorder"

/** The place of the recorder's argument that names the folder of the saved sets. */
private const val FOLDER_ARGUMENT = 0

/** The place of the argument that names the NewPipeExtractor version that the build uses. */
private const val VERSION_ARGUMENT = 1

/** The place of the first argument that names a set to record, if any does. */
private const val FIRST_SET_ARGUMENT = 2

/** The bytes in a kibibyte, the unit of a saved set's size in the log. */
private const val BYTES_PER_KIBIBYTE = 1024

/**
 * The set whose call the recorder also makes, without writing it, when no set it records names the
 * machine's address or the network's country: its stream links name the address, and its sw.js the
 * country.
 */
internal val LEARNING_SET = SavedSet.VIDEO_BIG_BUCK_BUNNY

/** Why a saved set failed the recorder's checks. */
private enum class FailedBecause {
    /** The call succeeded or failed when the set expects the other, or failed another way. */
    UNEXPECTED_RESULT,

    /** The call sent other requests than the set lists. */
    UNEXPECTED_REQUESTS,

    /** A real value was still in the set after masking. */
    UNMASKED_VALUE,
}

/** What a saved set's call returned, and every request it sent with YouTube's answer. */
internal class Recording(val error: YouTubeError?, val exchanges: List<SavedExchange>)

/**
 * Keeps every request that the recorder's client sends, and YouTube's answer, in memory. Nothing it
 * holds is ever written anywhere before it is masked and checked.
 */
private class RecordingInterceptor : Interceptor {
    val exchanges: MutableList<SavedExchange> = Collections.synchronizedList(mutableListOf())

    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()
        val response = chain.proceed(request)
        val contentType = response.body.contentType()
        val body = response.body.string()
        exchanges +=
            SavedExchange(
                request.toSavedRequest(),
                SavedResponse(
                    response.code,
                    response.headers.map { (name, value) -> Header(name, value) },
                    body,
                ),
            )
        return response.newBuilder().body(body.toResponseBody(contentType)).build()
    }
}

/**
 * Records the saved sets that the tests replay, from real YouTube. The task recordYouTubeResponses
 * in core/youtube/build.gradle.kts runs it, and docs/testing.md#recording-the-sets says when. Its
 * arguments are the folder of the saved sets, the NewPipeExtractor version that the build uses, and
 * the names of the sets to record, or none for every set. It exits with an error if it wrote no
 * set.
 */
fun main(args: Array<String>) {
    val folder = File(args[FOLDER_ARGUMENT])
    val newPipeExtractorVersion = args[VERSION_ARGUMENT]
    val sets = args.drop(FIRST_SET_ARGUMENT).map(SavedSet::valueOf).ifEmpty { SavedSet.entries }
    val logger = Logger(ConsoleLogWriter, LogLevel.INFO)
    if (!recordSets(sets, folder, newPipeExtractorVersion, logger, ::callRecorded)) exitProcess(1)
}

/**
 * Records [sets] into their folders in [folder], with [call], and returns whether it wrote them. It
 * logs through [logger], with the tag Dewfall.Recorder.
 *
 * It keeps YouTube's answers in memory only. It collects the real values from every set first: the
 * machine's address, the session's values, the network's country and data about viewers. If no set
 * names the address or the country, it also calls [LEARNING_SET], keeps only those values, and
 * writes nothing of it, and if they are still unknown, it writes nothing at all, because the check
 * couldn't look for them. Then, for each set, it masks every real value, and checks the masked set
 * for the real values of the whole run, the call's result and the requests it sent. Only when every
 * set passes does it write any file, so a run never writes a set that holds a real value it knows,
 * and never writes only some of the sets it was asked for, unless the disk fails while it writes
 * them. The log lines name only sets, kinds, endpoints and counts.
 */
internal fun recordSets(
    sets: List<SavedSet>,
    folder: File,
    newPipeExtractorVersion: String,
    logger: Logger,
    call: (SavedSet) -> Recording,
): Boolean {
    val log = logger.withTag(LOG_AREA)
    log.info("Recording saved sets from YouTube", LogField.count("sets", sets.size))
    val recordings = sets.map { it to call(it) }
    val passed = realValuesOfRun(recordings, call, log)?.let { checkedSets(recordings, it, log) }
    if (passed != null) {
        writeSets(passed, folder, newPipeExtractorVersion, log)
        log.info("Recorded every set. Run the tests, then review git diff --stat")
    }
    return passed != null
}

/**
 * The real values of every set in [recordings], or null if they don't name the machine's address
 * and the network's country even with [LEARNING_SET]'s, which it calls with [call] only then.
 */
private fun realValuesOfRun(
    recordings: List<Pair<SavedSet, Recording>>,
    call: (SavedSet) -> Recording,
    log: Logger,
): RealValues? {
    var real =
        recordings.fold(RealValues()) { values, (_, recording) ->
            values + realValuesOf(recording.exchanges)
        }
    if (!real.isComplete) {
        log.info(
            "No set names the address and the country, so calling another set to learn them",
            LogField.state("set", LEARNING_SET),
        )
        real += realValuesOf(call(LEARNING_SET).exchanges)
    }
    if (!real.isComplete) {
        log.error("Wrote no saved set, because no answer named the address or the country")
    }
    return real.takeIf { it.isComplete }
}

/**
 * Each set in [recordings], masked for the [real] values of the run, or null if any fails its
 * checks.
 */
private fun checkedSets(
    recordings: List<Pair<SavedSet, Recording>>,
    real: RealValues,
    log: Logger,
): List<Pair<SavedSet, MaskedExchanges>>? {
    val passed = mutableListOf<Pair<SavedSet, MaskedExchanges>>()
    var failed = 0
    for ((set, recording) in recordings) {
        val masked = mask(recording.exchanges, real)
        val failedBecause = failureOf(set, recording, masked, real, log)
        if (failedBecause == null) {
            passed += set to masked
        } else {
            failed++
            log.warn(
                "A saved set failed its checks",
                LogField.state("set", set),
                LogField.state("because", failedBecause),
            )
        }
    }
    if (failed > 0) {
        log.error(
            "Wrote no saved set, because some failed their checks",
            LogField.count("failed", failed),
        )
    }
    return passed.takeIf { failed == 0 }
}

/**
 * Writes the [passed] sets into their folders in [folder], recorded today with
 * [newPipeExtractorVersion], and logs what each holds.
 */
private fun writeSets(
    passed: List<Pair<SavedSet, MaskedExchanges>>,
    folder: File,
    newPipeExtractorVersion: String,
    log: Logger,
) {
    val recordedOn = LocalDate.now(ZoneOffset.UTC)
    // Every set's files, before any is written, so a set that can't be written as files stops the
    // run before it changes anything.
    val files = passed.map { (set, masked) ->
        formatSavedSet(set, SavedSetContent(recordedOn, newPipeExtractorVersion, masked.exchanges))
    }
    for ((position, setAndMasked) in passed.withIndex()) {
        val (set, masked) = setAndMasked
        save(set, folder, files[position], masked.exchanges.size, log)
        for ((kind, count) in masked.counts) {
            log.info(
                "Masked values in a saved set",
                LogField.state("set", set),
                LogField.state("value", kind),
                LogField.count("count", count),
            )
        }
    }
}

/**
 * Why the recording of [set], masked as [masked], fails the recorder's checks for the [real] values
 * of the run, or null if it passes. When the call sent other requests than [set] lists, it logs the
 * ones it sent, so the list can be changed.
 */
private fun failureOf(
    set: SavedSet,
    recording: Recording,
    masked: MaskedExchanges,
    real: RealValues,
    log: Logger,
): FailedBecause? {
    val sent = recording.exchanges.map { Endpoint.of(it.request) }
    val unmasked = unmaskedKinds(real, masked.exchanges)
    return when {
        recording.error != set.expectedError -> {
            logResult(set, recording.error, log)
            FailedBecause.UNEXPECTED_RESULT
        }
        sent != set.requests -> {
            for ((position, endpoint) in sent.withIndex()) {
                log.warn(
                    "A saved set's call sent a request",
                    LogField.state("set", set),
                    LogField.count("position", position + 1),
                    LogField.state("endpoint", endpoint),
                )
            }
            log.warn(
                "A saved set's call sent other requests than the set lists",
                LogField.state("set", set),
                LogField.count("listed", set.requests.size),
                LogField.count("sent", sent.size),
            )
            FailedBecause.UNEXPECTED_REQUESTS
        }
        unmasked.isNotEmpty() -> {
            for (kind in unmasked) {
                log.warn(
                    "A real value is still in a masked set",
                    LogField.state("set", set),
                    LogField.state("value", kind),
                )
            }
            FailedBecause.UNMASKED_VALUE
        }
        else -> null
    }
}

/**
 * Makes the call of [set] through the layer's own HTTP client and NewPipeDownloader, after the same
 * reset of NewPipeExtractor's state that the tests make, and keeps what it sent and received. The
 * layer and NewPipeExtractor log nothing here.
 */
private fun callRecorded(set: SavedSet): Recording {
    resetNewPipeExtractorState()
    val recorder = RecordingInterceptor()
    val client = youTubeHttpClient().newBuilder().addInterceptor(recorder).build()
    try {
        val calls =
            NewPipeCalls(NewPipeDownloader(client), Logger.Silent, Dispatchers.IO) {
                SAVED_SET_LOCALE
            }
        val result = runBlocking { set.call(YouTubeLayer(calls, Logger.Silent)) }
        val exchanges = synchronized(recorder.exchanges) { recorder.exchanges.toList() }
        return Recording((result as? YouTubeResult.Failure)?.error, exchanges)
    } finally {
        // OkHttp's threads would keep the JVM running for a minute after the last set.
        client.dispatcher.executorService.shutdown()
        client.connectionPool.evictAll()
    }
}

/**
 * Writes [files], masked and checked, as the files of [set] in its folder in [folder], and logs the
 * set's [requests] and size.
 */
private fun save(
    set: SavedSet,
    folder: File,
    files: Map<String, String>,
    requests: Int,
    log: Logger,
) {
    write(File(folder, set.directory), files)
    log.info(
        "Recorded a saved set",
        LogField.state("set", set),
        LogField.count("requests", requests),
        LogField.count(
            "kibibytes",
            files.values.sumOf { it.toByteArray().size } / BYTES_PER_KIBIBYTE,
        ),
    )
}

/**
 * Logs how the call of [set] ended, failing with [error] or succeeding, when the set expects
 * otherwise.
 */
private fun logResult(set: SavedSet, error: YouTubeError?, log: Logger) {
    if (error == null) {
        log.warn("A saved set's call succeeded", LogField.state("set", set))
    } else {
        log.warn(
            "A saved set's call failed",
            LogField.state("set", set),
            LogField.state("error", error),
        )
    }
}

/** Replaces the files in [directory] with [files], by name. */
private fun write(directory: File, files: Map<String, String>) {
    directory.mkdirs()
    for (old in directory.listFiles().orEmpty()) {
        if (old.isFile) check(old.delete()) { "Couldn't delete an old file of a saved set" }
    }
    for ((name, text) in files) {
        File(directory, name).writeText(text)
    }
}
