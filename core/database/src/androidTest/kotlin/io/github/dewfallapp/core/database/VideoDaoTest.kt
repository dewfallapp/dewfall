package io.github.dewfallapp.core.database

import androidx.test.ext.junit.runners.AndroidJUnit4
import io.github.dewfallapp.core.model.VideoKind
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.days
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.runner.RunWith

/**
 * The subscriptions spec's requirements about saving a channel's uploads, against the phone's own
 * SQLite. VideoMergeTest covers each part of the merge rule on the JVM.
 */
@RunWith(AndroidJUnit4::class)
class VideoDaoTest {
    private val database = inMemoryDatabase()
    private val videos = database.videoDao()
    private val channels = database.channelDao()

    @AfterTest fun closeDatabase() = database.close()

    @Test
    fun aFirstSaveKeepsEveryVideo() = runTest {
        val saved = listed(MONDAY, video(videoId(1)), video(videoId(2), publishedAt = null))

        videos.saveChannelUploads(details(), saved)

        assertEquals(saved[0].copy(listingNumber = 1), videos.video(videoId(1)))
        assertEquals(saved[1].copy(listingNumber = 1), videos.video(videoId(2)))
        assertEquals(MONDAY, videos.video(videoId(1))?.firstSeenAt)
    }

    @Test
    fun aVideoThatLeftTheListStays() = runTest {
        val first = listed(MONDAY, video(videoId(1)), video(videoId(2), title = "Old one"))
        videos.saveChannelUploads(details(), first)

        videos.saveChannelUploads(details(), listed(TUESDAY, video(videoId(1))))

        assertEquals(first[1].copy(listingNumber = 1), videos.video(videoId(2)))
    }

    @Test
    fun aChangedTitleIsTakenAndTheFirstSaveTimeKept() = runTest {
        videos.saveChannelUploads(details(), listed(MONDAY, video(videoId(1), title = "Old")))

        videos.saveChannelUploads(details(), listed(TUESDAY, video(videoId(1), title = "New")))

        val saved = assertNotNull(videos.video(videoId(1)))
        assertEquals("New", saved.title)
        assertEquals(MONDAY, saved.firstSeenAt)
        assertEquals(2L, saved.listingNumber)
    }

    @Test
    fun aPremiereThatStartedBecomesAVideoWithItsLength() = runTest {
        val premiere = video(videoId(1), kind = VideoKind.UPCOMING, durationMs = null)
        videos.saveChannelUploads(details(), listed(MONDAY, premiere))

        val started = video(videoId(1), kind = VideoKind.VIDEO, durationMs = 1_800_000)
        videos.saveChannelUploads(details(), listed(TUESDAY, started))

        val saved = assertNotNull(videos.video(videoId(1)))
        assertEquals(VideoKind.VIDEO, saved.kind)
        assertEquals(1_800_000, saved.durationMs)
    }

    @Test
    fun missingThumbnailsKeepTheSavedOnes() = runTest {
        videos.saveChannelUploads(details(), listed(MONDAY, video(videoId(1))))

        videos.saveChannelUploads(
            details(),
            listed(TUESDAY, video(videoId(1), thumbnails = emptyList())),
        )

        assertEquals(THUMBNAILS, videos.video(videoId(1))?.thumbnails)
    }

    @Test
    fun aLaterApproximateTimeKeepsTheSavedOne() = runTest {
        val exact = video(videoId(1), publishedAt = MONDAY, approximate = false)
        val estimate = video(videoId(2), publishedAt = MONDAY - 3.days, approximate = true)
        videos.saveChannelUploads(details(), listed(MONDAY, exact, estimate))

        videos.saveChannelUploads(
            details(),
            listed(
                WEDNESDAY + 14.days,
                video(videoId(1), publishedAt = TUESDAY, approximate = true),
                video(videoId(2), publishedAt = WEDNESDAY - 14.days, approximate = true),
            ),
        )

        val first = assertNotNull(videos.video(videoId(1)))
        assertEquals(MONDAY, first.publishedAt)
        assertFalse(first.publishedApproximate)
        val second = assertNotNull(videos.video(videoId(2)))
        assertEquals(MONDAY - 3.days, second.publishedAt)
        assertTrue(second.publishedApproximate)
    }

    @Test
    fun aSubscribedChannelTakesItsNewDetails() = runTest {
        channels.subscribe(channel(name = "Old name", subscribedAt = MONDAY))

        videos.saveChannelUploads(details(name = "New name"), listed(TUESDAY, video(videoId(1))))

        assertEquals(
            channel(name = "New name", subscribedAt = MONDAY),
            channels.subscribedChannel(CHANNEL_A).first(),
        )
    }

    @Test
    fun aSubscriptionFromAFileGetsAvatarsAndDescription() = runTest {
        channels.subscribe(channel(description = "", avatars = emptyList()))

        videos.saveChannelUploads(details(), listed(TUESDAY, video(videoId(1))))

        assertEquals(channel(), channels.subscribedChannel(CHANNEL_A).first())
    }

