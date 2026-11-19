package io.github.dewfallapp.core.playback

import android.net.Uri
import androidx.media3.common.MediaItem
import io.github.dewfallapp.core.data.playback.StreamLinksRepository
import io.github.dewfallapp.core.model.FormatChoice
import io.github.dewfallapp.core.model.PlaybackFailure
import io.github.dewfallapp.core.model.VideoId
import io.github.dewfallapp.core.model.YouTubeResult
import io.github.dewfallapp.core.model.chooseFormats
import javax.inject.Inject
import kotlinx.coroutines.withContext

/** What resolving a playback ID gave. */
internal sealed interface Resolution {
    /** The [item] to play, with a placeholder URI and the chosen links in its tag. */
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

/**
 * Turns a playback ID into the item that plays it: it fetches the video's stream links through the
 * repository and chooses the formats that the phone decodes, within the limit for a network that is
 * [metered] or not.
 */
internal class PlaybackResolver
@Inject
constructor(
    private val streamLinks: StreamLinksRepository,
    private val decoders: Decoders,
    private val dispatchers: PlaybackDispatchers,
) : ItemResolver {
    override suspend fun resolve(
        playbackId: String,
        videoId: VideoId,
        metered: Boolean,
    ): Resolution =
        when (val result = streamLinks.streamLinks(videoId)) {
            is YouTubeResult.Failure -> Resolution.Failed(PlaybackFailure.of(result.error))
            is YouTubeResult.Success -> {
                val choice =
                    withContext(dispatchers.decoders) {
                        chooseFormats(
                            result.value,
                            metered,
                            decoders::videoDecoding,
                            decoders::audioDecodes,
                        )
                    }
                if (choice == FormatChoice.NotDecodable) {
                    Resolution.Failed(PlaybackFailure.NOT_DECODABLE)
                } else {
                    Resolution.Resolved(item(playbackId, PlaybackTag(choice, metered)))
                }
            }
        }

    private fun item(playbackId: String, tag: PlaybackTag): MediaItem =
        MediaItem.Builder()
            .setMediaId(playbackId)
            .setUri(Uri.fromParts(PLACEHOLDER_SCHEME, playbackId, null))
            .setTag(tag)
            .build()
}
