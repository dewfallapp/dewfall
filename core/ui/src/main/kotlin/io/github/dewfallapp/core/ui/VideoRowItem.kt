package io.github.dewfallapp.core.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CornerSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import io.github.dewfallapp.core.designsystem.theme.DewfallSpacing
import io.github.dewfallapp.core.model.PublishedTime
import io.github.dewfallapp.core.model.Thumbnail
import io.github.dewfallapp.core.model.VideoKind
import io.github.dewfallapp.core.model.lengthLabel
import io.github.dewfallapp.core.model.timeAgo
import java.time.ZoneId
import kotlin.time.Duration
import kotlin.time.Instant

/**
 * The thumbnail box's width at the default font size, as in the design system's VideoRow. It grows
 * with the font size, so the length tag inside it is never cut off.
 */
private val ThumbnailWidth = 72.dp

/** A thumbnail's shape, 16:9, as YouTube's thumbnails are. */
private const val THUMBNAIL_ASPECT_RATIO = 16f / 9f

/** The opacity of the length tag's background, the design system's duration-scrim. */
private const val DURATION_SCRIM_ALPHA = 0.8f

/**
 * One video, after the design system's VideoRow: a thumbnail box with the size of [thumbnails] that
 * fits it, under the length of a video of [duration] and [kind], then the [title], which wraps as
 * far as it needs, and the [channelName], if there is one, and how long ago it was [published],
 * counted from [now] in [zone]. A list of one channel's videos leaves the name out. The box is the
 * design's plain placeholder while the thumbnail loads, when it can't, and when [thumbnails] is
 * empty. Its text reads as one node to a screen reader, and the image adds nothing to it.
 *
 * The meta line ends with the video's download status, when a list gives one: [statusWords], such
 * as DownloadStatusWords, which wrap with the rest of the meta line and read with the row's text,
 * and [statusControl], such as DownloadStatusControl, right-aligned at the line's end, which is a
 * focus target of its own. [supporting], such as the Downloads screen's progress bar and status
 * line, goes under the meta line and reads with the row's text.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun VideoRowItem(
    title: String,
    channelName: String?,
    duration: Duration?,
    kind: VideoKind,
    thumbnails: List<Thumbnail>,
    published: PublishedTime?,
    now: Instant,
    zone: ZoneId,
    modifier: Modifier = Modifier,
    statusWords: (@Composable () -> Unit)? = null,
    statusControl: (@Composable () -> Unit)? = null,
    supporting: (@Composable ColumnScope.() -> Unit)? = null,
) {
    Row(
        modifier =
            modifier.fillMaxWidth().padding(DewfallSpacing.sm).semantics(
                mergeDescendants = true,
            ) {},
        horizontalArrangement = Arrangement.spacedBy(DewfallSpacing.md),
    ) {
        ThumbnailBox(duration, kind, thumbnails)
        Column(modifier = Modifier.weight(1f)) {
            Text(text = title, style = MaterialTheme.typography.titleMedium)
            Row(verticalAlignment = Alignment.CenterVertically) {
                FlowRow(
                    modifier = Modifier.weight(1f),
                    horizontalArrangement = Arrangement.spacedBy(DewfallSpacing.sm),
                    itemVerticalAlignment = Alignment.CenterVertically,
                ) {
                    if (channelName != null) MetaText(channelName)
                    timeAgo(published, now, zone)?.let { MetaText(timeAgoText(it)) }
                    statusWords?.invoke()
                }
                statusControl?.invoke()
            }
            supporting?.invoke(this)
        }
    }
}

@Composable
private fun ThumbnailBox(
    duration: Duration?,
    kind: VideoKind,
    thumbnails: List<Thumbnail>,
    modifier: Modifier = Modifier,
) {
    val fontScale = LocalDensity.current.fontScale.coerceAtLeast(1f)
    Box(
        modifier =
            modifier
                .width(ThumbnailWidth * fontScale)
                .aspectRatio(THUMBNAIL_ASPECT_RATIO)
                .clip(MaterialTheme.shapes.small)
                .background(MaterialTheme.colorScheme.surfaceContainerHighest),
        contentAlignment = Alignment.BottomEnd,
    ) {
        RemoteImage(
            thumbnails = thumbnails,
            width = ThumbnailWidth,
            modifier = Modifier.matchParentSize(),
        )
        val label = lengthLabel(duration, kind)
        if (label != null) {
            val spoken = spokenLength(label)
            Text(
                text = lengthText(label),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurface,
                modifier =
                    Modifier.padding(DewfallSpacing.xs)
                        .clip(MaterialTheme.shapes.extraSmall)
                        .background(
                            MaterialTheme.colorScheme.surface.copy(alpha = DURATION_SCRIM_ALPHA),
                        )
                        .padding(horizontal = DewfallSpacing.xs)
                        .semantics { contentDescription = spoken },
            )
        }
    }
}

@Composable
private fun MetaText(text: String, modifier: Modifier = Modifier) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = modifier,
    )
}

/**
 * The part of a group's rounded shape that row [index] of [count] draws, so a list of rows on one
 * background reads as one group with rounded outer corners, as the design's groups do.
 */
@Composable
fun rowGroupShape(index: Int, count: Int): Shape {
    val large = MaterialTheme.shapes.large
    val none = CornerSize(0)
    return when {
        count == 1 -> large
        index == 0 -> large.copy(bottomStart = none, bottomEnd = none)
        index == count - 1 -> large.copy(topStart = none, topEnd = none)
        else -> RectangleShape
    }
}
