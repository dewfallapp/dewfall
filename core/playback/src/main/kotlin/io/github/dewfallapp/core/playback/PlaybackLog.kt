package io.github.dewfallapp.core.playback

import io.github.dewfallapp.core.logging.LogField
import io.github.dewfallapp.core.logging.Logger
import io.github.dewfallapp.core.model.PlaybackFailure
import javax.inject.Inject

/**
 * The playback service's log. A Service can't take the Logger in its constructor, so Hilt injects
 * this into a field of the service instead. It logs only the failure's category and Media3's error
 * code, never a link, an ID or a message. See ADR 0007.
 */
class PlaybackLog @Inject internal constructor(logger: Logger) {
    private val logger = logger.withTag("Playback")

    /**
     * One line for each failure: its [failure] category, Media3's [errorCode] if it has one, and
     * the [error] behind it, if any, which the Logger writes as class names and stack frames only.
     */
    internal fun failed(failure: PlaybackFailure, errorCode: Int?, error: Throwable? = null) {
        val category = LogField.state("category", failure)
        if (errorCode == null) {
            logger.warn("Playback failed", category, error = error)
        } else {
            logger.warn(
                "Playback failed",
                category,
                LogField.count("errorCode", errorCode),
                error = error,
            )
        }
    }

    /**
     * One line when the database can't save or read a video's watch state, with the [error] as
     * class names and stack frames only. It names no video and no position, which are user data.
     */
    internal fun watchStateFailed(error: Throwable) {
        logger.warn("Watch state failed", error = error)
    }
}
