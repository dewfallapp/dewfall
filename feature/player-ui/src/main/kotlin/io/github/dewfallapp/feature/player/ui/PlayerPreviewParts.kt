@file:OptIn(UnstableApi::class)

package io.github.dewfallapp.feature.player.ui

import android.os.Looper
import androidx.annotation.OptIn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.SimpleBasePlayer
import androidx.media3.common.SimpleBasePlayer.PositionSupplier
import androidx.media3.common.util.UnstableApi
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture
import io.github.dewfallapp.core.designsystem.theme.DewfallTheme
import io.github.dewfallapp.core.model.PublishedTime
import io.github.dewfallapp.feature.player.logic.PlayerState
import io.github.dewfallapp.feature.player.logic.VideoPage
import java.time.ZoneId
import kotlin.time.Duration.Companion.hours
import kotlin.time.Instant

// What PlayerPreviews.kt's previews share: a made-up video, never a real one, a fixed clock, so
// they don't change with the date, a made-up player, and the screen in Dewfall's theme.

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
            // A constant position. Given a plain number, SimpleBasePlayer moves a playing position
            // on with the clock, so each render would show another time.
            .setContentPositionMs(
                PositionSupplier.getConstant(
                    when (playbackState) {
                        Player.STATE_ENDED -> PREVIEW_DURATION_US / MICROS_PER_MILLI
                        else -> PREVIEW_POSITION_MS
                    },
                ),
            )
            .build()

    override fun getState(): State = fixedState

    /** ContentFrame hands the player its surface. The made-up player shows nothing on it. */
    override fun handleSetVideoOutput(videoOutput: Any): ListenableFuture<*> =
        Futures.immediateVoidFuture()

    override fun handleClearVideoOutput(videoOutput: Any?): ListenableFuture<*> =
        Futures.immediateVoidFuture()
}

/**
 * The video screen in [state], with [video] in its video area, at the fixed clock. Dewfall's own
 * palette, unless [dynamicColor] asks for the wallpaper's.
 */
@Composable
internal fun PlayerScreenPreview(
    state: PlayerState,
    dynamicColor: Boolean = false,
    video: () -> VideoAreaContent,
) {
    val content = remember { video() }
    DewfallTheme(dynamicColor = dynamicColor) {
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
