package io.github.dewfallapp.core.youtube.newpipe.live

import io.github.dewfallapp.core.logging.LogField
import io.github.dewfallapp.core.logging.Logger
import io.github.dewfallapp.core.model.ChannelId
import io.github.dewfallapp.core.model.ChannelUploads
import io.github.dewfallapp.core.model.SearchResults
import io.github.dewfallapp.core.model.StreamLinks
import io.github.dewfallapp.core.model.SubtitleLine
import io.github.dewfallapp.core.model.VideoDetails
import io.github.dewfallapp.core.model.VideoId
import io.github.dewfallapp.core.model.VideoKind
import io.github.dewfallapp.core.model.YouTubeError
import io.github.dewfallapp.core.model.YouTubeResult
import io.github.dewfallapp.core.youtube.newpipe.languageTagOf
import io.github.dewfallapp.core.youtube.newpipe.saved.AGE_RESTRICTED_CHANNEL_ID
import io.github.dewfallapp.core.youtube.newpipe.saved.BIG_BUCK_BUNNY_DURATION
import io.github.dewfallapp.core.youtube.newpipe.saved.BIG_BUCK_BUNNY_ID
import io.github.dewfallapp.core.youtube.newpipe.saved.BIG_BUCK_BUNNY_PUBLISHED
import io.github.dewfallapp.core.youtube.newpipe.saved.BIG_BUCK_BUNNY_TITLE
import io.github.dewfallapp.core.youtube.newpipe.saved.BLENDER_CHANNEL_ID
import io.github.dewfallapp.core.youtube.newpipe.saved.Endpoint
import io.github.dewfallapp.core.youtube.newpipe.saved.JAWED_CHANNEL_ID
import io.github.dewfallapp.core.youtube.newpipe.saved.ME_AT_THE_ZOO_DURATION
import io.github.dewfallapp.core.youtube.newpipe.saved.ME_AT_THE_ZOO_ID
import io.github.dewfallapp.core.youtube.newpipe.saved.ME_AT_THE_ZOO_TITLE
import io.github.dewfallapp.core.youtube.newpipe.saved.NASA_CHANNEL_ID
import io.github.dewfallapp.core.youtube.newpipe.saved.SavedSet
import io.github.dewfallapp.core.youtube.newpipe.saved.TEARS_OF_STEEL_DURATION
import io.github.dewfallapp.core.youtube.newpipe.saved.VideoSubtitles
import io.github.dewfallapp.core.youtube.newpipe.saved.YouTubeLayer
import io.github.dewfallapp.core.youtube.newpipe.saved.isInLanguage
import kotlin.time.Clock
import kotlin.time.Duration
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.delay
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull

/** The area of the live check's log tag, Dewfall.LiveCheck. */
internal const val LIVE_CHECK_LOG_AREA = "LiveCheck"

/** The message of each kind's result line, at INFO, WARN or ERROR by its verdict. */
private const val LIVE_CHECK_RESULT = "Live check result"

/** How long the check waits before it makes a call again that failed with NETWORK. */
private val RETRY_DELAY = 30.seconds

/** How long the stream links must still work, at least, when the check gets them. */
private val MIN_LINK_LIFETIME = 1.hours

/** The MIME type of the audio-only format whose first bytes the check asks for. */
private const val MP4_AUDIO = "audio/mp4"

/** The MIME type of the video-only format whose first bytes the check asks for. */
private const val MP4_VIDEO = "video/mp4"

/** The part of NASA's channel names that a search for NASA must find. */
private const val NASA_NAME = "NASA"

/** The kinds that the live check reports apart from the saved sets. */
internal enum class LiveKind {
    /** Big Buck Bunny's stream links, and the first bytes of an audio-only and a video-only one. */
    STREAM_LINKS_BIG_BUCK_BUNNY,

    /** Fails when every other kind was skipped, so that the run checked nothing. */
    NOTHING_CHECKED,
}

/** A format whose first bytes the check asks for. */
internal enum class FormatKind {
    AUDIO_ONLY,
    VIDEO_ONLY,
}

