package io.github.dewfallapp.core.model

/**
 * Why a call to the YouTube layer failed, in categories that a screen or a background job can act
 * on. The first three mean that nothing from YouTube can load right now. The others mean that one
 * video or channel can't be shown, while the rest of YouTube works.
 *
 * It is an enum, so it holds no user data, its toString is the constant's name, and LogField.state
 * can log it. A screen turns it into text with a string resource. See ADR 0008.
 */
enum class YouTubeError {
    /**
     * YouTube couldn't be reached: there is no connection, or the request failed or timed out on
     * the way. The phone may be offline, which only Android code can tell.
     */
    NETWORK,

    /**
     * YouTube refused to answer, such as by asking for a captcha or for proof that the user isn't a
     * bot. Trying again later may work.
     */
    BLOCKED,

    /**
     * YouTube answered, but the YouTube layer couldn't read the answer. YouTube has probably
     * changed something, and an app update is needed. See docs/breakage.md.
     */
    BROKEN,

    /** The video is private. */
    PRIVATE,

    /** The video is age-restricted, so only a signed-in adult can watch it. */
    AGE_RESTRICTED,

    /** The video isn't available in the user's country. */
    COUNTRY_BLOCKED,

    /** The video is only for the channel's paying members, or has to be bought or rented. */
    MEMBERS_OR_PAID,

    /** YouTube closed the channel, or the channel of the video. */
    CHANNEL_CLOSED,

    /**
     * YouTube says the video or channel can't be shown, with no reason the layer can tell apart. It
     * may have been removed or never existed, or it may be a premiere or live stream that hasn't
     * started.
     */
    UNAVAILABLE,
}
