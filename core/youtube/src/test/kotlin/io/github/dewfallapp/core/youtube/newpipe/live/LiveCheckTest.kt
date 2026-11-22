package io.github.dewfallapp.core.youtube.newpipe.live

import io.github.dewfallapp.core.logging.LogLevel
import io.github.dewfallapp.core.logging.Logger
import io.github.dewfallapp.core.model.ChannelUploads
import io.github.dewfallapp.core.model.SearchResults
import io.github.dewfallapp.core.model.StreamLinks
import io.github.dewfallapp.core.model.SubtitleLine
import io.github.dewfallapp.core.model.VideoDetails
import io.github.dewfallapp.core.model.VideoId
import io.github.dewfallapp.core.model.VideoKind
import io.github.dewfallapp.core.model.YouTubeError
import io.github.dewfallapp.core.model.YouTubeResult
import io.github.dewfallapp.core.youtube.newpipe.recordingLogger
import io.github.dewfallapp.core.youtube.newpipe.saved.BIG_BUCK_BUNNY_ID
import io.github.dewfallapp.core.youtube.newpipe.saved.Endpoint
import io.github.dewfallapp.core.youtube.newpipe.saved.SavedSet
import io.github.dewfallapp.core.youtube.newpipe.saved.SavedYouTube
import io.github.dewfallapp.core.youtube.newpipe.saved.VideoSubtitles
import io.github.dewfallapp.core.youtube.newpipe.saved.valueOrFail
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import kotlin.time.Clock
import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Instant
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.currentTime
import kotlinx.coroutines.test.runTest

/** A clock long before any saved link expires. */
private val EARLY_CLOCK = fixedClock(Instant.fromEpochSeconds(0))

/** The requests of a video call that YouTube stops at the visionOS client's answer. */
private val STOPPED_VIDEO_REQUESTS =
    listOf(Endpoint.VISITOR_ID_ON_WEB_HOST, Endpoint.VISIONOS_PLAYER)

/** A call that YouTube blocked, as it blocks video calls from GitHub's runners. */
private val BLOCKED = failure(YouTubeError.BLOCKED)

/** A call that failed with NETWORK. */
private val NETWORK = failure(YouTubeError.NETWORK)

/**
 * Tests the live check's own logic. It answers each call from the call's saved set, as the replay
 * tests do, so the saved answers must pass every check, and changes some answers to test the
 * failures. Nothing reaches the network.
 */
// currentTime, which shows that the check waits before it makes a call again, is experimental.
@OptIn(ExperimentalCoroutinesApi::class)
class LiveCheckTest {
    private val lines = mutableListOf<String>()
    private val fetched = mutableListOf<Pair<String, Long>>()
    private val playable = FirstBytesFetcher { link, length ->
        fetched += link to length
        FirstBytesResult(FirstBytesAnswer.PLAYABLE, clientOf(link), 206)
    }

    @Test
    fun `the check makes every saved set's call, then the stream links, and reports each`() {
        assertEquals(
            SavedSet.entries + LiveKind.STREAM_LINKS_BIG_BUCK_BUNNY + LiveKind.NOTHING_CHECKED,
            LIVE_KINDS,
        )
    }

    @Test
    fun `every kind passes against the saved answers`() = runTest {
        val results = liveCheck(replayCaller()).run()

        assertEquals(LIVE_KINDS.map { "${it.name} PASSED" }, results.map { it.line })
        // An audio-only and a video-only MP4 link, each with its length.
        val links = savedStreamLinks()
        val audio = links.audioFormats.first { it.mimeType == "audio/mp4" }
        val video = links.videoFormats.first { !it.includesAudio && it.mimeType == "video/mp4" }
        assertEquals(
            listOf(audio.url to audio.contentLength, video.url to video.contentLength),
            fetched,
        )
        assertTrue(
            "INFO Dewfall.LiveCheck Live check result kind=SEARCH_NASA verdict=PASSED" in lines,
        )
    }

    @Test
    fun `with skipping on, a call that YouTube blocks is skipped, and fails nothing`() = runTest {
        val results = liveCheck(replayCaller(::blockingVideoCalls), skipUnreachable = true).run()

        assertEquals(
            listOf(
                "VIDEO_BIG_BUCK_BUNNY SKIPPED RUNNER_BLOCKED",
                "STREAM_LINKS_BIG_BUCK_BUNNY SKIPPED RUNNER_BLOCKED",
            ),
            results.filter { it.verdict != Verdict.PASSED }.map { it.line },
        )
        assertEquals(emptyList(), fetched)
        assertTrue(
            "WARN Dewfall.LiveCheck Live check result kind=VIDEO_BIG_BUCK_BUNNY verdict=SKIPPED " +
                "reason=RUNNER_BLOCKED" in lines,
        )
        assertTrue("INFO Dewfall.LiveCheck Live check started skipUnreachable=true" in lines)
        assertEquals(0, exitCodeOf(results))
    }

