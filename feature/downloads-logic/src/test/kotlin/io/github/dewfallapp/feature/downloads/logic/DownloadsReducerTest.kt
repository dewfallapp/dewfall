package io.github.dewfallapp.feature.downloads.logic

import io.github.dewfallapp.core.model.ChannelId
import io.github.dewfallapp.core.model.Download
import io.github.dewfallapp.core.model.DownloadFailure
import io.github.dewfallapp.core.model.DownloadProgress
import io.github.dewfallapp.core.model.DownloadStatus
import io.github.dewfallapp.core.model.VideoId
import io.github.dewfallapp.core.model.VideoKind
import io.github.dewfallapp.core.model.VideoSummary
import io.github.dewfallapp.core.mvi.Next
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.time.Duration.Companion.hours
import kotlin.time.Instant

private val MONDAY = Instant.parse("2026-11-23T09:00:00Z")

private fun video(n: Int) =
    VideoSummary(
        id = VideoId("video${n.toString().padStart(6, '0')}"),
        title = "Video $n",
        channelId = ChannelId("UCaaaaaaaaaaaaaaaaaaaaaa"),
        channelName = "Kettle and Crumb",
        duration = null,
        published = null,
        thumbnails = emptyList(),
        kind = VideoKind.VIDEO,
    )

/** A download of video [n], asked for [n] hours after Monday. */
private fun download(n: Int, status: DownloadStatus, finishedAfterHours: Int? = null) =
    Download(
        video = video(n),
        status = status,
        requestedAt = MONDAY + n.hours,
        finishedAt = finishedAfterHours?.let { MONDAY + it.hours },
    )

private val progress = DownloadProgress(420, 1_000)

class DownloadsReducerTest {
    private fun reduce(state: DownloadsState, intent: DownloadsIntent) =
        DownloadsReducer.reduce(state, intent)

    @Test
    fun `the screen starts loading`() {
        assertEquals(DownloadsList.Loading, DownloadsState().list)
    }

    @Test
    fun `no downloads says so`() {
        assertEquals(
            Next(DownloadsState(DownloadsList.NoDownloads)),
            reduce(DownloadsState(), DownloadsIntent.DownloadsRead(emptyList())),
        )
    }

    @Test
    fun `unfinished downloads keep the queue's order, finished ones the newest first`() {
        val running = download(1, DownloadStatus.Downloading(progress))
        val oldDone = download(2, DownloadStatus.Downloaded(9), finishedAfterHours = 10)
        val waiting = download(3, DownloadStatus.Waiting(progress))
        val newDone = download(4, DownloadStatus.Downloaded(9), finishedAfterHours = 20)
        val paused = download(5, DownloadStatus.Paused(progress))
        val failed = download(6, DownloadStatus.Failed(DownloadFailure.NO_SPACE))

        val next =
            reduce(
                DownloadsState(),
                DownloadsIntent.DownloadsRead(
                    listOf(running, oldDone, waiting, newDone, paused, failed),
                ),
            )

        assertEquals(
            Next(
                DownloadsState(
                    DownloadsList.Shown(
                        downloading = listOf(running, waiting, paused, failed),
                        onPhone = listOf(newDone, oldDone),
                    ),
                ),
            ),
            next,
        )
    }

    @Test
    fun `finished downloads that finished together keep the queue's order`() {
        val first = download(1, DownloadStatus.Downloaded(9), finishedAfterHours = 10)
        val second = download(2, DownloadStatus.Downloaded(9), finishedAfterHours = 10)

        val list = reduce(DownloadsState(), DownloadsIntent.DownloadsRead(listOf(first, second)))

        assertEquals(
            DownloadsList.Shown(downloading = emptyList(), onPhone = listOf(first, second)),
            list.state.list,
        )
    }

    @Test
    fun `only unfinished downloads leave the phone's section empty`() {
        val waiting = download(1, DownloadStatus.Waiting(progress))

        assertEquals(
            DownloadsList.Shown(downloading = listOf(waiting), onPhone = emptyList()),
            reduce(DownloadsState(), DownloadsIntent.DownloadsRead(listOf(waiting))).state.list,
        )
    }

    @Test
    fun `a failed read says so, and a later read shows the downloads`() {
        val unreadable = reduce(DownloadsState(), DownloadsIntent.ReadFailed)
        assertEquals(Next(DownloadsState(DownloadsList.Unreadable)), unreadable)

        val waiting = download(1, DownloadStatus.Waiting(progress))
        assertEquals(
            DownloadsList.Shown(listOf(waiting), emptyList()),
            reduce(unreadable.state, DownloadsIntent.DownloadsRead(listOf(waiting))).state.list,
        )
    }

    @Test
    fun `a new read replaces the old one`() {
        val shown =
            reduce(
                    DownloadsState(),
                    DownloadsIntent.DownloadsRead(
                        listOf(download(1, DownloadStatus.Waiting(progress))),
                    ),
                )
                .state

        assertEquals(
            DownloadsList.NoDownloads,
            reduce(shown, DownloadsIntent.DownloadsRead(emptyList())).state.list,
        )
    }

    @Test
    fun `taps change nothing until the phone answers`() {
        val state =
            DownloadsState(
                DownloadsList.Shown(
                    listOf(download(1, DownloadStatus.Waiting(progress))),
                    listOf(),
                ),
            )
        val taps =
            listOf(
                DownloadsIntent.Pause(video(1).id),
                DownloadsIntent.Resume(video(1)),
                DownloadsIntent.Remove(video(1).id, finished = false),
                DownloadsIntent.Remove(video(1).id, finished = true),
            )

        for (tap in taps) {
            assertEquals(Next(state), reduce(state, tap), "$tap")
        }
    }

    @Test
    fun `a removed finished download says so once`() {
        val state = DownloadsState(DownloadsList.NoDownloads)

        assertEquals(
            Next(state, listOf(DownloadsEffect.ShowRemoved)),
            reduce(state, DownloadsIntent.Removed),
        )
    }

    @Test
    fun `the space starts unread`() {
        assertEquals(BytesInUse.Reading, DownloadsState().bytesInUse)
    }

    @Test
    fun `a read of the space stores the bytes and changes nothing else`() {
        val state = DownloadsState(DownloadsList.NoDownloads)

        assertEquals(
            Next(DownloadsState(DownloadsList.NoDownloads, BytesInUse.Read(3_600))),
            reduce(state, DownloadsIntent.BytesInUseRead(3_600)),
        )
    }

    @Test
    fun `a failed read of the space shows no amount and changes nothing else`() {
        val state = DownloadsState(DownloadsList.NoDownloads, BytesInUse.Read(3_600))

        assertEquals(
            Next(DownloadsState(DownloadsList.NoDownloads, BytesInUse.Unreadable)),
            reduce(state, DownloadsIntent.BytesInUseUnreadable),
        )
    }

    @Test
    fun `a read of the list keeps the space`() {
        val waiting = download(1, DownloadStatus.Waiting(progress))
        val state = DownloadsState(bytesInUse = BytesInUse.Read(420))

        val read = reduce(state, DownloadsIntent.DownloadsRead(listOf(waiting))).state
        val failed = reduce(state, DownloadsIntent.ReadFailed).state

        assertEquals(BytesInUse.Read(420), read.bytesInUse)
        assertEquals(DownloadsState(DownloadsList.Unreadable, BytesInUse.Read(420)), failed)
    }
}
