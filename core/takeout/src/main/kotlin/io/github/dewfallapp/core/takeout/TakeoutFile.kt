package io.github.dewfallapp.core.takeout

import io.github.dewfallapp.core.model.ChannelId

/** What [TakeoutReader] found in a file. */
sealed interface TakeoutFile {
    /**
     * The file's subscriptions table, with at least one channel. [channels] keeps the file's order,
     * and holds each channel ID once, with the name from its first line. [unreadableLines] counts
     * the lines that weren't blank, the header or a repeated channel, and that held no channel.
     */
    data class Subscriptions(val channels: List<TakeoutChannel>, val unreadableLines: Int) :
        TakeoutFile

    /** The file holds no table with a channel in it. */
    data object NoSubscriptions : TakeoutFile

    /**
     * A .csv file over [TakeoutReader.MAX_TABLE_BYTES], or a .zip file over
     * [TakeoutReader.MAX_ZIP_BYTES].
     */
    data object TooLarge : TakeoutFile
}

/**
 * One channel from a subscriptions table: its ID, and its name trimmed of white space at both ends,
 * never empty. Both are user data, so never log them. See ADR 0007.
 */
data class TakeoutChannel(val id: ChannelId, val name: String)
