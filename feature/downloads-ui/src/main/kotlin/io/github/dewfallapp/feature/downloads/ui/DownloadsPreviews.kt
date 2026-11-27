package io.github.dewfallapp.feature.downloads.ui

import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import io.github.dewfallapp.core.designsystem.theme.DewfallTheme
import io.github.dewfallapp.core.model.ChannelId
import io.github.dewfallapp.core.model.Download
import io.github.dewfallapp.core.model.DownloadFailure
import io.github.dewfallapp.core.model.DownloadProgress
import io.github.dewfallapp.core.model.DownloadStatus
import io.github.dewfallapp.core.model.VideoId
import io.github.dewfallapp.core.model.VideoKind
import io.github.dewfallapp.core.model.VideoSummary
import io.github.dewfallapp.core.ui.PreviewImages
import io.github.dewfallapp.core.ui.previewImageLinks
import io.github.dewfallapp.feature.downloads.logic.BytesInUse
import io.github.dewfallapp.feature.downloads.logic.DownloadsIntent
import io.github.dewfallapp.feature.downloads.logic.DownloadsList
import io.github.dewfallapp.feature.downloads.logic.DownloadsReducer
import io.github.dewfallapp.feature.downloads.logic.DownloadsState
import kotlin.time.Duration
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Instant

// Previews with made-up videos and channels from the design's frames, never real ones. The
// @PreviewTest wrappers in src/screenshotTest carry their @Preview annotations and render them as
// screenshot tests. They use Dewfall's own palette, so the images don't depend on a wallpaper,
// except the dynamic color previews, whose wrappers name a fixed one. Image links are on a made-up
// host, and PreviewImages draws each as a plain fill, so screenshots never load or show a real
// image. See docs/testing.md#screenshot-tests.

private val previewAskedAt = Instant.parse("2027-03-15T07:30:00Z")

/** A video ID is 11 characters. */
private const val VIDEO_ID_LENGTH = 11

/** A channel ID is "UC" and 22 more characters. */
private const val CHANNEL_ID_TAIL = 22

private fun download(
    n: Int,
    title: String,
    channelName: String,
    duration: Duration,
    status: DownloadStatus,
) =
    Download(
        video =
            VideoSummary(
                id = VideoId("v" + n.toString().padStart(VIDEO_ID_LENGTH - 1, '0')),
                title = title,
                channelId = ChannelId("UC" + n.toString().padStart(CHANNEL_ID_TAIL, '0')),
                channelName = channelName,
                duration = duration,
                published = null,
                thumbnails = previewImageLinks("video-$n"),
                kind = VideoKind.VIDEO,
            ),
        status = status,
        requestedAt = previewAskedAt + n.hours,
        finishedAt =
            if (status is DownloadStatus.Downloaded) previewAskedAt + (20 - n).hours else null,
    )

private const val MB = 1_000_000L

/**
 * The "Downloads" frame's running download and finished ones, with a row for every other state of a
 * download that isn't finished, in the repository's order.
 */
private val previewDownloads =
    listOf(
        download(
            1,
            "Why the night sky is dark, and what that says about the age of the universe",
            "Orbit Hour",
            92.minutes,
            DownloadStatus.Downloading(DownloadProgress(345 * MB, 820 * MB)),
        ),
        download(
            2,
            "Restoring a joiner's workbench older than the house",
            "Tidewater Woodshop",
            108.minutes,
            DownloadStatus.Downloaded(940 * MB),
        ),
        download(
            3,
            "Wild yeasts and the first week of a sourdough starter",
            "Kettle and Crumb",
            54.minutes,
            DownloadStatus.Waiting(DownloadProgress(0, null)),
        ),
        download(
            4,
            "Mosses, lichens and the slow chemistry of rock",
            "Larkspur Lectures",
            42.minutes,
            DownloadStatus.Paused(DownloadProgress(160 * MB, 380 * MB)),
        ),
        download(
            5,
            "How tea was blended before the tea bag",
            "Kettle and Crumb",
            18.minutes,
            DownloadStatus.Failed(DownloadFailure.NO_SPACE),
        ),
        download(
            6,
            "Sharpening hand planes on natural waterstones",
            "Tidewater Woodshop",
            28.minutes,
            DownloadStatus.Failed(DownloadFailure.UNAVAILABLE),
        ),
        download(
            7,
            "How reading rooms were lit before electric lamps",
            "Larkspur Lectures",
            74.minutes,
            DownloadStatus.Downloaded(1_234 * MB),
        ),
    )

/** The space the preview's downloads keep: the running, paused and finished ones' bytes. */
private const val PREVIEW_BYTES_IN_USE = (345 + 160 + 940 + 1_234) * MB

private fun read(downloads: List<Download>, bytesInUse: Long): DownloadsState {
    val listed =
        DownloadsReducer.reduce(DownloadsState(), DownloadsIntent.DownloadsRead(downloads)).state
    return DownloadsReducer.reduce(listed, DownloadsIntent.BytesInUseRead(bytesInUse)).state
}

/** The list can't be read, and neither can the space, as with a damaged database. */
private val unreadable = DownloadsState(DownloadsList.Unreadable, BytesInUse.Unreadable)

@Composable
private fun Preview(state: DownloadsState, dynamicColor: Boolean = false) {
    DewfallTheme(dynamicColor = dynamicColor) {
        PreviewImages {
            DownloadsScreen(
                state = state,
                snackbarHostState = remember { SnackbarHostState() },
                actions = DownloadsActions(onOpenVideo = {}, onOpenSearch = {}, onIntent = {}),
            )
        }
    }
}

@Composable
internal fun DownloadsPreview() {
    Preview(read(previewDownloads, PREVIEW_BYTES_IN_USE))
}

@Composable
internal fun DownloadsDynamicPreview() {
    Preview(read(previewDownloads, PREVIEW_BYTES_IN_USE), dynamicColor = true)
}

@Composable
internal fun NoDownloadsPreview() {
    Preview(read(emptyList(), bytesInUse = 0))
}

@Composable
internal fun NoDownloadsDynamicPreview() {
    Preview(read(emptyList(), bytesInUse = 0), dynamicColor = true)
}

@Composable
internal fun UnreadablePreview() {
    Preview(unreadable)
}

@Composable
internal fun UnreadableDynamicPreview() {
    Preview(unreadable, dynamicColor = true)
}
