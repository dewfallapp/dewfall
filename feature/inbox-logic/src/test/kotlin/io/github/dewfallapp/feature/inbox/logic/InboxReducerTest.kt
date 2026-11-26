package io.github.dewfallapp.feature.inbox.logic

import io.github.dewfallapp.core.model.ChannelId
import io.github.dewfallapp.core.model.Thumbnail
import io.github.dewfallapp.core.model.UploadsCheck
import io.github.dewfallapp.core.model.UploadsCheckFailure
import io.github.dewfallapp.core.model.VideoId
import io.github.dewfallapp.core.model.VideoKind
import io.github.dewfallapp.core.model.VideoSummary
import io.github.dewfallapp.core.mvi.Next
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
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
        val state = reduce(InboxState(), InboxIntent.Loaded(0, emptyMap(), emptyList()))

        assertEquals(InboxContent.NoChannels, state.content)
    }

    @Test
    fun `one channel and nothing new is caught up with 1 channel`() {
        val state = reduce(InboxState(), InboxIntent.Loaded(1, emptyMap(), emptyList()))

        assertEquals(InboxContent.CaughtUp(1), state.content)
    }

    @Test
    fun `four channels and nothing new is caught up with 4 channels`() {
        val state = reduce(InboxState(), InboxIntent.Loaded(4, emptyMap(), emptyList()))

        assertEquals(InboxContent.CaughtUp(4), state.content)
    }

    @Test
    fun `videos group by channel in the order each channel first appears`() {
        val state = reduce(InboxState(), InboxIntent.Loaded(2, emptyMap(), listOf(a1, b1, a2)))

        assertEquals(
            InboxContent.Videos(
                listOf(
                    InboxGroup(KETTLE, "Kettle and Crumb", emptyList(), listOf(a1, a2)),
                    InboxGroup(ORBIT, "Orbit Hour", emptyList(), listOf(b1)),
                ),
            ),
            state.content,
        )
    }

    @Test
    fun `a card has its channel's saved avatars, or none`() {
        val avatars =
            listOf(Thumbnail("https://images.example.invalid/kettle.jpg", width = 88, height = 88))
        val state =
            reduce(InboxState(), InboxIntent.Loaded(2, mapOf(KETTLE to avatars), listOf(a1, b1)))

        val groups = (state.content as InboxContent.Videos).groups
        assertEquals(avatars, groups.single { it.channelId == KETTLE }.channelAvatars)
        assertEquals(emptyList(), groups.single { it.channelId == ORBIT }.channelAvatars)
    }

    @Test
    fun `a card takes its name from its newest video`() {
        val renamed = a2.copy(channelName = "Kettle & Crumb")
        val state = reduce(InboxState(), InboxIntent.Loaded(1, emptyMap(), listOf(a1, renamed)))

        val group = (state.content as InboxContent.Videos).groups.single()
        assertEquals("Kettle and Crumb", group.channelName)
    }

    @Test
    fun `videos win over a count of 0 while the two reads disagree`() {
        val state = reduce(InboxState(), InboxIntent.Loaded(0, emptyMap(), listOf(b1)))

        assertEquals(
            InboxContent.Videos(listOf(InboxGroup(ORBIT, "Orbit Hour", emptyList(), listOf(b1)))),
            state.content,
        )
    }

    @Test
    fun `a new answer replaces the last one`() {
        val shown = reduce(InboxState(), InboxIntent.Loaded(2, emptyMap(), listOf(a1, b1)))

        val state = reduce(shown, InboxIntent.Loaded(2, emptyMap(), emptyList()))

        assertEquals(InboxContent.CaughtUp(2), state.content)
    }

    @Test
    fun `a read that failed shows unreadable, and a later answer replaces it`() {
        val failed = reduce(InboxState(InboxContent.CaughtUp(1)), InboxIntent.ReadFailed)
        assertEquals(InboxContent.Unreadable, failed.content)

        val state = reduce(failed, InboxIntent.Loaded(1, emptyMap(), emptyList()))

        assertEquals(InboxContent.CaughtUp(1), state.content)
    }

    private val running = UploadsCheck(running = true)
    private val offline = UploadsCheck(failure = UploadsCheckFailure.CANT_REACH_YOUTUBE)
    private val videos =
        InboxContent.Videos(listOf(InboxGroup(KETTLE, "Kettle and Crumb", emptyList(), listOf(a1))))

    @Test
    fun `a check's state is copied in, and the content stays`() {
        val before = InboxState(content = InboxContent.CaughtUp(2))

        val state = reduce(before, InboxIntent.CheckChanged(running))

        assertEquals(InboxState(InboxContent.CaughtUp(2), running), state)
    }

    @Test
    fun `a new answer keeps the check`() {
        val before = InboxState(content = InboxContent.CaughtUp(2), check = running)

        val state = reduce(before, InboxIntent.Loaded(2, emptyMap(), listOf(a1)))

        assertEquals(running, state.check)
        assertEquals(videos, state.content)
    }

    @Test
    fun `a failed read keeps the check`() {
        val state = reduce(InboxState(check = offline), InboxIntent.ReadFailed)

        assertEquals(InboxState(InboxContent.Unreadable, offline), state)
    }

    @Test
    fun `a tap on check for new videos changes nothing in the state`() {
        val before = InboxState(content = videos, check = offline)

        assertEquals(before, reduce(before, InboxIntent.CheckForNewVideos))
    }

    @Test
    fun `only cards and caught up offer a check`() {
        assertTrue(InboxState(content = videos).offersCheck)
        assertTrue(InboxState(content = InboxContent.CaughtUp(1)).offersCheck)
        assertFalse(InboxState(content = InboxContent.NoChannels).offersCheck)
        assertFalse(InboxState(content = InboxContent.Unreadable).offersCheck)
        assertFalse(InboxState(content = null).offersCheck)
    }

    @Test
    fun `the line reads checking while a check runs`() {
        assertTrue(InboxState(content = videos, check = running).checking)
        assertFalse(InboxState(content = videos, check = offline).checking)
    }

    @Test
    fun `the banner shows the last failure where a check is offered`() {
        assertEquals(
            UploadsCheckFailure.CANT_REACH_YOUTUBE,
            InboxState(content = videos, check = offline).banner,
        )
        assertEquals(
            UploadsCheckFailure.CANT_REACH_YOUTUBE,
            InboxState(content = InboxContent.CaughtUp(1), check = offline).banner,
        )
        assertNull(InboxState(content = InboxContent.NoChannels, check = offline).banner)
        assertNull(InboxState(content = InboxContent.Unreadable, check = offline).banner)
        assertNull(InboxState(content = null, check = offline).banner)
    }

    @Test
    fun `no banner while a check runs or after one that worked`() {
        val again = UploadsCheck(running = true, failure = UploadsCheckFailure.YOUTUBE_NOT_LOADING)
        assertNull(InboxState(content = videos, check = again).banner)
        assertNull(InboxState(content = videos, check = UploadsCheck()).banner)
    }
}
