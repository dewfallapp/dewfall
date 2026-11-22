package io.github.dewfallapp.feature.player.ui

import androidx.compose.runtime.Composable
import io.github.dewfallapp.feature.player.logic.SavedWatch

// Previews of the video screen for a watched video, whose chip says "Watched", with the made-up
// video and player in PlayerPreviewParts.kt. The @PreviewTest wrappers in src/screenshotTest carry
// their @Preview annotations, as PlayerPreviews.kt's comment says. See
// docs/testing.md#screenshot-tests.

private val watchedPage = shownPage.copy(saved = SavedWatch.Known(watched = true))

@Composable
internal fun WatchedPreview() {
    PlayerScreenPreview(watchedPage) { VideoAreaContent.Playing(PreviewPlayer(playing = true)) }
}

@Composable
internal fun WatchedDynamicPreview() {
    PlayerScreenPreview(watchedPage, dynamicColor = true) {
        VideoAreaContent.Playing(PreviewPlayer(playing = true))
    }
}
