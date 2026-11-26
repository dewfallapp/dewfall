package io.github.dewfallapp.core.ui

import android.graphics.drawable.ColorDrawable
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.toArgb
import coil3.annotation.ExperimentalCoilApi
import coil3.asImage
import coil3.compose.AsyncImagePreviewHandler
import coil3.compose.LocalAsyncImagePreviewHandler
import io.github.dewfallapp.core.model.Thumbnail

/**
 * A made-up image link for [name], on a host that doesn't exist, for previews' sample data, which
 * [PreviewImages] draws as a plain fill. Never a real video's or channel's link.
 */
fun previewImageLinks(name: String): List<Thumbnail> =
    listOf(Thumbnail("https://images.example.invalid/$name.jpg", width = 336, height = 188))

/**
 * Shows [content] with every image it loads replaced by a fixed, made-up one: a plain fill of the
 * theme's `primary` color, which neither placeholder uses, so a preview tells image from
 * placeholder at a glance. Previews call it inside DewfallTheme, so screenshot tests never need the
 * network and never hold a real thumbnail, which public screenshots must not. Coil uses it only in
 * previews, never in the app. See docs/testing.md#screenshot-tests.
 */
@OptIn(ExperimentalCoilApi::class)
@Composable
fun PreviewImages(content: @Composable () -> Unit) {
    val color = MaterialTheme.colorScheme.primary.toArgb()
    val handler = remember(color) { AsyncImagePreviewHandler { ColorDrawable(color).asImage() } }
    CompositionLocalProvider(LocalAsyncImagePreviewHandler provides handler, content = content)
}
