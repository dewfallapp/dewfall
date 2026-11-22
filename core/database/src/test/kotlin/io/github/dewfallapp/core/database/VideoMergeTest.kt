package io.github.dewfallapp.core.database

import io.github.dewfallapp.core.model.Thumbnail
import io.github.dewfallapp.core.model.VideoKind
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.days
import kotlin.time.Instant

private val FIRST_SAVE = Instant.parse("2026-11-01T10:00:00Z")
private val LATER_SAVE = Instant.parse("2026-11-15T10:00:00Z")
private val EXACT_TIME = Instant.parse("2026-10-29T17:42:00Z")
private val THUMBNAILS = listOf(Thumbnail("https://i.ytimg.com/vi/aaaaaaaaaaa/hq.jpg", 480, 360))
private val NEW_THUMBNAILS =
    listOf(Thumbnail("https://i.ytimg.com/vi/aaaaaaaaaaa/max.jpg", 1280, 720))

private fun video(
    title: String = "Title",
    channelId: String = "UCaaaaaaaaaaaaaaaaaaaaaa",
    channelName: String = "Channel",
    durationMs: Long? = 60_000,
    publishedAt: Instant? = EXACT_TIME,
    approximate: Boolean = false,
    kind: VideoKind = VideoKind.VIDEO,
    thumbnails: List<Thumbnail> = THUMBNAILS,
    seenAt: Instant = FIRST_SAVE,
    listPosition: Int = 0,
) =
    VideoEntity(
        id = "aaaaaaaaaaa",
        channelId = channelId,
        channelName = channelName,
        title = title,
        durationMs = durationMs,
        publishedAt = publishedAt,
        publishedApproximate = approximate,
        kind = kind,
        thumbnails = thumbnails,
        firstSeenAt = seenAt,
        listingNumber = 1,
        listPosition = listPosition,
    )

class VideoMergeTest {
    @Test
    fun `a video saved for the first time is kept as it came`() {
        val new = video()

        assertEquals(new, mergeVideo(saved = null, new = new))
    }

    @Test
    fun `YouTube's details come from the new save`() {
        val saved = video()
        val new =
            video(
                    title = "New title",
                    channelId = "UCbbbbbbbbbbbbbbbbbbbbbb",
                    channelName = "New name",
                    kind = VideoKind.LIVE,
                    seenAt = LATER_SAVE,
                    listPosition = 3,
                )
                .copy(listingNumber = 2)

        val merged = mergeVideo(saved, new)

        assertEquals("New title", merged.title)
        assertEquals("UCbbbbbbbbbbbbbbbbbbbbbb", merged.channelId)
        assertEquals("New name", merged.channelName)
        assertEquals(VideoKind.LIVE, merged.kind)
        assertEquals(2L, merged.listingNumber)
        assertEquals(3, merged.listPosition)
    }

    @Test
    fun `the time Dewfall first saved the video stays`() {
        val merged = mergeVideo(video(seenAt = FIRST_SAVE), video(seenAt = LATER_SAVE))

        assertEquals(FIRST_SAVE, merged.firstSeenAt)
    }

    @Test
    fun `a new length replaces the saved one, and no length keeps it`() {
        assertEquals(
            90_000,
            mergeVideo(video(durationMs = 60_000), video(durationMs = 90_000)).durationMs,
        )
        assertEquals(
            60_000,
            mergeVideo(video(durationMs = 60_000), video(durationMs = null)).durationMs,
        )
    }

    @Test
    fun `a premiere that started takes its length`() {
        val saved = video(kind = VideoKind.UPCOMING, durationMs = null)
        val merged = mergeVideo(saved, video(kind = VideoKind.VIDEO, durationMs = 120_000))

        assertEquals(VideoKind.VIDEO, merged.kind)
        assertEquals(120_000, merged.durationMs)
    }

    @Test
    fun `new thumbnails replace the saved ones, and none keeps them`() {
        assertEquals(
            NEW_THUMBNAILS,
            mergeVideo(video(), video(thumbnails = NEW_THUMBNAILS)).thumbnails,
        )
        assertEquals(THUMBNAILS, mergeVideo(video(), video(thumbnails = emptyList())).thumbnails)
    }

    @Test
    fun `an approximate time never replaces an exact one`() {
        val merged =
            mergeVideo(
                video(publishedAt = EXACT_TIME, approximate = false),
                video(publishedAt = EXACT_TIME - 2.days, approximate = true),
            )

        assertEquals(EXACT_TIME, merged.publishedAt)
        assertFalse(merged.publishedApproximate)
    }

