package io.github.dewfallapp.core.playback

import androidx.media3.common.MediaItem
import io.github.dewfallapp.core.model.PlaybackFailure
import io.github.dewfallapp.core.model.VideoId
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue
import kotlinx.coroutines.test.runTest

private val VIDEO = VideoId("aqz-KE-bpKQ")

class ResolveItemTest {
    private val items = PlaybackItems()

    @Test
    fun `a playback that can't start drops its video ID`() = runTest {
        val playbackId = items.add(VIDEO)
        val other = items.add(VideoId("YE7VzlLtp-4"))

        val resolution =
            resolveItem(
                items,
                { _, _, _ -> Resolution.Failed(PlaybackFailure.NETWORK) },
                playbackId,
                false,
            )

        assertEquals(Resolution.Failed(PlaybackFailure.NETWORK), resolution)
        assertFalse(items.contains(playbackId))
        assertTrue(items.contains(other))
    }

    @Test
    fun `a playback that resolves keeps its video ID, and the resolver gets the video`() = runTest {
        val playbackId = items.add(VIDEO)
        val asked = mutableListOf<Pair<VideoId, Boolean>>()

        val resolution =
            resolveItem(
                items,
                { _, videoId, metered ->
                    asked += videoId to metered
                    Resolution.Resolved(MediaItem.EMPTY)
                },
                playbackId,
                metered = true,
            )

        assertIs<Resolution.Resolved>(resolution)
        assertEquals(listOf(VIDEO to true), asked)
        assertTrue(items.contains(playbackId))
    }

    @Test
    fun `an unknown or missing playback ID fails as OTHER without asking`() = runTest {
        val unreachable = ItemResolver { _, _, _ -> error("The resolver shouldn't be asked") }

        assertEquals(
            Resolution.Failed(PlaybackFailure.OTHER),
            resolveItem(items, unreachable, "unknown", false),
        )
        assertEquals(
            Resolution.Failed(PlaybackFailure.OTHER),
            resolveItem(items, unreachable, null, false),
        )
    }

    @Test
    fun `removing a playback ID drops it even if its item never became the player's`() {
        val waiting = items.add(VIDEO)
        val playing = items.add(VideoId("YE7VzlLtp-4"))
        items.currentChanged(playing)

        items.remove(waiting)

        assertFalse(items.contains(waiting))
        assertTrue(items.contains(playing))
    }
}
