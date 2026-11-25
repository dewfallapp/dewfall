package io.github.dewfallapp.feature.channelpage.ui

import androidx.compose.runtime.Composable
import io.github.dewfallapp.core.designsystem.theme.DewfallTheme
import io.github.dewfallapp.core.model.Channel
import io.github.dewfallapp.core.model.ChannelId
import io.github.dewfallapp.core.model.PublishedTime
import io.github.dewfallapp.core.model.Thumbnail
import io.github.dewfallapp.core.model.VideoId
import io.github.dewfallapp.core.model.VideoKind
import io.github.dewfallapp.core.ui.PreviewImages
import io.github.dewfallapp.feature.channelpage.logic.ChannelState
import io.github.dewfallapp.feature.channelpage.logic.LoadArea
import io.github.dewfallapp.feature.channelpage.logic.UploadRow
import java.time.ZoneId
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Instant

// Previews with a made-up channel and videos, never real ones, after the "Channel page" frames,
// and a fixed clock, so they don't change with the date. The @PreviewTest wrappers in
// src/screenshotTest carry their @Preview annotations and render them as screenshot tests. They
// use Dewfall's own palette, so the images don't depend on a wallpaper, except the dynamic color
// preview, whose wrappers name a fixed one. Image links are on a made-up host, and PreviewImages
// draws each as a plain fill, so screenshots never load or show a real image. See
// docs/testing.md#screenshot-tests.

internal val previewNow = Instant.parse("2027-03-15T10:30:00Z")
internal val previewZone = ZoneId.of("UTC")

/** A made-up ID in a channel ID's form, never a real channel's. */
internal val previewId = ChannelId("UC" + "0".repeat(21) + "1")

internal val previewChannel =
    Channel(
        id = previewId,
        name = "Kettle and Crumb",
        avatars = previewImage("kettle-and-crumb"),
        description =
            "Bread, fermentation and the history of everyday food. New videos most weeks.",
    )

private const val LONG_DESCRIPTION =
    "Bread, fermentation and the history of everyday food. Each episode follows one loaf, " +
        "cheese or pickle from its oldest recipe to the way it is made today, with the tools, " +
        "the weather and the people who made it. New videos most weeks.\n\n" +
        "Recipes for every episode are in the description of its video."

/** A made-up image link for [name], which PreviewImages draws as a plain fill. */
private fun previewImage(name: String) =
    listOf(Thumbnail("https://images.example.invalid/$name.jpg", width = 336, height = 188))

private fun row(id: String, title: String, length: Int?, daysAgo: Int, kind: VideoKind) =
    UploadRow(
        id = VideoId(id),
        title = title,
        duration = length?.minutes,
        kind = kind,
        published = PublishedTime(previewNow - daysAgo.days, isApproximate = true),
        // The live row keeps the placeholder in the screenshots.
        thumbnails = if (kind == VideoKind.LIVE) emptyList() else previewImage(id),
    )

private val previewRows =
    listOf(
        row(
            "aaaaaaaaaaa",
            "Wild yeasts and the first week of a sourdough starter",
            54,
            1,
            VideoKind.VIDEO,
        ),
        row("bbbbbbbbbbb", "How tea was blended before the tea bag", 18, 1, VideoKind.VIDEO),
        row("ccccccccccc", "Butter churned by hand, from cream to crock", 31, 7, VideoKind.VIDEO),
        row("ddddddddddd", "Baking along, live from the bakery", null, 14, VideoKind.LIVE),
        row(
            "eeeeeeeeeee",
            "Why sourdough keeps longer than yeasted bread",
            22,
            150,
            VideoKind.VIDEO,
        ),
    )

internal fun loaded(rows: List<UploadRow> = previewRows) =
    ChannelState(
        openedId = previewId,
        load = LoadArea.Loaded(1, rows),
        shownChannel = previewChannel,
    )

@Composable
internal fun ChannelPreview(state: ChannelState, dynamicColor: Boolean = false) {
    DewfallTheme(dynamicColor = dynamicColor) {
        PreviewImages {
            ChannelScreen(
                state = state,
                now = previewNow,
                zone = previewZone,
                onIntent = {},
                onOpenVideo = {},
                onBack = {},
            )
        }
    }
}

@Composable
internal fun NotSubscribedPreview() {
    ChannelPreview(loaded().copy(reads = mapOf(previewId to null)))
}

@Composable
internal fun NotSubscribedDynamicPreview() {
    ChannelPreview(loaded().copy(reads = mapOf(previewId to null)), dynamicColor = true)
}

@Composable
internal fun SubscribedPreview() {
    ChannelPreview(loaded().copy(reads = mapOf(previewId to previewChannel)))
}

@Composable
internal fun UnsubscribeDialogPreview() {
    ChannelPreview(
        loaded().copy(reads = mapOf(previewId to previewChannel), confirmingUnsubscribe = true),
    )
}

@Composable
internal fun LongDescriptionPreview() {
    // No picture, so the header's emblem with its initial stays in the screenshots.
    val channel = previewChannel.copy(description = LONG_DESCRIPTION, avatars = emptyList())
    ChannelPreview(
        loaded().copy(shownChannel = channel, reads = mapOf(previewId to channel)),
    )
}

@Composable
internal fun ExpandedDescriptionPreview() {
    val channel = previewChannel.copy(description = LONG_DESCRIPTION)
    ChannelPreview(
        loaded()
            .copy(
                shownChannel = channel,
                reads = mapOf(previewId to channel),
                descriptionExpanded = true,
            ),
    )
}
