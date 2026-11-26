package io.github.dewfallapp.core.designsystem.component

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * A channel's emblem, after the design system's ChannelHeader: a circle on `tertiary-container`
 * with a 1dp `outline-variant` ring and the channel's [initial], or nothing when the name has none.
 * It is [size] wide at the default font size, and grows with the font size, so the initial always
 * fits. It is hidden from screen readers, which read the channel's name next to it. `:core:ui`'s
 * ChannelAvatar draws the channel's picture over it, so it is also the placeholder while the
 * picture loads, when it can't, and when the channel has none.
 */
@Composable
fun ChannelEmblem(
    initial: String?,
    size: Dp,
    modifier: Modifier = Modifier,
    textStyle: TextStyle = MaterialTheme.typography.titleSmall,
) {
    val scaledSize = size * LocalDensity.current.fontScale.coerceAtLeast(1f)
    Box(
        modifier =
            modifier
                .size(scaledSize)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.tertiaryContainer)
                .border(1.dp, MaterialTheme.colorScheme.outlineVariant, CircleShape)
                .clearAndSetSemantics {},
        contentAlignment = Alignment.Center,
    ) {
        if (initial != null) {
            Text(
                text = initial,
                style = textStyle,
                color = MaterialTheme.colorScheme.onTertiaryContainer,
            )
        }
    }
}
