package io.github.dewfallapp.core.playback

import androidx.media3.common.C
import androidx.media3.common.PlaybackException
import io.github.dewfallapp.core.data.download.PlayableDownload
import io.github.dewfallapp.core.logging.LogLevel
import io.github.dewfallapp.core.logging.Logger
import io.github.dewfallapp.core.model.AudioFormat
import io.github.dewfallapp.core.model.FormatChoice
import io.github.dewfallapp.core.model.PlaybackFailure
import io.github.dewfallapp.core.model.VideoFormat
import io.github.dewfallapp.core.model.VideoId
import java.io.File
import java.io.FileNotFoundException
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

// Made-up links; never real ones.
private const val VIDEO_LINK = "https://rr1---sn-masked.googlevideo.com/videoplayback?itag=137"
private const val AUDIO_LINK = "https://rr1---sn-masked.googlevideo.com/videoplayback?itag=251"
private const val MANIFEST = "https://manifest.googlevideo.com/api/manifest/hls_variant/id/MASKED"

private val video =
    VideoFormat(VIDEO_LINK, 137, "video/mp4", "avc1.640028", null, null, 1920, 1080, 30, false)
private val audio = AudioFormat(AUDIO_LINK, 251, "audio/webm", "opus", null, null, null)

class PlaybackPartsTest {
    @Test
    fun `the source plan follows the tag, and an item without it is refused`() {
        fun plan(choice: FormatChoice) = sourcePlan(PlaybackTag.Streamed(choice, metered = false))

        assertEquals(
            SourcePlan.Merged(VIDEO_LINK, AUDIO_LINK),
            plan(FormatChoice.Separate(video, audio)),
        )
        assertEquals(SourcePlan.Progressive(VIDEO_LINK), plan(FormatChoice.Combined(video)))
        assertEquals(SourcePlan.Hls(MANIFEST), plan(FormatChoice.Live(MANIFEST)))
        assertEquals(SourcePlan.Refused, plan(FormatChoice.NotDecodable))
        assertEquals(SourcePlan.Refused, sourcePlan(null))
        assertEquals(SourcePlan.Refused, sourcePlan("not a tag"))
    }

    @Test
    fun `a downloaded tag plans its files, merged or alone, and is never live`() {
        val videoFile = File("/data/downloads/folder-1/video.mp4")
        val audioFile = File("/data/downloads/folder-1/audio.webm")
        val separate = PlaybackTag.Downloaded(PlayableDownload.Separate(videoFile, audioFile))
        val combined = PlaybackTag.Downloaded(PlayableDownload.Combined(videoFile))

        assertEquals(SourcePlan.Files(videoFile, audioFile), sourcePlan(separate))
        assertEquals(SourcePlan.Files(videoFile, audio = null), sourcePlan(combined))
        assertFalse(separate.live)
        assertFalse(combined.live)
        assertTrue(PlaybackTag.Streamed(FormatChoice.Live(MANIFEST), metered = false).live)
        assertFalse(separate.toString().contains("folder-1"))
    }

    @Test
    fun `a files plan shows no path, and its equality compares both files`() {
        val videoFile = File("/data/downloads/folder-1/video.mp4")
        val audioFile = File("/data/downloads/folder-1/audio.webm")
        val separate = SourcePlan.Files(videoFile, audioFile)
        val combined = SourcePlan.Files(videoFile, audio = null)

        assertFalse(separate.toString().contains("folder-1"))
        assertFalse(combined.toString().contains("folder-1"))
        assertEquals(SourcePlan.Files(videoFile, audioFile).hashCode(), separate.hashCode())
        assertNotEquals(SourcePlan.Files(videoFile, File("/data/other/audio.webm")), separate)
        assertNotEquals(SourcePlan.Files(audioFile, audioFile), separate)
        assertNotEquals(combined, separate)
        assertNotEquals(separate, combined)
    }

    @Test
    fun `a downloaded item keeps only the CPU awake, and anything else Wi-Fi too`() {
        val downloaded =
            PlaybackTag.Downloaded(PlayableDownload.Combined(File("/data/downloads/f/media.mp4")))

        assertEquals(C.WAKE_MODE_LOCAL, wakeModeFor(downloaded))
        assertEquals(
            C.WAKE_MODE_NETWORK,
            wakeModeFor(PlaybackTag.Streamed(FormatChoice.Combined(video), metered = false)),
        )
        assertEquals(C.WAKE_MODE_NETWORK, wakeModeFor(null))
    }

