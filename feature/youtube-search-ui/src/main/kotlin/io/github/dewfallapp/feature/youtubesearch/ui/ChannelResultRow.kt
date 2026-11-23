package io.github.dewfallapp.feature.youtubesearch.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.github.dewfallapp.core.designsystem.R as DesignR
import io.github.dewfallapp.core.designsystem.component.ChannelEmblem
import io.github.dewfallapp.core.designsystem.theme.DewfallSpacing
import io.github.dewfallapp.core.model.initialOf
import io.github.dewfallapp.feature.youtubesearch.logic.ChannelRow

/** The channel's emblem in a row, as in the "Search YouTube, results" frames. */
private val EmblemSize = 36.dp

/**
 * The narrowest the emblem and the text may get beside the button. Below it, the button wraps under
 * them, so a large font or a narrow window never squeezes the name into a sliver.
 */
private val TextMinWidth = 160.dp

/** The excerpt shows at most this many lines, ending with an ellipsis. */
private const val EXCERPT_MAX_LINES = 2

/**
 * A channel found by a search: its emblem, its name, YouTube's excerpt of its description, and the
 * compact Subscribe button at the end, which shows only while [subscribed] is known. Tapping the
 * row outside the button runs [onOpen]. The button runs [onSubscribe] or [onUnsubscribe].
 */
@Composable
internal fun ChannelResultRow(
    row: ChannelRow,
    subscribed: Boolean?,
    onOpen: () -> Unit,
    onSubscribe: () -> Unit,
    onUnsubscribe: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val openLabel = stringResource(R.string.youtube_search_open_channel)
    FlowRow(
        modifier =
            modifier
                .clickable(onClickLabel = openLabel, onClick = onOpen)
                .padding(DewfallSpacing.sm),
        horizontalArrangement = Arrangement.spacedBy(DewfallSpacing.sm, Alignment.End),
        verticalArrangement = Arrangement.spacedBy(DewfallSpacing.sm),
        itemVerticalAlignment = Alignment.CenterVertically,
    ) {
        Row(
            modifier = Modifier.weight(1f).widthIn(min = TextMinWidth),
            horizontalArrangement = Arrangement.spacedBy(DewfallSpacing.sm),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            ChannelEmblem(initial = initialOf(row.name), size = EmblemSize)
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = row.name,
                    style = MaterialTheme.typography.titleSmall,
                    modifier = Modifier.semantics { heading() },
                )
                if (row.excerpt.isNotEmpty()) {
                    Text(
                        text = row.excerpt,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = EXCERPT_MAX_LINES,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
        if (subscribed != null) {
            CompactSubscribeButton(
                channelName = row.name,
                subscribed = subscribed,
                onClick = if (subscribed) onUnsubscribe else onSubscribe,
            )
        }
    }
}

/**
 * The design system's compact button for lists: a tonal "Subscribe" with the add glyph, or an
 * outlined "Subscribed" with the check glyph, as the SubscriptionCard README's "In lists" asks. It
 * is one Button in both states, so it keeps one semantics node, a polite live region whose
 * description names the state and the channel. When the state changes, TalkBack says the new
 * description once.
 */
@Composable
private fun CompactSubscribeButton(channelName: String, subscribed: Boolean, onClick: () -> Unit) {
    val word =
        stringResource(
            if (subscribed) {
                R.string.youtube_search_subscribed
            } else {
                R.string.youtube_search_subscribe
            },
        )
    val spoken =
        stringResource(
            if (subscribed) {
                R.string.youtube_search_subscribed_to
            } else {
                R.string.youtube_search_subscribe_to
            },
            channelName,
        )
    val action =
        stringResource(
            if (subscribed) {
                R.string.youtube_search_unsubscribe
            } else {
                R.string.youtube_search_subscribe
            },
        )
    Button(
        onClick = onClick,
        // The button stays a node of its own for screen readers, apart from the clickable row it
        // sits in, so it keeps a merging boundary before its own semantics replace the children's.
        modifier =
            Modifier.semantics(mergeDescendants = true) {}
                .clearAndSetSemantics {
                    contentDescription = spoken
                    role = Role.Button
                    liveRegion = LiveRegionMode.Polite
                    onClick(label = action) {
                        onClick()
                        true
                    }
                },
        colors =
            if (subscribed) {
                ButtonDefaults.outlinedButtonColors()
            } else {
                ButtonDefaults.filledTonalButtonColors()
            },
        border = if (subscribed) BorderStroke(1.dp, MaterialTheme.colorScheme.outline) else null,
        contentPadding = ButtonDefaults.ButtonWithIconContentPadding,
    ) {
        Icon(
            painter =
                painterResource(
                    if (subscribed) DesignR.drawable.ic_check else DesignR.drawable.ic_add,
                ),
            contentDescription = word,
            modifier = Modifier.size(ButtonDefaults.IconSize),
        )
        Spacer(Modifier.size(ButtonDefaults.IconSpacing))
        Text(text = word)
    }
}
