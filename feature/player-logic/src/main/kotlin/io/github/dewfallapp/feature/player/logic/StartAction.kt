package io.github.dewfallapp.feature.player.logic

/** What the video screen does with playback when its back stack entry shows. */
enum class StartAction {
    /** The entry opened for the first time: start playing. */
    PLAY,

    /**
     * The entry opened for the first time, and the player already plays this video, such as from
     * the mini player: go on with that playback as it is, and start nothing.
     */
    GO_ON,

    /**
     * The entry came back after the system ended the process or stopped the player: wait at the
     * saved position for the user to press play, and fetch nothing until then.
     */
    WAIT_FOR_PLAY,

    /** The entry is back after a configuration change, and its playback goes on. */
    NOTHING,
}

/**
 * What the video screen does with playback when its entry shows: whether playback [started] before,
 * as the entry's saved state says, whether its playback ID is still [playbackIdKnown] to the
 * player, which it isn't after the session was gone, and whether the player's current item
 * [playsThisVideo], a playback of this screen's video that another screen started.
 */
fun startAction(started: Boolean, playbackIdKnown: Boolean, playsThisVideo: Boolean): StartAction =
    when {
        !started && playsThisVideo -> StartAction.GO_ON
        !started -> StartAction.PLAY
        playbackIdKnown -> StartAction.NOTHING
        else -> StartAction.WAIT_FOR_PLAY
    }