/** What a successful call returned that the check doesn't expect. */
internal enum class WrongContent {
    WRONG_ID,
    WRONG_TITLE,
    WRONG_CHANNEL,
    WRONG_DURATION,
    WRONG_PUBLISHED,
    MARKED_LIVE,
    NO_NASA_CHANNEL,
    NO_VIDEO,
    SHORT_INCLUDED,
    VIDEO_OF_ANOTHER_CHANNEL,
    DUPLICATE_VIDEO,
    WRONG_ORDER,
    NOT_ONLY_ME_AT_THE_ZOO,
    WRONG_KIND,
    NO_AUDIO_ONLY_MP4,
    NO_VIDEO_ONLY_MP4,
    NO_HLS_MANIFEST,
    NOT_GOOGLEVIDEO,
    NO_EXPIRY,
    MISSING_TRACK,
    TOO_FEW_TRACKS,
    WRONG_LANGUAGE_TAG,
    TRANSLATED_TRACK,
    NO_LINES,
    TOO_FEW_LINES,
    EMPTY_LINE,
    LINE_OUT_OF_RANGE,
    LINE_OUT_OF_ORDER,
    MARKUP_LEFT,
    REPEATED_TEXT,
    WRONG_TEXT,
}

/** The status field of a request for first bytes that got no answer. */
private const val NO_STATUS = 0

/** The languages of the subtitle tracks that the subtitle sets check, as language subtags. */
private const val ENGLISH = "en"
private const val GERMAN = "de"
private const val HEBREW = "he"

/** A word that a line of "Me at the zoo"'s English track has said since 2005. */
private const val ELEPHANTS = "elephants"

/**
 * The least that Tears of Steel has: written tracks, of which it had 28 on November 18, 2026, and
 * lines of its auto-generated track, of which it had 97.
 */
private const val TEARS_OF_STEEL_LEAST_WRITTEN_TRACKS = 20
private const val TEARS_OF_STEEL_LEAST_AUTO_LINES = 50

/** How many words a row has before the next line starting with it counts as a repeat. */
private const val REPEATED_LINE_LEAST_WORDS = 3

/** A character reference, such as &#39; or &amp;, which the layer decodes. */
private val CHARACTER_REFERENCE = Regex("""&(?:#\d+|#x[0-9A-Fa-f]+|[A-Za-z]+);""")

/** The ending of a call that succeeded, for the `got` and `expected` fields of a result. */
private enum class CallSucceeded {
    SUCCESS,
}

/**
 * One call that the live check makes through the layer: [call], reported as [kind], which must end
 * and send the requests as [set] says.
 */
internal class LiveCall(
    val kind: Enum<*>,
    val set: SavedSet,
    val call: suspend YouTubeLayer.() -> YouTubeResult<*>,
)

/**
 * Every call that the live check makes, in order: the call of every saved set, so a new set joins
 * the check on its own, then Big Buck Bunny's stream links, which send the requests of its video
 * details.
 */
internal val LIVE_CALLS: List<LiveCall> =
    SavedSet.entries.map { LiveCall(it, it, it.call) } +
        LiveCall(LiveKind.STREAM_LINKS_BIG_BUCK_BUNNY, SavedSet.VIDEO_BIG_BUCK_BUNNY) {
            streamLinks(VideoId(BIG_BUCK_BUNNY_ID))
        }

/** Every kind in the results file, in its order. */
internal val LIVE_KINDS: List<Enum<*>> = LIVE_CALLS.map { it.kind } + LiveKind.NOTHING_CHECKED

/**
 * How one call through the layer went: its [result], the [requests] it sent, how many lines at WARN
 * or above the layer logged during it, and how long it took.
 */
internal class CallOutcome(
    val result: YouTubeResult<*>,
    val requests: List<Endpoint>,
    val warnings: Int,
    val duration: Duration,
) {
    /** The category that the call failed with, or null when it succeeded. */
    val error: YouTubeError?
        get() = (result as? YouTubeResult.Failure)?.error
}

/** Makes a call through the layer. The live run calls YouTube, and the tests fake it. */
internal fun interface LayerCaller {
    suspend fun call(liveCall: LiveCall): CallOutcome
}

