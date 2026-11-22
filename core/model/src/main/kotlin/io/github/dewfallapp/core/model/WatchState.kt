package io.github.dewfallapp.core.model

import kotlin.time.Duration
import kotlin.time.Instant

/**
 * What the user did with one video: where they left it, and whether and when they marked it
 * watched. A video that was never played or marked has none. It is user data, so never log it. See
 * ADR 0007.
 */
data class WatchState(
    /** The position to resume from. Zero for a video that was marked watched before it played. */
    val position: Duration,
    /**
     * When the video was first marked watched, or null if it isn't marked watched. Marking it
     * watched again keeps the first time, so a rule that counts from it stays the same.
     */
    val watchedAt: Instant?,
    /** The time of the last save of the position or the last mark. */
    val updatedAt: Instant,
)
