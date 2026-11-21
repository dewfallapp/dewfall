@file:OptIn(UnstableApi::class)

package io.github.dewfallapp.feature.player.ui

import androidx.annotation.OptIn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.media3.common.util.UnstableApi
import io.github.dewfallapp.core.model.PlaybackFailure

// Previews of the fullscreen player, with the made-up video and player in PlayerPreviewParts.kt.
// The @PreviewTest wrappers in src/screenshotTest render them. See
// docs/testing.md#screenshot-tests.

/** The fullscreen player showing [video], with the made-up video's title. */
@Composable
private fun FullscreenPreview(dynamicColor: Boolean = false, video: () -> VideoAreaContent) {
    val content = remember { video() }
    FullscreenPlayer(
        content = content,
        page = previewPage,
        onPlay = {},
        onTryAgain = {},
        onExit = {},
        dynamicColor = dynamicColor,
    )
}

@Composable
internal fun FullscreenPlayingPreview() {
    FullscreenPreview { VideoAreaContent.Playing(PreviewPlayer(playing = true)) }
}

@Composable
internal fun FullscreenPausedPreview() {
    FullscreenPreview { VideoAreaContent.Playing(PreviewPlayer(playing = false)) }
}

@Composable
internal fun FullscreenPausedDynamicPreview() {
    FullscreenPreview(dynamicColor = true) {
        VideoAreaContent.Playing(PreviewPlayer(playing = false))
    }
}

@Composable
internal fun FullscreenLivePreview() {
    FullscreenPreview { VideoAreaContent.Playing(PreviewPlayer(playing = false, live = true)) }
}

@Composable
internal fun FullscreenFailurePreview() {
    FullscreenPreview { VideoAreaContent.Failed(PlaybackFailure.NETWORK) }
}

@Composable
internal fun FullscreenWaitingPreview() {
    FullscreenPreview { VideoAreaContent.Waiting(PREVIEW_POSITION_MS) }
}
