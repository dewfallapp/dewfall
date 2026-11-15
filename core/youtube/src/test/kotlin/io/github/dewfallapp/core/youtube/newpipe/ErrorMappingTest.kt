package io.github.dewfallapp.core.youtube.newpipe

import io.github.dewfallapp.core.model.YouTubeError
import java.io.IOException
import java.io.InterruptedIOException
import java.net.SocketTimeoutException
import kotlin.test.Test
import kotlin.test.assertEquals
import org.schabi.newpipe.extractor.exceptions.AccountTerminatedException
import org.schabi.newpipe.extractor.exceptions.AgeRestrictedContentException
import org.schabi.newpipe.extractor.exceptions.ContentNotAvailableException
import org.schabi.newpipe.extractor.exceptions.ContentNotSupportedException
import org.schabi.newpipe.extractor.exceptions.ExtractionException
import org.schabi.newpipe.extractor.exceptions.GeographicRestrictionException
import org.schabi.newpipe.extractor.exceptions.PaidContentException
import org.schabi.newpipe.extractor.exceptions.ParsingException
import org.schabi.newpipe.extractor.exceptions.PrivateContentException
import org.schabi.newpipe.extractor.exceptions.ReCaptchaException
import org.schabi.newpipe.extractor.exceptions.SignInConfirmNotBotException
import org.schabi.newpipe.extractor.exceptions.UnsupportedContentInCountryException
import org.schabi.newpipe.extractor.exceptions.YoutubeMusicPremiumContentException

class ErrorMappingTest {
    @Test
    fun `each exception type maps to its category, a subclass before its parent`() {
        val expected =
            listOf(
                IOException() to YouTubeError.NETWORK,
                SocketTimeoutException() to YouTubeError.NETWORK,
                InterruptedIOException() to YouTubeError.NETWORK,
                HostNotAllowedException() to YouTubeError.BROKEN,
                ReCaptchaException("Captcha", "") to YouTubeError.BLOCKED,
                SignInConfirmNotBotException("Not a bot") to YouTubeError.BLOCKED,
                PrivateContentException("Private") to YouTubeError.PRIVATE,
                AgeRestrictedContentException("Age") to YouTubeError.AGE_RESTRICTED,
                GeographicRestrictionException("Country") to YouTubeError.COUNTRY_BLOCKED,
                UnsupportedContentInCountryException("Country") to YouTubeError.COUNTRY_BLOCKED,
                PaidContentException("Paid") to YouTubeError.MEMBERS_OR_PAID,
                YoutubeMusicPremiumContentException() to YouTubeError.MEMBERS_OR_PAID,
                AccountTerminatedException("Closed") to YouTubeError.CHANNEL_CLOSED,
                ContentNotAvailableException("Unavailable") to YouTubeError.UNAVAILABLE,
                ContentNotSupportedException("Not supported") to YouTubeError.UNAVAILABLE,
                ParsingException("Changed") to YouTubeError.BROKEN,
                ExtractionException("Changed") to YouTubeError.BROKEN,
                IllegalStateException("Bug") to YouTubeError.BROKEN,
            )

        for ((error, category) in expected) {
            assertEquals(category, youTubeErrorOf(error), error.javaClass.simpleName)
        }
    }

    @Test
    fun `a parsing error caused by a network problem is NETWORK`() {
        val error = ParsingException("Could not get the player", IOException())

        assertEquals(YouTubeError.NETWORK, youTubeErrorOf(error))
    }

    @Test
    fun `a parsing error caused by a captcha is BLOCKED`() {
        val error = ParsingException("Could not get the player", ReCaptchaException("Captcha", ""))

        assertEquals(YouTubeError.BLOCKED, youTubeErrorOf(error))
    }

    @Test
    fun `a parsing error caused by a refused host is BROKEN, not NETWORK`() {
        val error = ParsingException("Could not get the player", HostNotAllowedException())

        assertEquals(YouTubeError.BROKEN, youTubeErrorOf(error))
    }

    @Test
    fun `the first cause that names a category decides`() {
        val error =
            ExtractionException(
                ParsingException("Wrapped", IOException("Lost", ReCaptchaException("Captcha", "")))
            )

        assertEquals(YouTubeError.NETWORK, youTubeErrorOf(error))
    }

    @Test
    fun `a content error's own type wins over its cause`() {
        val error = PrivateContentException("Private", IOException())

        assertEquals(YouTubeError.PRIVATE, youTubeErrorOf(error))
    }

    @Test
    fun `a cause that names no category leaves the error BROKEN`() {
        val error = ParsingException("Changed", IllegalArgumentException())

        assertEquals(YouTubeError.BROKEN, youTubeErrorOf(error))
    }

    @Test
    fun `causes past the tenth are not read`() {
        var error: Exception = IOException()
        repeat(WRAPPERS_PAST_THE_LIMIT) { error = ParsingException("Wrapped", error) }

        assertEquals(YouTubeError.BROKEN, youTubeErrorOf(error))
    }

    @Test
    fun `a chain of causes that loops ends`() {
        val first = ParsingException("First")
        val second = ParsingException("Second", first)
        first.initCause(second)

        assertEquals(YouTubeError.BROKEN, youTubeErrorOf(first))
    }

    private companion object {
        /** Wrappers that put a cause 11th in the chain, one past the 10 that the mapping reads. */
        const val WRAPPERS_PAST_THE_LIMIT = 11
    }
}
