package io.github.dewfallapp.core.data.subscriptions

import io.github.dewfallapp.core.model.Channel
import io.github.dewfallapp.core.model.ChannelId
import io.github.dewfallapp.core.model.ChannelOverview
import io.github.dewfallapp.core.model.SubscribedChannel
import java.util.Locale
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.time.Instant

private val SUBSCRIBED_AT = Instant.parse("2026-11-22T12:00:00Z")

/** A made-up channel with a 24-character ID from [n]. */
private fun overview(name: String, n: Int = 0): ChannelOverview =
    ChannelOverview(
        SubscribedChannel(
            Channel(
                id = ChannelId("UC" + n.toString().padStart(22, '0')),
                name = name,
                avatars = emptyList(),
                description = "",
            ),
            SUBSCRIBED_AT,
        ),
        lastUpload = null,
    )

private fun names(channels: List<ChannelOverview>) = channels.map { it.subscription.channel.name }

/**
 * The order of the subscriptions spec's "The Channels screen lists the subscribed channels". The
 * locale is given, so the tests don't depend on the machine's.
 */
class ChannelOrderTest {
    @Test
    fun `case doesn't count`() {
        val channels =
            listOf(
                overview("orbit Hour"),
                overview("Kettle and Crumb"),
                overview("Tidewater Woodshop"),
                overview("Larkspur Lectures"),
            )

        assertEquals(
            listOf("Kettle and Crumb", "Larkspur Lectures", "orbit Hour", "Tidewater Woodshop"),
            names(sortedByName(channels, Locale.UK)),
        )
    }

    @Test
    fun `an accented letter sorts next to its plain one`() {
        val channels =
            listOf(
                overview("Zebra Hall"),
                overview("Élodie Bakes"),
                overview("Delta Notes"),
                overview("Falcon"),
            )

        assertEquals(
            listOf("Delta Notes", "Élodie Bakes", "Falcon", "Zebra Hall"),
            names(sortedByName(channels, Locale.UK)),
        )
    }

    @Test
    fun `equal names keep their order by channel ID`() {
        val channels =
            listOf(overview("Kettle", n = 3), overview("kettle", n = 1), overview("Kettle", n = 2))

        assertEquals(
            listOf(1, 2, 3).map { "UC" + it.toString().padStart(22, '0') },
            sortedByName(channels, Locale.UK).map { it.subscription.channel.id.value },
        )
    }
}