    @Test
    fun `the tag's text never shows the links`() {
        val tag = PlaybackTag.Streamed(FormatChoice.Separate(video, audio), metered = true)

        assertFalse(tag.toString().contains("googlevideo"))
    }

    @Test
    fun `an entry is dropped when its item is replaced or cleared, not when it is refreshed`() {
        val items = PlaybackItems()
        val first = items.add(VideoId("aqz-KE-bpKQ"))
        val second = items.add(VideoId("YE7VzlLtp-4"))

        items.currentChanged(first)
        items.currentChanged(first)
        assertTrue(items.contains(first))
        assertTrue(items.contains(second))

        val third = items.add(VideoId("dQw4w9WgXcQ"))
        items.currentChanged(second)
        assertFalse(items.contains(first))
        assertTrue(items.contains(third))

        items.currentChanged(null)
        assertFalse(items.contains(second))
        assertNotEquals(first, second)
        assertEquals(VideoId("dQw4w9WgXcQ"), items.videoId(third))

        items.clear()
        assertNull(items.videoId(third))
    }

    @Test
    fun `a description goes with its playback's entry`() {
        val items = PlaybackItems()
        val first = items.add(VideoId("aqz-KE-bpKQ"))
        val second = items.add(VideoId("YE7VzlLtp-4"))
        val before = items.described.value

        items.describe(first, PlaybackDescription("A title", "A channel"))
        assertEquals(PlaybackDescription("A title", "A channel"), items.description(first))
        assertEquals(before + 1, items.described.value)
        assertNull(items.description(second))

        items.describe(first, PlaybackDescription("Another title", "A channel"))
        assertEquals("Another title", items.description(first)?.title)

        // Replaced by another item.
        items.currentChanged(first)
        items.currentChanged(second)
        assertNull(items.description(first))

        // Removed, and cleared.
        items.describe(second, PlaybackDescription("A title", "A channel"))
        items.remove(second)
        assertNull(items.description(second))
        val third = items.add(VideoId("dQw4w9WgXcQ"))
        items.describe(third, PlaybackDescription("A title", "A channel"))
        items.clear()
        assertNull(items.description(third))
    }

    @Test
    fun `an unknown playback ID gets no description`() {
        val items = PlaybackItems()
        val before = items.described.value

        items.describe("unknown", PlaybackDescription("A title", "A channel"))

        assertNull(items.description("unknown"))
        assertEquals(before, items.described.value)
    }

    @Test
    fun `a description's text never shows the title or the channel`() {
        val text = PlaybackDescription("A title", "A channel").toString()

        assertFalse(text.contains("A title"))
        assertFalse(text.contains("A channel"))
    }

    @Test
    fun `the app counts as visible until a screen says otherwise`() {
        val sessions = PlaybackSessions()
        assertTrue(sessions.picture.value.appVisible)

        sessions.setAppVisible(false)
        assertFalse(sessions.picture.value.appVisible)

        sessions.setAppVisible(true)
        assertTrue(sessions.picture.value.appVisible)
    }

    @Test
    fun `the picture shows only while the app can be seen and a screen holds it`() {
        val sessions = PlaybackSessions()
        assertFalse(sessions.picture.value.showsPicture, "no holder")

        val first = sessions.holdPicture()
        assertTrue(sessions.picture.value.showsPicture, "one holder")

        sessions.setAppVisible(false)
        assertFalse(sessions.picture.value.showsPicture, "the app hidden")
        sessions.setAppVisible(true)

        val second = sessions.holdPicture()
        first.release()
        assertTrue(sessions.picture.value.showsPicture, "the second holder")

        second.release()
        assertFalse(sessions.picture.value.showsPicture, "both released")
    }

    @Test
    fun `releasing a hold twice lowers the count once`() {
        val sessions = PlaybackSessions()
        val first = sessions.holdPicture()
        sessions.holdPicture()

        first.release()
        first.release()

        assertEquals(1, sessions.picture.value.holds)
        assertTrue(sessions.picture.value.showsPicture)
    }

    @Test
    fun `only a network that isn't metered counts as unmetered`() {
        assertTrue(isMetered(hasNetwork = false, notMetered = false))
        // A metered network, or one that is only temporarily not metered.
        assertTrue(isMetered(hasNetwork = true, notMetered = false))
        assertFalse(isMetered(hasNetwork = true, notMetered = true))
    }