/**
 * The live check of the YouTube layer. It makes every one of [LIVE_CALLS] through [caller], one at
 * a time, and checks each against what its saved set expects: how the call ends, the requests it
 * sends, apart from sw.js_data, that a success logged no warning, and content unlikely to change.
 * For Big Buck Bunny's stream links, it also checks that they last at least an hour and asks an
 * audio-only and a video-only link for their first bytes through [firstBytes].
 *
 * A call that fails with NETWORK, and a request for first bytes that does, is made again once after
 * [retryDelay]. By default the check skips nothing: a call that YouTube blocks fails its kind with
 * RUNNER_BLOCKED, and one that fails with NETWORK again fails with NETWORK, because a run by hand
 * that couldn't check a call proves nothing. When [skipUnreachable] is true, as the daily workflow
 * runs it, those kinds are skipped instead, because YouTube blocks some calls from GitHub's
 * runners, and [LiveKind.NOTHING_CHECKED] fails when every call was skipped.
 *
 * It logs through [logger] with the tag Dewfall.LiveCheck, and only enum names, counts and
 * durations: never a link, an ID, a title or an answer. See ADR 0007.
 */
internal class LiveCheck(
    private val caller: LayerCaller,
    private val firstBytes: FirstBytesFetcher,
    private val clock: Clock,
    logger: Logger,
    private val skipUnreachable: Boolean = false,
    private val retryDelay: Duration = RETRY_DELAY,
) {
    private val log = logger.withTag(LIVE_CHECK_LOG_AREA)

    /**
     * Runs the check and returns one result for each of [LIVE_KINDS], in order. [simulate], if it
     * is one of them, is reported as failed with [FailedBecause.SIMULATED] instead of its result.
     */
    suspend fun run(simulate: Enum<*>? = null): List<KindResult> {
        log.info("Live check started", LogField.flag("skipUnreachable", skipUnreachable))
        val checked = LIVE_CALLS.map { check(it) }
        val results = checked + nothingChecked(checked)
        return results
            .map {
                if (it.kind == simulate) KindResult.failed(it.kind, FailedBecause.SIMULATED) else it
            }
            .onEach(::logResult)
    }

    /**
     * Makes [liveCall], once more after NETWORK, and checks how it ended, its requests, warnings
     * and content, in that order. The first check that fails gives the result.
     */
    private suspend fun check(liveCall: LiveCall): KindResult {
        val outcome = callWithRetry(liveCall)
        log.info(
            "Live check made a call",
            LogField.state("kind", liveCall.kind),
            LogField.count("requests", outcome.requests.size),
            LogField.duration("duration", outcome.duration),
        )
        val kind = liveCall.kind
        val expected = liveCall.set.expectedError
        val error = outcome.error
        val unchecked = if (error == expected) null else uncheckedBecause(error)
        return when {
            unchecked != null -> notChecked(kind, unchecked)
            error != expected -> {
                KindResult.failed(
                    kind,
                    FailedBecause.UNEXPECTED_RESULT,
                    ResultField.state("expected", expected ?: CallSucceeded.SUCCESS),
                    ResultField.state("got", error ?: CallSucceeded.SUCCESS),
                )
            }
            withoutSwJsData(outcome.requests) != withoutSwJsData(liveCall.set.requests) -> {
                requestsChanged(liveCall, outcome.requests)
            }
            error == null && outcome.warnings > 0 -> {
                KindResult.failed(
                    kind,
                    FailedBecause.READ_IN_PART,
                    ResultField.count("warnings", outcome.warnings),
                )
            }
            else -> contentResult(kind, outcome.result)
        }
    }

    /** Makes [liveCall], and once more after [retryDelay] if it fails with NETWORK unexpectedly. */
    private suspend fun callWithRetry(liveCall: LiveCall): CallOutcome {
        val first = caller.call(liveCall)
        if (first.error != YouTubeError.NETWORK || liveCall.set.expectedError == first.error) {
            return first
        }
        log.warn(
            "Live check makes a call again after NETWORK",
            LogField.state("kind", liveCall.kind),
        )
        delay(retryDelay)
        return caller.call(liveCall)
    }

    /** Logs each request that [liveCall] sent, in order, and fails it with REQUESTS_CHANGED. */
    private fun requestsChanged(liveCall: LiveCall, sent: List<Endpoint>): KindResult {
        for ((position, endpoint) in sent.withIndex()) {
            log.warn(
                "A live call sent a request",
                LogField.state("kind", liveCall.kind),
                LogField.count("position", position + 1),
                LogField.state("endpoint", endpoint),
            )
        }
        return KindResult.failed(
            liveCall.kind,
            FailedBecause.REQUESTS_CHANGED,
            ResultField.count("expected", withoutSwJsData(liveCall.set.requests).size),
            ResultField.count("sent", withoutSwJsData(sent).size),
        )
    }

    /** The result of a successful call of [kind], or of one that failed as its set expects. */
    private suspend fun contentResult(kind: Enum<*>, result: YouTubeResult<*>): KindResult {
        val value = (result as? YouTubeResult.Success<*>)?.value
        if (kind == LiveKind.STREAM_LINKS_BIG_BUCK_BUNNY) {
            return streamLinksResult(value as StreamLinks)
        }
        val wrong = value?.let { wrongContentOf(kind, it) }
        return if (wrong == null) KindResult.passed(kind) else wrongContent(kind, wrong)
    }

    /**
     * Checks Big Buck Bunny's stream links: the formats a player needs, that they last at least
     * [MIN_LINK_LIFETIME], and the first bytes of an audio-only and a video-only MP4 link.
     */
    private suspend fun streamLinksResult(links: StreamLinks): KindResult {
        val kind = LiveKind.STREAM_LINKS_BIG_BUCK_BUNNY
        val audio =
            links.audioFormats
                .filter { it.mimeType == MP4_AUDIO }
                .firstNotNullOfOrNull { format ->
                    format.contentLength?.let { LinkToAsk(FormatKind.AUDIO_ONLY, format.url, it) }
                }
        val video =
            links.videoFormats
                .filter { !it.includesAudio && it.mimeType == MP4_VIDEO }
                .firstNotNullOfOrNull { format ->
                    format.contentLength?.let { LinkToAsk(FormatKind.VIDEO_ONLY, format.url, it) }
                }
        val expiresAt = links.expiresAt
        return when {
            links.videoId != VideoId(BIG_BUCK_BUNNY_ID) -> wrongContent(kind, WrongContent.WRONG_ID)
            audio == null -> wrongContent(kind, WrongContent.NO_AUDIO_ONLY_MP4)
            video == null -> wrongContent(kind, WrongContent.NO_VIDEO_ONLY_MP4)
            links.hlsManifestUrl == null -> wrongContent(kind, WrongContent.NO_HLS_MANIFEST)
            !allGoogleVideoLinks(links) -> wrongContent(kind, WrongContent.NOT_GOOGLEVIDEO)
            expiresAt == null -> wrongContent(kind, WrongContent.NO_EXPIRY)
            expiresAt < clock.now() + MIN_LINK_LIFETIME -> {
                KindResult.failed(kind, FailedBecause.LINKS_EXPIRE_SOON)
            }
            else -> firstBytesResult(kind, listOf(audio, video))
        }
    }

    /**
     * Asks each of [links] for its first bytes, and returns the first failure, else the first skip,
     * else a pass for [kind].
     */
    private suspend fun firstBytesResult(kind: Enum<*>, links: List<LinkToAsk>): KindResult {
        val results = mutableListOf<KindResult>()
        for (link in links) {
            val answer = fetchWithRetry(link)
            log.info(
                "Live check asked a link for its first bytes",
                LogField.state("format", link.format),
                LogField.state("client", answer.client),
                LogField.state("answer", answer.answer),
                LogField.count("status", answer.status ?: NO_STATUS),
            )
            results += firstBytesKindResult(kind, link.format, answer)
        }
        return results.firstOrNull { it.verdict == Verdict.FAILED }
            ?: results.firstOrNull { it.verdict == Verdict.SKIPPED }
            ?: KindResult.passed(kind)
    }

    /** Asks [link] for its first bytes, and once more after [retryDelay] after NETWORK. */
    private suspend fun fetchWithRetry(link: LinkToAsk): FirstBytesResult {
        val first = firstBytes.fetch(link.url, link.length)
        if (first.answer != FirstBytesAnswer.NETWORK) return first
        log.warn(
            "Live check asks a link for its first bytes again after NETWORK",
            LogField.state("format", link.format),
        )
        delay(retryDelay)
        return firstBytes.fetch(link.url, link.length)
    }

    /** A kind that couldn't be checked: skipped when [skipUnreachable] is true, else failed. */
    private fun notChecked(kind: Enum<*>, because: UncheckedBecause): KindResult =
        if (skipUnreachable) {
            KindResult.skipped(kind, because)
        } else {
            KindResult.failed(kind, because)
        }

    /**
     * The result of [kind] for the first bytes of [format]: a pass, a kind not checked after
     * NETWORK, or a failure.
     */
    private fun firstBytesKindResult(
        kind: Enum<*>,
        format: FormatKind,
        result: FirstBytesResult,
    ): KindResult =
        when (result.answer) {
            FirstBytesAnswer.PLAYABLE -> KindResult.passed(kind)
            FirstBytesAnswer.NETWORK -> notChecked(kind, UncheckedBecause.NETWORK)
            FirstBytesAnswer.WRONG_LENGTH -> {
                KindResult.failed(
                    kind,
                    FailedBecause.LENGTH_MISMATCH,
                    ResultField.state("format", format),
                )
            }
            else -> {
                KindResult.failed(
                    kind,
                    FailedBecause.LINK_NOT_PLAYABLE,
                    ResultField.state("format", format),
                    ResultField.state("answer", result.answer),
                    ResultField.count("status", result.status ?: NO_STATUS),
                )
            }
        }

    /** Logs [result] at INFO when it passed, WARN when it was skipped, and ERROR when it failed. */
    private fun logResult(result: KindResult) {
        val fields = buildList {
            add(LogField.state("kind", result.kind))
            add(LogField.state("verdict", result.verdict))
            result.reason?.let { add(LogField.state("reason", it)) }
            for (field in result.fields) {
                add(field.logField)
            }
        }
        when (result.verdict) {
            Verdict.PASSED -> log.info(LIVE_CHECK_RESULT, *fields.toTypedArray())
            Verdict.SKIPPED -> log.warn(LIVE_CHECK_RESULT, *fields.toTypedArray())
            Verdict.FAILED -> log.error(LIVE_CHECK_RESULT, *fields.toTypedArray())
        }
    }
}

