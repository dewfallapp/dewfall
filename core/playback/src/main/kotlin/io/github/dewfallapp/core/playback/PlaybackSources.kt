@file:OptIn(UnstableApi::class)

package io.github.dewfallapp.core.playback

import android.net.Uri
import androidx.annotation.OptIn
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DataSpec
import androidx.media3.datasource.FileDataSource
import androidx.media3.datasource.TransferListener
import androidx.media3.datasource.okhttp.OkHttpDataSource
import androidx.media3.exoplayer.drm.DrmSessionManagerProvider
import androidx.media3.exoplayer.hls.HlsMediaSource
import androidx.media3.exoplayer.source.MediaSource
import androidx.media3.exoplayer.source.MergingMediaSource
import androidx.media3.exoplayer.source.ProgressiveMediaSource
import androidx.media3.exoplayer.upstream.DefaultLoadErrorHandlingPolicy
import androidx.media3.exoplayer.upstream.LoadErrorHandlingPolicy
import io.github.dewfallapp.core.data.download.PlayableDownload
import io.github.dewfallapp.core.data.media.LiveMediaCalls
import io.github.dewfallapp.core.data.media.ProgressiveMediaCalls
import io.github.dewfallapp.core.model.FormatChoice
import io.github.dewfallapp.core.model.MediaRequestRefused
import java.io.File
import javax.inject.Inject
import javax.inject.Provider
import okhttp3.Call

/** The scheme of the placeholder URI of a resolved item, which is never fetched. */
internal const val PLACEHOLDER_SCHEME = "dewfall-playback"

/**
 * What a resolved item plays, kept in its MediaItem's tag: the stream, or the downloaded files. Its
 * kinds are plain classes, so their text never shows the links or the paths.
 */
internal sealed interface PlaybackTag {
    /** Whether the item is a live stream. */
    val live: Boolean

    /** The formats that [choice] names, chosen on a network that was [metered] or not. */
    class Streamed(val choice: FormatChoice, val metered: Boolean) : PlaybackTag {
        override val live: Boolean
            get() = choice is FormatChoice.Live
    }

    /** The finished download's [files] on the phone. A download is never live. */
    class Downloaded(val files: PlayableDownload.Files) : PlaybackTag {
        override val live: Boolean
            get() = false
    }
}

/**
 * The player's wake mode for an item with [tag], its MediaItem's tag: the CPU only for downloaded
 * files, which need no network, and the CPU and Wi-Fi for anything else, so streaming goes on with
 * the screen off.
 */
internal fun wakeModeFor(tag: Any?): Int =
    if (tag is PlaybackTag.Downloaded) C.WAKE_MODE_LOCAL else C.WAKE_MODE_NETWORK

/** The media sources that an item becomes, by its tag. */
internal sealed interface SourcePlan {
    /** A video format and an audio format, merged. */
    data class Merged(val videoUrl: String, val audioUrl: String) : SourcePlan

    /** One format with both sound and picture. */
    data class Progressive(val url: String) : SourcePlan

    /** A live stream's HLS manifest. */
    data class Hls(val manifestUrl: String) : SourcePlan

    /**
     * A downloaded [video] file and its [audio] file, merged, or one file with both when [audio] is
     * null. Paths are user data, so never log a plan.
     */
    data class Files(val video: File, val audio: File?) : SourcePlan

    /** An item without the tag, which fails without a request. */
    data object Refused : SourcePlan
}

/** The plan for an item with [tag], its MediaItem's tag. */
internal fun sourcePlan(tag: Any?): SourcePlan =
    when (tag) {
        is PlaybackTag.Streamed -> streamPlan(tag.choice)
        is PlaybackTag.Downloaded -> {
            when (val files = tag.files) {
                is PlayableDownload.Separate -> SourcePlan.Files(files.video, files.audio)
                is PlayableDownload.Combined -> SourcePlan.Files(files.media, audio = null)
            }
        }
        else -> SourcePlan.Refused
    }

private fun streamPlan(choice: FormatChoice): SourcePlan =
    when (choice) {
        is FormatChoice.Separate -> SourcePlan.Merged(choice.video.url, choice.audio.url)
        is FormatChoice.Combined -> SourcePlan.Progressive(choice.video.url)
        is FormatChoice.Live -> SourcePlan.Hls(choice.manifestUrl)
        FormatChoice.NotDecodable -> SourcePlan.Refused
    }

/**
 * Doesn't retry a refused link, so new links are fetched at once, or a request that was refused
 * before it was sent, so it is never sent again. Other errors keep the default retries.
 */
internal class PlaybackLoadErrorPolicy : DefaultLoadErrorHandlingPolicy() {
    override fun getRetryDelayMsFor(loadErrorInfo: LoadErrorHandlingPolicy.LoadErrorInfo): Long {
        val error = loadErrorInfo.exception
        return if (retries(httpStatusOf(error), refusedInChain(error))) {
            super.getRetryDelayMsFor(loadErrorInfo)
        } else {
            C.TIME_UNSET
        }
    }
}

