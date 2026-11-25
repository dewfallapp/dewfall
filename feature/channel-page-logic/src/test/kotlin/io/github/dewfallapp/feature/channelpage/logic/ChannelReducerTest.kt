package io.github.dewfallapp.feature.channelpage.logic

import io.github.dewfallapp.core.model.Channel
import io.github.dewfallapp.core.model.ChannelId
import io.github.dewfallapp.core.model.ChannelUploads
import io.github.dewfallapp.core.model.PublishedTime
import io.github.dewfallapp.core.model.Thumbnail
import io.github.dewfallapp.core.model.VideoId
import io.github.dewfallapp.core.model.VideoKind
import io.github.dewfallapp.core.model.VideoSummary
import io.github.dewfallapp.core.model.YouTubeError
import io.github.dewfallapp.core.model.YouTubeResult
import io.github.dewfallapp.core.mvi.Next
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Instant

// Made-up IDs in a channel ID's form, never real channels'.
private val OPENED = ChannelId("UC" + "0".repeat(21) + "1")
private val MOVED_TO = ChannelId("UC" + "0".repeat(21) + "2")
private val OTHER = ChannelId("UC" + "0".repeat(21) + "3")

private fun channel(id: ChannelId = OPENED, name: String = "Kettle and Crumb") =
    Channel(
        id = id,
        name = name,
        avatars = emptyList(),
        description = "Bread, fermentation and the history of everyday food.",
    )

private fun video(id: String, kind: VideoKind = VideoKind.VIDEO) =
    VideoSummary(
        id = VideoId(id),
        title = "Video $id",
        channelId = OPENED,
        channelName = "Kettle and Crumb",
        duration = 54.minutes,
        published = PublishedTime(Instant.parse("2026-11-21T08:00:00Z"), isApproximate = false),
        thumbnails =
            listOf(Thumbnail("https://images.example.invalid/$id.jpg", width = 336, height = 188)),
        kind = kind,
    )

private fun row(id: String, kind: VideoKind = VideoKind.VIDEO): UploadRow {
    val video = video(id, kind)
    return UploadRow(
        video.id,
        video.title,
        video.duration,
        video.kind,
        video.published,
        video.thumbnails,
    )
}

private fun success(channel: Channel = channel(), videos: List<VideoSummary> = emptyList()) =
    YouTubeResult.Success(ChannelUploads(channel, videos))

class ChannelReducerTest {
    private fun reduce(state: ChannelState, intent: ChannelIntent): ChannelState {
        val next = ChannelReducer.reduce(state, intent)
        assertEquals(Next(next.state), next)
        return next.state
    }

    private fun ChannelState.after(vararg intents: ChannelIntent): ChannelState =
        intents.fold(this) { state, intent -> reduce(state, intent) }

    private fun opened() = ChannelState(openedId = OPENED)

    private fun loaded(videos: List<VideoSummary> = emptyList()) =
        opened().after(ChannelIntent.LoadFinished(1, success(videos = videos)))

    private fun failedWith(error: YouTubeError) =
        opened().after(ChannelIntent.LoadFinished(1, YouTubeResult.Failure(error)))

    private fun read(id: ChannelId, stored: Channel?) = ChannelIntent.SubscriptionRead(id, stored)

    private fun loadedMoved() =
        opened().after(ChannelIntent.LoadFinished(1, success(channel = channel(MOVED_TO))))

    @Test
    fun `the page opens loading load 1, with no card`() {
        val state = opened()

        assertEquals(LoadArea.Loading(1), state.load)
        assertEquals(Subscription.Unknown, state.subscription)
        assertFalse(state.showsCard)
    }

    @Test
    fun `the running load's answer shows the channel and its rows in the layer's order`() {
        val state = loaded(listOf(video("aaaaaaaaaa1"), video("aaaaaaaaaa2", VideoKind.LIVE)))

        assertEquals(
            LoadArea.Loaded(1, listOf(row("aaaaaaaaaa1"), row("aaaaaaaaaa2", VideoKind.LIVE))),
            state.load,
        )
        assertEquals(channel(), state.shownChannel)
        assertNull(state.answeredId)
    }

    @Test
    fun `Shorts and repeated videos are left out`() {
        val state =
            loaded(
                listOf(
                    video("aaaaaaaaaa1"),
                    video("aaaaaaaaaa2", VideoKind.SHORT),
                    video("aaaaaaaaaa1"),
                ),
            )

        assertEquals(LoadArea.Loaded(1, listOf(row("aaaaaaaaaa1"))), state.load)
    }

