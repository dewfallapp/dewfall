package io.github.dewfallapp.core.model

/**
 * The size of an image to load into a space [widthPx] pixels wide: the smallest size with a width
 * that is at least [widthPx] wide, or the widest when none is. Sizes whose width YouTube didn't
 * give count only when no size has a width, and then the first of them is the one. Null for an
 * empty list. Sizes of the same width keep the list's order, so the first of them wins.
 */
fun List<Thumbnail>.forWidth(widthPx: Int): Thumbnail? {
    val sized = mapNotNull { thumbnail -> thumbnail.width?.let { width -> thumbnail to width } }
    if (sized.isEmpty()) return firstOrNull()
    val wideEnough = sized.filter { (_, width) -> width >= widthPx }
    val chosen =
        if (wideEnough.isNotEmpty()) {
            wideEnough.minBy { (_, width) -> width }
        } else {
            sized.maxBy { (_, width) -> width }
        }
    return chosen.first
}
