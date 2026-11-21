@file:OptIn(UnstableApi::class)

package io.github.dewfallapp.feature.player.ui

import android.content.res.Configuration
import android.os.Looper
import androidx.annotation.OptIn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.tooling.preview.Preview
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.SimpleBasePlayer
import androidx.media3.common.util.UnstableApi
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture
import io.github.dewfallapp.core.designsystem.theme.DewfallTheme
import io.github.dewfallapp.core.model.PlaybackFailure
import io.github.dewfallapp.core.model.PublishedTime
import io.github.dewfallapp.core.model.YouTubeError
import io.github.dewfallapp.feature.player.logic.DetailsArea
import io.github.dewfallapp.feature.player.logic.PlayerState
import io.github.dewfallapp.feature.player.logic.VideoPage
import java.time.ZoneId
import kotlin.time.Duration.Companion.hours
import kotlin.time.Instant

// Previews with a made-up video, never a real one, and a fixed clock, so they don't change with the
// date.

private val previewNow = Instant.parse("2027-03-15T10:30:00Z")
private val previewZone = ZoneId.of("UTC")

internal val previewPage =
    VideoPage(
        title = "How reading rooms were lit before electric lamps",
        channelName = "Larkspur Lectures",
        channelInitial = "L",
        description =
            "A walk through four nineteenth-century reading rooms and the ways their builders " +
                "brought daylight deep inside: high clerestory windows, pale plaster, and desks " +
                "turned to face north so no shadow fell across the page.\n\n" +
                "Recorded on location, with a floor plan for each room.",
        published = PublishedTime(previewNow - 7.hours, isApproximate = false),
        isLive = false,
    )

/** The length of the made-up video, 1:14:02, and where it plays, 18:24, as in the frames. */
private const val PREVIEW_DURATION_US = 4_442_000_000L
internal const val PREVIEW_POSITION_MS = 1_104_000L
private const val MICROS_PER_MILLI = 1000L

/**
 * A made-up player in a fixed state, for the previews. It has one item, whose ID is no real
 * playback ID.
 */
internal class PreviewPlayer(
    playing: Boolean,
    playbackState: Int = Player.STATE_READY,
    live: Boolean = false,
) : SimpleBasePlayer(Looper.getMainLooper()) {
    private val fixedState =
        State.Builder()
            .setAvailableCommands(Player.Commands.Builder().addAllCommands().build())
            .setPlayWhenReady(playing, Player.PLAY_WHEN_READY_CHANGE_REASON_USER_REQUEST)
            .setPlaybackState(playbackState)
            .setPlaylist(
                listOf(
                    MediaItemData.Builder("preview")
                        .setDurationUs(if (live) C.TIME_UNSET else PREVIEW_DURATION_US)
                        .setIsDynamic(live)
                        .setLiveConfiguration(if (live) MediaItem.LiveConfiguration.UNSET else null)
                        .build(),
                ),
            )
            .setContentPositionMs(
                when (playbackState) {
                    Player.STATE_ENDED -> PREVIEW_DURATION_US / MICROS_PER_MILLI
                    else -> PREVIEW_POSITION_MS
                },
            )
            .build()

    override fun getState(): State = fixedState

    /** ContentFrame hands the player its surface. The made-up player shows nothing on it. */
    override fun handleSetVideoOutput(videoOutput: Any): ListenableFuture<*> =
        Futures.immediateVoidFuture()

    override fun handleClearVideoOutput(videoOutput: Any?): ListenableFuture<*> =
        Futures.immediateVoidFuture()
}

@Composable
private fun Preview(state: PlayerState, video: () -> VideoAreaContent) {
    val content = remember { video() }
    DewfallTheme {
        PlayerScreen(
            state = state,
            now = previewNow,
            zone = previewZone,
            onIntent = {},
            onMinimize = {},
            videoArea = { modifier ->
                VideoArea(
                    content = content,
                    onPlay = {},
                    onTryAgain = {},
                    onFullscreen = {},
                    modifier = modifier,
                )
            },
        )
    }
}

