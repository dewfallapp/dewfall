package io.github.dewfallapp.core.playback

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** Records the steps that a refresh takes, in order. */
private class FakeRefreshTarget : RefreshTarget<String> {
    val steps = mutableListOf<String>()

    override fun replaceItem(item: String) {
        steps += "replace $item"
    }

    override fun seekTo(positionMs: Long) {
        steps += "seek $positionMs"
    }

    override fun seekToLiveEdge() {
        steps += "live edge"
    }

    override fun prepare() {
        steps += "prepare"
    }

    override fun play() {
        steps += "play"
    }
}

class RefreshTest {
    @Test
    fun `a video goes on from its old position, and plays if it was playing`() {
        val target = FakeRefreshTarget()

        refresh(target, "new", RefreshPoint(positionMs = 61_000, live = false, wasPlaying = true))

        assertEquals(listOf("replace new", "seek 61000", "prepare", "play"), target.steps)
    }

    @Test
    fun `a live stream goes back to its live edge, and stays paused if it was paused`() {
        val target = FakeRefreshTarget()

        refresh(target, "new", RefreshPoint(positionMs = 61_000, live = true, wasPlaying = false))

        assertEquals(listOf("replace new", "live edge", "prepare"), target.steps)
    }

    @Test
    fun `403 and 410 refresh once, and a second refusal in a row stands`() {
        val target = FakeRefreshTarget()
        val limit = RefreshLimit()
        val point = RefreshPoint(positionMs = 0, live = false, wasPlaying = true)

        for (status in listOf(403, 403)) {
            if (limit.shouldRefresh(status)) refresh(target, "new", point)
        }

        assertEquals(listOf("replace new", "seek 0", "prepare", "play"), target.steps)
        assertFalse(limit.shouldRefresh(410))
    }

    @Test
    fun `the limit resets once the item is ready, and other statuses never refresh`() {
        val limit = RefreshLimit()

        assertTrue(limit.shouldRefresh(410))
        limit.reset()

        assertTrue(limit.shouldRefresh(403))
        limit.reset()
        assertFalse(limit.shouldRefresh(404))
        assertFalse(limit.shouldRefresh(null))
    }
}
