package io.github.dewfallapp.core.youtube.newpipe.live

import io.github.dewfallapp.core.logging.LogLevel
import io.github.dewfallapp.core.logging.Logger
import io.github.dewfallapp.core.model.AudioFormat
import io.github.dewfallapp.core.model.ChannelId
import io.github.dewfallapp.core.model.StreamLinks
import io.github.dewfallapp.core.model.VideoDetails
import io.github.dewfallapp.core.model.VideoFormat
import io.github.dewfallapp.core.model.VideoId
import io.github.dewfallapp.core.model.YouTubeResult
import io.github.dewfallapp.core.youtube.newpipe.saved.BIG_BUCK_BUNNY_ID
import io.github.dewfallapp.core.youtube.newpipe.saved.Endpoint
import io.github.dewfallapp.core.youtube.newpipe.saved.SavedSet
import io.github.dewfallapp.core.youtube.newpipe.saved.SavedYouTube
import java.io.IOException
import kotlin.test.Test
import kotlin.test.assertTrue
import kotlin.time.Clock
import kotlin.time.Duration
import kotlin.time.Duration.Companion.days
import kotlin.time.Instant
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest

/** Text that stands for user data. No log line or result may hold it. */
private const val SENTINEL = "SENTINEL"

/** A made-up public IP address in a link, as googlevideo.com links hold the viewer's. */
private const val ADDRESS = "203.0.113.7"

/** A made-up stream link that holds both. */
private const val LINK =
    "https://rr1---sn-$SENTINEL.googlevideo.com/videoplayback?ip=$ADDRESS&id=$SENTINEL&c=ANDROID"

/** The clock of the check, long before the made-up links expire. */
private val CLOCK: Clock =
    object : Clock {
        override fun now(): Instant = Instant.fromEpochSeconds(0)
    }

/** When the made-up links expire. */
private val EXPIRES_AT = Instant.fromEpochSeconds(0) + 1.days

/**
 * Checks that the live check keeps ADR 0007's rules, because the daily workflow's log is public.
 * The calls return made-up content full of [SENTINEL], the requests for first bytes fail with an
 * exception whose message names a link, and no line may hold either.
 */
class LiveCheckOutputTest {
    private val lines = mutableListOf<String>()
    private val logger =
        Logger({ level, tag, text -> lines += "$level $tag $text" }, LogLevel.DEBUG)

    @Test
    fun `no log line or result holds a link, an ID, a title or an exception's message`() = runTest {
        val failing =
            firstBytesClient()
                .newBuilder()
                .addInterceptor { throw IOException("Failed to connect to $LINK") }
                .build()

        val results =
            LiveCheck(
                    sentinelCaller(layerLogger = logger),
                    FirstBytes(failing, logger),
                    CLOCK,
                    logger,
                )
                .run()

        val text = lines + results.map { it.line }
        for (forbidden in listOf(SENTINEL, ADDRESS, BIG_BUCK_BUNNY_ID, "googlevideo.com", "://")) {
            assertTrue(text.none { forbidden in it }, forbidden)
        }
        assertTrue(results.all { RESULT_LINE.matches(it.line) })
        // The requests for first bytes failed, and their exception was logged by its class.
        assertTrue(lines.any { it.startsWith("WARN Dewfall.FirstBytes") })
        assertTrue(lines.any { "java.io.IOException" in it })
    }

    @Test
    fun `every line that the check logs is constant text with typed fields`() = runTest {
        val playable = FirstBytesFetcher { link, _ ->
            FirstBytesResult(FirstBytesAnswer.PLAYABLE, clientOf(link), 206)
        }

        LiveCheck(sentinelCaller(layerLogger = Logger.Silent), playable, CLOCK, logger).run()

        val shape =
            Regex(
                "(INFO|WARN|ERROR) Dewfall\\.LiveCheck [A-Z][A-Za-z' ]*?" +
                    "( [a-z][A-Za-z]*=([A-Z0-9_]+|PT[0-9.]+S|[0-9]+|true|false))*"
            )
        assertTrue(lines.isNotEmpty())
        for (line in lines) {
            assertTrue(shape.matches(line), line)
        }
    }

    /**
     * Answers Big Buck Bunny's details and stream links with made-up content full of [SENTINEL],
     * after the requests that their saved set expects, and every other call from its saved set,
     * through a layer that logs through [layerLogger].
     */
    private fun TestScope.sentinelCaller(layerLogger: Logger) = LayerCaller { liveCall ->
        val requests = liveCall.set.requests
        when (liveCall.kind) {
            SavedSet.VIDEO_BIG_BUCK_BUNNY -> success(sentinelDetails(), requests)
            LiveKind.STREAM_LINKS_BIG_BUCK_BUNNY -> success(sentinelLinks(), requests)
            else -> {
                val youTube =
                    SavedYouTube(liveCall.set, layerLogger, StandardTestDispatcher(testScheduler))
                CallOutcome(youTube.call(liveCall.call), youTube.sentRequests, 0, Duration.ZERO)
            }
        }
    }

    @Test
    fun `an exception that nothing caught is logged without its message`() {
        crashLogger(logger)
            .uncaughtException(Thread.currentThread(), IllegalStateException("$SENTINEL $LINK"))

        assertTrue(lines.first().startsWith("ERROR Dewfall.LiveCheck The live check stopped"))
        assertTrue("java.lang.IllegalStateException" in lines.first())
        assertTrue(lines.none { SENTINEL in it || ADDRESS in it })
    }
}

/** Big Buck Bunny's details with made-up text in place of its title and description. */
private fun sentinelDetails() =
    VideoDetails(
        id = VideoId(BIG_BUCK_BUNNY_ID),
        title = "$SENTINEL title",
        channelId = ChannelId("UC$SENTINEL${SENTINEL}SENTIN"),
        channelName = "$SENTINEL channel",
        channelAvatars = emptyList(),
        description = "$SENTINEL description $LINK",
        duration = null,
        published = null,
        thumbnails = emptyList(),
        isLive = false,
    )

/** Stream links whose every link is [LINK], so the check gets as far as asking for first bytes. */
private fun sentinelLinks() =
    StreamLinks(
        videoId = VideoId(BIG_BUCK_BUNNY_ID),
        audioFormats =
            listOf(AudioFormat(LINK, 140, "audio/mp4", null, null, contentLength = 5000, null)),
        videoFormats =
            listOf(
                VideoFormat(LINK, 137, "video/mp4", null, null, 9000, null, null, null, false),
                VideoFormat(LINK, 18, "video/mp4", null, null, 9000, null, null, null, true),
            ),
        hlsManifestUrl = LINK,
        expiresAt = EXPIRES_AT,
    )

/** A call that succeeded with [value] after [requests]. */
private fun success(value: Any, requests: List<Endpoint>) =
    CallOutcome(YouTubeResult.Success(value), requests, warnings = 0, Duration.ZERO)