/** A link of [format] whose first bytes the check asks for, from a file of [length] bytes. */
private class LinkToAsk(val format: FormatKind, val url: String, val length: Long)

/** A failure of [kind] with WRONG_CONTENT, saying [what] is wrong. */
private fun wrongContent(kind: Enum<*>, what: WrongContent): KindResult =
    KindResult.failed(kind, FailedBecause.WRONG_CONTENT, ResultField.state("what", what))

/**
 * Why a call that failed with [error], when its set expects otherwise, couldn't be checked, if that
 * is why.
 */
private fun uncheckedBecause(error: YouTubeError?): UncheckedBecause? =
    when (error) {
        YouTubeError.BLOCKED -> UncheckedBecause.RUNNER_BLOCKED
        YouTubeError.NETWORK -> UncheckedBecause.NETWORK
        else -> null
    }

/** [NOTHING_CHECKED][LiveKind.NOTHING_CHECKED]: failed when every one of [results] was skipped. */
private fun nothingChecked(results: List<KindResult>): KindResult {
    val kind = LiveKind.NOTHING_CHECKED
    return if (results.all { it.verdict == Verdict.SKIPPED }) {
        KindResult.failed(
            kind,
            FailedBecause.EVERY_CALL_SKIPPED,
            ResultField.count(
                "blocked",
                results.count { it.reason == UncheckedBecause.RUNNER_BLOCKED },
            ),
            ResultField.count("network", results.count { it.reason == UncheckedBecause.NETWORK }),
        )
    } else {
        KindResult.passed(kind)
    }
}

