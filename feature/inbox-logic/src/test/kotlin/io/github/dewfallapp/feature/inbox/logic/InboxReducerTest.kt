package io.github.dewfallapp.feature.inbox.logic

import io.github.dewfallapp.core.model.ChannelId
import io.github.dewfallapp.core.model.VideoId
import io.github.dewfallapp.core.model.VideoKind
import io.github.dewfallapp.core.model.VideoSummary
import io.github.dewfallapp.core.mvi.Next
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.time.Duration.Companion.minutes

private val KETTLE = ChannelId("UCaaaaaaaaaaaaaaaaaaaaaa")
private val ORBIT = ChannelId("UCbbbbbbbbbbbbbbbbbbbbbb")

private fun video(id: String, channelId: ChannelId, channelName: String) =
    VideoSummary(
        id = VideoId(id),
        title = "Title of $id",
        channelId = channelId,
        channelName = channelName,
        duration = 12.minutes,
        published = null,
        thumbnails = emptyList(),
        kind = VideoKind.VIDEO,
    )

class InboxReducerTest {
    private fun reduce(state: InboxState, intent: InboxIntent): InboxState {
        val next = InboxReducer.reduce(state, intent)
        assertEquals(Next(next.state), next)
        return next.state
    }

    private val a1 = video("aaaaaaaaaa1", KETTLE, "Kettle and Crumb")
    private val a2 = video("aaaaaaaaaa2", KETTLE, "Kettle and Crumb")
    private val b1 = video("bbbbbbbbbb1", ORBIT, "Orbit Hour")

    @Test
    fun `the inbox starts with no answer`() {
        assertNull(InboxState().content)
    }

    @Test
    fun `no subscriptions show no channels yet`() {
        val state = reduce(InboxState(), InboxIntent.Loaded(0, emptyList()))

        assertEquals(InboxContent.NoChannels, state.content)
    }

    @Test
    fun `one channel and nothing new is caught up with 1 channel`() {
        val state = reduce(InboxState(), InboxIntent.Loaded(1, emptyList()))

        assertEquals(InboxContent.CaughtUp(1), state.content)
    }

    @Test
    fun `four channels and nothing new is caught up with 4 channels`() {
        val state = reduce(InboxState(), InboxIntent.Loaded(4, emptyList()))

        assertEquals(InboxContent.CaughtUp(4), state.content)
    }

    @Test
    fun `videos group by channel in the order each channel first appears`() {
        val state = reduce(InboxState(), InboxIntent.Loaded(2, listOf(a1, b1, a2)))

        assertEquals(
            InboxContent.Videos(
                listOf(
                    InboxGroup(KETTLE, "Kettle and Crumb", listOf(a1, a2)),
                    InboxGroup(ORBIT, "Orbit Hour", listOf(b1)),
                ),
            ),
            state.content,
        )
    }

    @Test
    fun `a card takes its name from its newest video`() {
        val renamed = a2.copy(channelName = "Kettle & Crumb")
        val state = reduce(InboxState(), InboxIntent.Loaded(1, listOf(a1, renamed)))

        val group = (state.content as InboxContent.Videos).groups.single()
        assertEquals("Kettle and Crumb", group.channelName)
    }

    @Test
    fun `videos win over a count of 0 while the two reads disagree`() {
        val state = reduce(InboxState(), InboxIntent.Loaded(0, listOf(b1)))

        assertEquals(
            InboxContent.Videos(listOf(InboxGroup(ORBIT, "Orbit Hour", listOf(b1)))),
            state.content,
        )
    }

    @Test
    fun `a new answer replaces the last one`() {
        val shown = reduce(InboxState(), InboxIntent.Loaded(2, listOf(a1, b1)))

        val state = reduce(shown, InboxIntent.Loaded(2, emptyList()))

        assertEquals(InboxContent.CaughtUp(2), state.content)
    }

    @Test
    fun `a read that failed shows unreadable, and a later answer replaces it`() {
        val failed = reduce(InboxState(InboxContent.CaughtUp(1)), InboxIntent.ReadFailed)
        assertEquals(InboxContent.Unreadable, failed.content)

        val state = reduce(failed, InboxIntent.Loaded(1, emptyList()))

        assertEquals(InboxContent.CaughtUp(1), state.content)
    }
}
