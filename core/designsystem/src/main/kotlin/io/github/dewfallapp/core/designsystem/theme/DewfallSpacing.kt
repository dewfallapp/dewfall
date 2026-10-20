package io.github.dewfallapp.core.designsystem.theme

import androidx.compose.ui.unit.dp

/**
 * Dewfall's spacing, from the spacing tokens in
 * docs/design/claude-design/design-system/tokens.json, where 1rem is 16dp. Layouts follow an 8dp
 * rhythm on a 4-column grid. A token edit there is copied here by hand, and DesignTokensTest fails
 * until both agree.
 */
object DewfallSpacing {
    /** space-xs, 4dp. Only for padding inside badges and chips. */
    val xs = 4.dp

    /** space-sm, 8dp. */
    val sm = 8.dp

    /** space-md, 16dp. */
    val md = 16.dp

    /** space-lg, 24dp. Between channel groups. */
    val lg = 24.dp

    /** space-xl, 32dp. Around empty-state content. */
    val xl = 32.dp

    /** The screen's edge margin on phones, 16dp. */
    val margin = 16.dp

    /** The gap between columns of the 4-column grid, 16dp. */
    val gutter = 16.dp
}
