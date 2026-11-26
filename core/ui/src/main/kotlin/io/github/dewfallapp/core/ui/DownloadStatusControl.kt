package io.github.dewfallapp.core.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedIconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.Hyphens
import androidx.compose.ui.text.style.LineBreak
import androidx.compose.ui.unit.dp
import io.github.dewfallapp.core.designsystem.R as DesignR
import io.github.dewfallapp.core.designsystem.theme.DewfallSpacing
import io.github.dewfallapp.core.model.DownloadStatus

/** The progress ring's size, as the design system's VideoRow sets it. */
private val RingSize = 40.dp

/** The downloaded mark's size, as the design system's VideoRow sets it. */
private val DownloadedMarkSize = 32.dp

/** The outlined download button's border. */
private val BorderWidth = 1.dp

/** A glyph beside the meta line's words, the size of the meta line's text. */
private val MetaGlyphSize = 16.dp

/** The glyph inside the ring and the downloaded mark. */
private val InnerGlyphSize = 20.dp

/**
 * A row's download words for [status], after the design system's VideoRow: the `schedule` glyph and
 * "Waiting to download", "Downloading, 42%", "Paused, 42%", or the `error` glyph in the error color
 * and the failure. A video that isn't downloaded, or is, has no words. The glyphs sit next to their
 * words, so they add nothing for a screen reader, and the words read with the row's text.
 */
@Composable
fun DownloadStatusWords(status: DownloadStatus, modifier: Modifier = Modifier) {
    val words = downloadWords(status) ?: return
    val colors = MaterialTheme.colorScheme
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(DewfallSpacing.xs),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        val glyph =
            when (status) {
                is DownloadStatus.Waiting -> DesignR.drawable.ic_schedule to colors.onSurfaceVariant
                is DownloadStatus.Failed -> DesignR.drawable.ic_error to colors.error
                else -> null
            }
        if (glyph != null) {
            Icon(
                painter = painterResource(glyph.first),
                contentDescription = null,
                tint = glyph.second,
                modifier = Modifier.size(MetaGlyphSize),
            )
        }
        Text(
            text = words,
            // Hyphenated, so a long word beside the control at a large font breaks at a
            // syllable. The design system forbids hyphens only in titles. The maintainer chose
            // this on November 26, 2026.
            style =
                MaterialTheme.typography.bodySmall.copy(
                    hyphens = Hyphens.Auto,
                    lineBreak = LineBreak.Paragraph,
                ),
            color = colors.onSurfaceVariant,
        )
    }
}

/**
 * A row's download control for [status], after the design system's VideoRow, at the end of the meta
 * line: the outlined download button, heard as "Download", "Go on downloading" or "Try downloading
 * again", which calls [onDownload]; the progress ring with the `pause` glyph, heard as "Pause
 * download", which calls [onPause]; or the round check mark, which isn't a button and is heard as
 * "Downloaded". A waiting download has no control. Each control is a focus target of its own, apart
 * from the row's text.
 */
@Composable
fun DownloadStatusControl(
    status: DownloadStatus,
    onDownload: () -> Unit,
    onPause: () -> Unit,
    modifier: Modifier = Modifier,
) {
    when (status) {
        DownloadStatus.NotDownloaded -> {
            DownloadButton(stringResource(R.string.core_ui_download), onDownload, modifier)
        }
        is DownloadStatus.Paused -> {
            DownloadButton(stringResource(R.string.core_ui_download_go_on), onDownload, modifier)
        }
        is DownloadStatus.Failed -> {
            DownloadButton(
                stringResource(R.string.core_ui_download_try_again),
                onDownload,
                modifier,
            )
        }
        is DownloadStatus.Downloading -> PauseRing(status.progress.percent, onPause, modifier)
        is DownloadStatus.Downloaded -> DownloadedMark(modifier)
        is DownloadStatus.Waiting -> Unit
    }
}

@Composable
private fun DownloadButton(
    description: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    OutlinedIconButton(
        onClick = onClick,
        modifier = modifier,
        // The design system's borders that mark a control use outline.
        border = BorderStroke(BorderWidth, MaterialTheme.colorScheme.outline),
    ) {
        Icon(
            painter = painterResource(DesignR.drawable.ic_download),
            contentDescription = description,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/** The ring, which turns without a percentage while the size is unknown. */
@Composable
private fun PauseRing(percent: Int?, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val colors = MaterialTheme.colorScheme
    IconButton(onClick = onClick, modifier = modifier) {
        Box(contentAlignment = Alignment.Center) {
            // The ring adds nothing for a screen reader: the row's words say how far it got.
            val ring = Modifier.size(RingSize).clearAndSetSemantics {}
            if (percent == null) {
                CircularProgressIndicator(
                    modifier = ring,
                    color = colors.primary,
                    trackColor = colors.surfaceContainerHighest,
                )
            } else {
                CircularProgressIndicator(
                    progress = { percent / PERCENT_WHOLE },
                    modifier = ring,
                    color = colors.primary,
                    trackColor = colors.surfaceContainerHighest,
                )
            }
            Icon(
                painter = painterResource(DesignR.drawable.ic_pause),
                contentDescription = stringResource(R.string.core_ui_download_pause),
                tint = colors.primary,
                modifier = Modifier.size(InnerGlyphSize),
            )
        }
    }
}

/** The check mark, which isn't a button but is a focus target of its own. */
@Composable
private fun DownloadedMark(modifier: Modifier = Modifier) {
    val description = downloadedText()
    Box(
        modifier =
            modifier
                .size(DownloadedMarkSize)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.primaryContainer)
                .semantics(mergeDescendants = true) { contentDescription = description },
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            painter = painterResource(DesignR.drawable.ic_check),
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onPrimaryContainer,
            modifier = Modifier.size(InnerGlyphSize),
        )
    }
}

/** A percentage's whole, as a Float for the ring. */
private const val PERCENT_WHOLE = 100f
