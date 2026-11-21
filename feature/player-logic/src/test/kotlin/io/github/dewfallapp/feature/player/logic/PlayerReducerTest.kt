package io.github.dewfallapp.feature.player.logic

import io.github.dewfallapp.core.model.ChannelId
import io.github.dewfallapp.core.model.PublishedTime
import io.github.dewfallapp.core.model.VideoDetails
import io.github.dewfallapp.core.model.VideoId
import io.github.dewfallapp.core.model.YouTubeError
import io.github.dewfallapp.core.model.YouTubeResult
import io.github.dewfallapp.core.mvi.Next
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Instant

private val PUBLISHED = PublishedTime(Instant.parse("2026-11-19T08:00:00Z"), isApproximate = false)

private fun details(isLive: Boolean = false) =
    VideoDetails(
        id = VideoId("aqz-KE-bpKQ"),
        title = "How reading rooms were lit before electric lamps",
        channelId = ChannelId("UCLA_DiR1FfKNvjuUpBHmylQ"),
        channelName = "Larkspur Lectures",
        channelAvatars = emptyList(),
        description = "A walk through four reading rooms.\nRecorded on location.",
        duration = 74.minutes,
        published = PUBLISHED,
        thumbnails = emptyList(),
        isLive = isLive,
    )

private val PAGE =
    VideoPage(
        title = "How reading rooms were lit before electric lamps",
        channelName = "Larkspur Lectures",
        channelInitial = "L",
        description = "A walk through four reading rooms.\nRecorded on location.",
        published = PUBLISHED,
        isLive = false,
    )

class PlayerReducerTest {
    private fun reduce(state: PlayerState, intent: PlayerIntent): PlayerState {
        val next = PlayerReducer.reduce(state, intent)
        assertEquals(Next(next.state), next)
        return next.state
    }

    private fun PlayerState.after(vararg intents: PlayerIntent): PlayerState =
        intents.fold(this) { state, intent -> reduce(state, intent) }

    private fun finished(number: Long, result: YouTubeResult<VideoDetails>) =
        PlayerIntent.DetailsFinished(number, result)

    private fun failedWith(error: YouTubeError) =
        PlayerState().after(PlayerIntent.Load, finished(1, YouTubeResult.Failure(error)))

    @Test
    fun `the screen starts loading, before any request`() {
        assertEquals(DetailsArea.Loading(number = 0), PlayerState().details)
    }

    @Test
    fun `Load starts request 1, once`() {
        assertEquals(DetailsArea.Loading(1), PlayerState().after(PlayerIntent.Load).details)
        assertEquals(
            DetailsArea.Loading(1),
            PlayerState().after(PlayerIntent.Load, PlayerIntent.Load).details,
        )
    }

    @Test
    fun `the answer to the running request shows the page`() {
        val state =
            PlayerState().after(PlayerIntent.Load, finished(1, YouTubeResult.Success(details())))

        assertEquals(DetailsArea.Shown(PAGE), state.details)
    }

    @Test
    fun `a live stream's page says it is live`() {
        val state =
            PlayerState()
                .after(
                    PlayerIntent.Load,
                    finished(1, YouTubeResult.Success(details(isLive = true))),
                )

        assertEquals(DetailsArea.Shown(PAGE.copy(isLive = true)), state.details)
    }

    @Test
    fun `a failure keeps its error`() {
        assertEquals(
            DetailsArea.Failed(1, YouTubeError.PRIVATE),
            failedWith(YouTubeError.PRIVATE).details,
        )
    }

    @Test
    fun `an answer before Load, or for another request, changes nothing`() {
        val success = YouTubeResult.Success(details())

        assertEquals(PlayerState(), PlayerState().after(finished(0, success)))
        assertEquals(PlayerState(), PlayerState().after(finished(1, success)))
        assertEquals(
            DetailsArea.Loading(1),
            PlayerState().after(PlayerIntent.Load, finished(2, success)).details,
        )
    }

    @Test
    fun `Try again after a failure that offers it starts the next request`() {
        for (error in listOf(YouTubeError.NETWORK, YouTubeError.BLOCKED, YouTubeError.BROKEN)) {
            assertEquals(
                DetailsArea.Loading(2),
                failedWith(error).after(PlayerIntent.TryAgain).details,
                error.name,
            )
        }
    }

    @Test
    fun `Try again after a failure that doesn't offer it changes nothing`() {
        val failed = failedWith(YouTubeError.PRIVATE)

        assertEquals(failed, failed.after(PlayerIntent.TryAgain))
    }

    @Test
    fun `Try again while loading or showing the page changes nothing`() {
        val loading = PlayerState().after(PlayerIntent.Load)
        val shown = loading.after(finished(1, YouTubeResult.Success(details())))

        assertEquals(loading, loading.after(PlayerIntent.TryAgain))
        assertEquals(shown, shown.after(PlayerIntent.TryAgain))
    }

    @Test
    fun `the failed request's late answer is dropped after Try again`() {
        val retrying = failedWith(YouTubeError.NETWORK).after(PlayerIntent.TryAgain)

        val state = retrying.after(finished(1, YouTubeResult.Success(details())))

        assertEquals(DetailsArea.Loading(2), state.details)
        assertEquals(
            DetailsArea.Shown(PAGE),
            state.after(finished(2, YouTubeResult.Success(details()))).details,
        )
    }

    @Test
    fun `a second failure keeps the new request's number`() {
        val state =
            failedWith(YouTubeError.NETWORK)
                .after(
                    PlayerIntent.TryAgain,
                    finished(2, YouTubeResult.Failure(YouTubeError.BLOCKED)),
                )

        assertEquals(DetailsArea.Failed(2, YouTubeError.BLOCKED), state.details)
    }

    @Test
    fun `the screen starts out of fullscreen`() {
        assertEquals(false, PlayerState().fullscreen)
    }

    @Test
    fun `the fullscreen intents turn fullscreen on and off`() {
        val entered = PlayerReducer.reduce(PlayerState(), PlayerIntent.EnterFullscreen)
        assertEquals(Next(PlayerState(fullscreen = true)), entered)

        val left = PlayerReducer.reduce(entered.state, PlayerIntent.ExitFullscreen)
        assertEquals(Next(PlayerState(fullscreen = false)), left)
    }

    @Test
    fun `the fullscreen intents leave the details alone`() {
        val shown = PlayerState(details = DetailsArea.Shown(PAGE))

        val entered = PlayerReducer.reduce(shown, PlayerIntent.EnterFullscreen).state
        assertEquals(shown.details, entered.details)
        assertEquals(
            shown.details,
            PlayerReducer.reduce(entered, PlayerIntent.ExitFullscreen).state.details,
        )
    }
}
