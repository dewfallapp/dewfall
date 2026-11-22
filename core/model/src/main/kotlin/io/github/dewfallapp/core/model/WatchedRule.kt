package io.github.dewfallapp.core.model

import kotlin.time.Duration

/** The share of a video's length, in tenths, from which a position counts as watched. */
private const val WATCHED_TENTHS = 9L

/** The number of tenths in a whole. */
private const val TENTHS = 10L

/**
 * Whether [position] counts as watched in a video of [length]: at least 90 percent of it, as
 * docs/vision.md says ("about 90 percent") and the maintainer confirmed on November 22, 2026. A
 * length that isn't known, or isn't above zero, never counts, so such a video counts as watched
 * only when it plays to its end. The player calls this, so the rule lives in one place. See
 * openspec/specs/player/spec.md.
 */
fun countsAsWatched(position: Duration, length: Duration?): Boolean {
    if (length == null || !length.isPositive() || length.isInfinite()) return false
    // Whole milliseconds, so the rule has no rounding of its own.
    return position.inWholeMilliseconds * TENTHS >= length.inWholeMilliseconds * WATCHED_TENTHS
}
