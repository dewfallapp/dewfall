package io.github.dewfallapp.navigation

import androidx.navigation3.runtime.NavKey
import io.github.dewfallapp.core.model.ChannelId
import io.github.dewfallapp.core.model.VideoId
import java.util.UUID

/**
 * Opens the video screen for [videoId], from the inbox, search results, a channel page or the mini
 * player, unless a video screen is already on top. A second tap that lands while the video screen
 * opens finds a PlayerKey on top, so it opens nothing. So the back stack holds at most one
 * PlayerKey, always on top, because opening a channel from a video screen removes that screen
 * first.
 */
internal fun MutableList<NavKey>.openVideo(videoId: VideoId) {
    if (lastOrNull() !is PlayerKey) {
        add(PlayerKey(videoId, entryId = UUID.randomUUID().toString()))
    }
}

/**
 * Opens the channel page for [channelId] from the screen of [from], such as a video screen or
 * Search YouTube, only while that screen is on top, so a second tap, which finds the channel page
 * on top, opens nothing. From a video screen it removes that screen, as its minimize button does,
 * so the video goes on in the mini player. When the screen under it is then already a page for the
 * same channel ID, that page shows again and no second one opens.
 */
internal fun MutableList<NavKey>.openChannel(from: NavKey, channelId: ChannelId) {
    if (lastOrNull() != from) return
    if (from is PlayerKey) remove(from)
    val under = lastOrNull()
    if (under is ChannelKey && under.channelId == channelId) return
    add(ChannelKey(channelId, entryId = UUID.randomUUID().toString()))
}

/**
 * Opens Search YouTube from the inbox, only while the inbox is on top, so a second tap while it
 * opens adds nothing.
 */
internal fun MutableList<NavKey>.openSearch() {
    if (lastOrNull() == InboxKey) add(SearchKey(entryId = UUID.randomUUID().toString()))
}

/**
 * Opens the channel page for [channelId] from one of the inbox's cards, only while the inbox is on
 * top, so a second tap while it opens adds nothing.
 */
internal fun MutableList<NavKey>.openChannelFromInbox(channelId: ChannelId) {
    if (lastOrNull() == InboxKey) add(ChannelKey(channelId, entryId = UUID.randomUUID().toString()))
}

/**
 * Closes the screen of [key], if it is still on the back stack, as the video screen's minimize
 * button and the back arrows of the channel page and Search YouTube do. A video's playback goes on
 * in the mini player. A second tap on the button, or one during the back gesture, finds it gone and
 * does nothing, so the inbox always stays. An empty back stack would crash, because NavDisplay
 * requires at least one entry.
 */
internal fun MutableList<NavKey>.close(key: NavKey) {
    if (key != InboxKey) remove(key)
}

/** Goes back one screen for the system's back gesture, but never past the first one. */
internal fun MutableList<NavKey>.goBack() {
    if (size > 1) removeAt(lastIndex)
}

/**
 * Whether the mini player shows under the back stack: while no video screen is on top. It draws
 * nothing itself while the player has no video.
 */
internal fun List<NavKey>.showsMiniPlayer(): Boolean = lastOrNull() !is PlayerKey
