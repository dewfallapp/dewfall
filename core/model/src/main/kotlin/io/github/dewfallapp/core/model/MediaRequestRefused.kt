package io.github.dewfallapp.core.model

import java.io.IOException

/**
 * The YouTube layer refused to send a request for a video's bytes, because of [reason]. Nothing was
 * sent. It is an IOException, because the player's HTTP data source passes only IOException on. Its
 * message is constant text, because a link is user data and messages end up in logs. See ADR 0007
 * and docs/architecture.md#the-player.
 */
class MediaRequestRefused(val reason: Reason) :
    IOException("The YouTube layer refused a request for video bytes") {

    /** Why the YouTube layer refused a request. */
    enum class Reason {
        /** The link or a redirect isn't HTTPS. */
        NOT_HTTPS,

        /** The link or a redirect points to a host outside googlevideo.com. */
        HOST_NOT_ALLOWED,

        /** The request was redirected more times than the layer follows. */
        TOO_MANY_REDIRECTS,

        /**
         * The link was made for a YouTube client whose User-Agent the layer doesn't know, or no
         * stream links call has given the User-Agent yet.
         */
        UNKNOWN_CLIENT,
    }
}
