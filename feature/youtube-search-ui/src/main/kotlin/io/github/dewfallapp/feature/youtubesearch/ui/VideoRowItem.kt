package io.github.dewfallapp.feature.youtubesearch.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import io.github.dewfallapp.core.designsystem.theme.DewfallSpacing
import io.github.dewfallapp.feature.youtubesearch.logic.VideoRow
import io.github.dewfallapp.feature.youtubesearch.logic.lengthLabel
import io.github.dewfallapp.feature.youtubesearch.logic.timeAgo
import java.time.ZoneId
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
 * One video, after the design system's VideoRow: a plain thumbnail box with the length, then the
 * title, which wraps as far as it needs, and the channel and time ago. The box stays plain
 * until #176 decides how to load thumbnails. Its text reads as one node to a screen reader.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun VideoRowItem(
    row: VideoRow,
    now: Instant,
    zone: ZoneId,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier =
            modifier.fillMaxWidth().padding(DewfallSpacing.sm).semantics(
                mergeDescendants = true
            ) {},
        horizontalArrangement = Arrangement.spacedBy(DewfallSpacing.md),
    ) {
        Thumbnail(row)
        Column(modifier = Modifier.weight(1f)) {
            Text(text = row.title, style = MaterialTheme.typography.titleMedium)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(DewfallSpacing.sm)) {
                MetaText(row.channelName)
                timeAgo(row.published, now, zone)?.let { MetaText(timeAgoText(it)) }
            }
        }
    }
}

@Composable
private fun Thumbnail(row: VideoRow, modifier: Modifier = Modifier) {
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
        val label = lengthLabel(row.duration, row.kind)
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
                            MaterialTheme.colorScheme.surface.copy(alpha = DURATION_SCRIM_ALPHA)
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
