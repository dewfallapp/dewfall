package io.github.dewfallapp.core.model

/**
 * Why a video stopped playing or couldn't start, in categories that the video screen turns into
 * words. It is an enum, so it holds no user data, and LogField.state can log it. See ADR 0007 and
 * docs/architecture.md#the-player.
 */
enum class PlaybackFailure {
    /** YouTube can't be reached, or the network failed while playing. */
    NETWORK,

    /**
     * YouTube refused to answer or can't be read, a link was refused even after new links, or the
     * YouTube layer refused a request.
     */
    YOUTUBE,
    PRIVATE,
    AGE_RESTRICTED,
    COUNTRY_BLOCKED,
    MEMBERS_OR_PAID,
    CHANNEL_CLOSED,
    UNAVAILABLE,

    /** The phone decodes none of the video's formats, or a decoder failed while playing. */
    NOT_DECODABLE,

    /** Anything else. */
    OTHER;

    /** Whether "Try again" can help. */
    val offersTryAgain: Boolean
        get() = this == NETWORK || this == YOUTUBE || this == OTHER

    companion object {
        /** The category of a stream links call that failed with [error]. */
        fun of(error: YouTubeError): PlaybackFailure =
            when (error) {
                YouTubeError.NETWORK -> NETWORK
                YouTubeError.BLOCKED,
                YouTubeError.BROKEN -> YOUTUBE
                YouTubeError.PRIVATE -> PRIVATE
                YouTubeError.AGE_RESTRICTED -> AGE_RESTRICTED
                YouTubeError.COUNTRY_BLOCKED -> COUNTRY_BLOCKED
                YouTubeError.MEMBERS_OR_PAID -> MEMBERS_OR_PAID
                YouTubeError.CHANNEL_CLOSED -> CHANNEL_CLOSED
                YouTubeError.UNAVAILABLE -> UNAVAILABLE
            }
    }
}
