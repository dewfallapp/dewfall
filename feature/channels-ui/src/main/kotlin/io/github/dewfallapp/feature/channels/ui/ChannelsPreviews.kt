package io.github.dewfallapp.feature.channels.ui

import androidx.compose.runtime.Composable
import io.github.dewfallapp.core.designsystem.theme.DewfallTheme
import io.github.dewfallapp.core.model.Channel
import io.github.dewfallapp.core.model.ChannelId
import io.github.dewfallapp.core.model.ChannelOverview
import io.github.dewfallapp.core.model.PublishedTime
import io.github.dewfallapp.core.model.SubscribedChannel
import io.github.dewfallapp.feature.channels.logic.ChannelsIntent
import io.github.dewfallapp.feature.channels.logic.ChannelsList
import io.github.dewfallapp.feature.channels.logic.ChannelsReducer
import io.github.dewfallapp.feature.channels.logic.ChannelsState
import java.time.ZoneId
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.hours
import kotlin.time.Instant

// Previews with made-up channels from the design's frames, never real ones, and a fixed clock, so
// they don't change with the date. The @PreviewTest wrappers in src/screenshotTest carry their
// @Preview annotations and render them as screenshot tests. They use Dewfall's own palette, so the
// images don't depend on a wallpaper, except the dynamic color previews, whose wrappers name a
// fixed one. See docs/testing.md#screenshot-tests.

private val previewNow = Instant.parse("2027-03-15T07:30:00Z")
private val previewZone = ZoneId.of("UTC")
private val previewSubscribedAt = Instant.parse("2027-01-04T09:00:00Z")

/** A channel ID is "UC" and 22 more characters. */
private const val CHANNEL_ID_TAIL = 22

private fun overview(n: Int, name: String, lastUpload: PublishedTime?) =
    ChannelOverview(
        SubscribedChannel(
            Channel(
                id = ChannelId("UC" + n.toString().padStart(CHANNEL_ID_TAIL, '0')),
                name = name,
                avatars = emptyList(),
                description = "",
            ),
            previewSubscribedAt,
        ),
        lastUpload,
    )

/**
 * The "Channels" frame's channels, in the repository's order, and one more that has no saved video
 * yet, as after an import, so it has no last upload line.
 */
private val previewChannels =
    listOf(
        overview(1, "Kettle and Crumb", PublishedTime(previewNow - 1.days, isApproximate = true)),
        overview(2, "Larkspur Lectures", PublishedTime(previewNow - 7.hours, isApproximate = false)),
        overview(3, "Orbit Hour", PublishedTime(previewNow - 1.days, isApproximate = true)),
        overview(4, "Quillwort Field Notes", null),
        overview(5, "Tidewater Woodshop", PublishedTime(previewNow - 4.hours, isApproximate = false)),
    )

private fun read(channels: List<ChannelOverview>): ChannelsState =
    ChannelsReducer.reduce(ChannelsState(), ChannelsIntent.ChannelsRead(channels)).state

@Composable
private fun Preview(state: ChannelsState, dynamicColor: Boolean = false) {
    DewfallTheme(dynamicColor = dynamicColor) {
        ChannelsScreen(
            state = state,
            now = previewNow,
            zone = previewZone,
            onOpenChannel = {},
            onOpenSearch = {},
        )
    }
}

@Composable
internal fun ChannelsPreview() {
    Preview(read(previewChannels))
}

@Composable
internal fun ChannelsDynamicPreview() {
    Preview(read(previewChannels), dynamicColor = true)
}

@Composable
internal fun NoChannelsPreview() {
    Preview(ChannelsState(ChannelsList.NoChannels))
}

@Composable
internal fun NoChannelsDynamicPreview() {
    Preview(ChannelsState(ChannelsList.NoChannels), dynamicColor = true)
}
