package io.github.dewfallapp.feature.player.ui

import androidx.compose.runtime.Composable
import androidx.media3.common.Player
import io.github.dewfallapp.core.model.PlaybackFailure
import io.github.dewfallapp.core.model.YouTubeError
import io.github.dewfallapp.feature.player.logic.DetailsArea
import io.github.dewfallapp.feature.player.logic.PlayerState
import io.github.dewfallapp.feature.player.logic.SavedWatch

// Previews of the video screen, with the made-up video and player in PlayerPreviewParts.kt. The
// @PreviewTest wrappers in src/screenshotTest carry their @Preview annotations and render them as
// screenshot tests. They use Dewfall's own palette, so the images don't depend on a wallpaper,
// except the dynamic color preview, whose wrappers name a fixed one. See
// docs/testing.md#screenshot-tests.

/** The video screen with its details, for a video that isn't watched. */
internal val shownPage =
    PlayerState(
        DetailsArea.Shown(previewPage),
        saved = SavedWatch.Known(watched = false),
    )

@Composable
internal fun PlayingPreview() {
    PlayerScreenPreview(shownPage) { VideoAreaContent.Playing(PreviewPlayer(playing = true)) }
}

@Composable
internal fun PlayingDynamicPreview() {
    PlayerScreenPreview(shownPage, dynamicColor = true) {
        VideoAreaContent.Playing(PreviewPlayer(playing = true))
    }
}

@Composable
internal fun PausedPreview() {
    PlayerScreenPreview(shownPage) { VideoAreaContent.Playing(PreviewPlayer(playing = false)) }
}

@Composable
internal fun EndedPreview() {
    PlayerScreenPreview(shownPage) {
        VideoAreaContent.Playing(PreviewPlayer(playing = true, Player.STATE_ENDED))
    }
}

@Composable
internal fun WaitingForPlayPreview() {
    PlayerScreenPreview(shownPage) { VideoAreaContent.Waiting(PREVIEW_POSITION_MS) }
}

@Composable
internal fun NotDecodablePreview() {
    PlayerScreenPreview(shownPage) { VideoAreaContent.Failed(PlaybackFailure.NOT_DECODABLE) }
}

@Composable
internal fun StoppedPlayingPreview() {
    PlayerScreenPreview(shownPage) { VideoAreaContent.Failed(PlaybackFailure.OTHER) }
}

@Composable
internal fun LiveWithNoDescriptionPreview() {
    PlayerScreenPreview(
        state =
            PlayerState(
                DetailsArea.Shown(
                    previewPage.copy(
                        title = "Baking along, live from the bakery",
                        channelName = "Kettle and Crumb",
                        // No picture, so the channel row keeps the emblem in the screenshots.
                        channelAvatars = emptyList(),
                        description = "",
                        isLive = true,
                    ),
                ),
            ),
        video = { VideoAreaContent.Playing(PreviewPlayer(playing = true, live = true)) },
    )
}

@Composable
internal fun LoadingPreview() {
    PlayerScreenPreview(PlayerState(DetailsArea.Loading(1))) {
        VideoAreaContent.Playing(PreviewPlayer(playing = true, Player.STATE_BUFFERING))
    }
}

@Composable
internal fun NetworkFailurePreview() {
    // Playback failed the same way, so the video area shows no second message.
    PlayerScreenPreview(PlayerState(DetailsArea.Failed(1, YouTubeError.NETWORK))) {
        VideoAreaContent.Playing(null)
    }
}

@Composable
internal fun UnavailablePreview() {
    PlayerScreenPreview(PlayerState(DetailsArea.Failed(1, YouTubeError.UNAVAILABLE))) {
        VideoAreaContent.Playing(null)
    }
}
