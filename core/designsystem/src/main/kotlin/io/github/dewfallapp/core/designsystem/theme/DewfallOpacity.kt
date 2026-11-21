package io.github.dewfallapp.core.designsystem.theme

/**
 * Dewfall's opacities that Material 3's components don't already apply, from the opacity tokens in
 * docs/design/claude-design/design-system/tokens.json. Each one comes with its first use. A token
 * edit there is copied here by hand, and DesignTokensTest fails until both agree.
 */
object DewfallOpacity {
    /**
     * duration-scrim, 0.8. `surface` at this opacity behind the duration tag on a thumbnail, and
     * behind the fullscreen player's controls over video.
     */
    const val DURATION_SCRIM = 0.8f
}
