package io.github.dewfallapp.core.data.subscriptions

import io.github.dewfallapp.core.model.UploadsCheck
import kotlinx.coroutines.flow.StateFlow

/**
 * The check for new videos: it asks YouTube for the latest uploads of every subscribed channel, one
 * channel after another, and saves them, so their new videos reach the inbox. It runs in the
 * app-wide scope, apart from any screen, so leaving a screen doesn't stop it, and Phase 7's
 * overnight job can run the same check. See docs/architecture.md#the-check-for-new-videos and the
 * subscriptions spec in openspec/.
 *
 * Main-safe. It logs counts only, never a channel or a video.
 */
interface UploadsChecker {
    /** The check's state, the same for every screen. It holds no user data. */
    val state: StateFlow<UploadsCheck>

    /** Starts a check, unless one runs, and returns at once. */
    fun start()
}