/** [requests] without sw.js_data, which only the first call in a process fetches. */
private fun withoutSwJsData(requests: List<Endpoint>): List<Endpoint> = requests.filter {
    it != Endpoint.SW_JS_DATA
}

/**
 * What [value], which the call of [kind] returned, holds that the check doesn't expect, or null.
 * The checks look only at content unlikely to change. A set without content checks, such as a set
 * that expects an error, returns null.
 */
private fun wrongContentOf(kind: Enum<*>, value: Any): WrongContent? =
    when (kind) {
        SavedSet.SEARCH_NASA -> wrongSearch(value as SearchResults)
        SavedSet.VIDEO_BIG_BUCK_BUNNY -> wrongBigBuckBunny(value as VideoDetails)
        SavedSet.UPLOADS_NASA -> wrongUploads(value as ChannelUploads, NASA_CHANNEL_ID)
        SavedSet.UPLOADS_JAWED -> wrongJawed(value as ChannelUploads)
        SavedSet.UPLOADS_AGE_RESTRICTED -> {
            wrongUploads(value as ChannelUploads, AGE_RESTRICTED_CHANNEL_ID)
        }
        SavedSet.SUBTITLES_ME_AT_THE_ZOO -> wrongMeAtTheZooSubtitles(value as VideoSubtitles)
        SavedSet.SUBTITLES_TEARS_OF_STEEL -> wrongTearsOfSteelSubtitles(value as VideoSubtitles)
        SavedSet.SUBTITLES_AUTO_DUBBED -> wrongAutoDubbedSubtitles(value as VideoSubtitles)
        else -> null
    }

