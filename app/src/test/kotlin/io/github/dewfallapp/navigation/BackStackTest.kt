package io.github.dewfallapp.navigation

import androidx.navigation3.runtime.NavKey
import io.github.dewfallapp.core.model.ChannelId
import io.github.dewfallapp.core.model.VideoId
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue

private val VIDEO = VideoId("aqz-KE-bpKQ")
private val OTHER = VideoId("dQw4w9WgXcQ")

// Made-up IDs in a channel ID's form, never real channels'.
private val CHANNEL = ChannelId("UC" + "0".repeat(21) + "1")
private val OTHER_CHANNEL = ChannelId("UC" + "0".repeat(21) + "2")

class BackStackTest {
    private val backStack = mutableListOf<NavKey>(SearchKey)

    @Test
    fun `opening a video from search adds its screen`() {
        backStack.openVideo(VIDEO)

        assertEquals(2, backStack.size)
        assertEquals(VIDEO, assertIs<PlayerKey>(backStack.last()).videoId)
    }

    @Test
    fun `a second tap while the video screen opens adds nothing`() {
        backStack.openVideo(VIDEO)
        backStack.openVideo(VIDEO)
        backStack.openVideo(OTHER)

        assertEquals(2, backStack.size)
        assertEquals(VIDEO, assertIs<PlayerKey>(backStack.last()).videoId)
    }

    @Test
    fun `closing the same screen twice removes it once and keeps search`() {
        backStack.openVideo(VIDEO)
        val player = backStack.last() as PlayerKey

        backStack.close(player)
        backStack.close(player)

        assertEquals(listOf<NavKey>(SearchKey), backStack)
    }

    @Test
    fun `closing after a back gesture already went back keeps search`() {
        backStack.openVideo(VIDEO)
        val player = backStack.last() as PlayerKey

        backStack.goBack()
        backStack.close(player)

        assertEquals(listOf<NavKey>(SearchKey), backStack)
    }

    @Test
    fun `going back never removes the first screen`() {
        backStack.openVideo(VIDEO)

        backStack.goBack()
        backStack.goBack()

        assertEquals(listOf<NavKey>(SearchKey), backStack)
    }

    @Test
    fun `the mini player shows on search, and not over a video screen`() {
        assertTrue(backStack.showsMiniPlayer())

        backStack.openVideo(VIDEO)
        assertFalse(backStack.showsMiniPlayer())

        backStack.goBack()
        assertTrue(backStack.showsMiniPlayer())
    }

    private fun openedVideo(): PlayerKey {
        backStack.openVideo(VIDEO)
        return backStack.last() as PlayerKey
    }

    @Test
    fun `opening the channel from a video screen replaces it with the channel page`() {
        val player = openedVideo()

        backStack.openChannel(player, CHANNEL)

        assertEquals(2, backStack.size)
        assertEquals(SearchKey, backStack.first())
        assertEquals(CHANNEL, assertIs<ChannelKey>(backStack.last()).channelId)
        assertTrue(backStack.showsMiniPlayer())
    }

    @Test
    fun `a second tap on the channel opens nothing more`() {
        val player = openedVideo()

        backStack.openChannel(player, CHANNEL)
        backStack.openChannel(player, CHANNEL)

        assertEquals(2, backStack.size)
    }

    @Test
    fun `a video opened from a channel page returns to that page for the same channel`() {
        backStack.openChannel(openedVideo(), CHANNEL)
        val page = backStack.last()
        backStack.openVideo(OTHER)

        backStack.openChannel(backStack.last() as PlayerKey, CHANNEL)

        assertEquals(listOf(SearchKey, page), backStack)
    }

    @Test
    fun `a video opened from a channel page opens another channel's page on top`() {
        backStack.openChannel(openedVideo(), CHANNEL)
        backStack.openVideo(OTHER)

        backStack.openChannel(backStack.last() as PlayerKey, OTHER_CHANNEL)

        assertEquals(3, backStack.size)
        assertEquals(OTHER_CHANNEL, assertIs<ChannelKey>(backStack.last()).channelId)
    }

    @Test
    fun `opening a channel from a video screen that isn't on top opens nothing`() {
        val player = openedVideo()
        backStack.goBack()

        backStack.openChannel(player, CHANNEL)

        assertEquals(listOf<NavKey>(SearchKey), backStack)
    }

    @Test
    fun `an upload or the mini player on a channel page opens its video on top`() {
        backStack.openChannel(openedVideo(), CHANNEL)

        backStack.openVideo(OTHER)

        assertEquals(3, backStack.size)
        assertEquals(OTHER, assertIs<PlayerKey>(backStack.last()).videoId)
        assertFalse(backStack.showsMiniPlayer())
    }

    @Test
    fun `the channel page's back arrow twice goes back once`() {
        backStack.openChannel(openedVideo(), CHANNEL)
        val page = backStack.last()

        backStack.close(page)
        backStack.close(page)

        assertEquals(listOf<NavKey>(SearchKey), backStack)
    }

    @Test
    fun `a channel opened from search sits on search, and back returns to it`() {
        backStack.openChannel(SearchKey, CHANNEL)

        assertEquals(2, backStack.size)
        val page = assertIs<ChannelKey>(backStack.last())
        assertEquals(CHANNEL, page.channelId)
        assertTrue(backStack.showsMiniPlayer())

        backStack.close(page)
        assertEquals(listOf<NavKey>(SearchKey), backStack)
    }

    @Test
    fun `a second tap on a channel in search opens nothing more`() {
        backStack.openChannel(SearchKey, CHANNEL)
        backStack.openChannel(SearchKey, CHANNEL)
        backStack.openChannel(SearchKey, OTHER_CHANNEL)

        assertEquals(2, backStack.size)
        assertEquals(CHANNEL, assertIs<ChannelKey>(backStack.last()).channelId)
    }

    @Test
    fun `closing search does nothing`() {
        backStack.close(SearchKey)

        assertEquals(listOf<NavKey>(SearchKey), backStack)
    }
}
