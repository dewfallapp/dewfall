package io.github.dewfallapp.feature.player.ui

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import androidx.media3.common.C
import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import dagger.hilt.android.lifecycle.HiltViewModel
import io.github.dewfallapp.core.data.download.DownloadsRepository
import io.github.dewfallapp.core.data.video.VideoDetailsRepository
import io.github.dewfallapp.core.data.watch.WatchStateRepository
import io.github.dewfallapp.core.model.PlaybackFailure
import io.github.dewfallapp.core.model.VideoDetails
import io.github.dewfallapp.core.model.VideoId
import io.github.dewfallapp.core.model.VideoKind
import io.github.dewfallapp.core.model.VideoSummary
import io.github.dewfallapp.core.model.WatchState
import io.github.dewfallapp.core.model.YouTubeResult
import io.github.dewfallapp.core.mvi.MviViewModel
import io.github.dewfallapp.core.playback.PictureHold
import io.github.dewfallapp.core.playback.PlaybackConnection
import io.github.dewfallapp.feature.player.logic.DetailsArea
import io.github.dewfallapp.feature.player.logic.PlayerIntent
import io.github.dewfallapp.feature.player.logic.PlayerReducer
import io.github.dewfallapp.feature.player.logic.PlayerState
import io.github.dewfallapp.feature.player.logic.StoredPosition
import io.github.dewfallapp.feature.player.logic.WaitingPosition
import io.github.dewfallapp.feature.player.logic.chipOf
import io.github.dewfallapp.feature.player.logic.page
import io.github.dewfallapp.feature.player.logic.sharesFailure
import io.github.dewfallapp.feature.player.logic.waitingPosition
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

/**
 * What the video screen's ViewModel needs from the screen that opens it. It is an ordinary class,
 * not the VideoId itself, because VideoId is a value class, and Dagger can't implement a factory
 * function that takes one. See docs/architecture.md#navigation.
 */
internal class PlayerArgs(val videoId: VideoId)

/** The key of whether the screen shows fullscreen in the ViewModel's saved state. */
private const val FULLSCREEN_KEY = "fullscreen"

/**
 * The playback ID of the player's [currentItemId] when it plays [videoId], as [videoOf] says, so a
 * screen that opens goes on with it, or null.
 */
internal fun playbackOfVideo(
    currentItemId: String?,
    videoId: VideoId,
    videoOf: (String) -> VideoId?,
): String? = currentItemId?.takeIf { videoOf(it) == videoId }

/**
 * Loads the video screen's details, one request at a time. It sends Load once when it is created,
 * and a request starts only when the reducer made a new one, so "Try again" with nothing to try
 * sends nothing. Leaving the screen cancels the running request.
 *
 * It takes no dispatcher, because the repository's call is main-safe. It also holds the screen's
 * PlaybackConnection and the playback ID of its latest start, or of the playback it goes on with.
 * It gives that playback the video's title and channel name for the media notification once the
 * details show, and releases it when it is cleared, so a minimized video plays on in the mini
 * player. It holds the picture while it exists. It keeps fullscreen in its saved state, so the
 * screen comes back in fullscreen after the process ends. Player commands skip intents, as ADR
 * 0002's note says.
 *
 * It follows whether the database marks the video watched for as long as it exists, and writes the
 * watched chip's marks, whose answers come back through that Flow. It has no Logger: a database
 * that can't be read leaves the chip hidden, and WatchProgress in the playback service logs the
 * failure once the video is the player's item, through its own reads; a mark that can't be written
 * leaves the chip as it was, with no log line.
 *
 * It follows the video's download the same way, and starts or pauses it from the download chip. A
 * download needs the video's summary, which it builds from the details it shows, or takes from the
 * download while the saved details show, so the chip's taps do nothing before the details arrive. A
 * download state that can't be read leaves the download chip hidden, and one that can't be written
 * leaves it as it was, with no log line, as the downloads repository's writes run in the app's
 * scope.
 *
 * It also follows the summary saved with the video's download, which the reducer shows when YouTube
 * can't be reached. A summary that can't be read counts as none, so the failure shows as before,
 * with no log line.
 */