/** A search for NASA lists one of NASA's channels and at least one ordinary video. */
private fun wrongSearch(results: SearchResults): WrongContent? =
    when {
        results.channels.none { NASA_NAME in it.name } -> WrongContent.NO_NASA_CHANNEL
        results.videos.none { it.kind == VideoKind.VIDEO } -> WrongContent.NO_VIDEO
        else -> null
    }

/** Big Buck Bunny's details are the ones its page has given since 2014. */
private fun wrongBigBuckBunny(details: VideoDetails): WrongContent? =
    when {
        details.id != VideoId(BIG_BUCK_BUNNY_ID) -> WrongContent.WRONG_ID
        details.title != BIG_BUCK_BUNNY_TITLE -> WrongContent.WRONG_TITLE
        details.channelId != ChannelId(BLENDER_CHANNEL_ID) -> WrongContent.WRONG_CHANNEL
        details.duration != BIG_BUCK_BUNNY_DURATION -> WrongContent.WRONG_DURATION
        details.published != BIG_BUCK_BUNNY_PUBLISHED -> WrongContent.WRONG_PUBLISHED
        details.isLive -> WrongContent.MARKED_LIVE
        else -> null
    }

/**
 * A channel's latest uploads are the channel's own, and keep ChannelUploads' promises: no Short, no
 * video twice, and newest first, with the videos that have no publish time last.
 */
private fun wrongUploads(uploads: ChannelUploads, channelId: String): WrongContent? {
    val videos = uploads.videos
    val times = videos.map { it.published?.instant }
    val dated = times.takeWhile { it != null }.filterNotNull()
    return when {
        uploads.channel.id != ChannelId(channelId) -> WrongContent.WRONG_CHANNEL
        videos.isEmpty() -> WrongContent.NO_VIDEO
        videos.any { it.kind == VideoKind.SHORT } -> WrongContent.SHORT_INCLUDED
        videos.any { it.channelId != uploads.channel.id } -> WrongContent.VIDEO_OF_ANOTHER_CHANNEL
        videos.map { it.id }.toSet().size != videos.size -> WrongContent.DUPLICATE_VIDEO
        times.drop(dated.size).any { it != null } || dated != dated.sortedDescending() -> {
            WrongContent.WRONG_ORDER
        }
        else -> null
    }
}

/** jawed's channel has one video, "Me at the zoo". */
private fun wrongJawed(uploads: ChannelUploads): WrongContent? {
    val video = uploads.videos.singleOrNull()
    return when {
        uploads.channel.id != ChannelId(JAWED_CHANNEL_ID) -> WrongContent.WRONG_CHANNEL
        video == null || video.id != VideoId(ME_AT_THE_ZOO_ID) -> {
            WrongContent.NOT_ONLY_ME_AT_THE_ZOO
        }
        video.title != ME_AT_THE_ZOO_TITLE -> WrongContent.WRONG_TITLE
        video.duration != ME_AT_THE_ZOO_DURATION -> WrongContent.WRONG_DURATION
        video.kind != VideoKind.VIDEO -> WrongContent.WRONG_KIND
        else -> null
    }
}

/**
 * "Me at the zoo" has written tracks in English and German, whose lines are clean, and the English
 * one tells of the elephants. Other tracks may come and go.
 */
private fun wrongMeAtTheZooSubtitles(subtitles: VideoSubtitles): WrongContent? {
    val english = subtitles.linesOf(ENGLISH, isAutoGenerated = false)
    val german = subtitles.linesOf(GERMAN, isAutoGenerated = false)
    return when {
        english == null || german == null -> WrongContent.MISSING_TRACK
        else -> {
            wrongLines(english, ME_AT_THE_ZOO_DURATION)
                ?: wrongLines(german, ME_AT_THE_ZOO_DURATION)
                ?: WrongContent.WRONG_TEXT.takeIf { english.none { ELEPHANTS in it.text } }
        }
    }
}

