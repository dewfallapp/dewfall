package io.github.dewfallapp.core.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import coil3.compose.AsyncImage
import io.github.dewfallapp.core.model.Thumbnail
import io.github.dewfallapp.core.model.forWidth
import kotlin.math.ceil

/**
 * The width in pixels of a space [widthDp] wide at the default font size, which grows with
 * [fontScale] but never shrinks below it, as the rows' boxes and emblems do, on a screen of
 * [density] pixels per dp. Rounded up, so the image is never narrower than its space.
 */
internal fun imageWidthPx(widthDp: Float, fontScale: Float, density: Float): Int =
    ceil(widthDp * fontScale.coerceAtLeast(1f) * density).toInt()

/** The link of the size of [thumbnails] to load into a space [widthPx] wide, or null for none. */
internal fun imageLink(thumbnails: List<Thumbnail>, widthPx: Int): String? =
    thumbnails.forWidth(widthPx)?.url

/**
 * The size of [thumbnails] that fits a space [width] wide at the default font size, drawn to fill
 * the space and cut to it, over whatever placeholder lies under it. While it loads, and when it
 * can't, it draws nothing, so the placeholder shows. It asks for nothing when [thumbnails] is
 * empty. It is hidden from screen readers: the row's text says what it shows.
 */
@Composable
internal fun RemoteImage(thumbnails: List<Thumbnail>, width: Dp, modifier: Modifier = Modifier) {
    val density = LocalDensity.current
    val link = imageLink(thumbnails, imageWidthPx(width.value, density.fontScale, density.density))
    if (link != null) {
        AsyncImage(
            model = link,
            contentDescription = null,
            modifier = modifier,
            contentScale = ContentScale.Crop,
        )
    }
}
