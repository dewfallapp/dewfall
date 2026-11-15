package io.github.dewfallapp.core.youtube.newpipe

import io.github.dewfallapp.core.model.Channel
import io.github.dewfallapp.core.model.ChannelId
import io.github.dewfallapp.core.model.PublishedTime
import io.github.dewfallapp.core.model.Thumbnail
import io.github.dewfallapp.core.model.VideoId
import io.github.dewfallapp.core.model.VideoKind
import io.github.dewfallapp.core.model.VideoSummary
import kotlin.time.Duration.Companion.seconds
import kotlin.time.toKotlinInstant
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import org.schabi.newpipe.extractor.Image
import org.schabi.newpipe.extractor.channel.ChannelInfoItem
import org.schabi.newpipe.extractor.localization.DateWrapper
import org.schabi.newpipe.extractor.stream.ContentAvailability
import org.schabi.newpipe.extractor.stream.StreamInfoItem
import org.schabi.newpipe.extractor.stream.StreamType

/**
 * The first path segment of a channel's link, such as https://www.youtube.com/channel/UC..., and
 * the prefix of the ID that NewPipeExtractor's channel link factory takes, such as channel/UC....
 */
internal const val CHANNEL_PATH = "channel"

/** The query parameter that holds a video's ID in a watch link. */
private const val VIDEO_ID_PARAMETER = "v"

/** The kinds of video that can be watched from start to end, so their length means something. */
private val KINDS_WITH_LENGTH = setOf(VideoKind.VIDEO, VideoKind.SHORT)

/** The stream types of a stream that is on air. */
internal val LIVE_STREAM_TYPES = setOf(StreamType.LIVE_STREAM, StreamType.AUDIO_LIVE_STREAM)

/**
 * Thrown when YouTube's answer about one video or channel lacks a field that its model needs. It
 * maps to BROKEN, because only an app update can make the layer read that answer. A list drops such
 * an item instead.
 */
internal class MissingFieldException : Exception("YouTube's answer lacks a required field")

/**
 * The ID in a video's watch link, such as https://www.youtube.com/watch?v=..., the form of every
 * link that NewPipeExtractor gives a video. Null for any other link.
 */
internal fun videoIdFromLink(link: String?): VideoId? =
    link?.toHttpUrlOrNull()?.queryParameter(VIDEO_ID_PARAMETER)?.let(VideoId::parseOrNull)

/**
 * The ID in a channel's link, such as https://www.youtube.com/channel/UC..., which may go on with a
 * tab, such as /streams. Null for any other link, such as a handle's link like /@name.
 */
internal fun channelIdFromLink(link: String?): ChannelId? {
    val segments = link?.toHttpUrlOrNull()?.pathSegments.orEmpty()
    return if (segments.firstOrNull() == CHANNEL_PATH) {
        segments.getOrNull(1)?.let(ChannelId::parseOrNull)
    } else {
        null
    }
}

/** Every size of an image. NewPipeExtractor gives -1 for a width or height that it doesn't know. */
internal fun List<Image>.toThumbnails(): List<Thumbnail> = map {
    Thumbnail(
        it.url,
        it.width.takeIf { width -> width > 0 },
        it.height.takeIf { height -> height > 0 },
    )
}

internal fun DateWrapper.toPublishedTime(): PublishedTime =
    PublishedTime(instant.toKotlinInstant(), isApproximate = isApproximation)

/**
 * What kind of video this item is. A premiere or stream that hasn't started counts as UPCOMING,
 * even when YouTube marks it live too. Then a stream on air is LIVE, and a Short is SHORT.
 */
internal fun StreamInfoItem.kind(): VideoKind =
    when {
        contentAvailability == ContentAvailability.UPCOMING -> VideoKind.UPCOMING
        streamType in LIVE_STREAM_TYPES -> VideoKind.LIVE
        isShortFormContent -> VideoKind.SHORT
        else -> VideoKind.VIDEO
    }

/**
 * This item as a video of its own channel, or null if its video ID, title, channel ID or channel
 * name is missing.
 */
internal fun StreamInfoItem.toVideoSummaryOrNull(): VideoSummary? {
    val channelId = channelIdFromLink(uploaderUrl)
    val channelName: String? = uploaderName
    return if (channelId != null && !channelName.isNullOrBlank()) {
        toVideoSummaryOrNull(channelId, channelName)
    } else {
        null
    }
}

/**
 * This item as a video of the channel with [channelId] and [channelName], or null if its video ID
 * or title is missing. A channel's tabs use this, because their items give the tab's link as the
 * channel's, and can lack the channel's name.
 */
internal fun StreamInfoItem.toVideoSummaryOrNull(
    channelId: ChannelId,
    channelName: String,
): VideoSummary? {
    val id = videoIdFromLink(url)
    // NewPipeExtractor gives "" for an empty title, and null only when the answer has no title.
    val title: String? = name
    if (id == null || title == null) return null
    val kind = kind()
    return VideoSummary(
        id = id,
        title = title,
        channelId = channelId,
        channelName = channelName,
        duration = duration.takeIf { it > 0 && kind in KINDS_WITH_LENGTH }?.seconds,
        published = uploadDate?.toPublishedTime(),
        thumbnails = thumbnails.toThumbnails(),
        kind = kind,
    )
}

/** This item as a channel, or null if its channel ID or name is missing. */
internal fun ChannelInfoItem.toChannelOrNull(): Channel? {
    val id = channelIdFromLink(url)
    val channelName: String? = name
    return if (id != null && !channelName.isNullOrBlank()) {
        Channel(id, channelName, thumbnails.toThumbnails(), description.orEmpty())
    } else {
        null
    }
}