    @Test
    fun `by default, a call that YouTube blocks fails, and the run exits with an error`() =
        runTest {
            val results = liveCheck(replayCaller(::blockingVideoCalls)).run()

            assertEquals(
                listOf(
                    "VIDEO_BIG_BUCK_BUNNY FAILED RUNNER_BLOCKED",
                    "STREAM_LINKS_BIG_BUCK_BUNNY FAILED RUNNER_BLOCKED",
                ),
                results.filter { it.verdict != Verdict.PASSED }.map { it.line },
            )
            assertEquals(emptyList(), fetched)
            assertTrue("INFO Dewfall.LiveCheck Live check started skipUnreachable=false" in lines)
            assertEquals(1, exitCodeOf(results))
        }

    @Test
    fun `by default, every call failing to get through fails each kind, not NOTHING_CHECKED`() =
        runTest {
            var calls = 0
            val results =
                liveCheck(LayerCaller { if (calls++ % 3 == 0) NETWORK else BLOCKED }).run()

            assertTrue(results.dropLast(1).all { it.verdict == Verdict.FAILED })
            assertTrue(
                results.dropLast(1).all {
                    it.reason == UncheckedBecause.RUNNER_BLOCKED ||
                        it.reason == UncheckedBecause.NETWORK
                },
            )
            // Nothing was skipped, so NOTHING_CHECKED has nothing to report.
            assertEquals("NOTHING_CHECKED PASSED", results.last().line)
            assertEquals(1, exitCodeOf(results))
        }

    @Test
    fun `with skipping on, NOTHING_CHECKED fails when every call is skipped`() = runTest {
        var calls = 0
        val results =
            liveCheck(
                    LayerCaller { if (calls++ % 3 == 0) NETWORK else BLOCKED },
                    skipUnreachable = true,
                )
                .run()

        val skipped = results.dropLast(1)
        assertTrue(skipped.all { it.verdict == Verdict.SKIPPED })
        val network = skipped.count { it.reason == UncheckedBecause.NETWORK }
        assertEquals(
            "NOTHING_CHECKED FAILED EVERY_CALL_SKIPPED " +
                "blocked=${LIVE_CALLS.size - network} network=$network",
            results.last().line,
        )
    }

    @Test
    fun `with skipping on, NOTHING_CHECKED passes when one call was checked`() = runTest {
        val results =
            liveCheck(
                    replayCaller { call, outcome ->
                        if (call.kind == SavedSet.UPLOADS_JAWED) outcome else BLOCKED
                    },
                    skipUnreachable = true,
                )
                .run()

        assertEquals(
            "UPLOADS_JAWED PASSED",
            results.single { it.kind == SavedSet.UPLOADS_JAWED }.line,
        )
        assertEquals("NOTHING_CHECKED PASSED", results.last().line)
    }

    @Test
    fun `a call that fails with NETWORK is made again after a wait, and checked in full`() =
        runTest {
            var searches = 0
            val start = currentTime

            val results =
                liveCheck(
                        replayCaller { call, outcome ->
                            if (call.kind == SavedSet.SEARCH_NASA && searches++ == 0) {
                                NETWORK
                            } else {
                                outcome
                            }
                        },
                    )
                    .run()

            assertEquals("SEARCH_NASA PASSED", results.first().line)
            assertEquals(2, searches)
            assertTrue(currentTime - start >= 30.seconds.inWholeMilliseconds)
            assertTrue(
                "WARN Dewfall.LiveCheck Live check makes a call again after NETWORK " +
                    "kind=SEARCH_NASA" in lines,
            )
        }

    @Test
    fun `a call that fails with NETWORK twice fails, or is skipped with skipping on`() = runTest {
        var searches = 0
        val caller = replayCaller { call, outcome ->
            if (call.kind == SavedSet.SEARCH_NASA) {
                searches++
                NETWORK
            } else {
                outcome
            }
        }

        val strict = liveCheck(caller).run()
        val skipping = liveCheck(caller, skipUnreachable = true).run()

        assertEquals("SEARCH_NASA FAILED NETWORK", strict.first().line)
        assertEquals("SEARCH_NASA SKIPPED NETWORK", skipping.first().line)
        // Each run makes the call twice.
        assertEquals(4, searches)
    }

