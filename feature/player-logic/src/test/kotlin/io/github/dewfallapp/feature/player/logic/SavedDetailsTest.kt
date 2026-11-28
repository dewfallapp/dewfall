package io.github.dewfallapp.feature.player.logic

import io.github.dewfallapp.core.model.ChannelId
import io.github.dewfallapp.core.model.PlaybackFailure
import io.github.dewfallapp.core.model.PublishedTime
import io.github.dewfallapp.core.model.Thumbnail
import io.github.dewfallapp.core.model.VideoDetails
import io.github.dewfallapp.core.model.VideoId
import io.github.dewfallapp.core.model.VideoKind
import io.github.dewfallapp.core.model.VideoSummary
import io.github.dewfallapp.core.model.YouTubeError
import io.github.dewfallapp.core.model.YouTubeResult
import io.github.dewfallapp.core.mvi.Next
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Instant

private val PUBLISHED = PublishedTime(Instant.parse("2026-11-07T08:00:00Z"), isApproximate = true)

/** The summary saved with the video's download, made up. */
private val SUMMARY =
    VideoSummary(
        id = VideoId("aqz-KE-bpKQ"),
        title = "How reading rooms were lit before electric lamps",
        channelId = ChannelId("UCLA_DiR1FfKNvjuUpBHmylQ"),
        channelName = "Larkspur Lectures",
        duration = 74.minutes,
        published = PUBLISHED,
        thumbnails = listOf(Thumbnail("https://images.example.invalid/hq.jpg", 480, 360)),
        kind = VideoKind.VIDEO,
    )

/** The page made from [SUMMARY]: no pictures, no description, not live. */
private val SAVED_PAGE =
    VideoPage(
        title = "How reading rooms were lit before electric lamps",
        channelId = ChannelId("UCLA_DiR1FfKNvjuUpBHmylQ"),
        channelName = "Larkspur Lectures",
        channelAvatars = emptyList(),
        description = "",
        published = PUBLISHED,
        isLive = false,
    )

private val DETAILS =
    VideoDetails(
        id = VideoId("aqz-KE-bpKQ"),
        title = "How reading rooms were lit before electric lamps, retitled",
        channelId = ChannelId("UCLA_DiR1FfKNvjuUpBHmylQ"),
        channelName = "Larkspur Lectures",
        channelAvatars =
            listOf(Thumbnail("https://images.example.invalid/larkspur-88.jpg", 88, 88)),
        description = "A walk through four reading rooms.",
        duration = 74.minutes,
        published = PUBLISHED,
        thumbnails = emptyList(),
        isLive = false,
    )

class SavedDetailsTest {
    private fun reduce(state: PlayerState, intent: PlayerIntent): PlayerState {
        val next = PlayerReducer.reduce(state, intent)
        assertEquals(Next(next.state), next)
        return next.state
    }

    private fun PlayerState.after(vararg intents: PlayerIntent): PlayerState =
        intents.fold(this) { state, intent -> reduce(state, intent) }

    private fun failure(number: Long, error: YouTubeError) =
        PlayerIntent.DetailsFinished(number, YouTubeResult.Failure(error))

    private fun success(number: Long) =
        PlayerIntent.DetailsFinished(number, YouTubeResult.Success(DETAILS))

    private val saved = PlayerIntent.SavedVideoChanged(SUMMARY)
    private val noSaved = PlayerIntent.SavedVideoChanged(null)

    private val offline: PlayerState =
        PlayerState().after(PlayerIntent.Load, failure(1, YouTubeError.NETWORK), saved)

    @Test
    fun `the database's answer keeps the saved summary`() {
        assertEquals(SUMMARY, PlayerState().after(saved).savedVideo)
        assertNull(PlayerState().after(saved, noSaved).savedVideo)
    }

    @Test
    fun `a failure to reach YouTube, then the summary, shows the saved page`() {
        assertEquals(DetailsArea.Saved(1, YouTubeError.NETWORK, SAVED_PAGE), offline.details)
    }

    @Test
    fun `the summary, then a failure to reach YouTube, shows the saved page`() {
        val state = PlayerState().after(saved, PlayerIntent.Load, failure(1, YouTubeError.NETWORK))

        assertEquals(DetailsArea.Saved(1, YouTubeError.NETWORK, SAVED_PAGE), state.details)
    }

