package io.github.dewfallapp.core.ui

import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import io.github.dewfallapp.core.designsystem.component.ChannelEmblem
import io.github.dewfallapp.core.model.Thumbnail
import io.github.dewfallapp.core.model.initialOf

/**
 * A channel's picture, from [avatars], in the design system's ChannelEmblem: the emblem with the
 * initial of [name] shows while the picture loads, when it can't, and when [avatars] is empty, and
 * the picture covers it once it has loaded, with the emblem's ring on top. It is [size] wide at the
 * default font size and grows with the font size, as the emblem does. It is hidden from screen
 * readers, which read the channel's name next to it.
 */
@Composable
fun ChannelAvatar(
    name: String,
    avatars: List<Thumbnail>,
    size: Dp,
    modifier: Modifier = Modifier,
    textStyle: TextStyle = MaterialTheme.typography.titleSmall,
) {
    val scaledSize = size * LocalDensity.current.fontScale.coerceAtLeast(1f)
    Box(modifier = modifier.size(scaledSize).clearAndSetSemantics {}) {
        ChannelEmblem(initial = initialOf(name), size = size, textStyle = textStyle)
        RemoteImage(
            thumbnails = avatars,
            width = size,
            modifier = Modifier.matchParentSize().clip(CircleShape),
        )
        Box(
            modifier =
                Modifier.matchParentSize()
                    .border(1.dp, MaterialTheme.colorScheme.outlineVariant, CircleShape),
        )
    }
}