    @Test
    fun `Big Buck Bunny's details fail the check when they fail, even with UNAVAILABLE`() =
        runTest {
            val results =
                liveCheck(
                        replacing(SavedSet.VIDEO_BIG_BUCK_BUNNY) {
                            failure(YouTubeError.UNAVAILABLE, STOPPED_VIDEO_REQUESTS)
                        },
                    )
                    .run()

            assertEquals(
                "VIDEO_BIG_BUCK_BUNNY FAILED UNEXPECTED_RESULT " +
                    "expected=SUCCESS got=UNAVAILABLE",
                results.single { it.kind == SavedSet.VIDEO_BIG_BUCK_BUNNY }.line,
            )
            assertTrue(
                "ERROR Dewfall.LiveCheck Live check result kind=VIDEO_BIG_BUCK_BUNNY " +
                    "verdict=FAILED reason=UNEXPECTED_RESULT expected=SUCCESS got=UNAVAILABLE" in
                    lines,
            )
        }

    @Test
    fun `a set that expects an error fails when the call ends another way`() = runTest {
        val results =
            liveCheck(
                    replayCaller { call, outcome ->
                        when (call.kind) {
                            SavedSet.VIDEO_PRIVATE -> {
                                failure(YouTubeError.BROKEN, STOPPED_VIDEO_REQUESTS)
                            }
                            SavedSet.CHANNEL_DELETED -> success(Unit, outcome.requests)
                            else -> outcome
                        }
                    },
                )
                .run()

        assertEquals(
            listOf(
                "VIDEO_PRIVATE FAILED UNEXPECTED_RESULT expected=PRIVATE got=BROKEN",
                "CHANNEL_DELETED FAILED UNEXPECTED_RESULT expected=UNAVAILABLE got=SUCCESS",
            ),
            results.filter { it.verdict != Verdict.PASSED }.map { it.line },
        )
    }

    @Test
    fun `a success that the layer logged a warning for fails READ_IN_PART`() = runTest {
        val results =
            liveCheck(
                    replacing(SavedSet.UPLOADS_NASA) {
                        CallOutcome(it.result, it.requests, warnings = 1, Duration.ZERO)
                    },
                )
                .run()

        assertEquals(
            "UPLOADS_NASA FAILED READ_IN_PART warnings=1",
            results.single { it.kind == SavedSet.UPLOADS_NASA }.line,
        )
    }

    @Test
    fun `other requests fail REQUESTS_CHANGED, and the log names each request`() = runTest {
        val results =
            liveCheck(
                    replacing(SavedSet.UPLOADS_NASA) {
                        success(
                            it.result.valueOrFail(),
                            listOf(Endpoint.SW_JS_DATA, Endpoint.BROWSE),
                        )
                    },
                )
                .run()

        assertEquals(
            "UPLOADS_NASA FAILED REQUESTS_CHANGED expected=2 sent=1",
            results.single { it.kind == SavedSet.UPLOADS_NASA }.line,
        )
        assertEquals(
            listOf(
                "WARN Dewfall.LiveCheck A live call sent a request kind=UPLOADS_NASA position=1 " +
                    "endpoint=SW_JS_DATA",
                "WARN Dewfall.LiveCheck A live call sent a request kind=UPLOADS_NASA position=2 " +
                    "endpoint=BROWSE",
            ),
            lines.filter { "sent a request" in it },
        )
    }

    @Test
    fun `sw_js_data counts as no request, wherever it comes or doesn't`() = runTest {
        val results =
            liveCheck(
                    replayCaller { _, outcome ->
                        val requests = outcome.requests.filter { it != Endpoint.SW_JS_DATA }
                        CallOutcome(outcome.result, requests, outcome.warnings, outcome.duration)
                    },
                )
                .run()

        assertTrue(results.all { it.verdict == Verdict.PASSED })
    }

    @Test
    fun `a search without one of NASA's channels fails WRONG_CONTENT`() = runTest {
        assertWrongContent(SavedSet.SEARCH_NASA, "NO_NASA_CHANNEL") {
            (it as SearchResults).copy(channels = emptyList())
        }
    }

