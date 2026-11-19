package io.github.dewfallapp.core.designsystem.component

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import io.github.dewfallapp.core.designsystem.theme.DewfallSpacing

/** The glyph in a banner, 20dp, as the design system's Banner says. */
private val BannerGlyphSize = 20.dp

/**
 * One short notice, after the design system's Banner: a [glyph] in [glyphTint] named
 * [glyphDescription] for screen readers, the [message], and a text button with [actionLabel] that
 * calls [onAction], when both are given. The banner stays neutral, so only the glyph may carry the
 * error color. A screen reader reads it when it appears.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun Banner(
    message: String,
    glyph: Painter,
    glyphDescription: String,
    glyphTint: Color,
    modifier: Modifier = Modifier,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null,
) {
    Column(
        modifier =
            modifier
                .fillMaxWidth()
                .clip(MaterialTheme.shapes.medium)
                .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                .padding(DewfallSpacing.md)
                .semantics { liveRegion = LiveRegionMode.Polite },
        verticalArrangement = Arrangement.spacedBy(DewfallSpacing.sm),
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(DewfallSpacing.sm)) {
            Icon(
                painter = glyph,
                contentDescription = glyphDescription,
                tint = glyphTint,
                modifier = Modifier.size(BannerGlyphSize),
            )
            Text(text = message, style = MaterialTheme.typography.bodyMedium)
        }
        if (actionLabel != null && onAction != null) {
            FlowRow(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                TextButton(onClick = onAction) { Text(actionLabel) }
            }
        }
    }
}
