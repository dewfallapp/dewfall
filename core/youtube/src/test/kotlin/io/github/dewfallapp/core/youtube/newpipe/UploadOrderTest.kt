package io.github.dewfallapp.core.youtube.newpipe

import io.github.dewfallapp.core.model.ChannelId
import io.github.dewfallapp.core.model.PublishedTime
import io.github.dewfallapp.core.model.VideoId
import io.github.dewfallapp.core.model.VideoKind
import io.github.dewfallapp.core.model.VideoSummary
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.time.Instant

class UploadOrderTest {
    @Test
    fun `videos come newest first`() {
        val older = video(VIDEO_A, time("2026-10-01T10:00:00Z"))
        val newer = video(VIDEO_B, time("2026-10-02T10:00:00Z"))

        assertEquals(ids(newer, older), ids(latestUploadsOrder(listOf(older, newer))))
    }

    @Test
    fun `exact and approximate times are compared as they are`() {
        // "1 day ago" becomes the start of that day, so a video from later that day comes first.
        val approximate = video(VIDEO_A, time("2026-10-01T00:00:00Z", isApproximate = true))
        val exact = video(VIDEO_B, time("2026-10-01T09:30:00Z"))
        val earlierExact = video(VIDEO_C, time("2026-09-30T23:00:00Z"))

        assertEquals(
            ids(exact, approximate, earlierExact),
            ids(latestUploadsOrder(listOf(approximate, earlierExact, exact))),
        )
    }

    @Test
    fun `an upcoming video with a scheduled start comes first`() {
        val recent = video(VIDEO_A, time("2026-10-01T10:00:00Z"))
        val upcoming = video(VIDEO_B, time("2026-12-01T18:00:00Z"), VideoKind.UPCOMING)

        assertEquals(ids(upcoming, recent), ids(latestUploadsOrder(listOf(recent, upcoming))))
    }

    @Test
    fun `a video with no time, an upcoming one included, comes last`() {
        val noTime = video(VIDEO_A, published = null)
        val upcomingWithoutStart = video(VIDEO_B, published = null, kind = VideoKind.UPCOMING)
        val dated = video(VIDEO_C, time("2026-10-01T10:00:00Z"))

        assertEquals(
            ids(dated, noTime, upcomingWithoutStart),
            ids(latestUploadsOrder(listOf(noTime, upcomingWithoutStart, dated))),
        )
    }

    @Test
    fun `videos with the same time keep YouTube's order, Videos tab first`() {
        val sameTime = time("2026-10-01T00:00:00Z", isApproximate = true)
        val videosTabFirst = video(VIDEO_A, sameTime)
        val videosTabSecond = video(VIDEO_B, sameTime)
        val liveTab = video(VIDEO_C, sameTime)

        assertEquals(
            ids(videosTabFirst, videosTabSecond, liveTab),
            ids(latestUploadsOrder(listOf(videosTabFirst, videosTabSecond, liveTab))),
        )
    }

    @Test
    fun `a video in both tabs is kept once, as the Videos tab's copy`() {
        val inVideosTab = video(VIDEO_A, time("2026-10-01T10:00:00Z"), VideoKind.VIDEO)
        val other = video(VIDEO_B, time("2026-09-01T10:00:00Z"))
        val inLiveTab = video(VIDEO_A, time("2026-10-01T10:00:00Z"), VideoKind.LIVE)

        val order = latestUploadsOrder(listOf(inVideosTab, other, inLiveTab))

        assertEquals(listOf(inVideosTab, other), order)
    }

    private fun video(
        id: String,
        published: PublishedTime?,
        kind: VideoKind = VideoKind.VIDEO,
    ): VideoSummary =
        VideoSummary(
            id = VideoId(id),
            title = "Title",
            channelId = ChannelId(CHANNEL_A),
            channelName = "Channel",
            duration = null,
            published = published,
            thumbnails = emptyList(),
            kind = kind,
        )

    private fun time(instant: String, isApproximate: Boolean = false) =
        PublishedTime(Instant.parse(instant), isApproximate)

    private fun ids(vararg videos: VideoSummary): List<VideoId> = videos.map { it.id }

    private fun ids(videos: List<VideoSummary>): List<VideoId> = videos.map { it.id }
}
