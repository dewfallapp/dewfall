package io.github.dewfallapp.core.data.database

import kotlin.time.Clock
import kotlin.time.Instant

/** A clock that always says [now], so the tests choose every time. */
internal class FixedClock(var now: Instant) : Clock {
    override fun now(): Instant = now
}
