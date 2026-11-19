package io.github.dewfallapp.feature.player.logic

import io.github.dewfallapp.core.model.PlaybackFailure
import io.github.dewfallapp.core.model.YouTubeError
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class PlaybackTest {
    @Test
    fun `the first opening plays, a configuration change does nothing, and a lost session waits`() {
        assertEquals(StartAction.PLAY, startAction(started = false, playbackIdKnown = false))
        assertEquals(StartAction.PLAY, startAction(started = false, playbackIdKnown = true))
        assertEquals(StartAction.NOTHING, startAction(started = true, playbackIdKnown = true))
        assertEquals(
            StartAction.WAIT_FOR_PLAY,
            startAction(started = true, playbackIdKnown = false),
        )
    }

    @Test
    fun `a failure that the details already say shows once`() {
        val details = DetailsArea.Failed(1, YouTubeError.NETWORK)

        assertTrue(sharesFailure(details, PlaybackFailure.NETWORK))
        assertNull(videoAreaFailure(details, PlaybackFailure.NETWORK))
    }

    @Test
    fun `BROKEN and BLOCKED details share YOUTUBE's words`() {
        assertTrue(
            sharesFailure(DetailsArea.Failed(1, YouTubeError.BROKEN), PlaybackFailure.YOUTUBE)
        )
        assertTrue(
            sharesFailure(DetailsArea.Failed(1, YouTubeError.BLOCKED), PlaybackFailure.YOUTUBE)
        )
    }

    @Test
    fun `different failures, or a failure beside working details, show in the video area`() {
        val failed = DetailsArea.Failed(1, YouTubeError.NETWORK)

        assertFalse(sharesFailure(failed, PlaybackFailure.NOT_DECODABLE))
        assertEquals(
            PlaybackFailure.NOT_DECODABLE,
            videoAreaFailure(failed, PlaybackFailure.NOT_DECODABLE),
        )
        assertEquals(
            PlaybackFailure.OTHER,
            videoAreaFailure(DetailsArea.Loading(1), PlaybackFailure.OTHER),
        )
        assertFalse(sharesFailure(failed, null))
        assertNull(videoAreaFailure(failed, null))
    }

    @Test
    fun `times read like the frames`() {
        assertEquals("0:00", clockText(0))
        assertEquals("0:00", clockText(-5))
        assertEquals("0:59", clockText(59_999))
        assertEquals("18:24", clockText(1_104_000))
        assertEquals("59:59", clockText(3_599_000))
        assertEquals("1:00:00", clockText(3_600_000))
        assertEquals("1:14:02", clockText(4_442_000))
    }
}