    @Test
    fun `Big Buck Bunny with another title, or live, fails WRONG_CONTENT`() = runTest {
        assertWrongContent(SavedSet.VIDEO_BIG_BUCK_BUNNY, "WRONG_TITLE") {
            (it as VideoDetails).copy(title = "Big Buck Bunny")
        }
        assertWrongContent(SavedSet.VIDEO_BIG_BUCK_BUNNY, "MARKED_LIVE") {
            (it as VideoDetails).copy(isLive = true)
        }
    }

    @Test
    fun `uploads with a Short, or out of order, fail WRONG_CONTENT`() = runTest {
        assertWrongContent(SavedSet.UPLOADS_NASA, "SHORT_INCLUDED") {
            val uploads = it as ChannelUploads
            val first = uploads.videos.first().copy(kind = VideoKind.SHORT)
            uploads.copy(videos = listOf(first) + uploads.videos.drop(1))
        }
        assertWrongContent(SavedSet.UPLOADS_NASA, "WRONG_ORDER") {
            val uploads = it as ChannelUploads
            uploads.copy(videos = uploads.videos.reversed())
        }
    }

    @Test
    fun `jawed's channel with more than one video fails WRONG_CONTENT`() = runTest {
        assertWrongContent(SavedSet.UPLOADS_JAWED, "NOT_ONLY_ME_AT_THE_ZOO") {
            val uploads = it as ChannelUploads
            uploads.copy(videos = uploads.videos + uploads.videos)
        }
    }

    @Test
    fun `Me at the zoo without its English or German track fails WRONG_CONTENT`() = runTest {
        assertWrongContent(SavedSet.SUBTITLES_ME_AT_THE_ZOO, "MISSING_TRACK") {
            val subtitles = it as VideoSubtitles
            subtitles.copy(read = subtitles.read.filter { read -> read.track.languageTag != "de" })
        }
    }

    @Test
    fun `a track that someone adds to Me at the zoo fails nothing`() = runTest {
        val results =
            liveCheck(
                    replacing(SavedSet.SUBTITLES_ME_AT_THE_ZOO) { outcome ->
                        val subtitles =
                            (outcome.result as YouTubeResult.Success).value as VideoSubtitles
                        val added = subtitles.tracks.first().copy(languageTag = "fr")
                        success(subtitles.copy(tracks = subtitles.tracks + added), outcome.requests)
                    },
                )
                .run()

        assertEquals(
            "SUBTITLES_ME_AT_THE_ZOO PASSED",
            results.single { it.kind == SavedSet.SUBTITLES_ME_AT_THE_ZOO }.line,
        )
    }

    @Test
    fun `lines that lost their text, their order or their decoding fail WRONG_CONTENT`() = runTest {
        val cases =
            listOf<Pair<String, (List<SubtitleLine>) -> List<SubtitleLine>>>(
                "NO_LINES" to { emptyList() },
                "EMPTY_LINE" to { lines -> listOf(lines.first().copy(text = " ")) + lines.drop(1) },
                "LINE_OUT_OF_RANGE" to
                    { lines ->
                        lines + lines.last().copy(start = 20.seconds, end = 21.seconds)
                    },
                "LINE_OUT_OF_ORDER" to { lines -> lines.reversed() },
                "MARKUP_LEFT" to { lines -> lines + lines.last().copy(text = "that&#39;s") },
                "MARKUP_LEFT" to { lines -> lines + lines.last().copy(text = "<b>cool</b>") },
            )
        for ((what, change) in cases) {
            assertWrongContent(SavedSet.SUBTITLES_ME_AT_THE_ZOO, what) {
                (it as VideoSubtitles).changeLines("en", change)
            }
        }
        assertWrongContent(SavedSet.SUBTITLES_ME_AT_THE_ZOO, "WRONG_TEXT") {
            (it as VideoSubtitles).changeLines("en") { lines ->
                lines.map { line -> line.copy(text = line.text.replace("elephants", "giraffes")) }
            }
        }
    }

    @Test
    fun `auto-generated lines that repeat the line before fail WRONG_CONTENT`() = runTest {
        assertWrongContent(SavedSet.SUBTITLES_TEARS_OF_STEEL, "REPEATED_TEXT") {
            (it as VideoSubtitles).changeLines("en") { lines ->
                // As WebVTT's rolling cues do: each cue starts with the whole line before it.
                lines.zipWithNext { previous, next ->
                    next.copy(text = previous.text + "\n" + next.text)
                }
            }
        }
    }