@HiltViewModel(assistedFactory = PlayerViewModel.Factory::class)
internal class PlayerViewModel
@AssistedInject
constructor(
    @Assisted private val args: PlayerArgs,
    private val repository: VideoDetailsRepository,
    private val watchStates: WatchStateRepository,
    private val downloads: DownloadsRepository,
    /** The player, which the route reads and sends play, pause and seek to, never through state. */
    internal val playback: PlaybackConnection,
    private val savedState: SavedStateHandle,
) :
    MviViewModel<PlayerState, PlayerIntent, Nothing>(
        PlayerState(fullscreen = savedState[FULLSCREEN_KEY] ?: false),
        PlayerReducer,
    ) {

    /** The running request. Only the main thread touches it. */
    private var detailsJob: Job? = null

    /** The number of the latest request that a call started for. */
    private var startedNumber = 0L

    /** The playback ID of this screen's latest start. Only the main thread touches it. */
    private var playbackId: String? = null

    /** Shows the picture while this screen exists, even while it turns to landscape. */
    private val picture: PictureHold = playback.holdPicture()

    /** The details that the screen shows, which a download keeps as its summary, or null. */
    private var shownDetails: VideoDetails? = null

    /**
     * The summary that a download keeps: the one of the details that the screen shows, or, while
     * the saved details show, the one saved with the download. Null before the details arrive.
     */
    private val downloadSummary: VideoSummary?
        get() =
            shownDetails?.let(::summaryOf)
                ?: state.value.savedVideo?.takeIf { state.value.details is DetailsArea.Saved }

    /** A mark or a download that the database can't write leaves its chip as it was. */
    private val writeFailed = CoroutineExceptionHandler { _, _ ->
        // Nothing to undo: the chip shows what the database holds, which didn't change.
    }

    init {
        dispatch(PlayerIntent.Load)
        viewModelScope.launch {
            watchStates
                .watchState(args.videoId)
                .map { it?.watchedAt != null }
                .distinctUntilChanged()
                .catch { dispatch(PlayerIntent.WatchStateUnreadable) }
                .collect { dispatch(PlayerIntent.WatchStateChanged(it)) }
        }
        viewModelScope.launch {
            downloads
                .status(args.videoId)
                .map(::chipOf)
                .distinctUntilChanged()
                .catch { dispatch(PlayerIntent.DownloadUnreadable) }
                .collect { dispatch(PlayerIntent.DownloadChanged(it)) }
        }
        viewModelScope.launch {
            downloads
                .savedVideo(args.videoId)
                .catch { emit(null) }
                .collect { dispatch(PlayerIntent.SavedVideoChanged(it)) }
        }
    }

    /**
     * The position the database stores for this screen's video, which the route reads only while
     * the screen waits for play. It stays out of screen state, because the playback service updates
     * it while the video plays. A database that can't be read gives Unreadable. Each collection
     * asks the repository anew.
     */
    internal val storedPosition: Flow<StoredPosition> = flow {
        emitAll(watchStates.watchState(args.videoId))
    }
        .map<WatchState?, StoredPosition> { StoredPosition.Known(it?.position) }
        .catch { emit(StoredPosition.Unreadable) }

    /** A tap's wait for the stored position's first answer. Only the main thread touches it. */
    private var storedAnswer: Job? = null

    /** Whether a tap's wait for the stored position's first answer still runs. */
    internal val waitsForStoredAnswer: Boolean
        get() = storedAnswer?.isActive == true

    /**
     * Runs [action] with the position that "Try again", or play after the session was gone, starts
     * from: [playingMs], the player's position while it plays this screen's video; or where the
     * screen stands, [waitingAt]; or, before the database's first answer, that answer, as
     * [afterStoredAnswer] gets it. While such a wait runs, a call does nothing, so a second tap
     * starts nothing.
     */
    internal fun withLastPosition(
        playingMs: Long?,
        waitingAt: WaitingPosition,
        action: (Long) -> Unit,
    ) {
        val standingMs = waitingAt.startMs
        when {
            playingMs != null -> action(playingMs)
            waitsForStoredAnswer -> Unit
            standingMs != null -> action(standingMs)
            else -> afterStoredAnswer(waitingAt.shownMs, action)
        }
    }

    /**
     * Runs [action], on the main thread, with where a waiting screen starts once the database's
     * first answer about the stored position comes, as [waitingPosition] picks it from that answer
     * and the screen's own [screenPositionMs], or C.TIME_UNSET. A call while an earlier one waits
     * does nothing, so a second tap starts nothing.
     */
    internal fun afterStoredAnswer(screenPositionMs: Long?, action: (Long) -> Unit) {
        if (waitsForStoredAnswer) return
        storedAnswer = viewModelScope.launch {
            val answer = waitingPosition(storedPosition.first(), screenPositionMs)
            action(answer.startMs ?: C.TIME_UNSET)
        }
    }

    /**
     * Cancels a tap's wait for the stored position's first answer, when the screen's composition
     * that asked for it leaves, such as on a rotation, so the old composition starts nothing.
     */
    internal fun cancelStoredAnswer() {
        storedAnswer?.cancel()
    }

    override fun runSideEffects(intent: PlayerIntent) {
        when (intent) {
            PlayerIntent.Load,
            PlayerIntent.TryAgain -> startNewRequest()
            is PlayerIntent.DetailsFinished -> {
                // The reducer has already run, so the state holds this answer, and a late answer
                // that it dropped keeps nothing.
                val result = intent.result
                if (result is YouTubeResult.Success && state.value.details is DetailsArea.Shown) {
                    shownDetails = result.value
                }
                describePlayback()
            }
            PlayerIntent.EnterFullscreen,
            PlayerIntent.ExitFullscreen -> savedState[FULLSCREEN_KEY] = state.value.fullscreen
            PlayerIntent.MarkWatched -> {
                viewModelScope.launch(writeFailed) { watchStates.markWatched(args.videoId) }
            }
            PlayerIntent.MarkUnwatched -> {
                viewModelScope.launch(writeFailed) { watchStates.markUnwatched(args.videoId) }
            }
            PlayerIntent.Download -> {
                val summary = downloadSummary ?: return
                viewModelScope.launch(writeFailed) { downloads.download(summary) }
            }
            // Only a saved page that the summary made or changed has new names for the playback.
            is PlayerIntent.SavedVideoChanged -> {
                if (state.value.details is DetailsArea.Saved) describePlayback()
            }
            PlayerIntent.PauseDownload -> {
                viewModelScope.launch(writeFailed) { downloads.pause(args.videoId) }
            }
            is PlayerIntent.WatchStateChanged,
            PlayerIntent.WatchStateUnreadable,
            is PlayerIntent.DownloadChanged,
            PlayerIntent.DownloadUnreadable -> Unit
        }
    }

    /** Starts a call for the state's request, if the reducer just made a new one. */
    private fun startNewRequest() {
        val number = (state.value.details as? DetailsArea.Loading)?.number
        if (number == null || number == startedNumber) return
        startedNumber = number
        detailsJob?.cancel()
        detailsJob = viewModelScope.launch {
            val result = repository.videoDetails(args.videoId)
            dispatch(PlayerIntent.DetailsFinished(number, result))
        }
    }

    /**
     * Starts playing the video from [positionMs], or from the start or live edge for C.TIME_UNSET,
     * under a new playback ID. It returns the ID for the route's saved state, and keeps it, so
     * leaving the screen stops that playback.
     */
    internal fun startPlayback(positionMs: Long): String {
        val id = playback.prepare(args.videoId, play = true, startPositionMs = positionMs)
        playbackId = id
        describePlayback()
        return id
    }

    /**
     * The playback ID of the player's item if it plays this screen's video, such as one that the
     * mini player shows, or null.
     */
    internal fun playbackOfThisVideo(): String? =
        playbackOfVideo(
            playback.player.value?.currentMediaItem?.mediaId,
            args.videoId,
            playback::videoOf,
        )

    /**
     * Goes on with [id], a playback that this ViewModel didn't start: one that already played this
     * video, or this screen's own after the ViewModel was made again. It describes and releases it
     * as its own.
     */
    internal fun follow(id: String) {
        playbackId = id
        describePlayback()
    }

    /**
     * Gives the latest playback the title and channel name that the media notification shows, once
     * the details show, YouTube's or the ones saved with the download. The reducer has already run,
     * so the state holds this intent's answer. Nothing clears them, so the playback keeps them.
     */
    private fun describePlayback() {
        val page = state.value.details.page ?: return
        val id = playbackId ?: return
        playback.describe(id, page.title, page.channelName)
    }

    /**
     * "Try again" in the details' place. When playback failed the same way, the video area shows no
     * second message, so this retries playback too, from [positionMs], and returns its new playback
     * ID. Otherwise it returns null.
     */
    internal fun tryAgainDetails(playbackFailure: PlaybackFailure?, positionMs: Long): String? {
        val shared = sharesFailure(state.value.details, playbackFailure)
        dispatch(PlayerIntent.TryAgain)
        return if (shared) startPlayback(positionMs) else null
    }

    /**
     * Releases this screen's playback, which plays on in the mini player while it is the player's
     * item, and lets go of the picture. Navigation 3 clears the ViewModel only after the screen's
     * exit animation.
     */
    override fun onCleared() {
        playbackId?.let(playback::release)
        picture.release()
    }

    /** Makes the ViewModel for the video in [PlayerArgs]. Hilt implements it. */
    @AssistedFactory
    interface Factory {
        fun create(args: PlayerArgs): PlayerViewModel
    }
}

/**
 * The summary that a download of the video in [details] keeps. The video screen's details don't
 * tell a Short from a video, so it is a video, or a live stream, which has no download chip.
 */
internal fun summaryOf(details: VideoDetails): VideoSummary =
    VideoSummary(
        id = details.id,
        title = details.title,
        channelId = details.channelId,
        channelName = details.channelName,
        duration = details.duration,
        published = details.published,
        thumbnails = details.thumbnails,
        kind = if (details.isLive) VideoKind.LIVE else VideoKind.VIDEO,
    )