    @Test
    fun `the failure line holds the category and the error code, and nothing else`() {
        val lines = mutableListOf<String>()
        val log =
            PlaybackLog(
                Logger(
                    { level, tag, text -> lines += "$level $tag $text" },
                    minLevel = LogLevel.DEBUG,
                ),
            )

        log.failed(PlaybackFailure.NETWORK, errorCode = 2001)
        log.failed(PlaybackFailure.PRIVATE, errorCode = null)
        // FileDataSource's error carries the file's path in its message.
        log.failed(
            PlaybackFailure.OTHER,
            errorCode = PlaybackException.ERROR_CODE_IO_FILE_NOT_FOUND,
            error = FileNotFoundException("/data/downloads/folder-1/audio.webm (No such file)"),
        )

        assertEquals(
            listOf(
                "WARN Dewfall.Playback Playback failed category=NETWORK errorCode=2001",
                "WARN Dewfall.Playback Playback failed category=PRIVATE",
            ),
            lines.take(2),
        )
        assertEquals(3, lines.size)
        assertTrue(lines[2].startsWith("WARN Dewfall.Playback Playback failed category=OTHER"))
        assertFalse(lines[2].contains("folder-1"), lines[2])
        assertFalse(lines[2].contains("audio.webm"), lines[2])
    }
}

class StopsPlaybackTest {
    @Test
    fun `stopping clears the player only while its item is the screen's own`() {
        assertTrue(stopsPlayback(currentItemId = "first", playbackId = "first"))
        // A second video started while the first one's screen was leaving.
        assertFalse(stopsPlayback(currentItemId = "second", playbackId = "first"))
        assertFalse(stopsPlayback(currentItemId = null, playbackId = "first"))
    }
}

class LeaveDecisionTest {
    private val nothing =
        LeaveDecision(hidesApp = false, stopsWaitingStart = false, pausesPlayer = false)

    @Test
    fun `leaving pauses only a playback that hasn't played`() {
        assertEquals(
            LeaveDecision(hidesApp = true, stopsWaitingStart = false, pausesPlayer = true),
            leaveDecision(false, waitingId = null, currentItemId = "first", playedId = null),
        )
        assertEquals(
            LeaveDecision(hidesApp = true, stopsWaitingStart = false, pausesPlayer = false),
            leaveDecision(false, waitingId = null, currentItemId = "first", playedId = "first"),
        )
    }

    @Test
    fun `a configuration change does nothing, whether the playback played or not`() {
        assertEquals(nothing, leaveDecision(true, waitingId = null, "first", playedId = null))
        assertEquals(nothing, leaveDecision(true, waitingId = null, "first", playedId = "first"))
        assertEquals(nothing, leaveDecision(true, waitingId = "second", null, playedId = null))
    }

    @Test
    fun `a start that waits for the connection won't play`() {
        assertEquals(
            LeaveDecision(hidesApp = true, stopsWaitingStart = true, pausesPlayer = false),
            leaveDecision(false, waitingId = "second", currentItemId = null, playedId = "first"),
        )
    }

    @Test
    fun `a new video that hasn't played pauses, though an older one did`() {
        assertTrue(
            leaveDecision(false, waitingId = null, "second", playedId = "first").pausesPlayer,
        )
    }

    @Test
    fun `with no item and no waiting start, leaving only hides the app`() {
        assertEquals(
            LeaveDecision(hidesApp = true, stopsWaitingStart = false, pausesPlayer = false),
            leaveDecision(false, waitingId = null, currentItemId = null, playedId = null),
        )
    }
}

class ReleaseDropsTest {
    @Test
    fun `releasing keeps the player's item and the waiting start for the mini player`() {
        assertFalse(releaseDrops(waitingId = null, currentItemId = "first", playbackId = "first"))
        assertFalse(releaseDrops(waitingId = "first", currentItemId = null, playbackId = "first"))
    }

    @Test
    fun `releasing drops a playback that has no item and doesn't wait`() {
        assertTrue(releaseDrops(waitingId = null, currentItemId = null, playbackId = "first"))
        // Another video took the player's place.
        assertTrue(releaseDrops(waitingId = "third", currentItemId = "second", "first"))
    }
}