    @Test
    fun `Tears of Steel without its tracks, or with YouTube's own codes, fails WRONG_CONTENT`() =
        runTest {
            assertWrongContent(SavedSet.SUBTITLES_TEARS_OF_STEEL, "WRONG_LANGUAGE_TAG") {
                val subtitles = it as VideoSubtitles
                subtitles.copy(
                    tracks =
                        subtitles.tracks.map { track ->
                            if (track.languageTag == "he") track.copy(languageTag = "iw") else track
                        },
                )
            }
            assertWrongContent(SavedSet.SUBTITLES_TEARS_OF_STEEL, "MISSING_TRACK") {
                (it as VideoSubtitles).copy(read = emptyList())
            }
            assertWrongContent(SavedSet.SUBTITLES_TEARS_OF_STEEL, "TOO_FEW_TRACKS") {
                val subtitles = it as VideoSubtitles
                subtitles.copy(
                    tracks = subtitles.tracks.filter { track -> track.languageTag in KEPT },
                )
            }
            assertWrongContent(SavedSet.SUBTITLES_TEARS_OF_STEEL, "TOO_FEW_LINES") {
                (it as VideoSubtitles).changeLines("en") { lines -> lines.take(10) }
            }
        }

    @Test
    fun `a second auto-generated track in English with a region fails nothing`() = runTest {
        // YouTube's answer for the dubbed video sometimes lists its English track as en and en-US.
        val results =
            liveCheck(
                    replacing(SavedSet.SUBTITLES_AUTO_DUBBED) { outcome ->
                        val subtitles =
                            (outcome.result as YouTubeResult.Success).value as VideoSubtitles
                        val auto = subtitles.tracks.first { track -> track.isAutoGenerated }
                        val regional = auto.copy(languageTag = "en-US")
                        success(
                            subtitles.copy(tracks = subtitles.tracks + regional),
                            outcome.requests,
                        )
                    },
                )
                .run()

        assertEquals(
            "SUBTITLES_AUTO_DUBBED PASSED",
            results.single { it.kind == SavedSet.SUBTITLES_AUTO_DUBBED }.line,
        )
    }

    @Test
    fun `a machine-translated track of dubbed audio fails WRONG_CONTENT`() = runTest {
        assertWrongContent(SavedSet.SUBTITLES_AUTO_DUBBED, "TRANSLATED_TRACK") {
            val subtitles = it as VideoSubtitles
            val auto = subtitles.tracks.first { track -> track.isAutoGenerated }
            subtitles.copy(tracks = subtitles.tracks + auto.copy(languageTag = "hi"))
        }
        assertWrongContent(SavedSet.SUBTITLES_AUTO_DUBBED, "MISSING_TRACK") {
            val subtitles = it as VideoSubtitles
            subtitles.copy(tracks = subtitles.tracks.filter { track -> !track.isAutoGenerated })
        }
    }

    @Test
    fun `stream links without what a player needs fail WRONG_CONTENT`() = runTest {
        val kind = LiveKind.STREAM_LINKS_BIG_BUCK_BUNNY
        assertWrongContent(kind, "NO_HLS_MANIFEST") {
            (it as StreamLinks).copy(hlsManifestUrl = null)
        }
        assertWrongContent(kind, "NO_AUDIO_ONLY_MP4") {
            val links = it as StreamLinks
            links.copy(audioFormats = links.audioFormats.map { it.copy(mimeType = "audio/webm") })
        }
        assertWrongContent(kind, "NOT_GOOGLEVIDEO") {
            val links = it as StreamLinks
            links.copy(
                videoFormats =
                    links.videoFormats.map {
                        it.copy(url = it.url.replace("googlevideo.com", "example.com"))
                    },
            )
        }
        assertEquals(emptyList(), fetched)
    }

    @Test
    fun `stream links that expire within an hour fail LINKS_EXPIRE_SOON`() = runTest {
        val expiresAt = assertNotNull(savedStreamLinks().expiresAt)

        val soon = liveCheck(replayCaller(), clock = fixedClock(expiresAt - 59.minutes)).run()
        val later = liveCheck(replayCaller(), clock = fixedClock(expiresAt - 61.minutes)).run()

        assertEquals(
            "STREAM_LINKS_BIG_BUCK_BUNNY FAILED LINKS_EXPIRE_SOON",
            soon.single { it.kind == LiveKind.STREAM_LINKS_BIG_BUCK_BUNNY }.line,
        )
        assertEquals(
            "STREAM_LINKS_BIG_BUCK_BUNNY PASSED",
            later.single { it.kind == LiveKind.STREAM_LINKS_BIG_BUCK_BUNNY }.line,
        )
    }

