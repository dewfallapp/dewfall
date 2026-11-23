package io.github.dewfallapp.feature.channels.logic

import io.github.dewfallapp.core.mvi.Next
import io.github.dewfallapp.core.mvi.Reducer

/**
 * The Channels screen's reducer. Each read replaces the list: an empty one says there are no
 * channels, and any other is shown in its order. The screen has no effects, so its effect type is
 * Nothing.
 */
object ChannelsReducer : Reducer<ChannelsState, ChannelsIntent, Nothing> {
    override fun reduce(state: ChannelsState, intent: ChannelsIntent): Next<ChannelsState, Nothing> =
        Next(
            when (intent) {
                is ChannelsIntent.ChannelsRead -> {
                    ChannelsState(
                        if (intent.channels.isEmpty()) {
                            ChannelsList.NoChannels
                        } else {
                            ChannelsList.Shown(intent.channels)
                        },
                    )
                }
            },
        )
}
