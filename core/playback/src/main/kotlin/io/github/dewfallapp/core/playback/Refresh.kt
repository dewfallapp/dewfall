package io.github.dewfallapp.core.playback

/**
 * The player that a refresh of expired links acts on. PlaybackService's adapter implements it over
 * its ExoPlayer, and tests implement it with a fake, so the steps run on the JVM.
 */
internal interface RefreshTarget<Item> {
    /** Puts [item], with the new links, in place of the current item. */
    fun replaceItem(item: Item)

    /** Goes to [positionMs] in the item. */
    fun seekTo(positionMs: Long)

    /** Goes to a live stream's live edge. */
    fun seekToLiveEdge()

    /** Loads the item again after the failure. */
    fun prepare()

    /** Plays. */
    fun play()
}

/** Where the player was when a link was refused. */
internal data class RefreshPoint(val positionMs: Long, val live: Boolean, val wasPlaying: Boolean)

/**
 * Puts [item], with new links, in place of the refused one on [target]: at the old position for a
 * video, or at the live edge for a live stream, playing only if it was playing.
 */
internal fun <Item> refresh(target: RefreshTarget<Item>, item: Item, point: RefreshPoint) {
    target.replaceItem(item)
    if (point.live) target.seekToLiveEdge() else target.seekTo(point.positionMs)
    target.prepare()
    if (point.wasPlaying) target.play()
}

/**
 * Allows one refresh of expired links for each failure. The limit resets once the refreshed item is
 * ready to play again, so a second expiry hours later refreshes too, while two refusals in a row
 * stop.
 */
internal class RefreshLimit {
    private var refreshed = false

    /** Whether a failure with [httpStatus] fetches new links. It counts as the one refresh. */
    fun shouldRefresh(httpStatus: Int?): Boolean {
        val refresh = isRefusedLink(httpStatus) && !refreshed
        if (refresh) refreshed = true
        return refresh
    }

    /** The item is ready to play, or another item took its place. */
    fun reset() {
        refreshed = false
    }
}
