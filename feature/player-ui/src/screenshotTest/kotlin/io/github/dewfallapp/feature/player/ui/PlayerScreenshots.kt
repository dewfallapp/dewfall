package io.github.dewfallapp.feature.player.ui

import android.content.res.Configuration
import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.tooling.preview.Wallpapers
import com.android.tools.screenshot.PreviewTest

@PreviewTest
@Preview(name = "Light")
@Preview(
    name = "Dark",
    uiMode = Configuration.UI_MODE_NIGHT_YES or Configuration.UI_MODE_TYPE_NORMAL,
)
@Preview(name = "LargeFont", fontScale = 2f)
@Preview(name = "Tablet", widthDp = 1280, heightDp = 800)
@Composable
private fun PlayingScreenshot() = PlayingPreview()

@PreviewTest
@Preview(name = "DynamicLight", wallpaper = Wallpapers.BLUE_DOMINATED_EXAMPLE)
@Preview(
    name = "DynamicDark",
    uiMode = Configuration.UI_MODE_NIGHT_YES or Configuration.UI_MODE_TYPE_NORMAL,
    wallpaper = Wallpapers.BLUE_DOMINATED_EXAMPLE,
)
@Composable
private fun PlayingDynamicScreenshot() = PlayingDynamicPreview()

@PreviewTest
@Preview(name = "Light")
@Preview(
    name = "Dark",
    uiMode = Configuration.UI_MODE_NIGHT_YES or Configuration.UI_MODE_TYPE_NORMAL,
)
@Composable
private fun PausedScreenshot() = PausedPreview()

@PreviewTest @Preview(name = "Light") @Composable private fun EndedScreenshot() = EndedPreview()

@PreviewTest
@Preview(name = "Light")
@Preview(name = "LargeFont", fontScale = 2f)
@Composable
private fun WaitingForPlayScreenshot() = WaitingForPlayPreview()

@PreviewTest
@Preview(name = "Light")
@Preview(
    name = "Dark",
    uiMode = Configuration.UI_MODE_NIGHT_YES or Configuration.UI_MODE_TYPE_NORMAL,
)
@Preview(name = "LargeFont", fontScale = 2f)
@Preview(name = "Tablet", widthDp = 1280, heightDp = 800)
@Composable
private fun NotDecodableScreenshot() = NotDecodablePreview()

@PreviewTest
@Preview(name = "Light")
@Composable
private fun StoppedPlayingScreenshot() = StoppedPlayingPreview()

@PreviewTest
@Preview(name = "Light")
@Preview(
    name = "Dark",
    uiMode = Configuration.UI_MODE_NIGHT_YES or Configuration.UI_MODE_TYPE_NORMAL,
)
@Composable
private fun LiveWithNoDescriptionScreenshot() = LiveWithNoDescriptionPreview()

@PreviewTest @Preview(name = "Light") @Composable private fun LoadingScreenshot() = LoadingPreview()

@PreviewTest
@Preview(name = "Light")
@Preview(
    name = "Dark",
    uiMode = Configuration.UI_MODE_NIGHT_YES or Configuration.UI_MODE_TYPE_NORMAL,
)
@Preview(name = "LargeFont", fontScale = 2f)
@Composable
private fun NetworkFailureScreenshot() = NetworkFailurePreview()

@PreviewTest
@Preview(name = "Light")
@Composable
private fun UnavailableScreenshot() = UnavailablePreview()
