package io.github.dewfallapp.core.playback

import io.github.dewfallapp.core.data.download.IncompleteReason
import io.github.dewfallapp.core.data.download.PlayableDownload
import io.github.dewfallapp.core.data.playback.StreamLinksRepository
import io.github.dewfallapp.core.logging.LogLevel
import io.github.dewfallapp.core.logging.Logger
import io.github.dewfallapp.core.model.AudioFormat
import io.github.dewfallapp.core.model.FormatChoice
import io.github.dewfallapp.core.model.PlaybackFailure
import io.github.dewfallapp.core.model.StreamLinks
import io.github.dewfallapp.core.model.VideoFormat
import io.github.dewfallapp.core.model.VideoId
import io.github.dewfallapp.core.model.YouTubeError
import io.github.dewfallapp.core.model.YouTubeResult
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertSame
import kotlin.test.assertTrue
import kotlinx.coroutines.test.runTest

private val VIDEO = VideoId("aqz-KE-bpKQ")

// Made-up links and paths; never real ones.
private const val VIDEO_LINK = "https://rr1---sn-masked.googlevideo.com/videoplayback?itag=136"
private const val AUDIO_LINK = "https://rr1---sn-masked.googlevideo.com/videoplayback?itag=251"
private val videoFile = File("/data/downloads/folder-1/video.mp4")
private val audioFile = File("/data/downloads/folder-1/audio.webm")

private val video =
    VideoFormat(VIDEO_LINK, 136, "video/mp4", "avc1.4d401f", null, null, 1280, 720, 30, false)
private val audio = AudioFormat(AUDIO_LINK, 251, "audio/webm", "opus", null, null, null)
private val videoLinks = StreamLinks(VIDEO, listOf(audio), listOf(video), null, null)

class ChooseTagTest {
    private val lines = mutableListOf<String>()
    private val log =
        PlaybackLog(Logger({ level, tag, text -> lines += "$level $tag $text" }, LogLevel.DEBUG))
    private val asked = mutableListOf<VideoId>()

    /** Stream links that give [result], counting each ask. */
    private fun streamLinks(
        result: YouTubeResult<StreamLinks> = YouTubeResult.Success(videoLinks),
    ) = StreamLinksRepository { videoId ->
        asked += videoId
        result
    }

    private suspend fun choose(
        download: PlayableDownload,
        links: StreamLinksRepository = streamLinks(),
        metered: Boolean = false,
    ): TagChoice =
        chooseTag(VIDEO, metered, { _ -> download }, links, log) { got, onMetered ->
            assertSame(videoLinks, got)
            assertEquals(metered, onMetered)
            FormatChoice.Separate(video, audio)
        }

    @Test
    fun `downloaded files play without asking for stream links`() = runTest {
        val files = PlayableDownload.Separate(videoFile, audioFile)

        val choice = assertIs<TagChoice.Plays>(choose(files, metered = true))

        assertSame(files, assertIs<PlaybackTag.Downloaded>(choice.tag).files)
        assertEquals(emptyList(), asked)
        assertEquals(listOf("INFO Dewfall.Playback Playing a download"), lines)
    }

    @Test
    fun `a video with no finished download streams, and logs nothing`() = runTest {
        val choice = assertIs<TagChoice.Plays>(choose(PlayableDownload.NotDownloaded))

        val tag = assertIs<PlaybackTag.Streamed>(choice.tag)
        assertEquals(FormatChoice.Separate(video, audio), tag.choice)
        assertEquals(listOf(VIDEO), asked)
        assertEquals(emptyList(), lines)
    }

    @Test
    fun `a finished download whose files can't play streams, and says why`() = runTest {
        for (reason in IncompleteReason.entries) {
            lines.clear()
            asked.clear()

            val choice = assertIs<TagChoice.Plays>(choose(PlayableDownload.Incomplete(reason)))

            assertIs<PlaybackTag.Streamed>(choice.tag)
            assertEquals(listOf(VIDEO), asked)
            assertEquals(
                listOf("WARN Dewfall.Playback Download can't play, streaming reason=$reason"),
                lines,
            )
        }
    }

    @Test
    fun `a download that can't be read streams`() = runTest {
        val choice = assertIs<TagChoice.Plays>(choose(PlayableDownload.Unreadable))

        assertIs<PlaybackTag.Streamed>(choice.tag)
        assertEquals(listOf(VIDEO), asked)
        assertEquals(listOf("WARN Dewfall.Playback Download unreadable, streaming"), lines)
    }

    @Test
    fun `a stream that fails fails as before`() = runTest {
        val failing = streamLinks(YouTubeResult.Failure(YouTubeError.NETWORK))

        assertEquals(
            TagChoice.Fails(PlaybackFailure.NETWORK),
            choose(PlayableDownload.NotDownloaded, failing),
        )
    }

    @Test
    fun `stream links the phone can't decode fail as before`() = runTest {
        val choice =
            chooseTag(VIDEO, false, { _ -> PlayableDownload.NotDownloaded }, streamLinks(), log) {
                _,
                _ ->
                FormatChoice.NotDecodable
            }

        assertEquals(TagChoice.Fails(PlaybackFailure.NOT_DECODABLE), choice)
    }

    @Test
    fun `the log lines hold no video ID or path`() = runTest {
        choose(PlayableDownload.Separate(videoFile, audioFile))
        choose(PlayableDownload.Incomplete(IncompleteReason.SIZE))
        choose(PlayableDownload.Unreadable)

        assertTrue(lines.isNotEmpty())
        for (line in lines) {
            assertFalse(line.contains(VIDEO.value), line)
            assertFalse(line.contains("folder-1"), line)
        }
    }
}
