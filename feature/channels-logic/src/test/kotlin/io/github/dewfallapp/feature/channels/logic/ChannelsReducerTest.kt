package io.github.dewfallapp.feature.channels.logic

import io.github.dewfallapp.core.model.Channel
import io.github.dewfallapp.core.model.ChannelId
import io.github.dewfallapp.core.model.ChannelOverview
import io.github.dewfallapp.core.model.PublishedTime
import io.github.dewfallapp.core.model.SubscribedChannel
import io.github.dewfallapp.core.mvi.Next
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.time.Instant

private val MONDAY = Instant.parse("2026-11-16T09:00:00Z")

private fun overview(id: String, name: String, lastUpload: PublishedTime? = null) =
    ChannelOverview(
        SubscribedChannel(
            Channel(id = ChannelId(id), name = name, avatars = emptyList(), description = ""),
            subscribedAt = MONDAY,
        ),
        lastUpload,
    )

class ChannelsReducerTest {
    private fun reduce(state: ChannelsState, intent: ChannelsIntent): ChannelsState {
        val next = ChannelsReducer.reduce(state, intent)
        assertEquals(Next(next.state), next)
        return next.state
    }

    private val kettle =
        overview("UCaaaaaaaaaaaaaaaaaaaaaa", "Kettle and Crumb", PublishedTime(MONDAY, false))
    private val orbit = overview("UCbbbbbbbbbbbbbbbbbbbbbb", "Orbit Hour")

    @Test
    fun `the screen starts loading`() {
        assertEquals(ChannelsList.Loading, ChannelsState().list)
    }

    @Test
    fun `an empty read says there are no channels`() {
        val state = reduce(ChannelsState(), ChannelsIntent.ChannelsRead(emptyList()))

        assertEquals(ChannelsState(ChannelsList.NoChannels), state)
    }

    @Test
    fun `a read shows the channels in its order`() {
        val state = reduce(ChannelsState(), ChannelsIntent.ChannelsRead(listOf(orbit, kettle)))

        assertEquals(ChannelsState(ChannelsList.Shown(listOf(orbit, kettle))), state)
    }

    @Test
    fun `a new read replaces the old one`() {
        val first = reduce(ChannelsState(), ChannelsIntent.ChannelsRead(listOf(kettle, orbit)))

        val state = reduce(first, ChannelsIntent.ChannelsRead(listOf(kettle)))

        assertEquals(ChannelsState(ChannelsList.Shown(listOf(kettle))), state)
    }

    @Test
    fun `channels after none show`() {
        val none = reduce(ChannelsState(), ChannelsIntent.ChannelsRead(emptyList()))

        val state = reduce(none, ChannelsIntent.ChannelsRead(listOf(orbit)))

        assertEquals(ChannelsState(ChannelsList.Shown(listOf(orbit))), state)
    }

    @Test
    fun `the last channel going away says there are none`() {
        val shown = reduce(ChannelsState(), ChannelsIntent.ChannelsRead(listOf(orbit)))

        val state = reduce(shown, ChannelsIntent.ChannelsRead(emptyList()))

        assertEquals(ChannelsState(ChannelsList.NoChannels), state)
    }

    @Test
    fun `a failed read says the channels can't be shown`() {
        val shown = reduce(ChannelsState(), ChannelsIntent.ChannelsRead(listOf(orbit)))

        val state = reduce(shown, ChannelsIntent.ReadFailed)

        assertEquals(ChannelsState(ChannelsList.Unreadable), state)
    }

    @Test
    fun `a read after a failed one shows the channels`() {
        val failed = reduce(ChannelsState(), ChannelsIntent.ReadFailed)

        val state = reduce(failed, ChannelsIntent.ChannelsRead(listOf(kettle)))

        assertEquals(ChannelsState(ChannelsList.Shown(listOf(kettle))), state)
    }
}
