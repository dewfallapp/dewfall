package io.github.dewfallapp.navigation

import androidx.compose.runtime.MutableState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.navigation3.runtime.NavKey
import io.github.dewfallapp.core.model.ChannelId
import io.github.dewfallapp.core.model.VideoId

/** The navigation bar's destinations, in the bar's order. */
internal enum class Destination {
    INBOX,
    CHANNELS,
    DOWNLOADS,
}

/**
 * The app's back stacks, one for each destination of the navigation bar, and which one shows. It
 * follows Navigation 3's multiple back stacks recipe
 * (https://developer.android.com/guide/navigation/navigation-3/recipes/multiple-backstacks): each
 * destination keeps its own history while another shows, and the user always leaves the app through
 * the inbox. The changes on one stack are BackStack.kt's functions, which this class calls on the
 * selected destination's stack. See docs/architecture.md#navigation.
 *
 * [inbox] starts with InboxKey, [channels] with ChannelsKey and [downloads] with DownloadsKey, and
 * none ever loses its first key. [selection] holds the destination that shows, so the caller can
 * save it.
 */
internal class AppNavigation(
    val inbox: MutableList<NavKey>,
    val channels: MutableList<NavKey>,
    val downloads: MutableList<NavKey>,
    selection: MutableState<Destination>,
) {
    /** The destination that shows. */
    var selected: Destination by selection
        private set

    /** The selected destination's back stack. */
    val current: MutableList<NavKey>
        get() = stackOf(selected)

    private fun stackOf(destination: Destination): MutableList<NavKey> =
        when (destination) {
            Destination.INBOX -> inbox
            Destination.CHANNELS -> channels
            Destination.DOWNLOADS -> downloads
        }

    /**
     * What NavDisplay shows, in order, from [inboxItems], [channelsItems] and [downloadsItems], the
     * stacks' keys or their decorated entries: the inbox's, then those of the selected destination
     * when it isn't the inbox. So the inbox sits under the Channels and Downloads screens, and
     * going back from either shows the inbox.
     */
    fun <T> shown(inboxItems: List<T>, channelsItems: List<T>, downloadsItems: List<T>): List<T> =
        when (selected) {
            Destination.INBOX -> inboxItems
            Destination.CHANNELS -> inboxItems + channelsItems
            Destination.DOWNLOADS -> inboxItems + downloadsItems
        }

    /**
     * Shows [destination], as a tap on the navigation bar does. A tap on the destination that
     * already shows closes the screens opened from it and shows its first screen, and on its first
     * screen it changes nothing.
     */
    fun select(destination: Destination) {
        if (destination != selected) {
            selected = destination
        } else {
            val stack = current
            while (stack.size > 1) {
                stack.removeAt(stack.lastIndex)
            }
        }
    }

    fun openVideo(videoId: VideoId) = current.openVideo(videoId)

    fun openChannel(from: NavKey, channelId: ChannelId) = current.openChannel(from, channelId)

    fun openSearch(from: NavKey) = current.openSearch(from)

    fun openTakeoutImport(from: NavKey) = current.openTakeoutImport(from)

    /**
     * Closes the screen of [key] on whichever stack holds it, as a back arrow or the minimize
     * button does. No stack's first key is ever removed.
     */
    fun close(key: NavKey) {
        for (destination in Destination.entries) {
            stackOf(destination).close(key)
        }
    }

    /**
     * Goes back one screen for the system's back gesture: the selected stack's top screen, or, on
     * the Channels or Downloads screen itself, back to the inbox's destination as it was.
     */
    fun goBack() {
        val stack = current
        if (stack.size > 1) {
            stack.removeAt(stack.lastIndex)
        } else if (selected != Destination.INBOX) {
            selected = Destination.INBOX
        }
    }

    /** Whether the navigation bar shows: see [showsNavigationBar]. */
    fun showsNavigationBar(): Boolean = current.showsNavigationBar()

    /** Whether the mini player shows: see [showsMiniPlayer]. */
    fun showsMiniPlayer(): Boolean = current.showsMiniPlayer()
}
