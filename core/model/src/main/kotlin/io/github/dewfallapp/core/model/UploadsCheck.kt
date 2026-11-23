package io.github.dewfallapp.core.model

/**
 * The state of the check for new videos, which asks YouTube for every subscribed channel's latest
 * uploads and saves them. One check runs at a time in the whole app, so every screen sees the same
 * state. It holds no user data. See the subscriptions spec in openspec/.
 */
data class UploadsCheck(
    /** Whether a check runs now. */
    val running: Boolean = false,
    /**
     * Why the last check stopped before it reached every channel, or null when it didn't stop that
     * way or no check has run since the app started. A new check clears it.
     */
    val failure: UploadsCheckFailure? = null,
)

/** Why a check for new videos stopped early, in the two ways a screen tells apart. */
enum class UploadsCheckFailure {
    /** YouTube couldn't be reached: there is no connection, or a request failed on the way. */
    CANT_REACH_YOUTUBE,

    /**
     * YouTube refused to answer, or answered in a way the app can't read, as YouTubeError's BLOCKED
     * and BROKEN say.
     */
    YOUTUBE_NOT_LOADING,
}
