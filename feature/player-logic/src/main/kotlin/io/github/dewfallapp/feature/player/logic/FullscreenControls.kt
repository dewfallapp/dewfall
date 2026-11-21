package io.github.dewfallapp.feature.player.logic

/**
 * Whether the fullscreen player's title, controls and system bars show. [shown] is what the user
 * last chose with a tap, or what the last change of playing set. They always show while the video
 * isn't playing or a screen reader is on, as Media3's PlayerView does when playback pauses, ends or
 * fails. See openspec/specs/player/spec.md.
 */
data class FullscreenControls(val shown: Boolean = true) {
    /** Whether they show, while the video is [playing] or not and a [screenReader] is on or not. */
    fun visible(playing: Boolean, screenReader: Boolean): Boolean =
        !playing || screenReader || shown

    /**
     * After a tap on the picture: they toggle while the video is [playing] and no [screenReader] is
     * on. Otherwise they stay, and a later tap starts from them showing.
     */
    fun tapped(playing: Boolean, screenReader: Boolean): FullscreenControls =
        if (playing && !screenReader) copy(shown = !shown) else copy(shown = true)

    /** After the video started or stopped playing: they show. */
    fun playingChanged(): FullscreenControls = copy(shown = true)

    /** After a touch or a key: they show, and the wait starts again. */
    fun touched(): FullscreenControls = copy(shown = true)

    /** After the wait while they showed and the video played: they hide. */
    fun timedOut(): FullscreenControls = copy(shown = false)

    /**
     * Whether they hide after the wait: only while they show, the video is [playing], and no
     * [screenReader] is on.
     */
    fun hidesAfterTimeout(playing: Boolean, screenReader: Boolean): Boolean =
        shown && playing && !screenReader
}
