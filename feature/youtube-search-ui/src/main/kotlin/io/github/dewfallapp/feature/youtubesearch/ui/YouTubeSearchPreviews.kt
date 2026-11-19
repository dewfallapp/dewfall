package io.github.dewfallapp.feature.youtubesearch.ui

import android.content.res.Configuration
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import io.github.dewfallapp.core.designsystem.theme.DewfallTheme
import io.github.dewfallapp.core.model.PublishedTime
import io.github.dewfallapp.core.model.VideoId
import io.github.dewfallapp.core.model.VideoKind
import io.github.dewfallapp.core.model.YouTubeError
import io.github.dewfallapp.feature.youtubesearch.logic.ResultsArea
import io.github.dewfallapp.feature.youtubesearch.logic.SearchRequest
import io.github.dewfallapp.feature.youtubesearch.logic.VideoRow
import io.github.dewfallapp.feature.youtubesearch.logic.YouTubeSearchState
import java.time.ZoneId
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Instant

// Previews with made-up videos, never real ones, and a fixed clock, so they don't change with the
// date.

private val previewNow = Instant.parse("2027-03-15T10:30:00Z")
private val previewZone = ZoneId.of("UTC")
private val sourdough = SearchRequest("sourdough", number = 1)

private fun row(
    id: String,
    title: String,
    channel: String,
    length: Int?,
    daysAgo: Int?,
    kind: VideoKind,
) =
    VideoRow(
        id = VideoId(id),
        title = title,
        channelName = channel,
        duration = length?.minutes,
        kind = kind,
        published = daysAgo?.let { PublishedTime(previewNow - it.days, isApproximate = true) },
    )

private val previewRows =
    listOf(
        row(
            "aaaaaaaaaaa",
            "Wild yeasts and the first week of a sourdough starter",
            "Kettle and Crumb",
            54,
            1,
            VideoKind.VIDEO,
        ),
        row(
            "bbbbbbbbbbb",
            "Feeding a sourdough starter while you're away for a week",
            "The Starter Jar",
            12,
            22,
            VideoKind.VIDEO,
        ),
        row(
            "ccccccccccc",
            "Sourdough baked on a wood-fired hearth, from mixing to cooling",
            "Hearth and Field",
            66,
            62,
            VideoKind.VIDEO,
        ),
        row(
            "ddddddddddd",
            "Baking along, live from the bakery",
            "Kettle and Crumb",
            null,
            // A live search result carries no publish time.
            null,
            VideoKind.LIVE,
        ),
    )

@Composable
private fun Preview(state: YouTubeSearchState, text: String = "") {
    DewfallTheme {
        YouTubeSearchScreen(
            state = state,
            textFieldState = rememberTextFieldState(text),
            now = previewNow,
            zone = previewZone,
            onIntent = {},
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
private fun ResultsPreview() {
    Preview(YouTubeSearchState(1, ResultsArea.Results(sourdough, previewRows)), text = "sourdough")
}

@Preview(name = "Light")
@Preview(
    name = "Dark",
    uiMode = Configuration.UI_MODE_NIGHT_YES or Configuration.UI_MODE_TYPE_NORMAL,
)
@Composable
private fun BeforeTheFirstSearchPreview() {
    Preview(YouTubeSearchState())
}

@Preview(name = "Light")
@Preview(name = "Large font", fontScale = 2f)
@Composable
private fun NoResultsPreview() {
    Preview(YouTubeSearchState(1, ResultsArea.NoResults(sourdough)), text = "sourdough")
}

@Preview(name = "Light")
@Preview(
    name = "Dark",
    uiMode = Configuration.UI_MODE_NIGHT_YES or Configuration.UI_MODE_TYPE_NORMAL,
)
@Composable
private fun FailedPreview() {
    Preview(
        YouTubeSearchState(1, ResultsArea.Failed(sourdough, YouTubeError.NETWORK)),
        text = "sourdough",
    )
}

@Preview(name = "Light")
@Composable
private fun LoadingPreview() {
    Preview(YouTubeSearchState(1, ResultsArea.Loading(sourdough)), text = "sourdough")
}