    @Test
    fun `a link that answers another status fails LINK_NOT_PLAYABLE, and both links are asked`() =
        runTest {
            val forbidden = FirstBytesFetcher { link, length ->
                fetched += link to length
                val first = fetched.size == 1
                val answer = if (first) FirstBytesAnswer.WRONG_STATUS else FirstBytesAnswer.PLAYABLE
                FirstBytesResult(answer, clientOf(link), if (first) 403 else 206)
            }

            val results = liveCheck(replayCaller(), firstBytes = forbidden).run()

            assertEquals(
                "STREAM_LINKS_BIG_BUCK_BUNNY FAILED LINK_NOT_PLAYABLE " +
                    "format=AUDIO_ONLY answer=WRONG_STATUS status=403",
                results.single { it.kind == LiveKind.STREAM_LINKS_BIG_BUCK_BUNNY }.line,
            )
            assertEquals(2, fetched.size)
            assertEquals(2, lines.count { "asked a link for its first bytes" in it })
        }

    @Test
    fun `a total length that differs from the format's fails LENGTH_MISMATCH`() = runTest {
        val wrongLength = FirstBytesFetcher { link, length ->
            fetched += link to length
            val answer =
                if (fetched.size == 2) FirstBytesAnswer.WRONG_LENGTH else FirstBytesAnswer.PLAYABLE
            FirstBytesResult(answer, clientOf(link), 206)
        }

        val results = liveCheck(replayCaller(), firstBytes = wrongLength).run()

        assertEquals(
            "STREAM_LINKS_BIG_BUCK_BUNNY FAILED LENGTH_MISMATCH format=VIDEO_ONLY",
            results.single { it.kind == LiveKind.STREAM_LINKS_BIG_BUCK_BUNNY }.line,
        )
    }

    @Test
    fun `a request for first bytes that fails with NETWORK twice fails, or is skipped`() = runTest {
        val unreachable = FirstBytesFetcher { link, length ->
            fetched += link to length
            FirstBytesResult(FirstBytesAnswer.NETWORK, clientOf(link), null)
        }

        val strict = liveCheck(replayCaller(), firstBytes = unreachable).run()
        val skipping =
            liveCheck(replayCaller(), firstBytes = unreachable, skipUnreachable = true).run()

        assertEquals(
            "STREAM_LINKS_BIG_BUCK_BUNNY FAILED NETWORK",
            strict.single { it.kind == LiveKind.STREAM_LINKS_BIG_BUCK_BUNNY }.line,
        )
        assertEquals(
            "STREAM_LINKS_BIG_BUCK_BUNNY SKIPPED NETWORK",
            skipping.single { it.kind == LiveKind.STREAM_LINKS_BIG_BUCK_BUNNY }.line,
        )
        // Each link twice in each run.
        assertEquals(8, fetched.size)
        assertEquals("NOTHING_CHECKED PASSED", skipping.last().line)
    }

    @Test
    fun `with skipping on, a failed link counts before a skipped one`() = runTest {
        val mixed = FirstBytesFetcher { link, length ->
            fetched += link to length
            if (fetched.size <= 2) {
                FirstBytesResult(FirstBytesAnswer.NETWORK, clientOf(link), null)
            } else {
                FirstBytesResult(FirstBytesAnswer.WRONG_STATUS, clientOf(link), 403)
            }
        }

        val results = liveCheck(replayCaller(), firstBytes = mixed, skipUnreachable = true).run()

        assertEquals(
            "STREAM_LINKS_BIG_BUCK_BUNNY FAILED LINK_NOT_PLAYABLE " +
                "format=VIDEO_ONLY answer=WRONG_STATUS status=403",
            results.single { it.kind == LiveKind.STREAM_LINKS_BIG_BUCK_BUNNY }.line,
        )
    }

    @Test
    fun `a simulated failure replaces the result of its kind`() = runTest {
        val results = liveCheck(replayCaller()).run(simulate = SavedSet.UPLOADS_JAWED)

        assertEquals(
            listOf("UPLOADS_JAWED FAILED SIMULATED"),
            results.filter { it.verdict != Verdict.PASSED }.map { it.line },
        )
    }

    /**
     * Runs the check with the saved answers, the outcome of [kind] changed by [change], and asserts
     * that [kind] fails with WRONG_CONTENT, naming [what].
     */
    private suspend fun TestScope.assertWrongContent(
        kind: Enum<*>,
        what: String,
        change: (Any?) -> Any,
    ) {
        val results =
            liveCheck(replacing(kind) { success(change(it.result.valueOrFail()), it.requests) })
                .run()

        assertEquals(
            "${kind.name} FAILED WRONG_CONTENT what=$what",
            results.single { it.kind == kind }.line,
        )
    }

