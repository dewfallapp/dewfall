package io.github.dewfallapp.feature.player.logic

import io.github.dewfallapp.core.model.ChannelId
import io.github.dewfallapp.core.model.DownloadFailure
import io.github.dewfallapp.core.model.DownloadProgress
import io.github.dewfallapp.core.model.DownloadStatus
import io.github.dewfallapp.core.model.YouTubeError
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

private fun page(isLive: Boolean) =
    VideoPage(
        title = "How reading rooms were lit before electric lamps",
        channelId = ChannelId("UCLA_DiR1FfKNvjuUpBHmylQ"),
        channelName = "Larkspur Lectures",
        channelAvatars = emptyList(),
        description = "",
        published = null,
        isLive = isLive,
    )

class DownloadChipTest {
    @Test
    fun `each status has its chip`() {
        val half = DownloadProgress(downloadedBytes = 50, totalBytes = 100)
        val unknown = DownloadProgress(downloadedBytes = 50, totalBytes = null)

        assertEquals(DownloadChip.NotDownloaded, chipOf(DownloadStatus.NotDownloaded))
        assertEquals(DownloadChip.Waiting, chipOf(DownloadStatus.Waiting(half)))
        assertEquals(DownloadChip.Downloading(50), chipOf(DownloadStatus.Downloading(half)))
        assertEquals(DownloadChip.Downloading(null), chipOf(DownloadStatus.Downloading(unknown)))
        assertEquals(DownloadChip.Paused(50), chipOf(DownloadStatus.Paused(half)))
        assertEquals(DownloadChip.Paused(null), chipOf(DownloadStatus.Paused(unknown)))
        assertEquals(DownloadChip.Downloaded, chipOf(DownloadStatus.Downloaded(bytes = 100)))
    }

    @Test
    fun `only a full phone gets its own failure`() {
        assertEquals(
            DownloadChip.Failed(noSpace = true),
            chipOf(DownloadStatus.Failed(DownloadFailure.NO_SPACE)),
        )
        for (reason in listOf(DownloadFailure.UNAVAILABLE, DownloadFailure.YOUTUBE)) {
            assertEquals(
                DownloadChip.Failed(noSpace = false),
                chipOf(DownloadStatus.Failed(reason)),
            )
        }
    }

    @Test
    fun `the chip shows once the details show, unless the video is live`() {
        val chip = DownloadChip.NotDownloaded

        assertEquals(
            chip,
            PlayerState(details = DetailsArea.Shown(page(isLive = false)), download = chip)
                .shownDownload,
        )
        assertNull(
            PlayerState(details = DetailsArea.Shown(page(isLive = true)), download = chip)
                .shownDownload,
        )
        assertNull(PlayerState(details = DetailsArea.Loading(1), download = chip).shownDownload)
        assertNull(
            PlayerState(
                    details = DetailsArea.Failed(1, YouTubeError.NETWORK),
                    download = chip,
                )
                .shownDownload,
        )
    }

    @Test
    fun `no chip shows while the download is unknown or unreadable`() {
        assertNull(PlayerState(details = DetailsArea.Shown(page(isLive = false))).shownDownload)
    }
}
