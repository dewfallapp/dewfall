@file:OptIn(UnstableApi::class)

package io.github.dewfallapp.feature.player.ui

import androidx.annotation.OptIn
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.media3.common.util.UnstableApi
import io.github.dewfallapp.core.designsystem.theme.DewfallTheme

// Previews of the mini player, with the made-up video and player in
// PlayerPreviewParts.kt. The @PreviewTest wrappers in src/screenshotTest carry their @Preview
// annotations and render them as screenshot tests. They use Dewfall's own palette, except the
// dynamic color previews, whose wrappers name a fixed wallpaper. See
// docs/testing.md#screenshot-tests.

/** How much of the made-up video has played, 18:24 of 1:14:02, as in the frames. */
private const val PREVIEW_PROGRESS = 0.249f

/**
 * The mini player's bar at the bottom of an empty screen, with no Surface, as in :app, so the bar's
 * own content color shows.
 */
@Composable
private fun MiniPreview(
    title: String = previewPage.title,
    channelName: String? = previewPage.channelName,
    showPlay: Boolean = false,
    dynamicColor: Boolean = false,
) {
    DewfallTheme(dynamicColor = dynamicColor) {
        Box(
            Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surface),
            contentAlignment = Alignment.BottomCenter,
        ) {
            MiniPlayerBar(
                title = title,
                channelName = channelName,
                progress = { PREVIEW_PROGRESS },
                showPlay = showPlay,
                ended = false,
                playEnabled = true,
                onPlayPause = {},
                onOpen = {},
                onClose = {},
            )
        }
    }
}

@Composable
internal fun MiniPlayerPlayingPreview() {
    MiniPreview()
}

@Composable
internal fun MiniPlayerPlayingDynamicPreview() {
    MiniPreview(dynamicColor = true)
}

@Composable
internal fun MiniPlayerPausedPreview() {
    MiniPreview(showPlay = true)
}

@Composable
internal fun MiniPlayerNowPlayingPreview() {
    MiniPreview(title = stringResource(R.string.mini_player_now_playing), channelName = null)
}
