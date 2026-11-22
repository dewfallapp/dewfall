@file:OptIn(UnstableApi::class)

package io.github.dewfallapp.feature.player.ui

import androidx.annotation.OptIn
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.layout.ContentScale
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.ui.compose.PlayerSurface
import androidx.media3.ui.compose.modifiers.resizeWithContentScale
import androidx.media3.ui.compose.state.rememberPresentationState

/**
 * The picture of [player] on a SurfaceView, fitted inside [modifier]'s bounds, with [shutter] over
 * it until the first frame. It does what media3-ui-compose 1.11.1's ContentFrame does with its
 * defaults, ContentScale.Fit and a SurfaceView, plus a clip at the SurfaceView's own bounds.
 *
 * Android 10 and 11 make the SurfaceView's hole with `Canvas.drawColor(0, PorterDuff.Mode.CLEAR)`,
 * which clears everything inside the canvas's clip, and Compose draws the SurfaceView into its
 * enclosing layer's canvas with no clip of its own. Without this clip, the hole cleared every pixel
 * drawn before it, such as the video screen's background and minimize button, and the window showed
 * black there (#211). Android 12 and later clear only the SurfaceView's rectangle, with
 * `Canvas.punchHole`, and the clip, which comes after the resize, is that same rectangle. So the
 * picture shows the same on every Android. Compare it with ContentFrame when Media3 updates.
 */
@Composable
internal fun ClippedContentFrame(
    player: Player,
    shutter: @Composable () -> Unit,
    modifier: Modifier = Modifier,
) {
    val presentation = rememberPresentationState(player)
    PlayerSurface(
        player = player,
        modifier =
            modifier
                .resizeWithContentScale(ContentScale.Fit, presentation.videoSizeDp)
                .clipToBounds(),
    )
    if (presentation.coverSurface) shutter()
}
