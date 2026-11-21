package io.github.dewfallapp.feature.player.logic

/**
 * What the mini player shows for the player's current item. [playbackId] is what its tap opens and
 * its close button stops. [title] is null when the item has none yet, and the mini player then says
 * "Now playing". [channelName] is null when there is none. [showsProgress] is false for a live
 * stream, which has no progress line. The title and the channel name are user data, so toString
 * never shows them.
 */
class MiniPlayerContent(
    val playbackId: String,
    val title: String?,
    val channelName: String?,
    val showsProgress: Boolean,
) {
    override fun equals(other: Any?): Boolean =
        other is MiniPlayerContent &&
            playbackId == other.playbackId &&
            title == other.title &&
            channelName == other.channelName &&
            showsProgress == other.showsProgress

    override fun hashCode(): Int {
        var result = playbackId.hashCode()
        result = 31 * result + title.hashCode()
        result = 31 * result + channelName.hashCode()
        return 31 * result + showsProgress.hashCode()
    }

    override fun toString(): String = "MiniPlayerContent(hidden)"
}

/**
 * What the mini player shows, from the player's [currentItemId], the item's [title] and
 * [channelName] as the session gives them, and whether it is [live]. It returns null when there is
 * no current item, so the mini player draws nothing. An empty or blank title or channel name counts
 * as none.
 */
fun miniPlayerContent(
    currentItemId: String?,
    title: CharSequence?,
    channelName: CharSequence?,
    live: Boolean,
): MiniPlayerContent? {
    val playbackId = currentItemId ?: return null
    return MiniPlayerContent(
        playbackId = playbackId,
        title = title?.toString()?.takeIf { it.isNotBlank() },
        channelName = channelName?.toString()?.takeIf { it.isNotBlank() },
        showsProgress = !live,
    )
}