    @Test
    fun `a channel with no videos loads with no rows`() {
        assertEquals(LoadArea.Loaded(1, emptyList()), loaded().load)
    }

    @Test
    fun `an answer for another load changes nothing`() {
        val state = opened()

        assertEquals(state, reduce(state, ChannelIntent.LoadFinished(2, success())))
        val shown = loaded()
        assertEquals(shown, reduce(shown, ChannelIntent.LoadFinished(1, success())))
    }

    @Test
    fun `a failure keeps its error`() {
        assertEquals(
            LoadArea.Failed(1, YouTubeError.CHANNEL_CLOSED),
            failedWith(YouTubeError.CHANNEL_CLOSED).load,
        )
    }

    @Test
    fun `Try again after a failure that offers it starts the next load`() {
        for (error in listOf(YouTubeError.NETWORK, YouTubeError.BLOCKED, YouTubeError.BROKEN)) {
            assertEquals(
                LoadArea.Loading(2),
                failedWith(error).after(ChannelIntent.TryAgain).load,
            )
        }
    }

    @Test
    fun `Try again after a failure that doesn't offer it, or with no failure, changes nothing`() {
        val closed = failedWith(YouTubeError.CHANNEL_CLOSED)
        assertEquals(closed, reduce(closed, ChannelIntent.TryAgain))
        assertEquals(opened(), reduce(opened(), ChannelIntent.TryAgain))
        assertEquals(loaded(), reduce(loaded(), ChannelIntent.TryAgain))
    }

    @Test
    fun `the failed load's late answer is dropped after Try again`() {
        val state =
            failedWith(YouTubeError.NETWORK)
                .after(ChannelIntent.TryAgain, ChannelIntent.LoadFinished(1, success()))

        assertEquals(LoadArea.Loading(2), state.load)
    }

    @Test
    fun `the card shows the subscribed channel once the channel is loaded and read`() {
        val state = loaded().after(read(OPENED, channel()))

        assertEquals(Subscription.Subscribed(channel()), state.subscription)
        assertTrue(state.showsCard)
        assertEquals(listOf(OPENED), state.subscribedIds)
    }

    @Test
    fun `the card says not subscribed when the database keeps nothing`() {
        val state = loaded().after(read(OPENED, null))

        assertEquals(Subscription.NotSubscribed, state.subscription)
        assertTrue(state.showsCard)
        assertEquals(emptyList(), state.subscribedIds)
    }

    @Test
    fun `no card while the channel loads, even when the database answered`() {
        assertFalse(opened().after(read(OPENED, null)).showsCard)
    }

    @Test
    fun `no card before the database answers, even when the channel loaded`() {
        assertFalse(loaded().showsCard)
    }

    @Test
    fun `a read for an ID that isn't the page's is ignored`() {
        val state = loaded()

        assertEquals(state, reduce(state, read(OTHER, channel(OTHER))))
    }

    @Test
    fun `a followed channel shows its kept channel and card when the load fails`() {
        val kept = channel(name = "Kettle and Crumb, as kept")
        val state = failedWith(YouTubeError.NETWORK).after(read(OPENED, kept))

        assertEquals(kept, state.shownChannel)
        assertEquals(Subscription.Subscribed(kept), state.subscription)
        assertTrue(state.showsCard)
        assertEquals(LoadArea.Failed(1, YouTubeError.NETWORK), state.load)
    }

    @Test
    fun `a followed channel shows no channel and no card while it loads`() {
        val state = opened().after(read(OPENED, channel()))

        assertNull(state.shownChannel)
        assertFalse(state.showsCard)
    }

    @Test
    fun `a kept channel read before the load fails shows once it fails`() {
        val kept = channel(name = "Kettle and Crumb, as kept")
        val state =
            opened()
                .after(
                    read(OPENED, kept),
                    ChannelIntent.LoadFinished(1, YouTubeResult.Failure(YouTubeError.NETWORK)),
                )

        assertEquals(kept, state.shownChannel)
        assertTrue(state.showsCard)
    }

    @Test
    fun `Try again keeps a followed channel and its card shown while it loads again`() {
        val kept = channel()
        val state =
            failedWith(YouTubeError.NETWORK).after(read(OPENED, kept), ChannelIntent.TryAgain)

        assertEquals(LoadArea.Loading(2), state.load)
        assertEquals(kept, state.shownChannel)
        assertTrue(state.showsCard)
    }

