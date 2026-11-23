package io.github.dewfallapp.core.database

import android.database.sqlite.SQLiteDatabase
import androidx.room3.useReaderConnection
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.github.dewfallapp.core.model.VideoKind
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.hours
import kotlin.time.Instant
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import org.junit.runner.RunWith

/** How long a test waits for Room's Flow to emit again after a change. */
private const val EMIT_TIMEOUT_MS = 5_000L

/** What a test reads of one row: the channel's ID, its last upload's time and approximate flag. */
private data class LastUpload(val channelId: String, val at: Instant?, val approximate: Boolean?)

/**
 * The subscriptions spec's rule for a Channels row's last upload, against the phone's own SQLite.
 * Each test subscribes and saves videos with times it chooses.
 */
@RunWith(AndroidJUnit4::class)
class ChannelOverviewsQueryTest {
    private val database = inMemoryDatabase()
    private val videos = database.videoDao()
    private val channels = database.channelDao()

    @AfterTest fun closeDatabase() = database.close()

    private suspend fun lastUploads(): List<LastUpload> =
        channels.channelOverviews().first().map {
            LastUpload(it.channel.id, it.lastPublishedAt, it.lastPublishedApproximate)
        }

    @Test
    fun aChannelWithNoSavedVideoHasNoLastUpload() = runTest {
        channels.subscribe(channel())

        assertEquals(listOf(LastUpload(CHANNEL_A, null, null)), lastUploads())
        assertEquals(channel(), channels.channelOverviews().first().single().channel)
    }

    @Test
    fun theNewestVideoShortOrLiveStreamCountsWithItsFlag() = runTest {
        channels.subscribe(channel())
        val saved =
            arrayOf(
                video(
                    videoId(1),
                    kind = VideoKind.LIVE,
                    publishedAt = WEDNESDAY,
                    approximate = true,
                ),
                video(videoId(2), kind = VideoKind.SHORT, publishedAt = TUESDAY),
                video(videoId(3), kind = VideoKind.VIDEO, publishedAt = MONDAY),
            )
        videos.saveChannelUploads(details(), listed(WEDNESDAY, *saved))

        assertEquals(listOf(LastUpload(CHANNEL_A, WEDNESDAY, true)), lastUploads())
    }

    @Test
    fun aShortCountsWhenItIsTheNewest() = runTest {
        channels.subscribe(channel())
        val saved =
            arrayOf(
                video(videoId(1), kind = VideoKind.SHORT, publishedAt = TUESDAY),
                video(videoId(2), kind = VideoKind.VIDEO, publishedAt = MONDAY, approximate = true),
            )
        videos.saveChannelUploads(details(), listed(WEDNESDAY, *saved))

        assertEquals(listOf(LastUpload(CHANNEL_A, TUESDAY, false)), lastUploads())
    }

    @Test
    fun anUpcomingPremiereIsSkippedEvenWhenNewest() = runTest {
        channels.subscribe(channel())
        val saved =
            arrayOf(
                video(videoId(1), kind = VideoKind.UPCOMING, publishedAt = WEDNESDAY + 24.hours),
                video(videoId(2), kind = VideoKind.UPCOMING, publishedAt = WEDNESDAY),
                video(videoId(3), kind = VideoKind.VIDEO, publishedAt = MONDAY),
            )
        videos.saveChannelUploads(details(), listed(WEDNESDAY, *saved))

        assertEquals(listOf(LastUpload(CHANNEL_A, MONDAY, false)), lastUploads())
    }

    @Test
    fun onlyUpcomingVideosGiveNoLastUpload() = runTest {
        channels.subscribe(channel())
        val saved = arrayOf(video(videoId(1), kind = VideoKind.UPCOMING, publishedAt = WEDNESDAY))
        videos.saveChannelUploads(details(), listed(WEDNESDAY, *saved))

        assertEquals(listOf(LastUpload(CHANNEL_A, null, null)), lastUploads())
    }

    @Test
    fun aVideoWithNoPublishTimeIsSkipped() = runTest {
        channels.subscribe(channel())
        val saved =
            arrayOf(video(videoId(1), publishedAt = null), video(videoId(2), publishedAt = MONDAY))
        videos.saveChannelUploads(details(), listed(WEDNESDAY, *saved))

        assertEquals(listOf(LastUpload(CHANNEL_A, MONDAY, false)), lastUploads())
    }

