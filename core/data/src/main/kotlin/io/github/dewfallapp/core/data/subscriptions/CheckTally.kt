package io.github.dewfallapp.core.data.subscriptions

import io.github.dewfallapp.core.model.UploadsCheckFailure
import io.github.dewfallapp.core.model.YouTubeError

/**
 * After this many answers in a row that the app can't read, a check stops, because YouTube has most
 * likely changed its pages, and every later request would fail the same way.
 */
internal const val BROKEN_IN_A_ROW_TO_STOP = 3

/**
 * What a check has met so far, and what each failure makes it do, as
 * docs/architecture.md#the-check-for-new-videos says: NETWORK and BLOCKED stop it at once, BROKEN
 * skips the channel unless it is the third in a row, and the errors about one channel skip it. It
 * holds counts only, so it can be logged.
 */
internal class CheckTally {
    /** Channels whose uploads were read and saved. */
    var saved = 0
        private set

    /** Channels that YouTube answered for under another ID. */
    var moved = 0
        private set

    /** Channels skipped after a failure of their own, BROKEN included. */
    var skipped = 0
        private set

    /** Channels whose answer the app read, as uploads or as an error about that channel. */
    private var understood = 0
    private var broken = 0
    private var brokenInARow = 0

    /** A channel's uploads were read and saved, under another ID if [wasMoved]. */
    fun onSaved(wasMoved: Boolean) {
        saved++
        if (wasMoved) moved++
        understood++
        brokenInARow = 0
    }

    /** A channel's read failed with [error]. Returns why the check stops now, or null to go on. */
    fun onFailed(error: YouTubeError): UploadsCheckFailure? =
        when (error) {
            YouTubeError.NETWORK -> UploadsCheckFailure.CANT_REACH_YOUTUBE
            YouTubeError.BLOCKED -> UploadsCheckFailure.YOUTUBE_NOT_LOADING
            YouTubeError.BROKEN -> {
                skipped++
                broken++
                brokenInARow++
                if (brokenInARow >= BROKEN_IN_A_ROW_TO_STOP) {
                    UploadsCheckFailure.YOUTUBE_NOT_LOADING
                } else {
                    null
                }
            }
            YouTubeError.PRIVATE,
            YouTubeError.AGE_RESTRICTED,
            YouTubeError.COUNTRY_BLOCKED,
            YouTubeError.MEMBERS_OR_PAID,
            YouTubeError.CHANNEL_CLOSED,
            YouTubeError.UNAVAILABLE -> {
                skipped++
                understood++
                brokenInARow = 0
                null
            }
        }

    /**
     * The failure a check that went through every channel ends with: YouTube isn't loading when no
     * answer could be read and at least one couldn't, else none.
     */
    fun failureAtEnd(): UploadsCheckFailure? =
        if (understood == 0 && broken > 0) UploadsCheckFailure.YOUTUBE_NOT_LOADING else null
}
