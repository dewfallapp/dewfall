package io.github.dewfallapp.core.model

/**
 * One size of an image on YouTube's servers, such as a video's thumbnail or a channel's avatar. A
 * model holds a list with every size YouTube offers for one image, and the screen picks the size
 * that fits.
 *
 * The URL is user data, so never log it. See ADR 0007.
 */
data class Thumbnail(
    val url: String,
    /** The width in pixels, or null if YouTube didn't say. */
    val width: Int?,
    /** The height in pixels, or null if YouTube didn't say. */
    val height: Int?,
)
