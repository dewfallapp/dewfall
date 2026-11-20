@file:OptIn(UnstableApi::class)

package io.github.dewfallapp.core.playback

import androidx.annotation.OptIn
import androidx.media3.common.PlaybackException
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.HttpDataSource
import io.github.dewfallapp.core.model.MediaRequestRefused
import io.github.dewfallapp.core.model.PlaybackFailure

/** The HTTP statuses with which YouTube refuses a stream link, which new links can fix. */
private val REFUSED_LINK_STATUSES = setOf(403, 410)

/** Media3's error codes for a network that failed or timed out. */
private val NETWORK_CODES =
    setOf(
        PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_FAILED,
        PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_TIMEOUT,
    )

/**
 * Media3's error codes for a decoder that can't decode the format, or failed while decoding it, so
 * "Try again" with the same format can't help. Two decoder codes aren't among them, because trying
 * again can help: ERROR_CODE_DECODER_INIT_FAILED, a decoder that failed to start, such as while
 * another app holds it, and ERROR_CODE_DECODING_RESOURCES_RECLAIMED, a decoder the system took
 * back. The maintainer decided the first on November 20, 2026.
 */
private val DECODER_CODES =
    setOf(
        PlaybackException.ERROR_CODE_DECODER_QUERY_FAILED,
        PlaybackException.ERROR_CODE_DECODING_FAILED,
        PlaybackException.ERROR_CODE_DECODING_FORMAT_EXCEEDS_CAPABILITIES,
        PlaybackException.ERROR_CODE_DECODING_FORMAT_UNSUPPORTED,
    )

/** How deep the cause-chain walk goes, so a chain with a loop ends. */
private const val MAX_CAUSE_DEPTH = 16

/** Whether YouTube refused a stream link with [httpStatus], which new links can fix. */
internal fun isRefusedLink(httpStatus: Int?): Boolean = httpStatus in REFUSED_LINK_STATUSES

/**
 * The category of a playback error with Media3's [errorCode], the HTTP status that the request got,
 * if any, and whether it was [refused] before it was sent. A refused request comes first, because
 * Media3 reports any IOException from a data source as a network error.
 */
internal fun failureOf(errorCode: Int, httpStatus: Int?, refused: Boolean): PlaybackFailure =
    when {
        refused || isRefusedLink(httpStatus) -> PlaybackFailure.YOUTUBE
        errorCode in NETWORK_CODES -> PlaybackFailure.NETWORK
        errorCode in DECODER_CODES -> PlaybackFailure.NOT_DECODABLE
        else -> PlaybackFailure.OTHER
    }

/**
 * Whether a load that failed with [httpStatus], or that was [refused] before it was sent, is tried
 * again. A refused link isn't, so new links are fetched at once, and a refused request is never
 * sent again.
 */
internal fun retries(httpStatus: Int?, refused: Boolean): Boolean =
    !refused && !isRefusedLink(httpStatus)

/**
 * [error] and its causes, in order. OkHttpDataSource wraps a failed call as an IOException around
 * an ExecutionException around the interceptor's own exception, so the YouTube layer's refusal sits
 * deep in the chain.
 */
internal fun causeChain(error: Throwable): Sequence<Throwable> =
    generateSequence(error) { it.cause?.takeIf { cause -> cause !== it } }.take(MAX_CAUSE_DEPTH)

/** The HTTP status in [error]'s cause chain, if a request got one that Media3 refused. */
internal fun httpStatusOf(error: Throwable): Int? =
    causeChain(error)
        .filterIsInstance<HttpDataSource.InvalidResponseCodeException>()
        .firstOrNull()
        ?.responseCode

/**
 * Whether the YouTube layer, or the player for an item without links, refused a request somewhere
 * in [error]'s cause chain.
 */
internal fun refusedInChain(error: Throwable): Boolean =
    causeChain(error).any { it is MediaRequestRefused }

/** The error that the media session sees in place of the player's own. */
internal data class CleanedError(val message: String, val errorCode: Int)

/**
 * The error that the media session sees in place of one with [errorCode], or null when there is no
 * error: the same code, with [message] and no cause. [message] is constant text from the app's
 * resources, because car displays and watches may show it. Media3 copies the message into Android's
 * media session, and ExoPlayer's messages can name the format's codec, size and language.
 */
internal fun cleanedError(errorCode: Int?, message: String): CleanedError? = errorCode?.let {
    CleanedError(message, it)
}
