package io.github.dewfallapp.feature.player.ui

import androidx.compose.runtime.Composable
import io.github.dewfallapp.core.model.PublishedTime
import io.github.dewfallapp.core.model.YouTubeError
import io.github.dewfallapp.feature.player.logic.DetailsArea
import io.github.dewfallapp.feature.player.logic.DownloadChip
import io.github.dewfallapp.feature.player.logic.PlayerState
import io.github.dewfallapp.feature.player.logic.SavedWatch
import kotlin.time.Duration.Companion.days

// Previews of the video screen offline, with the details saved with the video's download. Their
// @PreviewTest wrappers are in src/screenshotTest. See docs/testing.md#screenshot-tests.

/**
 * A downloaded video offline: the saved page, with no channel picture and no description, and the
 * failure in the description's place.
 */
private val savedPage =
    PlayerState(
        DetailsArea.Saved(
            number = 1,
            error = YouTubeError.NETWORK,
            page =
                previewPage.copy(
                    channelAvatars = emptyList(),
                    description = "",
                    published = PublishedTime(previewNow - 21.days, isApproximate = true),
                ),
        ),
        saved = SavedWatch.Known(watched = false),
        download = DownloadChip.Downloaded,
    )

@Composable
internal fun SavedDetailsPreview() {
    PlayerScreenPreview(savedPage) { VideoAreaContent.Playing(PreviewPlayer(playing = true)) }
}

@Composable
internal fun SavedDetailsDynamicPreview() {
    PlayerScreenPreview(savedPage, dynamicColor = true) {
        VideoAreaContent.Playing(PreviewPlayer(playing = true))
    }
}
