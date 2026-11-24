package io.github.dewfallapp.core.model

/** What an import of a Google Takeout file did. See docs/architecture.md#the-takeout-import. */
sealed interface TakeoutImportResult {
    /**
     * The file's channels are subscribed. [channels] holds every channel the file lists, once each,
     * in the file's order, with no avatars and an empty description. [added] of them weren't
     * subscribed before. [unreadableLines] counts the file's lines that held no channel. The
     * channels are user data, so never log them.
     */
    data class Imported(
        val channels: List<Channel>,
        val added: Int,
        val unreadableLines: Int,
    ) : TakeoutImportResult {
        /** How many of [channels] were already subscribed, and kept their details. */
        val alreadySubscribed: Int
            get() = channels.size - added
    }

    /** Nothing was subscribed, for [reason]. */
    data class Failed(val reason: TakeoutImportFailure) : TakeoutImportResult
}

/** Why an import of a Takeout file subscribed to nothing. */
enum class TakeoutImportFailure {
    /** The file holds no subscriptions table with a channel in it. */
    NO_CHANNELS,

    /** The file couldn't be opened or read, such as a damaged .zip file. */
    NOT_READABLE,

    /** A .csv file over 8 MiB, or a .zip file over 256 MiB. */
    TOO_LARGE,
}
