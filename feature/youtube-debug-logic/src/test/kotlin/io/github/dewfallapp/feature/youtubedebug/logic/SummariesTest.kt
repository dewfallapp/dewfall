package io.github.dewfallapp.feature.youtubedebug.logic

import io.github.dewfallapp.core.model.AudioTrack
import io.github.dewfallapp.core.model.AudioTrackKind
import io.github.dewfallapp.core.model.ChannelUploads
import io.github.dewfallapp.core.model.PublishedTime
import io.github.dewfallapp.core.model.SearchResults
import io.github.dewfallapp.core.model.StreamLinks
import io.github.dewfallapp.core.model.VideoDetails
import io.github.dewfallapp.core.model.VideoKind
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Instant

class SummariesTest {
    @Test
    fun `a search summary keeps the order and counts every kind, zeros too`() {
        val results =
            SearchResults(
                channels = listOf(channel("First", avatarCount = 3), channel("Second")),
                videos =
                    listOf(
                        video("A", VideoKind.VIDEO),
                        video("B", VideoKind.SHORT),
                        video("C", VideoKind.VIDEO),
                    ),
            )

        val summary = results.toSummary()

        assertEquals(listOf(ChannelLine("First", 3), ChannelLine("Second", 0)), summary.channels)
        assertEquals(listOf("A", "B", "C"), summary.videos.map { it.title })
        assertEquals(
            mapOf(
                VideoKind.VIDEO to 2,
                VideoKind.SHORT to 1,
                VideoKind.LIVE to 0,
                VideoKind.UPCOMING to 0,
            ),
            summary.videoKinds,
        )
        assertEquals(VideoKind.entries, summary.videoKinds.keys.toList())
    }

    @Test
    fun `a video line keeps what proves the item was read`() {
        val published = PublishedTime(Instant.parse("2026-01-02T03:04:05Z"), isApproximate = true)
        val item =
            video("A video", VideoKind.LIVE).copy(duration = 10.minutes, published = published)

        val line =
            SearchResults(channels = emptyList(), videos = listOf(item)).toSummary().videos.single()

        assertEquals(
            VideoLine(
                title = "A video",
                channelName = "A channel",
                kind = VideoKind.LIVE,
                duration = 10.minutes,
                published = published,
                thumbnailCount = 2,
            ),
            line,
        )
    }

    @Test
    fun `a video details summary measures the description`() {
        val details = videoDetails(description = "First line\nSecond line")

        val summary = details.toSummary()

        assertEquals(22, summary.descriptionLength)
        assertEquals(2, summary.descriptionLineCount)
        assertEquals(1, summary.thumbnailCount)
        assertEquals(3, summary.channelAvatarCount)
        assertEquals(TEST_CHANNEL_ID, summary.channelId)
    }

    @Test
    fun `an empty description has no lines`() {
        val summary = videoDetails(description = "").toSummary()

        assertEquals(0, summary.descriptionLength)
        assertEquals(0, summary.descriptionLineCount)
    }

    @Test
    fun `an uploads summary says whether YouTube sent the channel on`() {
        val uploads =
            ChannelUploads(
                channel = channel("NASA"),
                videos = listOf(video(kind = VideoKind.UPCOMING), video()),
            )

        val same = uploads.toSummary(askedFor = TEST_CHANNEL_ID)
        val sentOn = uploads.toSummary(askedFor = OTHER_CHANNEL_ID)

        assertTrue(same.isChannelAskedFor)
        assertFalse(sentOn.isChannelAskedFor)
        assertEquals(TEST_CHANNEL_ID, sentOn.channelId)
        assertEquals("NASA", same.channelName)
        assertEquals(listOf(VideoKind.UPCOMING, VideoKind.VIDEO), same.videos.map { it.kind })
        assertEquals(0, same.videoKinds.getValue(VideoKind.SHORT))
    }

    @Test
    fun `a stream links summary counts formats by type and sound`() {
        val links =
            StreamLinks(
                videoId = TEST_VIDEO_ID,
                audioFormats =
                    listOf(
                        audioFormat("audio/webm"),
                        audioFormat("audio/mp4"),
                        audioFormat("audio/webm"),
                    ),
                videoFormats =
                    listOf(
                        videoFormat("video/mp4", includesAudio = true),
                        videoFormat("video/webm"),
                        videoFormat("video/mp4"),
                    ),
                hlsManifestUrl =
                    "https://manifest.googlevideo.com/api/manifest/hls_variant/expire/1",
                expiresAt = Instant.parse("2026-11-17T20:00:00Z"),
            )

        val summary = links.toSummary()

        assertEquals(
            listOf("audio/webm" to 2, "audio/mp4" to 1, "video/mp4" to 2, "video/webm" to 1),
            summary.formatsPerMimeType.toList(),
        )
        assertEquals(3, summary.audioFormats.size)
        assertEquals(3, summary.videoFormats.size)
        assertEquals(1, summary.videoFormatsWithSound)
        assertTrue(summary.hasHlsManifest)
        assertEquals(Instant.parse("2026-11-17T20:00:00Z"), summary.expiresAt)
    }

    @Test
    fun `a stream links summary without a manifest or an expiry says so`() {
        val links =
            StreamLinks(
                videoId = TEST_VIDEO_ID,
                audioFormats = emptyList(),
                videoFormats = emptyList(),
                hlsManifestUrl = null,
                expiresAt = null,
            )

        val summary = links.toSummary()

        assertFalse(summary.hasHlsManifest)
        assertNull(summary.expiresAt)
        assertEquals(emptyMap(), summary.formatsPerMimeType)
    }

    @Test
    fun `format lines keep the format's details and the sound track's kind and language`() {
        val track =
            AudioTrack(
                id = "en.4",
                name = "English",
                languageTag = "en",
                kind = AudioTrackKind.DUBBED,
            )
        val links =
            StreamLinks(
                videoId = TEST_VIDEO_ID,
                audioFormats = listOf(audioFormat(track = track), audioFormat()),
                videoFormats = listOf(videoFormat(includesAudio = true)),
                hlsManifestUrl = null,
                expiresAt = null,
            )

        val summary = links.toSummary()

        assertEquals(
            AudioFormatLine(
                itag = 140,
                mimeType = "audio/mp4",
                codec = "mp4a.40.2",
                bitrate = null,
                contentLength = null,
                trackKind = AudioTrackKind.DUBBED,
                trackLanguageTag = "en",
            ),
            summary.audioFormats.first(),
        )
        assertNull(summary.audioFormats.last().trackKind)
        assertEquals(
            VideoFormatLine(
                itag = 137,
                mimeType = "video/mp4",
                codec = "avc1.640028",
                width = 1920,
                height = 1080,
                frameRate = 30,
                bitrate = null,
                contentLength = null,
                includesAudio = true,
            ),
            summary.videoFormats.single(),
        )
    }

    private fun videoDetails(description: String): VideoDetails =
        VideoDetails(
            id = TEST_VIDEO_ID,
            title = "A video",
            channelId = TEST_CHANNEL_ID,
            channelName = "A channel",
            channelAvatars = thumbnails(3),
            description = description,
            duration = null,
            published = null,
            thumbnails = thumbnails(1),
            isLive = false,
        )
}
