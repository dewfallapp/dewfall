package io.github.dewfallapp.feature.downloads.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import io.github.dewfallapp.core.designsystem.theme.DewfallSpacing
import io.github.dewfallapp.core.designsystem.theme.DewfallTheme
import io.github.dewfallapp.feature.downloads.logic.BytesInUse

// Previews of the storage card on its own, which the @PreviewTest wrappers in src/screenshotTest
// render as screenshot tests, as DownloadsPreviews.kt says.

/** 3.6 GB, as in the "Downloads" frame. */
private const val PREVIEW_BYTES = 3_600_000_000L

/** The storage card with an amount, with zero, and before its first read, one under the other. */
@Composable
private fun StorageCards(dynamicColor: Boolean) {
    DewfallTheme(dynamicColor = dynamicColor) {
        Surface {
            Column(
                modifier = Modifier.padding(DewfallSpacing.margin),
                verticalArrangement = Arrangement.spacedBy(DewfallSpacing.sm),
            ) {
                StorageCard(bytesInUse = BytesInUse.Read(PREVIEW_BYTES))
                StorageCard(bytesInUse = BytesInUse.Read(0))
                StorageCard(bytesInUse = BytesInUse.Reading)
            }
        }
    }
}

@Composable
internal fun StorageCardPreview() {
    StorageCards(dynamicColor = false)
}

@Composable
internal fun StorageCardDynamicPreview() {
    StorageCards(dynamicColor = true)
}
