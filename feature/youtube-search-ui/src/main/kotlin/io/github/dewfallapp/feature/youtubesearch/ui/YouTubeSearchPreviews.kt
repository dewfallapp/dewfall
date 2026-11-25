package io.github.dewfallapp.feature.youtubesearch.ui

import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalCursorBlinkEnabled
import io.github.dewfallapp.core.designsystem.theme.DewfallTheme
import io.github.dewfallapp.core.model.ChannelId
import io.github.dewfallapp.core.model.PublishedTime
import io.github.dewfallapp.core.model.VideoId
import io.github.dewfallapp.core.model.VideoKind
import io.github.dewfallapp.core.model.YouTubeError
import io.github.dewfallapp.core.ui.PreviewImages
import io.github.dewfallapp.core.ui.previewImageLinks
import io.github.dewfallapp.feature.youtubesearch.logic.ChannelRow
import io.github.dewfallapp.feature.youtubesearch.logic.ResultsArea
import io.github.dewfallapp.feature.youtubesearch.logic.SearchRequest
import io.github.dewfallapp.feature.youtubesearch.logic.VideoRow
import io.github.dewfallapp.feature.youtubesearch.logic.YouTubeSearchState
import java.time.ZoneId
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Instant

// Previews with made-up videos, never real ones, and a fixed clock, so they don't change with the
// date. Their image links are on a made-up host, and PreviewImages draws each as a plain fill, so
// screenshots never load or show a real image. The @PreviewTest wrappers in src/screenshotTest
// carry their @Preview annotations and render them as screenshot tests. They use Dewfall's own
// palette, so the images don't depend on a wallpaper, except the dynamic color preview, whose
// wrappers name a fixed one. See docs/testing.md#screenshot-tests.

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
    withImage: Boolean = true,
) =
    VideoRow(
        id = VideoId(id),
        title = title,
        channelName = channel,
        duration = length?.minutes,
        kind = kind,
        published = daysAgo?.let { PublishedTime(previewNow - it.days, isApproximate = true) },
        thumbnails = if (withImage) previewImageLinks(id) else emptyList(),
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
            // One row without a thumbnail keeps the placeholder in the screenshots.
            withImage = false,
        ),
    )

private val kettleId = ChannelId("UCaaaaaaaaaaaaaaaaaaaaaa")

private val previewChannels =
    listOf(
        ChannelRow(
            kettleId,
            "Kettle and Crumb",
            "Bread, fermentation and old recipes",
            previewImageLinks("kettle-and-crumb"),
        ),
        ChannelRow(
            ChannelId("UCbbbbbbbbbbbbbbbbbbbbbb"),
            "The Starter Jar",
            "Sourdough, start to finish. A starter fed every morning, a loaf every weekend, and " +
                "everything that went wrong along the way",
            emptyList(),
        ),
    )

/** The user subscribes to Kettle and Crumb, as in the "Search YouTube, results" frames. */
private val previewSubscribed = setOf(kettleId)

private fun results(
    channels: List<ChannelRow> = previewChannels,
    videos: List<VideoRow> = previewRows,
) =
    YouTubeSearchState(
        lastRequestNumber = 1,
        results = ResultsArea.Results(sourdough, channels, videos),
        subscribedIds = previewSubscribed,
    )

@Composable
private fun Preview(state: YouTubeSearchState, text: String = "", dynamicColor: Boolean = false) {
    // The search field takes focus, and its cursor blinks with the clock, so a screenshot would
    // catch it on or off depending on how long the render took. A steady cursor draws the same
    // every time.
    CompositionLocalProvider(LocalCursorBlinkEnabled provides false) {
        DewfallTheme(dynamicColor = dynamicColor) {
            PreviewImages {
                YouTubeSearchScreen(
                    state = state,
                    textFieldState = rememberTextFieldState(text),
                    now = previewNow,
                    zone = previewZone,
                    onIntent = {},
                    onOpenVideo = {},
                    onOpenChannel = {},
                    onBack = {},
                )
            }
        }
    }
}

@Composable
internal fun ResultsPreview() {
    Preview(results(), text = "sourdough")
}

@Composable
internal fun ResultsDynamicPreview() {
    Preview(results(), text = "sourdough", dynamicColor = true)
}

@Composable
internal fun OnlyChannelsPreview() {
    Preview(results(videos = emptyList()), text = "sourdough")
}

@Composable
internal fun UnsubscribeDialogPreview() {
    Preview(results().copy(confirmingUnsubscribe = kettleId), text = "sourdough")
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