    @Test
    fun `every other failure stays a failure with the summary`() {
        for (error in YouTubeError.entries - YouTubeError.NETWORK) {
            val first = PlayerState().after(PlayerIntent.Load, failure(1, error), saved)
            val last = PlayerState().after(saved, PlayerIntent.Load, failure(1, error))

            assertEquals(DetailsArea.Failed(1, error), first.details, error.name)
            assertEquals(DetailsArea.Failed(1, error), last.details, error.name)
        }
    }

    @Test
    fun `no summary leaves the failure`() {
        val state =
            PlayerState().after(PlayerIntent.Load, failure(1, YouTubeError.NETWORK), noSaved)

        assertEquals(DetailsArea.Failed(1, YouTubeError.NETWORK), state.details)
    }

    @Test
    fun `the summary going away brings back the same failure`() {
        val tried = offline.after(PlayerIntent.TryAgain, failure(2, YouTubeError.NETWORK), noSaved)

        assertEquals(DetailsArea.Failed(1, YouTubeError.NETWORK), offline.after(noSaved).details)
        assertEquals(DetailsArea.Failed(2, YouTubeError.NETWORK), tried.details)
    }

    @Test
    fun `a changed summary shows its new page`() {
        val renamed = SUMMARY.copy(title = "Reading rooms by lamplight", channelName = "Larkspur")

        val state = offline.after(PlayerIntent.SavedVideoChanged(renamed))

        assertEquals(
            DetailsArea.Saved(
                1,
                YouTubeError.NETWORK,
                SAVED_PAGE.copy(title = "Reading rooms by lamplight", channelName = "Larkspur"),
            ),
            state.details,
        )
    }

    @Test
    fun `Try again from the saved page starts the next request`() {
        assertEquals(DetailsArea.Loading(2), offline.after(PlayerIntent.TryAgain).details)
    }

    @Test
    fun `YouTube's answer after Try again replaces the saved page`() {
        val state = offline.after(PlayerIntent.TryAgain, success(2))

        val page = (state.details as DetailsArea.Shown).page
        assertEquals(DETAILS.title, page.title)
        assertEquals(DETAILS.description, page.description)
        assertEquals(DETAILS.channelAvatars, page.channelAvatars)
    }

    @Test
    fun `another failure to reach YouTube after Try again shows the saved page again`() {
        val state = offline.after(PlayerIntent.TryAgain, failure(2, YouTubeError.NETWORK))

        assertEquals(DetailsArea.Saved(2, YouTubeError.NETWORK, SAVED_PAGE), state.details)
    }

    @Test
    fun `a late answer changes nothing while the saved page shows`() {
        assertEquals(offline, offline.after(success(1)))
        assertEquals(offline, offline.after(success(2)))
    }

    @Test
    fun `the summary changes nothing while loading or showing YouTube's page`() {
        val loading = PlayerState().after(PlayerIntent.Load, saved)
        val shown = PlayerState().after(PlayerIntent.Load, success(1))

        assertEquals(DetailsArea.Loading(1), loading.details)
        assertEquals(shown.details, shown.after(saved).details)
        assertEquals(shown.details, shown.after(saved, noSaved).details)
    }

    @Test
    fun `the page of each area`() {
        val full = SAVED_PAGE.copy(description = "Recorded on location.")
        val shown: DetailsArea = DetailsArea.Shown(full)
        val savedArea: DetailsArea = DetailsArea.Saved(1, YouTubeError.NETWORK, SAVED_PAGE)

        assertEquals(full, shown.page)
        assertEquals(SAVED_PAGE, savedArea.page)
        assertNull(DetailsArea.Loading(1).page)
        assertNull(DetailsArea.Failed(1, YouTubeError.NETWORK).page)
    }

    @Test
    fun `the download chip shows with the saved page`() {
        val chip = DownloadChip.Paused(percent = 40)

        assertEquals(chip, offline.copy(download = chip).shownDownload)
    }

    @Test
    fun `a playback failure of the same category shows once with the saved page`() {
        val area = DetailsArea.Saved(1, YouTubeError.NETWORK, SAVED_PAGE)

        assertTrue(sharesFailure(area, PlaybackFailure.NETWORK))
        assertNull(videoAreaFailure(area, PlaybackFailure.NETWORK))
        assertFalse(sharesFailure(area, PlaybackFailure.NOT_DECODABLE))
        assertFalse(sharesFailure(area, null))
    }
}
