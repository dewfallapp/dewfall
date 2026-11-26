package io.github.dewfallapp.feature.inbox.logic

import io.github.dewfallapp.core.model.ChannelId
import io.github.dewfallapp.core.model.DownloadProgress
import io.github.dewfallapp.core.model.DownloadStatus
import io.github.dewfallapp.core.model.VideoId
import io.github.dewfallapp.core.model.VideoKind
import io.github.dewfallapp.core.model.VideoSummary
import io.github.dewfallapp.core.mvi.Next
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

private val VIDEO =
    VideoSummary(
        id = VideoId("aaaaaaaaaa1"),
        title = "Title of aaaaaaaaaa1",
        channelId = ChannelId("UCaaaaaaaaaaaaaaaaaaaaaa"),
        channelName = "Kettle and Crumb",
        duration = null,
        published = null,
        thumbnails = emptyList(),
        kind = VideoKind.VIDEO,
    )

private val OTHER = VideoId("bbbbbbbbbb1")

private val DOWNLOADING = DownloadStatus.Downloading(DownloadProgress(42, 100))

/** How the inbox's state follows the videos' downloads. */
class InboxDownloadsTest {
    private fun reduce(state: InboxState, intent: InboxIntent): InboxState {
        val next = InboxReducer.reduce(state, intent)
        assertEquals(Next(next.state), next)
        return next.state
    }

    @Test
    fun `rows show no download control before the phone answers`() {
        assertNull(InboxState().downloadOf(VIDEO.id))
    }

    @Test
    fun `the phone's answer gives each row its status, and a video with none is not downloaded`() {
        val state =
            reduce(InboxState(), InboxIntent.DownloadsChanged(mapOf(VIDEO.id to DOWNLOADING)))

        assertEquals(DOWNLOADING, state.downloadOf(VIDEO.id))
        assertEquals(DownloadStatus.NotDownloaded, state.downloadOf(OTHER))
    }

    @Test
    fun `a read that throws leaves the rows without a control and the rest as it was`() {
        val content = InboxContent.CaughtUp(channelCount = 2)
        val state = InboxState(content = content, downloads = mapOf(VIDEO.id to DOWNLOADING))

        val after = reduce(state, InboxIntent.DownloadsUnreadable)

        assertNull(after.downloadOf(VIDEO.id))
        assertEquals(content, after.content)
    }

    @Test
    fun `the download taps change nothing until the phone answers`() {
        val state = InboxState(downloads = emptyMap())

        assertEquals(state, reduce(state, InboxIntent.Download(VIDEO)))
        assertEquals(state, reduce(state, InboxIntent.PauseDownload(VIDEO.id)))
    }
}
