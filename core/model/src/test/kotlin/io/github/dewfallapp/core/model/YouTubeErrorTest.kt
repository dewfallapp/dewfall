package io.github.dewfallapp.core.model

import kotlin.test.Test
import kotlin.test.assertEquals

class YouTubeErrorTest {
    @Test
    fun `only the errors that stop all of YouTube offer Try again`() {
        val offering = YouTubeError.entries.filter { it.offersTryAgain }.toSet()

        assertEquals(
            setOf(YouTubeError.NETWORK, YouTubeError.BLOCKED, YouTubeError.BROKEN),
            offering,
        )
    }
}
