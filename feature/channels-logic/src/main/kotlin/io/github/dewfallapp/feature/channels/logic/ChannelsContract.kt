package io.github.dewfallapp.feature.channels.logic

import io.github.dewfallapp.core.model.ChannelOverview
import io.github.dewfallapp.core.mvi.MviIntent
import io.github.dewfallapp.core.mvi.MviState

/** What the Channels screen lists. The channels are user data, so never log them. */
sealed interface ChannelsList {
    /** Before the phone's first answer, for the moment before Room answers. */
    data object Loading : ChannelsList

    /** The user subscribes to no channel. */
    data object NoChannels : ChannelsList

    /** The subscribed channels, in the repository's order, by name. Never empty. */
    data class Shown(val channels: List<ChannelOverview>) : ChannelsList
}

/** The Channels screen's state. */
data class ChannelsState(val list: ChannelsList = ChannelsList.Loading) : MviState

/** What happens to the Channels screen. Every intent is a result of the ViewModel's read. */
sealed interface ChannelsIntent : MviIntent {
    /** The phone answered with every subscribed channel, sorted by name. */
    data class ChannelsRead(val channels: List<ChannelOverview>) : ChannelsIntent
}