/**
 * Tears of Steel has many written tracks, among them Hebrew, whose code iw the layer writes as he,
 * every track's tag is BCP 47 as Locale writes it, and its auto-generated English track has clean
 * lines. Tracks may be added.
 */
private fun wrongTearsOfSteelSubtitles(subtitles: VideoSubtitles): WrongContent? {
    val tracks = subtitles.tracks
    val written = tracks.filter { !it.isAutoGenerated }
    val auto = subtitles.linesOf(ENGLISH, isAutoGenerated = true)
    return when {
        tracks.any { languageTagOf(it.languageTag) != it.languageTag } -> {
            WrongContent.WRONG_LANGUAGE_TAG
        }
        auto == null || written.none { it.isInLanguage(HEBREW) } -> WrongContent.MISSING_TRACK
        written.size < TEARS_OF_STEEL_LEAST_WRITTEN_TRACKS -> WrongContent.TOO_FEW_TRACKS
        auto.isNotEmpty() && auto.size < TEARS_OF_STEEL_LEAST_AUTO_LINES -> {
            WrongContent.TOO_FEW_LINES
        }
        else -> wrongLines(auto, TEARS_OF_STEEL_DURATION)
    }
}

/**
 * A video with AI-dubbed audio has an auto-generated track, and every auto-generated track is in
 * English, the video's language: the layer leaves out the machine translations made from the dubbed
 * audio.
 */
private fun wrongAutoDubbedSubtitles(subtitles: VideoSubtitles): WrongContent? {
    val auto = subtitles.tracks.filter { it.isAutoGenerated }
    return when {
        auto.isEmpty() -> WrongContent.MISSING_TRACK
        auto.any { !it.isInLanguage(ENGLISH) } -> WrongContent.TRANSLATED_TRACK
        else -> null
    }
}

/**
 * What is wrong with [lines], the lines of a track of a video [duration] long, or null. Each line
 * has text, ends after it starts, and starts within the video. They are sorted by their start,
 * their text has no markup and no character reference left, and no line starts by repeating the
 * last row of the line before it, as the rolling cues of YouTube's WebVTT do. Lines may overlap,
 * and a line may end after the video does, as one of "Me at the zoo"'s German lines does.
 */
private fun wrongLines(lines: List<SubtitleLine>, duration: Duration): WrongContent? =
    when {
        lines.isEmpty() -> WrongContent.NO_LINES
        lines.any { it.text.isBlank() } -> WrongContent.EMPTY_LINE
        lines.any { it.end <= it.start || it.start >= duration } -> WrongContent.LINE_OUT_OF_RANGE
        lines.zipWithNext().any { (previous, next) -> next.start < previous.start } -> {
            WrongContent.LINE_OUT_OF_ORDER
        }
        lines.any { '<' in it.text || CHARACTER_REFERENCE.containsMatchIn(it.text) } -> {
            WrongContent.MARKUP_LEFT
        }
        lines.zipWithNext().any { (previous, next) -> repeats(previous, next) } -> {
            WrongContent.REPEATED_TEXT
        }
        else -> null
    }

/**
 * Whether [next] starts by repeating the last row of [previous], a row of a few words at least. The
 * rolling cues of YouTube's WebVTT do that: each cue starts with the row that ended the cue before.
 */
private fun repeats(previous: SubtitleLine, next: SubtitleLine): Boolean {
    val lastRow = previous.text.lines().last()
    return lastRow.split(' ').size >= REPEATED_LINE_LEAST_WORDS &&
        next.text.lines().first().startsWith(lastRow)
}

/** The lines that the call read of the first track in [language] of that kind, or null. */
private fun VideoSubtitles.linesOf(language: String, isAutoGenerated: Boolean) =
    read
        .firstOrNull {
            it.track.isInLanguage(language) && it.track.isAutoGenerated == isAutoGenerated
        }
        ?.lines

/** Whether every link in [links] is HTTPS to googlevideo.com or one of its subdomains. */
private fun allGoogleVideoLinks(links: StreamLinks): Boolean {
    val all =
        links.audioFormats.map { it.url } +
            links.videoFormats.map { it.url } +
            listOfNotNull(links.hlsManifestUrl)
    return all.all { link -> link.toHttpUrlOrNull()?.let(::isGoogleVideoUrl) == true }
}
