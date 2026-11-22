package io.github.dewfallapp.core.youtube.newpipe.live

import io.github.dewfallapp.core.logging.LogLevel
import io.github.dewfallapp.core.logging.LogWriter
import io.github.dewfallapp.core.logging.Logger
import io.github.dewfallapp.core.youtube.newpipe.NewPipeCalls
import io.github.dewfallapp.core.youtube.newpipe.NewPipeDownloader
import io.github.dewfallapp.core.youtube.newpipe.saved.ConsoleLogWriter
import io.github.dewfallapp.core.youtube.newpipe.saved.Endpoint
import io.github.dewfallapp.core.youtube.newpipe.saved.SAVED_SET_LOCALE
import io.github.dewfallapp.core.youtube.newpipe.saved.YouTubeLayer
import io.github.dewfallapp.core.youtube.newpipe.youTubeHttpClient
import java.io.File
import java.util.Collections
import java.util.concurrent.atomic.AtomicInteger
import kotlin.system.exitProcess
import kotlin.time.Clock
import kotlin.time.measureTimedValue
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.Response

/** The place of the argument that names the results file. */
private const val RESULTS_ARGUMENT = 0

/** The place of the argument that says whether to skip the calls that can't be checked. */
private const val SKIP_UNREACHABLE_ARGUMENT = 1

/** The place of the argument that names a kind to report as failed, if there is one. */
private const val SIMULATE_ARGUMENT = 2

/** The exit code of a run in which no kind failed. */
private const val PASSED_EXIT_CODE = 0

/** The exit code of a run in which a kind failed, or that couldn't run. */
private const val FAILED_EXIT_CODE = 1

/** How the task asks the check to run, read from main's arguments. */
internal class LiveCheckOptions(
    /** Whether a kind that couldn't be checked is skipped instead of failed. See [LiveCheck]. */
    val skipUnreachable: Boolean,
    /** The kind to report as failed, to test the workflow's issues, if any. */
    val simulate: Enum<*>?,
)

/**
 * Runs the live check of the YouTube layer against real YouTube and googlevideo.com. The task
 * liveYouTubeCheck in core/youtube/build.gradle.kts runs it, in the daily workflow or by hand, as
 * docs/testing.md#the-daily-youtube-check describes. Its arguments are the results file, `true` or
 * `false` for whether to skip the calls that can't be checked, and optionally the name of a kind to
 * report as failed, to test the workflow's issues.
 *
 * It logs each call and each kind's result through the Logger, with ConsoleLogWriter, and writes
 * one line for each kind to the results file. It exits with an error when a kind failed, when an
 * argument is wrong, or when the check stopped before it finished, which leaves no results file. It
 * files no issue: only the workflow's Report job does.
 */
fun main(args: Array<String>) {
    val logger = Logger(ConsoleLogWriter, LogLevel.INFO)
    // An exception that nothing catches, on this thread or another, would otherwise print its
    // message, which can hold a link, to standard error. The JVM still exits with an error.
    Thread.setDefaultUncaughtExceptionHandler(crashLogger(logger))
    val resultsFile = File(args[RESULTS_ARGUMENT])
    // A file from an earlier run would read as this run's results.
    resultsFile.delete()
    val options = optionsOf(args, logger) ?: exitProcess(FAILED_EXIT_CODE)
    val results = checkYouTube(logger, options)
    writeResults(resultsFile, results)
    exitProcess(exitCodeOf(results))
}

/**
 * The options in [args], or null after logging through [logger] which one is wrong. Neither value
 * is logged: they come from the command line, not from constant text.
 */
internal fun optionsOf(args: Array<String>, logger: Logger): LiveCheckOptions? {
    val log = logger.withTag(LIVE_CHECK_LOG_AREA)
    val skipUnreachable = args.getOrNull(SKIP_UNREACHABLE_ARGUMENT)?.toBooleanStrictOrNull()
    val simulateName = args.getOrNull(SIMULATE_ARGUMENT)
    val simulate = LIVE_KINDS.firstOrNull { it.name == simulateName }
    return when {
        skipUnreachable == null -> {
            log.error("Whether to skip the calls that can't be checked is not true or false")
            null
        }
        simulateName != null && simulate == null -> {
            log.error("The kind to report as failed is not a kind of the live check")
            null
        }
        else -> LiveCheckOptions(skipUnreachable, simulate)
    }
}