private val shownPage = PlayerState(DetailsArea.Shown(previewPage))

@Preview(name = "Light")
@Preview(
    name = "Dark",
    uiMode = Configuration.UI_MODE_NIGHT_YES or Configuration.UI_MODE_TYPE_NORMAL,
)
@Preview(name = "Large font", fontScale = 2f)
@Preview(name = "Tablet", widthDp = 1280, heightDp = 800)
@Composable
private fun PlayingPreview() {
    Preview(shownPage) { VideoAreaContent.Playing(PreviewPlayer(playing = true)) }
}

@Preview(name = "Light")
@Preview(
    name = "Dark",
    uiMode = Configuration.UI_MODE_NIGHT_YES or Configuration.UI_MODE_TYPE_NORMAL,
)
@Composable
private fun PausedPreview() {
    Preview(shownPage) { VideoAreaContent.Playing(PreviewPlayer(playing = false)) }
}

@Preview(name = "Light")
@Composable
private fun EndedPreview() {
    Preview(shownPage) {
        VideoAreaContent.Playing(PreviewPlayer(playing = true, Player.STATE_ENDED))
    }
}

@Preview(name = "Light")
@Preview(name = "Large font", fontScale = 2f)
@Composable
private fun WaitingForPlayPreview() {
    Preview(shownPage) { VideoAreaContent.Waiting(PREVIEW_POSITION_MS) }
}

@Preview(name = "Light")
@Preview(
    name = "Dark",
    uiMode = Configuration.UI_MODE_NIGHT_YES or Configuration.UI_MODE_TYPE_NORMAL,
)
@Preview(name = "Large font", fontScale = 2f)
@Preview(name = "Tablet", widthDp = 1280, heightDp = 800)
@Composable
private fun NotDecodablePreview() {
    Preview(shownPage) { VideoAreaContent.Failed(PlaybackFailure.NOT_DECODABLE) }
}

@Preview(name = "Light")
@Composable
private fun StoppedPlayingPreview() {
    Preview(shownPage) { VideoAreaContent.Failed(PlaybackFailure.OTHER) }
}

@Preview(name = "Light")
@Preview(
    name = "Dark",
    uiMode = Configuration.UI_MODE_NIGHT_YES or Configuration.UI_MODE_TYPE_NORMAL,
)
@Composable
private fun LiveWithNoDescriptionPreview() {
    Preview(
        state =
            PlayerState(
                DetailsArea.Shown(
                    previewPage.copy(
                        title = "Baking along, live from the bakery",
                        channelName = "Kettle and Crumb",
                        channelInitial = "K",
                        description = "",
                        isLive = true,
                    ),
                ),
            ),
        video = { VideoAreaContent.Playing(PreviewPlayer(playing = true, live = true)) },
    )
}

@Preview(name = "Light")
@Composable
private fun LoadingPreview() {
    Preview(PlayerState(DetailsArea.Loading(1))) {
        VideoAreaContent.Playing(PreviewPlayer(playing = true, Player.STATE_BUFFERING))
    }
}

@Preview(name = "Light")
@Preview(
    name = "Dark",
    uiMode = Configuration.UI_MODE_NIGHT_YES or Configuration.UI_MODE_TYPE_NORMAL,
)
@Preview(name = "Large font", fontScale = 2f)
@Composable
private fun NetworkFailurePreview() {
    // Playback failed the same way, so the video area shows no second message.
    Preview(PlayerState(DetailsArea.Failed(1, YouTubeError.NETWORK))) {
        VideoAreaContent.Playing(null)
    }
}

@Preview(name = "Light")
@Composable
private fun UnavailablePreview() {
    Preview(PlayerState(DetailsArea.Failed(1, YouTubeError.UNAVAILABLE))) {
        VideoAreaContent.Playing(null)
    }
}
