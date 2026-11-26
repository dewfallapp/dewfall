package io.github.dewfallapp.feature.player.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import io.github.dewfallapp.core.designsystem.theme.DewfallSpacing
import io.github.dewfallapp.core.designsystem.theme.DewfallTheme
import io.github.dewfallapp.feature.player.logic.DownloadChip

// Previews of the video screen's download chip, with the made-up video and player in
// PlayerPreviewParts.kt: the screen while the video downloads, and every state of the chip. The
// @PreviewTest wrappers in src/screenshotTest carry their @Preview annotations, as
// PlayerPreviews.kt's comment says. See docs/testing.md#screenshot-tests.

/** Every state of the download chip, in the order the downloads spec lists them. */
private val everyChip =
    listOf(
        DownloadChip.NotDownloaded,
        DownloadChip.Waiting,
        DownloadChip.Downloading(percent = 42),
        DownloadChip.Downloading(percent = null),
        DownloadChip.Paused(percent = 42),
        DownloadChip.Failed(noSpace = false),
        DownloadChip.Failed(noSpace = true),
        DownloadChip.Downloaded,
    )

private val downloadingPage = shownPage.copy(download = DownloadChip.Downloading(percent = 42))

@Composable
internal fun DownloadingPreview() {
    PlayerScreenPreview(downloadingPage) { VideoAreaContent.Playing(PreviewPlayer(playing = true)) }
}

@Composable
internal fun DownloadingDynamicPreview() {
    PlayerScreenPreview(downloadingPage, dynamicColor = true) {
        VideoAreaContent.Playing(PreviewPlayer(playing = true))
    }
}

/**
 * The screen once the video is downloaded, whose chip isn't a button, beside the watched chip,
 * which is, so the row shows that the two line up.
 */
@Composable
internal fun DownloadedPreview() {
    PlayerScreenPreview(shownPage.copy(download = DownloadChip.Downloaded)) {
        VideoAreaContent.Playing(PreviewPlayer(playing = false))
    }
}

@Composable
internal fun DownloadChipsPreview(dynamicColor: Boolean = false) {
    DewfallTheme(dynamicColor = dynamicColor) {
        Surface {
            Column(
                modifier = Modifier.padding(DewfallSpacing.md),
                verticalArrangement = Arrangement.spacedBy(DewfallSpacing.sm),
            ) {
                for (chip in everyChip) {
                    DownloadChipView(chip = chip, onDownload = {}, onPause = {})
                }
            }
        }
    }
}

@Composable
internal fun DownloadChipsDynamicPreview() {
    DownloadChipsPreview(dynamicColor = true)
}
