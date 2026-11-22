package io.github.dewfallapp.feature.channelpage.ui

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
private fun NotSubscribedScreenshot() = NotSubscribedPreview()

@PreviewTest
@Preview(name = "DynamicLight", wallpaper = Wallpapers.BLUE_DOMINATED_EXAMPLE)
@Preview(
    name = "DynamicDark",
    uiMode = Configuration.UI_MODE_NIGHT_YES or Configuration.UI_MODE_TYPE_NORMAL,
    wallpaper = Wallpapers.BLUE_DOMINATED_EXAMPLE,
)
@Composable
private fun NotSubscribedDynamicScreenshot() = NotSubscribedDynamicPreview()

@PreviewTest
@Preview(name = "Light")
@Preview(
    name = "Dark",
    uiMode = Configuration.UI_MODE_NIGHT_YES or Configuration.UI_MODE_TYPE_NORMAL,
)
@Preview(name = "LargeFont", fontScale = 2f)
@Composable
private fun SubscribedScreenshot() = SubscribedPreview()

@PreviewTest
@Preview(
    name = "Dark",
    uiMode = Configuration.UI_MODE_NIGHT_YES or Configuration.UI_MODE_TYPE_NORMAL,
)
@Composable
private fun UnsubscribeDialogScreenshot() = UnsubscribeDialogPreview()

@PreviewTest
@Preview(name = "Light")
@Preview(name = "LargeFont", fontScale = 2f)
@Composable
private fun LongDescriptionScreenshot() = LongDescriptionPreview()

@PreviewTest
@Preview(name = "Light")
@Composable
private fun ExpandedDescriptionScreenshot() = ExpandedDescriptionPreview()

@PreviewTest @Preview(name = "Light") @Composable private fun LoadingScreenshot() = LoadingPreview()

@PreviewTest
@Preview(name = "Light")
@Preview(
    name = "Dark",
    uiMode = Configuration.UI_MODE_NIGHT_YES or Configuration.UI_MODE_TYPE_NORMAL,
)
@Composable
private fun NoVideosScreenshot() = NoVideosPreview()

@PreviewTest
@Preview(name = "Light")
@Preview(
    name = "Dark",
    uiMode = Configuration.UI_MODE_NIGHT_YES or Configuration.UI_MODE_TYPE_NORMAL,
)
@Composable
private fun FailedScreenshot() = FailedPreview()

@PreviewTest
@Preview(name = "Light")
@Preview(
    name = "Dark",
    uiMode = Configuration.UI_MODE_NIGHT_YES or Configuration.UI_MODE_TYPE_NORMAL,
)
@Preview(name = "LargeFont", fontScale = 2f)
@Composable
private fun SubscribedOfflineScreenshot() = SubscribedOfflinePreview()

@PreviewTest @Preview(name = "Light") @Composable private fun ClosedScreenshot() = ClosedPreview()