    @Test
    fun `a later approximate time never replaces an earlier approximate one`() {
        val threeDaysAgo = FIRST_SAVE - 3.days
        val twoWeeksAgo = LATER_SAVE - 14.days
        val merged =
            mergeVideo(
                video(publishedAt = threeDaysAgo, approximate = true),
                video(publishedAt = twoWeeksAgo, approximate = true),
            )

        assertEquals(threeDaysAgo, merged.publishedAt)
        assertTrue(merged.publishedApproximate)
    }

    @Test
    fun `an exact time replaces an approximate one`() {
        val merged =
            mergeVideo(
                video(publishedAt = FIRST_SAVE - 3.days, approximate = true),
                video(publishedAt = EXACT_TIME, approximate = false),
            )

        assertEquals(EXACT_TIME, merged.publishedAt)
        assertFalse(merged.publishedApproximate)
    }

    @Test
    fun `an exact time replaces another exact one`() {
        val later = EXACT_TIME + 1.days
        val merged = mergeVideo(video(publishedAt = EXACT_TIME), video(publishedAt = later))

        assertEquals(later, merged.publishedAt)
    }

    @Test
    fun `an approximate time fills a missing one`() {
        val estimate = FIRST_SAVE - 3.days
        val merged =
            mergeVideo(
                video(publishedAt = null, approximate = false),
                video(publishedAt = estimate, approximate = true),
            )

        assertEquals(estimate, merged.publishedAt)
        assertTrue(merged.publishedApproximate)
    }

    @Test
    fun `no time in a later save keeps the saved one`() {
        val estimate = FIRST_SAVE - 3.days
        val merged =
            mergeVideo(
                video(publishedAt = estimate, approximate = true),
                video(publishedAt = null, approximate = false),
            )

        assertEquals(estimate, merged.publishedAt)
        assertTrue(merged.publishedApproximate)
    }

    @Test
    fun `an upcoming video that started takes its new approximate time`() {
        val scheduled = FIRST_SAVE + 1.days
        val estimate = LATER_SAVE - 7.days
        val merged =
            mergeVideo(
                video(kind = VideoKind.UPCOMING, publishedAt = scheduled, approximate = false),
                video(kind = VideoKind.VIDEO, publishedAt = estimate, approximate = true),
            )

        assertEquals(estimate, merged.publishedAt)
        assertTrue(merged.publishedApproximate)
    }

    @Test
    fun `a video that is still upcoming keeps an exact start over an approximate one`() {
        val scheduled = FIRST_SAVE + 1.days
        val merged =
            mergeVideo(
                video(kind = VideoKind.UPCOMING, publishedAt = scheduled, approximate = false),
                video(
                    kind = VideoKind.UPCOMING,
                    publishedAt = scheduled + 1.days,
                    approximate = true,
                ),
            )

        assertEquals(scheduled, merged.publishedAt)
    }
}

class ChannelMergeTest {
    private val saved =
        ChannelEntity(
            id = "UCaaaaaaaaaaaaaaaaaaaaaa",
            name = "Old name",
            description = "Old description",
            avatars = THUMBNAILS,
            subscribedAt = FIRST_SAVE,
        )

    @Test
    fun `the name, avatars and description come from the uploads`() {
        val merged =
            mergeChannelDetails(
                saved,
                ChannelDetails(saved.id, "New name", "New description", NEW_THUMBNAILS),
            )

        assertEquals(
            saved.copy(
                name = "New name",
                description = "New description",
                avatars = NEW_THUMBNAILS,
            ),
            merged,
        )
    }

    @Test
    fun `empty avatars and description keep the saved ones`() {
        val merged =
            mergeChannelDetails(saved, ChannelDetails(saved.id, "New name", "", emptyList()))

        assertEquals(saved.copy(name = "New name"), merged)
    }

    @Test
    fun `a subscription from a file gets the avatars and description`() {
        val fromFile = saved.copy(description = "", avatars = emptyList())

        val merged =
            mergeChannelDetails(
                fromFile,
                ChannelDetails(saved.id, saved.name, "Description", NEW_THUMBNAILS),
            )

        assertEquals("Description", merged.description)
        assertEquals(NEW_THUMBNAILS, merged.avatars)
        assertEquals(FIRST_SAVE, merged.subscribedAt)
    }
}
