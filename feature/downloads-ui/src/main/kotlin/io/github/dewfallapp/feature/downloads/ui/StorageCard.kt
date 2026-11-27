package io.github.dewfallapp.feature.downloads.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.semantics
import io.github.dewfallapp.core.designsystem.theme.DewfallSpacing
import io.github.dewfallapp.core.ui.fileSizeText
import io.github.dewfallapp.feature.downloads.logic.BytesInUse

/**
 * The "Offline storage" card at the top of the Downloads screen: its heading at the start and the
 * space that downloads take at the end, such as "3.6 GB", as in the "Downloads" frame. It has no
 * bar and no limit until Phase 7 adds the storage limit. Before the first read, and when the space
 * can't be read, it shows the heading with no amount, so the list under it doesn't move when the
 * amount comes. The amount goes under the heading when the two don't fit on one line, such as at a
 * large font in a narrow window. A screen reader reads the card as one item.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun StorageCard(bytesInUse: BytesInUse, modifier: Modifier = Modifier) {
    FlowRow(
        modifier =
            modifier
                .fillMaxWidth()
                .clip(MaterialTheme.shapes.medium)
                .background(MaterialTheme.colorScheme.surfaceContainerLow)
                .padding(DewfallSpacing.md)
                .semantics(mergeDescendants = true) {},
        horizontalArrangement = Arrangement.SpaceBetween,
        itemVerticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = stringResource(R.string.downloads_storage),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        if (bytesInUse is BytesInUse.Read) {
            Text(
                text = fileSizeText(bytesInUse.bytes),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurface,
            )
        }
    }
}
