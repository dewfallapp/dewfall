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
    private val backStack = mutableListOf<NavKey>(InboxKey)

    @Test
    fun `opening a video from the inbox adds its screen`() {
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
    fun `closing the same screen twice removes it once and keeps the inbox`() {
        backStack.openVideo(VIDEO)
        val player = backStack.last() as PlayerKey

        backStack.close(player)
        backStack.close(player)

        assertEquals(listOf<NavKey>(InboxKey), backStack)
    }

    @Test
    fun `closing after a back gesture already went back keeps the inbox`() {
        backStack.openVideo(VIDEO)
        val player = backStack.last() as PlayerKey

        backStack.goBack()
        backStack.close(player)

        assertEquals(listOf<NavKey>(InboxKey), backStack)
    }

    @Test
    fun `going back never removes the first screen`() {
        backStack.openVideo(VIDEO)

        backStack.goBack()
        backStack.goBack()

        assertEquals(listOf<NavKey>(InboxKey), backStack)
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
        assertEquals(InboxKey, backStack.first())
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

        assertEquals(listOf(InboxKey, page), backStack)
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

        assertEquals(listOf<NavKey>(InboxKey), backStack)
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

        assertEquals(listOf<NavKey>(InboxKey), backStack)
    }

    @Test
    fun `a channel opened from search sits on search, and back returns to it`() {
        backStack.openSearch(InboxKey)
        val search = assertIs<SearchKey>(backStack.last())
        backStack.openChannel(search, CHANNEL)

        assertEquals(3, backStack.size)
        val page = assertIs<ChannelKey>(backStack.last())
        assertEquals(CHANNEL, page.channelId)
        assertTrue(backStack.showsMiniPlayer())

        backStack.close(page)
        assertEquals(listOf(InboxKey, search), backStack)
    }

    @Test
    fun `a second tap on a channel in search opens nothing more`() {
        backStack.openSearch(InboxKey)
        val search = assertIs<SearchKey>(backStack.last())
        backStack.openChannel(search, CHANNEL)
        backStack.openChannel(search, CHANNEL)
        backStack.openChannel(search, OTHER_CHANNEL)

        assertEquals(3, backStack.size)
        assertEquals(CHANNEL, assertIs<ChannelKey>(backStack.last()).channelId)
    }

    @Test
    fun `closing the inbox does nothing`() {
        backStack.close(InboxKey)

        assertEquals(listOf<NavKey>(InboxKey), backStack)
    }

    @Test
    fun `search opens from the inbox once, and its back arrow stops at the inbox`() {
        backStack.openSearch(InboxKey)
        backStack.openSearch(InboxKey)
        val search = assertIs<SearchKey>(backStack.last())
        assertEquals(2, backStack.size)

        backStack.close(search)
        backStack.close(search)

        assertEquals(listOf<NavKey>(InboxKey), backStack)
    }

    @Test
    fun `search opens only while the inbox is on top`() {
        backStack.openVideo(VIDEO)

        backStack.openSearch(InboxKey)

        assertIs<PlayerKey>(backStack.last())
    }

    @Test
    fun `search opened again gets a new entry`() {
        backStack.openSearch(InboxKey)
        val first = backStack.last() as SearchKey
        backStack.close(first)
        backStack.openSearch(InboxKey)

        assertTrue(first.entryId != (backStack.last() as SearchKey).entryId)
    }

    @Test
    fun `a card opens its channel page once, only while the inbox is on top`() {
        backStack.openChannel(InboxKey, CHANNEL)
        backStack.openChannel(InboxKey, OTHER_CHANNEL)

        assertEquals(2, backStack.size)
        assertEquals(CHANNEL, assertIs<ChannelKey>(backStack.last()).channelId)
    }

    @Test
    fun `a video opens from search and from a channel page`() {
        backStack.openSearch(InboxKey)
        backStack.openVideo(VIDEO)
        assertIs<PlayerKey>(backStack.last())
        backStack.close(backStack.last())
        backStack.close(backStack.last())

        backStack.openChannel(InboxKey, CHANNEL)
        backStack.openVideo(OTHER)

        assertEquals(OTHER, assertIs<PlayerKey>(backStack.last()).videoId)
    }

    @Test
    fun `search opens from the Channels screen, and close keeps the Channels screen`() {
        val channels = mutableListOf<NavKey>(ChannelsKey)
        channels.openSearch(InboxKey)
        assertEquals(listOf<NavKey>(ChannelsKey), channels)

        channels.openSearch(ChannelsKey)
        val search = assertIs<SearchKey>(channels.last())
        channels.close(search)
        channels.close(ChannelsKey)

        assertEquals(listOf<NavKey>(ChannelsKey), channels)
    }

    @Test
    fun `the navigation bar shows on the inbox, the Channels screen and channel pages only`() {
        assertTrue(backStack.showsNavigationBar())
        assertTrue(listOf<NavKey>(ChannelsKey).showsNavigationBar())

        backStack.openChannel(InboxKey, CHANNEL)
        assertTrue(backStack.showsNavigationBar())

        backStack.openVideo(VIDEO)
        assertFalse(backStack.showsNavigationBar())

        val search = listOf(InboxKey, SearchKey(entryId = "e"))
        assertFalse(search.showsNavigationBar())
    }
}
