package io.github.dewfallapp.feature.player.ui

import android.content.res.Configuration
import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.tooling.preview.Wallpapers
import com.android.tools.screenshot.PreviewTest

@PreviewTest
@Preview(name = "Light", heightDp = 400)
@Preview(
    name = "Dark",
    heightDp = 400,
    uiMode = Configuration.UI_MODE_NIGHT_YES or Configuration.UI_MODE_TYPE_NORMAL,
)
@Preview(name = "LargeFont", heightDp = 400, fontScale = 2f)
@Preview(name = "Tablet", widthDp = 1280, heightDp = 800)
@Composable
private fun MiniPlayerPlayingScreenshot() = MiniPlayerPlayingPreview()

@PreviewTest
@Preview(name = "DynamicLight", heightDp = 400, wallpaper = Wallpapers.BLUE_DOMINATED_EXAMPLE)
@Preview(
    name = "DynamicDark",
    heightDp = 400,
    uiMode = Configuration.UI_MODE_NIGHT_YES or Configuration.UI_MODE_TYPE_NORMAL,
    wallpaper = Wallpapers.BLUE_DOMINATED_EXAMPLE,
)
@Composable
private fun MiniPlayerPlayingDynamicScreenshot() = MiniPlayerPlayingDynamicPreview()

@PreviewTest
@Preview(name = "Light", heightDp = 400)
@Preview(
    name = "Dark",
    heightDp = 400,
    uiMode = Configuration.UI_MODE_NIGHT_YES or Configuration.UI_MODE_TYPE_NORMAL,
)
@Composable
private fun MiniPlayerPausedScreenshot() = MiniPlayerPausedPreview()

@PreviewTest
@Preview(name = "Light", heightDp = 400)
@Preview(
    name = "Dark",
    heightDp = 400,
    uiMode = Configuration.UI_MODE_NIGHT_YES or Configuration.UI_MODE_TYPE_NORMAL,
)
@Composable
private fun MiniPlayerNowPlayingScreenshot() = MiniPlayerNowPlayingPreview()

@PreviewTest
@Preview(name = "Light", widthDp = 915, heightDp = 412)
@Preview(
    name = "Dark",
    widthDp = 915,
    heightDp = 412,
    uiMode = Configuration.UI_MODE_NIGHT_YES or Configuration.UI_MODE_TYPE_NORMAL,
)
@Preview(name = "LargeFont", widthDp = 915, heightDp = 412, fontScale = 2f)
@Preview(name = "TabletPortrait", widthDp = 800, heightDp = 1280)
@Composable
private fun FullscreenPausedScreenshot() = FullscreenPausedPreview()

@PreviewTest
@Preview(
    name = "DynamicLight",
    widthDp = 915,
    heightDp = 412,
    wallpaper = Wallpapers.BLUE_DOMINATED_EXAMPLE,
)
@Preview(
    name = "DynamicDark",
    widthDp = 915,
    heightDp = 412,
    uiMode = Configuration.UI_MODE_NIGHT_YES or Configuration.UI_MODE_TYPE_NORMAL,
    wallpaper = Wallpapers.BLUE_DOMINATED_EXAMPLE,
)
@Composable
private fun FullscreenPausedDynamicScreenshot() = FullscreenPausedDynamicPreview()

@PreviewTest
@Preview(name = "Light", widthDp = 915, heightDp = 412)
@Composable
private fun FullscreenPlayingScreenshot() = FullscreenPlayingPreview()

@PreviewTest
@Preview(name = "Light", widthDp = 915, heightDp = 412)
@Composable
private fun FullscreenLiveScreenshot() = FullscreenLivePreview()

@PreviewTest
@Preview(name = "Light", widthDp = 915, heightDp = 412)
@Preview(name = "LargeFont", widthDp = 915, heightDp = 412, fontScale = 2f)
@Composable
private fun FullscreenFailureScreenshot() = FullscreenFailurePreview()

@PreviewTest
@Preview(name = "Light", widthDp = 915, heightDp = 412)
@Composable
private fun FullscreenWaitingScreenshot() = FullscreenWaitingPreview()
