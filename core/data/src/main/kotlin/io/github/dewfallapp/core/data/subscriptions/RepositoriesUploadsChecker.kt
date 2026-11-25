package io.github.dewfallapp.core.data.subscriptions

import io.github.dewfallapp.core.data.channel.ChannelUploadsRepository
import io.github.dewfallapp.core.data.database.ApplicationScope
import io.github.dewfallapp.core.data.video.StoredVideosRepository
import io.github.dewfallapp.core.logging.LogField
import io.github.dewfallapp.core.logging.Logger
import io.github.dewfallapp.core.model.ChannelId
import io.github.dewfallapp.core.model.ChannelUploads
import io.github.dewfallapp.core.model.UploadsCheck
import io.github.dewfallapp.core.model.UploadsCheckFailure
import io.github.dewfallapp.core.model.YouTubeResult
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/**
 * Runs the check through the repositories, in the app-wide scope, one channel after another, as
 * docs/architecture.md#the-check-for-new-videos describes.
 *
 * A read or a write on the phone that throws, such as on a full disk or a damaged database, ends
 * the check through the coroutine's exception handler, which logs it and lets the app go on, as the
 * channel page's failed saves do. Room and SQLite throw several unrelated types for those, so the
 * handler takes any exception but a cancellation, which never reaches it. Such a check ends with no
 * failure, so no banner shows: the inbox says so itself if its own reads fail too.
 */
@Singleton
internal class RepositoriesUploadsChecker
@Inject
constructor(
    private val subscriptions: SubscriptionsRepository,
    private val uploads: ChannelUploadsRepository,
    private val storedVideos: StoredVideosRepository,
    @ApplicationScope private val externalScope: CoroutineScope,
    logger: Logger,
) : UploadsChecker {
    private val log = logger.withTag("UploadsCheck")
    private val mutableState = MutableStateFlow(UploadsCheck())
    override val state: StateFlow<UploadsCheck> = mutableState.asStateFlow()

    override fun start() {
        val before = mutableState.value
        // Claiming the check before launching keeps one at a time: a second start finds it taken.
        if (before.running || !mutableState.compareAndSet(before, UploadsCheck(running = true))) {
            return
        }
        val tally = CheckTally()
        var failure: UploadsCheckFailure? = null
        val phoneFailed = CoroutineExceptionHandler { _, error ->
            log.error("Uploads check failed on the phone", error = error)
        }
        externalScope
            .launch(phoneFailed) { failure = check(tally) }
            // Runs however the check ended, even if it never started, so the button comes back.
            .invokeOnCompletion {
                logEnd(tally, failure)
                mutableState.value = UploadsCheck(running = false, failure = failure)
            }
    }

    /** Checks every subscribed channel, and returns why it stopped early, or null. */
    private suspend fun check(tally: CheckTally): UploadsCheckFailure? {
        val channels = subscriptions.subscribedChannels().first()
        log.info("Uploads check started", LogField.count("channels", channels.size))
        val answered = HashSet<ChannelId>()
        for (subscribed in channels) {
            val asked = subscribed.channel.id
            // A moved channel subscribed under both IDs was already read under its new one.
            if (asked in answered) continue
            when (val result = uploads.latestUploads(asked)) {
                is YouTubeResult.Success -> {
                    answered += result.value.channel.id
                    tally.onSaved(wasMoved = save(asked, result.value))
                }
                is YouTubeResult.Failure -> {
                    val stop = tally.onFailed(result.error)
                    if (stop != null) return stop
                }
            }
        }
        return tally.failureAtEnd()
    }

    /**
     * Moves the subscription first when YouTube answered for [asked] under another ID, so the save
     * finds the new ID subscribed and refreshes its details. Returns whether it moved.
     */
    private suspend fun save(asked: ChannelId, got: ChannelUploads): Boolean {
        val moved = got.channel.id != asked
        if (moved) subscriptions.moveSubscription(from = asked, to = got.channel)
        storedVideos.saveUploads(got)
        return moved
    }

    private fun logEnd(tally: CheckTally, failure: UploadsCheckFailure?) {
        val counts =
            arrayOf(
                LogField.count("saved", tally.saved),
                LogField.count("moved", tally.moved),
                LogField.count("skipped", tally.skipped),
            )
        if (failure == null) {
            log.info("Uploads check ended", *counts)
        } else {
            log.warn("Uploads check stopped", *counts, LogField.state("failure", failure))
        }
    }
}
