package io.github.dewfallapp.feature.downloads.logic

import io.github.dewfallapp.core.model.Download
import io.github.dewfallapp.core.model.VideoId
import io.github.dewfallapp.core.model.VideoSummary
import io.github.dewfallapp.core.mvi.MviEffect
import io.github.dewfallapp.core.mvi.MviIntent
import io.github.dewfallapp.core.mvi.MviState

/** What the Downloads screen lists. The downloads are user data, so never log them. */
sealed interface DownloadsList {
    /** Before the phone's first answer, for the moment before Room answers. */
    data object Loading : DownloadsList

    /** The phone keeps no download. */
    data object NoDownloads : DownloadsList

    /**
     * The downloads in two sections: [downloading], every one that waits, runs, is paused or
     * failed, in the queue's order, and [onPhone], every finished one, the most recently finished
     * first. Never both empty.
     */
    data class Shown(val downloading: List<Download>, val onPhone: List<Download>) : DownloadsList

    /** The phone couldn't read what it keeps, such as from a damaged database. */
    data object Unreadable : DownloadsList
}

/** The Downloads screen's state. */
data class DownloadsState(val list: DownloadsList = DownloadsList.Loading) : MviState

/** What happens to the Downloads screen. */
sealed interface DownloadsIntent : MviIntent {
    /** The phone answered with every download, in the queue's order. */
    data class DownloadsRead(val downloads: List<Download>) : DownloadsIntent

    /** The read threw, so the screen says it can't show the downloads instead of crashing. */
    data object ReadFailed : DownloadsIntent

    /** The user tapped "Pause" on a waiting or running download. */
    data class Pause(val videoId: VideoId) : DownloadsIntent

    /** The user tapped "Resume" on a paused download or "Try again" on a failed one. */
    data class Resume(val video: VideoSummary) : DownloadsIntent

    /**
     * The user tapped "Cancel" on an unfinished download, or "Remove download" on a [finished] one,
     * which says "Download removed" once it is gone.
     */
    data class Remove(val videoId: VideoId, val finished: Boolean) : DownloadsIntent

    /** The ViewModel removed a finished download. */
    data object Removed : DownloadsIntent
}

/** What the Downloads screen shows once. */
sealed interface DownloadsEffect : MviEffect {
    /** Show "Download removed". */
    data object ShowRemoved : DownloadsEffect
}
