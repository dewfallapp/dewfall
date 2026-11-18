package io.github.dewfallapp.core.youtube.newpipe.saved

import io.github.dewfallapp.core.model.PublishedTime
import io.github.dewfallapp.core.model.YouTubeResult
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.temporal.ChronoUnit
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import kotlin.test.fail
import kotlin.time.Duration.Companion.days
import kotlin.time.Instant
import kotlin.time.toJavaInstant
import kotlin.time.toKotlinInstant

/**
 * How far before the plain count back NewPipeExtractor's estimate of a relative time can fall: it
 * truncates a time in days or longer to midnight, up to a day earlier, and takes one more day off
 * for years.
 */
private val EARLIEST_ESTIMATE_SLACK = 2.days

/**
 * The number of requests that the call sent, apart from the one for sw.js_data.
 * docs/architecture.md counts a function's requests that way, because only the first call in a
 * process that uses the web client fetches sw.js_data, and every replayed call is a first call.
 */
internal val SavedYouTube.countedRequests: Int
    get() = sentRequests.count { it != Endpoint.SW_JS_DATA }

/** The value of this result, or a failed test if the call failed. */
internal fun <T> YouTubeResult<T>.valueOrFail(): T =
    when (this) {
        is YouTubeResult.Success -> value
        is YouTubeResult.Failure -> fail("The call failed with $error")
    }

/**
 * Asserts that [published] is NewPipeExtractor's estimate for a relative time of [amount] [unit],
 * such as "21 years ago", in an answer that the layer read between [before] and [after].
 *
 * NewPipeExtractor counts back from the clock when it reads an answer, so a saved answer gives a
 * different instant on each day that a test runs. It counts back in the JVM's time zone and
 * truncates a time in days or longer to midnight there. So the instant is approximate, and lies
 * between [before] counted back, less two days, and [after] counted back, on any day and in any
 * time zone.
 */
internal fun assertAgo(
    published: PublishedTime?,
    amount: Long,
    unit: ChronoUnit,
    before: Instant,
    after: Instant,
) {
    assertNotNull(published)
    assertTrue(published.isApproximate, "A relative time of $amount $unit is an estimate")
    val zone = ZoneId.systemDefault()
    fun countBack(from: Instant): Instant =
        LocalDateTime.ofInstant(from.toJavaInstant(), zone)
            .minus(amount, unit)
            .atZone(zone)
            .toInstant()
            .toKotlinInstant()
    val earliest = countBack(before) - EARLIEST_ESTIMATE_SLACK
    val latest = countBack(after)
    assertTrue(
        published.instant in earliest..latest,
        "${published.instant} isn't between $earliest and $latest",
    )
}
