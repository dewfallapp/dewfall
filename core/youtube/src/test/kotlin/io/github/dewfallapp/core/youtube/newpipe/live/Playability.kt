package io.github.dewfallapp.core.youtube.newpipe.live

import io.github.dewfallapp.core.youtube.newpipe.saved.Endpoint
import io.github.dewfallapp.core.youtube.newpipe.saved.JsonObject
import io.github.dewfallapp.core.youtube.newpipe.saved.JsonString
import io.github.dewfallapp.core.youtube.newpipe.saved.JsonValue
import io.github.dewfallapp.core.youtube.newpipe.saved.parseJsonBody
import okhttp3.Interceptor
import okhttp3.Response

/**
 * The most bytes of a visionOS player answer that [PlayabilityRecorder] reads. The saved answers
 * are about 45 KB, so this leaves a wide margin. A longer answer is cut off, so it doesn't parse,
 * and reads as [PlayabilityStatus.NOT_JSON].
 */
private const val MAX_ANSWER_BYTES = 8L * 1024 * 1024

/**
 * The status of the playabilityStatus object in YouTube's answer to the visionOS client's player
 * request, as one of a fixed list of names. These are YouTube's status tokens, not its English
 * wording, and any other value becomes [OTHER], so no text from YouTube is kept.
 */
internal enum class PlayabilityStatus {
    // The four statuses that NewPipeExtractor v0.27.1's checkPlayabilityStatus compares, without
    // regard to case. OK lets the video play.
    OK,
    LOGIN_REQUIRED,
    UNPLAYABLE,
    ERROR,

    /** A status string other than the four above. */
    OTHER,

    /** A JSON answer with no playabilityStatus.status string. NewPipeExtractor lets it play. */
    STATUS_MISSING,

    /** An answer that isn't one JSON value, or one longer than [MAX_ANSWER_BYTES]. */
    NOT_JSON,
}

/**
 * Whether the playabilityStatus object holds a reason string. NewPipeExtractor v0.27.1 reaches its
 * catch-all ContentNotAvailableException either with no reason or with a reason that holds none of
 * the words it looks for, and this tells the two apart without keeping the reason.
 */
internal enum class ReasonGiven {
    GIVEN,
    MISSING,
}

/**
 * What the live check keeps of one visionOS player answer: its [status], and whether it gave a
 * reason, which is null when the answer isn't JSON.
 */
internal data class PlayabilityAnswer(val status: PlayabilityStatus, val reasonGiven: ReasonGiven?)

/**
 * Keeps what [playabilityAnswerOf] reads from the first answer to a visionOS player request since
 * the last [take]. NewPipeExtractor checks playability only in that answer, in fetchVisionOsClient.
 * It reads the answer with peekBody, so the whole body still reaches NewPipeDownloader, and lets
 * every other request through unread. It never logs or keeps the body.
 *
 * It throws nothing but the IOException of proceed and peekBody. Any other exception from an
 * interceptor would reach the layer as an IOException, which the check reads as NETWORK, makes
 * again, and skips on the runners.
 */
internal class PlayabilityRecorder : Interceptor {
    private val lock = Any()
    private var answer: PlayabilityAnswer? = null

    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()
        val response = chain.proceed(request)
        if (Endpoint.of(request.method, request.url) == Endpoint.VISIONOS_PLAYER) {
            val read = playabilityAnswerOf(response.peekBody(MAX_ANSWER_BYTES).string())
            synchronized(lock) {
                if (answer == null) answer = read
            }
        }
        return response
    }

    /** The answer kept since the last call, or null if none came, which it forgets. */
    fun take(): PlayabilityAnswer? =
        synchronized(lock) {
            val taken = answer
            answer = null
            taken
        }
}

/** What the live check keeps of [body], the text of a visionOS player answer. */
internal fun playabilityAnswerOf(body: String): PlayabilityAnswer {
    val json =
        try {
            parseJsonBody(body)
        } catch (_: StackOverflowError) {
            // Only nesting far deeper than any answer of YouTube's gets here.
            null
        } ?: return PlayabilityAnswer(PlayabilityStatus.NOT_JSON, null)
    val playability = (json as? JsonObject)?.member("playabilityStatus") as? JsonObject
    val status = (playability?.member("status") as? JsonString)?.value
    val reasonGiven =
        if (playability?.member("reason") is JsonString) ReasonGiven.GIVEN else ReasonGiven.MISSING
    return PlayabilityAnswer(statusOf(status), reasonGiven)
}

/** The name of [status], compared as NewPipeExtractor compares it, without regard to case. */
private fun statusOf(status: String?): PlayabilityStatus =
    when {
        status == null -> PlayabilityStatus.STATUS_MISSING
        status.equals("ok", ignoreCase = true) -> PlayabilityStatus.OK
        status.equals("login_required", ignoreCase = true) -> PlayabilityStatus.LOGIN_REQUIRED
        status.equals("unplayable", ignoreCase = true) -> PlayabilityStatus.UNPLAYABLE
        status.equals("error", ignoreCase = true) -> PlayabilityStatus.ERROR
        else -> PlayabilityStatus.OTHER
    }

/** The value of this object's first member named [name], or null if it has none. */
private fun JsonObject.member(name: String): JsonValue? =
    members.firstOrNull { it.first == name }?.second
