package io.github.dewfallapp.core.designsystem.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Shapes
import androidx.compose.ui.unit.dp

// The values come from the radius tokens in docs/design/claude-design/design-system/tokens.json,
// where 1rem is 16dp. A token edit there is copied here by hand, and DesignTokensTest fails until
// both agree. shape-full, for pills and circles, is Compose's CircleShape.

/** The Material 3 shape scale. Screens read it from MaterialTheme.shapes. */
internal val DewfallShapes =
    Shapes(
        // shape-extra-small: duration tags and badges
        extraSmall = RoundedCornerShape(4.dp),
        // shape-small: thumbnails and segmented buttons
        small = RoundedCornerShape(8.dp),
        // shape-medium: the video row's state layer, chips and the storage meter
        medium = RoundedCornerShape(12.dp),
        // shape-large: channel groups and bottom sheets
        large = RoundedCornerShape(16.dp),
        // shape-extra-large: dialogs
        extraLarge = RoundedCornerShape(24.dp),
    )
