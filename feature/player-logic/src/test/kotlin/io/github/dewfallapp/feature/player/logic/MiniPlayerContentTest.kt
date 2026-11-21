package io.github.dewfallapp.feature.player.logic

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

private const val ID = "playback 1"
private const val TITLE = "How reading rooms were lit before electric lamps"
private const val CHANNEL = "Larkspur Lectures"

class MiniPlayerContentTest {
    @Test
    fun `no current item shows nothing`() {
        assertNull(miniPlayerContent(currentItemId = null, TITLE, CHANNEL, live = false))
    }

    @Test
    fun `a current item shows its title, channel and progress`() {
        assertEquals(
            MiniPlayerContent(ID, TITLE, CHANNEL, showsProgress = true),
            miniPlayerContent(ID, TITLE, CHANNEL, live = false),
        )
    }

    @Test
    fun `an empty or blank title counts as none`() {
        assertNull(miniPlayerContent(ID, title = null, CHANNEL, live = false)?.title)
        assertNull(miniPlayerContent(ID, title = "", CHANNEL, live = false)?.title)
        assertNull(miniPlayerContent(ID, title = " \n", CHANNEL, live = false)?.title)
    }

    @Test
    fun `no channel name counts as none`() {
        assertNull(miniPlayerContent(ID, TITLE, channelName = null, live = false)?.channelName)
        assertNull(miniPlayerContent(ID, TITLE, channelName = "", live = false)?.channelName)
    }

    @Test
    fun `a live stream has no progress line`() {
        assertFalse(miniPlayerContent(ID, TITLE, CHANNEL, live = true)!!.showsProgress)
        assertTrue(miniPlayerContent(ID, TITLE, CHANNEL, live = false)!!.showsProgress)
    }

    @Test
    fun `the text never shows the title or the channel`() {
        val text = miniPlayerContent(ID, TITLE, CHANNEL, live = false).toString()

        assertFalse(TITLE in text)
        assertFalse(CHANNEL in text)
    }
}
