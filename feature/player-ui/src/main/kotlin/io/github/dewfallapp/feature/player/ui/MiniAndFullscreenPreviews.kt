@file:OptIn(UnstableApi::class)

package io.github.dewfallapp.feature.player.ui

import android.content.res.Configuration
import androidx.annotation.OptIn
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.media3.common.util.UnstableApi
import io.github.dewfallapp.core.designsystem.theme.DewfallTheme
import io.github.dewfallapp.core.model.PlaybackFailure

// Previews of the mini player and the fullscreen player, with the made-up video of PlayerPreviews,
// never a real one.

/** How much of the made-up video has played, 18:24 of 1:14:02, as in the frames. */
private const val PREVIEW_PROGRESS = 0.249f

@Composable
private fun MiniPreview(
    title: String = previewPage.title,
    channelName: String? = previewPage.channelName,
    showPlay: Boolean = false,
) {
    DewfallTheme {
        Surface {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.BottomCenter) {
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
}

@Preview(name = "Light", heightDp = 400)
@Preview(
    name = "Dark",
    heightDp = 400,
    uiMode = Configuration.UI_MODE_NIGHT_YES or Configuration.UI_MODE_TYPE_NORMAL,
)
@Preview(name = "Large font", heightDp = 400, fontScale = 2f)
@Preview(name = "Tablet", widthDp = 1280, heightDp = 800)
@Composable
private fun MiniPlayerPlayingPreview() {
    MiniPreview()
}

@Preview(name = "Light", heightDp = 400)
@Preview(
    name = "Dark",
    heightDp = 400,
    uiMode = Configuration.UI_MODE_NIGHT_YES or Configuration.UI_MODE_TYPE_NORMAL,
)
@Composable
private fun MiniPlayerPausedPreview() {
    MiniPreview(showPlay = true)
}

@Preview(name = "Light", heightDp = 400)
@Composable
private fun MiniPlayerNowPlayingPreview() {
    MiniPreview(title = stringResource(R.string.mini_player_now_playing), channelName = null)
}

@Composable
private fun FullscreenPreview(video: () -> VideoAreaContent) {
    val content = remember { video() }
    FullscreenPlayer(
        content = content,
        page = previewPage,
        onPlay = {},
        onTryAgain = {},
        onExit = {},
    )
}

@Preview(name = "Light", widthDp = 915, heightDp = 412)
@Preview(
    name = "Dark",
    widthDp = 915,
    heightDp = 412,
    uiMode = Configuration.UI_MODE_NIGHT_YES or Configuration.UI_MODE_TYPE_NORMAL,
)
@Preview(name = "Large font", widthDp = 915, heightDp = 412, fontScale = 2f)
@Preview(name = "Tablet in portrait", widthDp = 800, heightDp = 1280)
@Composable
private fun FullscreenPausedPreview() {
    FullscreenPreview { VideoAreaContent.Playing(PreviewPlayer(playing = false)) }
}

@Preview(name = "Dark", widthDp = 915, heightDp = 412)
@Composable
private fun FullscreenLivePreview() {
    FullscreenPreview { VideoAreaContent.Playing(PreviewPlayer(playing = false, live = true)) }
}

@Preview(name = "Dark", widthDp = 915, heightDp = 412)
@Preview(name = "Large font", widthDp = 915, heightDp = 412, fontScale = 2f)
@Composable
private fun FullscreenFailurePreview() {
    FullscreenPreview { VideoAreaContent.Failed(PlaybackFailure.NETWORK) }
}

@Preview(name = "Dark", widthDp = 915, heightDp = 412)
@Composable
private fun FullscreenWaitingPreview() {
    FullscreenPreview { VideoAreaContent.Waiting(PREVIEW_POSITION_MS) }
}