    @Test
    fun `a channel not followed shows no card when the load fails`() {
        val state = failedWith(YouTubeError.NETWORK).after(read(OPENED, null))

        assertNull(state.shownChannel)
        assertFalse(state.showsCard)
    }

    @Test
    fun `the loaded channel stays shown when the database's channel differs`() {
        val state = loaded().after(read(OPENED, channel(name = "An old name")))

        assertEquals(channel(), state.shownChannel)
    }

    @Test
    fun `unsubscribing offline keeps the channel and card, and Subscribe can follow it again`() {
        val kept = channel()
        val offline = failedWith(YouTubeError.NETWORK).after(read(OPENED, kept))

        val unsubscribed =
            offline.after(
                ChannelIntent.UnsubscribeTapped,
                ChannelIntent.UnsubscribeConfirmed,
                read(OPENED, null),
            )

        assertEquals(kept, unsubscribed.shownChannel)
        assertEquals(Subscription.NotSubscribed, unsubscribed.subscription)
        assertTrue(unsubscribed.showsCard)

        val again = unsubscribed.after(ChannelIntent.Subscribe, read(OPENED, kept))
        assertEquals(Subscription.Subscribed(kept), again.subscription)
        assertEquals(kept, again.shownChannel)
    }

    @Test
    fun `a moved channel adds its new ID and hides the card until that ID is read`() {
        val beforeLoad = opened().after(read(OPENED, null))

        val moved =
            beforeLoad.after(ChannelIntent.LoadFinished(1, success(channel = channel(MOVED_TO))))

        assertEquals(MOVED_TO, moved.answeredId)
        assertEquals(listOf(MOVED_TO, OPENED), moved.pageIds)
        assertEquals(Subscription.Unknown, moved.subscription)
        assertFalse(moved.showsCard)

        val read = moved.after(read(MOVED_TO, null))
        assertEquals(Subscription.NotSubscribed, read.subscription)
        assertTrue(read.showsCard)
    }

    @Test
    fun `a subscription under the old ID shows as subscribed and is the one to remove`() {
        val kept = channel(OPENED, name = "Kettle and Crumb, as kept")
        val state =
            opened()
                .after(
                    read(OPENED, kept),
                    ChannelIntent.LoadFinished(1, success(channel = channel(MOVED_TO))),
                    read(MOVED_TO, null),
                )

        assertEquals(Subscription.Subscribed(kept), state.subscription)
        assertEquals(listOf(OPENED), state.subscribedIds)
        assertEquals(channel(MOVED_TO), state.shownChannel)
    }

    @Test
    fun `subscriptions under both IDs are both removed, the answered one shown first`() {
        val state =
            loadedMoved().after(read(OPENED, channel(OPENED)), read(MOVED_TO, channel(MOVED_TO)))

        assertEquals(Subscription.Subscribed(channel(MOVED_TO)), state.subscription)
        assertEquals(listOf(MOVED_TO, OPENED), state.subscribedIds)
    }

    @Test
    fun `Subscribe changes no state`() {
        val state = loaded().after(read(OPENED, null))

        assertEquals(state, reduce(state, ChannelIntent.Subscribe))
    }

    @Test
    fun `Unsubscribe opens the dialog, and confirming or cancelling closes it`() {
        val subscribed = loaded().after(read(OPENED, channel()))

        val asking = subscribed.after(ChannelIntent.UnsubscribeTapped)
        assertTrue(asking.confirmingUnsubscribe)
        assertFalse(asking.after(ChannelIntent.UnsubscribeConfirmed).confirmingUnsubscribe)
        assertFalse(asking.after(ChannelIntent.UnsubscribeCancelled).confirmingUnsubscribe)
        assertEquals(subscribed, asking.after(ChannelIntent.UnsubscribeCancelled))
    }

    @Test
    fun `Unsubscribe opens no dialog unless the card says subscribed`() {
        val notSubscribed = loaded().after(read(OPENED, null))
        assertEquals(notSubscribed, reduce(notSubscribed, ChannelIntent.UnsubscribeTapped))
        assertEquals(opened(), reduce(opened(), ChannelIntent.UnsubscribeTapped))
    }

    @Test
    fun `the description folds and unfolds`() {
        val unfolded = loaded().after(ChannelIntent.DescriptionToggled)

        assertTrue(unfolded.descriptionExpanded)
        assertFalse(unfolded.after(ChannelIntent.DescriptionToggled).descriptionExpanded)
    }
}
