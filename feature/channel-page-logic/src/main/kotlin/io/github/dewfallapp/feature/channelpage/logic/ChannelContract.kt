package io.github.dewfallapp.feature.channelpage.logic

import io.github.dewfallapp.core.model.Channel
import io.github.dewfallapp.core.model.ChannelId
import io.github.dewfallapp.core.model.ChannelUploads
import io.github.dewfallapp.core.model.PublishedTime
import io.github.dewfallapp.core.model.VideoId
import io.github.dewfallapp.core.model.VideoKind
import io.github.dewfallapp.core.model.YouTubeError
import io.github.dewfallapp.core.model.YouTubeResult
import io.github.dewfallapp.core.mvi.MviIntent
import io.github.dewfallapp.core.mvi.MviState
import kotlin.time.Duration

/**
 * One of the channel's latest uploads, as a row shows it, without the channel's name. It keeps the
 * ID, so a row can open the video. The title is user data, so never log it.
 */
data class UploadRow(
    val id: VideoId,
    val title: String,
    /** The length, or null for a live stream, a video that hasn't started, or if unknown. */
    val duration: Duration?,
    val kind: VideoKind,
    /** When it was published, or is scheduled to start, or null if YouTube gave no time. */
    val published: PublishedTime?,
)

/** What the page shows in place of the uploads. */
sealed interface LoadArea {
    /** The number of the load this area belongs to. Each load gets a new one. */
    val number: Long

    data class Loading(override val number: Long) : LoadArea

    /** The load succeeded with [rows], newest first, which can be empty. */
    data class Loaded(override val number: Long, val rows: List<UploadRow>) : LoadArea

    data class Failed(override val number: Long, val error: YouTubeError) : LoadArea
}

/** Whether the user is subscribed to the channel, as the database says. */
sealed interface Subscription {
    /** The database hasn't answered yet for every ID of the page. */
    data object Unknown : Subscription

    data object NotSubscribed : Subscription

    /** The user is subscribed, and the database keeps the channel as [stored]. */
    data class Subscribed(val stored: Channel) : Subscription
}

/**
 * The channel page's state. The page's IDs are [openedId], the ID it was opened with, and
 * [answeredId], the one YouTube answered for when it sends the channel on to another. The
 * subscription covers both, so a subscription under an old ID still shows, and Unsubscribe removes
 * it. Channel IDs, names and descriptions are user data, so never log them.
 */
data class ChannelState(
    val openedId: ChannelId,
    /** The ID YouTube answered for, when a load gave another one than [openedId]. */
    val answeredId: ChannelId? = null,
    val load: LoadArea = LoadArea.Loading(number = 1),
    /**
     * What the database said for each of the page's IDs. A missing key means not read yet, and a
     * null value means not subscribed under that ID.
     */
    val reads: Map<ChannelId, Channel?> = emptyMap(),
    /**
     * The channel the page shows: the loaded one, or, after a load failed, the one the database
     * keeps. Nothing clears it, so the page keeps showing the channel after an unsubscribe.
     */
    val shownChannel: Channel? = null,
    val descriptionExpanded: Boolean = false,
    val confirmingUnsubscribe: Boolean = false,
) : MviState {
    /** The page's IDs, the answered one first. */
    val pageIds: List<ChannelId>
        get() = listOfNotNull(answeredId, openedId)

    /** Whether the user is subscribed under any of the page's IDs, once every one is read. */
    val subscription: Subscription
        get() {
            val stored = pageIds.firstNotNullOfOrNull { reads[it] }
            return when {
                pageIds.any { it !in reads } -> Subscription.Unknown
                stored == null -> Subscription.NotSubscribed
                else -> Subscription.Subscribed(stored)
            }
        }

    /** The IDs the user is subscribed under, which Unsubscribe removes. */
    val subscribedIds: List<ChannelId>
        get() = pageIds.filter { reads[it] != null }

    /** Whether the subscription card shows: once the page has a channel and knows the answer. */
    val showsCard: Boolean
        get() = shownChannel != null && subscription != Subscription.Unknown
}

/** What happens on the channel page. */
sealed interface ChannelIntent : MviIntent {
    /** Load [number] answered with [result]. */
    data class LoadFinished(val number: Long, val result: YouTubeResult<ChannelUploads>) :
        ChannelIntent

    /** The user tapped "Try again" after a failure. */
    data object TryAgain : ChannelIntent

    /** The database said that the user is subscribed under [channelId] as [stored], or isn't. */
    data class SubscriptionRead(val channelId: ChannelId, val stored: Channel?) : ChannelIntent

    /** The user tapped "Subscribe". The ViewModel keeps the subscription. */
    data object Subscribe : ChannelIntent

    /** The user tapped "Unsubscribe" on the card, which opens the dialog. */
    data object UnsubscribeTapped : ChannelIntent

    /** The user confirmed in the dialog. The ViewModel removes the subscription. */
    data object UnsubscribeConfirmed : ChannelIntent

    /** The user closed the dialog without unsubscribing. */
    data object UnsubscribeCancelled : ChannelIntent

    /** The user tapped "More" or "Less" under the description. */
    data object DescriptionToggled : ChannelIntent
}
