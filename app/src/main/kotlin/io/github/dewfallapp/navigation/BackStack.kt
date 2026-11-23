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
 * Opens the channel page for [channelId] from the screen of [from], such as a video screen, Search
 * YouTube, an inbox card or a row of the Channels screen, only while that screen is on top, so a
 * second tap, which finds the channel page on top, opens nothing. From a video screen it removes
 * that screen, as its minimize button does, so the video goes on in the mini player. When the
 * screen under it is then already a page for the same channel ID, that page shows again and no
 * second one opens.
 */
internal fun MutableList<NavKey>.openChannel(from: NavKey, channelId: ChannelId) {
    if (lastOrNull() != from) return
    if (from is PlayerKey) remove(from)
    val under = lastOrNull()
    if (under is ChannelKey && under.channelId == channelId) return
    add(ChannelKey(channelId, entryId = UUID.randomUUID().toString()))
}

/**
 * Opens Search YouTube from the screen of [from], the inbox or the Channels screen, only while that
 * screen is on top, so a second tap while it opens adds nothing.
 */
internal fun MutableList<NavKey>.openSearch(from: NavKey) {
    if (lastOrNull() == from) add(SearchKey(entryId = UUID.randomUUID().toString()))
}

/**
 * Opens the Takeout import screen from the screen of [from], the inbox or the Channels screen, with
 * its "Import from Google Takeout" button, only while that screen is on top, so a second tap while
 * it opens adds nothing.
 */
internal fun MutableList<NavKey>.openTakeoutImport(from: NavKey) {
    if (lastOrNull() == from) add(TakeoutImportKey(entryId = UUID.randomUUID().toString()))
}

/**
 * Closes the screen of [key], if it is still on the back stack, as the video screen's minimize
 * button, the back arrows of the channel page, Search YouTube and the Takeout import, and the
 * import's Done button do. A video's playback goes on in the mini player. A second tap on the
 * button, or one during the back gesture, finds it gone and does nothing, so the first screen, the
 * inbox or the Channels screen, always stays. An empty back stack would crash, because NavDisplay
 * requires at least one entry.
 */
internal fun MutableList<NavKey>.close(key: NavKey) {
    if (key != InboxKey && key != ChannelsKey) remove(key)
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

/**
 * Whether the navigation bar shows under the back stack: while the inbox, the Channels screen or a
 * channel page is on top, and not on Search YouTube or the video screen, as the design's frames
 * have it.
 */
internal fun List<NavKey>.showsNavigationBar(): Boolean =
    when (lastOrNull()) {
        InboxKey,
        ChannelsKey,
        is ChannelKey -> true
        else -> false
    }
