package io.github.dewfallapp.core.youtube.newpipe

import io.github.dewfallapp.core.model.YouTubeError
import java.io.IOException
import org.schabi.newpipe.extractor.exceptions.AccountTerminatedException
import org.schabi.newpipe.extractor.exceptions.AgeRestrictedContentException
import org.schabi.newpipe.extractor.exceptions.ContentNotAvailableException
import org.schabi.newpipe.extractor.exceptions.ContentNotSupportedException
import org.schabi.newpipe.extractor.exceptions.GeographicRestrictionException
import org.schabi.newpipe.extractor.exceptions.PaidContentException
import org.schabi.newpipe.extractor.exceptions.PrivateContentException
import org.schabi.newpipe.extractor.exceptions.ReCaptchaException
import org.schabi.newpipe.extractor.exceptions.SignInConfirmNotBotException
import org.schabi.newpipe.extractor.exceptions.UnsupportedContentInCountryException
import org.schabi.newpipe.extractor.exceptions.YoutubeMusicPremiumContentException

/** The most causes that [youTubeErrorOf] looks through, as many as the logger's error summary. */
private const val MAX_CAUSES = 10

/**
 * The category of [error], which NewPipeExtractor or NewPipeDownloader threw. Each type is checked
 * before its parent. An error whose type names no category, such as ParsingException, takes the
 * category of its first cause that is a refused host, a refusal by YouTube or a network problem,
 * and is BROKEN otherwise. docs/architecture.md#mapping-newpipeextractors-exceptions lists the
 * mapping.
 *
 * A cancelled call never gets here. NewPipeExtractor passes the cancellation on as
 * InterruptedIOException, an IOException, so NewPipeCalls checks for cancellation first.
 */
internal fun youTubeErrorOf(error: Exception): YouTubeError =
    categoryOfType(error)
        ?: generateSequence(error.cause) { it.cause }
            .take(MAX_CAUSES)
            .firstNotNullOfOrNull(::refusalOrNetworkCategory)
        ?: YouTubeError.BROKEN

/** The category that [error]'s own type names, or null for a type that names none. */
private fun categoryOfType(error: Throwable): YouTubeError? =
    refusalOrNetworkCategory(error)
        ?: when (error) {
            is PrivateContentException -> YouTubeError.PRIVATE
            is AgeRestrictedContentException -> YouTubeError.AGE_RESTRICTED
            is GeographicRestrictionException,
            is UnsupportedContentInCountryException -> YouTubeError.COUNTRY_BLOCKED
            is PaidContentException,
            is YoutubeMusicPremiumContentException -> YouTubeError.MEMBERS_OR_PAID
            is AccountTerminatedException -> YouTubeError.CHANNEL_CLOSED
            is ContentNotAvailableException,
            is ContentNotSupportedException -> YouTubeError.UNAVAILABLE
            else -> null
        }

/**
 * The category of [error] if it is a refused host, a refusal by YouTube or a network problem, or
 * null otherwise. NewPipeExtractor wraps these in ParsingException in some places, such as when it
 * fetches YouTube's player code, so [youTubeErrorOf] also looks for them among the causes.
 */
private fun refusalOrNetworkCategory(error: Throwable): YouTubeError? =
    when (error) {
        // Before IOException, which it extends.
        is HostNotAllowedException -> YouTubeError.BROKEN
        is ReCaptchaException,
        is SignInConfirmNotBotException -> YouTubeError.BLOCKED
        is IOException -> YouTubeError.NETWORK
        else -> null
    }
