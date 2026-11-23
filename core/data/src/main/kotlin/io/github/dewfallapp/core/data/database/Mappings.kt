package io.github.dewfallapp.core.data.database

import io.github.dewfallapp.core.database.ChannelDetails
import io.github.dewfallapp.core.database.ChannelEntity
import io.github.dewfallapp.core.database.ChannelOverviewRow
import io.github.dewfallapp.core.database.VideoEntity
import io.github.dewfallapp.core.database.WatchStateEntity
import io.github.dewfallapp.core.model.Channel
import io.github.dewfallapp.core.model.ChannelId
import io.github.dewfallapp.core.model.ChannelOverview
import io.github.dewfallapp.core.model.ChannelUploads
import io.github.dewfallapp.core.model.PublishedTime
import io.github.dewfallapp.core.model.SubscribedChannel
import io.github.dewfallapp.core.model.VideoId
import io.github.dewfallapp.core.model.VideoSummary
import io.github.dewfallapp.core.model.WatchState
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Instant

// The mapping between the database's rows and Dewfall's models. The rows hold IDs as text, and
// only IDs that passed ChannelId's and VideoId's checks are ever written, so reading them back
// with the constructors can't fail on a row this app wrote.

internal fun ChannelEntity.toSubscribedChannel(): SubscribedChannel =
    SubscribedChannel(
        channel =
            Channel(
                id = ChannelId(id),
                name = name,
                avatars = avatars,
                description = description,
            ),
        subscribedAt = subscribedAt,
    )

/** A row of ChannelDao's channelOverviews: the subscription, and its last upload's time if any. */
internal fun ChannelOverviewRow.toChannelOverview(): ChannelOverview =
    ChannelOverview(
        subscription = channel.toSubscribedChannel(),
        lastUpload =
            lastPublishedAt?.let { instant ->
                PublishedTime(instant, isApproximate = lastPublishedApproximate ?: false)
            },
    )

internal fun Channel.toEntity(subscribedAt: Instant): ChannelEntity =
    ChannelEntity(
        id = id.value,
        name = name,
        description = description,
        avatars = avatars,
        subscribedAt = subscribedAt,
    )

internal fun Channel.toDetails(): ChannelDetails =
    ChannelDetails(id = id.value, name = name, description = description, avatars = avatars)

/**
 * The rows that a save of [this] at [now] brings: each video first seen now, at its place in the
 * list. The DAO gives them the save's listing number.
 */
internal fun ChannelUploads.toEntities(now: Instant): List<VideoEntity> =
    videos.mapIndexed { place, video ->
        video.toEntity(now, place)
    }

internal fun VideoSummary.toEntity(now: Instant, listPosition: Int): VideoEntity =
    VideoEntity(
        id = id.value,
        channelId = channelId.value,
        channelName = channelName,
        title = title,
        durationMs = duration?.inWholeMilliseconds,
        publishedAt = published?.instant,
        publishedApproximate = published?.isApproximate ?: false,
        kind = kind,
        thumbnails = thumbnails,
        firstSeenAt = now,
        listingNumber = null,
        listPosition = listPosition,
    )

/** The saved video as a list shows it. A row with no publish time has no PublishedTime. */
internal fun VideoEntity.toSummary(): VideoSummary =
    VideoSummary(
        id = VideoId(id),
        title = title,
        channelId = ChannelId(channelId),
        channelName = channelName,
        duration = durationMs?.milliseconds,
        published = publishedAt?.let { PublishedTime(it, isApproximate = publishedApproximate) },
        thumbnails = thumbnails,
        kind = kind,
    )

internal fun WatchStateEntity.toWatchState(): WatchState =
    WatchState(position = positionMs.milliseconds, watchedAt = watchedAt, updatedAt = updatedAt)