    private fun liveCheck(
        caller: LayerCaller,
        firstBytes: FirstBytesFetcher = playable,
        clock: Clock = EARLY_CLOCK,
        skipUnreachable: Boolean = false,
    ) = LiveCheck(caller, firstBytes, clock, recordingLogger(lines), skipUnreachable)
}

/**
 * Tests the fields that say how YouTube answered the visionOS player request of a call that got
 * UNAVAILABLE when its set expects something else. The answers are made up, and every other call is
 * answered from its saved set.
 */
class LiveCheckPlayabilityTest {
    private val lines = mutableListOf<String>()

    @Test
    fun `an UNAVAILABLE call names its visionOS answer's status and whether it gave a reason`() =
        runTest {
            val unplayable = PlayabilityAnswer(PlayabilityStatus.UNPLAYABLE, ReasonGiven.GIVEN)
            val results =
                liveCheck(
                        replacing(SavedSet.VIDEO_BIG_BUCK_BUNNY) {
                            failure(YouTubeError.UNAVAILABLE, playability = unplayable)
                        },
                    )
                    .run()

            assertEquals(
                "VIDEO_BIG_BUCK_BUNNY FAILED UNEXPECTED_RESULT expected=SUCCESS got=UNAVAILABLE " +
                    "playability=UNPLAYABLE reasonGiven=GIVEN",
                results.single { it.kind == SavedSet.VIDEO_BIG_BUCK_BUNNY }.line,
            )
            assertTrue(
                "ERROR Dewfall.LiveCheck Live check result kind=VIDEO_BIG_BUCK_BUNNY " +
                    "verdict=FAILED reason=UNEXPECTED_RESULT expected=SUCCESS got=UNAVAILABLE " +
                    "playability=UNPLAYABLE reasonGiven=GIVEN" in lines,
            )
        }

    @Test
    fun `the fields follow the answer, for any set that doesn't expect UNAVAILABLE`() = runTest {
        val changed: Map<Enum<*>, CallOutcome> =
            mapOf(
                SavedSet.VIDEO_BIG_BUCK_BUNNY to
                    unavailable(PlayabilityStatus.LOGIN_REQUIRED, ReasonGiven.MISSING),
                SavedSet.VIDEO_PRIVATE to unavailable(PlayabilityStatus.OTHER, ReasonGiven.GIVEN),
                LiveKind.STREAM_LINKS_BIG_BUCK_BUNNY to
                    unavailable(PlayabilityStatus.NOT_JSON, null),
                // A broken call, and one with no visionOS answer, get no field.
                SavedSet.SUBTITLES_ME_AT_THE_ZOO to
                    failure(
                        YouTubeError.BROKEN,
                        playability = PlayabilityAnswer(PlayabilityStatus.OK, ReasonGiven.MISSING),
                    ),
                SavedSet.SUBTITLES_TEARS_OF_STEEL to failure(YouTubeError.UNAVAILABLE),
            )

        val results =
            liveCheck(replayCaller { call, outcome -> changed[call.kind] ?: outcome }).run()

        assertEquals(
            listOf(
                "VIDEO_BIG_BUCK_BUNNY FAILED UNEXPECTED_RESULT expected=SUCCESS got=UNAVAILABLE " +
                    "playability=LOGIN_REQUIRED reasonGiven=MISSING",
                "VIDEO_PRIVATE FAILED UNEXPECTED_RESULT expected=PRIVATE got=UNAVAILABLE " +
                    "playability=OTHER reasonGiven=GIVEN",
                "SUBTITLES_ME_AT_THE_ZOO FAILED UNEXPECTED_RESULT expected=SUCCESS got=BROKEN",
                "SUBTITLES_TEARS_OF_STEEL FAILED UNEXPECTED_RESULT expected=SUCCESS " +
                    "got=UNAVAILABLE",
                "STREAM_LINKS_BIG_BUCK_BUNNY FAILED UNEXPECTED_RESULT expected=SUCCESS " +
                    "got=UNAVAILABLE playability=NOT_JSON",
            ),
            results.filter { it.verdict != Verdict.PASSED }.map { it.line },
        )
        assertTrue(results.all { RESULT_LINE.matches(it.line) })
    }

