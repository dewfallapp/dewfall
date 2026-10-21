package io.github.dewfallapp.core.model

/** The number of characters in every YouTube video ID. */
private const val VIDEO_ID_LENGTH = 11

/** The text that every YouTube channel ID starts with. */
private const val CHANNEL_ID_PREFIX = "UC"

/** The number of characters in every YouTube channel ID: the prefix and 22 more. */
private const val CHANNEL_ID_LENGTH = 24

/**
 * A YouTube video's ID, such as the part after `v=` in a watch link. It is 11 characters from A to
 * Z, a to z, 0 to 9, `-` and `_`. The constructor checks this and throws IllegalArgumentException
 * for any other text, so every VideoId is well formed. For text from outside the app, such as an
 * imported file, use [parseOrNull].
 *
 * An ID is user data, so never log it. Neither toString nor the constructor's error message
 * contains it. Read [value] where the ID itself is needed. See ADR 0007.
 */
@JvmInline
value class VideoId(val value: String) {
    init {
        require(isVideoId(value)) { "Not a YouTube video ID" }
    }

    /** A fixed text that leaves out the ID. */
    override fun toString(): String = "VideoId(hidden)"

    companion object {
        /** The video ID in [text], or null if [text] isn't a video ID. */
        fun parseOrNull(text: String): VideoId? = if (isVideoId(text)) VideoId(text) else null
    }
}

/**
 * A YouTube channel's ID, the one that YouTube's channel links and a Google Takeout export use. It
 * is "UC" followed by 22 characters from A to Z, a to z, 0 to 9, `-` and `_`. Handles such as
 * `@name` and old user names aren't channel IDs. The constructor checks this and throws
 * IllegalArgumentException for any other text, so every ChannelId is well formed. For text from
 * outside the app, such as an imported file, use [parseOrNull].
 *
 * An ID is user data, so never log it. Neither toString nor the constructor's error message
 * contains it. Read [value] where the ID itself is needed. See ADR 0007.
 */
@JvmInline
value class ChannelId(val value: String) {
    init {
        require(isChannelId(value)) { "Not a YouTube channel ID" }
    }

    /** A fixed text that leaves out the ID. */
    override fun toString(): String = "ChannelId(hidden)"

    companion object {
        /** The channel ID in [text], or null if [text] isn't a channel ID. */
        fun parseOrNull(text: String): ChannelId? = if (isChannelId(text)) ChannelId(text) else null
    }
}

private fun isVideoId(text: String): Boolean =
    text.length == VIDEO_ID_LENGTH && text.all(::isIdCharacter)

private fun isChannelId(text: String): Boolean =
    text.length == CHANNEL_ID_LENGTH &&
        text.startsWith(CHANNEL_ID_PREFIX) &&
        text.all(::isIdCharacter)

/** Whether [char] can appear in a YouTube ID. Only ASCII letters and digits, `-` and `_` can. */
private fun isIdCharacter(char: Char): Boolean =
    char in 'A'..'Z' || char in 'a'..'z' || char in '0'..'9' || char == '-' || char == '_'
