package io.github.dewfallapp.feature.inbox.ui

import androidx.compose.runtime.Composable
import io.github.dewfallapp.core.designsystem.theme.DewfallTheme
import io.github.dewfallapp.core.model.ChannelId
import io.github.dewfallapp.core.model.PublishedTime
import io.github.dewfallapp.core.model.VideoId
import io.github.dewfallapp.core.model.VideoKind
import io.github.dewfallapp.core.model.VideoSummary
import io.github.dewfallapp.feature.inbox.logic.InboxContent
import io.github.dewfallapp.feature.inbox.logic.InboxIntent
import io.github.dewfallapp.feature.inbox.logic.InboxReducer
import io.github.dewfallapp.feature.inbox.logic.InboxState
import java.time.ZoneId
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Instant

// Previews with made-up channels and videos from the design's frames, never real ones, and a fixed
// clock, so they don't change with the date. The @PreviewTest wrappers in src/screenshotTest carry
// their @Preview annotations and render them as screenshot tests. They use Dewfall's own palette,
// so the images don't depend on a wallpaper, except the dynamic color previews, whose wrappers
// name a fixed one. See docs/testing.md#screenshot-tests.

private val previewNow = Instant.parse("2027-03-15T07:30:00Z")
private val previewZone = ZoneId.of("UTC")

/** The channels the previews subscribe to, as in the "Inbox" frame. */
private const val PREVIEW_CHANNELS = 4

/** A channel ID is "UC" and 22 more characters. */
private const val CHANNEL_ID_TAIL = 22

/** YouTube gives times from a day on as days ago, which Dewfall marks approximate. */
private const val HOURS_IN_A_DAY = 24

private fun video(id: String, channel: Int, name: String, title: String, length: Int, ago: Int) =
    VideoSummary(
        id = VideoId(id),
        title = title,
        channelId = ChannelId("UC" + channel.toString().padStart(CHANNEL_ID_TAIL, '0')),
        channelName = name,
        duration = length.minutes,
        published = PublishedTime(previewNow - ago.hours, isApproximate = ago >= HOURS_IN_A_DAY),
        thumbnails = emptyList(),
        kind = VideoKind.VIDEO,
    )

private val previewVideos =
    listOf(
        video(
            id = "aaaaaaaaaa1",
            channel = 1,
            name = "Tidewater Woodshop",
            title = "Restoring a joiner's workbench older than the house",
            length = 108,
            ago = 4,
        ),
        video(
            id = "aaaaaaaaaa2",
            channel = 2,
            name = "Larkspur Lectures",
            title = "How reading rooms were lit before electric lamps",
            length = 74,
            ago = 7,
        ),
        video(
            id = "aaaaaaaaaa3",
            channel = 1,
            name = "Tidewater Woodshop",
            title = "Sharpening hand planes on natural waterstones",
            length = 28,
            ago = 9,
        ),
        video(
            id = "aaaaaaaaaa4",
            channel = 2,
            name = "Larkspur Lectures",
            title = "Mosses, lichens and the slow chemistry of rock",
            length = 42,
            ago = 26,
        ),
        video(
            id = "aaaaaaaaaa5",
            channel = 3,
            name = "Kettle and Crumb",
            title = "Wild yeasts and the first week of a sourdough starter",
            length = 54,
            ago = 27,
        ),
        video(
            id = "aaaaaaaaaa6",
            channel = 3,
            name = "Kettle and Crumb",
            title = "How tea was blended before the tea bag",
            length = 18,
            ago = 30,
        ),
        video(
            id = "aaaaaaaaaa7",
            channel = 4,
            name = "Orbit Hour",
            title = "Why the night sky is dark, and what that says about the age of the universe",
            length = 92,
            ago = 32,
        ),
    )

/** The state the reducer makes from [videos] and [subscribed] channels. */
private fun loaded(subscribed: Int, videos: List<VideoSummary>): InboxState =
    InboxReducer.reduce(InboxState(), InboxIntent.Loaded(subscribed, videos)).state

@Composable
private fun Preview(state: InboxState, dynamicColor: Boolean = false) {
    DewfallTheme(dynamicColor = dynamicColor) {
        InboxScreen(
            state = state,
            now = previewNow,
            zone = previewZone,
            onOpenVideo = {},
            onOpenChannel = {},
            onOpenSearch = {},
            onImportFromTakeout = {},
        )
    }
}

@Composable
internal fun VideosPreview() {
    Preview(loaded(PREVIEW_CHANNELS, previewVideos))
}

@Composable
internal fun VideosDynamicPreview() {
    Preview(loaded(PREVIEW_CHANNELS, previewVideos), dynamicColor = true)
}

@Composable
internal fun NoChannelsPreview() {
    Preview(InboxState(InboxContent.NoChannels))
}

@Composable
internal fun NoChannelsDynamicPreview() {
    Preview(InboxState(InboxContent.NoChannels), dynamicColor = true)
}

@Composable
internal fun CaughtUpPreview() {
    Preview(InboxState(InboxContent.CaughtUp(PREVIEW_CHANNELS)))
}

@Composable
internal fun CaughtUpDynamicPreview() {
    Preview(InboxState(InboxContent.CaughtUp(PREVIEW_CHANNELS)), dynamicColor = true)
}

@Composable
internal fun UnreadablePreview() {
    Preview(InboxState(InboxContent.Unreadable))
}
