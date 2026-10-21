package io.github.dewfallapp.core.model

import kotlin.time.Instant

/**
 * When a video was published, or when a premiere or live stream that hasn't started is scheduled to
 * start.
 *
 * YouTube doesn't always give an exact time. Sometimes it gives only a date, or a relative time in
 * days or longer, such as "3 weeks ago". Then [isApproximate] is true, and [instant] is an
 * estimate: its time of day means nothing, and its date can be as far off as the unit YouTube gave.
 * A relative time in hours or minutes, such as "7 hours ago", isn't marked approximate, but it is
 * only as exact as that unit.
 */
data class PublishedTime(val instant: Instant, val isApproximate: Boolean)
