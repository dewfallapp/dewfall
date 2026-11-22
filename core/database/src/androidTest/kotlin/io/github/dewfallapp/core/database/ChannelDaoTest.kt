package io.github.dewfallapp.core.database

import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.runner.RunWith

/** The subscriptions spec's requirements about subscribing and unsubscribing. */
@RunWith(AndroidJUnit4::class)
class ChannelDaoTest {
    private val database = inMemoryDatabase()
    private val channels = database.channelDao()

    @AfterTest fun closeDatabase() = database.close()

    @Test
    fun subscribingKeepsTheChannelAndItsDetails() = runTest {
        channels.subscribe(channel())

        assertEquals(listOf(channel()), channels.subscribedChannels().first())
        assertEquals(channel(), channels.subscribedChannel(CHANNEL_A).first())
    }

    @Test
    fun aChannelNeverSubscribedToIsNotThere() = runTest {
        channels.subscribe(channel(id = CHANNEL_A))

        assertNull(channels.subscribedChannel(CHANNEL_B).first())
        assertEquals(listOf(CHANNEL_A), channels.subscribedChannels().first().map { it.id })
    }

    @Test
    fun subscribingAgainKeepsTheFirstTimeAndDetails() = runTest {
        channels.subscribe(channel(subscribedAt = MONDAY))
        channels.subscribe(channel(name = "Another name", subscribedAt = TUESDAY))

        assertEquals(listOf(channel(subscribedAt = MONDAY)), channels.subscribedChannels().first())
    }

    @Test
    fun unsubscribingRemovesTheChannel() = runTest {
        channels.subscribe(channel())

        channels.unsubscribe(CHANNEL_A)

        assertNull(channels.subscribedChannel(CHANNEL_A).first())
        assertEquals(emptyList(), channels.subscribedChannels().first())
    }

    @Test
    fun unsubscribingFromAChannelNotSubscribedChangesNothing() = runTest {
        channels.subscribe(channel(id = CHANNEL_A))

        channels.unsubscribe(CHANNEL_B)

        assertEquals(listOf(channel(id = CHANNEL_A)), channels.subscribedChannels().first())
    }

    @Test
    fun watchedVideosStayAfterUnsubscribing() = runTest {
        val halfWatched = videoId(1)
        val watched = videoId(2)
        channels.subscribe(channel())
        database
            .videoDao()
            .saveChannelUploads(details(), listed(MONDAY, video(halfWatched), video(watched)))
        database.watchStateDao().savePosition(halfWatched, 300_000, TUESDAY)
        database.watchStateDao().markWatched(watched, TUESDAY)

        channels.unsubscribe(CHANNEL_A)

        assertNotNull(database.videoDao().video(halfWatched))
        assertNotNull(database.videoDao().video(watched))
        assertEquals(300_000, database.watchStateDao().watchState(halfWatched).first()?.positionMs)
        assertEquals(TUESDAY, database.watchStateDao().watchState(watched).first()?.watchedAt)
    }

    @Test
    fun subscribingAgainLaterTakesTheNewTime() = runTest {
        channels.subscribe(channel(subscribedAt = MONDAY))
        channels.unsubscribe(CHANNEL_A)

        channels.subscribe(channel(subscribedAt = WEDNESDAY))

        assertEquals(WEDNESDAY, channels.subscribedChannel(CHANNEL_A).first()?.subscribedAt)
    }

    @Test
    fun channelsComeByNameIgnoringCase() = runTest {
        // By code point, "T" comes before "l", so only a case-insensitive order gives this one.
        channels.subscribe(channel(id = CHANNEL_A, name = "Tidewater Woodshop"))
        channels.subscribe(channel(id = CHANNEL_B, name = "larkspur Lectures"))

        assertEquals(
            listOf("larkspur Lectures", "Tidewater Woodshop"),
            channels.subscribedChannels().first().map { it.name },
        )
    }
}
