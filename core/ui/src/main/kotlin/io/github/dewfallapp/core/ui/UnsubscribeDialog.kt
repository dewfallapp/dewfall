package io.github.dewfallapp.core.ui

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource

/**
 * The dialog that asks before unsubscribing from [channelName], as the design system's
 * SubscriptionCard README asks: "Unsubscribe" runs [onConfirm], and "Cancel", the back gesture and
 * a tap outside run [onDismiss]. The channel page and Search YouTube's channel rows share it.
 */
@Composable
fun UnsubscribeDialog(channelName: String, onConfirm: () -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(text = stringResource(R.string.core_ui_unsubscribe_title, channelName)) },
        text = { Text(text = stringResource(R.string.core_ui_unsubscribe_body)) },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text(text = stringResource(R.string.core_ui_unsubscribe))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(text = stringResource(R.string.core_ui_cancel)) }
        },
    )
}
