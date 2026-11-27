package io.github.dewfallapp.feature.downloads.ui

import android.content.res.Configuration
import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.tooling.preview.Wallpapers
import com.android.tools.screenshot.PreviewTest

@PreviewTest
@Preview(name = "Light", heightDp = 1600)
@Preview(
    name = "Dark",
    uiMode = Configuration.UI_MODE_NIGHT_YES or Configuration.UI_MODE_TYPE_NORMAL,
    heightDp = 1600,
)
@Preview(name = "LargeFont", fontScale = 2f, heightDp = 2400)
@Preview(name = "Tablet", widthDp = 1280, heightDp = 1600)
@Composable
private fun DownloadsScreenshot() = DownloadsPreview()

@PreviewTest
@Preview(name = "DynamicLight", wallpaper = Wallpapers.BLUE_DOMINATED_EXAMPLE, heightDp = 1600)
@Preview(
    name = "DynamicDark",
    uiMode = Configuration.UI_MODE_NIGHT_YES or Configuration.UI_MODE_TYPE_NORMAL,
    wallpaper = Wallpapers.BLUE_DOMINATED_EXAMPLE,
    heightDp = 1600,
)
@Composable
private fun DownloadsDynamicScreenshot() = DownloadsDynamicPreview()

@PreviewTest
@Preview(name = "Light")
@Preview(
    name = "Dark",
    uiMode = Configuration.UI_MODE_NIGHT_YES or Configuration.UI_MODE_TYPE_NORMAL,
)
@Preview(name = "LargeFont", fontScale = 2f)
@Composable
private fun NoDownloadsScreenshot() = NoDownloadsPreview()

@PreviewTest
@Preview(name = "DynamicLight", wallpaper = Wallpapers.BLUE_DOMINATED_EXAMPLE)
@Composable
private fun NoDownloadsDynamicScreenshot() = NoDownloadsDynamicPreview()

@PreviewTest
@Preview(name = "Light")
@Preview(
    name = "Dark",
    uiMode = Configuration.UI_MODE_NIGHT_YES or Configuration.UI_MODE_TYPE_NORMAL,
)
@Preview(name = "LargeFont", fontScale = 2f)
@Composable
private fun UnreadableScreenshot() = UnreadablePreview()

@PreviewTest
@Preview(name = "DynamicLight", wallpaper = Wallpapers.BLUE_DOMINATED_EXAMPLE)
@Composable
private fun UnreadableDynamicScreenshot() = UnreadableDynamicPreview()

@PreviewTest
@Preview(name = "Light", widthDp = 320)
@Preview(
    name = "Dark",
    uiMode = Configuration.UI_MODE_NIGHT_YES or Configuration.UI_MODE_TYPE_NORMAL,
    widthDp = 320,
)
@Preview(name = "LargeFont", fontScale = 2f, widthDp = 320)
@Composable
private fun StorageCardScreenshot() = StorageCardPreview()

@PreviewTest
@Preview(name = "DynamicLight", wallpaper = Wallpapers.BLUE_DOMINATED_EXAMPLE, widthDp = 320)
@Composable
private fun StorageCardDynamicScreenshot() = StorageCardDynamicPreview()
