package io.github.dewfallapp.feature.channelpage.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.unit.dp
import io.github.dewfallapp.core.designsystem.R as DesignR
import io.github.dewfallapp.core.designsystem.theme.DewfallSpacing
import io.github.dewfallapp.feature.channelpage.logic.ChannelIntent

/** The glyph in the subscription card's status line, as the SubscriptionCard README asks. */
private val StatusGlyphSize = 24.dp

/**
 * The design system's SubscriptionCard: the state in words with a glyph, then the one action that
 * changes it.
 */
@Composable
internal fun SubscriptionCard(
    channelName: String,
    subscribed: Boolean,
    onIntent: (ChannelIntent) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier =
            modifier
                .fillMaxWidth()
                .clip(MaterialTheme.shapes.large)
                .background(MaterialTheme.colorScheme.surfaceContainerLow)
                .padding(DewfallSpacing.md),
        verticalArrangement = Arrangement.spacedBy(DewfallSpacing.sm),
    ) {
        SubscriptionStatus(channelName = channelName, subscribed = subscribed)
        Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.CenterEnd) {
            if (subscribed) {
                TextButton(onClick = { onIntent(ChannelIntent.UnsubscribeTapped) }) {
                    Text(text = stringResource(R.string.channel_page_unsubscribe))
                }
            } else {
                SubscribeButton(onClick = { onIntent(ChannelIntent.Subscribe) })
            }
        }
    }
}

/**
 * The card's status line. It is one lasting node in both states, a polite live region whose
 * description, the state with the channel's name and then the line under it, changes, so TalkBack
 * says the new state once after a tap.
 */
@Composable
private fun SubscriptionStatus(channelName: String, subscribed: Boolean) {
    val texts =
        if (subscribed) {
            StatusTexts(
                R.string.channel_page_subscribed_to,
                R.string.channel_page_subscribed,
                R.string.channel_page_subscribed_body,
                DesignR.drawable.ic_check,
            )
        } else {
            StatusTexts(
                R.string.channel_page_not_subscribed_to,
                R.string.channel_page_not_subscribed,
                R.string.channel_page_not_subscribed_body,
                DesignR.drawable.ic_add,
            )
        }
    val spoken =
        stringResource(
            R.string.channel_page_status_spoken,
            stringResource(texts.spoken, channelName),
            stringResource(texts.body),
        )
    Row(
        modifier =
            Modifier.fillMaxWidth().clearAndSetSemantics {
                contentDescription = spoken
                liveRegion = LiveRegionMode.Polite
            },
        horizontalArrangement = Arrangement.spacedBy(DewfallSpacing.sm),
    ) {
        Icon(
            painter = painterResource(texts.glyph),
            contentDescription = spoken,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(StatusGlyphSize),
        )
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(DewfallSpacing.xs),
        ) {
            Text(text = stringResource(texts.title), style = MaterialTheme.typography.titleSmall)
            Text(
                text = stringResource(texts.body),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/** The string and glyph resources of one state of the card's status line. */
private class StatusTexts(val spoken: Int, val title: Int, val body: Int, val glyph: Int)

/** The filled "Subscribe" button with the add glyph, as the SubscriptionCard README asks. */
@Composable
private fun SubscribeButton(onClick: () -> Unit) {
    Button(onClick = onClick, contentPadding = ButtonDefaults.ButtonWithIconContentPadding) {
        // The button's word says what the glyph shows, so screen readers read the word only.
        Icon(
            painter = painterResource(DesignR.drawable.ic_add),
            contentDescription = stringResource(R.string.channel_page_subscribe),
            modifier = Modifier.size(ButtonDefaults.IconSize).clearAndSetSemantics {},
        )
        Spacer(Modifier.size(ButtonDefaults.IconSpacing))
        Text(text = stringResource(R.string.channel_page_subscribe))
    }
}

/** The dialog that "Unsubscribe" opens. Cancel, the back gesture and a tap outside close it. */
@Composable
internal fun UnsubscribeDialog(channelName: String, onIntent: (ChannelIntent) -> Unit) {
    AlertDialog(
        onDismissRequest = { onIntent(ChannelIntent.UnsubscribeCancelled) },
        title = {
            Text(text = stringResource(R.string.channel_page_unsubscribe_title, channelName))
        },
        text = { Text(text = stringResource(R.string.channel_page_unsubscribe_body)) },
        confirmButton = {
            TextButton(onClick = { onIntent(ChannelIntent.UnsubscribeConfirmed) }) {
                Text(text = stringResource(R.string.channel_page_unsubscribe))
            }
        },
        dismissButton = {
            TextButton(onClick = { onIntent(ChannelIntent.UnsubscribeCancelled) }) {
                Text(text = stringResource(R.string.channel_page_cancel))
            }
        },
    )
}
