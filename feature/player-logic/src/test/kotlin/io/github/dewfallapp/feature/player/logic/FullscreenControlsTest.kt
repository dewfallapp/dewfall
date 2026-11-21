package io.github.dewfallapp.feature.player.logic

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

private val SHOWN = FullscreenControls(shown = true)
private val HIDDEN = FullscreenControls(shown = false)

class FullscreenControlsTest {
    @Test
    fun `they start shown`() {
        assertTrue(FullscreenControls().shown)
    }

    @Test
    fun `while playing without a screen reader, they show as the user chose`() {
        assertTrue(SHOWN.visible(playing = true, screenReader = false))
        assertFalse(HIDDEN.visible(playing = true, screenReader = false))
    }

    @Test
    fun `they show whenever the video isn't playing`() {
        assertTrue(SHOWN.visible(playing = false, screenReader = false))
        assertTrue(HIDDEN.visible(playing = false, screenReader = false))
    }

    @Test
    fun `they show whenever a screen reader is on`() {
        assertTrue(SHOWN.visible(playing = true, screenReader = true))
        assertTrue(HIDDEN.visible(playing = true, screenReader = true))
        assertTrue(HIDDEN.visible(playing = false, screenReader = true))
    }

    @Test
    fun `a tap toggles them only while playing without a screen reader`() {
        assertEquals(HIDDEN, SHOWN.tapped(playing = true, screenReader = false))
        assertEquals(SHOWN, HIDDEN.tapped(playing = true, screenReader = false))
    }

    @Test
    fun `a tap while paused or with a screen reader keeps them shown`() {
        assertEquals(SHOWN, SHOWN.tapped(playing = false, screenReader = false))
        assertEquals(SHOWN, HIDDEN.tapped(playing = false, screenReader = false))
        assertEquals(SHOWN, SHOWN.tapped(playing = true, screenReader = true))
        assertEquals(SHOWN, HIDDEN.tapped(playing = true, screenReader = true))
    }

    @Test
    fun `starting or stopping playing shows them`() {
        assertEquals(SHOWN, HIDDEN.playingChanged())
        assertEquals(SHOWN, SHOWN.playingChanged())
    }

    @Test
    fun `a touch or a key shows them`() {
        assertEquals(SHOWN, HIDDEN.touched())
        assertEquals(SHOWN, SHOWN.touched())
    }

    @Test
    fun `the wait hides them`() {
        assertEquals(HIDDEN, SHOWN.timedOut())
    }

    @Test
    fun `they hide after the wait only while shown and playing without a screen reader`() {
        assertTrue(SHOWN.hidesAfterTimeout(playing = true, screenReader = false))
        assertFalse(HIDDEN.hidesAfterTimeout(playing = true, screenReader = false))
        assertFalse(SHOWN.hidesAfterTimeout(playing = false, screenReader = false))
        assertFalse(SHOWN.hidesAfterTimeout(playing = true, screenReader = true))
        assertFalse(HIDDEN.hidesAfterTimeout(playing = false, screenReader = true))
    }

    @Test
    fun `they don't hide while a finger is held down, such as on the seek bar`() {
        assertFalse(SHOWN.hidesAfterTimeout(playing = true, screenReader = false, held = true))
        assertTrue(SHOWN.hidesAfterTimeout(playing = true, screenReader = false, held = false))
    }
}