    @Test
    fun twoVideosAtTheSameTimeGiveTheLaterSavedOne() = runTest {
        channels.subscribe(channel())
        videos.saveChannelUploads(
            details(),
            listed(MONDAY, video(videoId(1), publishedAt = TUESDAY, approximate = true)),
        )
        videos.saveChannelUploads(
            details(),
            listed(TUESDAY, video(videoId(2), publishedAt = TUESDAY, approximate = false)),
        )

        assertEquals(listOf(LastUpload(CHANNEL_A, TUESDAY, false)), lastUploads())
    }

    @Test
    fun otherChannelsVideosAndUnsubscribedChannelsAreLeftOut() = runTest {
        channels.subscribe(channel(id = CHANNEL_A))
        val a = arrayOf(video(videoId(1), channelId = CHANNEL_A, publishedAt = MONDAY))
        val b = arrayOf(video(videoId(2), channelId = CHANNEL_B, publishedAt = WEDNESDAY))
        videos.saveChannelUploads(details(id = CHANNEL_A), listed(WEDNESDAY, *a))
        videos.saveChannelUploads(details(id = CHANNEL_B), listed(WEDNESDAY, *b))

        assertEquals(listOf(LastUpload(CHANNEL_A, MONDAY, false)), lastUploads())
    }

    /**
     * AndroidSQLiteDriver runs only SELECT-like statements as queries, so EXPLAIN QUERY PLAN goes
     * through a plain framework database instead. It gets the same tables and indexes, copied from
     * the Room database's sqlite_master, and the phone's same SQLite library plans the query.
     */
    @Test
    fun thePlanUsesTheIndexAndSortsNothing() = runTest {
        val schema = database.useReaderConnection { connection ->
            connection.usePrepared(
                "SELECT sql FROM sqlite_master WHERE sql IS NOT NULL AND " +
                    "type IN ('table', 'index') AND name NOT LIKE 'sqlite_%' AND " +
                    "name != 'android_metadata' AND name != 'room_master_table'",
            ) { statement ->
                buildList {
                    while (statement.step()) {
                        add(statement.getText(0))
                    }
                }
            }
        }
        val plain = SQLiteDatabase.create(null)
        val plan =
            try {
                for (sql in schema) {
                    plain.execSQL(sql)
                }
                plain.rawQuery("EXPLAIN QUERY PLAN $CHANNEL_OVERVIEWS_QUERY", null).use { cursor ->
                    buildList {
                        while (cursor.moveToNext()) {
                            add(cursor.getString(cursor.getColumnIndexOrThrow("detail")))
                        }
                    }
                }
            } finally {
                plain.close()
            }

        assertTrue(
            plan.any { "index_videos_channel_id_published_at" in it },
            "No plan line names the index: $plan",
        )
        assertFalse(plan.any { "USE TEMP B-TREE" in it }, "The plan sorts: $plan")
    }

    @Test
    fun theFlowEmitsAgainAfterASubscriptionASaveAndAnUnsubscription() = runTest {
        val seen = Channel<List<LastUpload>>(Channel.UNLIMITED)
        val collector =
            launch(Dispatchers.Default) {
                channels
                    .channelOverviews()
                    .map { rows ->
                        rows.map {
                            LastUpload(
                                it.channel.id,
                                it.lastPublishedAt,
                                it.lastPublishedApproximate,
                            )
                        }
                    }
                    .collect { seen.send(it) }
            }
        suspend fun next(expected: List<LastUpload>) =
            withContext(Dispatchers.Default) {
                withTimeout(EMIT_TIMEOUT_MS) {
                    var latest = seen.receive()
                    while (latest != expected) {
                        latest = seen.receive()
                    }
                    latest
                }
            }

        next(emptyList())
        channels.subscribe(channel())
        next(listOf(LastUpload(CHANNEL_A, null, null)))
        videos.saveChannelUploads(
            details(),
            listed(TUESDAY, video(videoId(1), publishedAt = MONDAY)),
        )
        next(listOf(LastUpload(CHANNEL_A, MONDAY, false)))
        channels.unsubscribe(CHANNEL_A)
        next(emptyList())
        collector.cancel()
    }
}
