package io.github.dewfallapp.feature.player.logic

private const val SECONDS_PER_MINUTE = 60L
private const val SECONDS_PER_HOUR = 3600L
private const val MILLIS_PER_SECOND = 1000L

/**
 * A playback position or length of [ms] milliseconds as the controls show it: "18:24" under an
 * hour, "1:14:02" from an hour on. A negative time counts as 0.
 */
fun clockText(ms: Long): String {
    val seconds = ms.coerceAtLeast(0) / MILLIS_PER_SECOND
    val hours = seconds / SECONDS_PER_HOUR
    val minutes = seconds % SECONDS_PER_HOUR / SECONDS_PER_MINUTE
    val rest = seconds % SECONDS_PER_MINUTE
    return if (hours > 0) {
        "%d:%02d:%02d".format(hours, minutes, rest)
    } else {
        "%d:%02d".format(minutes, rest)
    }
}
