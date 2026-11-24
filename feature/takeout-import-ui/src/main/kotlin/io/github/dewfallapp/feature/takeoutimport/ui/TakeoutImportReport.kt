package io.github.dewfallapp.feature.takeoutimport.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import io.github.dewfallapp.core.designsystem.component.ChannelEmblem
import io.github.dewfallapp.core.designsystem.theme.DewfallSpacing
import io.github.dewfallapp.core.model.initialOf
import io.github.dewfallapp.core.ui.rowGroupShape
import io.github.dewfallapp.feature.takeoutimport.logic.ImportedChannelRow
import io.github.dewfallapp.feature.takeoutimport.logic.TakeoutImportState

/** A channel's emblem in the report, as in the "Flow 1: channels imported" frame. */
private val EmblemSize = 36.dp

/** The import's report: how many channels the file holds, the counts, and the channels. */
@Composable
internal fun ColumnScope.Report(state: TakeoutImportState.Imported, onDone: () -> Unit) {
    LazyColumn(
        modifier = Modifier.weight(1f).fillMaxWidth(),
        contentPadding =
            PaddingValues(
                start = DewfallSpacing.margin,
                end = DewfallSpacing.margin,
                bottom = DewfallSpacing.md,
            ),
    ) {
        reportHeader(state)
        channelRows(state.channels)
    }
    BottomArea { WideButton(text = stringResource(R.string.takeout_import_done), onClick = onDone) }
}

private fun LazyListScope.reportHeader(state: TakeoutImportState.Imported) {
    item(key = "header") {
        Column(
            modifier = Modifier.padding(top = DewfallSpacing.md, bottom = DewfallSpacing.lg),
            verticalArrangement = Arrangement.spacedBy(DewfallSpacing.lg),
        ) {
            Text(
                text =
                    pluralStringResource(
                        R.plurals.takeout_import_found,
                        state.channels.size,
                        state.channels.size,
                    ),
                style = MaterialTheme.typography.displaySmall,
                modifier = Modifier.semantics { heading() },
            )
            Text(
                text = stringResource(R.string.takeout_import_from_file),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(text = countsLine(state), style = MaterialTheme.typography.bodyLarge)
        }
    }
}

/** The counts that aren't zero, such as "35 new. 5 were already in Dewfall.", in one line. */
@Composable
private fun countsLine(state: TakeoutImportState.Imported): String {
    val parts = mutableListOf<String>()
    if (state.added > 0) {
        parts += pluralStringResource(R.plurals.takeout_import_new, state.added, state.added)
    }
    if (state.alreadySubscribed > 0) {
        parts +=
            pluralStringResource(
                R.plurals.takeout_import_already,
                state.alreadySubscribed,
                state.alreadySubscribed,
            )
    }
    if (state.unreadableLines > 0) {
        parts +=
            pluralStringResource(
                R.plurals.takeout_import_unreadable,
                state.unreadableLines,
                state.unreadableLines,
            )
    }
    return parts.joinToString(stringResource(R.string.takeout_import_counts_separator))
}

/**
 * The channels in one card, as in the frame. Each row draws its part of the card, so the list stays
 * lazy for files with hundreds of channels.
 */
private fun LazyListScope.channelRows(channels: List<ImportedChannelRow>) {
    itemsIndexed(channels, key = { _, row -> row.id.value }) { index, row ->
        Row(
            modifier =
                Modifier.fillMaxWidth()
                    .clip(rowGroupShape(index, channels.size))
                    .background(MaterialTheme.colorScheme.surfaceContainer)
                    .padding(
                        horizontal = DewfallSpacing.md,
                        vertical = if (channels.size == 1) DewfallSpacing.md else DewfallSpacing.sm,
                    ),
            horizontalArrangement = Arrangement.spacedBy(DewfallSpacing.md),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            ChannelEmblem(initial = initialOf(row.name), size = EmblemSize)
            Text(
                text = row.name,
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.weight(1f),
            )
        }
    }
}
