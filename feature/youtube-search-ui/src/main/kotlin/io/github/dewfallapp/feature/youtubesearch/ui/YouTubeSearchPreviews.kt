package io.github.dewfallapp.feature.youtubesearch.ui

import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalCursorBlinkEnabled
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
// date. The @PreviewTest wrappers in src/screenshotTest carry their @Preview annotations and
// render them as screenshot tests. They use Dewfall's own palette, so the images don't depend on a
// wallpaper, except the dynamic color preview, whose wrappers name a fixed one. See
// docs/testing.md#screenshot-tests.

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
private fun Preview(state: YouTubeSearchState, text: String = "", dynamicColor: Boolean = false) {
    // The search field takes focus, and its cursor blinks with the clock, so a screenshot would
    // catch it on or off depending on how long the render took. A steady cursor draws the same
    // every time.
    CompositionLocalProvider(LocalCursorBlinkEnabled provides false) {
        DewfallTheme(dynamicColor = dynamicColor) {
            YouTubeSearchScreen(
                state = state,
                textFieldState = rememberTextFieldState(text),
                now = previewNow,
                zone = previewZone,
                onIntent = {},
                onOpenVideo = {},
            )
        }
    }
}

@Composable
internal fun ResultsPreview() {
    Preview(YouTubeSearchState(1, ResultsArea.Results(sourdough, previewRows)), text = "sourdough")
}

@Composable
internal fun ResultsDynamicPreview() {
    Preview(
        YouTubeSearchState(1, ResultsArea.Results(sourdough, previewRows)),
        text = "sourdough",
        dynamicColor = true,
    )
}

@Composable
internal fun BeforeTheFirstSearchPreview() {
    Preview(YouTubeSearchState())
}

@Composable
internal fun NoResultsPreview() {
    Preview(YouTubeSearchState(1, ResultsArea.NoResults(sourdough)), text = "sourdough")
}

@Composable
internal fun FailedPreview() {
    Preview(
        YouTubeSearchState(1, ResultsArea.Failed(sourdough, YouTubeError.NETWORK)),
        text = "sourdough",
    )
}

@Composable
internal fun LoadingPreview() {
    Preview(YouTubeSearchState(1, ResultsArea.Loading(sourdough)), text = "sourdough")
}
