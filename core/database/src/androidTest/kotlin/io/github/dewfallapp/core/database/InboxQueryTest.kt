package io.github.dewfallapp.core.database

import androidx.test.ext.junit.runners.AndroidJUnit4
import io.github.dewfallapp.core.model.VideoKind
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.time.Duration.Companion.days
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

/** A week before MONDAY, for videos published long before anyone subscribed. */
private val LAST_WEEK: Instant = MONDAY - 7.days

/**
 * The inbox spec's rules for which saved videos are in the inbox, and their order, against the
 * phone's own SQLite. Each test subscribes, saves and marks videos with times it chooses.
 */
@RunWith(AndroidJUnit4::class)
class InboxQueryTest {
    private val database = inMemoryDatabase()
    private val videos = database.videoDao()
    private val channels = database.channelDao()
    private val watch = database.watchStateDao()

    @AfterTest fun closeDatabase() = database.close()

    private suspend fun inbox(): List<String> =
        videos.inboxVideos(VideoKind.VIDEO).first().map { it.id }

    /** A video of [channelId] published [hoursAfter] hours after [LAST_WEEK]. */
    private fun old(n: Int, hoursAfter: Int, channelId: String = CHANNEL_A) =
        video(videoId(n), channelId = channelId, publishedAt = LAST_WEEK + hoursAfter.hours)

    /** Three videos published last week, newest first, as a save lists them. */
    private fun threeOld() = arrayOf(old(1, 3), old(2, 2), old(3, 1))

    @Test
    fun aNewSubscriptionShowsTheTwoLatest() = runTest {
        channels.subscribe(channel(subscribedAt = MONDAY))
        val saved = (1..5).map { old(it, hoursAfter = 10 - it) }.toTypedArray()
        videos.saveChannelUploads(details(), listed(MONDAY + 1.hours, *saved))

        assertEquals(listOf(videoId(1), videoId(2)), inbox())
    }

    @Test
    fun videosPublishedAfterSubscribingAreNewAndOlderOnesGiveWay() = runTest {
        channels.subscribe(channel(subscribedAt = MONDAY))
        videos.saveChannelUploads(details(), listed(MONDAY, *threeOld()))
        val fresh = (11..13).map { video(videoId(it), publishedAt = TUESDAY + (14 - it).hours) }
        videos.saveChannelUploads(details(), listed(WEDNESDAY, *fresh.toTypedArray(), *threeOld()))

        assertEquals(listOf(videoId(11), videoId(12), videoId(13)), inbox())
    }

    @Test
    fun aVideoALaterSaveBringsIsNewWhateverItsEstimate() = runTest {
        channels.subscribe(channel(subscribedAt = TUESDAY))
        videos.saveChannelUploads(details(), listed(TUESDAY, *threeOld()))
        // Came out after subscribing, but YouTube's estimates put them on Monday.
        val estimated =
            (11..13).map {
                video(videoId(it), publishedAt = MONDAY + (14 - it).hours, approximate = true)
            }
        videos.saveChannelUploads(
            details(),
            listed(WEDNESDAY, *estimated.toTypedArray(), *threeOld()),
        )

        assertEquals(listOf(videoId(11), videoId(12), videoId(13)), inbox())
    }

    @Test
    fun afterSubscribingAgainTheFirstVideosSavedAreTheFirstOnes() = runTest {
        channels.subscribe(channel(subscribedAt = MONDAY))
        videos.saveChannelUploads(details(), listed(MONDAY, *threeOld()))
        channels.unsubscribe(CHANNEL_A)
        channels.subscribe(channel(subscribedAt = TUESDAY))
        // The first save after subscribing again brings no video Dewfall didn't already keep.
        videos.saveChannelUploads(details(), listed(TUESDAY, *threeOld()))
        // The next one brings 3 videos published before subscribing again.
        val gap = (61..63).map { old(it, hoursAfter = 70 - it) }
        videos.saveChannelUploads(details(), listed(WEDNESDAY, *gap.toTypedArray(), *threeOld()))

        assertEquals(listOf(videoId(61), videoId(62)), inbox())
    }

