package io.github.dewfallapp.navigation

import androidx.navigation3.runtime.NavKey
import io.github.dewfallapp.core.model.VideoId
import java.util.UUID

/**
 * Opens the video screen for [videoId], only while Search YouTube is on top. A second tap that
 * lands while the video screen opens finds a PlayerKey on top, so it opens nothing.
 */
internal fun MutableList<NavKey>.openVideo(videoId: VideoId) {
    if (lastOrNull() == SearchKey) add(PlayerKey(videoId, entryId = UUID.randomUUID().toString()))
}

/**
 * Closes the video screen of [key], if it is still on the back stack, as its minimize button does.
 * Its playback goes on in the mini player. A second tap on the button, or one during the back
 * gesture, finds it gone and does nothing, so Search YouTube always stays. An empty back stack
 * would crash, because NavDisplay requires at least one entry.
 */
internal fun MutableList<NavKey>.close(key: PlayerKey) {
    remove(key)
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
