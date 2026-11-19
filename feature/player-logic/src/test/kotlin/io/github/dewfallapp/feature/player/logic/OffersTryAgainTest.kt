package io.github.dewfallapp.feature.player.logic

import io.github.dewfallapp.core.model.YouTubeError
import kotlin.test.Test
import kotlin.test.assertEquals

class OffersTryAgainTest {
    @Test
    fun `only the errors that stop all of YouTube offer Try again`() {
        val offering = YouTubeError.entries.filter(::offersTryAgain).toSet()

        assertEquals(
            setOf(YouTubeError.NETWORK, YouTubeError.BLOCKED, YouTubeError.BROKEN),
            offering,
        )
    }
}
