package io.github.dewfallapp.feature.takeoutimport.ui

import androidx.compose.runtime.Composable
import io.github.dewfallapp.core.designsystem.theme.DewfallTheme
import io.github.dewfallapp.core.model.ChannelId
import io.github.dewfallapp.core.model.TakeoutImportFailure
import io.github.dewfallapp.feature.takeoutimport.logic.ImportedChannelRow
import io.github.dewfallapp.feature.takeoutimport.logic.TakeoutImportState

// Previews with made-up channels, never real ones: the design's own names and placeholder IDs. The
// @PreviewTest wrappers in src/screenshotTest carry their @Preview annotations and render them as
// screenshot tests. They use Dewfall's own palette, so the images don't depend on a wallpaper,
// except the dynamic color preview, whose wrappers name a fixed one. See
// docs/testing.md#screenshot-tests.

private val previewChannels =
    listOf(
            "Larkspur Lectures",
            "Tidewater Woodshop",
            "Kettle and Crumb",
            "Orbit Hour",
            "Hearth and Field",
            "The Starter Jar",
        )
        .mapIndexed { index, name ->
            ImportedChannelRow(ChannelId("UCpreview" + index.toString().padStart(15, '0')), name)
        }

/** How many channels the report with only new ones lists. */
private const val ONLY_NEW_COUNT = 4

private val importedWithEveryCount =
    TakeoutImportState.Imported(
        previewChannels,
        added = 4,
        alreadySubscribed = 2,
        unreadableLines = 1,
    )

@Composable
private fun Preview(state: TakeoutImportState, dynamicColor: Boolean = false) {
    DewfallTheme(dynamicColor = dynamicColor) {
        TakeoutImportScreen(state = state, onChooseFile = {}, onBack = {}, onDone = {})
    }
}

@Composable
internal fun IdlePreview() {
    Preview(TakeoutImportState.Idle)
}

@Composable
internal fun IdleDynamicPreview() {
    Preview(TakeoutImportState.Idle, dynamicColor = true)
}

@Composable
internal fun ReadingPreview() {
    Preview(TakeoutImportState.Reading)
}

@Composable
internal fun ImportedPreview() {
    Preview(importedWithEveryCount)
}

@Composable
internal fun ImportedDynamicPreview() {
    Preview(importedWithEveryCount, dynamicColor = true)
}

@Composable
internal fun ImportedOnlyNewPreview() {
    Preview(
        TakeoutImportState.Imported(
            previewChannels.take(ONLY_NEW_COUNT),
            added = ONLY_NEW_COUNT,
            alreadySubscribed = 0,
            unreadableLines = 0,
        ),
    )
}

@Composable
internal fun NoChannelsPreview() {
    Preview(TakeoutImportState.Failed(TakeoutImportFailure.NO_CHANNELS))
}

@Composable
internal fun NotReadablePreview() {
    Preview(TakeoutImportState.Failed(TakeoutImportFailure.NOT_READABLE))
}

@Composable
internal fun TooLargePreview() {
    Preview(TakeoutImportState.Failed(TakeoutImportFailure.TOO_LARGE))
}
