package io.github.dewfallapp.feature.inbox.ui

import io.github.dewfallapp.core.data.download.DownloadsRepository
import io.github.dewfallapp.core.data.subscriptions.SubscriptionsRepository
import io.github.dewfallapp.core.data.subscriptions.UploadsChecker
import io.github.dewfallapp.core.data.video.StoredVideosRepository
import io.github.dewfallapp.core.model.Channel
import io.github.dewfallapp.core.model.ChannelId
import io.github.dewfallapp.core.model.ChannelOverview
import io.github.dewfallapp.core.model.ChannelUploads
import io.github.dewfallapp.core.model.Download
import io.github.dewfallapp.core.model.DownloadStatus
import io.github.dewfallapp.core.model.SubscribedChannel
import io.github.dewfallapp.core.model.UploadsCheck
import io.github.dewfallapp.core.model.VideoId
import io.github.dewfallapp.core.model.VideoKind
import io.github.dewfallapp.core.model.VideoSummary
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Instant
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.onStart

internal val KETTLE =
    Channel(ChannelId("UCaaaaaaaaaaaaaaaaaaaaaa"), "Kettle and Crumb", emptyList(), "")
internal val SUBSCRIBED_AT = Instant.parse("2026-11-16T09:00:00Z")

internal val VIDEO =
    VideoSummary(
        id = VideoId("aaaaaaaaaa1"),
        title = "Wild yeasts",
        channelId = KETTLE.id,
        channelName = KETTLE.name,
        duration = 54.minutes,
        published = null,
        thumbnails = emptyList(),
        kind = VideoKind.VIDEO,
    )

/** Subscriptions the test sets. Only the list is read; the rest isn't used by the inbox. */
internal class FakeSubscriptions : SubscriptionsRepository {
    val channels = MutableStateFlow<List<SubscribedChannel>>(emptyList())

    override fun subscribedChannels(): Flow<List<SubscribedChannel>> = channels

    override fun channelOverviews(): Flow<List<ChannelOverview>> = error("Not used")

    override fun subscribedChannel(channelId: ChannelId) = error("Not used")

    override fun isSubscribed(channelId: ChannelId) = error("Not used")

    override suspend fun subscribe(channel: Channel) = error("Not used")

    override suspend fun subscribeAll(channels: List<Channel>): Int = error("Not used")

    override suspend fun unsubscribe(channelId: ChannelId) = error("Not used")

    override suspend fun moveSubscription(from: ChannelId, to: Channel) = error("Not used")
}

/** The inbox's videos the test sets. Saving isn't used by the inbox. */
internal class FakeStoredVideos : StoredVideosRepository {
    val inbox = MutableStateFlow<List<VideoSummary>>(emptyList())

    /** When true, the read throws, as a damaged database would. */
    var fails = false

    /** How many times a read of the inbox's videos started. */
    var starts = 0

    override suspend fun saveUploads(uploads: ChannelUploads) = error("Not used")

    override fun inboxVideos(): Flow<List<VideoSummary>> =
        (if (fails) flow { error("Damaged database") } else inbox).onStart { starts++ }
}

/** A check the test sets, which counts its starts. */
internal class FakeChecker : UploadsChecker {
    override val state = MutableStateFlow(UploadsCheck())

    var starts = 0

    override fun start() {
        starts++
    }
}

/**
 * A downloads repository that answers from [statuses] and records its writes. A test can make reads
 * or writes throw.
 */
internal class FakeDownloads : DownloadsRepository {
    val statuses = MutableStateFlow<Map<VideoId, DownloadStatus>>(emptyMap())
    val writes = mutableListOf<String>()
    var failReads = false
    var failWrites = false

    override fun statuses(): Flow<Map<VideoId, DownloadStatus>> =
        if (failReads) {
            flow { throw IllegalStateException("The database can't be read") }
        } else {
            statuses
        }

    override fun status(videoId: VideoId): Flow<DownloadStatus> =
        error("The inbox reads every status at once")

    override fun bytesInUse(): Flow<Long> = error("The inbox shows no storage")

    override suspend fun download(video: VideoSummary) {
        writes += "download ${video.id.value}"
        check(!failWrites) { "The disk is full" }
    }

    override suspend fun pause(videoId: VideoId) {
        writes += "pause ${videoId.value}"
        check(!failWrites) { "The disk is full" }
    }

    override fun downloads(): Flow<List<Download>> = error("The inbox doesn't list downloads")

    override suspend fun remove(videoId: VideoId) = error("The inbox doesn't remove downloads")
}