    @Test
    fun aRowNoSaveOfUploadsWroteIsNeverNewByItsFirstSave() = runTest {
        channels.subscribe(channel(subscribedAt = MONDAY))
        videos.saveChannelUploads(details(), listed(MONDAY, *threeOld()))
        // A video saved another way, as a later phase may, first seen after the first save.
        videos.insertVideos(listOf(old(9, hoursAfter = 0).copy(firstSeenAt = WEDNESDAY)))

        assertEquals(listOf(videoId(1), videoId(2)), inbox())
    }

    @Test
    fun aRowNoSaveOfUploadsWroteDoesNotMoveTheFirstSave() = runTest {
        channels.subscribe(channel(subscribedAt = MONDAY))
        // Saved another way after subscribing, before the first save of the channel's uploads.
        videos.insertVideos(listOf(old(9, hoursAfter = 0).copy(firstSeenAt = MONDAY + 1.hours)))
        videos.saveChannelUploads(details(), listed(TUESDAY, *threeOld()))

        assertEquals(listOf(videoId(1), videoId(2)), inbox())
    }

    @Test
    fun liveUpcomingAndShortsAreLeftOutAndTakeNoPlaceInTheTwoLatest() = runTest {
        channels.subscribe(channel(subscribedAt = MONDAY))
        val saved =
            arrayOf(
                video(videoId(21), kind = VideoKind.UPCOMING, publishedAt = WEDNESDAY),
                video(videoId(22), kind = VideoKind.LIVE, publishedAt = TUESDAY + 2.hours),
                video(videoId(23), kind = VideoKind.SHORT, publishedAt = TUESDAY + 1.hours),
                video(videoId(24), publishedAt = TUESDAY),
                *threeOld(),
            )
        videos.saveChannelUploads(details(), listed(TUESDAY + 3.hours, *saved))

        assertEquals(listOf(videoId(24), videoId(1)), inbox())
    }

    @Test
    fun aWatchedVideoLeavesAndNoOlderOneTakesItsPlace() = runTest {
        channels.subscribe(channel(subscribedAt = MONDAY))
        videos.saveChannelUploads(details(), listed(MONDAY, *threeOld()))

        watch.markWatched(videoId(1), TUESDAY)

        assertEquals(listOf(videoId(2)), inbox())
    }

    @Test
    fun aVideoStartedSinceSubscribingStaysWhenNoLongerNew() = runTest {
        channels.subscribe(channel(subscribedAt = MONDAY))
        videos.saveChannelUploads(details(), listed(MONDAY, *threeOld()))
        watch.savePosition(videoId(2), positionMs = 60_000, now = MONDAY + 2.hours)
        val fresh = (11..12).map { video(videoId(it), publishedAt = TUESDAY + (13 - it).hours) }
        videos.saveChannelUploads(details(), listed(WEDNESDAY, *fresh.toTypedArray(), *threeOld()))

        assertEquals(listOf(videoId(11), videoId(12), videoId(2)), inbox())
    }

    @Test
    fun anOldVideoStartedAfterSubscribingJoins() = runTest {
        channels.subscribe(channel(subscribedAt = MONDAY))
        videos.saveChannelUploads(details(), listed(MONDAY, *threeOld()))

        watch.savePosition(videoId(3), positionMs = 60_000, now = TUESDAY)

        assertEquals(listOf(videoId(1), videoId(2), videoId(3)), inbox())
    }

    @Test
    fun aVideoStartedBeforeSubscribingIsLeftOut() = runTest {
        watch.savePosition(videoId(3), positionMs = 60_000, now = MONDAY - 1.hours)
        channels.subscribe(channel(subscribedAt = MONDAY))
        videos.saveChannelUploads(details(), listed(MONDAY, *threeOld()))

        assertEquals(listOf(videoId(1), videoId(2)), inbox())
    }

    @Test
    fun aVideoMarkedUnwatchedAfterSubscribingJoins() = runTest {
        watch.savePosition(videoId(3), positionMs = 60_000, now = MONDAY - 2.hours)
        watch.markWatched(videoId(3), MONDAY - 1.hours)
        channels.subscribe(channel(subscribedAt = MONDAY))
        videos.saveChannelUploads(details(), listed(MONDAY, *threeOld()))

        watch.markUnwatched(videoId(3), TUESDAY)

        assertEquals(listOf(videoId(1), videoId(2), videoId(3)), inbox())
    }

