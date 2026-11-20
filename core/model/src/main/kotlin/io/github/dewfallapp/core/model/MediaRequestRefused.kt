package io.github.dewfallapp.core.model

import java.io.IOException

/**
 * The YouTube layer, or the player for an item without links, refused to send a request for a
 * video's bytes, because of [reason]. Nothing was sent. It is an IOException, because the player's
 * HTTP data source passes only IOException on. Its message is constant text, because a link is user
 * data and messages end up in logs. See ADR 0007 and docs/architecture.md#the-player.
 */
class MediaRequestRefused(val reason: Reason) :
    IOException("A request for video bytes was refused") {

    /** Why the request was refused, by the YouTube layer or, for NO_LINKS, by the player. */
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

        /**
         * The player's item never got its links from the YouTube layer, so the player sends nothing
         * for it.
         */
        NO_LINKS,
    }
}
