package io.github.dewfallapp.core.playback

import android.net.Uri
import androidx.media3.common.MediaItem
import io.github.dewfallapp.core.data.download.PlayableDownload
import io.github.dewfallapp.core.data.download.PlayableDownloads
import io.github.dewfallapp.core.data.playback.StreamLinksRepository
import io.github.dewfallapp.core.model.FormatChoice
import io.github.dewfallapp.core.model.PlaybackFailure
import io.github.dewfallapp.core.model.StreamLinks
import io.github.dewfallapp.core.model.VideoId
import io.github.dewfallapp.core.model.YouTubeResult
import io.github.dewfallapp.core.model.chooseFormats
import javax.inject.Inject
import kotlinx.coroutines.withContext

/** What resolving a playback ID gave. */
internal sealed interface Resolution {
    /**
     * The [item] to play, with a placeholder URI and, in its tag, the chosen links or the
     * downloaded files.
     */
    class Resolved(val item: MediaItem) : Resolution

    /** The video can't play, for the reason that [failure] names. */
    data class Failed(val failure: PlaybackFailure) : Resolution
}

/** Turns a playback ID and its video into the item that plays it. */
internal fun interface ItemResolver {
    suspend fun resolve(playbackId: String, videoId: VideoId, metered: Boolean): Resolution
}

/**
 * Resolves [playbackId] through [resolver], on a network that is [metered] or not. When the video
 * can't play, it drops the ID's entry from [items], so a playback that never started keeps no video
 * ID. An ID with no entry fails as OTHER.
 */
internal suspend fun resolveItem(
    items: PlaybackItems,
    resolver: ItemResolver,
    playbackId: String?,
    metered: Boolean,
): Resolution {
    val videoId = playbackId?.let(items::videoId)
    val resolution =
        if (playbackId == null || videoId == null) {
            Resolution.Failed(PlaybackFailure.OTHER)
        } else {
            resolver.resolve(playbackId, videoId, metered)
        }
    if (resolution is Resolution.Failed && playbackId != null) items.remove(playbackId)
    return resolution
}

/** What a video plays from, before it becomes an item. */
internal sealed interface TagChoice {
    /** The video plays what [tag] names. */
    class Plays(val tag: PlaybackTag) : TagChoice

    /** The video can't play, for [failure]. */
    data class Fails(val failure: PlaybackFailure) : TagChoice
}

/**
 * What the video with [videoId] plays from: its downloaded files when [downloads] finds them all on
 * the phone, or else its stream links from [streamLinks], with the formats that [choose] picks from
 * them on a network that is [metered] or not. Each download that can't play writes one line to
 * [log] before the video streams. The downloaded files' formats were chosen with the player's own
 * decoders, so they aren't asked again. See openspec/specs/player/spec.md.
 */
internal suspend fun chooseTag(
    videoId: VideoId,
    metered: Boolean,
    downloads: PlayableDownloads,
    streamLinks: StreamLinksRepository,
    log: PlaybackLog,
    choose: suspend (StreamLinks, Boolean) -> FormatChoice,
): TagChoice {
    when (val download = downloads.playable(videoId)) {
        is PlayableDownload.Files -> {
            log.playingDownload()
            return TagChoice.Plays(PlaybackTag.Downloaded(download))
        }
        PlayableDownload.NotDownloaded -> Unit
        is PlayableDownload.Incomplete -> log.downloadIncomplete(download.reason)
        PlayableDownload.Unreadable -> log.downloadUnreadable()
    }
    return when (val result = streamLinks.streamLinks(videoId)) {
        is YouTubeResult.Failure -> TagChoice.Fails(PlaybackFailure.of(result.error))
        is YouTubeResult.Success -> {
            val choice = choose(result.value, metered)
            if (choice == FormatChoice.NotDecodable) {
                TagChoice.Fails(PlaybackFailure.NOT_DECODABLE)
            } else {
                TagChoice.Plays(PlaybackTag.Streamed(choice, metered))
            }
        }
    }
}

/**
 * Turns a playback ID into the item that plays it, as [chooseTag] decides, with the formats that
 * the phone decodes, within the limit for a network that is metered or not.
 */
internal class PlaybackResolver
@Inject
constructor(
    private val downloads: PlayableDownloads,
    private val streamLinks: StreamLinksRepository,
    private val decoders: Decoders,
    private val dispatchers: PlaybackDispatchers,
    private val log: PlaybackLog,
) : ItemResolver {
    override suspend fun resolve(
        playbackId: String,
        videoId: VideoId,
        metered: Boolean,
    ): Resolution =
        when (
            val choice =
                chooseTag(videoId, metered, downloads, streamLinks, log) { links, onMetered ->
                    withContext(dispatchers.decoders) {
                        chooseFormats(
                            links,
                            onMetered,
                            decoders::videoDecoding,
                            decoders::audioDecodes,
                        )
                    }
                }
        ) {
            is TagChoice.Plays -> Resolution.Resolved(item(playbackId, choice.tag))
            is TagChoice.Fails -> Resolution.Failed(choice.failure)
        }

    private fun item(playbackId: String, tag: PlaybackTag): MediaItem =
        MediaItem.Builder()
            .setMediaId(playbackId)
            .setUri(Uri.fromParts(PLACEHOLDER_SCHEME, playbackId, null))
            .setTag(tag)
            .build()
}