    @Test
    fun aVideoNeverPlayedAndMarkedUnwatchedAfterSubscribingStaysOut() = runTest {
        watch.markWatched(videoId(3), MONDAY - 1.hours)
        channels.subscribe(channel(subscribedAt = MONDAY))
        videos.saveChannelUploads(details(), listed(MONDAY, *threeOld()))

        watch.markUnwatched(videoId(3), TUESDAY)

        assertEquals(listOf(videoId(1), videoId(2)), inbox())
    }

    @Test
    fun unsubscribingEmptiesTheInboxAndSubscribingAgainStartsOver() = runTest {
        channels.subscribe(channel(subscribedAt = MONDAY))
        videos.saveChannelUploads(details(), listed(MONDAY, *threeOld()))

        channels.unsubscribe(CHANNEL_A)
        assertEquals(emptyList(), inbox())

        channels.subscribe(channel(subscribedAt = WEDNESDAY))
        videos.saveChannelUploads(details(), listed(WEDNESDAY, *threeOld()))
        assertEquals(listOf(videoId(1), videoId(2)), inbox())
    }

    @Test
    fun aChannelNeverSubscribedToHasNoVideosInTheInbox() = runTest {
        val saved = arrayOf(video(videoId(31), channelId = CHANNEL_B, publishedAt = WEDNESDAY))
        videos.saveChannelUploads(details(id = CHANNEL_B), listed(WEDNESDAY, *saved))

        assertEquals(emptyList(), inbox())
    }

    @Test
    fun videosComeNewestFirstAcrossChannels() = runTest {
        channels.subscribe(channel(subscribedAt = MONDAY))
        channels.subscribe(channel(id = CHANNEL_B, name = "Orbit Hour", subscribedAt = MONDAY))
        val a =
            arrayOf(
                video(videoId(41), publishedAt = WEDNESDAY),
                video(videoId(42), publishedAt = MONDAY + 1.hours),
            )
        val b = arrayOf(video(videoId(43), channelId = CHANNEL_B, publishedAt = TUESDAY))
        videos.saveChannelUploads(details(), listed(WEDNESDAY, *a))
        videos.saveChannelUploads(details(id = CHANNEL_B), listed(WEDNESDAY, *b))

        assertEquals(listOf(videoId(41), videoId(43), videoId(42)), inbox())
    }

    @Test
    fun equalTimesKeepYouTubesOrder() = runTest {
        channels.subscribe(channel(subscribedAt = MONDAY))
        val same = (51..53).map { video(videoId(it), publishedAt = TUESDAY, approximate = true) }
        videos.saveChannelUploads(details(), listed(WEDNESDAY, *same.toTypedArray()))

        assertEquals(listOf(videoId(51), videoId(52), videoId(53)), inbox())
    }

    @Test
    fun theFlowEmitsAgainAfterAWatchedMarkASaveAndASubscription() = runTest {
        channels.subscribe(channel(subscribedAt = MONDAY))
        videos.saveChannelUploads(details(), listed(MONDAY, *threeOld()))
        val seen = Channel<List<String>>(Channel.UNLIMITED)
        val collector =
            launch(Dispatchers.Default) {
                videos
                    .inboxVideos(VideoKind.VIDEO)
                    .map { list -> list.map { it.id } }
                    .collect {
                        seen.send(it)
                    }
            }
        suspend fun next(expected: List<String>) =
            withContext(Dispatchers.Default) {
                withTimeout(EMIT_TIMEOUT_MS) {
                    var latest = seen.receive()
                    while (latest != expected) {
                        latest = seen.receive()
                    }
                    latest
                }
            }

        next(listOf(videoId(1), videoId(2)))
        watch.markWatched(videoId(1), TUESDAY)
        next(listOf(videoId(2)))
        val fresh = arrayOf(video(videoId(11), publishedAt = TUESDAY))
        videos.saveChannelUploads(details(), listed(WEDNESDAY, *fresh, *threeOld()))
        // The new video and the watched one are the 2 latest now, so the second one gives way.
        next(listOf(videoId(11)))
        val b = arrayOf(video(videoId(43), channelId = CHANNEL_B, publishedAt = TUESDAY - 1.hours))
        videos.saveChannelUploads(details(id = CHANNEL_B), listed(WEDNESDAY, *b))
        channels.subscribe(channel(id = CHANNEL_B, name = "Orbit Hour", subscribedAt = MONDAY))
        next(listOf(videoId(11), videoId(43)))
        collector.cancel()
    }
}