    @Test
    fun `a set that expects UNAVAILABLE passes with its answer, and names no status`() = runTest {
        val results =
            liveCheck(
                    replacing(SavedSet.VIDEO_NEVER_EXISTED) {
                        failure(
                            YouTubeError.UNAVAILABLE,
                            it.requests,
                            PlayabilityAnswer(PlayabilityStatus.ERROR, ReasonGiven.GIVEN),
                        )
                    },
                )
                .run()

        assertEquals(
            "VIDEO_NEVER_EXISTED PASSED",
            results.single { it.kind == SavedSet.VIDEO_NEVER_EXISTED }.line,
        )
    }

    private fun liveCheck(caller: LayerCaller): LiveCheck {
        val playable = FirstBytesFetcher { link, _ ->
            FirstBytesResult(FirstBytesAnswer.PLAYABLE, clientOf(link), 206)
        }
        return LiveCheck(caller, playable, EARLY_CLOCK, recordingLogger(lines))
    }
}

/** A call that got UNAVAILABLE, whose visionOS player answer said [status] and [reasonGiven]. */
private fun unavailable(status: PlayabilityStatus, reasonGiven: ReasonGiven?) =
    failure(YouTubeError.UNAVAILABLE, playability = PlayabilityAnswer(status, reasonGiven))

/**
 * Answers each call from its saved set, through the layer, as the replay tests do, then passes the
 * outcome through [change]. The outcome counts the lines at WARN or above that the layer logged
 * with WarningCounter, as the live run does.
 */
private fun TestScope.replayCaller(
    change: (LiveCall, CallOutcome) -> CallOutcome = { _, outcome -> outcome },
): LayerCaller = LayerCaller { liveCall ->
    val warnings = WarningCounter { _, _, _ -> }
    val youTube =
        SavedYouTube(
            liveCall.set,
            Logger(warnings, LogLevel.DEBUG),
            StandardTestDispatcher(testScheduler),
        )
    val result = youTube.call(liveCall.call)
    change(liveCall, CallOutcome(result, youTube.sentRequests, warnings.count, Duration.ZERO))
}

/**
 * Blocks Big Buck Bunny's details and stream links, as YouTube blocks them from GitHub's runners,
 * and leaves [outcome] of every other call as it is.
 */
private fun blockingVideoCalls(call: LiveCall, outcome: CallOutcome): CallOutcome =
    if (call.set == SavedSet.VIDEO_BIG_BUCK_BUNNY) BLOCKED else outcome

/**
 * Answers each call from its saved set, except the call of [kind], whose outcome [change] makes.
 */
private fun TestScope.replacing(kind: Enum<*>, change: (CallOutcome) -> CallOutcome) =
    replayCaller { call, outcome ->
        if (call.kind == kind) change(outcome) else outcome
    }

/** Big Buck Bunny's stream links in the saved set. */
private suspend fun TestScope.savedStreamLinks(): StreamLinks =
    SavedYouTube(
            SavedSet.VIDEO_BIG_BUCK_BUNNY,
            Logger.Silent,
            StandardTestDispatcher(testScheduler),
        )
        .call { streamLinks(VideoId(BIG_BUCK_BUNNY_ID)) }
        .valueOrFail()

/** The languages of the Tears of Steel tracks that a test keeps when it takes the others away. */
private val KEPT = setOf("en", "he")

/** These subtitles, with the read lines of the written or auto track in [languageTag] changed. */
private fun VideoSubtitles.changeLines(
    languageTag: String,
    change: (List<SubtitleLine>) -> List<SubtitleLine>,
) =
    copy(
        read =
            read.map {
                if (it.track.languageTag == languageTag) it.copy(lines = change(it.lines)) else it
            },
    )

/** A call that succeeded with [value] after [requests]. */
private fun success(value: Any?, requests: List<Endpoint>) =
    CallOutcome(YouTubeResult.Success(value), requests, warnings = 0, Duration.ZERO)

/**
 * A call that failed with [error] after [requests], which the layer logged once, and whose visionOS
 * player answer said [playability], if one came.
 */
private fun failure(
    error: YouTubeError,
    requests: List<Endpoint> = STOPPED_VIDEO_REQUESTS,
    playability: PlayabilityAnswer? = null,
) = CallOutcome(YouTubeResult.Failure(error), requests, warnings = 1, Duration.ZERO, playability)

/** A clock that always says [now]. */
private fun fixedClock(now: Instant): Clock =
    object : Clock {
        override fun now(): Instant = now
    }