/**
 * Makes the ExoPlayer's media sources from each item's tag, with every request going through the
 * YouTube layer's MediaRequests: a new [progressiveCalls] factory for each progressive source, so
 * each numbers its requests from 0, and [liveCalls] for HLS. A downloaded item's files are read
 * with FileDataSource, which reads any URI's path as a local file and never opens a connection, so
 * only the resolver's tag, which holds files that PlayableDownloads checked are inside the
 * downloads' folder, decides what it reads. An item without the tag fails, and never falls back to
 * a default HTTP data source.
 */
internal class PlaybackSources
@Inject
constructor(
    @ProgressiveMediaCalls private val progressiveCalls: Provider<Call.Factory>,
    @LiveMediaCalls private val liveCalls: Call.Factory,
) : MediaSource.Factory {
    private val policy = PlaybackLoadErrorPolicy()

    /** There is no DRM, so this keeps none. */
    override fun setDrmSessionManagerProvider(
        drmSessionManagerProvider: DrmSessionManagerProvider,
    ): MediaSource.Factory = this

    /** The sources always use [PlaybackLoadErrorPolicy], so this keeps none. */
    override fun setLoadErrorHandlingPolicy(
        loadErrorHandlingPolicy: LoadErrorHandlingPolicy,
    ): MediaSource.Factory = this

    override fun getSupportedTypes(): IntArray =
        intArrayOf(C.CONTENT_TYPE_OTHER, C.CONTENT_TYPE_HLS)

    override fun createMediaSource(mediaItem: MediaItem): MediaSource {
        val tag = mediaItem.localConfiguration?.tag
        return when (val plan = sourcePlan(tag)) {
            is SourcePlan.Merged -> {
                MergingMediaSource(
                    /* adjustPeriodTimeOffsets= */ true,
                    /* clipDurations= */ true,
                    progressive(child(mediaItem, plan.videoUrl, tag)),
                    progressive(child(mediaItem, plan.audioUrl, tag)),
                )
            }
            is SourcePlan.Progressive -> progressive(child(mediaItem, plan.url, tag))
            is SourcePlan.Hls -> {
                HlsMediaSource.Factory(OkHttpDataSource.Factory(liveCalls))
                    .setLoadErrorHandlingPolicy(policy)
                    .createMediaSource(child(mediaItem, plan.manifestUrl, tag))
            }
            is SourcePlan.Files -> {
                val audio = plan.audio
                if (audio == null) {
                    local(child(mediaItem, plan.video, tag))
                } else {
                    MergingMediaSource(
                        /* adjustPeriodTimeOffsets= */ true,
                        /* clipDurations= */ true,
                        local(child(mediaItem, plan.video, tag)),
                        local(child(mediaItem, audio, tag)),
                    )
                }
            }
            SourcePlan.Refused -> {
                ProgressiveMediaSource.Factory { RefusingDataSource() }
                    .setLoadErrorHandlingPolicy(policy)
                    .createMediaSource(child(mediaItem, "$PLACEHOLDER_SCHEME:refused", tag = null))
            }
        }
    }

    private fun progressive(item: MediaItem): MediaSource =
        ProgressiveMediaSource.Factory(OkHttpDataSource.Factory(progressiveCalls.get()))
            .setLoadErrorHandlingPolicy(policy)
            .createMediaSource(item)

    /** A source that reads [item]'s downloaded file from the phone. */
    private fun local(item: MediaItem): MediaSource =
        ProgressiveMediaSource.Factory(FileDataSource.Factory())
            .setLoadErrorHandlingPolicy(policy)
            .createMediaSource(item)

    private fun child(item: MediaItem, file: File, tag: Any?): MediaItem =
        child(item, Uri.fromFile(file).toString(), tag)

    /**
     * The item that one source loads: the playback ID, the link or the file's URI, and the tag,
     * which the service reads back from the player's current item. The media session never sees it,
     * because SessionPlayer shows only the playback ID.
     */
    private fun child(item: MediaItem, url: String, tag: Any?): MediaItem =
        MediaItem.Builder().setMediaId(item.mediaId).setUri(url).setTag(tag).build()
}

/** A data source that refuses every request, for an item without the tag. */
private class RefusingDataSource : DataSource {
    override fun addTransferListener(transferListener: TransferListener) = Unit

    override fun open(dataSpec: DataSpec): Long =
        throw MediaRequestRefused(MediaRequestRefused.Reason.NO_LINKS)

    override fun read(buffer: ByteArray, offset: Int, length: Int): Int = C.RESULT_END_OF_INPUT

    override fun getUri(): Uri? = null

    override fun close() = Unit
}
