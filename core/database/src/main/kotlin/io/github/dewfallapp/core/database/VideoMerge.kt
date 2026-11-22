package io.github.dewfallapp.core.database

import io.github.dewfallapp.core.model.VideoKind

/**
 * The video to keep when a save brings [new] for a video already saved as [saved], or [new] itself
 * when nothing is saved yet. The rule is the subscriptions spec's "Saving again updates YouTube's
 * details" and "A later save never makes a publish time vaguer":
 * - The title, channel ID, channel name and kind come from [new].
 * - The length and the thumbnails come from [new], unless it has none.
 * - The publish time comes from [new] only when it is exact, when none was saved, or when [saved]
 *   was upcoming and [new] isn't. Otherwise the saved time and whether it is approximate stay. A
 *   [new] with no time keeps the saved one.
 * - The time Dewfall first saved the video stays.
 * - The listing number and the place in the list come from [new].
 */
internal fun mergeVideo(saved: VideoEntity?, new: VideoEntity): VideoEntity {
    if (saved == null) return new
    val takesNewTime =
        new.publishedAt != null &&
            (!new.publishedApproximate ||
                saved.publishedAt == null ||
                (saved.kind == VideoKind.UPCOMING && new.kind != VideoKind.UPCOMING))
    return new.copy(
        durationMs = new.durationMs ?: saved.durationMs,
        publishedAt = if (takesNewTime) new.publishedAt else saved.publishedAt,
        publishedApproximate =
            if (takesNewTime) new.publishedApproximate else saved.publishedApproximate,
        thumbnails = new.thumbnails.ifEmpty { saved.thumbnails },
        firstSeenAt = saved.firstSeenAt,
    )
}

/**
 * A subscribed channel's row after a save of its uploads brings [details]: the name comes from
 * [details], and so do the avatars and the description, unless they are empty. The time of
 * subscribing never changes. This is the subscriptions spec's "Saving a channel's uploads refreshes
 * its details".
 */
internal fun mergeChannelDetails(saved: ChannelEntity, details: ChannelDetails): ChannelEntity =
    saved.copy(
        name = details.name,
        description = details.description.ifEmpty { saved.description },
        avatars = details.avatars.ifEmpty { saved.avatars },
    )
