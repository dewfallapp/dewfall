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

/** Every this many videos, one is an upcoming premiere, which the query skips. */
private const val UPCOMING_EVERY = 50

/** The runs that count, after one warm-up run. */
private const val RUNS = 5

/** The bound for one run of the query, as docs/testing.md#database-tests says. */
private const val BOUND_MS = 100L

/**
 * Times the Channels screen's query on a heavy user's data, as docs/testing.md#database-tests
 * describes. It is skipped unless the instrumentation argument `channelsTiming` is `true`, because
 * it takes a while and its time depends on the device. It reports the median in milliseconds as the
 * instrumentation status `channels_median_ms`, and fails when the median is at or over the bound.
 */
@RunWith(AndroidJUnit4::class)
class ChannelOverviewsTimingTest {
    private val database = inMemoryDatabase()

    @AfterTest fun closeDatabase() = database.close()

    @Test
    fun theChannelsQueryStaysUnderItsBound() = runBlocking {
        val arguments = InstrumentationRegistry.getArguments()
        assumeTrue(arguments.getString("channelsTiming") == "true")
        fill()
        val dao = database.channelDao()
        dao.channelOverviews().first()
        val times =
            (1..RUNS).map {
                val mark = TimeSource.Monotonic.markNow()
                dao.channelOverviews().first()
                mark.elapsedNow().inWholeMilliseconds
            }
        val median = times.sorted()[RUNS / 2]
        val status = Bundle()
        status.putLong("channels_median_ms", median)
        status.putString("channels_runs_ms", times.joinToString(","))
        InstrumentationRegistry.getInstrumentation().sendStatus(0, status)
        assertTrue(median < BOUND_MS, "The median was $median ms, at or over $BOUND_MS ms")
    }

    /**
     * Subscribes [CHANNELS] channels on MONDAY, then saves [VIDEOS_PER_CHANNEL] videos for each on
     * TUESDAY, one every 6 hours back from WEDNESDAY, with an upcoming premiere every
     * [UPCOMING_EVERY]th, the newest one included, so the query skips a row for each channel.
     */
    private suspend fun fill() {
        val channels = database.channelDao()
        val videos = database.videoDao()
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
                        kind = if (n % UPCOMING_EVERY == 0) VideoKind.UPCOMING else VideoKind.VIDEO,
                    )
                }
            videos.saveChannelUploads(
                details(id = channelId),
                listed(TUESDAY, *saved.toTypedArray()),
            )
        }
    }
}