    @Test
    fun savingAChannelNotSubscribedDoesNotSubscribeIt() = runTest {
        videos.saveChannelUploads(
            details(id = CHANNEL_B),
            listed(MONDAY, video(videoId(1), channelId = CHANNEL_B)),
        )

        assertNull(channels.subscribedChannel(CHANNEL_B).first())
        assertNotNull(videos.video(videoId(1)))
    }

    @Test
    fun equalTimesKeepYouTubesOrder() = runTest {
        val sameTime = MONDAY - 3.days
        val a = video(videoId(3), publishedAt = sameTime, approximate = true)
        val b = video(videoId(1), publishedAt = sameTime, approximate = true)
        val c = video(videoId(2), publishedAt = sameTime, approximate = true)

        videos.saveChannelUploads(details(), listed(MONDAY, a, b, c))

        assertEquals(
            listOf(videoId(3), videoId(1), videoId(2)),
            videos.channelVideos(CHANNEL_A).first().map { it.id },
        )
    }

    @Test
    fun videosWithNoTimeComeLastInYouTubesOrder() = runTest {
        val d = video(videoId(5), publishedAt = null)
        val older = video(videoId(1), publishedAt = MONDAY)
        val e = video(videoId(4), publishedAt = null)
        val newer = video(videoId(2), publishedAt = TUESDAY)

        videos.saveChannelUploads(details(), listed(WEDNESDAY, d, older, e, newer))

        assertEquals(
            listOf(videoId(2), videoId(1), videoId(5), videoId(4)),
            videos.channelVideos(CHANNEL_A).first().map { it.id },
        )
    }

    @Test
    fun aVideoALaterSaveNoLongerHoldsComesAfterEqualTimes() = runTest {
        val sameTime = MONDAY - 3.days
        videos.saveChannelUploads(
            details(),
            listed(
                MONDAY,
                video(videoId(1), publishedAt = sameTime),
                video(videoId(2), publishedAt = sameTime),
            ),
        )

        videos.saveChannelUploads(
            details(),
            listed(
                TUESDAY,
                video(videoId(3), publishedAt = sameTime),
                video(videoId(2), publishedAt = sameTime),
            ),
        )

        assertEquals(
            listOf(videoId(3), videoId(2), videoId(1)),
            videos.channelVideos(CHANNEL_A).first().map { it.id },
        )
    }

    @Test
    fun aLaterSaveComesFirstWhenTheClockRepeats() = runTest {
        val sameTime = MONDAY - 3.days
        videos.saveChannelUploads(
            details(),
            listed(
                MONDAY,
                video(videoId(1), publishedAt = sameTime),
                video(videoId(2), publishedAt = sameTime),
            ),
        )

        videos.saveChannelUploads(
            details(),
            listed(MONDAY, video(videoId(2), publishedAt = sameTime)),
        )

        assertEquals(
            listOf(videoId(2), videoId(1)),
            videos.channelVideos(CHANNEL_A).first().map { it.id },
        )
    }

    @Test
    fun aLaterSaveComesFirstWhenTheClockGoesBack() = runTest {
        val sameTime = MONDAY - 3.days
        videos.saveChannelUploads(
            details(),
            listed(
                WEDNESDAY,
                video(videoId(1), publishedAt = sameTime),
                video(videoId(2), publishedAt = sameTime),
            ),
        )

        videos.saveChannelUploads(
            details(),
            listed(MONDAY, video(videoId(2), publishedAt = sameTime)),
        )

        assertEquals(
            listOf(videoId(2), videoId(1)),
            videos.channelVideos(CHANNEL_A).first().map { it.id },
        )
    }

    @Test
    fun aRepeatedVideoIsKeptOnceAtItsFirstPlace() = runTest {
        val first = video(videoId(1), title = "First")
        val again = video(videoId(1), title = "Again")

        videos.saveChannelUploads(details(), listed(MONDAY, first, video(videoId(2)), again))

        val saved = assertNotNull(videos.video(videoId(1)))
        assertEquals("First", saved.title)
        assertEquals(0, saved.listPosition)
        assertEquals(2, videos.channelVideos(CHANNEL_A).first().size)
    }

    @Test
    fun aSaveOfMoreThanFiveHundredVideosWorksTwice() = runTest {
        val many = (1..1_200).map { video(videoId(it), title = "Old") }
        videos.saveChannelUploads(details(), listed(MONDAY, *many.toTypedArray()))

        val renamed = many.map { it.copy(title = "New") }
        videos.saveChannelUploads(details(), listed(TUESDAY, *renamed.toTypedArray()))

        val saved = videos.channelVideos(CHANNEL_A).first()
        assertEquals(1_200, saved.size)
        assertTrue(saved.all { it.title == "New" && it.firstSeenAt == MONDAY })
    }
}
