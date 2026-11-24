package io.github.dewfallapp.navigation

import androidx.compose.runtime.mutableStateOf
import androidx.navigation3.runtime.NavKey
import io.github.dewfallapp.core.model.ChannelId
import io.github.dewfallapp.core.model.VideoId
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

private val VIDEO = VideoId("aqz-KE-bpKQ")

// Made-up IDs in a channel ID's form, never real channels'.
private val CHANNEL = ChannelId("UC" + "0".repeat(21) + "1")
private val OTHER_CHANNEL = ChannelId("UC" + "0".repeat(21) + "2")

/** The rules of the navigation bar's two back stacks, in the inbox spec's navigation bar part. */
class AppNavigationTest {
    private val inbox = mutableListOf<NavKey>(InboxKey)
    private val channels = mutableListOf<NavKey>(ChannelsKey)
    private val navigation = AppNavigation(inbox, channels, mutableStateOf(Destination.INBOX))

    @Test
    fun `the app starts on the inbox, with only the inbox shown`() {
        assertEquals(Destination.INBOX, navigation.selected)
        assertEquals(listOf<NavKey>(InboxKey), navigation.shown(inbox, channels))
        assertTrue(navigation.showsNavigationBar())
    }

    @Test
    fun `selecting Channels shows it over the inbox`() {
        navigation.select(Destination.CHANNELS)

        assertEquals(Destination.CHANNELS, navigation.selected)
        assertEquals(listOf<NavKey>(InboxKey, ChannelsKey), navigation.shown(inbox, channels))
    }

    @Test
    fun `a row opens its channel page once, on the Channels stack`() {
        navigation.select(Destination.CHANNELS)

        navigation.openChannel(ChannelsKey, CHANNEL)
        navigation.openChannel(ChannelsKey, OTHER_CHANNEL)

        assertEquals(2, channels.size)
        assertEquals(CHANNEL, assertIs<ChannelKey>(channels.last()).channelId)
        assertEquals(listOf<NavKey>(InboxKey), inbox)
        assertTrue(navigation.showsNavigationBar())
    }

    @Test
    fun `an inbox card opens its channel page once, on the inbox stack`() {
        navigation.openChannel(InboxKey, CHANNEL)
        navigation.openChannel(InboxKey, OTHER_CHANNEL)

        assertEquals(2, inbox.size)
        assertEquals(CHANNEL, assertIs<ChannelKey>(inbox.last()).channelId)
        assertEquals(listOf<NavKey>(ChannelsKey), channels)
    }

    @Test
    fun `switching keeps each destination's history`() {
        navigation.select(Destination.CHANNELS)
        navigation.openChannel(ChannelsKey, CHANNEL)
        val page = channels.last()

        navigation.select(Destination.INBOX)
        assertEquals(listOf<NavKey>(InboxKey), navigation.shown(inbox, channels))
        navigation.select(Destination.CHANNELS)

        assertEquals(listOf(InboxKey, ChannelsKey, page), navigation.shown(inbox, channels))
        navigation.goBack()
        assertEquals(listOf<NavKey>(ChannelsKey), channels)
    }

    @Test
    fun `back on the Channels screen shows the inbox destination as it was`() {
        navigation.openChannel(InboxKey, CHANNEL)
        navigation.select(Destination.CHANNELS)

        navigation.goBack()

        assertEquals(Destination.INBOX, navigation.selected)
        assertEquals(2, inbox.size)
        assertEquals(listOf<NavKey>(ChannelsKey), channels)
    }

    @Test
    fun `back on the inbox changes nothing, so Android leaves the app`() {
        navigation.goBack()

        assertEquals(Destination.INBOX, navigation.selected)
        assertEquals(listOf<NavKey>(InboxKey), inbox)
    }

    @Test
    fun `reselecting a destination returns to its first screen, and on it changes nothing`() {
        navigation.select(Destination.CHANNELS)
        navigation.openChannel(ChannelsKey, CHANNEL)
        navigation.openVideo(VIDEO)
        navigation.close(channels.last())

        navigation.select(Destination.CHANNELS)
        assertEquals(listOf<NavKey>(ChannelsKey), channels)

        navigation.select(Destination.CHANNELS)
        assertEquals(listOf<NavKey>(ChannelsKey), channels)
        assertEquals(Destination.CHANNELS, navigation.selected)
    }

    @Test
    fun `search opens from either destination once, and hides the bar`() {
        navigation.select(Destination.CHANNELS)

        navigation.openSearch(ChannelsKey)
        navigation.openSearch(ChannelsKey)

        assertEquals(2, channels.size)
        assertIs<SearchKey>(channels.last())
        assertFalse(navigation.showsNavigationBar())
        assertTrue(navigation.showsMiniPlayer())
    }

    @Test
    fun `the import opens from either destination once, hides the bar, and closes back`() {
        navigation.openTakeoutImport(InboxKey)
        navigation.openTakeoutImport(InboxKey)
        val fromInbox = assertIs<TakeoutImportKey>(inbox.last())
        assertEquals(2, inbox.size)
        assertFalse(navigation.showsNavigationBar())
        navigation.close(fromInbox)
        assertEquals(listOf<NavKey>(InboxKey), inbox)

        navigation.select(Destination.CHANNELS)
        navigation.openTakeoutImport(ChannelsKey)
        val fromChannels = assertIs<TakeoutImportKey>(channels.last())
        assertFalse(navigation.showsNavigationBar())
        navigation.close(fromChannels)

        assertEquals(listOf<NavKey>(ChannelsKey), channels)
        assertTrue(navigation.showsNavigationBar())
    }

    @Test
    fun `search in both destinations gets two entries`() {
        navigation.openSearch(InboxKey)
        val inboxSearch = assertIs<SearchKey>(inbox.last())
        navigation.openVideo(VIDEO)
        navigation.openChannel(inbox.last(), CHANNEL)
        navigation.select(Destination.CHANNELS)
        navigation.openSearch(ChannelsKey)
        val channelsSearch = assertIs<SearchKey>(channels.last())

        assertNotEquals(inboxSearch.entryId, channelsSearch.entryId)
        assertEquals(2, navigation.shown(inbox, channels).count { it is SearchKey })
    }

    @Test
    fun `a video opens on the selected stack and hides the bar and the mini player`() {
        navigation.select(Destination.CHANNELS)

        navigation.openVideo(VIDEO)

        assertIs<PlayerKey>(channels.last())
        assertEquals(listOf<NavKey>(InboxKey), inbox)
        assertFalse(navigation.showsNavigationBar())
        assertFalse(navigation.showsMiniPlayer())
    }

    @Test
    fun `close removes a screen from its stack and never a first screen`() {
        navigation.select(Destination.CHANNELS)
        navigation.openChannel(ChannelsKey, CHANNEL)
        val page = channels.last()

        navigation.close(page)
        navigation.close(page)
        navigation.close(ChannelsKey)
        navigation.close(InboxKey)

        assertEquals(listOf<NavKey>(ChannelsKey), channels)
        assertEquals(listOf<NavKey>(InboxKey), inbox)
    }
}
