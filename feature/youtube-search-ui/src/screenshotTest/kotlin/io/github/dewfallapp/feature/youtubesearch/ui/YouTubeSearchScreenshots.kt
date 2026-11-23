package io.github.dewfallapp.feature.youtubesearch.ui

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
private fun ResultsScreenshot() = ResultsPreview()

@PreviewTest
@Preview(name = "DynamicLight", wallpaper = Wallpapers.BLUE_DOMINATED_EXAMPLE)
@Preview(
    name = "DynamicDark",
    uiMode = Configuration.UI_MODE_NIGHT_YES or Configuration.UI_MODE_TYPE_NORMAL,
    wallpaper = Wallpapers.BLUE_DOMINATED_EXAMPLE,
)
@Composable
private fun ResultsDynamicScreenshot() = ResultsDynamicPreview()

@PreviewTest
@Preview(name = "Light")
@Preview(name = "LargeFont", fontScale = 2f)
@Composable
private fun OnlyChannelsScreenshot() = OnlyChannelsPreview()

// Only the dark image, as for the channel page's dialog: Material 3 draws a dialog in a window of
// its own, whose light image came out slightly different on the CI runners. See
// docs/testing.md#screenshot-tests.
@PreviewTest
@Preview(
    name = "Dark",
    uiMode = Configuration.UI_MODE_NIGHT_YES or Configuration.UI_MODE_TYPE_NORMAL,
)
@Composable
private fun UnsubscribeDialogScreenshot() = UnsubscribeDialogPreview()

@PreviewTest
@Preview(name = "Light")
@Preview(
    name = "Dark",
    uiMode = Configuration.UI_MODE_NIGHT_YES or Configuration.UI_MODE_TYPE_NORMAL,
)
@Composable
private fun BeforeTheFirstSearchScreenshot() = BeforeTheFirstSearchPreview()

@PreviewTest
@Preview(name = "Light")
@Preview(name = "LargeFont", fontScale = 2f)
@Composable
private fun NoResultsScreenshot() = NoResultsPreview()

@PreviewTest
@Preview(name = "Light")
@Preview(
    name = "Dark",
    uiMode = Configuration.UI_MODE_NIGHT_YES or Configuration.UI_MODE_TYPE_NORMAL,
)
@Composable
private fun FailedScreenshot() = FailedPreview()

@PreviewTest @Preview(name = "Light") @Composable private fun LoadingScreenshot() = LoadingPreview()
