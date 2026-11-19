package io.github.dewfallapp.feature.player.ui

import android.content.res.Configuration
import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import io.github.dewfallapp.core.designsystem.theme.DewfallTheme
import io.github.dewfallapp.core.model.PublishedTime
import io.github.dewfallapp.core.model.YouTubeError
import io.github.dewfallapp.feature.player.logic.DetailsArea
import io.github.dewfallapp.feature.player.logic.PlayerState
import io.github.dewfallapp.feature.player.logic.VideoPage
import java.time.ZoneId
import kotlin.time.Duration.Companion.hours
import kotlin.time.Instant

// Previews with a made-up video, never a real one, and a fixed clock, so they don't change with the
// date.

private val previewNow = Instant.parse("2027-03-15T10:30:00Z")
private val previewZone = ZoneId.of("UTC")

private val previewPage =
    VideoPage(
        title = "How reading rooms were lit before electric lamps",
        channelName = "Larkspur Lectures",
        channelInitial = "L",
        description =
            "A walk through four nineteenth-century reading rooms and the ways their builders " +
                "brought daylight deep inside: high clerestory windows, pale plaster, and desks " +
                "turned to face north so no shadow fell across the page.\n\n" +
                "Recorded on location, with a floor plan for each room.",
        published = PublishedTime(previewNow - 7.hours, isApproximate = false),
        isLive = false,
    )

@Composable
private fun Preview(state: PlayerState) {
    DewfallTheme {
        PlayerScreen(
            state = state,
            now = previewNow,
            zone = previewZone,
            onIntent = {},
            onClose = {},
        )
    }
}

@Preview(name = "Light")
@Preview(
    name = "Dark",
    uiMode = Configuration.UI_MODE_NIGHT_YES or Configuration.UI_MODE_TYPE_NORMAL,
)
@Preview(name = "Large font", fontScale = 2f)
@Preview(name = "Tablet", widthDp = 1280, heightDp = 800)
@Composable
private fun DetailsPreview() {
    Preview(PlayerState(DetailsArea.Shown(previewPage)))
}

@Preview(name = "Light")
@Preview(
    name = "Dark",
    uiMode = Configuration.UI_MODE_NIGHT_YES or Configuration.UI_MODE_TYPE_NORMAL,
)
@Composable
private fun LiveWithNoDescriptionPreview() {
    Preview(
        PlayerState(
            DetailsArea.Shown(
                previewPage.copy(
                    title = "Baking along, live from the bakery",
                    channelName = "Kettle and Crumb",
                    channelInitial = "K",
                    description = "",
                    isLive = true,
                )
            )
        )
    )
}

@Preview(name = "Light")
@Composable
private fun LoadingPreview() {
    Preview(PlayerState(DetailsArea.Loading(1)))
}

@Preview(name = "Light")
@Preview(
    name = "Dark",
    uiMode = Configuration.UI_MODE_NIGHT_YES or Configuration.UI_MODE_TYPE_NORMAL,
)
@Preview(name = "Large font", fontScale = 2f)
@Composable
private fun NetworkFailurePreview() {
    Preview(PlayerState(DetailsArea.Failed(1, YouTubeError.NETWORK)))
}

@Preview(name = "Light")
@Composable
private fun UnavailablePreview() {
    Preview(PlayerState(DetailsArea.Failed(1, YouTubeError.UNAVAILABLE)))
}
