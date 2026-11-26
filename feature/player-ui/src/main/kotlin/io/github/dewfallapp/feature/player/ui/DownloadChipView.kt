package io.github.dewfallapp.feature.player.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import io.github.dewfallapp.core.designsystem.R as DesignR
import io.github.dewfallapp.core.designsystem.theme.DewfallSpacing
import io.github.dewfallapp.core.ui.downloadFailedText
import io.github.dewfallapp.core.ui.downloadPausedText
import io.github.dewfallapp.core.ui.downloadText
import io.github.dewfallapp.core.ui.downloadWaitingText
import io.github.dewfallapp.core.ui.downloadedText
import io.github.dewfallapp.core.ui.downloadingText
import io.github.dewfallapp.feature.player.logic.DownloadChip

/** A chip's glyph, as the design system's Chip component sets it. */
private val GlyphSize = 18.dp

/** A chip's least height, as the design system's Chip component sets it. */
private val ChipMinHeight = 32.dp

/** The width of an unselected chip's border, as the design system's Chip component sets it. */
private val BorderWidth = 1.dp

/** A percentage's whole, as a Float for the ring. */
private const val PERCENT_WHOLE = 100f

/**
 * The video screen's download chip for [chip], after the player spec's "The video screen downloads
 * the video" and the design system's Chip component. "Download", "Paused" and a failure are assist
 * chips that call [onDownload], and "Downloading" one that calls [onPause]. "Waiting to download"
 * and the selected "Downloaded" aren't clickable, so a screen reader announces no action. A screen
 * reader reads the chip's words, and the glyph or ring adds nothing.
 */
@Composable
internal fun DownloadChipView(
    chip: DownloadChip,
    onDownload: () -> Unit,
    onPause: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = MaterialTheme.colorScheme
    when (chip) {
        DownloadChip.NotDownloaded -> {
            ActionChip(
                downloadText(),
                DesignR.drawable.ic_download,
                colors.primary,
                onDownload,
                modifier,
            )
        }
        is DownloadChip.Paused -> {
            ActionChip(
                downloadPausedText(chip.percent),
                DesignR.drawable.ic_download,
                colors.primary,
                onDownload,
                modifier,
            )
        }
        is DownloadChip.Failed -> {
            ActionChip(
                downloadFailedText(chip.noSpace),
                DesignR.drawable.ic_error,
                colors.error,
                onDownload,
                modifier,
            )
        }
        is DownloadChip.Downloading -> DownloadingChip(chip.percent, onPause, modifier)
        DownloadChip.Waiting -> {
            StaticChip(
                label = downloadWaitingText(),
                glyph = DesignR.drawable.ic_schedule,
                selected = false,
                modifier = modifier,
            )
        }
        DownloadChip.Downloaded -> {
            StaticChip(
                label = downloadedText(),
                glyph = DesignR.drawable.ic_check,
                selected = true,
                modifier = modifier,
            )
        }
    }
}

/** An assist chip with [label] and a leading [glyph] in [glyphColor]. */
@Composable
private fun ActionChip(
    label: String,
    glyph: Int,
    glyphColor: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    AssistChip(
        onClick = onClick,
        label = { Text(text = label) },
        modifier = modifier,
        leadingIcon = {
            Icon(
                painter = painterResource(glyph),
                contentDescription = null,
                tint = glyphColor,
                modifier = Modifier.size(GlyphSize),
            )
        },
        shape = MaterialTheme.shapes.medium,
        colors =
            AssistChipDefaults.assistChipColors(labelColor = MaterialTheme.colorScheme.onSurface),
        border = BorderStroke(BorderWidth, MaterialTheme.colorScheme.outline),
    )
}

/** "Downloading", with a small ring, which turns without a percentage while the size is unknown. */
@Composable
private fun DownloadingChip(percent: Int?, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val colors = MaterialTheme.colorScheme
    AssistChip(
        onClick = onClick,
        label = { Text(text = downloadingText(percent)) },
        modifier = modifier,
        leadingIcon = {
            // The ring adds nothing for a screen reader: the chip's words say how far it got.
            val ring = Modifier.size(GlyphSize).clearAndSetSemantics {}
            if (percent == null) {
                CircularProgressIndicator(
                    modifier = ring,
                    color = colors.primary,
                    trackColor = colors.surfaceContainerHighest,
                    strokeWidth = 2.dp,
                )
            } else {
                CircularProgressIndicator(
                    progress = { percent / PERCENT_WHOLE },
                    modifier = ring,
                    color = colors.primary,
                    trackColor = colors.surfaceContainerHighest,
                    strokeWidth = 2.dp,
                )
            }
        },
        shape = MaterialTheme.shapes.medium,
        colors = AssistChipDefaults.assistChipColors(labelColor = colors.onSurface),
        border = BorderStroke(BorderWidth, colors.outline),
    )
}

/**
 * A chip that isn't a button: unselected, with an outline and [glyph], or [selected], filled, after
 * the design system's selected chip.
 */
@Composable
private fun StaticChip(
    label: String,
    glyph: Int,
    selected: Boolean,
    modifier: Modifier = Modifier,
) {
    val colors = MaterialTheme.colorScheme
    val content = if (selected) colors.onSecondaryContainer else colors.onSurfaceVariant
    Surface(
        modifier = modifier.semantics(mergeDescendants = true) { this.selected = selected },
        shape = MaterialTheme.shapes.medium,
        color = if (selected) colors.secondaryContainer else colors.surface,
        contentColor = content,
        border = if (selected) null else BorderStroke(BorderWidth, colors.outline),
    ) {
        Row(
            modifier =
                Modifier.defaultMinSize(minHeight = ChipMinHeight)
                    .padding(start = DewfallSpacing.sm, end = DewfallSpacing.md),
            horizontalArrangement = Arrangement.spacedBy(DewfallSpacing.sm),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                painter = painterResource(glyph),
                contentDescription = null,
                tint = content,
                modifier = Modifier.size(GlyphSize),
            )
            Text(text = label, style = MaterialTheme.typography.labelLarge)
        }
    }
}
