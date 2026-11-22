package io.github.dewfallapp.core.database

import android.os.Bundle
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import io.github.dewfallapp.core.model.VideoKind
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.hours
import kotlin.time.TimeSource
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assume.assumeTrue
import org.junit.runner.RunWith

/** The heavy user's data: this many subscribed channels, each with this many saved videos. */
private const val CHANNELS = 300
private const val VIDEOS_PER_CHANNEL = 300

/** A watch state for every this many videos, a tenth of them. */
private const val WATCH_STATE_EVERY = 10

/** The runs that count, after one warm-up run. */
private const val RUNS = 5

/** The bound for one run of the inbox query, as docs/testing.md#database-tests says. */
private const val BOUND_MS = 250L

/**
 * Times the inbox query on a heavy user's data, as docs/testing.md#database-tests describes. It is
 * skipped unless the instrumentation argument `inboxTiming` is `true`, because it takes a while and
 * its time depends on the device. Run it by hand, as docs/testing.md#database-tests says. It
 * reports the median in milliseconds as the instrumentation status `inbox_median_ms`, and fails
 * when the median is at or over the bound.
 */
@RunWith(AndroidJUnit4::class)
class InboxQueryTimingTest {
    private val database = inMemoryDatabase()

    @AfterTest fun closeDatabase() = database.close()

    @Test
    fun theInboxQueryStaysUnderItsBound() = runBlocking {
        val arguments = InstrumentationRegistry.getArguments()
        assumeTrue(arguments.getString("inboxTiming") == "true")
        fill()
        val dao = database.videoDao()
        dao.inboxVideos(VideoKind.VIDEO).first()
        val times =
            (1..RUNS).map {
                val mark = TimeSource.Monotonic.markNow()
                dao.inboxVideos(VideoKind.VIDEO).first()
                mark.elapsedNow().inWholeMilliseconds
            }
        val median = times.sorted()[RUNS / 2]
        val status = Bundle()
        status.putLong("inbox_median_ms", median)
        status.putString("inbox_runs_ms", times.joinToString(","))
        InstrumentationRegistry.getInstrumentation().sendStatus(0, status)
        assertTrue(median < BOUND_MS, "The median was $median ms, at or over $BOUND_MS ms")
    }

    /**
     * Subscribes [CHANNELS] channels on MONDAY, then saves [VIDEOS_PER_CHANNEL] videos for each on
     * TUESDAY, one every 6 hours back from WEDNESDAY, so a few of each channel's are new, and gives
     * every [WATCH_STATE_EVERY]th video a position, half of them marked watched.
     */
    private suspend fun fill() {
        val channels = database.channelDao()
        val videos = database.videoDao()
        val watch = database.watchStateDao()
        for (c in 0 until CHANNELS) {
            val channelId = "UC" + c.toString().padStart(22, '0')
            channels.subscribe(channel(id = channelId, subscribedAt = MONDAY))
            val saved =
                (0 until VIDEOS_PER_CHANNEL).map { n ->
                    video(
                        id = videoId(c * VIDEOS_PER_CHANNEL + n),
                        channelId = channelId,
                        publishedAt = WEDNESDAY - (n * 6).hours,
                        approximate = n > 4,
                    )
                }
            videos.saveChannelUploads(
                details(id = channelId),
                listed(TUESDAY, *saved.toTypedArray()),
            )
        }
        for (n in 0 until CHANNELS * VIDEOS_PER_CHANNEL step WATCH_STATE_EVERY) {
            watch.savePosition(videoId(n), positionMs = 60_000, now = TUESDAY + 1.hours)
            if (n % (2 * WATCH_STATE_EVERY) == 0) watch.markWatched(videoId(n), TUESDAY + 2.hours)
        }
    }
}
