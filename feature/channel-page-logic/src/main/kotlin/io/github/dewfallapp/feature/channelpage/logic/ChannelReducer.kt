package io.github.dewfallapp.feature.channelpage.logic

import io.github.dewfallapp.core.model.ChannelUploads
import io.github.dewfallapp.core.model.VideoKind
import io.github.dewfallapp.core.model.VideoSummary
import io.github.dewfallapp.core.model.YouTubeResult
import io.github.dewfallapp.core.mvi.Next
import io.github.dewfallapp.core.mvi.Reducer

/**
 * The channel page's reducer. A load's answer counts only while that load is running. Subscribing
 * and unsubscribing change no state here: the ViewModel writes to the database, and the card
 * changes when the database answers, so it shows what is kept, never a guess. The page has no
 * effects, so its effect type is Nothing.
 */
object ChannelReducer : Reducer<ChannelState, ChannelIntent, Nothing> {
    override fun reduce(state: ChannelState, intent: ChannelIntent): Next<ChannelState, Nothing> =
        Next(
            when (intent) {
                is ChannelIntent.LoadFinished -> state.finished(intent)
                ChannelIntent.TryAgain -> state.triedAgain()
                is ChannelIntent.SubscriptionRead -> state.read(intent)
                ChannelIntent.Subscribe -> state
                ChannelIntent.UnsubscribeTapped -> state.askedToUnsubscribe()
                ChannelIntent.UnsubscribeConfirmed,
                ChannelIntent.UnsubscribeCancelled -> state.copy(confirmingUnsubscribe = false)
                ChannelIntent.DescriptionToggled -> {
                    state.copy(descriptionExpanded = !state.descriptionExpanded)
                }
            },
        )
}

/** Shows the answer in [intent], if it is for the load that is running. */
private fun ChannelState.finished(intent: ChannelIntent.LoadFinished): ChannelState {
    if (load != LoadArea.Loading(intent.number)) return this
    return when (val result = intent.result) {
        is YouTubeResult.Success -> loaded(intent.number, result.value)
        is YouTubeResult.Failure -> {
            copy(
                load = LoadArea.Failed(intent.number, result.error),
                shownChannel = shownChannel ?: pageIds.firstNotNullOfOrNull { reads[it] },
            )
        }
    }
}

/**
 * Shows [uploads]. When YouTube answered for another ID than the opened one, that ID joins the
 * page's IDs, and the card hides until the database answers for it.
 */
private fun ChannelState.loaded(number: Long, uploads: ChannelUploads): ChannelState {
    val channel = uploads.channel
    val rows =
        uploads.videos.filter { it.kind != VideoKind.SHORT }.distinctBy { it.id }.map(::toRow)
    return copy(
        answeredId = channel.id.takeIf { it != openedId },
        load = LoadArea.Loaded(number, rows),
        shownChannel = channel,
    )
}

/** Loads again after a failure that offers "Try again", or changes nothing. */
private fun ChannelState.triedAgain(): ChannelState {
    val failed = load as? LoadArea.Failed
    return if (failed != null && failed.error.offersTryAgain) {
        copy(load = LoadArea.Loading(failed.number + 1))
    } else {
        this
    }
}

/**
 * Keeps the database's answer for one of the page's IDs. After a load failed, a kept channel
 * becomes the one the page shows, if it shows none yet, so a followed channel shows offline. While
 * the channel loads, the page shows no channel and no card, as the spec asks.
 */
private fun ChannelState.read(intent: ChannelIntent.SubscriptionRead): ChannelState {
    if (intent.channelId !in pageIds) return this
    val stored = intent.stored
    return copy(
        reads = reads + (intent.channelId to stored),
        shownChannel =
            if (load is LoadArea.Failed && shownChannel == null) stored else shownChannel,
    )
}

/** Opens the dialog, only while the card says "Subscribed". */
private fun ChannelState.askedToUnsubscribe(): ChannelState =
    if (showsCard && subscription is Subscription.Subscribed) {
        copy(confirmingUnsubscribe = true)
    } else {
        this
    }

private fun toRow(video: VideoSummary): UploadRow =
    UploadRow(
        id = video.id,
        title = video.title,
        duration = video.duration,
        kind = video.kind,
        published = video.published,
        thumbnails = video.thumbnails,
    )
