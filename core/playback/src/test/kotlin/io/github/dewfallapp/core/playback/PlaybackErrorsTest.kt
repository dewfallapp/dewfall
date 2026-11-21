package io.github.dewfallapp.core.playback

import androidx.media3.common.PlaybackException
import io.github.dewfallapp.core.model.MediaRequestRefused
import io.github.dewfallapp.core.model.PlaybackFailure
import io.github.dewfallapp.core.model.YouTubeError
import java.io.IOException
import java.util.concurrent.ExecutionException
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class PlaybackErrorsTest {
    @Test
    fun `a refusal behind OkHttpDataSource's wrapping counts as YOUTUBE, not NETWORK`() {
        // OkHttpDataSource at 1.11.1 wraps a failed call as new IOException(executionException).
        val chain =
            IOException(
                ExecutionException(
                    MediaRequestRefused(MediaRequestRefused.Reason.HOST_NOT_ALLOWED),
                ),
            )

        assertTrue(refusedInChain(chain))
        assertFalse(refusedInChain(IOException(ExecutionException(IOException()))))
        assertEquals(
            PlaybackFailure.YOUTUBE,
            failureOf(PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_FAILED, null, true),
        )
    }

    @Test
    fun `the cause-chain walk ends on a loop`() {
        val first = IOException()
        val second = IOException(first)
        first.initCause(second)

        assertFalse(refusedInChain(first))
    }

    @Test
    fun `refused links and refused requests are never retried, and the rest keep the default`() {
        assertFalse(retries(httpStatus = 403, refused = false))
        assertFalse(retries(httpStatus = 410, refused = false))
        assertFalse(retries(httpStatus = null, refused = true))
        assertTrue(retries(httpStatus = 404, refused = false))
        assertTrue(retries(httpStatus = 500, refused = false))
        assertTrue(retries(httpStatus = null, refused = false))
    }

    @Test
    fun `each error code maps to its category`() {
        val cases =
            mapOf(
                PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_FAILED to
                    PlaybackFailure.NETWORK,
                PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_TIMEOUT to
                    PlaybackFailure.NETWORK,
                PlaybackException.ERROR_CODE_DECODER_INIT_FAILED to PlaybackFailure.OTHER,
                PlaybackException.ERROR_CODE_DECODER_QUERY_FAILED to PlaybackFailure.NOT_DECODABLE,
                PlaybackException.ERROR_CODE_DECODING_FAILED to PlaybackFailure.NOT_DECODABLE,
                PlaybackException.ERROR_CODE_DECODING_FORMAT_EXCEEDS_CAPABILITIES to
                    PlaybackFailure.NOT_DECODABLE,
                PlaybackException.ERROR_CODE_DECODING_FORMAT_UNSUPPORTED to
                    PlaybackFailure.NOT_DECODABLE,
                PlaybackException.ERROR_CODE_DECODING_RESOURCES_RECLAIMED to PlaybackFailure.OTHER,
                PlaybackException.ERROR_CODE_IO_UNSPECIFIED to PlaybackFailure.OTHER,
                PlaybackException.ERROR_CODE_BEHIND_LIVE_WINDOW to PlaybackFailure.OTHER,
                PlaybackException.ERROR_CODE_UNSPECIFIED to PlaybackFailure.OTHER,
            )

        for ((code, failure) in cases) {
            assertEquals(failure, failureOf(code, httpStatus = null, refused = false), "$code")
        }
    }

    @Test
    fun `a refused link that stands is YOUTUBE, and another bad status is OTHER`() {
        val badStatus = PlaybackException.ERROR_CODE_IO_BAD_HTTP_STATUS

        assertEquals(PlaybackFailure.YOUTUBE, failureOf(badStatus, 403, refused = false))
        assertEquals(PlaybackFailure.YOUTUBE, failureOf(badStatus, 410, refused = false))
        assertEquals(PlaybackFailure.OTHER, failureOf(badStatus, 404, refused = false))
    }

    @Test
    fun `each YouTubeError of a failed resolution maps to its category`() {
        val expected =
            mapOf(
                YouTubeError.NETWORK to PlaybackFailure.NETWORK,
                YouTubeError.BLOCKED to PlaybackFailure.YOUTUBE,
                YouTubeError.BROKEN to PlaybackFailure.YOUTUBE,
                YouTubeError.PRIVATE to PlaybackFailure.PRIVATE,
                YouTubeError.AGE_RESTRICTED to PlaybackFailure.AGE_RESTRICTED,
                YouTubeError.COUNTRY_BLOCKED to PlaybackFailure.COUNTRY_BLOCKED,
                YouTubeError.MEMBERS_OR_PAID to PlaybackFailure.MEMBERS_OR_PAID,
                YouTubeError.CHANNEL_CLOSED to PlaybackFailure.CHANNEL_CLOSED,
                YouTubeError.UNAVAILABLE to PlaybackFailure.UNAVAILABLE,
            )

        assertEquals(YouTubeError.entries.toSet(), expected.keys)
        for ((error, failure) in expected) {
            assertEquals(failure, PlaybackFailure.of(error))
        }
    }

    @Test
    fun `the session's error keeps the code, with the given constant text and no cause`() {
        val message = "This video stopped playing."
        val decoding = cleanedError(PlaybackException.ERROR_CODE_DECODING_FAILED, message)
        val network =
            cleanedError(PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_FAILED, message)

        assertEquals(CleanedError(message, PlaybackException.ERROR_CODE_DECODING_FAILED), decoding)
        // The same text for every failure, so it says nothing about the video.
        assertEquals(message, network?.message)
        // CleanedError holds only a message and a code, so the session's error has no cause.
        assertEquals(
            setOf("message", "errorCode"),
            CleanedError::class.java.declaredFields.map { it.name }.toSet(),
        )
        assertNull(cleanedError(null, message))
    }
}
