package io.github.dewfallapp.navigation

import androidx.navigation3.runtime.NavKey
import io.github.dewfallapp.core.model.ChannelId
import io.github.dewfallapp.core.model.VideoId
import java.util.UUID

/**
 * Opens the video screen for [videoId], from search results, a channel page or the mini player,
 * unless a video screen is already on top. A second tap that lands while the video screen opens
 * finds a PlayerKey on top, so it opens nothing. So the back stack holds at most one PlayerKey,
 * always on top, because opening a channel from a video screen removes that screen first.
 */
internal fun MutableList<NavKey>.openVideo(videoId: VideoId) {
    if (lastOrNull() !is PlayerKey) {
        add(PlayerKey(videoId, entryId = UUID.randomUUID().toString()))
    }
}

/**
 * Opens the channel page for [channelId] from the video screen of [from], only while that screen is
 * on top. It removes the video screen, as its minimize button does, so the video goes on in the
 * mini player. When the screen under it is already a page for the same channel ID, that page shows
 * again and no second one opens. A second tap finds [from] gone, so it opens nothing.
 */
internal fun MutableList<NavKey>.openChannel(from: PlayerKey, channelId: ChannelId) {
    if (lastOrNull() != from) return
    remove(from)
    val under = lastOrNull()
    if (under is ChannelKey && under.channelId == channelId) return
    add(ChannelKey(channelId, entryId = UUID.randomUUID().toString()))
}

/**
 * Closes the screen of [key], if it is still on the back stack, as the video screen's minimize
 * button and the channel page's back arrow do. A video's playback goes on in the mini player. A
 * second tap on the button, or one during the back gesture, finds it gone and does nothing, so
 * Search YouTube always stays. An empty back stack would crash, because NavDisplay requires at
 * least one entry.
 */
internal fun MutableList<NavKey>.close(key: NavKey) {
    if (key != SearchKey) remove(key)
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