/** The exit code of a run with [results]: an error when any kind failed. */
internal fun exitCodeOf(results: List<KindResult>): Int =
    if (results.any { it.verdict == Verdict.FAILED }) FAILED_EXIT_CODE else PASSED_EXIT_CODE

/**
 * Runs the live check through the layer's own HTTP client and NewPipeDownloader, as the app sends
 * its requests, so the host allow list applies, with [options].
 */
private fun checkYouTube(logger: Logger, options: LiveCheckOptions): List<KindResult> {
    val requests = EndpointRecorder()
    val playability = PlayabilityRecorder()
    val layerLines = WarningCounter(ConsoleLogWriter)
    val youTubeClient =
        youTubeHttpClient()
            .newBuilder()
            .addInterceptor(requests)
            .addInterceptor(playability)
            .build()
    val bytesClient = firstBytesClient()
    try {
        val layerLogger = Logger(layerLines, LogLevel.INFO)
        val calls =
            NewPipeCalls(NewPipeDownloader(youTubeClient), layerLogger, Dispatchers.IO) {
                SAVED_SET_LOCALE
            }
        val caller =
            YouTubeCaller(YouTubeLayer(calls, layerLogger), requests, playability, layerLines)
        val check =
            LiveCheck(
                caller,
                FirstBytes(bytesClient, logger),
                Clock.System,
                logger,
                options.skipUnreachable,
            )
        return runBlocking { check.run(options.simulate) }
    } finally {
        // OkHttp's threads would keep the JVM running for a minute.
        youTubeClient.shutDown()
        bytesClient.shutDown()
    }
}

/** Writes [results] to [file], one line for each kind. */
internal fun writeResults(file: File, results: List<KindResult>) {
    file.parentFile?.mkdirs()
    file.writeText(formatResults(results))
}

/**
 * Logs an exception that nothing caught through [logger], as class names and stack frames only,
 * never its message. See ADR 0007.
 */
internal fun crashLogger(logger: Logger): Thread.UncaughtExceptionHandler {
    val log = logger.withTag(LIVE_CHECK_LOG_AREA)
    return Thread.UncaughtExceptionHandler { _, error ->
        log.error("The live check stopped before it finished", error = error)
    }
}

/**
 * Makes each call through [layer], and takes the requests it sent from [requests], what YouTube's
 * visionOS player answer said from [playability], and the number of warnings the layer logged from
 * [warnings].
 */
internal class YouTubeCaller(
    private val layer: YouTubeLayer,
    private val requests: EndpointRecorder,
    private val playability: PlayabilityRecorder,
    private val warnings: WarningCounter,
) : LayerCaller {
    override suspend fun call(liveCall: LiveCall): CallOutcome {
        requests.take()
        playability.take()
        val warningsBefore = warnings.count
        val (result, duration) = measureTimedValue { liveCall.call(layer) }
        return CallOutcome(
            result,
            requests.take(),
            warnings.count - warningsBefore,
            duration,
            playability.take(),
        )
    }
}

/** Keeps the endpoint of every request that the layer's HTTP client sends, in order. */
internal class EndpointRecorder : Interceptor {
    private val sent = Collections.synchronizedList(mutableListOf<Endpoint>())

    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()
        sent += Endpoint.of(request.method, request.url)
        return chain.proceed(request)
    }

    /** The endpoints kept since the last call, which it forgets. */
    fun take(): List<Endpoint> =
        synchronized(sent) {
            val taken = sent.toList()
            sent.clear()
            taken
        }
}

/** Writes each line to [writer], and counts the lines at WARN or above. */
internal class WarningCounter(private val writer: LogWriter) : LogWriter {
    private val warnings = AtomicInteger()

    /** The lines at WARN or above so far. */
    val count: Int
        get() = warnings.get()

    override fun write(level: LogLevel, tag: String, text: String) {
        if (level >= LogLevel.WARN) warnings.incrementAndGet()
        writer.write(level, tag, text)
    }
}

/** Shuts down [client]'s threads and connections. */
private fun OkHttpClient.shutDown() {
    dispatcher.executorService.shutdown()
    connectionPool.evictAll()
}
